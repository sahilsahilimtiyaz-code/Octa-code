package com.sahil.octacode.di

import com.sahil.octacode.domain.mission.MissionEngine
import org.koin.dsl.module

// M3b: engine singleton — consumed by MissionLaunch / MissionDetail / Home.
val engineModule = module {
    single { MissionEngine(repository = get(), registry = get(), providers = get()) }
}
