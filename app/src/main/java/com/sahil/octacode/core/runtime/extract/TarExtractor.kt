package com.sahil.octacode.core.runtime.extract

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** Everything an extraction produced, in paths relative to the install root. */
data class ExtractResult(
    /** Regular files and symlinks created. */
    val files: List<String>,
    /** Directories that were created or already present. */
    val directories: List<String>,
    /** Entries deliberately not materialised (unsupported type or unsafe link). */
    val skipped: Int,
    val bytesWritten: Long
) {
    val isEmpty: Boolean get() = files.isEmpty()
}

/**
 * Streaming tar reader that will not write outside its root.
 *
 * Hand-written rather than delegated to a library, because every path and every
 * link target has to be judged before it touches the filesystem: a `.deb` is
 * untrusted input that arrives over the network, and "the archive said so" is
 * not a reason to write to `/sdcard`, or through a symlink that points there.
 */
/**
 * Maps a raw archive path to where it belongs under the root.
 * Top-level because Kotlin has no nested type aliases.
 */
typealias PathMapper = (String) -> PathMapping

object TarExtractor {

    private const val BLOCK = TarHeader.BLOCK

    fun extract(
        input: InputStream,
        root: File,
        maxBytes: Long,
        mapPath: PathMapper
    ): ExtractResult {
        root.mkdirs()
        if (!root.isDirectory) {
            throw ExtractionException("install root ${root.path} is not a directory")
        }

        val canonicalRoot = root.canonicalFile
        val header = ByteArray(BLOCK)
        val written = LinkedHashSet<String>()
        val directories = LinkedHashSet<String>()
        var skipped = 0
        var bytesWritten = 0L

        // GNU long-name and PAX overrides describe the entry that follows them.
        var gnuName: String? = null
        var gnuLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null

        while (true) {
            val got = ArArchive.readUpTo(input, header, BLOCK)
            if (got == 0) break
            if (got < BLOCK) throw ExtractionException("truncated tar header")
            if (TarHeader.isZeroBlock(header)) break

            TarHeader.verifyChecksum(header)

            var name = TarHeader.cString(header, 0, 100)
            var link = TarHeader.cString(header, 157, 100)
            val prefix = TarHeader.cString(header, 345, 155)
            if (prefix.isNotEmpty()) name = "$prefix/$name"

            val size = TarHeader.parseOctal(header, 124, 12, "tar member size")
            val mode = TarHeader.parseMode(header)
            // Type 0 and type NUL both mean a plain file; normalise so the
            // branches below never have to name the NUL byte itself.
            val rawType = header[156].toInt()
            val type = if (rawType == 0) '0' else rawType.toChar()

            when (type) {
                'L' -> {
                    gnuName = TarHeader.readText(input, size)
                    continue
                }
                'K' -> {
                    gnuLink = TarHeader.readText(input, size)
                    continue
                }
                'x', 'g' -> {
                    val pax = TarHeader.readText(input, size)
                    paxPath = TarHeader.readPaxField(pax, "path") ?: paxPath
                    paxLink = TarHeader.readPaxField(pax, "linkpath") ?: paxLink
                    continue
                }
            }

            paxPath?.let { name = it }
            paxLink?.let { link = it }
            gnuName?.let { name = it }
            gnuLink?.let { link = it }
            paxPath = null
            paxLink = null
            gnuName = null
            gnuLink = null

            if (name.isEmpty() || name == "." || name == "./") {
                TarHeader.skipData(input, size)
                continue
            }

            val relative = when (val decision = mapPath(name)) {
                is PathMapping.Place -> decision.relative
                PathMapping.Ignore -> {
                    // The prefix's own directory chain: expected, not a casualty.
                    TarHeader.skipData(input, size)
                    continue
                }
                PathMapping.Refuse -> {
                    skipped++
                    TarHeader.skipData(input, size)
                    continue
                }
            }
            val target = resolve(canonicalRoot, relative, name)

            when (type) {
                '0' -> {
                    directories += parentOf(relative)
                    bytesWritten += writeFile(
                        input, size, target, canonicalRoot, maxBytes, bytesWritten, name
                    )
                    applyMode(target, mode, name)
                    written += relative
                    TarHeader.skipPadding(input, size)
                }

                '5' -> {
                    target.mkdirs()
                    if (!target.isDirectory) {
                        throw ExtractionException("could not create directory for '$name'")
                    }
                    written += relative
                    directories += relative
                    TarHeader.skipData(input, size)
                }

                '1', '2' -> {
                    val safeTarget = safeLinkTarget(relative, link)
                    if (safeTarget == null) {
                        // Not fatal: the package carries a dangling or out-of-tree link.
                        skipped++
                        TarHeader.skipData(input, size)
                        continue
                    }
                    createLink(target, safeTarget, hard = type == '1')
                    directories += parentOf(relative)
                    written += relative
                    TarHeader.skipData(input, size)
                }

                else -> {
                    // Devices, sockets and fifos mean nothing inside an app sandbox.
                    skipped++
                    TarHeader.skipData(input, size)
                }
            }
        }

        return ExtractResult(
            files = written.toList(),
            directories = directories.filter { it.isNotEmpty() }.sorted(),
            skipped = skipped,
            bytesWritten = bytesWritten
        )
    }

    // ---------------------------------------------------------------- writing

