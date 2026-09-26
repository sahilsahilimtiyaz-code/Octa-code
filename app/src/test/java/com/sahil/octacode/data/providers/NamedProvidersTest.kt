package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.provider.defaultModelFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards what M12.1 actually ships: five more providers reachable by API key.
 *
 * The two failure modes worth pinning are the ones a user cannot diagnose from
 * the outside — a base URL that does not exist looks exactly like a bad key,
 * and a provider whose key field never made it to the Settings screen cannot
 * be configured at all.
 */
class NamedProvidersTest {

    private fun http() = HttpClient(
        MockEngine { error("no request should leave the device in this test") }
    )

    private fun adapterFor(
        id: ProviderId,
        key: () -> String?
    ): CompatibleProviderAdapter =
        CompatibleProviderAdapter(id, NAMED_PROVIDER_BASE_URLS.getValue(id), http(), key)

    private val request = ChatRequest(messages = emptyList(), model = "some-model")

    /**
     * `validate()` is suspend by contract — probes may eventually need the
     * network — but every case here is decided before any request, so running
     * it on this thread is both correct and the whole point: the rules under
     * test are the offline ones.
     */
    private fun statusOf(id: ProviderId, key: () -> String?): ProviderStatus =
        runBlocking { adapterFor(id, key).validate() }

    // --- wiring ------------------------------------------------------------

    @Test
    fun `the named set is exactly the providers that speak the openai protocol`() {
        assertEquals(
            setOf(
                ProviderId.DEEPSEEK,
                ProviderId.GROQ,
                ProviderId.MISTRAL,
                ProviderId.XAI,
                ProviderId.OPENROUTER
            ),
            NAMED_PROVIDER_BASE_URLS.keys
        )
    }

    @Test
    fun `every provider is adapter-backed`() {
        // If someone adds an enum entry without wiring it, this fails — which
        // is the point: an unwired provider would otherwise surface as a row
        // in Settings that cannot be filled in, or as an endpoint in the
        // picker that never responds.
        //
        // Claude and Gemini used to be the expected two. They have adapters of
        // their own now, so the set is empty and any new gap is a failure
        // again rather than the documented state.
        val wired = NAMED_PROVIDER_BASE_URLS.keys + setOf(
            ProviderId.OPENAI,
            ProviderId.CUSTOM,
            ProviderId.CLAUDE,
            ProviderId.GEMINI,
        )
        assertEquals(emptySet<ProviderId>(), ProviderId.entries.toSet() - wired)
    }

    @Test
    fun `every base url is https and carries no path stream would append again`() {
        NAMED_PROVIDER_BASE_URLS.forEach { (id, url) ->
            assertTrue("$id: $url", url.startsWith("https://"))
            assertTrue("$id must not end in a slash: $url", !url.endsWith("/"))
            // openAiCompatibleStream appends this itself; a base that already
            // had it would post to /chat/completions/chat/completions.
            assertTrue("$id already carries the path: $url", !url.contains("/chat/completions"))
        }
    }

    @Test
    fun `deepseek is called at the path its own docs document`() {
        // Its documented curl posts to https://api.deepseek.com/chat/completions
        // with no /v1 — inventing one would have produced 404s that read to the
        // user as a bad key.
        val url = NAMED_PROVIDER_BASE_URLS.getValue(ProviderId.DEEPSEEK)
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            url.trimEnd('/') + "/chat/completions"
        )
    }

    // --- validation --------------------------------------------------------

    @Test
    fun `an unset key reports the provider as missing rather than broken`() {
        val status = statusOf(ProviderId.GROQ) { null }

        assertTrue("was: $status", status is ProviderStatus.MissingKey)
        assertTrue("must name the provider: ${status.reason}", status.reason.contains("Groq"))
    }

    @Test
    fun `a blank key is treated as no key rather than as a real one`() {
        // CredentialStore strips blanks, but a provider that believed it had a
        // key would send an unauthenticated request and earn a bare 401.
        assertTrue(statusOf(ProviderId.MISTRAL) { "   " } is ProviderStatus.MissingKey)
    }

    @Test
    fun `a key in its own box is ready, not a foreign key`() {
        val status = statusOf(ProviderId.OPENROUTER) { "sk-or-v1-abcdef123456" }

        assertTrue("was: $status", status is ProviderStatus.Ready)
    }

    @Test
    fun `a key from another provider names that provider`() {
        val status = statusOf(ProviderId.DEEPSEEK) { "gsk_abcdef123456" }

        assertTrue("was: $status", status is ProviderStatus.Misconfigured)
        assertTrue("must name Groq: ${status.reason}", status.reason.contains("Groq"))
    }

    // --- sending -----------------------------------------------------------

    @Test
    fun `sending without a key fails before any request leaves the device`() {
        val adapter = adapterFor(ProviderId.XAI) { null }

        // chatStream throws eagerly rather than inside the flow, so the user
        // sees "key not configured" instead of a request that goes out with no
        // Authorization header and comes back as a bare 401.
        val thrown = assertThrows(IllegalStateException::class.java) {
            adapter.chatStream(request)
        }
        assertTrue("message was: ${thrown.message}", thrown.message!!.contains("xAI"))
        assertTrue("should say where to fix it: ${thrown.message}", thrown.message!!.contains("Settings"))
    }

    // --- defaults ----------------------------------------------------------

    @Test
    fun `a named provider's default model is a real id, never the placeholder`() {
        // "default" is the app's own marker for "unset" and is rejected by every
        // real server — CUSTOM returns it deliberately, the named providers must
        // not, because theirs goes on the wire on the first send before the
        // picker has ever been opened.
        NAMED_PROVIDER_BASE_URLS.keys.forEach { id ->
            val model = defaultModelFor(id)
            assertTrue("$id defaulted to the placeholder", model != "default")
            assertTrue("$id defaulted to a blank model", model.isNotBlank())
        }
    }

    @Test
    fun `only the custom endpoint keeps the unset placeholder`() {
        // The placeholder is refused by every real server, so this is the one
        // provider allowed to keep it: CUSTOM has no model of its own to name,
        // and `resolveCustomModel` substitutes the model the user typed or
        // refuses to send.
        //
        // Claude and Gemini returned "default" while they had no adapter. They
        // do now, so their first send needs a real id — a placeholder would
        // reach the API and earn an HTTP 400 the user could not interpret.
        assertEquals("default", defaultModelFor(ProviderId.CUSTOM))

        ProviderId.entries.filter { it != ProviderId.CUSTOM }.forEach { id ->
            val model = defaultModelFor(id)
            assertTrue("$id defaulted to the placeholder", model != "default")
            assertTrue("$id defaulted to a blank model", model.isNotBlank())
        }

        assertEquals("claude-opus-5", defaultModelFor(ProviderId.CLAUDE))
        assertEquals("gemini-3.7-flash", defaultModelFor(ProviderId.GEMINI))
    }
}
