package com.sahil.octacode.core.runtime.extract

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** A `.deb` is an `ar` archive; we only ever need to pull members out of it. */
object ArArchive {

    private const val MAGIC = "!<arch>\n"
    private const val HEADER_BYTES = 60

    data class Member(val name: String, val offset: Long, val size: Long)

    /**
     * Indexes the members of [file] without reading their contents.
     *
     * Only headers are touched, so a 125MB `.deb` costs a handful of seeks — the
     * payload is streamed later through [open].
     */
    fun members(file: File): List<Member> {
        val out = mutableListOf<Member>()
        FileInputStream(file).use { input ->
            val magic = ByteArray(MAGIC.length)
            readFully(input, magic, magic.size, "ar magic")
            if (String(magic, Charsets.US_ASCII) != MAGIC) {
                throw ExtractionException("${file.name} is not an ar archive")
            }
            var position = MAGIC.length.toLong()

            val header = ByteArray(HEADER_BYTES)
            while (true) {
                val got = readUpTo(input, header, header.size)
                if (got == 0) break
                if (got < header.size) {
                    // Some `ar` writers finish the file with a newline or spaces.
                    // A short read made of anything else means it was cut off.
                    var trailingPadding = true
                    for (i in 0 until got) {
                        val b = header[i]
                        if (b != '\n'.code.toByte() && b != '\r'.code.toByte() && b != ' '.code.toByte()) {
                            trailingPadding = false
                            break
                        }
                    }
                    if (trailingPadding) break
                    throw ExtractionException("truncated ar header in ${file.name}")
                }
                position += got

                var name = String(header, 0, 16, Charsets.US_ASCII).trimEnd(' ', '/')
                var size = parseDecimal(header, 48, 10, "ar member size")
                var offset = position

                // BSD-style long names store the real name at the front of the data.
                if (name.startsWith("#1/")) {
                    val nameLen = name.substring(3).toIntOrNull()
                        ?: throw ExtractionException("malformed BSD ar name in ${file.name}")
                    if (nameLen <= 0 || nameLen > size) {
                        throw ExtractionException("ar long-name length $nameLen out of range")
                    }
                    val nameBytes = ByteArray(nameLen)
                    readFully(input, nameBytes, nameBytes.size, "ar long name")
                    position += nameLen
                    name = String(nameBytes, Charsets.US_ASCII).trimEnd(' ')
                    size -= nameLen
                    offset += nameLen
                }

                out += Member(name, offset, size)
                // ar pads every member to an even offset.
                val advance = size + (size % 2)
                skipFully(input, advance, "ar member payload")
                position += advance
            }
        }
        return out
    }

    /** Opens a bounded stream over one member; the caller owns closing it. */
    fun open(file: File, member: Member): InputStream {
        val input = FileInputStream(file)
        try {
            skipFully(input, member.offset, "seek to ar member")
        } catch (e: Exception) {
            input.close()
            throw e
        }
        return BoundedInputStream(input, member.size)
    }

    internal fun readFully(input: InputStream, buffer: ByteArray, length: Int, what: String) {
        if (readUpTo(input, buffer, length) < length) {
            throw ExtractionException("unexpected end of stream while reading $what")
        }
    }

    internal fun readUpTo(input: InputStream, buffer: ByteArray, length: Int): Int {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    internal fun skipFully(input: InputStream, count: Long, what: String) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            // skip() reports 0 both at EOF and on some non-blocking paths.
            if (input.read() < 0) throw ExtractionException("unexpected end of stream while $what")
            remaining--
        }
    }

    private fun parseDecimal(header: ByteArray, offset: Int, length: Int, what: String): Long {
        val text = String(header, offset, length, Charsets.US_ASCII).trim()
        return text.toLongOrNull()
            ?: throw ExtractionException("malformed $what ('$text')")
    }
}
