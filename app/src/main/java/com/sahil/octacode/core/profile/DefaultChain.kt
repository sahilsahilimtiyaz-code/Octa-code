package com.sahil.octacode.core.profile

/**
 * The chain as it stands before Agent and Runtime registries exist.
 *
 * Today this build *is* exactly one agent running locally, so these are not
 * placeholders standing in for something unknown — they are the complete
 * truth about what runs. That distinction matters: recording a fabricated
 * runtime id would put a value in every saved session that no screen ever
 * validated.
 *
 * When Agents and Runtimes gain real registries, selection stops coming from
 * here and starts coming from there. [RunProfile] itself does not change, so
 * sessions written today stay readable — `workspaceId` was null because no
 * folder was chosen, not because the field was missing.
 */
object DefaultChain {

    const val AGENT_ID = "octa"
    const val RUNTIME_ID = "local"

    /**
     * @param modelId the model actually requested for this conversation, from
     *   [com.sahil.octacode.core.provider.defaultModelFor] or the user's
     *   explicit selection.
     */
    fun of(modelId: String, workspaceId: String? = null): RunProfile = RunProfile(
        agentId = AGENT_ID,
        runtimeId = RUNTIME_ID,
        modelId = modelId,
        workspaceId = workspaceId,
    )
}
