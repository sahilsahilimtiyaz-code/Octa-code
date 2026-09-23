package com.sahil.octacode.di

import com.sahil.octacode.data.mission.MissionDao
import com.sahil.octacode.data.mission.MissionDatabase
import com.sahil.octacode.data.mission.MissionDiffDao
import com.sahil.octacode.data.mission.PhaseEventDao
import com.sahil.octacode.data.mission.PhaseRunDao
import com.sahil.octacode.data.mission.RoomMissionRepository
import com.sahil.octacode.domain.mission.MissionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// M3a mission persistence graph. Consumed by M3b engine + M3d UI (no orphans).
val dataModule = module {
    single<MissionDatabase> { MissionDatabase.build(androidContext()) }

    single<MissionDao> { get<MissionDatabase>().missionDao() }
    single<PhaseRunDao> { get<MissionDatabase>().phaseRunDao() }
    single<PhaseEventDao> { get<MissionDatabase>().phaseEventDao() }
    single<MissionDiffDao> { get<MissionDatabase>().missionDiffDao() }

    single<MissionRepository> {
        RoomMissionRepository(
            missionDao = get(),
            phaseRunDao = get(),
            phaseEventDao = get(),
            missionDiffDao = get()
        )
    }
}
