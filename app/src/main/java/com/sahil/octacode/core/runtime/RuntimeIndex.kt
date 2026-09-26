package com.sahil.octacode.core.runtime

import java.io.File

/**
 * Answers "where is tool X?" from what has actually been unpacked.
 *
 * Backed purely by the ledger's recorded paths, so it can only ever point at
 * files this app verified and installed itself — never at anything that merely
 * happens to be lying around on the device.
 *
 * Execution is R3's business: PrefixShell asks this for bash/dash/sh and runs
 * whatever comes back, so resolving through the ledger is what guarantees a
 * shell is started from files this app verified rather than from the host.
 * A returned path still means only "installed by us and present on disk" —
 * nothing here proves the bytes are a working program.
 */
class RuntimeIndex(
    private val prefix: File,
    private val ledger: RuntimeLedger
) {
    /** Directories that would go first on a PATH built from the runtime. */
    fun pathDirectories(): List<File> =
        listOf(File(prefix, "bin"), File(prefix, "sbin"))
            .filter { it.isDirectory }

    /**
     * Absolute path of [command] if an installed package provides it.
     *
     * Only `bin/` and `sbin/` are considered: indexing every installed file
     * would let `which("COPYING")` resolve to a licence document.
     * A dangling symlink resolves to null rather than to a path that cannot work.
     */
    fun commandPath(command: String): File? {
        if (command.isBlank() || command.contains('/')) return null
        val candidates = ledger.snapshot().values
            .asSequence()
            .flatMap { it.installedFiles.asSequence() }
            .filter { it == "bin/$command" || it == "sbin/$command" }
            .distinct()
            .toList()
        if (candidates.isEmpty()) return null

        // Prefer the plain `bin/` spelling when a package ships both.
        val chosen = candidates.firstOrNull { it.startsWith("bin/") } ?: candidates.first()
        val file = File(prefix, chosen)
        return if (file.isFile) file else null
    }

    /**
     * Every command the installed runtime can name, sorted for stable display.
     * A path that has since gone missing is left out rather than advertised.
     */
    fun commands(): List<String> =
        ledger.snapshot().values
            .flatMap { it.installedFiles }
            .filter { it.startsWith("bin/") || it.startsWith("sbin/") }
            .map { it.substringAfterLast('/') }
            .distinct()
            .filter { File(prefix, "bin/$it").exists() || File(prefix, "sbin/$it").exists() }
            .sorted()

    /** How many packages are unpacked — the honest "is it installed" count. */
    fun installedPackageCount(): Int = ledger.installedIds().size
}
