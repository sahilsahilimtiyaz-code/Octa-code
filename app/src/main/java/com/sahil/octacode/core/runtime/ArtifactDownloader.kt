package com.sahil.octacode.core.runtime

import com.sahil.octacode.data.net.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Streams a pinned artifact to disk and proves it is the pinned bytes before
 * anyone is allowed to treat it as installed.
 *
 * Guarantees (all covered by tests):
 *  - SHA-256 is computed over the bytes actually written, including a resumed prefix.
 *  - A hash mismatch DELETES the file and reports it — it is never "good enough".
 *  - A partial file resumes with `Range`; a mismatched full file starts over.
 *  - 429/5xx retry with the project's existing backoff; other statuses fail at once.
 *  - Cancellation is never swallowed as a failure.
 */
class ArtifactDownloader(
    private val http: HttpClient,
    private val retry: RetryPolicy = RetryPolicy()
) {

    sealed interface Outcome {
        val bytes: Long

        /** Bytes match the pin. [reused] = they were already on disk, no network spent. */
        data class Verified(override val bytes: Long, val reused: Boolean) : Outcome

        data class HashMismatch(override val bytes: Long, val expected: String, val actual: String) : Outcome

        data class HttpError(override val bytes: Long, val status: Int, val message: String) : Outcome

        data class Failed(override val bytes: Long, val reason: String) : Outcome
    }

    private sealed interface Handshake {
        data class Open(val response: HttpResponse, val resumed: Boolean) : Handshake
        data class RetryIn(val delayMs: Long) : Handshake
        data class GiveUp(val outcome: Outcome) : Handshake
    }

    private data class StreamOutcome(val bytes: Long, val sha256: String)

    suspend fun download(
        url: String,
        expectedSha256: String,
        expectedSize: Long,
        target: File,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit = { _, _ -> }
    ): Outcome {
        target.parentFile?.mkdirs()

        // 1. Decide whether an existing file is a resumable partial or a candidate.
        var offset = 0L
        if (target.isFile) {
            val length = target.length()
            val complete = expectedSize <= 0 || length == expectedSize
            if (complete) {
                val onDisk = sha256Of(target)
                if (onDisk.equals(expectedSha256, ignoreCase = true)) {
                    onProgress(length, if (expectedSize > 0) expectedSize else length)
                    return Outcome.Verified(length, reused = true)
                }
                // Present but wrong → never resume from suspect bytes.
                target.delete()
                offset = 0L
            } else if (length < expectedSize) {
                offset = length
            } else {
                target.delete()
                offset = 0L
            }
        }

        var attempt = 0
        while (true) {
            when (val negotiation = negotiate(url, offset, target.length(), attempt)) {
                is Handshake.GiveUp -> return negotiation.outcome
                is Handshake.RetryIn -> {
                    attempt++
                    delay(negotiation.delayMs)
                    continue
                }
                is Handshake.Open -> {
                    val startOffset = if (negotiation.resumed) offset else 0L
                    val outcome = try {
                        val streamed = stream(negotiation.response, target, startOffset, expectedSize, onProgress)
                        verify(streamed, expectedSha256, expectedSize, target, onProgress)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null // stream broke — decide below
                    }
                    if (outcome != null) return outcome

                    attempt++
                    val partial = if (target.isFile) target.length() else 0L
                    if (attempt >= retry.maxAttempts) {
                        return Outcome.Failed(
                            partial,
                            "Connection lost after $partial bytes (attempt $attempt of ${retry.maxAttempts})"
                        )
                    }
                    offset = partial // continue from wherever we actually got to
                    delay(retry.delayForAttempt(attempt))
                }
            }
        }
    }

    private suspend fun negotiate(url: String, offset: Long, bytesSoFar: Long, attempt: Int): Handshake {
        val response = try {
            http.get(url) {
                if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "${e::class.simpleName ?: "error"}: ${e.message ?: "no detail"}"
            return if (attempt >= retry.maxAttempts) {
                Handshake.GiveUp(Outcome.Failed(bytesSoFar, "Network error — $message"))
            } else {
                Handshake.RetryIn(retry.delayForAttempt(attempt + 1))
            }
        }

        val status = response.status.value
        return when {
            response.status == HttpStatusCode.PartialContent -> Handshake.Open(response, resumed = true)
            status in 200..299 -> Handshake.Open(response, resumed = false)
            retry.shouldRetry(status, attempt + 1) -> {
                bodyOrEmpty(response)
                Handshake.RetryIn(retry.delayForAttempt(attempt + 1))
            }
            else -> Handshake.GiveUp(
                Outcome.HttpError(
                    bytes = bytesSoFar,
                    status = status,
                    message = bodyOrEmpty(response).ifBlank { response.status.description }
                )
            )
        }
    }

    private suspend fun stream(
        response: HttpResponse,
        target: File,
        offset: Long,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ): StreamOutcome {
        val digest = MessageDigest.getInstance("SHA-256")
        if (offset > 0) seedDigest(digest, target, offset)

        var written = offset
        val channel = response.bodyAsChannel()
        // append=true only when we are continuing an existing partial prefix.
        FileOutputStream(target, offset > 0).use { out ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = channel.readAvailable(buffer, 0, buffer.size)
                if (n < 0) break
                if (n == 0) {
                    channel.awaitContent()
                    continue
                }
                out.write(buffer, 0, n)
                digest.update(buffer, 0, n)
                written += n
                onProgress(written, expectedSize)
            }
            out.flush()
        }
        return StreamOutcome(written, digest.digest().toHex())
    }

    private fun verify(
        streamed: StreamOutcome,
        expectedSha256: String,
        expectedSize: Long,
        target: File,
        onProgress: (Long, Long) -> Unit
    ): Outcome {
        if (expectedSize > 0 && streamed.bytes != expectedSize) {
            target.delete()
            return Outcome.Failed(streamed.bytes, "Received ${streamed.bytes} of $expectedSize bytes")
        }
        if (!streamed.sha256.equals(expectedSha256, ignoreCase = true)) {
            target.delete()
            return Outcome.HashMismatch(streamed.bytes, expectedSha256, streamed.sha256)
        }
        onProgress(streamed.bytes, expectedSize)
        return Outcome.Verified(streamed.bytes, reused = false)
    }

    private fun seedDigest(digest: MessageDigest, file: File, limit: Long) {
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var remaining = limit
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n <= 0) break
                digest.update(buffer, 0, n)
                remaining -= n
            }
            if (remaining != 0L) throw IOException("partial file shorter than resume offset ($remaining bytes missing)")
        }
    }

    private suspend fun bodyOrEmpty(response: HttpResponse): String = try {
        response.bodyAsText().take(ERROR_BODY_CHARS)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ""
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private const val ERROR_BODY_CHARS = 300

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
