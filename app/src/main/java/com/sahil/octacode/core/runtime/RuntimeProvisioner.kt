package com.sahil.octacode.core.runtime

import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives installation of runtime groups: download → verify → unpack.
 *
 * Contract:
 *  - Only artifacts whose SHA-256 matched the pinned value enter the ledger.
 *  - Only ledgered artifacts get unpacked, and only unpacked ones count as
 *    installed — "downloaded" and "on disk" are never conflated.
 *  - A group stops at the first failure and says why (never "succeeded" anyway).
 *  - Re-running an install is idempotent: anything already verified AND unpacked
 *    is skipped, and a byte-perfect file already in the cache is re-verified with
 *    no network. That is also the recovery path if the ledger file is ever lost
 *    or damaged, or if an earlier unpack failed and left nothing behind.
 */
class RuntimeProvisioner(
    private val manifest: RuntimeManifest,
    private val ledger: RuntimeLedger,
    private val downloader: ArtifactDownloader,
    private val installer: ArtifactInstaller,
    private val cacheDir: File
) {
    // CoroutineExceptionHandler, not just SupervisorJob: SupervisorJob only
    // stops a failure cancelling SIBLINGS, it does not consume the exception.
    // Without a handler every throw here — a ledger that cannot be written
    // because storage is full, an unexpected I/O failure — reached Android's
    // default uncaught handler and killed the process, which is why tapping
    // Install could throw the user out of the app with nothing on screen.
    // The handler turns that into the same visible, retryable error the
    // deliberate failure paths already produce.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, throwable ->
                _state.update { s ->
                    s.copy(
                        busy = false,
                        activeGroupId = null,
                        progress = null,
                        lastError = ProvisionError(
                            groupId = s.activeGroupId,
                            itemId = null,
                            reason = "Install stopped: " +
                                (throwable.message?.takeIf { it.isNotBlank() }
                                    ?: throwable::class.simpleName
                                    ?: "unknown error") +
                                ". Everything already verified is kept, so pressing " +
                                "Install again resumes without re-downloading.",
                            retryable = true
                        )
                    )
                }
            }
    )
    private val _state = MutableStateFlow(ProvisionerState(fetched = ledger.snapshot()))
    val state: StateFlow<ProvisionerState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        if (ledger.wasCorrupt) {
            _state.update {
                it.copy(
                    lastError = ProvisionError(
                        groupId = null,
                        itemId = null,
                        reason = "Stored verification record was unreadable. Every artifact will be " +
                            "re-checked against its pinned checksum before use — press Install to re-verify.",
                        retryable = true
                    )
                )
            }
        }
    }

    fun group(groupId: String): RuntimeGroup? = manifest.group(groupId)

    /** True while any install is running (only one runs at a time). */
    fun isBusy(): Boolean = _state.value.busy

    /** Install a single group (the per-group button on the Runtime screen). */
    fun install(groupId: String) {
        if (manifest.group(groupId) == null) {
            fail(null, null, "Unknown runtime group '$groupId'", retryable = false)
            return
        }
        runQueue(listOf(groupId))
    }

    /**
     * Install every group that is part of the default runtime.
     *
     * Optional groups are left out, deliberately. `optional` used to be a label
     * the screen drew and nothing else — no group carried it, so the flag never
     * had to mean anything. It does now: the glibc root filesystem is 29 MB
     * that nothing in the default runtime needs, and quietly adding it to the
     * "install everything" button would spend a tenth more of the user's data
     * and their time on a capability they have not asked for. Each optional
     * group still has its own row, with its own real byte count and its own
     * button — the choice stays theirs to make.
     */
    fun installAll() = runQueue(manifest.groups.filterNot { it.optional }.map { it.id })

    /**
     * True when this artifact still owes the user work: not fetched yet, or
     * fetched but never (successfully) unpacked.
     */
    private fun needsWork(artifact: RuntimeArtifact): Boolean {
        val record = ledger.get(artifact.id) ?: return true
        return !record.isInstalled
    }

    private fun runQueue(groupIds: List<String>) {
        if (_state.value.busy) return

        // Up-front pass: decide whether there is anything to do at all, so `busy`
        // can be published before the coroutine starts. Group membership is only
        // a hint here — the real plan is re-resolved per group inside the run.
        val planned = groupIds.filter { id ->
            manifest.group(id)?.items?.any { needsWork(it) } == true
        }

        if (planned.isEmpty()) {
            _state.update { it.copy(fetched = ledger.snapshot(), lastError = null) }
            return
        }

        _state.update {
            it.copy(
                busy = true,
                activeGroupId = planned.first(),
                lastError = null,
                progress = null
            )
        }

        job = scope.launch {
            try {
                for (groupId in planned) {
                    val group = manifest.group(groupId) ?: continue
                    // Re-resolved HERE: an artifact already handled by an earlier
                    // group in this same queue must not be downloaded or unpacked twice.
                    val pending = group.items.filter { needsWork(it) }
                    if (pending.isEmpty()) continue
                    // First failure ends the whole queue — nothing after it is
                    // claimed as done, and everything verified so far is kept.
                    if (!fetchGroup(group, pending)) return@launch
                }
            } finally {
                _state.update {
                    it.copy(busy = false, activeGroupId = null, progress = null, fetched = ledger.snapshot())
                }
            }
        }
    }

    /**
     * Downloads and verifies every pending artifact of one group, in order.
     * Returns false if it stopped early — [fail] has already recorded the real
     * reason, and the caller tears the run down.
     */
    private suspend fun fetchGroup(group: RuntimeGroup, pending: List<RuntimeArtifact>): Boolean {
        val groupId = group.id
        val groupTotal = pending.sumOf { it.size }
        var groupDone = 0L

        for ((index, artifact) in pending.withIndex()) {
            currentCoroutineContext().ensureActive()
            val target = File(cacheDir, artifact.cacheName)
            _state.update {
                it.copy(
                    progress = GroupProgress(
                        groupId = groupId,
                        itemId = artifact.id,
                        itemIndex = index + 1,
                        itemCount = pending.size,
                        itemBytesDone = 0L,
                        itemBytesTotal = artifact.size,
                        groupBytesDone = groupDone,
                        groupBytesTotal = groupTotal
                    )
                )
            }

            val outcome = downloader.download(
                url = manifest.urlFor(artifact),
                expectedSha256 = artifact.sha256,
                expectedSize = artifact.size,
                target = target
            ) { done, _ ->
                _state.update { s ->
                    val running = s.progress
                    if (s.activeGroupId == groupId && running != null && running.itemId == artifact.id) {
                        s.copy(
                            progress = running.copy(
                                itemBytesDone = done,
                                itemBytesTotal = artifact.size,
                                groupBytesDone = groupDone + done
                            )
                        )
                    } else {
                        s
                    }
                }
            }

            when (outcome) {
                is ArtifactDownloader.Outcome.Verified -> {
                    ledger.markVerified(artifact)
                    // Verified means the bytes are good; it does not mean the
                    // tool is on disk yet. Unpack it now or say why not.
                    when (val installed = installer.install(artifact, target)) {
                        is InstallResult.Failed -> {
                            fail(groupId, artifact.id, installed.reason, retryable = true)
                            return false
                        }
                        // A few refused entries do not make a 4000-file package
                        // unusable; they are counted on the record and shown.
                        is InstallResult.Installed -> Unit
                        is InstallResult.AlreadyInstalled -> Unit
                    }
                    groupDone += artifact.size
                    _state.update {
                        it.copy(
                            fetched = ledger.snapshot(),
                            progress = it.progress?.copy(
                                itemBytesDone = artifact.size,
                                groupBytesDone = groupDone
                            )
                        )
                    }
                }
                is ArtifactDownloader.Outcome.HashMismatch -> {
                    fail(
                        groupId, artifact.id,
                        "Checksum mismatch on ${artifact.id} ${artifact.version} — expected " +
                            "${outcome.expected.take(12)}…, received ${outcome.actual.take(12)}…. " +
                            "The file was deleted rather than trusted.",
                        retryable = true
                    )
                    return false
                }
                is ArtifactDownloader.Outcome.HttpError -> {
                    fail(
                        groupId, artifact.id,
                        "HTTP ${outcome.status} fetching ${artifact.id} ${artifact.version} — ${outcome.message}",
                        retryable = outcome.status == 429 || outcome.status in 500..599
                    )
                    return false
                }
                is ArtifactDownloader.Outcome.Failed -> {
                    fail(
                        groupId, artifact.id,
                        "Downloading ${artifact.id} ${artifact.version} — ${outcome.reason}",
                        retryable = true
                    )
                    return false
                }
            }
        }
        return true
    }

    /** Stop the in-flight run. Bytes already fetched stay on disk and resume later. */
    fun cancel() {
        val active = job ?: return
        if (!active.isActive) return
        val groupId = _state.value.activeGroupId
        active.cancel()
        _state.update {
            it.copy(
                lastError = ProvisionError(
                    groupId = groupId,
                    itemId = null,
                    reason = "Stopped. Downloaded bytes are kept — Install resumes from there.",
                    retryable = true
                )
            )
        }
    }

    /**
     * Unpack every installed artifact of a group back out of the prefix.
     * Nothing is deleted that another package still claims.
     */
    fun uninstall(groupId: String) {
        if (_state.value.busy) return
        val group = manifest.group(groupId)
        if (group == null) {
            fail(null, null, "Unknown runtime group '$groupId'", retryable = false)
            return
        }
        _state.update {
            it.copy(busy = true, activeGroupId = groupId, lastError = null, progress = null)
        }
        job = scope.launch {
            var problem: String? = null
            try {
                for (artifact in group.items) {
                    currentCoroutineContext().ensureActive()
                    when (val result = installer.uninstall(artifact.id)) {
                        is UninstallResult.Failed -> {
                            problem = result.reason
                            break
                        }
                        else -> Unit
                    }
                }
            } finally {
                _state.update {
                    it.copy(
                        busy = false,
                        activeGroupId = null,
                        progress = null,
                        fetched = ledger.snapshot(),
                        lastError = problem?.let { reason ->
                            ProvisionError(groupId, null, reason, retryable = false)
                        }
                    )
                }
            }
        }
    }

    /** Drop every verified record, cached artifact and unpacked file. Irreversible. */
    fun clearAll() {
        job?.cancel()
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
        installer.removeAll()
        ledger.clear()
        _state.update { ProvisionerState(fetched = ledger.snapshot()) }
    }

    fun dismissError() {
        _state.update { it.copy(lastError = null) }
    }

    private fun fail(groupId: String?, itemId: String?, reason: String, retryable: Boolean) {
        _state.update { it.copy(lastError = ProvisionError(groupId, itemId, reason, retryable)) }
    }
}
