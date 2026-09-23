package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.serialization.Serializable

/**
 * M3a domain model — pure Kotlin, no Android/Room types.
 * Repository maps Entity ↔ this.
 */
data class Mission(
    val id: String,
    val goal: String,
    val projectPath: String,
    val projectType: ProjectType,
    val providerId: ProviderId,
    val autonomy: AutonomyLevel,
    val status: MissionStatus,
    val currentPhase: MissionPhase?,
    val failureReason: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class PhaseRun(
    val id: Long,
    val missionId: String,
    val phase: MissionPhase,
    val status: PhaseStatus,
    val startedAt: Long?,
    val endedAt: Long?,
    val outputSummary: String?,
    val errorReason: String?
)

data class PhaseEvent(
    val id: Long,
    val missionId: String,
    val phase: MissionPhase,
    val timestamp: Long,
    val kind: EventKind,
    val payloadJson: String
)

data class MissionDiff(
    val id: Long,
    val missionId: String,
    val phase: MissionPhase,
    val path: String,
    val beforeHash: String,
    val afterHash: String,
    val patchText: String,
    val decision: DiffDecision
)

/** Terminal states — no further phase transitions allowed. */
val MissionStatus.isTerminal: Boolean
    get() = this == MissionStatus.FAILED ||
        this == MissionStatus.COMPLETE ||
        this == MissionStatus.CANCELLED

/** Serializable payloads stored in phase_events.payloadJson (kotlinx). */
@Serializable
data class PhaseProgressPayload(val message: String, val percent: Int? = null)

@Serializable
data class PhaseFailurePayload(val reason: String, val retryable: Boolean = true)

@Serializable
data class CheckpointPayload(val planSummary: String, val approvalsRequired: List<String> = emptyList())
