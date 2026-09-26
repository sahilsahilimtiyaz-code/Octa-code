package com.sahil.octacode.core.runtime.extract

import java.io.InputStream

/**
 * Parsing for the fixed-size record that heads every tar member.
 *
 * Split out of [TarExtractor] so the byte-level work - octal fields, the header
 * checksum, GNU and PAX metadata payloads - is testable on its own.
 */
internal object TarHeader {

    const val BLOCK = 512

    /**
     * Sums the header with the checksum field blanked, as tar specifies.
     *
     * The field must be *replaced* by eight spaces, not merely have eight
     * spaces subtracted from it — subtracting spaces from a field that now holds
     * octal digits leaves the digits' weight in the sum and rejects every
     * well-formed archive, including every package Termux ships.
     */
    fun verifyChecksum(header: ByteArray) {
        var unsigned = 0L
        var signed = 0L
        for (i in header.indices) {
            if (i in 148 until 156) continue // counted as eight spaces below
            val b = header[i].toInt() and 0xFF
            unsigned += b
            signed += if (b > 127) b - 256L else b.toLong()
        }
        unsigned += 8L * 0x20
        signed += 8L * 0x20

        val stored = parseOctal(header, 148, 8, "tar checksum")
        if (stored != unsigned && stored != signed) {
            throw ExtractionException(
                "tar header checksum mismatch (stored $stored, computed $unsigned) " +
                    "- the archive is corrupt"
            )
        }
    }

    fun parseOctal(block: ByteArray, offset: Int, length: Int, what: String): Long {
        val text = String(block, offset, length, Charsets.US_ASCII)
            .trim { it == ' ' || it.code == 0 }
        if (text.isEmpty()) return 0L
        if (!text.all { it in '0'..'7' }) {
            throw ExtractionException("malformed $what ('$text')")
        }
        return text.toLongOrNull(8)
            ?: throw ExtractionException("malformed $what ('$text')")
    }

    /** Reads a NUL-terminated fixed-width field. */
    fun cString(block: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && block[end].toInt() != 0) end++
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    fun isZeroBlock(block: ByteArray): Boolean {
        for (b in block) if (b.toInt() != 0) return false
        return true
    }

    /**
     * The permission bits this entry asks for.
     *
     * Read so [TarExtractor] can restore the execute bit and nothing else. The
     * field is 8 bytes of octal at offset 100, per the ustar layout.
     */
    fun parseMode(block: ByteArray): Int =
        parseOctal(block, 100, 8, "tar mode").toInt()

    /** Consumes a payload carrying a single string (GNU `L`/`K`, PAX records). */
    fun readText(input: InputStream, size: Long): String {
        if (size < 0 || size > 64 * 1024 * 1024) {
            throw ExtractionException("tar metadata entry of $size bytes is implausible")
        }
        val bytes = ByteArray(size.toInt())
        ArArchive.readFully(input, bytes, bytes.size, "tar metadata entry")
        skipPadding(input, size)
        return String(bytes, Charsets.UTF_8).trim { it == ' ' || it == '\n' || it.code == 0 }
    }

    /** Pulls one `key=value` record out of a PAX header body. */
    fun readPaxField(data: String, key: String): String? {
        var offset = 0
        while (offset < data.length) {
            val space = data.indexOf(' ', offset)
            if (space < 0) break
            val length = data.substring(offset, space).toIntOrNull() ?: break
            if (length <= 0 || offset + length > data.length) break
            val record = data.substring(space + 1, offset + length).trimEnd('\n')
            val eq = record.indexOf('=')
            if (eq > 0 && record.substring(0, eq) == key) return record.substring(eq + 1)
            offset += length
        }
        return null
    }

    /** Skips the padding that rounds a payload up to a whole block. */
    fun skipPadding(input: InputStream, size: Long) {
        val remainder = size % BLOCK
        if (remainder != 0L) ArArchive.skipFully(input, BLOCK - remainder, "tar padding")
    }

    /** Discards a payload we have decided not to materialise. */
    fun skipData(input: InputStream, size: Long) {
        if (size > 0) ArArchive.skipFully(input, size, "tar entry payload")
        skipPadding(input, size)
    }
}
