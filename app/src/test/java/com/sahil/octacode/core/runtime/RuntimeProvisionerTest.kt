package com.sahil.octacode.core.runtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** End-to-end R1: manifest → downloader → ledger → provisioner state. */
class RuntimeProvisionerTest {

    private lateinit var dir: File
    private lateinit var cache: File
    private lateinit var prefix: File
    private lateinit var ledgerFile: File

    // Real `.deb` archives, not raw bytes: every install now unpacks, so a test
    // that only proved the download would be proving the wrong half.
    private val alpha = DebFixtures.debOf(
        "bin/alpha" to "alpha-1.0",
        "share/doc/alpha/readme" to "the first tool"
    )
    private val beta = DebFixtures.debOf("bin/beta" to "beta-1.0")
    private val gamma = DebFixtures.debOf("bin/gamma" to "gamma-1.0")

    private lateinit var manifest: RuntimeManifest

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-prov-${System.nanoTime()}").apply { mkdirs() }
        cache = File(dir, "cache").apply { mkdirs() }
        prefix = File(dir, "prefix")
        ledgerFile = File(dir, "ledger.json")

        val baseItems = listOf(
            artifact("alpha", alpha, "pool/main/a/alpha/alpha_1.0_aarch64.deb"),
            artifact("beta", beta, "pool/main/b/beta/beta_1.0_aarch64.deb")
        )
        // "extra" reuses beta, so a full install has to dedupe across closures.
        val extraItems = listOf(
            baseItems[1],
            artifact("gamma", gamma, "pool/main/g/gamma/gamma_1.0_aarch64.deb")
        )
        manifest = RuntimeManifest(
            schema = 1,
            name = "test-runtime",
            arch = "aarch64",
            abi = "arm64-v8a",
            index = "https://mirror.test/index",
            baseUrl = "https://mirror.test/apt/",
            generated = "2026-09-25",
            groups = listOf(
                RuntimeGroup(
                    id = "base",
                    title = "Base",
                    description = "first group",
                    optional = false,
                    totalBytes = baseItems.sumOf { it.size },
                    items = baseItems
                ),
                RuntimeGroup(
                    id = "extra",
                    title = "Extra",
                    description = "second group",
                    optional = false,
                    totalBytes = extraItems.sumOf { it.size },
                    items = extraItems
                )
            ),
            unionBytes = listOf(alpha, beta, gamma).sumOf { it.size.toLong() },
            packageCount = 3
        )
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun artifact(id: String, bytes: ByteArray, path: String) = RuntimeArtifact(
        id = id,
        version = "1.0",
        size = bytes.size.toLong(),
        sha256 = bytes.sha256(),
        path = path
    )

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private sealed interface Reply {
        data class Bytes(val body: ByteArray) : Reply
        data object Missing : Reply
    }

    private fun serve(handler: (path: String) -> Reply): HttpClient =
        HttpClient(MockEngine { request ->
            when (val reply = handler(request.url.encodedPath)) {
                is Reply.Missing -> respondError(HttpStatusCode.NotFound)
                is Reply.Bytes -> respond(reply.body, HttpStatusCode.OK)
            }
        })

    private fun newProvisioner(client: HttpClient): RuntimeProvisioner {
        // One ledger shared with its installer, so a provisioner never sees a
        // different view of what is installed than the thing doing the installing.
        val ledger = RuntimeLedger(ledgerFile)
        return RuntimeProvisioner(
            manifest = manifest,
            ledger = ledger,
            downloader = ArtifactDownloader(client),
            installer = ArtifactInstaller(prefix, ledger),
            cacheDir = cache
        )
    }

    /** The real on-device path for an artifact — derived, never hand-typed. */
    private fun cached(id: String): File =
        File(cache, manifest.distinctArtifacts().first { it.id == id }.cacheName)

    private suspend fun RuntimeProvisioner.awaitIdle(): ProvisionerState =
        withTimeout(30_000) { state.first { !it.busy } }

    private fun fileName(path: String) = path.substringAfterLast('/')

    @Test
    fun `installs a whole group and records every verified artifact`() = runBlocking {
        var requests = 0
        val prov = newProvisioner(serve { path ->
            requests++
            when (fileName(path)) {
                "alpha_1.0_aarch64.deb" -> Reply.Bytes(alpha)
                "beta_1.0_aarch64.deb" -> Reply.Bytes(beta)
                else -> Reply.Missing
            }
        })

        prov.install("base")
        val final = prov.awaitIdle()

        assertNull("unexpected error: ${final.lastError}", final.lastError)
        assertEquals(2, final.fetched.size)
        assertEquals(2, requests)
        assertEquals(manifest.group("base")!!.totalBytes, final.totalFetchedBytes())
        assertArrayEquals(alpha, cached("alpha").readBytes())
        assertTrue(final.isComplete(manifest.group("base")!!))

        // Downloaded AND unpacked are two different claims; both must hold.
        assertEquals(2, final.installedCount())
        assertTrue(final.isInstalled(manifest.group("base")!!))
        assertEquals("alpha-1.0", File(prefix, "bin/alpha").readText())
        assertEquals(
            "nested paths must land where they were written in the archive",
            "the first tool",
            File(prefix, "share/doc/alpha/readme").readText()
        )
        assertEquals("beta-1.0", File(prefix, "bin/beta").readText())
    }

