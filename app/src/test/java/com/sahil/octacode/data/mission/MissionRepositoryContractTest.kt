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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3a repository contract tests against an in-memory fake
 * (same semantics as Room: cascade delete, append-only events, recoverable=RUNNING).
 */
private class FakeMissionRepository : com.sahil.octacode.domain.mission.MissionRepository {
    private val missions = MutableStateFlow<Map<String, Mission>>(emptyMap())
    private val runs = MutableStateFlow<List<PhaseRun>>(emptyList())
    private val events = MutableStateFlow<List<PhaseEvent>>(emptyList())
    private val diffs = MutableStateFlow<List<MissionDiff>>(emptyList())
    private var nextEventId = 1L
    private var nextRunId = 1L
    private var nextDiffId = 1L

    override suspend fun createMission(mission: Mission) {
        check(!missions.value.containsKey(mission.id)) { "duplicate mission ${mission.id}" }
        missions.update { it + (mission.id to mission) }
    }

    override suspend fun updateMission(mission: Mission) {
        check(missions.value.containsKey(mission.id)) { "missing mission ${mission.id}" }
        missions.update { it + (mission.id to mission) }
    }

    override suspend fun getMission(id: String): Mission? = missions.value[id]

    override fun observeMissions(): Flow<List<Mission>> =
        missions.map { it.values.sortedByDescending { m -> m.updatedAt } }

    override fun observeMission(id: String): Flow<Mission?> =
        missions.map { it[id] }

    override suspend fun listMissions(): List<Mission> =
        missions.value.values.sortedByDescending { it.updatedAt }

    override suspend fun listRecoverable(): List<Mission> =
        listMissions().filter { it.status == MissionStatus.RUNNING }

    override suspend fun upsertPhaseRun(run: PhaseRun) {
        val stored = if (run.id == 0L) run.copy(id = nextRunId++) else run
        runs.update { list ->
            list.filterNot { it.id == stored.id && it.missionId == stored.missionId } + stored
        }
    }

    override suspend fun getPhaseRuns(missionId: String): List<PhaseRun> =
        runs.value.filter { it.missionId == missionId }.sortedBy { it.phase.index }

    override fun observePhaseRuns(missionId: String): Flow<List<PhaseRun>> =
        runs.map { list -> list.filter { it.missionId == missionId }.sortedBy { it.phase.index } }

    override suspend fun appendEvent(event: PhaseEvent) {
        val stored = event.copy(id = nextEventId++)
        events.update { it + stored }
    }

    override suspend fun getEvents(missionId: String): List<PhaseEvent> =
        events.value.filter { it.missionId == missionId }

    override fun observeEvents(missionId: String): Flow<List<PhaseEvent>> =
        events.map { list -> list.filter { it.missionId == missionId } }

    override suspend fun upsertDiff(diff: MissionDiff) {
        val stored = if (diff.id == 0L) diff.copy(id = nextDiffId++) else diff
        diffs.update { list -> list.filterNot { it.id == stored.id } + stored }
    }

    override suspend fun getDiffs(missionId: String): List<MissionDiff> =
        diffs.value.filter { it.missionId == missionId }

    override suspend fun updateDiffDecision(diffId: Long, decision: DiffDecision) {
        diffs.update { list ->
            list.map { if (it.id == diffId) it.copy(decision = decision) else it }
        }
    }

    override suspend fun deleteMission(id: String) {
        missions.update { it - id }
        runs.update { l -> l.filterNot { it.missionId == id } }
        events.update { l -> l.filterNot { it.missionId == id } }
        diffs.update { l -> l.filterNot { it.missionId == id } }
    }
}

private fun mission(
    id: String = "m1",
    status: MissionStatus = MissionStatus.DRAFT,
    phase: MissionPhase? = MissionPhase.UNDERSTAND_TASK,
    updatedAt: Long = 1L
) = Mission(
    id = id,
    goal = "Add dark theme",
    projectPath = "/sdcard/projects/app",
    projectType = ProjectType.ANDROID,
    providerId = ProviderId.OPENAI,
    autonomy = AutonomyLevel.ASK,
    status = status,
    currentPhase = phase,
    failureReason = null,
    createdAt = 0L,
    updatedAt = updatedAt
)

class MissionRepositoryContractTest {

    private val repo = FakeMissionRepository()

    @Test
    fun `create then get round-trips mission fields`() = runTest {
        val m = mission()
        repo.createMission(m)
        assertEquals(m, repo.getMission("m1"))
        assertEquals(listOf(m), repo.listMissions())
    }

    @Test
    fun `update mutates status and phase for engine transitions`() = runTest {
        repo.createMission(mission())
        val running = repo.getMission("m1")!!.copy(
            status = MissionStatus.RUNNING,
            currentPhase = MissionPhase.PLAN_APPROACH,
            updatedAt = 2L
        )
        repo.updateMission(running)
        val loaded = repo.getMission("m1")!!
        assertEquals(MissionStatus.RUNNING, loaded.status)
        assertEquals(MissionPhase.PLAN_APPROACH, loaded.currentPhase)
    }

    @Test
    fun `listRecoverable returns only RUNNING missions`() = runTest {
        repo.createMission(mission(id = "a", status = MissionStatus.RUNNING))
        repo.createMission(mission(id = "b", status = MissionStatus.PAUSED, updatedAt = 2))
        repo.createMission(mission(id = "c", status = MissionStatus.FAILED, updatedAt = 3))
        repo.createMission(mission(id = "d", status = MissionStatus.COMPLETE, updatedAt = 4))
        val recoverable = repo.listRecoverable()
        assertEquals(listOf("a"), recoverable.map { it.id })
    }

