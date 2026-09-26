package com.sahil.octacode.core.runtime

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/**
 * Builds real `.deb` archives in memory so tests exercise the actual unpacker.
 *
 * Hand-rolled on purpose: the point is to feed [DebExtractor] something with the
 * exact shape Termux ships - `ar` framing, `data.tar.xz`, and every path carrying
 * `data/data/com.termux/files/usr/` - so a test that passes here is testing the
 * same byte layout a downloaded package will have.
 */
object DebFixtures {

    /** The prefix Termux bakes into every archive path. */
    const val PREFIX = "data/data/com.termux/files/usr/"

    data class Entry(
        val name: String,
        val type: Char = '0',
        val link: String = "",
        val data: ByteArray = ByteArray(0),
        /**
         * The permission field as it appears in the tar header, octal and
         * eight wide. Settable because the extractor's handling of it is
         * behaviour worth testing rather than an implementation detail.
         */
        val mode: String = "0000644"
    ) {
        companion object {
            fun file(path: String, contents: String) =
                Entry(path, '0', "", contents.toByteArray(Charsets.UTF_8))

            /** A file the archive asks to be runnable, as a real binary is. */
            fun executable(path: String, contents: String) =
                Entry(path, '0', "", contents.toByteArray(Charsets.UTF_8), "0000755")

            fun dir(path: String) = Entry(path, '5')

            fun symlink(path: String, target: String) = Entry(path, '2', target)
        }
    }

    /** A `.deb` whose payload entries are already full Termux-relative paths. */
    fun deb(rawEntries: List<Entry>): ByteArray = ar(
        listOf(
            "debian-binary" to "2.0\n".toByteArray(Charsets.US_ASCII),
            "control.tar.xz" to xz(tar(listOf(Entry("./control")))),
            "data.tar.xz" to xz(tar(rawEntries))
        )
    )

    /** A `.deb` whose payload paths are relative to the Termux prefix. */
    fun debOf(vararg relativePaths: Pair<String, String>): ByteArray =
        deb(relativePaths.map { Entry.file(PREFIX + it.first, it.second) })

    /**
     * A gzipped tar laid out the way an Ubuntu OCI root image is: paths that
     * are already the ones the filesystem will have, with no package prefix.
     *
     * The same [tar] writer serves both, which is the point — the root
     * filesystem is unpacked by the same reader, and a test that built its
     * archive some other way would not be exercising that reader.
     */
    fun rootfsTgz(vararg relativePaths: Pair<String, String>): ByteArray =
        gzip(tar(relativePaths.map { Entry.file(it.first, it.second) }))

    fun gzip(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(payload) }
        return out.toByteArray()
    }

    fun xz(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // xz 1.12 takes FilterOptions, not a raw preset — there is no (stream, int) overload.
        XZOutputStream(out, LZMA2Options(6)).use { it.write(payload) }
        return out.toByteArray()
    }

    fun ar(members: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray(Charsets.US_ASCII))
        for ((name, body) in members) {
            val header = buildString {
                append(name.padEnd(16))
                append("0".padEnd(12)) // mtime
                append("0".padEnd(6)) // uid
                append("0".padEnd(6)) // gid
                append("100644".padEnd(8)) // mode
                append(body.size.toString().padEnd(10))
                append("`\n")
            }
            check(header.length == 60) { "ar header is ${header.length} bytes, expected 60" }
            out.write(header.toByteArray(Charsets.US_ASCII))
            out.write(body)
            if (body.size % 2 == 1) out.write(0) // ar pads to even offsets
        }
        return out.toByteArray()
    }

    fun tar(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        for (entry in entries) {
            out.write(header(entry))
            if (entry.data.isNotEmpty()) {
                out.write(entry.data)
                val remainder = entry.data.size % 512
                if (remainder != 0) out.write(ByteArray(512 - remainder))
            }
        }
        out.write(ByteArray(1024)) // the two zero blocks that end a tar
        return out.toByteArray()
    }

    private fun header(entry: Entry): ByteArray {
        val block = ByteArray(512)
        put(block, 0, entry.name, 100)
        put(block, 100, entry.mode, 8) // mode
        put(block, 108, "0000000", 8) // uid
        put(block, 116, "0000000", 8) // gid
        put(block, 124, octal(entry.data.size, 12), 12) // size
        put(block, 136, octal(0, 12), 12) // mtime
        for (i in 148 until 156) block[i] = 0x20 // checksum field counts as spaces
        block[156] = entry.type.code.toByte()
        put(block, 157, entry.link, 100)
        put(block, 257, "ustar", 6) // "ustar" plus the zero byte already in the block
        block[263] = '0'.code.toByte()
        block[264] = '0'.code.toByte()

        var sum = 0
        for (b in block) sum += b.toInt() and 0xFF
        // Six octal digits over the first half; the trailing spaces stay put.
        put(block, 148, octal(sum, 6), 8)
        return block
    }

    private fun put(block: ByteArray, offset: Int, value: String, width: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val length = minOf(bytes.size, width)
        System.arraycopy(bytes, 0, block, offset, length)
    }

    private fun octal(value: Int, width: Int): String = value.toString(8).padStart(width, '0')
}
