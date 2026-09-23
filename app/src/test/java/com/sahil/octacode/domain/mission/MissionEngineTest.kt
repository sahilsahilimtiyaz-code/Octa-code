package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.capability.ProjectTypeDetector
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * M3b engine tests: handlers 1–6, checkpoint gating, honesty stops at phase 7,
 * cancel, persistence. Keeps M3a repository contract semantics via in-memory fake.
 */
private class FakeMissionRepository : MissionRepository {
    private val missions = MutableStateFlow<Map<String, Mission>>(emptyMap())
    private val runs = MutableStateFlow<List<PhaseRun>>(emptyList())
    private val events = MutableStateFlow<List<PhaseEvent>>(emptyList())
    private val diffs = MutableStateFlow<List<MissionDiff>>(emptyList())
    private var nextEventId = 1L
    private var nextRunId = 1L
    private var nextDiffId = 1L

    override suspend fun createMission(mission: Mission) {
        check(!missions.value.containsKey(mission.id)) { "duplicate ${mission.id}" }
        missions.update { it + (mission.id to mission) }
    }

    override suspend fun updateMission(mission: Mission) {
        check(missions.value.containsKey(mission.id)) { "missing ${mission.id}" }
        missions.update { it + (mission.id to mission) }
    }

    override suspend fun getMission(id: String): Mission? = missions.value[id]

    override fun observeMissions(): Flow<List<Mission>> =
        missions.map { it.values.sortedByDescending { m -> m.updatedAt } }

    override fun observeMission(id: String): Flow<Mission?> = missions.map { it[id] }

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
        events.update { it + event.copy(id = nextEventId++) }
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

private class FakeRegistry(
    private val statuses: Map<ProviderId, ProviderStatus> = mapOf(
        ProviderId.OPENAI to ProviderStatus.MissingKey("API key not configured")
    ),
    initialAutonomy: AutonomyLevel = AutonomyLevel.ASK
) : CapabilityRegistry {
    private val _autonomy = MutableStateFlow(initialAutonomy)
    override val autonomy: StateFlow<AutonomyLevel> = _autonomy

    override suspend fun providerStatus(id: ProviderId): ProviderStatus =
        statuses[id] ?: ProviderStatus.Unavailable("No adapter for $id")

    override suspend fun refreshAll(): Map<ProviderId, ProviderStatus> =
        ProviderId.entries.associateWith { providerStatus(it) }

    override fun setAutonomy(level: AutonomyLevel) {
        _autonomy.value = level
    }
}

class MissionEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun androidProjectRoot(): String {
        val root = tmp.newFolder("android-app")
        tmp.newFolder("android-app", "app", "src", "main")
        java.io.File(root, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
        java.io.File(root, "app/src/main/AndroidManifest.xml").writeText("<manifest/>")
        return root.absolutePath
    }

    private fun kotlinx.coroutines.test.TestScope.engine(
        repo: FakeMissionRepository,
        registry: FakeRegistry = FakeRegistry()
    ) = MissionEngine(
        repository = repo,
        registry = registry,
        providers = emptyMap(),
        clock = { 1000L },
        scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        )
    )

    private fun kotlinx.coroutines.test.TestScope.settle() {
        testScheduler.advanceUntilIdle()
    }

    private fun kotlinx.coroutines.test.TestScope.awaitCheckpoint(engine: MissionEngine) {
        var i = 0
        while (!engine.state.value.awaitingApproval && i++ < 10_000) {
            testScheduler.advanceUntilIdle()
        }
        assertTrue("checkpoint not reached", engine.state.value.awaitingApproval)
    }

