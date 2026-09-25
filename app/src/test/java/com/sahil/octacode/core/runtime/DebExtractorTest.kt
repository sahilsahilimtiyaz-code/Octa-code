package com.sahil.octacode.core.runtime

import com.sahil.octacode.core.runtime.extract.DebExtractor
import com.sahil.octacode.core.runtime.extract.ExtractionException
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Exercises the unpacker against archives with the exact shape Termux ships:
 * `ar` container, `data.tar.xz` payload, and paths carrying the full
 * `data/data/com.termux/files/usr/` prefix.
 */
class DebExtractorTest {

    private lateinit var dir: File
    private lateinit var root: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-deb-${System.nanoTime()}")
            .apply { mkdirs() }
        root = File(dir, "prefix")
    }

    @After
    fun tearDown() {
        // Block body, not an expression body: deleteRecursively() returns Boolean and
        // JUnit4 refuses any @After method that is not declared void.
        dir.deleteRecursively()
    }

    private fun debOf(vararg paths: Pair<String, String>): File =
        File(dir, "pkg.deb").apply { writeBytes(DebFixtures.debOf(*paths)) }

    private fun debWith(entries: List<DebFixtures.Entry>): File =
        File(dir, "pkg.deb").apply { writeBytes(DebFixtures.deb(entries)) }

    private fun expectFailure(message: String? = null, block: () -> Unit) {
        try {
            block()
            fail("expected the extraction to be refused${message?.let { " because it $it" } ?: ""}")
        } catch (e: ExtractionException) {
            if (message != null) {
                assertTrue(
                    "reason was: ${e.message}",
                    e.message.orEmpty().contains(message)
                )
            }
        }
    }

    @Test
    fun `the termux prefix is stripped so files land at the install root`() {
        val deb = debOf("bin/bash" to "hello", "lib/libz.so.1" to "libz")

        val result = DebExtractor.extract(deb, root)

        assertEquals(
            "naive extraction would produce prefix/data/data/com.termux/files/usr/bin/bash",
            listOf("bin/bash", "lib/libz.so.1"),
            result.files
        )
        assertEquals("hello", File(root, "bin/bash").readText())
        assertEquals("libz", File(root, "lib/libz.so.1").readText())
        assertEquals(0, result.skipped)
        assertEquals("five bytes plus four", 9L, result.bytesWritten)
    }

    @Test
    fun `the prefix directory chain is ignored rather than counted as skipped`() {
        val entries = listOf(
            DebFixtures.Entry.dir("."),
            DebFixtures.Entry.dir("./data"),
            DebFixtures.Entry.dir("./data/data"),
            DebFixtures.Entry.dir("./data/data/com.termux"),
            DebFixtures.Entry.dir("./data/data/com.termux/files"),
            DebFixtures.Entry.dir("./data/data/com.termux/files/usr"),
            DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/sh", "shell")
        )

        val result = DebExtractor.extract(debWith(entries), root)

        assertEquals("every package carries this chain; it is not an anomaly", 0, result.skipped)
        assertEquals(listOf("bin/sh"), result.files)
        assertEquals("shell", File(root, "bin/sh").readText())
    }

    @Test
    fun `a path outside the prefix is refused instead of written wherever it asks`() {
        val deb = debWith(
            listOf(
                DebFixtures.Entry.file("etc/passwd", "must not appear"),
                DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/ok", "fine")
            )
        )

        val result = DebExtractor.extract(deb, root)

        assertEquals(1, result.skipped)
        assertFalse("nothing may escape the prefix", File(root, "etc/passwd").exists())
        assertEquals("fine", File(root, "bin/ok").readText())
    }

    @Test
    fun `a relative symlink is created with its target intact`() {
        val deb = debWith(
            listOf(
                DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/python3.14", "real"),
                DebFixtures.Entry.symlink(DebFixtures.PREFIX + "bin/python", "python3.14")
            )
        )

        val result = DebExtractor.extract(deb, root)

        val link = File(root, "bin/python")
        assertTrue("expected a symlink at ${link.path}", Files.isSymbolicLink(link.toPath()))
        assertEquals("python3.14", Files.readSymbolicLink(link.toPath()).toString())
        assertTrue(result.files.contains("bin/python"))
    }

    @Test
    fun `an absolute link into the original termux prefix is rewritten to a relative one`() {
        // bzip2 really ships two of these, pointing at a path that does not exist here.
        val deb = debWith(
            listOf(
                DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/bzdiff", "diff"),
                DebFixtures.Entry.symlink(
                    DebFixtures.PREFIX + "bin/bzcmp",
                    "/data/data/com.termux/files/usr/bin/bzdiff"
                )
            )
        )

        val result = DebExtractor.extract(deb, root)

        val link = File(root, "bin/bzcmp")
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals(
            "a link to the old install path would dangle forever",
            "bzdiff",
            Files.readSymbolicLink(link.toPath()).toString()
        )
        assertEquals("both entries belong to this package", 0, result.skipped)
    }

    @Test
    fun `a symlink pointing outside the prefix is dropped and counted`() {
        val deb = debWith(
            listOf(
                DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/thing", "t"),
                DebFixtures.Entry.symlink(DebFixtures.PREFIX + "bin/evil", "/sdcard/Download")
            )
        )

        val result = DebExtractor.extract(deb, root)

        assertEquals(1, result.skipped)
        assertFalse(File(root, "bin/evil").exists())
        assertTrue("the safe half still installs", File(root, "bin/thing").isFile)
    }

    @Test
    fun `a path that climbs out of the root fails the whole extraction`() {
        val deb = debWith(
            listOf(
                DebFixtures.Entry.file(DebFixtures.PREFIX + "../../escape", "bad"),
                DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/innocent", "good")
            )
        )

        expectFailure("escapes the install root") { DebExtractor.extract(deb, root) }
        assertFalse("nothing may be left behind by a rejected archive", File(root, "bin/innocent").exists())
    }

    @Test
    fun `expansion past the safety cap is refused`() {
        val deb = debOf("bin/huge" to "x".repeat(4096))

        expectFailure("safety cap") { DebExtractor.extract(deb, root, maxBytes = 256) }

        assertFalse(File(root, "bin/huge").exists())
    }

    @Test
    fun `a header whose checksum no longer matches is rejected as corrupt`() {
        val tarBytes = DebFixtures.tar(
            listOf(DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/x", "payload"))
        )
        tarBytes[1] = 'X'.code.toByte() // edits the name without fixing the sum
        val deb = File(dir, "pkg.deb").apply {
            writeBytes(
                DebFixtures.ar(
                    listOf(
                        "debian-binary" to "2.0\n".toByteArray(Charsets.US_ASCII),
                        "data.tar.xz" to DebFixtures.xz(tarBytes)
                    )
                )
            )
        }

        expectFailure("checksum mismatch") { DebExtractor.extract(deb, root) }
    }

    @Test
    fun `a file that is not a deb at all is rejected before anything is opened`() {
        val junk = File(dir, "junk.deb").apply { writeBytes(ByteArray(200) { 7 }) }

        expectFailure("not an ar archive") { DebExtractor.extract(junk, root) }
    }

    @Test
    fun `a deb missing its debian-binary member is rejected`() {
        val deb = File(dir, "pkg.deb").apply {
            writeBytes(
                DebFixtures.ar(
                    listOf("data.tar.xz" to DebFixtures.xz(DebFixtures.tar(emptyList())))
                )
            )
        }

        expectFailure("no debian-binary") { DebExtractor.extract(deb, root) }
    }
}
