package com.sahil.octacode.data.mission

import com.sahil.octacode.domain.mission.RestoreResult
import com.sahil.octacode.domain.mission.SnapshotStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M3c app-private snapshot dir (Option A): filesDir/mission_snapshots/{missionId}/
 * Manifest lines: `1\trelative/path` (existed) or `0\trelative/path` (created later).
 */
class FileSnapshotStore(
    private val storeRoot: File
) : SnapshotStore {

    override suspend fun capture(
        missionId: String,
        projectRoot: File,
        relativePaths: List<String>
    ) = withContext(Dispatchers.IO) {
        val dir = missionDir(missionId)
        val filesRoot = File(dir, "files")
        if (filesRoot.exists()) filesRoot.deleteRecursively()
        filesRoot.mkdirs()
        val manifest = StringBuilder()
        relativePaths.distinct().forEach { rel ->
            val safe = FileDiffSafe.resolve(projectRoot, rel) ?: return@forEach
            if (safe.isFile) {
                val dest = File(filesRoot, rel)
                dest.parentFile?.mkdirs()
                safe.copyTo(dest, overwrite = true)
                manifest.append("1\t").append(rel).append('\n')
            } else {
                manifest.append("0\t").append(rel).append('\n')
            }
        }
        File(dir, "manifest.txt").writeText(manifest.toString())
    }

    override suspend fun hasSnapshot(missionId: String): Boolean =
        withContext(Dispatchers.IO) { File(missionDir(missionId), "manifest.txt").isFile }

    override suspend fun restore(missionId: String, projectRoot: File): RestoreResult =
        withContext(Dispatchers.IO) {
            val manifest = File(missionDir(missionId), "manifest.txt")
            if (!manifest.isFile) return@withContext RestoreResult(0, 0)
            var restored = 0
            var deleted = 0
            manifest.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                val flag = line.substring(0, tab)
                val rel = line.substring(tab + 1)
                val target = FileDiffSafe.resolve(projectRoot, rel) ?: return@forEach
                if (flag == "1") {
                    val snap = File(File(missionDir(missionId), "files"), rel)
                    if (snap.isFile) {
                        target.parentFile?.mkdirs()
                        snap.copyTo(target, overwrite = true)
                        restored++
                    }
                } else if (target.exists()) {
                    target.delete()
                    deleted++
                }
            }
            RestoreResult(restored, deleted)
        }

    override suspend fun clear(missionId: String) = withContext(Dispatchers.IO) {
        missionDir(missionId).deleteRecursively()
        Unit
    }

    private fun missionDir(missionId: String): File =
        File(storeRoot, missionId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
}

/** Local path safety without pulling domain object into data helpers awkwardly. */
private object FileDiffSafe {
    fun resolve(root: File, relative: String): File? {
        val normalized = relative.trim().replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        val target = File(root, normalized)
        return try {
            val rootPath = root.canonicalFile.toPath()
            val targetPath = target.canonicalFile.toPath()
            if (!targetPath.startsWith(rootPath)) null else target
        } catch (_: Exception) {
            null
        }
    }
}
