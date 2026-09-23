package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.domain.mission.MissionRepository
import com.sahil.octacode.domain.mission.MissionStatus
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// M3b Home hub: launch missions, resume recoverable, open recent.
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onNewMission: () -> Unit,
    onOpenMission: (String) -> Unit,
    repository: MissionRepository = koinInject(),
    engine: MissionEngine = koinInject()
) {
    val scope = rememberCoroutineScope()
    val missions by repository.observeMissions().collectAsState(initial = emptyList())
    val engineState by engine.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Octa Code", style = MaterialTheme.typography.headlineLarge, color = NeonBlue)
        Text(
            "Mobile AI Coding Agent — M3b Mission Engine",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkMuted
        )

        GlassPanel(title = "Mission hub", accent = NeonGreen) {
            Text(
                "16-phase pipeline · handlers 1–6 live · phases 7+ pause for M3c (honest stop).",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onNewMission, modifier = Modifier.fillMaxWidth()) {
                Text("New Mission")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text("Open Agent Chat")
            }
        }

        if (engineState.activeMissionId != null && (engineState.busy || engineState.awaitingApproval)) {
            GlassPanel(title = "Active engine", accent = NeonBlue) {
                Text(
                    "Phase ${engineState.currentPhase?.index ?: "—"} · ${engineState.message}",
                    style = MaterialTheme.typography.bodySmall
                )
                engineState.activeMissionId?.let { id ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onOpenMission(id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open running mission")
                    }
                }
            }
        }

        GlassPanel(title = "Recoverable", accent = WarningAmber) {
            val recoverable = missions.filter { it.status == MissionStatus.RUNNING }
            if (recoverable.isEmpty()) {
                Text(
                    "None — no missions left RUNNING by a crash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
            } else {
                recoverable.forEach { m ->
                    Spacer(Modifier.height(6.dp))
                    Text(m.goal, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        m.failureReason ?: m.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                engine.start(m.id)
                                onOpenMission(m.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Resume ${m.currentPhase?.title ?: "mission"}") }
                }
            }
        }

        Text("Recent missions", style = MaterialTheme.typography.titleMedium)
        if (missions.isEmpty()) {
            Text(
                "No missions yet.",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted
            )
        } else {
            missions.forEach { m ->
                GlassPanel(accent = statusAccent(m.status)) {
                    Text(m.goal, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${m.status.name} · ${m.projectType.name} · phase ${m.currentPhase?.index ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onOpenMission(m.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open") }
                }
            }
        }
    }
}

private fun statusAccent(status: MissionStatus) = when (status) {
    MissionStatus.COMPLETE -> NeonGreen
    MissionStatus.FAILED -> NeonRed
    MissionStatus.CANCELLED -> NeonRed
    MissionStatus.RUNNING -> NeonBlue
    MissionStatus.PAUSED -> WarningAmber
    MissionStatus.DRAFT -> OnDarkMuted
}
