package com.sahil.octacode.core.shell

import com.sahil.octacode.core.runtime.ArtifactInstaller
import com.sahil.octacode.core.runtime.DebFixtures
import com.sahil.octacode.core.runtime.InstallResult
import com.sahil.octacode.core.runtime.RuntimeArtifact
import com.sahil.octacode.core.runtime.RuntimeIndex
import com.sahil.octacode.core.runtime.RuntimeLedger
import com.sahil.octacode.ui.screens.renderShellOutput
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The shell must resolve only from paths this app unpacked, must hand the
 * prefix's environment to whatever it starts, and must never report success
 * for something that did not run.
 *
 * The end-to-end cases copy this host's own /bin/sh into the fixture prefix:
 * that exercises the real ProcessBuilder wiring — environment, concurrent
 * stream draining, exit-code capture — without needing an Android device or
 * a downloaded Termux package. They skip on a host with no POSIX shell.
 */
class PrefixShellTest {

    private lateinit var dir: File
    private lateinit var prefix: File
    private lateinit var home: File
    private lateinit var ledger: RuntimeLedger
    private lateinit var index: RuntimeIndex
    private lateinit var shell: PrefixShell

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-shell-${System.nanoTime()}")
            .apply { mkdirs() }
        prefix = File(dir, "prefix")
        home = File(dir, "home")
        ledger = RuntimeLedger(File(dir, "ledger.json"))
        index = RuntimeIndex(prefix, ledger)
        shell = PrefixShell(prefix, home, index)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun install(id: String, vararg paths: Pair<String, String>) {
        val bytes = DebFixtures.debOf(*paths)
        val artifact = RuntimeArtifact(
            id = id,
            version = "1.0",
            size = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) },
            path = "pool/main/$id/${id}_1.0.deb"
        )
        ledger.markVerified(artifact)
        val deb = File(dir, "$id.deb").apply { writeBytes(bytes) }
        val result = ArtifactInstaller(prefix, ledger).install(artifact, deb)
        check(result is InstallResult.Installed || result is InstallResult.AlreadyInstalled) {
            "fixture install failed: $result"
        }
    }

    /**
     * Put a shell the ledger already recognises at prefix/bin/sh, but make it
     * one this JVM can genuinely execute.
     */
    private fun installExecutableShell() {
        val system = File("/bin/sh")
        assumeTrue("no POSIX shell on this host", system.isFile)

        install("sh", "bin/sh" to "placeholder")
        val target = File(prefix, "bin/sh")
        system.copyTo(target, overwrite = true)
        assumeTrue("could not mark the fixture shell executable", target.setExecutable(true))
    }

    // --- resolution ------------------------------------------------------

    @Test
    fun `no shell is offered before anything is unpacked`() {
        assertNull(shell.shellPath())
        assertNull(shell.shellName())
        assertFalse(shell.isInstalled())
    }

    @Test
    fun `bash is preferred when the runtime provides several shells`() {
        install("bash", "bin/bash" to "b")
        install("dash", "bin/dash" to "d")
        install("sh", "bin/sh" to "s")

        assertEquals("bash", shell.shellName())
        assertEquals(File(prefix, "bin/bash").path, shell.shellPath()!!.path)
        assertTrue(shell.isInstalled())
    }

    @Test
    fun `dash is used when bash was never installed`() {
        install("dash", "bin/dash" to "d")
        install("sh", "bin/sh" to "s")

        assertEquals("dash", shell.shellName())
    }

    @Test
    fun `sh is the last resort`() {
        install("sh", "bin/sh" to "s")

        assertEquals("sh", shell.shellName())
    }

    @Test
    fun `a shell the index does not know is not borrowed from the host PATH`() {
        // /bin/sh almost certainly exists on the test host, but it is not part
        // of this app's verified runtime, so it must not resolve.
        assumeTrue("no POSIX shell on this host", File("/bin/sh").isFile)

        assertNull(shell.shellPath())
        assertFalse(shell.isInstalled())
    }

    // --- environment -----------------------------------------------------

    @Test
    fun `environment points every lookup at the prefix`() {
        val env = shell.environment()

        assertEquals(prefix.absolutePath, env["PREFIX"])
        assertEquals(home.absolutePath, env["HOME"])
        assertEquals(File(home, "tmp").absolutePath, env["TMPDIR"])
        assertEquals(File(prefix, "lib").absolutePath, env["LD_LIBRARY_PATH"])

        val path = env["PATH"]!!
        assertTrue("prefix bin must lead PATH, was $path", path.startsWith(File(prefix, "bin").absolutePath))
        assertTrue("prefix sbin must follow, was $path", path.contains(File(prefix, "sbin").absolutePath))
    }

    @Test
    fun `environment admits there is no terminal attached`() {
        // Claiming a tty that does not exist makes programs wait for input
        // that will never arrive.
        assertEquals("dumb", shell.environment()["TERM"])
    }

    // --- running ---------------------------------------------------------

    @Test
    fun `running without a runtime fails with a stated reason`() = runBlocking {
        val result = shell.run("echo hi")

        assertEquals(ShellResult.EXIT_NOT_INSTALLED, result.exitCode)
        assertFalse("nothing executed", result.executed)
        assertEquals("", result.binary)
        assertEquals("no output is claimed when no process ran", "", result.stdout)
        assertEquals(PrefixShell.NOT_INSTALLED_MESSAGE, result.stderr)
    }

    @Test
    fun `a command runs and reports its own exit code`() = runBlocking {
        installExecutableShell()

        val result = shell.run("echo hi")

        assertTrue(result.executed)
        assertEquals(File(prefix, "bin/sh").path, result.binary)
        assertEquals(0, result.exitCode)
        assertEquals("hi", result.stdout.trim())
    }

    @Test
    fun `a failing command keeps its non-zero exit`() = runBlocking {
        installExecutableShell()

        val result = shell.run("exit 3")

        assertEquals(3, result.exitCode)
        assertFalse(result.timedOut)
    }

    @Test
    fun `stdout and stderr are both captured`() = runBlocking {
        installExecutableShell()

        val result = shell.run("echo out; echo err 1>&2")

        assertTrue("stdout was ${result.stdout}", result.stdout.contains("out"))
        assertTrue("stderr was ${result.stderr}", result.stderr.contains("err"))
    }

    @Test
    fun `a shell is started in its own home directory`() = runBlocking {
        installExecutableShell()

        val result = shell.run("pwd")

        assertEquals(home.path, result.stdout.trim())
        assertTrue("HOME should have been created", home.isDirectory)
    }

    // --- display ---------------------------------------------------------

    @Test
    fun `a clean exit adds no status line`() {
        val lines = renderShellOutput(ShellResult("echo hi", "/bin/sh", 0, "hi\n", ""))

        assertEquals(listOf("hi"), lines)
    }

    @Test
    fun `a failure records the exit code after the output`() {
        val lines = renderShellOutput(ShellResult("bad", "/bin/sh", 2, "", "boom\n"))

        assertEquals(listOf("boom", "→ exit 2"), lines)
    }

    @Test
    fun `a shell that never started is not given an exit line`() {
        val lines = renderShellOutput(
            ShellResult(
                command = "echo hi",
                binary = "",
                exitCode = ShellResult.EXIT_NOT_INSTALLED,
                stdout = "",
                stderr = "Runtime not installed."
            )
        )

        assertEquals(listOf("Runtime not installed."), lines)
    }

    @Test
    fun `a timeout is named as a timeout`() {
        val lines = renderShellOutput(
            ShellResult("slow", "/bin/sh", ShellResult.EXIT_TIMEOUT, "", "", timedOut = true)
        )

        assertEquals(listOf("→ timed out"), lines)
    }

    @Test
    fun `trailing blank lines from a command are not rendered as empty rows`() {
        val lines = renderShellOutput(ShellResult("x", "/bin/sh", 0, "a\n\n\n", ""))

        assertEquals(listOf("a"), lines)
    }
}
