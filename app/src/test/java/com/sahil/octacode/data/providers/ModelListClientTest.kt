package com.sahil.octacode.data.providers

import com.sahil.octacode.core.provider.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `GET /models` side of "models come from the provider".
 *
 * Two things are being pinned that a user cannot diagnose from outside: the
 * path (a wrong one reads as a bad key) and where the key travels (a URL is
 * logged and referred; a header is not).
 */
class ModelListClientTest {

    @Test
    fun `ids come out of an openai-shaped list body`() {
        val body = """
            {"object":"list","data":[
                {"id":"grok-4.6","object":"model","created":1,"owned_by":"xai"},
                {"id":"grok-4.5","object":"model","created":2,"owned_by":"xai"}
            ]}
        """.trimIndent()

        assertEquals(listOf("grok-4.6", "grok-4.5"), decodeModelIds(body))
    }

    @Test
    fun `fields we do not understand are ignored rather than fatal`() {
        // Providers add their own to this payload. Refusing on one would make
        // the fetch fail for a reason the user is never shown.
        val body = """
            {"object":"list","extra":true,
             "data":[{"id":"m","owned_by":"x","extra":{"deep":[1]}}]}
        """.trimIndent()

        assertEquals(listOf("m"), decodeModelIds(body))
    }

    @Test
    fun `blank and repeated ids are dropped`() {
        val body = """{"data":[{"id":"a"},{"id":"  a  "},{"id":"   "},{"id":"b"}]}"""

        assertEquals(listOf("a", "b"), decodeModelIds(body))
    }

    @Test
    fun `a list with nothing in it is a real answer`() {
        assertEquals(emptyList<String>(), decodeModelIds("""{"object":"list","data":[]}"""))
    }

    @Test
    fun `a body that is not a list is refused, not reported as empty`() {
        // "0 models" and "this endpoint has nothing for you" are opposite
        // meanings, and an HTML error page must become neither.
        val thrown = assertThrows(SerializationException::class.java) {
            decodeModelIds("<html><body>502 Bad Gateway</body></html>")
        }
        assertTrue(thrown.message!!.isNotBlank())
    }

    @Test
    fun `the list is fetched at models with the key in a header and never the url`() = runBlocking {
        var url: String? = null
        var auth: String? = null
        val http = HttpClient(MockEngine { request ->
            url = request.url.toString()
            auth = request.headers[HttpHeaders.Authorization]
            respond("""{"data":[{"id":"m"}]}""", HttpStatusCode.OK)
        })

        val ids = fetchModelIds(http, MODEL_LIST_ENDPOINTS.getValue(ProviderId.GROQ), "gsk_secret")

        assertEquals(listOf("m"), ids)
        assertEquals("https://api.groq.com/openai/v1/models", url)
        assertEquals("Bearer gsk_secret", auth)
        // The key must not be in the URL: URLs are what gets logged, cached
        // and put into referrers.
        assertFalse(url!!.contains("gsk_secret"))
    }

    @Test
    fun `a base with a trailing slash does not become a double slash`() {
        // `…/v1//models` answers 404 for a path nobody sees, and the message
        // would say the provider has no list at all — the wrong conclusion
        // from a character nobody thought about.
        assertEquals("https://api.example/v1/models", listUrlFor("https://api.example/v1"))
        assertEquals("https://api.example/v1/models", listUrlFor("https://api.example/v1/"))
    }

    @Test
    fun `the list extends each named provider's own base rather than restating it`() {
        // The base is written once, for streaming. Deriving the list from it
        // means a URL corrected in one place cannot disagree with the other.
        NAMED_PROVIDER_BASE_URLS.forEach { (id, base) ->
            val endpoint = MODEL_LIST_ENDPOINTS.getValue(id)
            assertEquals("$id list url", listUrlFor(base), endpoint.url)
            assertTrue("$id lost its scheme", endpoint.url.startsWith("https://"))
            assertEquals("$id format", ModelListFormat.OPENAI, endpoint.format)
            assertEquals("$id header", HttpHeaders.Authorization, endpoint.authHeader)
        }
    }

