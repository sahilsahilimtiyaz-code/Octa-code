package com.sahil.octacode.core.profile

/**
 * The chain that makes this a coding platform instead of a chat box with a
 * model dropdown:
 *
 *     Agent → Runtime → Model → Workspace → Chat
 *
 *     OpenCode  →  Local Runtime  →  MiMo-V2.6-Flash  →  /storage/.../proj  →  session
 *
 * This is the only type where the four axes meet, and they stay independent:
 * choosing a different model must not move where code runs, and opening a
 * different folder must not drop the model. Screens bind to one field each and
 * never reach into another's.
 *
 * [workspaceId] is nullable because a session can be opened before a folder is
 * picked; the agent then has no working directory, which it reports rather
 * than pretending it has one.
 */
data class RunProfile(
    val agentId: String,
    val runtimeId: String,
    val modelId: String,
    val workspaceId: String? = null,
) {
    /** True when every axis is chosen and a session may start. */
    val isComplete: Boolean
        get() = agentId.isNotBlank() && runtimeId.isNotBlank() && modelId.isNotBlank()

    companion object {
        /** An explicitly empty profile — never a valid starting point. */
        val EMPTY = RunProfile(agentId = "", runtimeId = "", modelId = "")
    }
}
