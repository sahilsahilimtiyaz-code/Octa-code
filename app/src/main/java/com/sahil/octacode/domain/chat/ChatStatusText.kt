package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ProviderId

// M4b pure status copy — no Android/Compose types, fully unit-testable.
// Every string doubles as an honest state label (never a fake promise).

data class AgentCardText(val title: String, val body: String, val ready: Boolean)

fun agentCardText(providerId: ProviderId, status: ProviderStatus?): AgentCardText = when (status) {
    is ProviderStatus.Ready ->
        AgentCardText(
            title = "Agent ready",
            body = "${providerId.title} connected — ${status.reason}",
            ready = true
        )
    null ->
        AgentCardText(title = "Agent unavailable", body = "Probing provider…", ready = false)
    else ->
        AgentCardText(
            title = "Agent unavailable",
            body = "No provider or local runtime is connected. " +
                "Configure Model & Provider from the menu before starting a session.",
            ready = false
        )
}

/** Status-bar line: text + whether the dot is green. */
fun agentDotText(status: ProviderStatus?): Pair<String, Boolean> =
    if (status is ProviderStatus.Ready) "Agent ready" to true
    else "Agent unavailable" to false

/** Composer model slot: provider title only when Ready, else the honest fallback. */
fun modelSlotLabel(providerId: ProviderId, status: ProviderStatus?): String =
    if (status is ProviderStatus.Ready) providerId.title else "Model unavailable"

fun composerHelperText(status: ProviderStatus?): String =
    if (status is ProviderStatus.Ready) "Streams live from the selected provider. Nothing is faked."
    else "Connect a provider or local runtime to send a message."
