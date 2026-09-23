package com.sahil.octacode.data.mission

import com.sahil.octacode.data.mission.MissionMappers.toDomain
import com.sahil.octacode.data.mission.MissionMappers.toEntity
import com.sahil.octacode.domain.mission.DiffDecision
import com.sahil.octacode.domain.mission.Mission
import com.sahil.octacode.domain.mission.MissionDiff
import com.sahil.octacode.domain.mission.MissionRepository
import com.sahil.octacode.domain.mission.PhaseEvent
import com.sahil.octacode.domain.mission.PhaseRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * M3a Room-backed repository. Children cascade on mission delete (FK).
 * [listRecoverable] powers crash → resume (missions stuck RUNNING).
 */
class RoomMissionRepository(
    private val missionDao: MissionDao,
    private val phaseRunDao: PhaseRunDao,
    private val phaseEventDao: PhaseEventDao,
    private val missionDiffDao: MissionDiffDao
) : MissionRepository {

    override suspend fun createMission(mission: Mission) {
        missionDao.insert(mission.toEntity())
    }

    override suspend fun updateMission(mission: Mission) {
        missionDao.update(mission.toEntity())
    }

    override suspend fun getMission(id: String): Mission? =
        missionDao.getById(id)?.toDomain()

    override fun observeMissions(): Flow<List<Mission>> =
        missionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeMission(id: String): Flow<Mission?> =
        missionDao.observeById(id).map { it?.toDomain() }

    override suspend fun listMissions(): List<Mission> =
        missionDao.getAll().map { it.toDomain() }

    override suspend fun listRecoverable(): List<Mission> =
        missionDao.getRecoverable().map { it.toDomain() }

    override suspend fun upsertPhaseRun(run: PhaseRun) {
        phaseRunDao.upsert(run.toEntity())
    }

    override suspend fun getPhaseRuns(missionId: String): List<PhaseRun> =
        phaseRunDao.getForMission(missionId).map { it.toDomain() }

    override fun observePhaseRuns(missionId: String): Flow<List<PhaseRun>> =
        phaseRunDao.observeForMission(missionId).map { list -> list.map { it.toDomain() } }

    override suspend fun appendEvent(event: PhaseEvent) {
        // Append-only: always insert with id=0 so Room assigns; strip any client id.
        phaseEventDao.insert(event.copy(id = 0).toEntity())
    }

    override suspend fun getEvents(missionId: String): List<PhaseEvent> =
        phaseEventDao.getForMission(missionId).map { it.toDomain() }

    override fun observeEvents(missionId: String): Flow<List<PhaseEvent>> =
        phaseEventDao.observeForMission(missionId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertDiff(diff: MissionDiff) {
        missionDiffDao.upsert(diff.toEntity())
    }

    override suspend fun getDiffs(missionId: String): List<MissionDiff> =
        missionDiffDao.getForMission(missionId).map { it.toDomain() }

    override suspend fun updateDiffDecision(diffId: Long, decision: DiffDecision) {
        missionDiffDao.updateDecision(diffId, decision.name)
    }

    override suspend fun deleteMission(id: String) {
        // FK CASCADE removes phase_runs / phase_events / mission_diffs.
        missionDao.deleteById(id)
    }
}
