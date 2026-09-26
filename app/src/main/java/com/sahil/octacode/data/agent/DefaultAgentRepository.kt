package com.sahil.octacode.data.agent

import com.sahil.octacode.core.agent.AgentDef
import com.sahil.octacode.core.runtime.RuntimeArtifact
import com.sahil.octacode.core.runtime.RuntimeLedger
import com.sahil.octacode.core.runtime.RuntimeManifest
import com.sahil.octacode.core.shell.ProotRunner
import com.sahil.octacode.domain.agent.AgentRepository
import java.io.File

/**
 * Reports the agents the manifest pins, judged by what is actually on disk.
 *
 * The order the checks are written in is the whole point. "Installed" and
 * "can be run" are different facts, and conflating them is how an app ends up
 * offering a tool whose only missing piece is the runtime that executes it:
 *
 *  - the binary is present, but PRoot is not → installed, not launchable
 *  - the binary is present, but the glibc userland is not → impossible today
 *    (the binary lives inside that userland) and checked anyway, because a
 *    half-removed runtime should say so rather than look ready
 *  - the ledger says installed, but the file is gone → the runtime was
 *    partially removed, and the honest answer is to reinstall
 *  - nothing → not installed, with the real byte count
 *
 * The byte count comes from the manifest pin, so "install it" is quoted from
 * the same number the download will actually cost.
 */
class DefaultAgentRepository(
    private val manifest: RuntimeManifest,
    private val ledger: RuntimeLedger,
    private val rootfs: File,
    private val proot: ProotRunner,
) : AgentRepository {

    override suspend fun agents(): List<AgentDef> =
        manifest.distinctArtifacts()
            .filter { it.kind == RuntimeArtifact.Kind.EXECUTABLE }
            .map { describe(it) }

    private fun describe(artifact: RuntimeArtifact): AgentDef {
        val guestPath = artifact.guestCommandPath
            ?: return AgentDef(
                id = artifact.id,
                displayName = artifact.id,
                isEnabled = false,
                blockedReason = "This build has no guest location for it."
            )

        val binary = File(rootfs, guestPath)
        val recorded = ledger.get(artifact.id)?.isInstalled == true
        // canExecute, not exists: R4.1's lesson is that a file can be present
        // and still be a file nobody can run, and every check that only asked
        // "is it there" would have said yes.
        val onDisk = binary.isFile && binary.canExecute()
        val prootPresent = proot.prootPath() != null
        val guestUsable = proot.isInstalled()

        val blocked = when {
            onDisk && prootPresent && guestUsable -> null

            onDisk && !prootPresent ->
                "Installed, but PRoot is not — without it a glibc binary cannot " +
                    "be started at all. Install \"PRoot sandbox\" in Settings → Runtime."

            onDisk ->
                "Installed, but the glibc userland is not usable — the shell it " +
                    "would run through is missing. Reinstall \"glibc userland\" in " +
                    "Settings → Runtime."

            recorded ->
                "Recorded as installed, but $guestPath is gone — the runtime was " +
                    "partly removed. Install it again from Settings → Runtime."

            !prootPresent ->
                "Needs PRoot and the glibc userland, and neither is installed. " +
                    "Install \"PRoot sandbox\" and \"glibc userland\" in Settings → Runtime first."

            else ->
                "Not installed. ${artifact.size / 1_048_576} MB, fetched from the " +
                    "OpenCode project and checked against a SHA-256 pinned in this build."
        }

        return AgentDef(
            id = artifact.id,
            displayName = titleFor(artifact.id),
            // Installed and launchable are reported separately on purpose: the
            // file being here is a fact, and whether anything can start it is a
            // different one.
            isInstalled = onDisk,
            // The version is the pin we verified, not a guess and not a probe:
            // these are the exact bytes that were unpacked.
            version = if (onDisk) artifact.version else null,
            defaultRuntimeId = "glibc",
            blockedReason = blocked,
            command = if (blocked == null) artifact.id else null,
            downloadBytes = artifact.size
        )
    }

    /**
     * The agent's name, taken from the group that installs it.
     *
     * From the manifest rather than a map here, so a new agent is named by the
     * same line of the same file that decides what gets downloaded — and so
     * there is no second list to forget to update.
     */
    private fun titleFor(artifactId: String): String =
        manifest.groups
            .firstOrNull { group -> group.items.any { it.id == artifactId } }
            ?.title
            ?: artifactId
}
