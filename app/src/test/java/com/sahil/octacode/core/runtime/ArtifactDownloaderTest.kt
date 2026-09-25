package com.sahil.octacode.core.runtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArtifactDownloaderTest {

    private lateinit var dir: File
    private lateinit var target: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-dl-${System.nanoTime()}").apply { mkdirs() }
        target = File(dir, "artifact.deb")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val body = ByteArray(200_000) { (it % 251).toByte() }

    @Test
    fun `verified download writes the bytes and reports progress`() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.size.toString()))
        })
        val seen = mutableListOf<Pair<Long, Long>>()

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target) { d, t -> seen += d to t }

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.Verified)
        assertFalse((outcome as ArtifactDownloader.Outcome.Verified).reused)
        assertArrayEquals(body, target.readBytes())
        assertEquals(body.size.toLong(), seen.last().first)
        assertEquals(body.size.toLong(), seen.last().second)
        assertEquals(1, requests)
    }

    @Test
    fun `hash mismatch deletes the file instead of trusting it`() = runBlocking {
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK) })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256("something else".toByteArray()), body.size.toLong(), target)

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.HashMismatch)
        val mismatch = outcome as ArtifactDownloader.Outcome.HashMismatch
        assertEquals(sha256(body), mismatch.actual)
        assertFalse("mismatched file must not survive", target.exists())
    }

    @Test
    fun `short stream fails as incomplete and leaves nothing behind`() = runBlocking {
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK) })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size + 10L, target)

        val failed = outcome as ArtifactDownloader.Outcome.Failed
        assertTrue("reason was: ${failed.reason}", failed.reason.contains("Received"))
        assertFalse(target.exists())
    }

    @Test
    fun `partial file resumes with a range request and still hashes correctly`() = runBlocking {
        val partial = 50_000
        target.writeBytes(body.copyOf(partial))
        var rangeHeader: String? = null

        val client = HttpClient(MockEngine { request ->
            val range = request.headers[HttpHeaders.Range]
            rangeHeader = range
            val start = range?.substringAfter("bytes=")?.substringBefore("-")?.toIntOrNull() ?: 0
            if (start > 0) {
                respond(body.copyOfRange(start, body.size), HttpStatusCode.PartialContent)
            } else {
                respond(body, HttpStatusCode.OK)
            }
        })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target)

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.Verified)
        assertEquals("bytes=$partial-", rangeHeader)
        assertArrayEquals(body, target.readBytes())
    }

    @Test
    fun `a correct file already on disk costs zero requests`() = runBlocking {
        target.writeBytes(body)
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(body, HttpStatusCode.OK)
        })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target)

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.Verified)
        val verified = outcome as ArtifactDownloader.Outcome.Verified
        assertTrue("expected the on-disk bytes to be reused, got $verified", verified.reused)
        assertEquals(0, requests)
    }

    @Test
    fun `a complete but wrong file is restarted rather than resumed`() = runBlocking {
        target.writeBytes(ByteArray(body.size) { 7 })
        var rangeHeader: String? = null

        val client = HttpClient(MockEngine { request ->
            rangeHeader = request.headers[HttpHeaders.Range]
            respond(body, HttpStatusCode.OK)
        })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target)

        assertTrue(outcome is ArtifactDownloader.Outcome.Verified)
        assertEquals("suspect bytes must not be resumed", null, rangeHeader)
        assertArrayEquals(body, target.readBytes())
    }

    @Test
    fun `server errors retry then succeed`() = runBlocking {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) respondError(HttpStatusCode.InternalServerError) else respond(body, HttpStatusCode.OK)
        })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target)

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.Verified)
        assertEquals(2, calls)
    }

    @Test
    fun `missing artifact fails immediately without retrying`() = runBlocking {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respondError(HttpStatusCode.NotFound)
        })

        val outcome = ArtifactDownloader(client)
            .download("https://mirror.test/apt/pool/x.deb", sha256(body), body.size.toLong(), target)

        assertTrue("got $outcome", outcome is ArtifactDownloader.Outcome.HttpError)
        val error = outcome as ArtifactDownloader.Outcome.HttpError
        assertEquals(404, error.status)
        assertEquals("4xx must not be retried", 1, calls)
        assertFalse(target.exists())
    }
}
