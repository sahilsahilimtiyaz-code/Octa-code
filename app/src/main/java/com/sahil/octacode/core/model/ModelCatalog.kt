package com.sahil.octacode.core.model

import com.sahil.octacode.core.provider.ProviderId

/**
 * The models this build can offer without first talking to a provider.
 *
 * A bundled list rather than a fetched one: `AiProvider.validate()` is
 * documented to probe *configuration* with no network side effects, so nothing
 * here claims to have heard from an endpoint today. The ids are the stable
 * public wire ids of each family — if a provider later retires one, the
 * request fails and `chatStream` surfaces that error, which is the behaviour
 * this project wants rather than a silently missing row.
 *
 * [ModelDef.contextWindow] is deliberately null on every entry. It is the one
 * field whose own docs say guessing it produces "a number the UI presents as
 * fact", and we have not asked an endpoint.
 *
 * Only adapters whose model space we can state are represented. The custom
 * endpoint is omitted on purpose: what its server will accept is whatever the
 * user configured, so that entry is contributed at runtime from the stored
 * model instead of being guessed at here. Claude and Gemini are unavailable in
 * this build, so listing their models would offer rows that cannot be reached.
 */
object ModelCatalog {

    val bundled: List<ModelDef> = listOf(
        ModelDef(
            id = "gpt-4o-mini",
            displayName = "GPT-4o mini",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Small, fast, cheapest of the 4o line.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.VISION,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "gpt-4o",
            displayName = "GPT-4o",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "General purpose, strongest of the 4o line.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.VISION,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "gpt-4.1-mini",
            displayName = "GPT-4.1 mini",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Long-context line at a small price.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.VISION,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Long-context line, largest of the three.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.VISION,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "gpt-4-turbo",
            displayName = "GPT-4 Turbo",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Earlier GPT-4 revision, still widely available.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.VISION,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "o3-mini",
            displayName = "o3 mini",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Reasons before answering; slower, better at hard problems.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.THINKING,
                ModelCapability.STREAMING,
            ),
        ),
        ModelDef(
            id = "gpt-3.5-turbo",
            displayName = "GPT-3.5 Turbo",
            provider = "OpenAI",
            adapter = ProviderId.OPENAI,
            description = "Oldest line still served; no image input.",
            capabilities = setOf(
                ModelCapability.TOOLS,
                ModelCapability.STREAMING,
            ),
        ),
    )

    /** The subset carried by [adapter], which is what a provider tab shows. */
    fun forAdapter(adapter: ProviderId): List<ModelDef> =
        bundled.filter { it.adapter == adapter }

    fun byId(id: String): ModelDef? = bundled.firstOrNull { it.id == id }
}
