package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * M3b observable engine state. Persisted truth lives in [MissionRepository] —
 * this is the in-memory projection the UI collects.
 */
data class MissionEngineState(
    val activeMissionId: String? = null,
    val status: MissionStatus = MissionStatus.DRAFT,
    val currentPhase: MissionPhase? = null,
    val phaseStatuses: Map<MissionPhase, PhaseStatus> = emptyMap(),
    val message: String = "",
    val percent: Int? = null,
    val awaitingApproval: Boolean = false,
    val busy: Boolean = false,
    val lastError: String? = null
)

/** Outcome of a single phase handler. No fake success — failures carry a reason. */
sealed interface PhaseOutcome {
    data class Succeeded(
        val summary: String,
        val patchMission: (Mission.() -> Mission)? = null
    ) : PhaseOutcome

    data class Failed(val reason: String, val retryable: Boolean = true) : PhaseOutcome
    data class AwaitingApproval(
        val payload: CheckpointPayload,
        val onApproved: (suspend () -> String)? = null,
        val onRejected: (suspend () -> Unit)? = null
    ) : PhaseOutcome

    /** Honest skip for maySkip phases (no toolchain, healthy rollback, …). */
    data class Skipped(val reason: String) : PhaseOutcome
}

/** Runtime passed into each phase handler (pure domain — no Android types). */
data class PhaseContext(
    val mission: Mission,
    val autonomy: AutonomyLevel,
    val registry: CapabilityRegistry,
    val providers: Map<ProviderId, AiProvider>,
    val priorSummaries: Map<MissionPhase, String>,
    val repository: MissionRepository,
    val snapshots: SnapshotStore,
    val processRunner: ProcessRunner,
    val emitProgress: suspend (String) -> Unit
)

/** Contract for one pipeline phase. M3c ships handlers for indices 1–16. */
interface PhaseHandler {
    val phase: MissionPhase
    suspend fun execute(context: PhaseContext): PhaseOutcome
}

/**
 * M3c mission engine — observable, cancellable, persisted.
 * Runs the full 16-phase pipeline; auto-rolls back snapshots when a phase
 * fails after implement.
 */
