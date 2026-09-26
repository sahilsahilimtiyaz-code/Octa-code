package com.sahil.octacode.data.providers

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [explainSendFailure] is literally the text a person reads when a send dies —
 * the user's report was that it said "failed some error" — so it is held to
 * two properties: every branch must name a next step, and no branch may echo
 * credentials back into a conversation that gets stored.
 */
class SendFailureTest {

    private fun explain(t: Throwable, model: String? = "gpt-4o") =
        explainSendFailure(t, "OpenAI", model)

    // --- HTTP classification -------------------------------------------------

    @Test
    fun `401 names the key and says where to fix it`() {
        val out = explain(ProviderHttpException(401, "HTTP 401 from https://api.openai.com/v1"))
        assertTrue("was: $out", out.contains("API key"))
        assertTrue("was: $out", out.contains("Settings → Providers"))
        assertTrue("status should be visible: $out", out.contains("401"))
    }

    @Test
    fun `401 never echoes a body that quotes the presented key`() {
        // Real providers put part of the key back in the error body. That text
        // is written into the conversation and persisted, so it must not travel.
        val leaky = "Incorrect API key provided: sk-abcdef-super-secret"
        val out = explain(ProviderHttpException(401, "HTTP 401 from https://api.openai.com/v1: $leaky"))
        assertFalse("leaked the key fragment: $out", out.contains("sk-abcdef"))
        assertFalse("leaked the raw body: $out", out.contains("super-secret"))
    }

    @Test
    fun `402 says the account is out of credit`() {
        val out = explain(ProviderHttpException(402, "HTTP 402 from https://api.openai.com/v1"))
        assertTrue("was: $out", out.contains("credit"))
        assertTrue("was: $out", out.contains("402"))
    }

    @Test
    fun `403 names the model it was refused for`() {
        val out = explain(ProviderHttpException(403, "HTTP 403 from https://api.openai.com/v1"))
        assertTrue("was: $out", out.contains("\"gpt-4o\""))
        assertTrue("was: $out", out.contains("403"))
    }

    @Test
    fun `404 points at the base URL as well as the model`() {
        val out = explain(ProviderHttpException(404, "HTTP 404 from https://wrong.example/v1"))
        assertTrue("was: $out", out.contains("base URL"))
        assertTrue("was: $out", out.contains("404"))
    }

    @Test
    fun `429 says to wait rather than only reporting the code`() {
        val out = explain(ProviderHttpException(429, "HTTP 429 from https://api.openai.com/v1"))
        assertTrue("was: $out", out.contains("rate-limit"))
        assertTrue("was: $out", out.contains("moment"))
    }

    @Test
    fun `5xx blames the provider rather than the user`() {
        val out = explain(ProviderHttpException(503, "HTTP 503 from https://api.openai.com/v1"))
        assertTrue("was: $out", out.contains("having trouble"))
        assertTrue("was: $out", out.contains("503"))
    }

    @Test
    fun `an unclassified status forwards the provider's own detail`() {
        val out = explain(ProviderHttpException(418, "HTTP 418 from https://api.example/v1: teapot says no"))
        assertTrue("detail was dropped: $out", out.contains("teapot says no"))
        assertTrue("was: $out", out.contains("418"))
    }

    @Test
    fun `an unclassified status without detail still reads as a sentence`() {
        val out = explain(ProviderHttpException(418, "HTTP 418 from https://api.example/v1"))
        assertTrue("was: $out", out.endsWith("."))
        assertFalse("dangling separator: $out", out.contains(": ."))
        assertFalse("left the raw form in place: $out", out.contains("https://"))
    }

    @Test
    fun `an HTML error page is not forwarded as prose`() {
        val out = explain(ProviderHttpException(418, "HTTP 418 from https://api.example/v1: <html><body>502</body></html>"))
        assertFalse("forwarded markup: $out", out.contains("<html>"))
        assertTrue("was: $out", out.contains("418"))
    }

    // --- transport failures --------------------------------------------------

    @Test
    fun `an unresolvable host reads as offline, not as a raw exception`() {
        val out = explain(UnknownHostException("api.openai.com"))
        assertTrue("was: $out", out.contains("offline"))
        assertFalse("was a raw exception string: $out", out.contains("api.openai.com"))
    }

    @Test
    fun `a timeout is distinguished from a refusal to connect`() {
        val out = explain(SocketTimeoutException("timeout"))
        assertTrue("was: $out", out.contains("did not answer in time"))
    }

    @Test
    fun `a refused connection points at the custom base URL too`() {
        val out = explain(ConnectException("Connection refused"))
        assertTrue("was: $out", out.contains("Settings → Providers"))
    }

