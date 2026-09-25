package com.sahil.octacode.core.runtime.extract

import java.io.IOException
import java.io.InputStream

/**
 * A read-only window over [delegate] of exactly [limit] bytes.
 *
 * Keeps a caller from ever reading past the end of an archive member into the
 * next one, which would silently corrupt whatever it builds from the result.
 */
internal class BoundedInputStream(
    private val delegate: InputStream,
    private val limit: Long
) : InputStream() {

    private var remaining = limit

    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = delegate.read()
        if (b >= 0) remaining--
        return b
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        if (length == 0) return 0
        val allowed = minOf(length.toLong(), remaining).toInt()
        val n = delegate.read(buffer, offset, allowed)
        if (n > 0) remaining -= n
        return n
    }

    override fun skip(count: Long): Long {
        val skipped = minOf(count, remaining)
        val actual = delegate.skip(skipped)
        if (actual > 0) remaining -= actual
        return actual
    }

    override fun available(): Int = minOf(remaining, delegate.available().toLong()).toInt()

    override fun close() = delegate.close()

    fun requireExhausted() {
        if (remaining != 0L) {
            throw IOException("stream ended ${remaining} bytes short of the declared size")
        }
    }
}
