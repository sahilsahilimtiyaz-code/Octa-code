package com.sahil.octacode.data.model

import com.sahil.octacode.core.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the layout of the fetched-models preferences file.
 *
 * The rule being protected: one key per provider, holding ids only. A codec
 * that quietly wrote something else would still round-trip today and stop
 * tomorrow, which is exactly the failure this format exists to avoid.
 */
class FetchedModelsCodecTest {

    @Test
    fun `an entry decodes back to models carrying their provider`() {
        val (key, value) = FetchedModelsCodec.entry(ProviderId.GROQ, listOf("a", "b"))
        val decoded = FetchedModelsCodec.decode(mapOf(key to value))

        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals("Groq", decoded.first().provider)
        assertEquals(ProviderId.GROQ, decoded.first().adapter)
    }

    @Test
    fun `the display name is the id, not an invented prettier one`() {
        // `/models` supplies no name, and inventing one means inventing one
        // that may not match what the user sees on the provider's console.
        val (key, value) =
            FetchedModelsCodec.entry(ProviderId.OPENROUTER, listOf("openai/gpt-4o-mini"))
        val model = FetchedModelsCodec.decode(mapOf(key to value)).single()

        assertEquals("openai/gpt-4o-mini", model.displayName)
        assertEquals("OpenRouter", model.provider)
    }

    @Test
    fun `blank and repeated ids are dropped on the way in`() {
        val (key, value) = FetchedModelsCodec.entry(
            ProviderId.MISTRAL,
            listOf("  one  ", "one", "", "   ", "two")
        )

        assertEquals(listOf("one", "two"), value.split('\n'))
        assertEquals(
            listOf("one", "two"),
            FetchedModelsCodec.decode(mapOf(key to value)).map { it.id }
        )
    }

    @Test
    fun `keys written by anything else are left alone`() {
        val (key, value) = FetchedModelsCodec.entry(ProviderId.DEEPSEEK, listOf("deepseek-v4-flash"))
        val decoded = FetchedModelsCodec.decode(
            mapOf(
                key to value,
                // Another codec's prefix, in the same class of file.
                "favorite|gpt-4o" to true,
                "unrelated" to "nope",
            )
        )

        assertEquals(listOf("deepseek-v4-flash"), decoded.map { it.id })
    }

    @Test
    fun `a value of the wrong type costs that provider only`() {
        // One bad entry must not take the whole file with it — a codec that
        // threw here would read as "every provider lost its models".
        val (key, value) = FetchedModelsCodec.entry(ProviderId.GROQ, listOf("llama-x"))
        val wrongTypeKey = FetchedModelsCodec.entry(ProviderId.XAI, listOf("ignored")).first

        val decoded = FetchedModelsCodec.decode(mapOf(key to value, wrongTypeKey to 42))

        assertEquals(listOf("llama-x"), decoded.map { it.id })
    }

    @Test
    fun `providers decode in enum order so a shared id resolves the same way every time`() {
        // A preferences map has no order of its own, so decoding iterates the
        // enum: without that, two providers offering the same id would take
        // turns being the one that answers for it.
        val groq = FetchedModelsCodec.entry(ProviderId.GROQ, listOf("shared"))
        val mistral = FetchedModelsCodec.entry(ProviderId.MISTRAL, listOf("shared"))
        val decoded = FetchedModelsCodec.decode(
            mapOf(mistral.first to mistral.second, groq.first to groq.second)
        )

        assertEquals(listOf(ProviderId.GROQ, ProviderId.MISTRAL), decoded.map { it.adapter })
    }

    @Test
    fun `an empty file decodes to nothing rather than to defaults`() {
        assertTrue(FetchedModelsCodec.decode(emptyMap<String, Any?>()).isEmpty())
    }

    @Test
    fun `nothing is guessed about a model the endpoint did not describe`() {
        // `/models` returns ids and nothing else. Every capability badge or
        // context window here would be a number and a claim the UI presents
        // as fact, having never been told either.
        val (key, value) = FetchedModelsCodec.entry(ProviderId.XAI, listOf("grok-4.6"))
        val model = FetchedModelsCodec.decode(mapOf(key to value)).single()

        assertEquals("", model.description)
        assertTrue(model.capabilities.isEmpty())
        assertFalse(model.isFree)
        assertFalse(model.isLocal)
        assertFalse(model.supportsThinking)
        assertFalse(model.supportsTools)
        assertFalse(model.supportsVision)
        assertNull(model.contextWindow)
    }
}
