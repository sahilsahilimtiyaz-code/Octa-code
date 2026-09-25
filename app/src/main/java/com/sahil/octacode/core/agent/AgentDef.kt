package com.sahil.octacode.core.agent

/**
 * An agent the user can run: OpenCode, Claude Code, or a custom one.
 *
 * An agent is the *persona and toolset*; it picks a [RuntimeTarget][com.sahil.octacode.core.runtime.RuntimeTarget]
 * to execute in and a model to think with. The three are chosen separately so
 * swapping the model never changes which agent is driving.
 *
 * [isInstalled] and [version] are reported by probe, never assumed — an agent
 * we have not seen on the device reports `isInstalled = false` and `version = null`.
 */
data class AgentDef(
    val id: String,
    val displayName: String,
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = false,
    val version: String? = null,
    val defaultRuntimeId: String? = null,
    /** Remote/custom agents only; local agents have no endpoint. */
    val endpoint: String? = null,
    /** Why this agent cannot be selected yet. Null = selectable. */
    val blockedReason: String? = null,
) {
    val isSelectable: Boolean get() = isEnabled && blockedReason == null
}
