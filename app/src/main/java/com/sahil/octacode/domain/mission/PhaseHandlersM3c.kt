package com.sahil.octacode.domain.mission

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.ProjectType
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.security.SecretRedactor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

// M3c phase handlers 7–16: real writes, review gate, skip-honest local steps,
// process-backed test/build/install when tools exist, snapshot rollback.

private fun modelFor(id: ProviderId): String = when (id) {
    ProviderId.OPENAI -> "gpt-4o-mini"
    ProviderId.CUSTOM -> "default"
    ProviderId.CLAUDE -> "default"
    ProviderId.GEMINI -> "default"
}

private fun providerNotReadyReason(status: ProviderStatus): String = when (status) {
    is ProviderStatus.Ready -> "Ready"
    is ProviderStatus.MissingKey -> "Unavailable — ${status.reason}"
    is ProviderStatus.Misconfigured -> "Unavailable — ${status.reason}"
    is ProviderStatus.Unavailable -> "Unavailable — ${status.reason}"
}

private object ImplementHandler : PhaseHandler {
    override val phase = MissionPhase.IMPLEMENT

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        if (!root.isDirectory) {
            return PhaseOutcome.Failed(
                "Project path is not a directory: ${context.mission.projectPath}",
                retryable = false
            )
        }

        val providerId = context.mission.providerId
        val status = context.registry.providerStatus(providerId)
        if (status !is ProviderStatus.Ready) {
            return PhaseOutcome.Failed(
                "Cannot implement: provider ${providerId.title} is ${providerNotReadyReason(status)}",
                retryable = status is ProviderStatus.MissingKey || status is ProviderStatus.Misconfigured
            )
        }
        val provider = context.providers[providerId]
            ?: return PhaseOutcome.Failed(
                "Cannot implement: no adapter registered for $providerId",
                retryable = false
            )

        context.emitProgress("Requesting full-file edits from ${providerId.title}…")
        val tree = FileDiff.listProjectTree(root)
        val plan = context.priorSummaries[MissionPhase.PLAN_APPROACH]
            ?: context.mission.goal
        val userPrompt = buildString {
            appendLine("Goal: ${context.mission.goal}")
            appendLine("Project type: ${context.mission.projectType.name}")
            appendLine("Project path: ${context.mission.projectPath}")
            appendLine("Plan: ${plan.take(2000)}")
            appendLine("Files (${tree.size}):")
            tree.forEach { appendLine(" - $it") }
        }

        val raw = StringBuilder()
        try {
            provider.chatStream(
                ChatRequest(
                    messages = listOf(
                        ChatMessage(ChatRole.SYSTEM, EditProtocol.systemPrompt()),
                        ChatMessage(ChatRole.USER, userPrompt)
                    ),
                    model = modelFor(providerId),
                    maxTokens = 8192,
                    temperature = 0.1
                )
            ).collect { chunk ->
                if (chunk.delta.isNotEmpty()) raw.append(chunk.delta)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return PhaseOutcome.Failed(
                "Provider stream failed during implement: ${t.message ?: t::class.java.simpleName}",
                retryable = true
            )
        }

        val edits = EditProtocol.parse(raw.toString())
        if (edits.isEmpty()) {
            return PhaseOutcome.Failed(
                "Model produced no OCTA_EDIT blocks — nothing to write (honest fail, no empty success)",
                retryable = true
            )
        }

        data class Planned(val edit: EditProtocol.FileEdit, val target: File)
        val planned = mutableListOf<Planned>()
        val unsafe = mutableListOf<String>()
        edits.forEach { edit ->
            val target = FileDiff.resolveSafe(root, edit.path)
            if (target == null) unsafe += edit.path else planned += Planned(edit, target)
        }
        if (unsafe.isNotEmpty()) {
            return PhaseOutcome.Failed(
                "Unsafe path(s) rejected: ${unsafe.joinToString()}",
                retryable = false
            )
        }

        context.emitProgress("Snapshotting ${planned.size} file(s) before write…")
        context.snapshots.capture(
            missionId = context.mission.id,
            projectRoot = root,
            relativePaths = planned.map { it.edit.path }
        )

        var written = 0
        planned.forEach { item ->
            context.emitProgress("Writing ${item.edit.path}")
            val target = item.target
            target.parentFile?.mkdirs()
            val before = if (target.isFile) target.readText() else null
            val beforeHash = before?.let { FileDiff.sha256(it) } ?: FileDiff.NEW_FILE_HASH
            target.writeText(item.edit.content)
            val afterHash = FileDiff.sha256(item.edit.content)
            val patch = FileDiff.unifiedPatch(item.edit.path, before, item.edit.content)
            context.repository.upsertDiff(
                MissionDiff(
                    id = 0,
                    missionId = context.mission.id,
                    phase = MissionPhase.IMPLEMENT,
                    path = item.edit.path.replace('\\', '/'),
                    beforeHash = beforeHash,
                    afterHash = afterHash,
                    patchText = SecretRedactor.redact(patch),
                    decision = DiffDecision.PENDING
                )
            )
            written++
        }

        val paths = planned.joinToString { it.edit.path }
        return PhaseOutcome.Succeeded(
            "Implemented $written file(s) via full replacement: $paths"
        )
    }
}

