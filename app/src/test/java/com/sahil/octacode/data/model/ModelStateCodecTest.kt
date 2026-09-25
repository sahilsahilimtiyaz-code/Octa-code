package com.sahil.octacode.data.model

import com.sahil.octacode.core.model.ModelUserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStateCodecTest {

    @Test
    fun `round trip keeps both halves of a model's state`() {
        val states = mapOf(
            "gpt-4o-mini" to ModelUserState(
                modelId = "gpt-4o-mini",
                isFavorite = true,
                lastUsedAt = 1_700_000_000_000L,
            ),
            "gpt-4o" to ModelUserState(modelId = "gpt-4o"),
        )

        assertEquals(states, ModelStateCodec.decode(ModelStateCodec.encode(states)))
    }

    @Test
    fun `empty state encodes to nothing rather than to placeholders`() {
        assertTrue(ModelStateCodec.encode(emptyMap()).isEmpty())
        // An explicit type: decode's `Map<String, *>` parameter leaves nothing
        // for the compiler to infer from an untyped emptyMap.
        assertTrue(ModelStateCodec.decode(emptyMap<String, Any?>()).isEmpty())
    }

    @Test
    fun `a favorite with no timestamp does not write a used key`() {
        val encoded = ModelStateCodec.encode(
            mapOf("gpt-4o" to ModelUserState(modelId = "gpt-4o", isFavorite = true))
        )

        assertEquals(setOf("favorite|gpt-4o"), encoded.keys)
        val decoded = ModelStateCodec.decode(encoded)
        assertTrue(decoded.getValue("gpt-4o").isFavorite)
        assertNull("a favorite must not invent a last-used time",
            decoded.getValue("gpt-4o").lastUsedAt)
    }

    @Test
    fun `an unfavorite is stored, not omitted`() {
        // If false were dropped, clearing a favorite would leave the old key
        // from a previous write looking like it never changed.
        val encoded = ModelStateCodec.encode(
            mapOf("gpt-4o" to ModelUserState(modelId = "gpt-4o", isFavorite = false))
        )
        assertEquals(false, encoded["favorite|gpt-4o"])
        assertFalse(ModelStateCodec.decode(encoded).getValue("gpt-4o").isFavorite)
    }

    @Test
    fun `the two halves arrive in either order and still merge`() {
        // SharedPreferences enumerates keys in whatever order it likes; the
        // timestamp and the favorite are written under separate keys, so one
        // must not overwrite the other when it is applied second.
        val favoriteFirst = ModelStateCodec.decode(
            mapOf("favorite|x" to true, "used|x" to 42L)
        )
        val usedFirst = ModelStateCodec.decode(
            mapOf("used|x" to 42L, "favorite|x" to true)
        )

        assertEquals(favoriteFirst, usedFirst)
        val merged = favoriteFirst.getValue("x")
        assertTrue(merged.isFavorite)
        assertEquals(42L, merged.lastUsedAt)
    }

    @Test
    fun `model ids containing separators survive`() {
        // OpenRouter ids look like "openai/gpt-4o-mini"; a codec that split on
        // the prefix without taking the rest whole would shred these.
        val id = "openai/gpt-4o-mini"
        val states = mapOf(
            id to ModelUserState(modelId = id, isFavorite = true, lastUsedAt = 7L)
        )

        assertEquals(states, ModelStateCodec.decode(ModelStateCodec.encode(states)))
    }

    @Test
    fun `debris in the preferences file is skipped, not thrown on`() {
        val decoded = ModelStateCodec.decode(
            mapOf(
                "favorite|gpt-4o" to true,
                // Wrong type for its key, and a key we never wrote.
                "used|gpt-4o" to "not a timestamp",
                "unrelated_key" to "something else",
                "favorite|bad-type" to 17,
            )
        )

        assertEquals(
            mapOf("gpt-4o" to ModelUserState(modelId = "gpt-4o", isFavorite = true)),
            decoded,
        )
        // "used|gpt-4o" was unreadable, so that model must not gain a time.
        assertNull(decoded.getValue("gpt-4o").lastUsedAt)
    }

    @Test
    fun `encode is stable for the same input`() {
        // write() clears and rewrites from this map on every change, so a
        // non-deterministic encoding would produce churn for no reason.
        val states = mapOf(
            "a" to ModelUserState(modelId = "a", isFavorite = true, lastUsedAt = 9L),
            "b" to ModelUserState(modelId = "b"),
        )

        assertEquals(ModelStateCodec.encode(states), ModelStateCodec.encode(states))
    }
}
