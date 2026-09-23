package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.capability.ProjectTypeDetector
import com.sahil.octacode.core.capability.ProviderStatus
import java.io.File

// M3b: real phase handlers for indices 1–6. No stubs — each does local work
// and reports honestly (Unavailable / UNKNOWN / MissingKey pass through).

private object UnderstandTaskHandler : PhaseHandler {
    override val phase = MissionPhase.UNDERSTAND_TASK

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val goal = context.mission.goal.trim()
        if (goal.isEmpty()) {
            return PhaseOutcome.Failed("Goal is empty", retryable = false)
        }
        if (context.mission.projectPath.isBlank()) {
            return PhaseOutcome.Failed("Project path is empty", retryable = false)
        }
        context.emitProgress("Goal accepted: ${goal.take(120)}")
        context.emitProgress("Path: ${context.mission.projectPath}")
        return PhaseOutcome.Succeeded(
            "Understood task: $goal | path=${context.mission.projectPath} | autonomy=${context.autonomy}"
        )
    }
}

private object DetectProjectHandler : PhaseHandler {
    override val phase = MissionPhase.DETECT_PROJECT

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        if (!root.exists()) {
            return PhaseOutcome.Failed(
                "Project path does not exist: ${context.mission.projectPath}",
                retryable = false
            )
        }
        if (!root.isDirectory) {
            return PhaseOutcome.Failed(
                "Project path is not a directory: ${context.mission.projectPath}",
                retryable = false
            )
        }
        val type = ProjectTypeDetector.detect(root)
        context.emitProgress("Detected project type: ${type.name}")
        if (type == ProjectType.UNKNOWN) {
            context.emitProgress("Honest result: UNKNOWN — no known project markers found")
        }
        return PhaseOutcome.Succeeded(
            summary = "Detected project type: ${type.name}",
            patchMission = { copy(projectType = type) }
        )
    }
}

private object SelectTeamHandler : PhaseHandler {
    override val phase = MissionPhase.SELECT_TEAM

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        // Deterministic team from real project type — not a model hallucination.
        val team = when (context.mission.projectType) {
            ProjectType.ANDROID -> listOf("Architect", "Android Engineer", "Reviewer")
            ProjectType.NODE -> listOf("Architect", "Backend Engineer", "Reviewer")
            ProjectType.PYTHON -> listOf("Architect", "Python Engineer", "Reviewer")
            ProjectType.UNKNOWN -> listOf("Analyst", "General Engineer", "Reviewer")
        }
        context.emitProgress("Team: ${team.joinToString(" · ")}")
        return PhaseOutcome.Succeeded("Team (${context.mission.projectType.name}): ${team.joinToString(", ")}")
    }
}

private object SelectProviderHandler : PhaseHandler {
    override val phase = MissionPhase.SELECT_PROVIDER

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val id = context.mission.providerId
        val status = context.registry.providerStatus(id)
        val text = when (status) {
            is ProviderStatus.Ready -> "Ready — ${status.reason}"
            is ProviderStatus.MissingKey -> "Unavailable — ${status.reason}"
            is ProviderStatus.Misconfigured -> "Unavailable — ${status.reason}"
            is ProviderStatus.Unavailable -> "Unavailable — ${status.reason}"
        }
        context.emitProgress("${id.title}: $text")
        // Selection always succeeds (we chose a runtime); readiness is reported
        // honestly so implement phases (M3c+) can gate on it without lying now.
        return PhaseOutcome.Succeeded("Selected ${id.title} [$id]: $text")
    }
}

private object PlanApproachHandler : PhaseHandler {
    override val phase = MissionPhase.PLAN_APPROACH

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val goal = context.mission.goal.trim()
        val type = context.mission.projectType
        val providerLine = context.priorSummaries[MissionPhase.SELECT_PROVIDER]
            ?: "provider not recorded"
        val steps = buildList {
            add("Scope: ${goal.take(160)}")
            add("Project: ${type.name} at ${context.mission.projectPath}")
            add("Team phase summary: ${context.priorSummaries[MissionPhase.SELECT_TEAM] ?: "—"}")
            add("Provider: $providerLine")
            add("Approach: inspect tree → minimal edit → verify (local heuristic, no model call in M3b)")
            when (type) {
                ProjectType.ANDROID -> {
                    add("Android: touch Gradle/module sources under app/ when implementing")
                    add("Android: run unit tests before assembleDebug")
                }
                ProjectType.NODE -> {
                    add("Node: edit package.json scripts carefully")
                    add("Node: run npm test / npm run lint when implementing")
                }
                ProjectType.PYTHON -> {
                    add("Python: respect pyproject/requirements pins")
                    add("Python: run pytest/unittest when implementing")
                }
                ProjectType.UNKNOWN -> {
                    add("UNKNOWN project: map files first; do not assume build system")
                }
            }
            add("Checkpoint next: confirm plan before any file writes (phase 7+, M3c)")
        }
        steps.forEachIndexed { i, s ->
            context.emitProgress("Plan ${i + 1}/${steps.size}: $s")
        }
        return PhaseOutcome.Succeeded(
            "Local heuristic plan (${steps.size} steps): ${steps.joinToString(" | ")}"
        )
    }
}

private object UserCheckpointHandler : PhaseHandler {
    override val phase = MissionPhase.USER_CHECKPOINT

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val plan = context.priorSummaries[MissionPhase.PLAN_APPROACH]
            ?: context.mission.goal
        val approvals = buildList {
            add("goal")
            add("projectPath")
            if (context.autonomy == com.sahil.octacode.core.capability.AutonomyLevel.ASK ||
                context.autonomy == com.sahil.octacode.core.capability.AutonomyLevel.BALANCED
            ) {
                add("implement (M3c)")
            }
        }
        val payload = CheckpointPayload(planSummary = plan.take(500), approvalsRequired = approvals)

        if (MissionPhase.approvalRequired(MissionPhase.USER_CHECKPOINT, context.autonomy)) {
            context.emitProgress("Checkpoint required (autonomy=${context.autonomy})")
            return PhaseOutcome.AwaitingApproval(payload)
        }
        context.emitProgress("Checkpoint auto-approved (autonomy=${context.autonomy})")
        return PhaseOutcome.Succeeded("Checkpoint auto-approved (autonomy=${context.autonomy})")
    }
}

/** Full pipeline handlers — phases 1–6 (M3b) + 7–16 (M3c). */
fun defaultPhaseHandlers(): Map<MissionPhase, PhaseHandler> = (
    listOf(
        UnderstandTaskHandler,
        DetectProjectHandler,
        SelectTeamHandler,
        SelectProviderHandler,
        PlanApproachHandler,
        UserCheckpointHandler
    ) + m3cPhaseHandlers()
).associateBy { it.phase }
