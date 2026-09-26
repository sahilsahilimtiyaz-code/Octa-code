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
 * Only OpenAI's model space is stated here. The remaining providers —
 * DeepSeek, Groq, Mistral, xAI, OpenRouter, Claude, Gemini — are carried as
 * *endpoints* instead: their first model comes from `ChatModelDefaults` and
 * their full list from a fetch once a key is saved for them. The custom
 * endpoint is omitted on purpose rather than by that pattern — what its server
 * will accept is whatever the user configured, so nothing here can describe it
 * and nothing is fetched for it either.
 *
 * Listing one set of providers but not the others is not an oversight, and it
 * is not "Claude and Gemini are unavailable in this build" as an earlier note
 * here claimed: those two have had real adapters since they could be reached.
 * What would be wrong is claiming a bundled list is *complete* when half of
 * the providers were never in it.
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

    /**
     * [bundled] plus [fetched] — the list the selector shows and that id
     * resolution reads from once a key has fetched anything.
     *
     * Bundled wins a collision, so a fetched copy of an id already shipped
     * here is dropped rather than listed twice. That is deliberate: the
     * bundled entry carries a description and known capabilities that a
     * `/models` response cannot supply, and this catalog's own contract is
     * that an id the endpoint later retires fails loudly at request time
     * instead of vanishing from the list.
     *
     * Duplicates *within* [fetched] are dropped too, first provider winning,
     * because a model id resolves to exactly one adapter downstream — the same
     * id offered by two providers could only ever be reachable through one of
     * them, and picking arbitrarily at lookup time would mean sending to
     * whichever happened to sort first.
     */
    fun mergedWith(fetched: List<ModelDef>): List<ModelDef> {
        if (fetched.isEmpty()) return bundled
        val taken = bundled.mapTo(HashSet()) { it.id }
        val extras = ArrayList<ModelDef>(fetched.size)
        for (model in fetched) {
            if (taken.add(model.id)) extras += model
        }
        return bundled + extras
    }

    /** The subset carried by [adapter], which is what a provider tab shows. */
    fun forAdapter(adapter: ProviderId): List<ModelDef> =
        bundled.filter { it.adapter == adapter }

    fun byId(id: String): ModelDef? = bundled.firstOrNull { it.id == id }
}