class MissionEngine(
    private val repository: MissionRepository,
    private val registry: CapabilityRegistry,
    private val providers: Map<ProviderId, AiProvider>,
    private val handlers: Map<MissionPhase, PhaseHandler> = defaultPhaseHandlers(),
    private val snapshots: SnapshotStore = InMemorySnapshotStore(),
    private val processRunner: ProcessRunner = ProcessRunner.Unavailable,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = null
) {
    private val _state = MutableStateFlow(MissionEngineState())
    val state: StateFlow<MissionEngineState> = _state

    // Backstop: without a CoroutineExceptionHandler an unexpected throw in the
    // pipeline reached Android's default handler and killed the process. It
    // now lands in lastError, which MissionDetailScreen already renders.
    private val scope: CoroutineScope = scope ?: CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                _state.update { s ->
                    s.copy(
                        busy = false,
                        lastError = "Run stopped: " +
                            (throwable.message?.takeIf { it.isNotBlank() }
                                ?: throwable::class.simpleName
                                ?: "unknown error")
                    )
                }
            }
    )
    private var runJob: Job? = null
    private var approval: CompletableDeferred<Boolean>? = null

    /** Create a mission and start the pipeline. Returns mission id. */
    suspend fun launch(
        goal: String,
        projectPath: String,
        providerId: ProviderId
    ): String {
        require(goal.isNotBlank()) { "goal must not be blank" }
        require(projectPath.isNotBlank()) { "projectPath must not be blank" }
        val now = clock()
        val id = UUID.randomUUID().toString()
        val mission = Mission(
            id = id,
            goal = goal.trim(),
            projectPath = projectPath.trim(),
            projectType = ProjectType.UNKNOWN,
            providerId = providerId,
            autonomy = registry.autonomy.value,
            status = MissionStatus.DRAFT,
            currentPhase = MissionPhase.UNDERSTAND_TASK,
            failureReason = null,
            createdAt = now,
            updatedAt = now
        )
        repository.createMission(mission)
        start(id)
        return id
    }

    /** Start or resume a mission from its persisted currentPhase. */
    suspend fun start(missionId: String) {
        val mission = repository.getMission(missionId)
            ?: error("Mission not found: $missionId")
        if (mission.status.isTerminal) {
            _state.value = MissionEngineState(
                activeMissionId = missionId,
                status = mission.status,
                currentPhase = mission.currentPhase,
                message = mission.failureReason ?: mission.status.name,
                lastError = mission.failureReason
            )
            return
        }
        if (runJob?.isActive == true && _state.value.activeMissionId == missionId) return

        hydrateFromRepository(missionId)
        val wasPaused = mission.status == MissionStatus.PAUSED
        repository.updateMission(
            mission.copy(
                status = MissionStatus.RUNNING,
                failureReason = null,
                updatedAt = clock()
            )
        )
        if (wasPaused && mission.currentPhase != null) {
            emitEvent(missionId, mission.currentPhase, EventKind.MISSION_RESUMED, """{"reason":"resume"}""")
        }
        _state.value = _state.value.copy(
            status = MissionStatus.RUNNING,
            busy = true,
            awaitingApproval = false,
            lastError = null,
            message = "Running…"
        )
        runJob = scope.launch {
            runLoop(missionId)
        }
    }

    /** Soft-pause a running mission (checkpoint wait is separate). */
    fun pause() {
        val id = _state.value.activeMissionId ?: return
        if (_state.value.awaitingApproval) return
        runJob?.cancel(PauseRequest)
        _state.value = _state.value.copy(
            busy = false,
            awaitingApproval = false,
            message = "Paused"
        )
        scope.launch {
            val m = repository.getMission(id) ?: return@launch
            if (m.status == MissionStatus.RUNNING) {
                repository.updateMission(m.copy(status = MissionStatus.PAUSED, updatedAt = clock()))
                m.currentPhase?.let { setPhaseStatus(m, it, PhaseStatus.PENDING, null) }
                emitEvent(
                    id,
                    m.currentPhase ?: MissionPhase.UNDERSTAND_TASK,
                    EventKind.MISSION_PAUSED,
                    """{"reason":"paused by user"}"""
                )
            }
        }
    }

    /** Terminal cancel — never resumes. */
    fun cancel() {
        val id = _state.value.activeMissionId ?: return
        approval?.complete(false)
        approval = null
        runJob?.cancel(CancelRequest)
        _state.value = _state.value.copy(
            busy = false,
            awaitingApproval = false,
            status = MissionStatus.CANCELLED,
            message = "Cancelled"
        )
        scope.launch {
            val m = repository.getMission(id) ?: return@launch
            if (!m.status.isTerminal) {
                m.currentPhase?.let { ph ->
                    val existing = repository.getPhaseRuns(id).firstOrNull { it.phase == ph }
                    if (existing != null && existing.status == PhaseStatus.RUNNING) {
                        repository.upsertPhaseRun(
                            existing.copy(
                                status = PhaseStatus.CANCELLED,
                                endedAt = clock(),
                                errorReason = "cancelled"
                            )
                        )
                    }
                    emitEvent(id, ph, EventKind.PHASE_CANCELLED, """{"reason":"user cancel"}""")
                }
                repository.updateMission(
                    m.copy(
                        status = MissionStatus.CANCELLED,
                        updatedAt = clock(),
                        failureReason = "Cancelled by user"
                    )
                )
                emitEvent(
                    id,
                    m.currentPhase ?: MissionPhase.UNDERSTAND_TASK,
                    EventKind.MISSION_CANCELLED,
                    """{"reason":"user cancel"}"""
                )
            }
        }
    }

    /** Approve a waiting USER_CHECKPOINT. */
    fun approveCheckpoint() {
        if (!_state.value.awaitingApproval) return
        approval?.complete(true)
    }

    /** Reject a waiting checkpoint → mission FAILED with reason. */
    fun rejectCheckpoint() {
        if (!_state.value.awaitingApproval) return
        approval?.complete(false)
    }

    /** Load persisted mission + runs into state (detail open + recovery). */
    suspend fun hydrateFromRepository(missionId: String) {
        val mission = repository.getMission(missionId) ?: return
        val runs = repository.getPhaseRuns(missionId)
        val isActive = runJob?.isActive == true && _state.value.activeMissionId == missionId
        _state.value = MissionEngineState(
            activeMissionId = missionId,
            status = mission.status,
            currentPhase = mission.currentPhase ?: _state.value.currentPhase,
            phaseStatuses = runs.associate { it.phase to it.status },
            message = mission.failureReason ?: _state.value.message.ifEmpty { mission.status.name },
            percent = (mission.currentPhase ?: MissionPhase.UNDERSTAND_TASK).let {
                ((it.index - 1) * 100) / MissionPhase.TOTAL
            },
            awaitingApproval = _state.value.awaitingApproval && _state.value.activeMissionId == missionId,
            busy = isActive,
            lastError = mission.failureReason
        )
    }

    /** Missions left RUNNING by a crash — UI offers resume. */
    suspend fun listRecoverable(): List<Mission> = repository.listRecoverable()

    fun shutdown() {
        runJob?.cancel()
        approval?.complete(false)
        scope.coroutineContext[Job]?.cancel()
    }

    // —— internals ————————————————————————————————————————————————

    private suspend fun runLoop(missionId: String) {
        try {
            while (true) {
                val mission = repository.getMission(missionId) ?: return
                if (mission.status.isTerminal) {
                    _state.value = _state.value.copy(busy = false, status = mission.status)
                    return
                }
                val phase = mission.currentPhase
                if (phase == null) {
                    failMission(mission, "Mission has no current phase", retryable = false)
                    return
                }

                val handler = handlers[phase]
                if (handler == null) {
                    failPhase(mission, phase, "No handler registered for ${phase.name}", retryable = false)
                    failMission(mission, "No handler registered for phase ${phase.index}", retryable = false)
                    return
                }

                beginPhase(mission, phase)
                val prior = repository.getPhaseRuns(missionId)
                    .filter {
                        (it.status == PhaseStatus.SUCCEEDED || it.status == PhaseStatus.SKIPPED) &&
                            it.phase != phase
                    }
                    .associate { it.phase to (it.outputSummary ?: "") }

                val outcome = try {
                    handler.execute(
                        PhaseContext(
                            mission = mission,
                            autonomy = mission.autonomy,
                            registry = registry,
                            providers = providers,
                            priorSummaries = prior,
                            repository = repository,
                            snapshots = snapshots,
                            processRunner = processRunner,
                            emitProgress = { msg ->
                                emitEvent(missionId, phase, EventKind.PHASE_PROGRESS, progressJson(msg))
                                _state.value = _state.value.copy(message = msg)
                            }
                        )
                    )
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    failPhase(mission, phase, t.message ?: t::class.java.simpleName, retryable = false)
                    if (phase.index >= MissionPhase.IMPLEMENT.index) {
                        performAutoRollback(mission)
                    }
                    failMission(mission, t.message ?: "Handler crashed", retryable = false)
                    return
                }

                when (outcome) {
                    is PhaseOutcome.Succeeded -> {
                        succeedPhase(mission, phase, outcome.summary)
                        if (!advanceAfterPhase(missionId, phase, outcome.patchMission)) return
                    }

                    is PhaseOutcome.Skipped -> {
                        if (!phase.maySkip) {
                            failPhase(mission, phase, "Illegal skip: ${outcome.reason}", retryable = false)
                            failMission(mission, "Phase ${phase.name} cannot be skipped", retryable = false)
                            return
                        }
                        skipPhase(mission, phase, outcome.reason)
                        if (!advanceAfterPhase(missionId, phase, null)) return
                    }

                    is PhaseOutcome.Failed -> {
                        failPhase(mission, phase, outcome.reason, outcome.retryable)
                        if (phase.index >= MissionPhase.IMPLEMENT.index) {
                            performAutoRollback(mission)
                        }
                        failMission(mission, outcome.reason, outcome.retryable)
                        return
                    }

                    is PhaseOutcome.AwaitingApproval -> {
                        emitEvent(
                            missionId, phase, EventKind.CHECKPOINT_REQUIRED,
                            Json.encodeToString(CheckpointPayload.serializer(), outcome.payload)
                        )
                        repository.updateMission(
                            mission.copy(status = MissionStatus.PAUSED, updatedAt = clock())
                        )
                        _state.value = _state.value.copy(
                            status = MissionStatus.PAUSED,
                            awaitingApproval = true,
                            busy = false,
                            message = "Waiting for checkpoint approval",
                            currentPhase = phase,
                            percent = ((phase.index - 1) * 100) / MissionPhase.TOTAL
                        )
                        val deferred = CompletableDeferred<Boolean>()
                        approval = deferred
                        val ok = deferred.await()
                        approval = null
                        if (!ok) {
                            try {
                                outcome.onRejected?.invoke()
                            } catch (_: Throwable) {
                                // reject side-effects are best-effort; mission still fails
                            }
                            emitEvent(missionId, phase, EventKind.CHECKPOINT_REJECTED, """{"reason":"rejected"}""")
                            failPhase(mission, phase, "Checkpoint rejected by user", retryable = false)
                            if (phase.index >= MissionPhase.IMPLEMENT.index) {
                                performAutoRollback(mission)
                            }
                            failMission(mission, "Checkpoint rejected by user", retryable = false)
                            return
                        }
                        val approvedSummary = try {
                            outcome.onApproved?.invoke() ?: "Checkpoint approved"
                        } catch (t: CancellationException) {
                            throw t
                        } catch (t: Throwable) {
                            failPhase(mission, phase, t.message ?: "Approval side-effect failed", retryable = false)
                            if (phase.index >= MissionPhase.IMPLEMENT.index) {
                                performAutoRollback(mission)
                            }
                            failMission(mission, t.message ?: "Approval side-effect failed", retryable = false)
                            return
                        }
                        emitEvent(missionId, phase, EventKind.CHECKPOINT_APPROVED, """{"reason":"approved"}""")
                        val approved = repository.getMission(missionId) ?: return
                        succeedPhase(approved, phase, approvedSummary)
                        if (!advanceAfterPhase(missionId, phase, null)) return
                        _state.value = _state.value.copy(
                            status = MissionStatus.RUNNING,
                            awaitingApproval = false,
                            busy = true,
                            message = "Running…"
                        )
                    }
                }
            }
        } catch (t: CancellationException) {
            if (t === PauseRequest || t === CancelRequest) return
            val id = _state.value.activeMissionId
            if (id != null) {
                val m = repository.getMission(id)
                if (m != null && m.status == MissionStatus.RUNNING) {
                    repository.updateMission(
                        m.copy(
                            status = MissionStatus.PAUSED,
                            failureReason = "Engine interrupted",
                            updatedAt = clock()
                        )
                    )
                }
                _state.value = _state.value.copy(
                    busy = false,
                    status = MissionStatus.PAUSED,
                    message = "Interrupted"
                )
            }
        } catch (t: Throwable) {
            val id = _state.value.activeMissionId ?: return
            val m = repository.getMission(id)
            if (m != null && !m.status.isTerminal) {
                failMission(m, t.message ?: t::class.java.simpleName, retryable = false)
            }
        }
    }

    private suspend fun beginPhase(mission: Mission, phase: MissionPhase) {
        val now = clock()
        val existing = repository.getPhaseRuns(mission.id).firstOrNull { it.phase == phase }
        repository.upsertPhaseRun(
            if (existing == null) {
                PhaseRun(
                    id = 0,
                    missionId = mission.id,
                    phase = phase,
                    status = PhaseStatus.RUNNING,
                    startedAt = now,
                    endedAt = null,
                    outputSummary = null,
                    errorReason = null
                )
            } else {
                existing.copy(
                    status = PhaseStatus.RUNNING,
                    startedAt = now,
                    endedAt = null,
                    outputSummary = null,
                    errorReason = null
                )
            }
        )
        emitEvent(mission.id, phase, EventKind.PHASE_STARTED, """{"phase":"${phase.name}"}""")
        _state.value = _state.value.copy(
            activeMissionId = mission.id,
            status = MissionStatus.RUNNING,
            currentPhase = phase,
            busy = true,
            awaitingApproval = false,
            message = phase.title,
            percent = ((phase.index - 1) * 100) / MissionPhase.TOTAL,
            phaseStatuses = _state.value.phaseStatuses + (phase to PhaseStatus.RUNNING),
            lastError = null
        )
    }

    /** Advance currentPhase after success/skip/approve. Returns false when mission completed/stopped. */
    private suspend fun advanceAfterPhase(
        missionId: String,
        phase: MissionPhase,
        patchMission: (Mission.() -> Mission)?
    ): Boolean {
        val next = phase.next()
        if (next == null) {
            completeMission(missionId, phase)
            return false
        }
        val latest = repository.getMission(missionId) ?: return false
        val patched = patchMission?.let { latest.it() } ?: latest
        repository.updateMission(
            patched.copy(
                currentPhase = next,
                updatedAt = clock(),
                status = MissionStatus.RUNNING,
                failureReason = null
            )
        )
        _state.value = _state.value.copy(
            currentPhase = next,
            percent = ((next.index - 1) * 100) / MissionPhase.TOTAL,
            phaseStatuses = _state.value.phaseStatuses + (phase to
                (_state.value.phaseStatuses[phase] ?: PhaseStatus.SUCCEEDED))
        )
        return true
    }

    private suspend fun skipPhase(mission: Mission, phase: MissionPhase, reason: String) {
        val now = clock()
        val existing = repository.getPhaseRuns(mission.id).firstOrNull { it.phase == phase }
        repository.upsertPhaseRun(
            PhaseRun(
                id = existing?.id ?: 0,
                missionId = mission.id,
                phase = phase,
                status = PhaseStatus.SKIPPED,
                startedAt = existing?.startedAt ?: now,
                endedAt = now,
                outputSummary = reason,
                errorReason = null
            )
        )
        emitEvent(mission.id, phase, EventKind.PHASE_SKIPPED, progressJson(reason))
        _state.value = _state.value.copy(
            phaseStatuses = _state.value.phaseStatuses + (phase to PhaseStatus.SKIPPED),
            message = reason
        )
    }

    /**
     * After implement, any failure restores the app-private snapshot and
     * records ROLLBACK_PERFORMED (spec: rollback if failed).
     */
    private suspend fun performAutoRollback(mission: Mission) {
        try {
            val missionId = mission.id
            if (!snapshots.hasSnapshot(missionId)) return
            val root = java.io.File(mission.projectPath)
            if (!root.isDirectory) return
            val result = snapshots.restore(missionId, root)
            if (result.total == 0) return
            val reason = "Restored ${result.restored}, removed ${result.deletedCreated} created file(s)"
            val existing = repository.getPhaseRuns(missionId).firstOrNull {
                it.phase == MissionPhase.ROLLBACK
            }
            repository.upsertPhaseRun(
                PhaseRun(
                    id = existing?.id ?: 0,
                    missionId = missionId,
                    phase = MissionPhase.ROLLBACK,
                    status = PhaseStatus.SUCCEEDED,
                    startedAt = existing?.startedAt ?: clock(),
                    endedAt = clock(),
                    outputSummary = "Auto-rollback: $reason",
                    errorReason = null
                )
            )
            emitEvent(
                missionId,
                MissionPhase.ROLLBACK,
                EventKind.ROLLBACK_PERFORMED,
                progressJson(reason)
            )
            _state.value = _state.value.copy(
                message = "Rollback: $reason",
                phaseStatuses = _state.value.phaseStatuses +
                    (MissionPhase.ROLLBACK to PhaseStatus.SUCCEEDED)
            )
        } catch (t: Throwable) {
            try {
                emitEvent(
                    mission.id,
                    MissionPhase.ROLLBACK,
                    EventKind.ROLLBACK_PERFORMED,
                    progressJson("Rollback failed: ${t.message}")
                )
            } catch (_: Throwable) {
                // persistence already failing — avoid masking original error
            }
        }
    }

    private suspend fun succeedPhase(mission: Mission, phase: MissionPhase, summary: String) {
        val now = clock()
        val existing = repository.getPhaseRuns(mission.id).firstOrNull { it.phase == phase }
        repository.upsertPhaseRun(
            PhaseRun(
                id = existing?.id ?: 0,
                missionId = mission.id,
                phase = phase,
                status = PhaseStatus.SUCCEEDED,
                startedAt = existing?.startedAt ?: now,
                endedAt = now,
                outputSummary = summary,
                errorReason = null
            )
        )
        emitEvent(mission.id, phase, EventKind.PHASE_SUCCEEDED, progressJson(summary))
        _state.value = _state.value.copy(
            phaseStatuses = _state.value.phaseStatuses + (phase to PhaseStatus.SUCCEEDED),
            message = summary
        )
    }

    private suspend fun failPhase(
        mission: Mission,
        phase: MissionPhase,
        reason: String,
        retryable: Boolean
    ) {
        val now = clock()
        val existing = repository.getPhaseRuns(mission.id).firstOrNull { it.phase == phase }
        repository.upsertPhaseRun(
            PhaseRun(
                id = existing?.id ?: 0,
                missionId = mission.id,
                phase = phase,
                status = PhaseStatus.FAILED,
                startedAt = existing?.startedAt ?: now,
                endedAt = now,
                outputSummary = null,
                errorReason = reason
            )
        )
        emitEvent(
            mission.id, phase, EventKind.PHASE_FAILED,
            Json.encodeToString(PhaseFailurePayload.serializer(), PhaseFailurePayload(reason, retryable))
        )
        _state.value = _state.value.copy(
            phaseStatuses = _state.value.phaseStatuses + (phase to PhaseStatus.FAILED)
        )
    }

    private suspend fun failMission(mission: Mission, reason: String, retryable: Boolean) {
        repository.updateMission(
            mission.copy(status = MissionStatus.FAILED, failureReason = reason, updatedAt = clock())
        )
        emitEvent(
            mission.id,
            mission.currentPhase ?: MissionPhase.UNDERSTAND_TASK,
            EventKind.MISSION_FAILED,
            Json.encodeToString(PhaseFailurePayload.serializer(), PhaseFailurePayload(reason, retryable))
        )
        _state.value = _state.value.copy(
            status = MissionStatus.FAILED,
            busy = false,
            awaitingApproval = false,
            message = reason,
            lastError = reason,
            percent = 100
        )
    }

    private suspend fun completeMission(missionId: String, lastPhase: MissionPhase) {
        val m = repository.getMission(missionId) ?: return
        repository.updateMission(
            m.copy(status = MissionStatus.COMPLETE, failureReason = null, updatedAt = clock())
        )
        emitEvent(missionId, lastPhase, EventKind.MISSION_COMPLETED, """{"ok":true}""")
        _state.value = _state.value.copy(
            status = MissionStatus.COMPLETE,
            busy = false,
            awaitingApproval = false,
            message = "Mission complete",
            lastError = null,
            percent = 100
        )
    }

    private suspend fun setPhaseStatus(
        mission: Mission,
        phase: MissionPhase,
        status: PhaseStatus,
        error: String?
    ) {
        val existing = repository.getPhaseRuns(mission.id).firstOrNull { it.phase == phase }
        val now = clock()
        repository.upsertPhaseRun(
            PhaseRun(
                id = existing?.id ?: 0,
                missionId = mission.id,
                phase = phase,
                status = status,
                startedAt = existing?.startedAt ?: now,
                endedAt = if (status == PhaseStatus.PENDING) null else (existing?.endedAt ?: now),
                outputSummary = existing?.outputSummary,
                errorReason = error
            )
        )
        _state.value = _state.value.copy(
            phaseStatuses = _state.value.phaseStatuses + (phase to status)
        )
    }

    private suspend fun emitEvent(
        missionId: String,
        phase: MissionPhase,
        kind: EventKind,
        payloadJson: String = "{}"
    ) {
        repository.appendEvent(
            PhaseEvent(
                id = 0,
                missionId = missionId,
                phase = phase,
                timestamp = clock(),
                kind = kind,
                payloadJson = payloadJson
            )
        )
    }

    private fun progressJson(message: String): String =
        Json.encodeToString(PhaseProgressPayload.serializer(), PhaseProgressPayload(message))

    private object PauseRequest : CancellationException("pause")
    private object CancelRequest : CancellationException("cancel")
}
