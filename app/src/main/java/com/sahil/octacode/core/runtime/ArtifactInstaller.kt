package com.sahil.octacode.core.runtime

import com.sahil.octacode.core.runtime.extract.DebExtractor
import com.sahil.octacode.core.runtime.extract.ExtractResult
import com.sahil.octacode.core.runtime.extract.PathMapping
import com.sahil.octacode.core.runtime.extract.TarExtractor
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

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

    /**
     * Where the glibc root filesystem lives.
     *
     * A sibling of the prefix, never inside it: both trees carry `bin`, `lib`
     * and `usr`, and merging a bionic userland with a glibc one leaves
     * something that is neither — binaries bound to the wrong loader, and
     * libraries that resolve each other in an order nobody chose.
     */
    val rootfsRoot: File get() = File(prefix.parentFile, "rootfs")

    fun install(artifact: RuntimeArtifact, deb: File): InstallResult {
        val record = ledger.get(artifact.id)
            ?: return InstallResult.Failed(
                "${artifact.id} has no verification record — refusing to unpack unverified bytes"
            )
        if (record.isInstalled) return InstallResult.AlreadyInstalled(record.installedFiles.size)
        if (!deb.isFile) {
            return InstallResult.Failed("verified bytes for ${artifact.id} are missing from the cache")
        }
        if (artifact.kind == RuntimeArtifact.Kind.ROOTFS) return installRootfs(artifact, deb)

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
     * A root filesystem is not a package.
     *
     * Its entries are already the paths it will have on disk (`usr/bin/bash`),
     * so there is no Termux prefix to strip and nothing to relocate — but the
     * whole tree is only usable if all of it arrived, and a half-unpacked
     * Ubuntu is worse than none: it would start, answer some commands and fail
     * on the rest, which is the least honest state of all. So it is extracted
     * to staging and swapped in whole, and the ledger record is written only
     * after that swap succeeded.
     */
    private fun installRootfs(artifact: RuntimeArtifact, archive: File): InstallResult {
        val staging = File(stagingRoot, artifact.id)
        staging.deleteRecursively()
        staging.mkdirs()

        val extracted = try {
            GZIPInputStream(archive.inputStream().buffered()).use { gunzipped ->
                TarExtractor.extract(
                    gunzipped, staging, rootfsSafetyCap(archive.length()), ::rootfsPath
                )
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            return InstallResult.Failed(
                "Unpacking ${artifact.id} ${artifact.version} — ${e.message ?: "unknown error"}"
            )
        }

        if (extracted.isEmpty) {
            staging.deleteRecursively()
            return InstallResult.Failed(
                "${artifact.id} ${artifact.version} contained no filesystem — unexpected image layout"
            )
        }

        // Top-level entries only. The ledger claims "this app put these here";
        // naming all 3400 of them says the same thing at a hundred times the
        // size, and removal recurses either way.
        val topLevel = extracted.files
            .map { it.substringBefore('/') }
            .filter { it.isNotEmpty() }
            .distinct()

        return try {
            if (rootfsRoot.exists() && !rootfsRoot.deleteRecursively()) {
                // An if-expression rather than an early return: returning out
                // of the middle of a try block that is itself the return value
                // is legal but hard to read, and this file has one such
                // pattern already.
                InstallResult.Failed(
                    "Could not clear the previous ${artifact.id}. Remove it and try again — " +
                        "the verified bytes are still cached."
                )
            } else {
                if (!staging.renameTo(rootfsRoot)) {
                    // renameTo fails across some filesystems; copying is slower
                    // but produces the same tree.
                    staging.copyRecursively(rootfsRoot, overwrite = true)
                }
                ledger.markInstalled(artifact.id, topLevel, extracted.skipped)
                staging.deleteRecursively()
                InstallResult.Installed(topLevel.size, extracted.skipped, extracted.bytesWritten)
            }
        } catch (e: Exception) {
            InstallResult.Failed(
                "Installing ${artifact.id} — ${e.message ?: "unknown error"}. " +
                    "The verified bytes stay cached, so retrying re-unpacks without re-downloading."
            )
        }
    }

    /**
     * Where one archive entry belongs in a root filesystem.
     *
     * The paths are already final, so this is nearly a no-op — but it is a
     * no-op *on purpose*, written in one readable place rather than implied by
     * an inline lambda. Traversal and symlink escapes are TarExtractor's job
     * and it checks every entry, root filesystem or package.
     */
    private fun rootfsPath(path: String): PathMapping {
        val cleaned = path.removePrefix("./").removePrefix("/").trimEnd('/')
        return if (cleaned.isEmpty() || cleaned == ".") PathMapping.Ignore
        else PathMapping.Place(cleaned)
    }

    /** An Ubuntu OCI root unpacks to roughly 3.5x; this caps a hostile one. */
    private fun rootfsSafetyCap(compressedBytes: Long): Long =
        maxOf(512L * 1024 * 1024, compressedBytes * 16)

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