    @Test
    fun `second install is idempotent and spends no network`() = runBlocking {
        var requests = 0
        val prov = newProvisioner(serve {
            requests++
            Reply.Bytes(if (it.contains("alpha")) alpha else beta)
        })

        prov.install("base")
        prov.awaitIdle()
        assertEquals(2, requests)

        prov.install("base")
        val second = prov.awaitIdle()

        assertNull(second.lastError)
        assertEquals("no refetch of verified artifacts", 2, requests)
        assertEquals(2, second.fetched.size)
    }

    @Test
    fun `failure stops the group and reports a real reason`() = runBlocking {
        val prov = newProvisioner(serve { path ->
            when (fileName(path)) {
                "alpha_1.0_aarch64.deb" -> Reply.Bytes(alpha)
                else -> Reply.Missing
            }
        })

        prov.install("base")
        val final = withTimeout(30_000) {
            prov.state.first { !it.busy && it.lastError != null }
        }

        val error = requireNotNull(final.lastError) { "expected the group to report a failure" }
        assertEquals("base", error.groupId)
        assertEquals("beta", error.itemId)
        assertTrue("reason was: ${error.reason}", error.reason.contains("HTTP 404"))
        assertEquals("alpha must still be recorded", 1, final.fetched.size)
        assertFalse("failed artifact must not be written", cached("beta").exists())
        assertTrue("verified artifact must survive the failure", cached("alpha").exists())
    }

    @Test
    fun `checksum mismatch is reported as untrusted, not as success`() = runBlocking {
        // Right SIZE, wrong bytes — so this fails on the hash, not on the length.
        val corrupted = ByteArray(alpha.size) { 3 }
        val prov = newProvisioner(serve { Reply.Bytes(corrupted) })

        prov.install("base")
        val final = withTimeout(30_000) {
            prov.state.first { !it.busy && it.lastError != null }
        }

        val error = requireNotNull(final.lastError) { "expected a checksum failure to be reported" }
        assertTrue("reason was: ${error.reason}", error.reason.contains("Checksum mismatch"))
        assertEquals("alpha", error.itemId)
        assertTrue("nothing may be recorded", final.fetched.isEmpty())
        assertEquals(0L, final.totalFetchedBytes())
        assertFalse("corrupted file must be deleted", cached("alpha").exists())
    }

    @Test
    fun `lost ledger re-verifies cached bytes without re-downloading`() = runBlocking {
        var requests = 0
        val client = serve {
            requests++
            Reply.Bytes(if (it.contains("alpha")) alpha else beta)
        }

        newProvisioner(client).apply {
            install("base")
            awaitIdle()
        }
        assertEquals(2, requests)

        // Ledger gone (crash/corruption) but the artifacts are still on disk.
        RuntimeLedger(ledgerFile).clear()

        val recovered = newProvisioner(client)
        recovered.install("base")
        val state = recovered.awaitIdle()

        assertNull(state.lastError)
        assertEquals("re-verify must not re-download", 2, requests)
        assertEquals(2, state.fetched.size)
    }

    @Test
    fun `installAll covers every group and downloads shared artifacts once`() = runBlocking {
        var requests = 0
        val prov = newProvisioner(serve { path ->
            requests++
            when (fileName(path)) {
                "alpha_1.0_aarch64.deb" -> Reply.Bytes(alpha)
                "beta_1.0_aarch64.deb" -> Reply.Bytes(beta)
                else -> Reply.Bytes(gamma)
            }
        })

        val fullCost = (alpha.size + beta.size + gamma.size).toLong()
        assertEquals(
            "shared package must be counted once",
            fullCost,
            prov.state.value.pendingBytes(manifest)
        )

        prov.installAll()
        val final = prov.awaitIdle()

        assertNull("unexpected error: ${final.lastError}", final.lastError)
        assertEquals("beta is in both groups but fetched once", 3, requests)
        assertEquals(3, final.fetched.size)
        assertTrue("whole runtime should be complete", final.isComplete(manifest))
        assertEquals(0L, final.pendingBytes(manifest))
        assertTrue(cached("alpha").isFile)
        assertTrue(cached("beta").isFile)
        assertTrue(cached("gamma").isFile)
        // "Complete" must mean on disk, not merely downloaded.
        assertEquals("every package should be unpacked", 3, final.installedCount())
        assertEquals("beta-1.0", File(prefix, "bin/beta").readText())
        assertEquals("gamma-1.0", File(prefix, "bin/gamma").readText())
    }

    @Test
    fun `installAll stops the whole queue at the first failure`() = runBlocking {
        val prov = newProvisioner(serve { path ->
            when (fileName(path)) {
                "alpha_1.0_aarch64.deb" -> Reply.Bytes(alpha)
                "beta_1.0_aarch64.deb" -> Reply.Bytes(beta)
                else -> Reply.Missing // gamma is unreachable
            }
        })

        prov.installAll()
        val final = withTimeout(30_000) {
            prov.state.first { !it.busy && it.lastError != null }
        }

        val error = requireNotNull(final.lastError) { "expected the queue to stop" }
        assertEquals("extra", error.groupId)
        assertEquals("gamma", error.itemId)
        assertTrue("reason was: ${error.reason}", error.reason.contains("HTTP 404"))
        assertEquals(
            "everything before the failure stays verified",
            2,
            final.fetched.size
        )
        assertFalse(cached("gamma").exists())
    }
}
