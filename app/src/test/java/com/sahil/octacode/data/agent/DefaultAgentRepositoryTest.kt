package com.sahil.octacode.data.agent

import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.runtime.RuntimeArtifact
import com.sahil.octacode.core.runtime.RuntimeGroup
import com.sahil.octacode.core.runtime.RuntimeIndex
import com.sahil.octacode.core.runtime.RuntimeLedger
import com.sahil.octacode.core.runtime.RuntimeManifest
import com.sahil.octacode.core.shell.ProotRunner
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * An agent row is either launchable or says what is missing. Those are the
 * only two acceptable states, and the second is the one that is easy to get
 * wrong: a single "unavailable" across three different missing pieces sends
 * the user hunting for the wrong one, and a row that reports ready while
 * nothing can start it is the affordance that does nothing.
 */
class DefaultAgentRepositoryTest {

    private lateinit var dir: File
    private lateinit var rootfs: File
    private lateinit var prefix: File
    private lateinit var ledger: RuntimeLedger

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "octa-agents-${System.nanoTime()}")
            .apply { mkdirs() }
        rootfs = File(dir, "rootfs")
        prefix = File(dir, "prefix")
        ledger = RuntimeLedger(File(dir, "ledger.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val agent = RuntimeArtifact(
        id = "opencode",
        version = "1.18.32",
        size = 60_418_875,
        sha256 = "0".repeat(64),
        path = "opencode-linux-arm64.tar.gz",
        url = "https://example.invalid/opencode-linux-arm64.tar.gz",
        kind = RuntimeArtifact.Kind.EXECUTABLE
    )

    private val aDeb = RuntimeArtifact(
        id = "bash",
        version = "5.3",
        size = 956_852,
        sha256 = "1".repeat(64),
        path = "pool/main/b/bash/bash_5.3_aarch64.deb"
    )

    private fun manifest(optionalAgent: Boolean = true) = RuntimeManifest(
        schema = 1,
        name = "octa-runtime",
        arch = "aarch64",
        abi = "arm64-v8a",
        index = "https://example.invalid/Packages",
        baseUrl = "https://example.invalid/",
        generated = "2026-09-26",
        groups = listOf(
            RuntimeGroup("base", "Linux userland", "", false, aDeb.size, listOf(aDeb)),
            RuntimeGroup(
                "opencode", "OpenCode", "", optionalAgent, agent.size, listOf(agent)
            )
        ),
        unionBytes = aDeb.size + agent.size,
        packageCount = 2
    )

    private fun installProot() {
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/proot").writeText("#!/system/bin/sh\n")
        ledger.markVerified(
            RuntimeArtifact(
                id = "proot", version = "1", size = 1,
                sha256 = "0".repeat(64), path = "pool/main/p/proot/proot_1.deb"
            )
        )
        ledger.markInstalled("proot", listOf("bin/proot"), 0)
    }

    private fun installRootfs() {
        File(rootfs, "usr").mkdirs()
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/bash").writeText("ELF")
    }

    /** Puts the agent where the installer would have put it. */
    private fun installAgent(recordIt: Boolean = true, runnable: Boolean = true) {
        val target = File(rootfs, "usr/local/bin/opencode")
        target.parentFile?.mkdirs()
        target.writeText("ELF")
        if (runnable) target.setExecutable(true, false)
        if (recordIt) ledger.markInstalled("opencode", listOf("usr/local/bin/opencode"), 0)
    }

    private fun repository(m: RuntimeManifest = manifest()) = DefaultAgentRepository(
        manifest = m,
        ledger = ledger,
        rootfs = rootfs,
        proot = ProotRunner(
            rootfs = rootfs,
            prefix = prefix,
            home = File(dir, "home"),
            index = RuntimeIndex(prefix, ledger)
        )
    )

    private fun agents(m: RuntimeManifest = manifest()) = runBlocking {
        repository(m).agents()
    }

    // --- what the manifest says -------------------------------------------

    @Test
    fun `only guest executables are agents`() {
        // A .deb is a runtime package, not something a person runs on purpose.
        // Listing one would put "bash" on a screen titled Agents.
        val found = agents()

        assertEquals(listOf("opencode"), found.map { it.id })
        assertEquals("OpenCode", found.single().displayName)
    }

    @Test
    fun `an agent reports what it would cost to install`() {
        // Quoted from the manifest pin, so the number on screen is the number
        // the download will actually charge.
        assertEquals(60_418_875, agents().single().downloadBytes)
    }

    // --- readiness --------------------------------------------------------

    @Test
    fun `nothing installed says so, and names both runtimes it needs`() {
        val agent = agents().single()

        assertFalse(agent.isInstalled)
        assertFalse(agent.isLaunchable)
        assertNull("an unlaunchable agent must carry no command", agent.command)
        val reason = agent.blockedReason
        assertNotNull(reason)
        assertTrue("must name PRoot: $reason", reason!!.contains("PRoot"))
        assertTrue("must name the glibc userland: $reason", reason.contains("glibc userland"))
        assertTrue("must say where to fix it: $reason", reason.contains("Runtime"))
    }

    @Test
    fun `installed with no proot is installed but not launchable`() {
        // The distinction this whole class exists for. The file is here; what
        // is missing is the thing that can start it, and those need different
        // fixes.
        installAgent()

        val agent = agents().single()

        assertTrue("the file really is there", agent.isInstalled)
        assertFalse(agent.isLaunchable)
        assertTrue(
            "must name the missing piece: ${agent.blockedReason}",
            agent.blockedReason!!.contains("PRoot")
        )
    }

    @Test
    fun `everything present makes it launchable with a real command`() {
        installProot()
        installRootfs()
        installAgent()

        val agent = agents().single()

        assertTrue("was: ${agent.blockedReason}", agent.isLaunchable)
        assertNull(agent.blockedReason)
        assertEquals("opencode", agent.command)
        assertEquals("1.18.32", agent.version)
        // The command is the manifest id, and it is also the guest filename —
        // if those ever drift, the row would launch something that is not there.
        assertEquals(agent.id, agent.command)
        assertTrue(agent.isSelectable)
    }

    @Test
    fun `a recorded agent whose file vanished asks for a reinstall`() {
        // Partly removed runtime: the ledger still claims it. Reporting the
        // tool as ready here would send the user to a command that is not there.
        installProot()
        installRootfs()
        // markVerified first: the ledger refuses to record an install for an
        // artifact it has no proof of, so without this there is no claim to
        // have gone stale and the test would silently be testing something else.
        ledger.markVerified(agent)
        ledger.markInstalled("opencode", listOf("usr/local/bin/opencode"), 0)

        val agent = agents().single()

        assertFalse("the file is gone, so it is not installed", agent.isInstalled)
        assertFalse(agent.isLaunchable)
        assertTrue(
            "must ask for a reinstall: ${agent.blockedReason}",
            agent.blockedReason!!.contains("Install it again")
        )
    }

    @Test
    fun `a binary that landed non-runnable is not reported as installed`() {
        // R4.1's exact failure, one layer up: the file is present and every
        // check that asked only "is it there" would have said yes.
        installProot()
        installRootfs()
        installAgent(runnable = false)

        val agent = agents().single()

        assertFalse("present but not runnable is not usable", agent.isInstalled)
        assertFalse(agent.isLaunchable)
    }

    @Test
    fun `a glibc userland without its shell is reported, not assumed`() {
        // The rootfs is there but the guest cannot run anything, which is a
        // different fault from the rootfs being absent.
        installProot()
        File(rootfs, "usr").mkdirs()
        installAgent()

        val agent = agents().single()

        assertTrue("the binary is present", agent.isInstalled)
        assertFalse(agent.isLaunchable)
        assertTrue(
            "must say the userland is unusable: ${agent.blockedReason}",
            agent.blockedReason!!.contains("not usable")
        )
    }

    @Test
    fun `an optional agent group does not change what an agent is`() {
        // Whether it is in the default install is a manifest decision about
        // download cost; it has no bearing on whether the row works.
        installProot()
        installRootfs()
        installAgent()

        assertTrue(agents(manifest(optionalAgent = true)).single().isLaunchable)
        assertTrue(agents(manifest(optionalAgent = false)).single().isLaunchable)
    }
}
