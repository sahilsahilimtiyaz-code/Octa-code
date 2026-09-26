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
    /**
     * The command that starts this agent inside the guest, or null when this
     * build has no way to start it.
     *
     * Null is the load-bearing part. An agent that is merely present on disk is
     * a file, and a screen that offers it without a way to run it is the
     * affordance that does nothing. Whoever fills this in is asserting that a
     * real command will execute, so it is set only once the runtime that can
     * execute it is verified.
     */
    val command: String? = null,
    /** How much the install costs, so the fix can be quoted rather than implied. */
    val downloadBytes: Long = 0L
) {
    val isSelectable: Boolean get() = isEnabled && blockedReason == null

    /**
     * True when this build can actually start the agent: installed, unblocked,
     * and with a command to run.
     */
    val isLaunchable: Boolean get() = isSelectable && command != null
}
