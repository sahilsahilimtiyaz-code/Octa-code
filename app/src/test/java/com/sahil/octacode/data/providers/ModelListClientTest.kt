package com.sahil.octacode.data.providers

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
    fun `the list is fetched once, at models, whether or not the base has a slash`() = runBlocking {
        var url: String? = null
        var auth: String? = null
        val http = HttpClient(MockEngine { request ->
            url = request.url.toString()
            auth = request.headers[HttpHeaders.Authorization]
            respond("""{"data":[{"id":"m"}]}""", HttpStatusCode.OK)
        })

        val ids = fetchModelIds(http, "https://api.groq.com/openai/v1/", "gsk_secret")

        assertEquals(listOf("m"), ids)
        // A stored base with a trailing slash must not become //models, and a
        // base that already carries a path must not have it repeated.
        assertEquals("https://api.groq.com/openai/v1/models", url)
        assertEquals("Bearer gsk_secret", auth)
        // The key must not be in the URL: URLs are what gets logged, cached
        // and put into referrers.
        assertFalse(url!!.contains("gsk_secret"))
    }

    @Test
    fun `a rejected key surfaces as a status rather than as an unreadable body`() {
        runBlocking {
            val http = HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) })
            var status: Int? = null
            try {
                fetchModelIds(http, "https://api.groq.com/openai/v1", "gsk_bad")
            } catch (e: ProviderHttpException) {
                status = e.status
            }
            assertEquals(401, status ?: -1)
        }
    }
}
