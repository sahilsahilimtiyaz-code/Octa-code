package com.sahil.octacode.core.model

import com.sahil.octacode.core.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `wire ids are unique`() {
        // A duplicate id would make byId pick one arbitrarily and would split
        // a model's favorite across two rows in the same section.
        val ids = ModelCatalog.bundled.map { it.id }
        assertEquals("duplicate ids: ${ids.groupingBy { it }.eachCount().filterValues { it > 1 }}",
            ids.size, ids.toSet().size)
    }

    @Test
    fun `no entry uses the unset placeholder as an id`() {
        // "default" is our own marker for "no model chosen" in the custom
        // endpoint; it must never be a selectable row, because sending it is
        // exactly the thing resolveCustomModel exists to refuse.
        ModelCatalog.bundled.forEach { model ->
            assertFalse("${model.id} must not be the placeholder", model.id == "default")
            assertTrue("${model.id} must not be blank", model.id.isNotBlank())
        }
    }

    @Test
    fun `every entry names a real adapter and a display name`() {
        ModelCatalog.bundled.forEach { model ->
            assertTrue("${model.id} has no display name", model.displayName.isNotBlank())
            assertTrue(
                "${model.id} uses ${model.adapter}",
                model.adapter in listOf(ProviderId.OPENAI, ProviderId.CUSTOM),
            )
        }
    }

    @Test
    fun `provider is a grouping key the selector can sort`() {
        // ModelSectioning groups by this string and sorts the map, so it has to
        // be stable — an entry that spelled it differently would open a second
        // header for the same provider.
        val providers = ModelCatalog.bundled.map { it.provider }.toSet()
        assertEquals(setOf("OpenAI"), providers)
    }

    @Test
    fun `context windows are unknown rather than guessed`() {
        // ModelDef's own docs: guessing this value yields "a number the UI
        // presents as fact". We have not asked an endpoint, so we do not know.
        ModelCatalog.bundled.forEach { model ->
            assertNull("${model.id} reports a window", model.contextWindow)
        }
    }

    @Test
    fun `free and local flags are both off for network models`() {
        ModelCatalog.bundled.forEach { model ->
            assertFalse("${model.id} claims a free tier", model.isFree)
            assertFalse("${model.id} claims to run on-device", model.isLocal)
        }
    }

    @Test
    fun `forAdapter filters without inventing rows`() {
        val openAi = ModelCatalog.forAdapter(ProviderId.OPENAI)
        assertEquals(ModelCatalog.bundled, openAi)

        val custom = ModelCatalog.forAdapter(ProviderId.CUSTOM)
        assertTrue("the custom endpoint's model space is whatever the user configured",
            custom.isEmpty())
    }

    @Test
    fun `byId finds an entry and returns null for an unknown one`() {
        assertNotNull(ModelCatalog.byId("gpt-4o-mini"))
        assertNull(ModelCatalog.byId("model-that-does-not-exist"))
    }
}