    @Test
    fun `events are append-only and never overwritten`() = runTest {
        repo.createMission(mission())
        listOf(
            EventKind.PHASE_STARTED,
            EventKind.PHASE_PROGRESS,
            EventKind.PHASE_SUCCEEDED
        ).forEachIndexed { i, kind ->
            repo.appendEvent(
                PhaseEvent(
                    id = 99L, // client id must be ignored (append assigns)
                    missionId = "m1",
                    phase = MissionPhase.UNDERSTAND_TASK,
                    timestamp = i.toLong(),
                    kind = kind,
                    payloadJson = "{}"
                )
            )
        }
        val events = repo.getEvents("m1")
        assertEquals(3, events.size)
        assertEquals(
            listOf(EventKind.PHASE_STARTED, EventKind.PHASE_PROGRESS, EventKind.PHASE_SUCCEEDED),
            events.map { it.kind }
        )
        assertEquals(listOf(1L, 2L, 3L), events.map { it.id })
    }

    @Test
    fun `phase runs upsert by id and sort by phase index`() = runTest {
        repo.createMission(mission())
        repo.upsertPhaseRun(
            PhaseRun(0, "m1", MissionPhase.SELECT_PROVIDER, PhaseStatus.SUCCEEDED, 1, 2, "ok", null)
        )
        repo.upsertPhaseRun(
            PhaseRun(0, "m1", MissionPhase.UNDERSTAND_TASK, PhaseStatus.SUCCEEDED, 1, 2, "ok", null)
        )
        val runs = repo.getPhaseRuns("m1")
        assertEquals(
            listOf(MissionPhase.UNDERSTAND_TASK, MissionPhase.SELECT_PROVIDER),
            runs.map { it.phase }
        )
        // update existing row (same id) replaces status
        repo.upsertPhaseRun(runs[0].copy(status = PhaseStatus.FAILED, errorReason = "boom"))
        val updated = repo.getPhaseRuns("m1")
        assertEquals(2, updated.size)
        assertEquals(PhaseStatus.FAILED, updated[0].status)
        assertEquals("boom", updated[0].errorReason)
    }

    @Test
    fun `diff decision updates in place`() = runTest {
        repo.createMission(mission())
        repo.upsertDiff(
            MissionDiff(0, "m1", MissionPhase.REVIEW_DIFF, "Main.kt", "h1", "h2", "@@ @@", DiffDecision.PENDING)
        )
        val d = repo.getDiffs("m1").single()
        assertEquals(DiffDecision.PENDING, d.decision)
        repo.updateDiffDecision(d.id, DiffDecision.ACCEPTED)
        assertEquals(DiffDecision.ACCEPTED, repo.getDiffs("m1").single().decision)
    }

    @Test
    fun `deleteMission cascades children`() = runTest {
        repo.createMission(mission())
        repo.upsertPhaseRun(PhaseRun(0, "m1", MissionPhase.TEST, PhaseStatus.RUNNING, 1, null, null, null))
        repo.appendEvent(
            PhaseEvent(0, "m1", MissionPhase.TEST, 1, EventKind.PHASE_STARTED, "{}")
        )
        repo.upsertDiff(
            MissionDiff(0, "m1", MissionPhase.REVIEW_DIFF, "a", "b", "c", "p", DiffDecision.PENDING)
        )
        repo.deleteMission("m1")
        assertNull(repo.getMission("m1"))
        assertTrue(repo.getPhaseRuns("m1").isEmpty())
        assertTrue(repo.getEvents("m1").isEmpty())
        assertTrue(repo.getDiffs("m1").isEmpty())
    }

    @Test
    fun `mappers round-trip domain through entity strings`() {
        val m = mission(phase = MissionPhase.BUILD, status = MissionStatus.PAUSED)
        val entity = MissionMappers.run { m.toEntity() }
        val back = MissionMappers.run { entity.toDomain() }
        assertEquals(m, back)
        assertEquals(MissionPhase.BUILD.index, entity.currentPhaseIndex)

        val run = PhaseRun(5, "m1", MissionPhase.INSTALL, PhaseStatus.SKIPPED, null, null, null, "no adb")
        assertEquals(run, MissionMappers.run { run.toEntity().toDomain() })

        val ev = PhaseEvent(7, "m1", MissionPhase.ROLLBACK, 9L, EventKind.ROLLBACK_PERFORMED, """{"x":1}""")
        assertEquals(ev, MissionMappers.run { ev.toEntity().toDomain() })

        val diff = MissionDiff(3, "m1", MissionPhase.REVIEW_DIFF, "p", "b", "a", "@@", DiffDecision.REJECTED)
        assertEquals(diff, MissionMappers.run { diff.toEntity().toDomain() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapper fails loudly on unknown mission status`() {
        MissionMappers.run {
            MissionEntity(
                id = "x", goal = "g", projectPath = "/", projectType = "ANDROID",
                providerId = "OPENAI", autonomy = "ASK", status = "NOT_A_STATUS",
                currentPhaseIndex = null, failureReason = null, createdAt = 0, updatedAt = 0
            ).toDomain()
        }
    }
}
