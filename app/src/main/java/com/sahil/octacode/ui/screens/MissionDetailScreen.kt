package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.EventKind
import com.sahil.octacode.domain.mission.MissionDiff
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.domain.mission.MissionRepository
import com.sahil.octacode.domain.mission.MissionStatus
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.components.DiffCard
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.components.PhaseRail
import com.sahil.octacode.ui.components.StatusBanner
import com.sahil.octacode.ui.components.StreamTerminal
import com.sahil.octacode.ui.components.ThinkingOrb
import com.sahil.octacode.ui.components.bannerToneFor
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// M3b MissionDetail: engine state + phase rail + stream + checkpoint controls.
@Composable
fun MissionDetailScreen(
    missionId: String,
    onBack: () -> Unit,
    engine: MissionEngine = koinInject(),
    repository: MissionRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    val engineState by engine.state.collectAsState()
    val mission by repository.observeMission(missionId).collectAsState(initial = null)
    val runs by repository.observePhaseRuns(missionId).collectAsState(initial = emptyList())
    val events by repository.observeEvents(missionId).collectAsState(initial = emptyList())
    val diffs by androidx.compose.runtime.produceState<List<MissionDiff>>(
        initialValue = emptyList(),
        key1 = missionId
    ) {
        value = repository.getDiffs(missionId)
    }

    LaunchedEffect(missionId) {
        engine.hydrateFromRepository(missionId)
    }

    val status = mission?.status ?: engineState.status
    val phaseStatuses = runs.associate { it.phase to it.status }
        .ifEmpty { engineState.phaseStatuses }
    val currentPhase = mission?.currentPhase ?: engineState.currentPhase
    val failure = mission?.failureReason
    val terminal = status == MissionStatus.FAILED ||
        status == MissionStatus.COMPLETE ||
        status == MissionStatus.CANCELLED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Mission", style = MaterialTheme.typography.headlineSmall)
        }

        GlassPanel(title = mission?.goal ?: "…", accent = NeonBlue) {
            StatusBanner(
                tone = bannerToneFor(
                    status = status,
                    awaiting = engineState.awaitingApproval,
                    busy = engineState.busy,
                    hasError = failure != null
                ),
                title = status.name + (currentPhase?.let { " · ${it.title}" } ?: ""),
                message = failure ?: engineState.message
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "provider=${mission?.providerId?.title ?: "—"} · " +
                    "autonomy=${mission?.autonomy?.name ?: "—"} · " +
                    "type=${mission?.projectType?.name ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
            engineState.percent?.let { p ->
                Text(
                    "progress ~ $p%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
            }
        }

        if (engineState.busy && !engineState.awaitingApproval) {
            ThinkingOrb(label = engineState.message.ifBlank { "Working" })
        }

        if (engineState.awaitingApproval) {
            GlassPanel(title = "User checkpoint", accent = WarningAmber) {
                Text(
                    "Approve to continue past phase 6. Reject fails the mission with reason.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { engine.approveCheckpoint() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve") }
                    OutlinedButton(
                        onClick = { engine.rejectCheckpoint() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reject") }
                }
            }
        }

        if (!terminal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { engine.pause() },
                    enabled = engineState.busy && !engineState.awaitingApproval,
                    modifier = Modifier.weight(1f)
                ) { Text("Pause") }
                OutlinedButton(
                    onClick = { engine.cancel() },
                    enabled = engineState.busy || status == MissionStatus.PAUSED,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { scope.launch { engine.start(missionId) } },
                    enabled = status == MissionStatus.PAUSED && !engineState.awaitingApproval,
                    modifier = Modifier.weight(1f)
                ) { Text("Resume") }
            }
        }

        GlassPanel(title = "Phases", accent = NeonGreen) {
            PhaseRail(currentPhase = currentPhase, statuses = phaseStatuses)
        }

        GlassPanel(title = "Event stream", accent = NeonBlue) {
            val lines = events.map { e ->
                val phaseTag = "%02d".format(e.phase.index)
                val msg = when (e.kind) {
                    EventKind.PHASE_PROGRESS,
                    EventKind.PHASE_SUCCEEDED,
                    EventKind.PHASE_FAILED,
                    EventKind.CHECKPOINT_REQUIRED -> e.payloadJson
                    else -> e.kind.name
                }
                "[$phaseTag] ${e.kind.name}: $msg"
            }
            StreamTerminal(lines = lines)
        }

        if (diffs.isEmpty()) {
            Text(
                "No diffs yet — file writes arrive with phase 7+ (M3c).",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
        } else {
            Text("Diffs", style = MaterialTheme.typography.titleMedium)
            diffs.forEach { d ->
                DiffCard(diff = d)
            }
        }

        if (status == MissionStatus.PAUSED && failure != null && failure.contains("M3c")) {
            StatusBanner(
                tone = BannerTone.Info,
                title = "M3b complete through checkpoint",
                message = failure
            )
        }
    }
}
