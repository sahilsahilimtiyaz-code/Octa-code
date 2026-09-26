package com.sahil.octacode.core.runtime

/**
 * Where the coding agent executes. Independent of the model — §3 requires that
 * selecting one must not silently change the other.
 */
enum class RuntimeScope(val label: String) {
    LOCAL("Local"),
    REMOTE("Remote"),
    CUSTOM("Custom"),
}

/**
 * A place the agent can run.
 *
 * [blockedReason] is the honesty valve for controls that cannot work yet.
 * The original reason — Android refusing to exec() anything written to
 * app-writable storage — was removed by R3's targetSdk 28 pin, so a local
 * runtime can now start. The field remains for whatever genuinely cannot:
 * a remote scope with no endpoint, or a target that cannot honestly claim
 * to be running. A screen renders this sentence next to a disabled control
 * instead of a Start button that swallows the tap. `null` means the controls
 * are live.
 *
 * [version] is nullable for the same reason [contextWindow] is: an unprobed
 * runtime has no version, and inventing one would be a lie in the UI.
 */
data class RuntimeTarget(
    val id: String,
    val displayName: String,
    val scope: RuntimeScope,
    val version: String? = null,
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    /** Set for REMOTE/CUSTOM scopes only. */
    val endpoint: String? = null,
    val capabilities: Set<String> = emptySet(),
    /** The runtime currently backing the active session. */
    val isActive: Boolean = false,
    /** Why Start/Stop is disabled. Null = operational. */
    val blockedReason: String? = null,
) {
    /** Controls may be touched only when there is no documented blocker. */
    val isOperational: Boolean get() = blockedReason == null
}