    /**
     * Restores the execute bit, and deliberately nothing else.
     *
     * An archive is untrusted input, so its mode is not applied wholesale: a
     * package that asked for setuid or setgid would be granting itself
     * privileges it has no business having, and honouring owner-write on
     * someone else's file is a decision this app should not be making on the
     * archive's behalf.
     *
     * The execute bit is the exception that has to be honoured. Without it,
     * every binary in an unpacked root filesystem lands non-executable, and a
     * userland that cannot execute anything is not a userland — the failure
     * is a bare "Permission denied" from a shell the user installed and was
     * told was ready.
     *
     * `ownerOnly = false` because 0755 means group and other may execute too,
     * and a guest process running as any uid has to be able to start it.
     */
    private fun applyMode(target: File, mode: Int, entryName: String) {
        val executable = (mode and 0b001_001_001) != 0
        if (!target.setExecutable(executable, false) && executable) {
            throw ExtractionException(
                "'$entryName' needs to be executable and this filesystem will not " +
                    "allow it — install it somewhere else or uninstall the runtime"
            )
        }
    }

    private fun writeFile(
        input: InputStream,
        size: Long,
        target: File,
        canonicalRoot: File,
        maxBytes: Long,
        alreadyWritten: Long,
        entryName: String
    ): Long {
        // Never write *through* what is already there: it could be a symlink
        // left by an earlier package that points outside the root.
        if (target.exists()) {
            if (target.isDirectory) {
                throw ExtractionException("'$entryName' would replace a directory with a file")
            }
            if (!target.delete()) {
                throw ExtractionException("could not replace existing ${target.path}")
            }
        }

        val parent = target.parentFile ?: throw ExtractionException("no parent for ${target.path}")
        parent.mkdirs()
        val canonicalParent = try {
            parent.canonicalFile
        } catch (e: IOException) {
            throw ExtractionException("cannot resolve the parent of '$entryName'", e)
        }
        if (!canonicalParent.toPath().startsWith(canonicalRoot.toPath())) {
            throw ExtractionException(
                "'$entryName' resolves outside the install root via ${canonicalParent.path}"
            )
        }

        if (alreadyWritten + size > maxBytes) {
            throw ExtractionException(
                "archive expands past the ${maxBytes / (1024 * 1024)}MB safety cap"
            )
        }

        val buffer = ByteArray(BLOCK * 8)
        var remaining = size
        FileOutputStream(target).use { out ->
            while (remaining > 0) {
                val wanted = minOf(remaining, buffer.size.toLong()).toInt()
                ArArchive.readFully(input, buffer, wanted, "contents of '$entryName'")
                out.write(buffer, 0, wanted)
                remaining -= wanted
            }
        }
        return size
    }

    private fun createLink(target: File, linkTarget: String, hard: Boolean) {
        if (target.exists() || isSymlink(target)) {
            if (!target.delete() && !isSymlink(target)) {
                throw ExtractionException("could not replace ${target.path}")
            }
        }
        // Resolved once, non-null, rather than safe-called here and used
        // unsafely two lines down: a hard link's target is resolved against
        // the parent, so a null there is a crash inside the very code that
        // decides where an untrusted archive is allowed to put things.
        val parent = target.parentFile
            ?: throw ExtractionException("no parent directory for ${target.path}")
        parent.mkdirs()
        try {
            if (hard) {
                Files.createLink(target.toPath(), parent.resolve(linkTarget).toPath())
            } else {
                Files.createSymbolicLink(target.toPath(), File(linkTarget).toPath())
            }
        } catch (e: IOException) {
            throw ExtractionException("could not create link ${target.path} -> $linkTarget", e)
        }
    }

    private fun isSymlink(file: File): Boolean =
        try {
            Files.isSymbolicLink(file.toPath())
        } catch (e: Exception) {
            false
        }

    // ----------------------------------------------------------------- safety

    /**
     * Resolves [relative] under [root], rejecting anything that escapes it.
     *
     * A `..` that walks past the root is a hostile entry, not a quirk, so it
     * fails the whole extraction instead of being quietly dropped.
     */
    private fun resolve(root: File, relative: String, entryName: String): File {
        if (relative.startsWith("/")) {
            throw ExtractionException("'$entryName' is an absolute path ('$relative')")
        }
        val stack = ArrayList<String>()
        for (segment in relative.split('/')) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (stack.isEmpty()) {
                    throw ExtractionException("'$entryName' escapes the install root ('$relative')")
                } else {
                    stack.removeAt(stack.lastIndex)
                }
                else -> stack.add(segment)
            }
        }
        if (stack.isEmpty()) return root
        return File(root, stack.joinToString("/"))
    }

    /**
     * Returns the link target to materialise, or null when it must be dropped.
     *
     * Relative targets are kept only if they stay inside the root. Absolute
     * targets are rewritten when they address the original Termux prefix -
     * `bzip2` ships two - and dropped otherwise, since a link out to `/sdcard`
     * would outlive this install.
     */
    private fun safeLinkTarget(relative: String, link: String): String? {
        if (link.isEmpty()) return null
        val linkDir = parentOf(relative)

        if (link.startsWith("/")) {
            val stripped = DebExtractor.stripTermuxPrefix(link.removePrefix("/")) ?: return null
            if (stripped.isEmpty()) return null
            return relativeFrom(linkDir, stripped)
        }

        val combined = if (linkDir.isEmpty()) link else "$linkDir/$link"
        val stack = ArrayList<String>()
        for (segment in combined.split('/')) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack.add(segment)
            }
        }
        // A target that collapses to nothing, or would still climb, is unusable.
        if (stack.isEmpty()) return null
        return link
    }

    private fun relativeFrom(fromDir: String, to: String): String {
        val from = if (fromDir.isEmpty()) emptyList() else fromDir.split('/')
        val target = to.split('/')
        var shared = 0
        while (shared < from.size && shared < target.size && from[shared] == target[shared]) shared++
        val up = List(from.size - shared) { ".." }
        return (up + target.drop(shared)).joinToString("/")
    }

    private fun parentOf(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index <= 0) "" else path.substring(0, index)
    }
}
