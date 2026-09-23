package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.DiffDecision
import com.sahil.octacode.domain.mission.EventKind
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.domain.mission.MissionPhase
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
import com.sahil.octacode.ui.components.formatEventLine
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// M3c MissionDetail: engine state + phase rail + stream + checkpoint + live diffs.
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
    val diffs by repository.observeDiffs(missionId).collectAsState(initial = emptyList())

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
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = p.coerceIn(0, 100) / 100f,
                    modifier = Modifier
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = NeonGreen,
                    trackColor = OnDarkMuted.copy(alpha = 0.25f)
                )
            }
        }

        if (engineState.busy && !engineState.awaitingApproval) {
            GlassPanel(accent = NeonBlue) {
                ThinkingOrb(
                    label = engineState.message.ifBlank { "Working" },
                    percent = engineState.percent
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { engine.cancel() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Stop mission") }
            }
        }

        if (engineState.awaitingApproval) {
            GlassPanel(title = "Approval required", accent = WarningAmber) {
                val phaseTitle = currentPhase?.title ?: "checkpoint"
                val msg = when (currentPhase) {
                    MissionPhase.USER_CHECKPOINT ->
                        "Approve to continue past the planning checkpoint. Reject fails the mission."
                    MissionPhase.REVIEW_DIFF ->
                        "Approve to accept pending file diffs. Reject fails the mission and rolls back writes."
                    MissionPhase.BUILD ->
                        "Approve to run the project build. Reject fails the mission."
                    MissionPhase.INSTALL ->
                        "Approve to install the APK via adb. Reject fails the mission."
                    else ->
                        "Approve to continue past $phaseTitle. Reject fails the mission with reason."
                }
                Text(
                    msg,
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
            val phaseErrors = runs.associate { it.phase to it.errorReason }
            PhaseRail(
                currentPhase = currentPhase,
                statuses = phaseStatuses,
                errors = phaseErrors
            )
        }

        GlassPanel(title = "Event stream", accent = NeonBlue) {
            val lines = events.map { e -> formatEventLine(e) }
            StreamTerminal(lines = lines)
        }

        if (diffs.isEmpty()) {
            Text(
                "No diffs yet — file writes appear after Implement.",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
        } else {
            Text("Diffs", style = MaterialTheme.typography.titleMedium)
            diffs.forEach { d ->
                DiffCard(
                    diff = d,
                    onAccept = {
                        scope.launch { repository.updateDiffDecision(d.id, DiffDecision.ACCEPTED) }
                    },
                    onReject = {
                        scope.launch { repository.updateDiffDecision(d.id, DiffDecision.REJECTED) }
                    }
                )
            }
        }

        if (repoHasRollback(events)) {
            StatusBanner(
                tone = BannerTone.Warning,
                title = "Rollback performed",
                message = "Snapshot restored after failure — files reverted to pre-implement state."
            )
        }
    }
}

private fun repoHasRollback(events: List<com.sahil.octacode.domain.mission.PhaseEvent>): Boolean =
    events.any { it.kind == EventKind.ROLLBACK_PERFORMED }
