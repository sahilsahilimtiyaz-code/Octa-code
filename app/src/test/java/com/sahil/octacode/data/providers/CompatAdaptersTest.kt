package com.sahil.octacode.data.providers

import com.sahil.octacode.core.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two things that made failed requests undebuggable: the provider's
 * explanation was thrown away, and a key from the wrong provider passed
 * validation. Both are pure functions, so both are pinned here.
 */
class CompatAdaptersTest {

    @Test
    fun `openai body keeps the sentence that explains the 401`() {
        val body = """{"error":{"message":"Incorrect API key provided: sk-or-v1-abc123.
            You can find your API key at https://platform.openai.com/account/api-keys.",
            "type":"invalid_request_error","param":null,"code":"invalid_api_key"}}"""

        val message = describeHttpError(401, "https://api.openai.com/v1", body)

        assertTrue(message.startsWith("HTTP 401 from https://api.openai.com/v1"))
        assertTrue(message.contains("Incorrect API key provided"))
        // Multi-line explanations are collapsed so the chat bubble stays one line.
        assertFalse(message.contains('\n'))
    }

    @Test
    fun `openrouter model error is surfaced rather than a bare status`() {
        val body = """{"error":{"message":"model is required","code":400}}"""

        assertEquals(
            "HTTP 400 from https://openrouter.ai/api/v1: model is required",
            describeHttpError(400, "https://openrouter.ai/api/v1", body)
        )
    }

    @Test
    fun `bare status is reported when the body says nothing useful`() {
        assertEquals(
            "HTTP 500 from https://api.openai.com/v1",
            describeHttpError(500, "https://api.openai.com/v1", "   ")
        )
    }

    @Test
    fun `html error pages are not passed off as the reason`() {
        assertNull(providerMessage("<html><body>502 Bad Gateway</body></html>"))
    }

    @Test
    fun `nested and top level message fields both decode`() {
        assertEquals(
            "model is required",
            providerMessage("""{"error":{"message":"model is required"}}""")
        )
        assertEquals(
            "model is required",
            providerMessage("""{"message":"model is required"}""")
        )
    }

    @Test
    fun `escaped characters in the provider message are unescaped`() {
        assertEquals(
            "line one\nline two\ttabbed é and A",
            providerMessage("""{"error":{"message":"line one\nline two\ttabbed é and A"}}""")
        )
    }

    @Test
    fun `a key from another provider is caught before the request`() {
        // Points at OpenRouter's own card now that OpenRouter has one; telling
        // the user to use Custom endpoint would send them to a worse place than
        // the box that actually exists for their key.
        assertEquals(
            "OpenRouter key detected — paste it into OpenRouter instead of OpenAI",
            foreignProviderHint("sk-or-v1-abcdef123456")
        )
        // Claude and Gemini used to have wording of their own claiming they
        // were "not implemented in this build". They have adapters now, so the
        // one unified sentence is the only thing that can be true — and it
        // names the provider by its card title, like every other owner does.
        val claudeHint = foreignProviderHint("sk-ant-api03-xyz")!!
        assertTrue("was: $claudeHint", claudeHint.contains("Claude"))
        assertFalse("still claims it is unimplemented: $claudeHint", claudeHint.contains("not implemented"))
        assertTrue(foreignProviderHint("AIzaSyExample")!!.contains("Gemini"))
    }

    @Test
    fun `a key is only called misplaced when it is actually in the wrong box`() {
        // Right box stays silent, or every Groq session would open by being
        // told its own key was a Groq key.
        assertNull(foreignProviderHint("sk-or-v1-abcdef123456", ProviderId.OPENROUTER))
        assertNull(foreignProviderHint("gsk_abcdef123456", ProviderId.GROQ))
        assertNull(foreignProviderHint("xai-abcdef123456", ProviderId.XAI))

        // Wrong box gets named, with the destination the user should use.
        assertTrue(foreignProviderHint("gsk_abcdef123456", ProviderId.DEEPSEEK)!!.contains("Groq"))
        assertTrue(foreignProviderHint("xai-abcdef123456", ProviderId.MISTRAL)!!.contains("xAI"))
        assertTrue(
            foreignProviderHint("sk-or-v1-abcdef123456", ProviderId.XAI)!!.contains("OpenRouter")
        )
    }

    @Test
    fun `genuine openai key prefixes are left alone`() {
        assertNull(foreignProviderHint("sk-proj-abcdef123456"))
        assertNull(foreignProviderHint("sk-svcacct-abcdef123456"))
        assertNull(foreignProviderHint("sk-abcdef123456"))
    }

    @Test
    fun `the default placeholder is never put on the wire`() {
        assertNull(resolveCustomModel("default", "default"))
        assertNull(resolveCustomModel("", "  "))
        assertNull(resolveCustomModel("default", ""))
        assertEquals("openai/gpt-4o-mini", resolveCustomModel("default", "openai/gpt-4o-mini"))
        assertEquals("deepseek/deepseek-chat", resolveCustomModel("deepseek/deepseek-chat", "ignored"))
    }

    private fun assertFalse(value: Boolean) = assertTrue(!value)
}
