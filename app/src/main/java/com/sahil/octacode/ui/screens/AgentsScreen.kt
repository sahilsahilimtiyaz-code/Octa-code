package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.agent.AgentDef
import com.sahil.octacode.domain.agent.AgentRepository
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import org.koin.compose.koinInject

/** Three outcomes, because "no agents" and "could not find out" are not the same. */
private sealed interface AgentList {
    data object Loading : AgentList
    data class Loaded(val agents: List<AgentDef>) : AgentList
    data class Failed(val reason: String) : AgentList
}

/**
 * The agents this build can install and start.
 *
 * This is the drawer's third section, and it exists because `AgentDef` finally
 * has something behind it. The row used to be absent on purpose: a category
 * with nothing to open is worse than no category. Now every row is either
 * launchable or says exactly which piece is missing.
 *
 * What it deliberately does not claim: that a chat turn can be handed to an
 * agent. In this build an agent is a program the terminal can run, and that is
 * what the row offers.
 */
@Composable
fun AgentsScreen(
    onBack: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenInTerminal: (String) -> Unit,
    repository: AgentRepository = koinInject(),
) {
    // Observed rather than remembered: installing the runtime happens on
    // another screen, and the answer has to be true the moment the user comes
    // back from it.
    val list by produceState<AgentList>(AgentList.Loading, repository) {
        value = try {
            AgentList.Loaded(repository.agents())
        } catch (e: Exception) {
            AgentList.Failed(e.message ?: "the agent list could not be read")
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Agents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        when (val current = list) {
            // Nothing rendered while loading. Showing "no agents" here would
            // be a claim the app has not checked yet.
            AgentList.Loading -> Unit

            is AgentList.Failed -> Text(
                text = "Could not work out what agents are available — $current.reason.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarningAmber
            )

            is AgentList.Loaded ->
                if (current.agents.isEmpty()) {
                    Text(
                        text = "No agents in this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        current.agents.forEach { agent ->
                            AgentCard(
                                agent = agent,
                                onOpenRuntime = onOpenRuntime,
                                onLaunch = { userland ->
                                    agent.command?.let { onOpenInTerminal(userland) }
                                }
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentDef,
    onOpenRuntime: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    GlassPanel(title = agent.displayName) {
        Text(
            text = when {
                agent.isLaunchable -> "ready · ${agent.version}"
                agent.isInstalled -> "installed, cannot start"
                else -> "not installed"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (agent.isLaunchable) NeonGreen else WarningAmber
        )

        Spacer(Modifier.height(6.dp))

        // The whole reason, whenever there is one. A single "unavailable"
        // across three different missing pieces would send the user hunting
        // for the wrong one.
        agent.blockedReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "Runs in the terminal's glibc userland: type its name and press Run.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkMuted
        )

        Spacer(Modifier.height(10.dp))

        if (agent.isLaunchable) {
            Button(
                onClick = { onLaunch(agent.defaultRuntimeId ?: "glibc") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open in terminal")
            }
        } else {
            // One action, and it is the one that fixes the reason above. A
            // button that appeared to install from here would be a promise
            // this screen cannot keep — the runtime screen is where that
            // actually happens, with the real byte count.
            Button(onClick = onOpenRuntime, modifier = Modifier.fillMaxWidth()) {
                Text("Open Runtime settings")
            }
        }
    }
}
