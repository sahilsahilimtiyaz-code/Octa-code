package com.sahil.octacode.core.model

import com.sahil.octacode.core.provider.ProviderId

/**
 * What a model can do. A capability that is not listed is a capability we do
 * not know about — absence means "unproven", never "yes".
 */
enum class ModelCapability(val label: String) {
    TOOLS("Tools"),
    VISION("Vision"),
    THINKING("Reasoning"),
    STREAMING("Streaming"),
}

/**
 * One entry in the model catalog.
 *
 * Deliberately NOT the adapter. `adapter` says which [AiProvider][com.sahil.octacode.core.provider.AiProvider]
 * implementation transports the request — there are four, two of them real —
 * while `id` and the rest describe what the user picked. Keeping those axes
 * apart is what lets the catalog grow to hundreds of entries without adding an
 * adapter class per model, which is the requirement in §2.
 *
 * [contextWindow] is nullable on purpose: we do not know it until an endpoint
 * tells us, and guessing would be a number the UI presents as fact.
 */
data class ModelDef(
    /** Wire identifier sent to the endpoint, e.g. `gpt-4o-mini`. */
    val id: String,
    val displayName: String,
    /** Grouping key shown as a section header, e.g. `OpenAI`. */
    val provider: String,
    /** Which adapter implementation serves this model. */
    val adapter: ProviderId,
    val description: String = "",
    val contextWindow: Int? = null,
    val capabilities: Set<ModelCapability> = emptySet(),
    /** Free tier available — drives a badge, not availability. */
    val isFree: Boolean = false,
    /** Runs on-device rather than over the network. */
    val isLocal: Boolean = false,
) {
    val supportsThinking: Boolean get() = ModelCapability.THINKING in capabilities
    val supportsTools: Boolean get() = ModelCapability.TOOLS in capabilities
    val supportsVision: Boolean get() = ModelCapability.VISION in capabilities
}

/**
 * Per-model user state, kept out of [ModelDef] so the catalog itself stays
 * immutable and shareable: favorites and recents change per user, the catalog
 * does not.
 */
data class ModelUserState(
    val modelId: String,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
)