private object ReviewDiffHandler : PhaseHandler {
    override val phase = MissionPhase.REVIEW_DIFF

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val diffs = context.repository.getDiffs(context.mission.id)
        if (diffs.isEmpty()) {
            return PhaseOutcome.Failed(
                "No diffs to review — implement wrote nothing",
                retryable = false
            )
        }
        val pending = diffs.filter { it.decision == DiffDecision.PENDING }
        val rejected = diffs.filter { it.decision == DiffDecision.REJECTED }
        val accepted = diffs.filter { it.decision == DiffDecision.ACCEPTED }

        if (rejected.isNotEmpty() && pending.isEmpty()) {
            return PhaseOutcome.Failed(
                "User rejected ${rejected.size}/${diffs.size} diff(s): " +
                    rejected.joinToString { it.path },
                retryable = false
            )
        }
        if (pending.isEmpty()) {
            return PhaseOutcome.Succeeded(
                "Review: ${accepted.size} accepted, ${rejected.size} rejected of ${diffs.size}"
            )
        }

        if (context.autonomy == AutonomyLevel.HIGH_AUTONOMY) {
            pending.forEach {
                context.repository.updateDiffDecision(it.id, DiffDecision.ACCEPTED)
            }
            return PhaseOutcome.Succeeded(
                "Auto-accepted ${pending.size} diff(s) (HIGH_AUTONOMY): " +
                    pending.joinToString { it.path }
            )
        }

        context.emitProgress("Waiting for user to accept ${pending.size} file change(s)…")
        val payload = CheckpointPayload(
            planSummary = "Review ${pending.size} change(s): " +
                pending.take(12).joinToString { it.path },
            approvalsRequired = listOf("diff-review")
        )
        return PhaseOutcome.AwaitingApproval(
            payload = payload,
            onApproved = {
                pending.forEach {
                    context.repository.updateDiffDecision(it.id, DiffDecision.ACCEPTED)
                }
                "Accepted ${pending.size} diff(s): ${pending.joinToString { it.path }}"
            },
            onRejected = {
                pending.forEach {
                    context.repository.updateDiffDecision(it.id, DiffDecision.REJECTED)
                }
            }
        )
    }
}

private object FormatHandler : PhaseHandler {
    override val phase = MissionPhase.FORMAT

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val npm = context.processRunner.which("npm")
        val packageJson = File(root, "package.json")
        if (npm != null && packageJson.isFile) {
            val text = packageJson.readText()
            if (text.contains("\"format\"")) {
                context.emitProgress("Running npm run format…")
                val result = context.processRunner.run(
                    listOf(npm, "run", "format"),
                    workingDir = root,
                    timeoutMs = 120_000
                )
                return if (result.isSuccess) {
                    PhaseOutcome.Succeeded("Formatted via npm run format")
                } else {
                    PhaseOutcome.Failed("npm run format failed: ${result.tail()}", retryable = true)
                }
            }
        }
        // No known formatter in toolchain — honest skip (maySkip=true).
        return PhaseOutcome.Skipped(
            "No configured formatter available (project has no format script / tool on PATH)"
        )
    }
}

private object AnalyzeImpactHandler : PhaseHandler {
    override val phase = MissionPhase.ANALYZE_IMPACT

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val diffs = context.repository.getDiffs(context.mission.id)
        val paths = diffs.map { it.path }
        context.emitProgress("Analyzing impact of ${paths.size} changed file(s)…")

