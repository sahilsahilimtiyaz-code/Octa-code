package com.sahil.octacode.di

import com.sahil.octacode.data.mission.FileSnapshotStore
import com.sahil.octacode.domain.mission.JvmProcessRunner
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.domain.mission.ProcessRunner
import com.sahil.octacode.domain.mission.SnapshotStore
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// M3c engine singleton — full 16-phase handlers + snapshot rollback + process runner.
val engineModule = module {
    single<SnapshotStore> {
        FileSnapshotStore(File(androidContext().filesDir, "mission_snapshots"))
    }
    single<ProcessRunner> { JvmProcessRunner() }
    single {
        MissionEngine(
            repository = get(),
            registry = get(),
            providers = get(),
            snapshots = get(),
            processRunner = get()
        )
    }
}
