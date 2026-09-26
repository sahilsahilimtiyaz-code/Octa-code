package com.sahil.octacode.di

import com.sahil.octacode.core.runtime.ArtifactDownloader
import com.sahil.octacode.core.runtime.ArtifactInstaller
import com.sahil.octacode.core.runtime.BundledRuntimeManifest
import com.sahil.octacode.core.runtime.RuntimeIndex
import com.sahil.octacode.core.runtime.RuntimeLedger
import com.sahil.octacode.core.runtime.RuntimeManifest
import com.sahil.octacode.core.runtime.RuntimeProvisioner
import com.sahil.octacode.data.agent.DefaultAgentRepository
import com.sahil.octacode.core.shell.PrefixShell
import com.sahil.octacode.core.shell.ProotRunner
import com.sahil.octacode.data.net.KtorHttpFactory
import com.sahil.octacode.domain.agent.AgentRepository
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// R1/R2 runtime graph: bundled manifest → downloader (own client, no request
// cap) → verification ledger → installer → provisioner, plus the index that
// answers "where is tool X" from what is genuinely on disk, and the shell
// (R3) that executes what the index names. Consumed by RuntimeScreen and
// TerminalScreen.
val runtimeModule = module {
    single<RuntimeManifest> { BundledRuntimeManifest(androidContext().assets).load() }

    single { RuntimeLedger(File(androidContext().filesDir, "runtimes/ledger.json")) }

    // Dedicated client: artifact downloads can be 100MB+, far past the 90s
    // request timeout of the shared API client.
    single { ArtifactDownloader(KtorHttpFactory.createDownloadClient()) }

    // Everything unpacks into one prefix, Termux-style.
    single { ArtifactInstaller(File(androidContext().filesDir, "runtimes/prefix"), get()) }

    single { RuntimeIndex(File(androidContext().filesDir, "runtimes/prefix"), get()) }

    // R3: the index says where a shell is, this runs it. HOME lives outside
    // the prefix so uninstalling the runtime cannot delete the shell's own
    // home directory, and so a shell never writes into unpacked package files.
    single {
        PrefixShell(
            prefix = File(androidContext().filesDir, "runtimes/prefix"),
            home = File(androidContext().filesDir, "home"),
            index = get()
        )
    }

    // R4: PRoot plus a glibc root filesystem, so a prebuilt Linux binary can
    // run at all — the Termux prefix is bionic and cannot load one. Same home
    // as the Termux shell on purpose: a file written in either is one file, not
    // two trees that quietly diverge.
    single {
        ProotRunner(
            rootfs = File(androidContext().filesDir, "runtimes/rootfs"),
            prefix = File(androidContext().filesDir, "runtimes/prefix"),
            home = File(androidContext().filesDir, "home"),
            index = get()
        )
    }

    // M13a: what the app can install and start, read from the same manifest
    // and ledger the runtime screen writes to. Reporting installed-ness from
    // one place is the point — a second list would go on claiming a tool was
    // available after the user removed it.
    single<AgentRepository> {
        DefaultAgentRepository(
            manifest = get(),
            ledger = get(),
            rootfs = File(androidContext().filesDir, "runtimes/rootfs"),
            proot = get()
        )
    }

    single {
        RuntimeProvisioner(
            manifest = get(),
            ledger = get(),
            downloader = get(),
            installer = get(),
            cacheDir = File(androidContext().filesDir, "runtimes/cache")
        )
    }
}
