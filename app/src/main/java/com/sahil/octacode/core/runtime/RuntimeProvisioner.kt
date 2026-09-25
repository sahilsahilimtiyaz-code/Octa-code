package com.sahil.octacode.core.runtime

import java.io.File
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
 * Drives installation of runtime groups: download → verify → record.
 *
 * Contract:
 *  - Only artifacts whose SHA-256 matched the pinned value enter the ledger.
 *  - A group stops at the first failure and says why (never "succeeded" anyway).
 *  - Re-running an install is idempotent: anything already verified is skipped,
 *    and a byte-perfect file already in the cache is re-verified with no network.
 *    That is also the recovery path if the ledger file is ever lost or damaged.
 */
class RuntimeProvisioner(
    private val manifest: RuntimeManifest,
    private val ledger: RuntimeLedger,
    private val downloader: ArtifactDownloader,
    private val cacheDir: File
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    fun install(groupId: String) {
        val group = manifest.group(groupId)
        if (group == null) {
            fail(null, null, "Unknown runtime group '$groupId'", retryable = false)
            return
        }
        if (_state.value.busy) return

        val pending = group.items.filterNot { ledger.contains(it.id) }
        if (pending.isEmpty()) {
            _state.update { it.copy(fetched = ledger.snapshot(), lastError = null) }
            return
        }

        val groupTotal = pending.sumOf { it.size }
        var groupDone = 0L

        // Busy is published BEFORE the coroutine starts so callers never observe a
        // launched-but-not-yet-running install as idle.
        _state.update {
            it.copy(busy = true, activeGroupId = groupId, lastError = null, progress = null)
        }

        job = scope.launch {
            try {
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
                            return@launch
                        }
                        is ArtifactDownloader.Outcome.HttpError -> {
                            fail(
                                groupId, artifact.id,
                                "HTTP ${outcome.status} fetching ${artifact.id} ${artifact.version} — ${outcome.message}",
                                retryable = outcome.status == 429 || outcome.status in 500..599
                            )
                            return@launch
                        }
                        is ArtifactDownloader.Outcome.Failed -> {
                            fail(
                                groupId, artifact.id,
                                "Downloading ${artifact.id} ${artifact.version} — ${outcome.reason}",
                                retryable = true
                            )
                            return@launch
                        }
                    }
                }
            } finally {
                _state.update {
                    it.copy(busy = false, activeGroupId = null, progress = null, fetched = ledger.snapshot())
                }
            }
        }
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

    /** Drop every verified record and cached artifact. Irreversible. */
    fun clearAll() {
        job?.cancel()
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
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