    @Test
    fun `every provider has a list address except the custom endpoint`() {
        // CUSTOM is the one exception because a self-hosted server has no
        // catalogue we could rely on: calling a path it may not have would
        // read as the app being broken rather than as a server without one.
        assertEquals(setOf(ProviderId.CUSTOM), ProviderId.entries.toSet() - MODEL_LIST_ENDPOINTS.keys)
    }

    @Test
    fun `claude and gemini carry the auth header their own api documents`() {
        // Bearer satisfies neither. A wrong header on a real key returns the
        // same 401 a wrong key does, so the failure would be indistinguishable
        // from outside — the user would be told their key was rejected when it
        // was never read.
        val claude = MODEL_LIST_ENDPOINTS.getValue(ProviderId.CLAUDE)
        assertEquals("x-api-key", claude.authHeader)
        assertEquals("Claude must send the bare key", "", claude.authScheme)
        assertEquals(ModelListFormat.OPENAI, claude.format)
        assertTrue("was: ${claude.url}", claude.url.contains("https://api.anthropic.com/v1/models"))

        val gemini = MODEL_LIST_ENDPOINTS.getValue(ProviderId.GEMINI)
        assertEquals("x-goog-api-key", gemini.authHeader)
        assertEquals("Gemini must send the bare key", "", gemini.authScheme)
        assertEquals(ModelListFormat.GEMINI, gemini.format)
        assertTrue(
            "was: ${gemini.url}",
            gemini.url.contains("https://generativelanguage.googleapis.com/v1beta/models"),
        )
    }

    @Test
    fun `claude's list is read with x-api-key, which bearer is not`() = runBlocking {
        var declared: String? = null
        var bearer: String? = null
        val http = HttpClient(MockEngine { request ->
            declared = request.headers["x-api-key"]
            bearer = request.headers[HttpHeaders.Authorization]
            respond("""{"data":[{"id":"claude-opus-5"},{"id":"claude-opus-4-6"}]}""", HttpStatusCode.OK)
        })

        val ids = fetchModelIds(http, MODEL_LIST_ENDPOINTS.getValue(ProviderId.CLAUDE), "sk-ant_secret")

        assertEquals(listOf("claude-opus-5", "claude-opus-4-6"), ids)
        assertEquals("sk-ant_secret", declared)
        assertNull("claude must not be asked for a Bearer token", bearer)
    }

    @Test
    fun `gemini's list is read with its own shape, not the openai one`() = runBlocking {
        var auth: String? = null
        val http = HttpClient(MockEngine { request ->
            auth = request.headers["x-goog-api-key"]
            respond(
                """
                {"models":[
                    {"name":"models/gemini-3.7-flash","supportedGenerationMethods":["generateContent"]},
                    {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
                    {"name":"models/gemini-2.5-pro"}
                ]}
                """.trimIndent(),
                HttpStatusCode.OK,
            )
        })

        val ids = fetchModelIds(http, MODEL_LIST_ENDPOINTS.getValue(ProviderId.GEMINI), "AIza_secret")

        assertEquals("AIza_secret", auth)
        // Three corrections: the `models/` prefix is not part of the id the
        // chat endpoint accepts, an embedding model cannot answer a chat, and
        // a model that says nothing about its methods is kept rather than
        // dropped — hiding a working model is the worse of the two errors.
        assertEquals(listOf("gemini-3.7-flash", "gemini-2.5-pro"), ids)
    }

    @Test
    fun `a rejected key surfaces as a status rather than as an unreadable body`() {
        runBlocking {
            val http = HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) })
            var status: Int? = null
            try {
                fetchModelIds(http, MODEL_LIST_ENDPOINTS.getValue(ProviderId.GROQ), "gsk_bad")
            } catch (e: ProviderHttpException) {
                status = e.status
            }
            assertEquals(401, status ?: -1)
        }
    }
}