    @Test
    fun `launch blank goal is rejected`() = runTest {
        val e = engine(FakeMissionRepository())
        try {
            e.launch("  ", "/tmp", ProviderId.OPENAI)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("goal"))
        }
    }

    @Test
    fun `missing project path fails DETECT_PROJECT honestly`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo)
        val id = e.launch("Add feature", "/definitely/missing/path/octa", ProviderId.OPENAI)
        settle()

        assertEquals(MissionStatus.FAILED, repo.getMission(id)!!.status)
        val runs = repo.getPhaseRuns(id)
        assertEquals(PhaseStatus.SUCCEEDED, runs.first { it.phase == MissionPhase.UNDERSTAND_TASK }.status)
        val detect = runs.first { it.phase == MissionPhase.DETECT_PROJECT }
        assertEquals(PhaseStatus.FAILED, detect.status)
        assertTrue(detect.errorReason!!.contains("does not exist"))
        assertEquals(MissionPhase.DETECT_PROJECT, repo.getMission(id)!!.currentPhase)
        assertNotNull(e.state.value.lastError)
        assertTrue(e.state.value.lastError!!.contains("does not exist"))
    }

    @Test
    fun `ASK autonomy waits at USER_CHECKPOINT then pauses for M3c after approve`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.ASK))
        val id = e.launch("Add dark theme", androidProjectRoot(), ProviderId.OPENAI)
        awaitCheckpoint(e)
        assertEquals(MissionStatus.PAUSED, repo.getMission(id)!!.status)
        assertEquals(MissionPhase.USER_CHECKPOINT, repo.getMission(id)!!.currentPhase)

        val kinds = repo.getEvents(id).map { it.kind }
        assertTrue(kinds.contains(EventKind.CHECKPOINT_REQUIRED))
        assertTrue(kinds.contains(EventKind.PHASE_STARTED))

        e.approveCheckpoint()
        settle()

        val m = repo.getMission(id)!!
        assertEquals(MissionStatus.PAUSED, m.status)
        assertEquals(MissionPhase.IMPLEMENT, m.currentPhase)
        assertTrue(m.failureReason!!.contains("M3c"))
        assertFalse(e.state.value.awaitingApproval)
        assertFalse(e.state.value.busy)

        val runs = repo.getPhaseRuns(id)
        // Phases 1–6 succeeded
        listOf(
            MissionPhase.UNDERSTAND_TASK,
            MissionPhase.DETECT_PROJECT,
            MissionPhase.SELECT_TEAM,
            MissionPhase.SELECT_PROVIDER,
            MissionPhase.PLAN_APPROACH,
            MissionPhase.USER_CHECKPOINT
        ).forEach { ph ->
            assertEquals("phase $ph", PhaseStatus.SUCCEEDED, runs.first { it.phase == ph }.status)
        }
        assertTrue(repo.getEvents(id).any { it.kind == EventKind.CHECKPOINT_APPROVED })
        assertTrue(repo.getEvents(id).any { it.kind == EventKind.MISSION_PAUSED })
        // Phase 7 never ran (no fake implement)
        assertNull(runs.firstOrNull { it.phase == MissionPhase.IMPLEMENT })
    }

    @Test
    fun `reject checkpoint fails mission with reason`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.ASK))
        val id = e.launch("Add dark theme", androidProjectRoot(), ProviderId.OPENAI)
        awaitCheckpoint(e)
        assertTrue(e.state.value.awaitingApproval)

        e.rejectCheckpoint()
        settle()

        val m = repo.getMission(id)!!
        assertEquals(MissionStatus.FAILED, m.status)
        assertEquals("Checkpoint rejected by user", m.failureReason)
        assertTrue(repo.getEvents(id).any { it.kind == EventKind.CHECKPOINT_REJECTED })
    }

    @Test
    fun `HIGH_AUTONOMY auto-approves checkpoint without waiting`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.HIGH_AUTONOMY))
        val id = e.launch("Add dark theme", androidProjectRoot(), ProviderId.OPENAI)
        settle()

        assertFalse(e.state.value.awaitingApproval)
        val m = repo.getMission(id)!!
        assertEquals(MissionStatus.PAUSED, m.status)
        assertEquals(MissionPhase.IMPLEMENT, m.currentPhase)
        val cp = repo.getPhaseRuns(id).first { it.phase == MissionPhase.USER_CHECKPOINT }
        assertEquals(PhaseStatus.SUCCEEDED, cp.status)
        assertTrue(cp.outputSummary!!.contains("auto-approved"))
        assertTrue(repo.getEvents(id).none { it.kind == EventKind.CHECKPOINT_REQUIRED })
    }

    @Test
    fun `DETECT_PROJECT detects ANDROID from real files`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.HIGH_AUTONOMY))
        val id = e.launch("x", androidProjectRoot(), ProviderId.OPENAI)
        settle()

        assertEquals(ProjectType.ANDROID, repo.getMission(id)!!.projectType)
        val team = repo.getPhaseRuns(id).first { it.phase == MissionPhase.SELECT_TEAM }
        assertTrue(team.outputSummary!!.contains("ANDROID"))
        assertTrue(team.outputSummary!!.contains("Android Engineer"))
    }

    @Test
    fun `SELECT_PROVIDER reports MissingKey honestly and still selects`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry())
        val id = e.launch("x", androidProjectRoot(), ProviderId.OPENAI)
        awaitCheckpoint(e)

        val run = repo.getPhaseRuns(id).first { it.phase == MissionPhase.SELECT_PROVIDER }
        assertEquals(PhaseStatus.SUCCEEDED, run.status)
        assertTrue(run.outputSummary!!.contains("Unavailable"))
        assertTrue(run.outputSummary!!.contains("API key not configured"))
        assertTrue(run.outputSummary!!.contains("Selected"))
    }

    @Test
    fun `events include PHASE_STARTED and PHASE_SUCCEEDED for understand task`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.HIGH_AUTONOMY))
        val id = e.launch("hello", androidProjectRoot(), ProviderId.OPENAI)
        settle()

        val events = repo.getEvents(id)
        val understand = events.filter { it.phase == MissionPhase.UNDERSTAND_TASK }
        assertTrue(understand.any { it.kind == EventKind.PHASE_STARTED })
        assertTrue(understand.any { it.kind == EventKind.PHASE_SUCCEEDED })
        assertTrue(events.any { it.kind == EventKind.PHASE_PROGRESS })
        assertTrue(events.any { it.kind == EventKind.MISSION_PAUSED })
    }

    @Test
    fun `state exposes phase statuses after settle`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.HIGH_AUTONOMY))
        e.launch("hello", androidProjectRoot(), ProviderId.OPENAI)
        settle()

        val s = e.state.value
        assertEquals(MissionStatus.PAUSED, s.status)
        assertEquals(MissionPhase.IMPLEMENT, s.currentPhase)
        MissionPhase.ORDERED.take(6).forEach { ph ->
            assertEquals(
                "phase $ph",
                PhaseStatus.SUCCEEDED,
                s.phaseStatuses[ph]
            )
        }
        assertNotNull(s.percent)
        assertTrue(s.percent!! in 0..99)
    }

    @Test
    fun `recoverable list surfaces running missions for resume`() = runTest {
        val repo = FakeMissionRepository()
        // Simulate crash: mission stuck RUNNING
        repo.createMission(
            Mission(
                id = "crash",
                goal = "g",
                projectPath = "/tmp",
                projectType = ProjectType.UNKNOWN,
                providerId = ProviderId.OPENAI,
                autonomy = AutonomyLevel.ASK,
                status = MissionStatus.RUNNING,
                currentPhase = MissionPhase.PLAN_APPROACH,
                failureReason = null,
                createdAt = 0,
                updatedAt = 1
            )
        )
        val e = engine(repo)
        val recoverable = e.listRecoverable()
        assertEquals(listOf("crash"), recoverable.map { it.id })
    }

    @Test
    fun `plan phase summary is local heuristic not model claim`() = runTest {
        val repo = FakeMissionRepository()
        val e = engine(repo, FakeRegistry(initialAutonomy = AutonomyLevel.HIGH_AUTONOMY))
        val id = e.launch("Add logout button", androidProjectRoot(), ProviderId.OPENAI)
        settle()

        val plan = repo.getPhaseRuns(id).first { it.phase == MissionPhase.PLAN_APPROACH }
        assertTrue(plan.outputSummary!!.contains("Local heuristic plan"))
        assertFalse(plan.outputSummary!!.contains("model generated"))
    }

    @Test
    fun `handlers map covers exactly phases 1-6`() {
        val map = defaultPhaseHandlers()
        assertEquals(
            listOf(
                MissionPhase.UNDERSTAND_TASK,
                MissionPhase.DETECT_PROJECT,
                MissionPhase.SELECT_TEAM,
                MissionPhase.SELECT_PROVIDER,
                MissionPhase.PLAN_APPROACH,
                MissionPhase.USER_CHECKPOINT
            ),
            map.keys.sortedBy { it.index }
        )
        assertNull(map[MissionPhase.IMPLEMENT])
        assertNull(map[MissionPhase.COMPLETE])
    }

    @Test
    fun `ProjectTypeDetector integration matches engine detect phase`() {
        // Sanity: detector used by handler is the real M1 detector.
        val root = androidProjectRoot()
        assertEquals(ProjectType.ANDROID, ProjectTypeDetector.detect(java.io.File(root)))
    }
}
