package com.sahil.octacode.core.runtime

import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The index may only ever answer from paths this app verified and unpacked,
 * and it must refuse to name something that is not actually usable.
 */
class RuntimeIndexTest {

    private lateinit var dir: File
    private lateinit var prefix: File
    private lateinit var ledger: RuntimeLedger
    private lateinit var index: RuntimeIndex

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-idx-${System.nanoTime()}")
            .apply { mkdirs() }
        prefix = File(dir, "prefix")
        ledger = RuntimeLedger(File(dir, "ledger.json"))
        index = RuntimeIndex(prefix, ledger)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun artifactFor(id: String, bytes: ByteArray): RuntimeArtifact =
        RuntimeArtifact(
            id = id,
            version = "1.0",
            size = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) },
            path = "pool/main/$id/${id}_1.0.deb"
        )

    private fun install(id: String, vararg paths: Pair<String, String>) =
        installRaw(id, DebFixtures.debOf(*paths))

    private fun installRaw(id: String, bytes: ByteArray) {
        val artifact = artifactFor(id, bytes)
        ledger.markVerified(artifact)
        val deb = File(dir, "$id.deb").apply { writeBytes(bytes) }
        val result = ArtifactInstaller(prefix, ledger).install(artifact, deb)
        check(result is InstallResult.Installed || result is InstallResult.AlreadyInstalled) {
            "fixture install failed: $result"
        }
    }

    @Test
    fun `an installed tool in bin is found`() {
        install("bash", "bin/bash" to "shell", "share/doc/bash/copy" to "licence")

        val found = index.commandPath("bash")

        assertTrue("expected to find bash", found != null)
        assertEquals(File(prefix, "bin/bash").path, found!!.path)
        assertTrue(found.isFile)
    }

    @Test
    fun `a tool no package provides resolves to nothing rather than a guess`() {
        install("bash", "bin/bash" to "shell")

        assertNull(index.commandPath("node"))
        assertNull(index.commandPath("git"))
    }

    @Test
    fun `documentation outside bin is not offered as a command`() {
        install("bash", "bin/bash" to "shell", "share/doc/bash/copy" to "licence")

        assertNull("which(copy) must not resolve to a licence file", index.commandPath("copy"))
    }

    @Test
    fun `a command spelling that is already a path is refused`() {
        install("bash", "bin/bash" to "shell")

        assertNull(index.commandPath("bin/bash"))
        assertNull(index.commandPath(""))
    }

    @Test
    fun `a dangling symlink is neither found nor listed`() {
        installRaw(
            "broken",
            DebFixtures.deb(
                listOf(
                    DebFixtures.Entry.file(DebFixtures.PREFIX + "bin/real", "x"),
                    DebFixtures.Entry.symlink(
                        DebFixtures.PREFIX + "bin/gone",
                        "missing-target"
                    )
                )
            )
        )

        assertNull(
            "a link to nothing is not an available tool",
            index.commandPath("gone")
        )
        assertEquals(
            "the intact half still answers",
            File(prefix, "bin/real").path,
            index.commandPath("real")!!.path
        )
        assertEquals(listOf("real"), index.commands())
    }

    @Test
    fun `commands lists only what lives in bin or sbin, sorted`() {
        install("shell", "bin/zsh" to "z", "bin/ash" to "a", "sbin/nologin" to "n")
        install("docs", "share/doc/thing/README" to "r")

        assertEquals(listOf("ash", "nologin", "zsh"), index.commands())
    }

    @Test
    fun `path directories only include the ones that exist`() {
        assertTrue(index.pathDirectories().isEmpty())

        install("bash", "bin/bash" to "shell")

        val dirs = index.pathDirectories()
        assertEquals(1, dirs.size)
        assertEquals(File(prefix, "bin").path, dirs[0].path)
    }

    @Test
    fun `nothing is reported before anything is installed`() {
        assertNull(index.commandPath("bash"))
        assertTrue(index.commands().isEmpty())
        assertEquals(0, index.installedPackageCount())
    }
}
