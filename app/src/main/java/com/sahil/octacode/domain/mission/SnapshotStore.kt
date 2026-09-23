package com.sahil.octacode.domain.mission

import java.io.File

/**
 * M3c app-private snapshot of files before implement writes.
 * Rollback restores these originals (Option A decision).
 */
interface SnapshotStore {
    /** Copy current contents of [relativePaths] under [projectRoot] into the store. */
    suspend fun capture(missionId: String, projectRoot: File, relativePaths: List<String>)

    suspend fun hasSnapshot(missionId: String): Boolean

    /**
     * Restore pre-implement state for [missionId] into [projectRoot].
     * Files that did not exist before are deleted if present now.
     */
    suspend fun restore(missionId: String, projectRoot: File): RestoreResult

    /** Drop snapshot data after mission terminal (optional hygiene). */
    suspend fun clear(missionId: String)
}

data class RestoreResult(val restored: Int, val deletedCreated: Int) {
    val total: Int get() = restored + deletedCreated
}

/** In-memory store for unit tests / default engine wiring. */
class InMemorySnapshotStore : SnapshotStore {
    private data class Entry(val relativePath: String, val existed: Boolean, val content: String?)

    private val snapshots = mutableMapOf<String, List<Entry>>()

    override suspend fun capture(missionId: String, projectRoot: File, relativePaths: List<String>) {
        val entries = relativePaths.map { rel ->
            val f = File(projectRoot, rel)
            if (f.isFile) Entry(rel, existed = true, content = f.readText())
            else Entry(rel, existed = false, content = null)
        }
        snapshots[missionId] = entries
    }

    override suspend fun hasSnapshot(missionId: String): Boolean =
        snapshots.containsKey(missionId)

    override suspend fun restore(missionId: String, projectRoot: File): RestoreResult {
        val entries = snapshots[missionId] ?: return RestoreResult(0, 0)
        var restored = 0
        var deleted = 0
        entries.forEach { e ->
            val target = File(projectRoot, e.relativePath)
            if (e.existed && e.content != null) {
                target.parentFile?.mkdirs()
                target.writeText(e.content)
                restored++
            } else if (!e.existed && target.exists()) {
                target.delete()
                deleted++
            }
        }
        return RestoreResult(restored, deleted)
    }

    override suspend fun clear(missionId: String) {
        snapshots.remove(missionId)
    }
}
