package com.sahil.octacode.core.model

import com.sahil.octacode.core.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selector's value is entirely in its grouping rules: Recent on top,
 * every model browsable under its provider, Favorites at the bottom, and
 * search that never silently empties the list. These pin that structure so a
 * later refactor cannot quietly reorder or de-duplicate it.
 */
class ModelSectioningTest {

    private val gpt4o = model("gpt-4o", "GPT-4o")
    private val gpt4oMini = model("gpt-4o-mini", "GPT-4o mini")
    private val o3 = model("o3", "o3")
    private val geminiPro = model("gemini-pro", "Gemini 1.5 Pro", provider = "Google", adapter = ProviderId.GEMINI)

    private val catalog = listOf(gpt4o, gpt4oMini, o3, geminiPro)

    // --- section order -------------------------------------------------------

    @Test
    fun `recent comes first, providers next, favorites last`() {
        val sections = ModelSectioning.build(
            models = catalog,
            states = mapOf(
                gpt4o.id to ModelUserState(gpt4o.id, isFavorite = true, lastUsedAt = 1_000L),
                geminiPro.id to ModelUserState(geminiPro.id, lastUsedAt = 2_000L),
            ),
        )

        assertEquals(listOf(SectionKind.RECENT, SectionKind.PROVIDER, SectionKind.PROVIDER, SectionKind.FAVORITE),
            sections.map { it.kind })
        assertEquals("Recent", sections.first().title)
        assertEquals("Favorites", sections.last().title)
    }

    @Test
    fun `no recents or favorites means provider sections only`() {
        val sections = ModelSectioning.build(models = catalog)

        assertTrue(sections.all { it.kind == SectionKind.PROVIDER })
        assertEquals(listOf("Google", "OpenAI"), sections.map { it.title })
    }

    // --- provider groups stay complete ---------------------------------------

    @Test
    fun `a provider group lists every model even when some are in recent or favorites`() {
        val sections = ModelSectioning.build(
            models = catalog,
            states = mapOf(gpt4o.id to ModelUserState(gpt4o.id, isFavorite = true, lastUsedAt = 5L)),
        )

        val openAi = sections.first { it.title == "OpenAI" }
        assertEquals(3, openAi.models.size)
        assertEquals(listOf("GPT-4o", "GPT-4o mini", "o3"), openAi.models.map { it.model.displayName })
    }

    @Test
    fun `provider groups are sorted by name so the list has a stable spine`() {
        val sections = ModelSectioning.build(models = catalog)
        assertEquals(listOf("Google", "OpenAI"), sections.map { it.title })
    }

    // --- recent ---------------------------------------------------------------

    @Test
    fun `recent is ordered by last used and capped at the shortcut limit`() {
        val many = (1..9).map { model("m$it", "Model $it") }
        val states = many.associate {
            it.id to ModelUserState(it.id, lastUsedAt = it.id.removePrefix("m").toLong())
        }

        val recent = ModelSectioning.build(models = many, states = states)
            .first { it.kind == SectionKind.RECENT }

        assertEquals(ModelSectioning.RECENT_LIMIT, recent.models.size)
        assertEquals("Model 9", recent.models.first().model.displayName)
        assertEquals("Model 5", recent.models.last().model.displayName)
    }

    @Test
    fun `a model with no user state never enters recent`() {
        val sections = ModelSectioning.build(models = catalog)
        assertFalse(sections.any { it.kind == SectionKind.RECENT })
    }

    // --- favorites -------------------------------------------------------------

    @Test
    fun `favorites are listed by name and may also appear in recent`() {
        val states = mapOf(
            gpt4oMini.id to ModelUserState(gpt4oMini.id, isFavorite = true, lastUsedAt = 9L),
            gpt4o.id to ModelUserState(gpt4o.id, isFavorite = true, lastUsedAt = 1L),
        )

        val sections = ModelSectioning.build(models = catalog, states = states)
        val favorites = sections.first { it.kind == SectionKind.FAVORITE }

        assertEquals(listOf("GPT-4o", "GPT-4o mini"), favorites.models.map { it.model.displayName })
        assertEquals(
            "a favorite with history belongs in Recent too",
            2,
            sections.first { it.kind == SectionKind.RECENT }.models.size,
        )
    }

    // --- search ----------------------------------------------------------------

    @Test
    fun `search matches display name wire id and provider`() {
        assertEquals(
            "substring matching means 'mini' would also hit 'gemini', so use a discriminating query",
            listOf("GPT-4o mini"),
            ModelSectioning.build(models = catalog, query = "gpt-4o mini").flatMap { it.models }
                .map { it.model.displayName },
        )
        assertEquals(
            "the wire id must be searchable even when it does not appear in the display name",
            listOf("Gemini 1.5 Pro"),
            ModelSectioning.build(models = catalog, query = "gemini-pro").flatMap { it.models }
                .map { it.model.displayName },
        )
        assertEquals(
            listOf("Google"),
            ModelSectioning.build(models = catalog, query = "google").map { it.title },
        )
    }

    @Test
    fun `search is case insensitive and tolerates surrounding whitespace`() {
        val sections = ModelSectioning.build(models = catalog, query = "  GPT-4O MINI  ")
        assertEquals(listOf("GPT-4o mini"), sections.flatMap { it.models }.map { it.model.displayName })
    }

    @Test
    fun `a search with no hits yields no sections so the screen can show an empty state`() {
        val sections = ModelSectioning.build(models = catalog, query = "model-that-does-not-exist")
        assertTrue(sections.isEmpty())
        assertTrue(ModelSectioning.isEmptyAfterSearch(models = catalog, query = "model-that-does-not-exist"))
    }

    @Test
    fun `a non-empty catalog with a blank query is never reported as empty`() {
        assertFalse(ModelSectioning.isEmptyAfterSearch(models = catalog, query = ""))
        assertTrue(catalog.isNotEmpty())
    }

    @Test
    fun `an empty catalog reports empty only through the screens own path`() {
        // Zero models is "no catalog yet", not "search failed" — the caller
        // distinguishes them so it never claims the user's query was wrong.
        assertFalse(ModelSectioning.isEmptyAfterSearch(models = emptyList(), query = "anything"))
        assertTrue(ModelSectioning.build(models = emptyList()).isEmpty())
    }

    // --- selection --------------------------------------------------------------

    @Test
    fun `selection is flagged on every row that shows it including duplicates`() {
        val states = mapOf(gpt4o.id to ModelUserState(gpt4o.id, isFavorite = true, lastUsedAt = 3L))
        val sections = ModelSectioning.build(
            models = catalog,
            states = states,
            selectedModelId = gpt4o.id,
        )

        val flagged = sections.flatMap { it.models }.count { it.isSelected }
        // Recent + provider group + Favorites — three places must agree.
        assertEquals(3, flagged)
        sections.flatMap { it.models }.forEach { row ->
            assertEquals(row.model.id == gpt4o.id, row.isSelected)
        }
    }

    @Test
    fun `a model with no state still renders with a default state rather than null`() {
        val row = ModelSectioning.build(models = catalog)
            .first { it.title == "OpenAI" }
            .models.first { it.model.id == o3.id }

        assertEquals(ModelUserState(modelId = o3.id), row.state)
        assertFalse(row.state.isFavorite)
    }

    // --- fixture -----------------------------------------------------------------

    private fun model(
        id: String,
        displayName: String,
        provider: String = "OpenAI",
        adapter: ProviderId = ProviderId.OPENAI,
    ) = ModelDef(
        id = id,
        displayName = displayName,
        provider = provider,
        adapter = adapter,
    )
}
