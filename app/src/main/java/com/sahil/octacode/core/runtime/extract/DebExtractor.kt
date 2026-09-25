package com.sahil.octacode.core.runtime.extract

import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Unpacks a Termux `.deb` into an install root.
 *
 * Two things about these packages are not what a Debian archive normally looks
 * like, and both were confirmed against the real mirrors before this code was
 * written:
 *
 *  1. The payload is `data.tar.xz` in every package sampled, including the
 *     125MB `rust` one, so LZMA2 is the only decompressor that matters.
 *  2. Entries are *not* package-relative. Each one embeds the whole Termux
 *     prefix - `./data/data/com.termux/files/usr/bin/bash` - so extracting
 *     naively would bury everything under `prefix/data/data/com.termux/...`.
 *     [stripTermuxPrefix] removes it, and an entry that does not sit under it
 *     (the `./data/...` directory chain itself, or anything unexpected) is
 *     skipped rather than guessed at.
 */
object DebExtractor {

    /** The prefix every Termux package hardcodes into its paths. */
    const val TERMUX_PREFIX = "data/data/com.termux/files/usr/"

    /**
     * Observed expansion runs from about 3x (proot) to 10.5x (resolv-conf) of
     * the compressed `.deb`, so a cap needs real headroom to be useful against
     * a decompression bomb without ever tripping on an honest package.
     */
    fun safetyCap(compressedBytes: Long): Long =
        maxOf(128L * 1024 * 1024, compressedBytes * 16)

    /**
     * Decides where one archive path belongs under the install root.
     *
     * Never guesses: something outside the prefix is refused rather than
     * written wherever it asked to go.
     */
    fun classify(path: String): PathMapping {
        val cleaned = path.removePrefix("./").removePrefix("/")
        if (cleaned.isEmpty()) return PathMapping.Ignore
        // ".", "./data" and friends are just the directories leading to the prefix.
        if (cleaned == TERMUX_PREFIX.trimEnd('/')) return PathMapping.Ignore
        if (TERMUX_PREFIX.startsWith("$cleaned/")) return PathMapping.Ignore
        if (cleaned.startsWith(TERMUX_PREFIX)) {
            return PathMapping.Place(cleaned.removePrefix(TERMUX_PREFIX))
        }
        return PathMapping.Refuse
    }

    /** The place [path] occupies under the install root, or null if it has none. */
    fun stripTermuxPrefix(path: String): String? =
        (classify(path) as? PathMapping.Place)?.relative

    /** Streams `data.tar.*` out of [deb] and unpacks it beneath [root]. */
    fun extract(deb: File, root: File, maxBytes: Long = safetyCap(deb.length())): ExtractResult {
        val members = ArArchive.members(deb)

        val binary = members.firstOrNull { it.name == "debian-binary" }
            ?: throw ExtractionException("${deb.name} has no debian-binary member - not a .deb")
        val version = ArArchive.open(deb, binary).use { readPrefix(it, 64) }
        if (!version.startsWith("2.")) {
            throw ExtractionException("${deb.name} declares archive version '$version', expected 2.x")
        }

        val data = members.firstOrNull { it.name.startsWith("data.tar") }
            ?: throw ExtractionException("${deb.name} has no data.tar member")

        val raw = ArArchive.open(deb, data)
        val payload: InputStream = try {
            when {
                data.name.endsWith(".xz") -> XZInputStream(raw)
                data.name.endsWith(".gz") -> GZIPInputStream(raw)
                data.name == "data.tar" -> raw
                else -> raw
            }
        } catch (e: Exception) {
            raw.close()
            throw ExtractionException("${deb.name}: cannot open ${data.name}", e)
        }

        return payload.use { TarExtractor.extract(it, root, maxBytes, ::classify) }
    }

    private fun readPrefix(input: InputStream, limit: Int): String {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val n = input.read(buffer, read, limit - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.US_ASCII).trim { it == ' ' || it.code == 0 }
    }
}