    @Test
    fun `a TLS failure suggests checking for https`() {
        val out = explain(SSLException("handshake failed"))
        assertTrue("was: $out", out.contains("https://"))
    }

    @Test
    fun `an unreadable reply is explained as a likely error page`() {
        val out = explain(SerializationException("Unexpected JSON token at offset 0"))
        assertTrue("was: $out", out.contains("could not read"))
        assertTrue("detail should survive: $out", out.contains("Unexpected JSON token"))
    }

    @Test
    fun `a plain IO failure still names the provider`() {
        val out = explain(IOException("Broken pipe"))
        assertTrue("was: $out", out.contains("OpenAI"))
        assertTrue("was: $out", out.contains("Broken pipe"))
    }

    // --- ordering and fallbacks ---------------------------------------------
    // UnknownHostException/ConnectException/SocketTimeoutException/SSLException
    // are all IOExceptions: if the general branch were tested first, every one
    // of them would collapse into the vague "Network problem" message.

    @Test
    fun `specific transport types are not swallowed by the general IO branch`() {
        listOf(
            UnknownHostException("h") to "offline",
            SocketTimeoutException("t") to "did not answer in time",
            ConnectException("c") to "Settings → Providers",
            SSLException("s") to "https://"
        ).forEach { (t, expected) ->
            val out = explain(t)
            assertTrue(
                "${t::class.java.simpleName} fell through to the generic branch: $out",
                out.contains(expected)
            )
        }
    }

    @Test
    fun `an adapter pre-flight message passes through untouched`() {
        // "no API key configured" already says exactly what is missing; wrapping
        // it in our own sentence would only make it longer and less specific.
        val out = explain(IllegalStateException("no API key configured for OpenAI"))
        assertTrue(out.contains("no API key configured"))
    }

    @Test
    fun `a message-less exception still produces readable text`() {
        val out = explain(RuntimeException())
        assertTrue("empty output", out.isNotBlank())
    }

    @Test
    fun `a blank message falls back to the class name`() {
        val out = explain(RuntimeException("   "))
        assertTrue("was: $out", out.contains("RuntimeException"))
    }

    @Test
    fun `a null model id reads as the selected model, never as null`() {
        val out = explain(ProviderHttpException(403, "HTTP 403 from https://x/v1"), model = null)
        assertTrue("was: $out", out.contains("the selected model"))
        assertFalse("leaked the null: $out", out.contains("null"))
    }

    // --- failures while listing models --------------------------------------

    @Test
    fun `a 403 while listing talks about the list, not about a model nobody picked`() {
        // explainSendFailure's 403 ends with "or pick another model". On a
        // request that only asked what was available, no model was chosen —
        // that instruction would send the user off to do something irrelevant.
        val out = explainFetchFailure(
            ProviderHttpException(403, "HTTP 403 from https://api.example/v1"),
            "Groq"
        )

        assertTrue("was: $out", out.contains("model list"))
        assertFalse("reached for a model nobody picked: $out", out.contains("pick another model"))
        assertFalse("must not cite a selection: $out", out.contains("the selected model"))
    }

    @Test
    fun `a 404 while listing does not send the reader to a control that does not exist`() {
        // Every URL that can produce this is set by this build — the custom
        // endpoint has no list to fetch and never appears here. The earlier
        // wording told readers to "check the base URL", a field only the
        // custom endpoint has, which would have left them searching for
        // something to correct and finding nothing. What still works is the
        // fact worth giving them instead.
        val out = explainFetchFailure(
            ProviderHttpException(404, "HTTP 404 from https://wrong.example/v1"),
            "Mistral"
        )

        assertTrue("was: $out", out.contains("no model list"))
        assertFalse("sent the reader to a field that is not there: $out", out.contains("base URL"))
        assertFalse("reached for a model: $out", out.contains("pick another model"))
        assertTrue("should say what still works: $out", out.contains("default model"))
    }

    @Test
    fun `transport failures say the same thing whichever way they were going`() {
        // Delegated rather than rewritten: an unreachable host is an
        // unreachable host, and duplicating the wording would give it two
        // places to drift apart in.
        val out = explainFetchFailure(UnknownHostException("api.groq.com"), "Groq")

        assertTrue("was: $out", out.contains("offline"))
        assertFalse("leaked the host: $out", out.contains("api.groq.com"))
    }

    @Test
    fun `an unclassified status still carries what the provider said`() {
        val out = explainFetchFailure(
            ProviderHttpException(418, "HTTP 418 from https://api.example/v1: teapot says no"),
            "xAI"
        )

        assertTrue("detail dropped: $out", out.contains("teapot says no"))
        assertTrue("was: $out", out.contains("418"))
    }
}