        val linesTouched = paths.sumOf { p ->
            val f = FileDiff.resolveSafe(root, p)
            if (f != null && f.isFile) f.readText().lineSequence().count() else 0
        }
        val dirs = paths.map { p -> p.substringBefore('/', "") }.filter { it.isNotEmpty() }.distinct()
        val type = context.mission.projectType
        val notes = buildList {
            add("Local heuristic impact analysis (no model claim)")
            add("Changed files: ${paths.size} (${paths.take(20).joinToString()})")
            add("Approx. lines in targets: $linesTouched")
            if (dirs.isNotEmpty()) add("Top-level areas: ${dirs.joinToString()}")
            when (type) {
                ProjectType.ANDROID ->
                    add("Android: unit tests + assembleDebug cover most regressions")
                ProjectType.NODE ->
                    add("Node: package.json / src changes affect install + test scripts")
                ProjectType.PYTHON ->
                    add("Python: module changes affect import graph and tests")
                ProjectType.UNKNOWN ->
                    add("UNKNOWN project: impact limited to listed files only")
            }
            add("Goal: ${context.mission.goal.take(160)}")
        }
        notes.forEachIndexed { i, n -> context.emitProgress("Impact ${i + 1}/${notes.size}: $n") }
        return PhaseOutcome.Succeeded(notes.joinToString(" | "))
    }
}

private object TestHandler : PhaseHandler {
    override val phase = MissionPhase.TEST

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val type = context.mission.projectType

        val wrapper = File(root, "gradlew")
        val java = context.processRunner.which("java")
        if (wrapper.isFile && java != null) {
            context.emitProgress("Running ./gradlew testDebugUnitTest…")
            val result = context.processRunner.run(
                listOf(wrapper.absolutePath, "testDebugUnitTest"),
                workingDir = root,
                timeoutMs = 300_000
            )
            return if (result.isSuccess) {
                PhaseOutcome.Succeeded("Gradle unit tests passed\n${result.tail(400)}")
            } else {
                PhaseOutcome.Failed(
                    "Tests failed (exit=${result.exitCode}): ${result.tail()}",
                    retryable = false
                )
            }
        }

        val npm = context.processRunner.which("npm")
        if (npm != null && File(root, "package.json").isFile) {
            context.emitProgress("Running npm test…")
            val result = context.processRunner.run(
                listOf(npm, "test"),
                workingDir = root,
                timeoutMs = 300_000
            )
            return if (result.isSuccess) {
                PhaseOutcome.Succeeded("npm test passed\n${result.tail(400)}")
            } else {
                PhaseOutcome.Failed(
                    "npm test failed (exit=${result.exitCode}): ${result.tail()}",
                    retryable = false
                )
            }
        }

        val pytest = context.processRunner.which("pytest")
        if (pytest != null && type == ProjectType.PYTHON) {
            context.emitProgress("Running pytest…")
            val result = context.processRunner.run(
                listOf(pytest, "-q"),
                workingDir = root,
                timeoutMs = 300_000
            )
            return if (result.isSuccess) {
                PhaseOutcome.Succeeded("pytest passed\n${result.tail(400)}")
            } else {
                PhaseOutcome.Failed(
                    "pytest failed (exit=${result.exitCode}): ${result.tail()}",
                    retryable = false
                )
            }
        }

        return PhaseOutcome.Skipped(
            "No test toolchain available (need gradlew+java, npm, or pytest on PATH)"
        )
    }
}

private object BuildHandler : PhaseHandler {
    override val phase = MissionPhase.BUILD

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val wrapper = File(root, "gradlew")
        val java = context.processRunner.which("java")
        val cmd: List<String>? = when {
            wrapper.isFile && java != null ->
                listOf(wrapper.absolutePath, "assembleDebug")
            java != null && context.processRunner.which("gradle") != null ->
                listOf(context.processRunner.which("gradle")!!, "assembleDebug")
            else -> null
        }
        if (cmd == null) {
            return PhaseOutcome.Skipped(
                "No build toolchain available (gradlew/gradle + java not found)"
            )
        }

        if (MissionPhase.approvalRequired(MissionPhase.BUILD, context.autonomy)) {
            context.emitProgress("Build approval required (autonomy=${context.autonomy})")
            return PhaseOutcome.AwaitingApproval(
                CheckpointPayload(
                    planSummary = "Run build: ${cmd.joinToString(" ")} in ${context.mission.projectPath}",
                    approvalsRequired = listOf("build")
                )
            )
        }

        context.emitProgress("Building: ${cmd.joinToString(" ")}…")
        val result = context.processRunner.run(cmd, workingDir = root, timeoutMs = 600_000)
        return if (result.isSuccess) {
            PhaseOutcome.Succeeded("Build succeeded\n${result.tail(500)}")
        } else {
            PhaseOutcome.Failed(
                "Build failed (exit=${result.exitCode}): ${result.tail()}",
                retryable = false
            )
        }
    }
}

