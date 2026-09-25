package com.sahil.octacode.core.runtime

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Proof that a specific artifact's bytes matched its pinned SHA-256.
 * This is the ONLY thing that lets a download be treated as usable.
 *
 * [installedFiles] adds a second, independent claim: that those bytes were
 * afterwards unpacked into the prefix, and which paths that produced. An empty
 * list therefore means "verified but not unpacked" — the two are never
 * conflated, so the screen can say exactly what is and is not on disk yet.
 */
@Serializable
data class ArtifactRecord(
    val id: String,
    val version: String,
    val sha256: String,
    val bytes: Long,
    val verifiedAtMillis: Long,
    /** Paths this package placed under the prefix, relative to it. */
    val installedFiles: List<String> = emptyList(),
    val installedAtMillis: Long = 0,
    /** Entries dropped because they had no meaning here (surfaced, never silent). */
    val skippedEntries: Int = 0
) {
    val isInstalled: Boolean get() = installedFiles.isNotEmpty()
}

/** Versioned ledger file — kept separate from schema so a corrupt file is detectable. */
@Serializable
private data class LedgerFile(
    val schema: Int = SCHEMA,
    val artifacts: Map<String, ArtifactRecord> = emptyMap()
) {
    companion object {
        const val SCHEMA = 1
    }
}

/**
 * Which artifacts have been fetched AND checksum-verified on this device.
 *
 * Persistence is a small JSON file written atomically (tmp + rename) so a crash
 * mid-write can never leave a half-trusted ledger behind. If the file is unreadable
 * we deliberately start EMPTY: the downloader re-hashes whatever is already in the
 * cache, so a damaged ledger costs a re-verify, never a re-download, and never lets
 * an unverified byte through.
 */
class RuntimeLedger(
    private val file: File,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var cache: MutableMap<String, ArtifactRecord>? = null

    @Volatile
    private var corrupt = false

    /**
     * Set if the on-disk ledger could not be parsed (surfaced, not swallowed).
     * Reading it loads the ledger first — otherwise a fresh instance would report
     * "clean" for a file it had never looked at.
     */
    val wasCorrupt: Boolean
        get() {
            loaded()
            return corrupt
        }

    @Synchronized
    fun snapshot(): Map<String, ArtifactRecord> = HashMap(loaded())

    @Synchronized
    fun contains(id: String): Boolean = loaded().containsKey(id)

    @Synchronized
    fun get(id: String): ArtifactRecord? = loaded()[id]

    @Synchronized
    fun markVerified(artifact: RuntimeArtifact): ArtifactRecord {
        // Re-verifying must not erase what is already unpacked on disk: a lost
        // or damaged ledger rebuilds itself by re-hashing the cache.
        val previous = loaded()[artifact.id]
        val record = ArtifactRecord(
            id = artifact.id,
            version = artifact.version,
            sha256 = artifact.sha256,
            bytes = artifact.size,
            verifiedAtMillis = clock(),
            installedFiles = previous?.installedFiles.orEmpty(),
            installedAtMillis = previous?.installedAtMillis ?: 0,
            skippedEntries = previous?.skippedEntries ?: 0
        )
        loaded()[artifact.id] = record
        persist()
        return record
    }

    /**
     * Records what unpacking [id] actually put on disk.
     * Returns null if the artifact was never verified — an unverified artifact
     * is never allowed to claim any path.
     */
    @Synchronized
    fun markInstalled(id: String, files: List<String>, skipped: Int): ArtifactRecord? {
        val current = loaded()[id] ?: return null
        val record = current.copy(
            installedFiles = files,
            installedAtMillis = clock(),
            skippedEntries = skipped
        )
        loaded()[id] = record
        persist()
        return record
    }

    /**
     * Drops the unpacked claim but keeps the verification, so uninstalling does
     * not throw away proof that the cached bytes are good.
     */
    @Synchronized
    fun markUninstalled(id: String) {
        val current = loaded()[id] ?: return
        loaded()[id] = current.copy(
            installedFiles = emptyList(),
            installedAtMillis = 0,
            skippedEntries = 0
        )
        persist()
    }

    /**
     * Paths every package EXCEPT [id] lays claim to. An uninstall may only
     * delete what no other package still needs — removing a shared file would
     * quietly break something the user did not ask us to touch.
     */
    @Synchronized
    fun filesClaimedByOthers(id: String): Set<String> =
        loaded()
            .filterKeys { it != id }
            .values
            .flatMapTo(HashSet()) { it.installedFiles }

    @Synchronized
    fun installedIds(): List<String> = loaded().values.filter { it.isInstalled }.map { it.id }

    @Synchronized
    fun remove(id: String) {
        if (loaded().remove(id) != null) persist()
    }

    @Synchronized
    fun clear() {
        cache = mutableMapOf()
        corrupt = false
        persist()
    }

    @Synchronized
    fun totalBytes(): Long = loaded().values.sumOf { it.bytes }

    /**
     * Synchronised in its own right: [wasCorrupt] calls this without holding the
     * lock, and a double first-load would hand two callers different maps (a lost
     * update on the next write).
     */
    @Synchronized
    private fun loaded(): MutableMap<String, ArtifactRecord> {
        cache?.let { return it }
        val map = mutableMapOf<String, ArtifactRecord>()
        if (file.isFile) {
            val parsed = runCatching {
                json.decodeFromString(LedgerFile.serializer(), file.readText())
            }
            parsed.onSuccess { map.putAll(it.artifacts) }
                .onFailure {
                    // Trust nothing we could not read — artifacts get re-verified below.
                    corrupt = true
                    map.clear()
                }
        }
        cache = map
        return map
    }

    private fun persist() {
        val artifacts = HashMap(loaded())
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(LedgerFile.serializer(), LedgerFile(artifacts = artifacts)))
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse to rename over an existing file.
            file.delete()
            if (!tmp.renameTo(file)) {
                error("could not persist runtime ledger to ${file.absolutePath}")
            }
        }
    }
}
