package com.sahil.octacode.di

import com.sahil.octacode.data.chat.ChatDao
import com.sahil.octacode.data.chat.ChatDatabase
import com.sahil.octacode.data.chat.RoomChatRepository
import com.sahil.octacode.data.mission.MissionDao
import com.sahil.octacode.data.mission.MissionDatabase
import com.sahil.octacode.data.mission.MissionDiffDao
import com.sahil.octacode.data.mission.PhaseEventDao
import com.sahil.octacode.data.mission.PhaseRunDao
import com.sahil.octacode.data.mission.RoomMissionRepository
import com.sahil.octacode.data.model.ModelUserStateStore
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.domain.chat.ChatRepository
import com.sahil.octacode.domain.chat.RetentionReport
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

    // App settings: one store, read as a flow by every screen. Registered here
    // rather than in AppModule because it is persistence, not presentation.
    single<SettingsRepository> { SettingsRepository(androidContext()) }

    // Favorites and recents for the model selector. Beside settings because it
    // is the same kind of thing: preferences, read as a flow, not secrets.
    single { ModelUserStateStore(androidContext()) }

    // Chat history lives in its own database — see ChatDatabase for why it
    // must not join octa_missions (destructive fallback + no exported schema).
    single<ChatDatabase> { ChatDatabase.build(androidContext()) }
    single<ChatDao> { get<ChatDatabase>().chatDao() }
    single<ChatRepository> { RoomChatRepository(dao = get()) }

    // What the startup retention pass did. Registered beside the repositories
    // because it is reported state about data, not a screen's own concern.
    single { RetentionReport() }
}