private object InstallHandler : PhaseHandler {
    override val phase = MissionPhase.INSTALL

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val adb = context.processRunner.which("adb")
        if (adb == null) {
            return PhaseOutcome.Skipped("no adb")
        }
        val apk = File(root, "app/build/outputs/apk/debug/app-debug.apk")
        if (!apk.isFile) {
            return PhaseOutcome.Skipped("No debug APK at ${apk.relativeTo(root)} — build skipped or failed earlier")
        }

        if (MissionPhase.approvalRequired(MissionPhase.INSTALL, context.autonomy)) {
            context.emitProgress("Install approval required (autonomy=${context.autonomy})")
            return PhaseOutcome.AwaitingApproval(
                CheckpointPayload(
                    planSummary = "adb install -r ${apk.name}",
                    approvalsRequired = listOf("install")
                )
            )
        }

        context.emitProgress("Installing APK via adb…")
        val result = context.processRunner.run(
            listOf(adb, "install", "-r", apk.absolutePath),
            workingDir = root,
            timeoutMs = 180_000
        )
        return if (result.isSuccess) {
            PhaseOutcome.Succeeded("Installed ${apk.name}\n${result.tail(400)}")
        } else {
            PhaseOutcome.Failed(
                "adb install failed (exit=${result.exitCode}): ${result.tail()}",
                retryable = true
            )
        }
    }
}

private object VerifyHandler : PhaseHandler {
    override val phase = MissionPhase.VERIFY

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val root = File(context.mission.projectPath)
        val diffs = context.repository.getDiffs(context.mission.id)
        if (diffs.isEmpty()) {
            return PhaseOutcome.Failed("Verify failed: no recorded file changes", retryable = false)
        }
        context.emitProgress("Verifying ${diffs.size} file(s) against after-hashes…")
        val mismatches = mutableListOf<String>()
        diffs.forEach { d ->
            if (d.decision == DiffDecision.REJECTED) return@forEach
            val f = FileDiff.resolveSafe(root, d.path)
            if (f == null || !f.isFile) {
                mismatches += "${d.path} (missing)"
                return@forEach
            }
            if (d.afterHash == FileDiff.NEW_FILE_HASH) {
                // created file — existence is enough (content may be reformatted later)
                return@forEach
            }
            val actual = FileDiff.sha256(f.readText())
            if (actual != d.afterHash) {
                mismatches += "${d.path} (hash ${actual.take(12)} != ${d.afterHash.take(12)})"
            }
        }
        return if (mismatches.isEmpty()) {
            val accepted = diffs.count { it.decision == DiffDecision.ACCEPTED }
            PhaseOutcome.Succeeded(
                "Verified ${diffs.size} change(s) ($accepted accepted) — files match expected state"
            )
        } else {
            PhaseOutcome.Failed(
                "Verify failed for ${mismatches.size} file(s): ${mismatches.joinToString()}",
                retryable = false
            )
        }
    }
}

private object RollbackHandler : PhaseHandler {
    override val phase = MissionPhase.ROLLBACK

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        // Reached only on the healthy path (failures auto-rollback in the engine).
        val has = context.snapshots.hasSnapshot(context.mission.id)
        return if (has) {
            PhaseOutcome.Skipped(
                "Mission healthy after verify — snapshot kept, no rollback needed"
            )
        } else {
            PhaseOutcome.Skipped("No snapshot recorded for this mission")
        }
    }
}

private object CompleteHandler : PhaseHandler {
    override val phase = MissionPhase.COMPLETE

    override suspend fun execute(context: PhaseContext): PhaseOutcome {
        val diffs = context.repository.getDiffs(context.mission.id)
        val accepted = diffs.count { it.decision == DiffDecision.ACCEPTED }
        context.emitProgress("Closing mission with ${diffs.size} recorded change(s)…")
        return PhaseOutcome.Succeeded(
            "Mission complete: ${diffs.size} change(s), $accepted accepted — " +
                (diffs.joinToString { it.path }.ifBlank { "no file paths" })
        )
    }
}

/** M3c handlers for indices 7–16. */
fun m3cPhaseHandlers(): List<PhaseHandler> = listOf(
    ImplementHandler,
    ReviewDiffHandler,
    FormatHandler,
    AnalyzeImpactHandler,
    TestHandler,
    BuildHandler,
    InstallHandler,
    VerifyHandler,
    RollbackHandler,
    CompleteHandler
)
