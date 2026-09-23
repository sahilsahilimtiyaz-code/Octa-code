package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// M4b pure-logic tests for status copy (no Compose needed).
class ChatStatusTextTest {

    @Test
    fun `null status probes honestly`() {
        val t = agentCardText(ProviderId.OPENAI, null)
        assertEquals("Agent unavailable", t.title)
        assertFalse(t.ready)
    }

    @Test
    fun `ready status names provider`() {
        val t = agentCardText(ProviderId.OPENAI, ProviderStatus.Ready("key present"))
        assertEquals("Agent ready", t.title)
        assertTrue(t.ready)
        assertTrue(t.body.contains("OpenAI"))
    }

    @Test
    fun `missing key keeps unavailable copy`() {
        val t = agentCardText(ProviderId.OPENAI, ProviderStatus.MissingKey("no key"))
        assertEquals("Agent unavailable", t.title)
        assertTrue(t.body.contains("Configure Model & Provider"))
    }

    @Test
    fun `model slot falls back honestly`() {
        assertEquals(
            "Model unavailable",
            modelSlotLabel(ProviderId.CLAUDE, ProviderStatus.Unavailable("not implemented"))
        )
        assertEquals(
            "Custom endpoint",
            modelSlotLabel(ProviderId.CUSTOM, ProviderStatus.Ready("ok"))
        )
    }

    @Test
    fun `composer helper guides when offline`() {
        assertTrue(composerHelperText(null).contains("Connect a provider"))
        assertTrue(composerHelperText(ProviderStatus.Ready("ok")).contains("Streams live"))
    }
}
