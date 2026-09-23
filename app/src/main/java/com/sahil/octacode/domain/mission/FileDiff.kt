package com.sahil.octacode.domain.mission

import java.io.File
import java.security.MessageDigest

// M3c file utilities: content hashing, safe path resolution, patch text for diffs.
object FileDiff {

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Marker hash for files that did not exist before implement (new files). */
    const val NEW_FILE_HASH: String = "__new__"

    fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.trim().replace('\\', '/')
        if (normalized.isEmpty()) return false
        if (normalized.startsWith("/")) return false
        if (normalized.length >= 2 && normalized[1] == ':') return false
        val parts = normalized.split('/')
        if (parts.any { it == ".." }) return false
        if (parts.any { it.isEmpty() && parts.size > 1 }) return false
        return true
    }

    /**
     * Resolve [relative] under [root], refusing escapes outside the project.
     * Returns null when unsafe or when canonical paths leave the root.
     */
    fun resolveSafe(root: File, relative: String): File? {
        if (!isSafeRelativePath(relative)) return null
        val target = File(root, relative.replace('\\', '/'))
        return try {
            val rootPath = root.canonicalFile.toPath()
            val targetPath = target.canonicalFile.toPath()
            if (!targetPath.startsWith(rootPath)) null else target
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Full-replacement style patch (Option A): show whole-file before/after
     * when content differs. Truncated for UI safety.
     */
    fun unifiedPatch(path: String, before: String?, after: String): String {
        if (before != null && before == after) return ""
        val maxLines = 400
        val sb = StringBuilder()
        sb.append("--- a/").append(path).append('\n')
        sb.append("+++ b/").append(path).append('\n')
        if (before == null) {
            sb.append("@@ new file @@\n")
            after.lineSequence().take(maxLines).forEach { sb.append('+').append(it).append('\n') }
            if (after.lineSequence().count() > maxLines) {
                sb.append("+… ").append(after.lineSequence().count() - maxLines).append(" more lines\n")
            }
        } else {
            sb.append("@@ full replace @@\n")
            before.lineSequence().take(maxLines).forEach { sb.append('-').append(it).append('\n') }
            after.lineSequence().take(maxLines).forEach { sb.append('+').append(it).append('\n') }
            val beforeCount = before.lineSequence().count()
            val afterCount = after.lineSequence().count()
            if (beforeCount > maxLines || afterCount > maxLines) {
                sb.append("@@ truncated (before=$beforeCount after=$afterCount lines) @@\n")
            }
        }
        return sb.toString()
    }

    /** Bounded project tree for model context — skips build noise. */
    fun listProjectTree(root: File, maxEntries: Int = 80): List<String> {
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<String>()
        val skipDirs = setOf(
            ".git", ".gradle", ".idea", "build", "node_modules",
            "captures", ".externalNativeBuild", ".cxx", "__pycache__"
        )

        fun walk(dir: File, depth: Int) {
            if (out.size >= maxEntries || depth > 7) return
            val children = dir.listFiles() ?: return
            children.sortedBy { it.name.lowercase() }.forEach { f ->
                if (out.size >= maxEntries) return
                if (f.name.startsWith(".") && f.name != ".") {
                    if (f.isDirectory && f.name == ".github") walk(f, depth + 1)
                    return@forEach
                }
                if (f.isDirectory) {
                    if (f.name in skipDirs) return@forEach
                    walk(f, depth + 1)
                } else {
                    out += f.relativeTo(root).path.replace('\\', '/')
                }
            }
        }

        walk(root, 0)
        return out
    }
}
