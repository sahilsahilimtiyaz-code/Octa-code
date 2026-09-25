package com.sahil.octacode.core.model

/**
 * A rendered section of the model selector.
 *
 * Ordering follows the structure the product calls for: [SectionKind.RECENT]
 * first as a shortcut, provider groups in the middle as the complete listing,
 * [SectionKind.FAVORITE] last.
 */
data class ModelSection(
    val title: String,
    val kind: SectionKind,
    /** Complete for PROVIDER sections — a provider header can show the count. */
    val models: List<ModelRow>,
)

enum class SectionKind { RECENT, PROVIDER, FAVORITE }

/** One selectable line: the catalog entry plus this user's state on it. */
data class ModelRow(
    val model: ModelDef,
    val state: ModelUserState,
    val isSelected: Boolean,
)

/**
 * Turns a flat model list into the grouped, filtered, searchable sections the
 * selector renders. Pure — no Context, no clock, no repository — so the
 * ordering rules can be pinned by unit tests instead of judged by eye.
 *
 * Recent and Favorites are *shortcuts that deliberately repeat* entries also
 * shown under their provider; provider groups are never reduced to compensate.
 * That is what keeps "every model is browsable in exactly one place" true
 * while still surfacing the three you reach for most at the top and bottom.
 */
object ModelSectioning {

    /** How many recently-used models the Recent shortcut surfaces. */
    const val RECENT_LIMIT = 5

    /**
     * @param query matched case-insensitively against display name, wire id and
     *   provider name. Blank shows everything.
     * @param states per-model favorites and recents; a model with no entry is
     *   neither, and still appears in its provider group.
     */
    fun build(
        models: List<ModelDef>,
        states: Map<String, ModelUserState> = emptyMap(),
        selectedModelId: String? = null,
        query: String = "",
    ): List<ModelSection> {
        val rows = models.filter { it.matches(query) }.map { it.toRow(states, selectedModelId) }
        if (rows.isEmpty()) return emptyList()

        val recent = rows
            .mapNotNull { row -> states[row.model.id]?.lastUsedAt?.let { row to it } }
            .sortedByDescending { (_, lastUsedAt) -> lastUsedAt }
            .map { (row, _) -> row }
            .take(RECENT_LIMIT)

        val favorites = rows
            .filter { states[it.model.id]?.isFavorite == true }
            .sortedBy { it.model.displayName }

        val providers = rows
            .groupBy { it.model.provider }
            .toSortedMap()
            .map { (provider, group) ->
                ModelSection(
                    title = provider,
                    kind = SectionKind.PROVIDER,
                    models = group.sortedBy { it.model.displayName },
                )
            }

        return buildList {
            if (recent.isNotEmpty()) {
                add(ModelSection(title = "Recent", kind = SectionKind.RECENT, models = recent))
            }
            addAll(providers)
            if (favorites.isNotEmpty()) {
                add(ModelSection(title = "Favorites", kind = SectionKind.FAVORITE, models = favorites))
            }
        }
    }

    /** True when the whole list filtered away — the UI shows an empty state. */
    fun isEmptyAfterSearch(
        models: List<ModelDef>,
        query: String,
    ): Boolean = models.isNotEmpty() && models.none { it.matches(query) }

    private fun ModelDef.matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return displayName.lowercase().contains(needle) ||
            id.lowercase().contains(needle) ||
            provider.lowercase().contains(needle)
    }

    private fun ModelDef.toRow(
        states: Map<String, ModelUserState>,
        selectedModelId: String?,
    ) = ModelRow(
        model = this,
        state = states[id] ?: ModelUserState(modelId = id),
        isSelected = id == selectedModelId,
    )
}
