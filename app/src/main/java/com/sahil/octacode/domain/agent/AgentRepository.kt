package com.sahil.octacode.domain.agent

import com.sahil.octacode.core.agent.AgentDef

/**
 * The agents this build knows how to install and start.
 *
 * An interface rather than a list, because every field of an [AgentDef] that
 * matters here is observed rather than declared: whether the binary is present
 * on disk, whether the runtime that can execute it is installed, and what is
 * missing when it is not. A hardcoded list would have to be edited every time
 * the manifest changed, and would go on claiming a tool was available after
 * the user removed it.
 */
interface AgentRepository {
    suspend fun agents(): List<AgentDef>
}
