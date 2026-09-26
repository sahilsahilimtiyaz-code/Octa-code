package com.sahil.octacode.core.runtime

import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unpack, record, remove - and never claim a path we did not actually write. */
class ArtifactInstallerTest {

    private lateinit var dir: File
    private lateinit var prefix: File
    private lateinit var ledger: RuntimeLedger
    private lateinit var installer: ArtifactInstaller

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-inst-${System.nanoTime()}")
            .apply { mkdirs() }
        prefix = File(dir, "prefix")
        ledger = RuntimeLedger(File(dir, "ledger.json"))
        installer = ArtifactInstaller(prefix, ledger)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun artifact(id: String, bytes: ByteArray) = RuntimeArtifact(
        id = id,
        version = "1.0",
        size = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) },
        path = "pool/main/$id/${id}_1.0.deb"
    )

    /** Verified bytes staged where the downloader would have left them. */
    private fun cached(id: String, bytes: ByteArray): File =
        File(dir, "$id.deb").apply { writeBytes(bytes) }

    @Test
    fun `install unpacks and records exactly the paths it created`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run", "share/tool/data" to "payload")
        val art = artifact("tool", bytes)
        ledger.markVerified(art)

        val result = installer.install(art, cached("tool", bytes))

        assertTrue("got $result", result is InstallResult.Installed)
        assertEquals("run", File(prefix, "bin/tool").readText())
        assertEquals("payload", File(prefix, "share/tool/data").readText())

        val record = requireNotNull(ledger.get("tool"))
        assertTrue(record.isInstalled)
        assertEquals(listOf("bin/tool", "share/tool/data"), record.installedFiles.sorted())
        assertEquals("nothing should have been refused", 0, record.skippedEntries)
    }

    @Test
    fun `bytes that were never verified are never unpacked`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run")
        val art = artifact("tool", bytes)

        val result = installer.install(art, cached("tool", bytes))

        assertTrue("got $result", result is InstallResult.Failed)
        val reason = (result as InstallResult.Failed).reason
        assertTrue("reason was: $reason", reason.contains("no verification record"))
        assertFalse(File(prefix, "bin/tool").exists())
    }

    @Test
    fun `installing twice does no work the second time`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run")
        val art = artifact("tool", bytes)
        ledger.markVerified(art)
        val deb = cached("tool", bytes)

        assertTrue(installer.install(art, deb) is InstallResult.Installed)
        val second = installer.install(art, deb)

        assertTrue("got $second", second is InstallResult.AlreadyInstalled)
        assertEquals(1, ledger.get("tool")!!.installedFiles.size)
    }

    @Test
    fun `a package that is not a deb leaves nothing behind`() {
        val junk = ByteArray(400) { 9 }
        val art = artifact("broken", junk)
        ledger.markVerified(art)

        val result = installer.install(art, cached("broken", junk))

        assertTrue("got $result", result is InstallResult.Failed)
        assertFalse("must not count as installed", ledger.get("broken")!!.isInstalled)
        assertFalse("staging tree must be cleaned up", File(dir, "staging/broken").exists())
        assertEquals("prefix must be untouched", 0, prefix.listFiles()?.size ?: 0)
    }

    @Test
    fun `uninstall removes the package's own files but keeps its verification`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run", "share/tool/data" to "payload")
        val art = artifact("tool", bytes)
        ledger.markVerified(art)
        installer.install(art, cached("tool", bytes))

        val result = installer.uninstall("tool")

        assertTrue("got $result", result is UninstallResult.Removed)
        assertEquals(2, (result as UninstallResult.Removed).files)
        assertEquals(0, result.keptForOthers)
        assertFalse(File(prefix, "bin/tool").exists())
        assertFalse(File(prefix, "share/tool/data").exists())
        assertTrue("verification is not thrown away with the files", ledger.contains("tool"))
        assertFalse(ledger.get("tool")!!.isInstalled)
    }

    @Test
    fun `a path another package still claims survives an uninstall`() {
        val a = artifact("pkg-a", DebFixtures.debOf("bin/shared" to "from a", "bin/a" to "a"))
        val b = artifact("pkg-b", DebFixtures.debOf("bin/shared" to "from b", "bin/b" to "b"))
        ledger.markVerified(a)
        ledger.markVerified(b)
        installer.install(a, cached("a", DebFixtures.debOf("bin/shared" to "from a", "bin/a" to "a")))
        installer.install(b, cached("b", DebFixtures.debOf("bin/shared" to "from b", "bin/b" to "b")))

        val result = installer.uninstall("pkg-a")

        assertTrue("got $result", result is UninstallResult.Removed)
        val removed = result as UninstallResult.Removed
        assertEquals("only pkg-a's exclusive file goes", 1, removed.files)
        assertEquals("the shared one is kept and said so", 1, removed.keptForOthers)
        assertTrue("pkg-b still needs it", File(prefix, "bin/shared").isFile)
        assertFalse(File(prefix, "bin/a").exists())
        assertTrue(File(prefix, "bin/b").isFile)
    }

    @Test
    fun `uninstalling something that was never unpacked says so`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run")
        ledger.markVerified(artifact("tool", bytes))

        val result = installer.uninstall("tool")

        assertTrue("got $result", result is UninstallResult.NotInstalled)
        assertTrue(
            "reason was: ${(result as UninstallResult.NotInstalled).reason}",
            result.reason.contains("not unpacked")
        )
    }

    @Test
    fun `removeAll clears the prefix but leaves the ledger readable`() {
        val bytes = DebFixtures.debOf("bin/tool" to "run")
        val art = artifact("tool", bytes)
        ledger.markVerified(art)
        installer.install(art, cached("tool", bytes))

        installer.removeAll()

        assertFalse(File(prefix, "bin/tool").exists())
        assertTrue("the cached, verified bytes are still proven good", ledger.contains("tool"))
    }

    // --- R4: a glibc root filesystem --------------------------------------
    //
    // Not a package. Its paths are already the ones the filesystem will have,
    // and it must land somewhere of its own: the prefix is a bionic userland
    // and merging the two leaves binaries bound to the wrong loader.

    private fun rootfsArtifact(id: String, bytes: ByteArray) = RuntimeArtifact(
        id = id,
        version = "1.0",
        size = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) },
        path = "ubuntu-noble-oci-arm64-root.tar.gz",
        url = "https://partner-images.canonical.com/oci/noble/20260924/" +
            "ubuntu-noble-oci-arm64-root.tar.gz",
        kind = RuntimeArtifact.Kind.ROOTFS
    )

    @Test
    fun `a root filesystem unpacks beside the prefix, never inside it`() {
        val bytes = DebFixtures.rootfsTgz(
            "usr/bin/bash" to "ELF",
            "etc/os-release" to "NAME=Ubuntu"
        )
        val art = rootfsArtifact("glibc-rootfs", bytes)
        ledger.markVerified(art)

        val result = installer.install(art, cached("glibc-rootfs", bytes))

        assertTrue("got $result", result is InstallResult.Installed)
        val root = installer.rootfsRoot
        assertEquals("ELF", File(root, "usr/bin/bash").readText())
        assertEquals("NAME=Ubuntu", File(root, "etc/os-release").readText())
        // The whole point of the sibling directory. A bionic prefix with a
        // glibc /usr inside it is a userland that is neither.
        assertFalse("must not land in the prefix", File(prefix, "usr/bin/bash").exists())
        assertFalse("rootfs must be a sibling of the prefix", root == prefix)
    }

    @Test
    fun `the record names top level entries only, and that is enough to remove it`() {
        val bytes = DebFixtures.rootfsTgz(
            "usr/bin/bash" to "ELF",
            "usr/bin/dash" to "ELF",
            "etc/os-release" to "NAME=Ubuntu"
        )
        val art = rootfsArtifact("glibc-rootfs", bytes)
        ledger.markVerified(art)
        installer.install(art, cached("glibc-rootfs", bytes))

        val record = requireNotNull(ledger.get("glibc-rootfs"))
        assertTrue(record.isInstalled)
        // The ledger claims "this app put these here". Naming all three paths
        // says the same thing at a size that grows with every image; the
        // top level is the honest summary, and removal recurses either way.
        assertEquals(listOf("etc", "usr"), record.installedFiles.sorted())
    }

    @Test
    fun `a root filesystem with nothing in it is refused, not recorded`() {
        val bytes = DebFixtures.gzip(DebFixtures.tar(emptyList()))
        val art = rootfsArtifact("glibc-rootfs", bytes)
        ledger.markVerified(art)

        val result = installer.install(art, cached("glibc-rootfs", bytes))

        assertTrue("got $result", result is InstallResult.Failed)
        assertTrue(
            "reason was: ${(result as InstallResult.Failed).reason}",
            result.reason.contains("no filesystem")
        )
        // Nothing unpacked means nothing claimed — otherwise the runner would
        // report a glibc userland that is not there.
        assertFalse(requireNotNull(ledger.get("glibc-rootfs")).isInstalled)
        assertFalse(installer.rootfsRoot.exists())
    }

    @Test
    fun `reinstalling replaces the tree rather than merging into the old one`() {
        val first = DebFixtures.rootfsTgz("usr/bin/bash" to "OLD", "stale/file" to "gone")
        val art = rootfsArtifact("glibc-rootfs", first)
        ledger.markVerified(art)
        installer.install(art, cached("glibc-rootfs", first))
        assertTrue(File(installer.rootfsRoot, "stale/file").isFile)

        val second = DebFixtures.rootfsTgz("usr/bin/bash" to "NEW")
        ledger.markUninstalled("glibc-rootfs")
        val reinstalled = rootfsArtifact("glibc-rootfs", second)
        ledger.markVerified(reinstalled)
        installer.install(reinstalled, cached("glibc-rootfs2", second))

        assertEquals("NEW", File(installer.rootfsRoot, "usr/bin/bash").readText())
        // An upgraded image that kept the old file would be a tree assembled
        // from two different releases, which is not a version of anything.
        assertFalse(
            "a file from the previous image survived",
            File(installer.rootfsRoot, "stale/file").exists()
        )
    }

    @Test
    fun `unverified rootfs bytes are refused exactly as a package would be`() {
        val bytes = DebFixtures.rootfsTgz("usr/bin/bash" to "ELF")
        val art = rootfsArtifact("glibc-rootfs", bytes)
        // No markVerified: this is the case where the bytes came from somewhere
        // the app did not check.
        val result = installer.install(art, cached("glibc-rootfs", bytes))

        assertTrue("got $result", result is InstallResult.Failed)
        assertTrue(
            "reason was: ${(result as InstallResult.Failed).reason}",
            result.reason.contains("no verification record")
        )
    }
}
