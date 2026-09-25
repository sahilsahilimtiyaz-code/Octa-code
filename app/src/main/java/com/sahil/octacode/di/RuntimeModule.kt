package com.sahil.octacode.di

import com.sahil.octacode.core.runtime.ArtifactDownloader
import com.sahil.octacode.core.runtime.BundledRuntimeManifest
import com.sahil.octacode.core.runtime.RuntimeLedger
import com.sahil.octacode.core.runtime.RuntimeManifest
import com.sahil.octacode.core.runtime.RuntimeProvisioner
import com.sahil.octacode.data.net.KtorHttpFactory
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// R1 runtime provisioning graph: bundled manifest → downloader (own client, no
// request cap) → verification ledger → provisioner. Consumed by RuntimeScreen.
val runtimeModule = module {
    single<RuntimeManifest> { BundledRuntimeManifest(androidContext().assets).load() }

    single { RuntimeLedger(File(androidContext().filesDir, "runtimes/ledger.json")) }

    // Dedicated client: artifact downloads can be 100MB+, far past the 90s
    // request timeout of the shared API client.
    single { ArtifactDownloader(KtorHttpFactory.createDownloadClient()) }

    single {
        RuntimeProvisioner(
            manifest = get(),
            ledger = get(),
            downloader = get(),
            cacheDir = File(androidContext().filesDir, "runtimes/cache")
        )
    }
}
