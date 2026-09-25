package com.sahil.octacode.core.runtime

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeLedgerTest {

    private lateinit var dir: File
    private lateinit var file: File
    private var now = 1_000L

    private val artifact = RuntimeArtifact(
        id = "bash",
        version = "5.3.20",
        size = 956_852L,
        sha256 = "a".repeat(64),
        path = "pool/main/b/bash/bash_5.3.20_aarch64.deb"
    )

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-ledger-${System.nanoTime()}").apply { mkdirs() }
        file = File(dir, "ledger.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `verified artifact round trips through disk`() {
        RuntimeLedger(file) { now }.markVerified(artifact)

        val reloaded = RuntimeLedger(file) { now + 5 }
        assertTrue(reloaded.contains("bash"))
        val record = reloaded.get("bash")!!
        assertEquals("5.3.20", record.version)
        assertEquals(artifact.sha256, record.sha256)
        assertEquals(956_852L, record.bytes)
        assertEquals(1_000L, record.verifiedAtMillis)
        assertEquals(956_852L, reloaded.totalBytes())
        assertFalse("fresh ledger is not corrupt", reloaded.wasCorrupt)
    }

    @Test
    fun `unreadable ledger starts empty and says so`() {
        file.writeText("{ this is not json")

        val ledger = RuntimeLedger(file)
        assertTrue("corruption must be surfaced", ledger.wasCorrupt)
        assertTrue("must not trust unreadable contents", ledger.snapshot().isEmpty())
        assertEquals(0L, ledger.totalBytes())

        // ...and it recovers: the next verification rewrites a valid file.
        ledger.markVerified(artifact)
        assertFalse("repaired file must load clean", RuntimeLedger(file).wasCorrupt)
        assertTrue(RuntimeLedger(file).contains("bash"))
    }

    @Test
    fun `missing file is a clean empty ledger, not corruption`() {
        val ledger = RuntimeLedger(file)
        assertFalse(ledger.wasCorrupt)
        assertTrue(ledger.snapshot().isEmpty())
        assertNull(ledger.get("bash"))
    }

    @Test
    fun `remove and clear persist`() {
        val ledger = RuntimeLedger(file)
        ledger.markVerified(artifact)
        ledger.remove("bash")
        assertFalse(RuntimeLedger(file).contains("bash"))

        ledger.markVerified(artifact)
        ledger.clear()
        val reloaded = RuntimeLedger(file)
        assertFalse(reloaded.contains("bash"))
        assertEquals(0L, reloaded.totalBytes())
    }

    @Test
    fun `no temporary file survives a write`() {
        RuntimeLedger(file).markVerified(artifact)
        val leftovers = dir.listFiles()!!.map { it.name }
        assertEquals(listOf("ledger.json"), leftovers)
    }
}
