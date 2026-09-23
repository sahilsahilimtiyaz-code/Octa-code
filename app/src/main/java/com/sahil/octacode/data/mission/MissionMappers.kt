package com.sahil.octacode.data.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.domain.mission.DiffDecision
import com.sahil.octacode.domain.mission.EventKind
import com.sahil.octacode.domain.mission.Mission
import com.sahil.octacode.domain.mission.MissionDiff
import com.sahil.octacode.domain.mission.MissionPhase
import com.sahil.octacode.domain.mission.MissionStatus
import com.sahil.octacode.domain.mission.PhaseEvent
import com.sahil.octacode.domain.mission.PhaseRun
import com.sahil.octacode.domain.mission.PhaseStatus

// Entity ↔ domain mappers. Unknown enum strings fail loudly (honesty > silent default).
internal object MissionMappers {

    fun MissionEntity.toDomain(): Mission = Mission(
        id = id,
        goal = goal,
        projectPath = projectPath,
        projectType = ProjectType.valueOf(projectType),
        providerId = ProviderId.valueOf(providerId),
        autonomy = AutonomyLevel.valueOf(autonomy),
        status = MissionStatus.valueOf(status),
        currentPhase = currentPhaseIndex?.let { MissionPhase.fromIndex(it) },
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Mission.toEntity(): MissionEntity = MissionEntity(
        id = id,
        goal = goal,
        projectPath = projectPath,
        projectType = projectType.name,
        providerId = providerId.name,
        autonomy = autonomy.name,
        status = status.name,
        currentPhaseIndex = currentPhase?.index,
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun PhaseRunEntity.toDomain(): PhaseRun = PhaseRun(
        id = id,
        missionId = missionId,
        phase = MissionPhase.fromIndex(phaseIndex)
            ?: error("Unknown phaseIndex=$phaseIndex in phase_runs"),
        status = PhaseStatus.valueOf(status),
        startedAt = startedAt,
        endedAt = endedAt,
        outputSummary = outputSummary,
        errorReason = errorReason
    )

    fun PhaseRun.toEntity(): PhaseRunEntity = PhaseRunEntity(
        id = id,
        missionId = missionId,
        phaseIndex = phase.index,
        status = status.name,
        startedAt = startedAt,
        endedAt = endedAt,
        outputSummary = outputSummary,
        errorReason = errorReason
    )

    fun PhaseEventEntity.toDomain(): PhaseEvent = PhaseEvent(
        id = id,
        missionId = missionId,
        phase = MissionPhase.fromIndex(phaseIndex)
            ?: error("Unknown phaseIndex=$phaseIndex in phase_events"),
        timestamp = timestamp,
        kind = EventKind.valueOf(kind),
        payloadJson = payloadJson
    )

    fun PhaseEvent.toEntity(): PhaseEventEntity = PhaseEventEntity(
        id = id,
        missionId = missionId,
        phaseIndex = phase.index,
        timestamp = timestamp,
        kind = kind.name,
        payloadJson = payloadJson
    )

    fun MissionDiffEntity.toDomain(): MissionDiff = MissionDiff(
        id = id,
        missionId = missionId,
        phase = MissionPhase.fromIndex(phaseIndex)
            ?: error("Unknown phaseIndex=$phaseIndex in mission_diffs"),
        path = path,
        beforeHash = beforeHash,
        afterHash = afterHash,
        patchText = patchText,
        decision = DiffDecision.valueOf(decision)
    )

    fun MissionDiff.toEntity(): MissionDiffEntity = MissionDiffEntity(
        id = id,
        missionId = missionId,
        phaseIndex = phase.index,
        path = path,
        beforeHash = beforeHash,
        afterHash = afterHash,
        patchText = patchText,
        decision = decision.name
    )
}
