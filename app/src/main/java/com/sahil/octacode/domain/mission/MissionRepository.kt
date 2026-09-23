package com.sahil.octacode.domain.mission

import kotlinx.coroutines.flow.Flow

// Marker for DI clarity; RoomMissionRepository is the sole production impl (M3a).

/**
 * M3a persistence contract. Room implements this; tests use in-memory fakes.
 * All writes that the engine depends on for crash-recovery go through here.
 */
interface MissionRepository {
    suspend fun createMission(mission: Mission)
    suspend fun updateMission(mission: Mission)
    suspend fun getMission(id: String): Mission?
    fun observeMissions(): Flow<List<Mission>>
    fun observeMission(id: String): Flow<Mission?>
    suspend fun listMissions(): List<Mission>

    /** Missions left RUNNING by a crash → engine offers resume. */
    suspend fun listRecoverable(): List<Mission>

    suspend fun upsertPhaseRun(run: PhaseRun)
    suspend fun getPhaseRuns(missionId: String): List<PhaseRun>
    fun observePhaseRuns(missionId: String): Flow<List<PhaseRun>>

    suspend fun appendEvent(event: PhaseEvent)
    suspend fun getEvents(missionId: String): List<PhaseEvent>
    fun observeEvents(missionId: String): Flow<List<PhaseEvent>>

    suspend fun upsertDiff(diff: MissionDiff)
    suspend fun getDiffs(missionId: String): List<MissionDiff>
    fun observeDiffs(missionId: String): Flow<List<MissionDiff>>
    suspend fun updateDiffDecision(diffId: Long, decision: DiffDecision)

    /** Wipe a mission and children (user delete). */
    suspend fun deleteMission(id: String)
}
