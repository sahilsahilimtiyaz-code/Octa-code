package com.sahil.octacode.core.runtime

import com.sahil.octacode.core.runtime.extract.DebExtractor
import com.sahil.octacode.core.runtime.extract.ExtractResult
import java.io.File
import java.nio.file.Files

sealed interface InstallResult {
    data class Installed(val files: Int, val skipped: Int, val bytesWritten: Long) : InstallResult
    data class AlreadyInstalled(val files: Int) : InstallResult
    data class Failed(val reason: String) : InstallResult
}

sealed interface UninstallResult {
    data class Removed(val files: Int, val keptForOthers: Int) : UninstallResult
    data class NotInstalled(val reason: String) : UninstallResult
    data class Failed(val reason: String) : UninstallResult
}

/**
 * Turns verified bytes into files under the prefix, and back again.
 *
 * Unpacking happens in a staging directory first. If a package is malformed we
 * discard the staging tree and the prefix never hears about it, so a failure
 * cannot leave half a toolchain behind claiming to be installed.
 */
class ArtifactInstaller(
    private val prefix: File,
    private val ledger: RuntimeLedger
) {
    private val stagingRoot: File get() = File(prefix.parentFile, "staging")

    fun install(artifact: RuntimeArtifact, deb: File): InstallResult {
        val record = ledger.get(artifact.id)
            ?: return InstallResult.Failed(
                "${artifact.id} has no verification record — refusing to unpack unverified bytes"
            )
        if (record.isInstalled) return InstallResult.AlreadyInstalled(record.installedFiles.size)
        if (!deb.isFile) {
            return InstallResult.Failed("verified bytes for ${artifact.id} are missing from the cache")
        }

        val staging = File(stagingRoot, artifact.id)
        staging.deleteRecursively()
        staging.mkdirs()

        val extracted = try {
            DebExtractor.extract(deb, staging, DebExtractor.safetyCap(deb.length()))
        } catch (e: Exception) {
            staging.deleteRecursively()
            return InstallResult.Failed(
                "Unpacking ${artifact.id} ${artifact.version} — ${e.message ?: "unknown error"}"
            )
        }

        if (extracted.isEmpty) {
            staging.deleteRecursively()
            return InstallResult.Failed(
                "${artifact.id} ${artifact.version} contains nothing under " +
                    "${DebExtractor.TERMUX_PREFIX} — unexpected package layout"
            )
        }

        val moved = ArrayList<String>(extracted.files.size)
        return try {
            moveIntoPlace(staging, extracted, moved)
            ledger.markInstalled(artifact.id, moved, extracted.skipped)
            staging.deleteRecursively()
            InstallResult.Installed(moved.size, extracted.skipped, extracted.bytesWritten)
        } catch (e: Exception) {
            // Roll back rather than recording a half-installed package: leaving
            // these files would make the run look finished when it was not, and
            // would leave data nothing could ever clean up.
            for (relative in moved) File(prefix, relative).delete()
            staging.deleteRecursively()
            InstallResult.Failed(
                "Installing ${artifact.id} ${artifact.version} — ${e.message ?: "unknown error"}. " +
                    "The verified bytes stay cached, so retrying re-unpacks without re-downloading."
            )
        }
    }

    /**
     * Fills [moved] as it goes rather than at the end, so a failure partway
     * through still knows exactly what this attempt placed and can take it
     * back. Returning the list instead would hand the caller an empty one on
     * the very path where it needs it.
     */
    private fun moveIntoPlace(
        staging: File,
        extracted: ExtractResult,
        moved: MutableList<String>
    ) {
        for (dir in extracted.directories) {
            File(prefix, dir).mkdirs()
        }
        for (relative in extracted.files) {
            val source = File(staging, relative)
            val destination = File(prefix, relative)

            if (destination.exists() || isSymlink(destination)) {
                if (destination.isDirectory && !isSymlink(destination)) {
                    error("'$relative' would have to replace a directory")
                }
                if (!destination.delete()) error("could not replace ${destination.path}")
            }
            destination.parentFile?.mkdirs()

            if (isSymlink(source)) {
                Files.createSymbolicLink(destination.toPath(), Files.readSymbolicLink(source.toPath()))
            } else if (!source.renameTo(destination)) {
                source.copyTo(destination, overwrite = true)
                source.delete()
            }
            moved += relative
        }
    }

    /**
     * Removes what [id] unpacked and nothing more: a path another package also
     * claims is kept, and said so, because deleting it would break something
     * the user never asked us to touch.
     */
    fun uninstall(id: String): UninstallResult {
        val record = ledger.get(id)
            ?: return UninstallResult.NotInstalled("$id is not in the ledger")
        if (!record.isInstalled) {
            return UninstallResult.NotInstalled("$id is verified but not unpacked — nothing to remove")
        }

        val claimedByOthers = ledger.filesClaimedByOthers(id)
        var removed = 0
        var kept = 0
        val removedPaths = ArrayList<String>()

        for (relative in record.installedFiles) {
            if (relative in claimedByOthers) {
                kept++
                continue
            }
            val file = File(prefix, relative)
            if (file.isDirectory && !isSymlink(file)) continue
            if (file.delete() || !file.exists()) {
                removed++
                removedPaths += relative
            } else {
                kept++
            }
        }

        // Directories only go once nothing needs them, deepest first. This loop
        // deliberately refuses to touch anything that is not a directory:
        // `installedFiles` also holds regular files, and one of those may be the
        // shared path just spared above.
        val candidates = (record.installedFiles + removedPaths.map { parentOf(it) })
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.count { c -> c == '/' } }
        for (relative in candidates) {
            if (relative in claimedByOthers) continue
            val directory = File(prefix, relative)
            if (directory.isDirectory && !isSymlink(directory)) directory.delete()
        }

        ledger.markUninstalled(id)
        return UninstallResult.Removed(files = removed, keptForOthers = kept)
    }

    /** Clears the whole prefix and every staging remnant. Irreversible. */
    fun removeAll() {
        prefix.deleteRecursively()
        stagingRoot.deleteRecursively()
        prefix.mkdirs()
    }

    private fun parentOf(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index <= 0) "" else path.substring(0, index)
    }

    private fun isSymlink(file: File): Boolean =
        try {
            Files.isSymbolicLink(file.toPath())
        } catch (e: Exception) {
            false
        }
}
