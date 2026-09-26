package com.sahil.octacode.core.shell

import com.sahil.octacode.core.runtime.RuntimeArtifact
import com.sahil.octacode.core.runtime.RuntimeIndex
import com.sahil.octacode.core.runtime.RuntimeLedger
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R4's whole claim is one sentence: a prebuilt glibc Linux binary cannot run
 * in the bionic Termux prefix, and PRoot is what makes it run anyway.
 *
 * The argument list is where that claim is either true or quietly untrue, and
 * it is the part that is easiest to get wrong in a way nothing notices — a
 * missing `-b /proc` still starts, a missing `-0` still starts, and a Termux
 * prefix left on the guest PATH starts too, and then every bionic binary on
 * it dies on the ELF loader. So it is pinned here, without needing a root
 * filesystem, a PRoot binary, or a device.
 */
class ProotRunnerTest {

    private lateinit var dir: File
    private lateinit var prefix: File
    private lateinit var rootfs: File
    private lateinit var ledger: RuntimeLedger
    private lateinit var index: RuntimeIndex

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-proot-${System.nanoTime()}")
            .apply { mkdirs() }
        prefix = File(dir, "prefix")
        rootfs = File(dir, "rootfs")
        ledger = RuntimeLedger(File(dir, "ledger.json"))
        index = RuntimeIndex(prefix, ledger)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun argv(vararg binds: String) = prootArgv(
        proot = File(prefix, "bin/proot").absolutePath,
        rootfs = rootfs.absolutePath,
        home = File(dir, "home").absolutePath,
        command = "uname -a",
        binds = binds.toList()
    )

    /** Every value that followed a `-b`, in the order they were given. */
    private fun List<String>.boundPaths(): List<String> =
        mapIndexedNotNull { i, arg -> if (i > 0 && get(i - 1) == "-b") arg else null }

    private fun List<String>.valueAfter(flag: String): String? {
        val i = indexOf(flag)
        return if (i >= 0) getOrNull(i + 1) else null
    }

    private fun runner() = ProotRunner(
        rootfs = rootfs,
        prefix = prefix,
        home = File(dir, "home"),
        index = index
    )

    /** Marks proot unpacked, exactly the way a real install would. */
    private fun installProot() {
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/proot").writeText("#!/system/bin/sh\n")
        ledger.markVerified(
            RuntimeArtifact(
                id = "proot",
                version = "1",
                size = 1,
                sha256 = "0".repeat(64),
                path = "pool/main/p/proot/proot_1.deb"
            )
        )
        ledger.markInstalled("proot", listOf("bin/proot"), 0)
    }

    // --- the invocation ----------------------------------------------------

    @Test
    fun `the command runs in the guest's own bash`() {
        val a = argv()
        // Not the prefix's bash: that one is bionic, and running it under a
        // glibc root is precisely the failure this class exists to prevent.
        assertEquals(ProotRunner.GUEST_SHELL, a[a.size - 3])
        assertEquals("-c", a[a.size - 2])
        assertEquals("uname -a", a[a.size - 1])
    }

    @Test
    fun `proot is told which root filesystem to use`() {
        assertEquals(rootfs.absolutePath, argv().valueAfter("-r"))
    }

    @Test
    fun `the kernel interfaces a linux process needs are bound in`() {
        // Without /proc a great deal of userspace gives up, and it usually
        // presents as a shell that hangs rather than as a missing flag.
        val bound = argv().boundPaths()
        listOf("/dev", "/proc", "/sys").forEach {
            assertTrue("$it must be bound, got $bound", bound.contains(it))
        }
    }

    @Test
    fun `the guest home is the app's own home, not a directory inside the image`() {
        // Otherwise a file the user creates in the sandbox vanishes when the
        // root filesystem is replaced, and nothing in the app can see it.
        assertTrue(
            "was: ${argv().boundPaths()}",
            argv().boundPaths().contains("${File(dir, "home").absolutePath}:${ProotRunner.GUEST_HOME}")
        )
    }

    @Test
    fun `the guest starts from a clean environment, not android's`() {
        val a = argv()
        // -i is the whole isolation boundary for the environment. Without it
        // the guest inherits whatever the Android process was handed, several
        // of which mean nothing in a Linux userland.
        val envIndex = a.indexOf("/usr/bin/env")
        assertTrue("env must be used: $a", envIndex >= 0)
        assertEquals("-i", a[envIndex + 1])
    }

    @Test
    fun `the bionic prefix is never on the guest path`() {
        val a = argv()
        val guestPath = a.single { it.startsWith("PATH=") }
        assertFalse(
            "a bionic binary on the guest path dies on the ELF loader: $guestPath",
            guestPath.contains(prefix.absolutePath)
        )
        // It is Ubuntu's own PATH, so a guest `ls` is the guest's `ls`.
        assertEquals("PATH=" + ProotRunner.GUEST_PATH, guestPath)
    }

    @Test
    fun `extra binds are passed through and do not disturb the fixed ones`() {
        val bound = argv("/sdcard/Download", "/data/local/tmp").boundPaths()
        assertTrue(bound.contains("/sdcard/Download"))
        assertTrue(bound.contains("/data/local/tmp"))
        listOf("/dev", "/proc", "/sys").forEach {
            assertTrue("$it must survive an extra bind: $bound", bound.contains(it))
        }
    }

    @Test
    fun `the caller is presented as root and the guest is cleaned up after`() {
        val a = argv()
        // Package tools refuse to run otherwise, and nothing here is actually
        // privileged.
        assertTrue("proot must fake uid 0: $a", a.contains("-0"))
        // Without this a long-running guest process outlives the command that
        // started it, still holding the root filesystem open.
        assertTrue("guest must be killed on exit: $a", a.contains("--kill-on-exit"))
    }

    // --- readiness ---------------------------------------------------------
    //
    // isInstalled is what the terminal's chip label and its Run button both
    // read, so it has to mean "this can run" rather than "a file exists".

    @Test
    fun `neither half alone counts as installed`() {
        assertFalse("no proot and no rootfs", runner().isInstalled())

        installProot()
        assertFalse("proot but no root filesystem", runner().isInstalled())

        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/bash").writeText("binary")
        assertTrue("both halves present", runner().isInstalled())
    }

    @Test
    fun `a missing runtime is a named failure, not an empty success`() {
        val result = runBlocking { runner().run("uname -a") }

        assertFalse("must not look like it ran", result.executed)
        assertEquals(ShellResult.EXIT_NOT_INSTALLED, result.exitCode)
        assertTrue("must name the fix: ${result.stderr}", result.stderr.contains("PRoot"))
    }

    @Test
    fun `a root filesystem without a shell is reported as missing, not attempted`() {
        installProot()

        val result = runBlocking { runner().run("uname -a") }

        assertEquals(ShellResult.EXIT_NOT_INSTALLED, result.exitCode)
        assertTrue(
            "must say which runtime is absent: ${result.stderr}",
            result.stderr.contains("glibc")
        )
    }
}
