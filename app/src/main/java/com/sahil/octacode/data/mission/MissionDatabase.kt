package com.sahil.octacode.data.mission

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// M3a: single Room DB for mission state (spec: persisted, survives restart).
@Database(
    entities = [
        MissionEntity::class,
        PhaseRunEntity::class,
        PhaseEventEntity::class,
        MissionDiffEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MissionDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun phaseRunDao(): PhaseRunDao
    abstract fun phaseEventDao(): PhaseEventDao
    abstract fun missionDiffDao(): MissionDiffDao

    companion object {
        const val NAME = "octa_missions"

        fun build(context: Context): MissionDatabase =
            Room.databaseBuilder(context.applicationContext, MissionDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
