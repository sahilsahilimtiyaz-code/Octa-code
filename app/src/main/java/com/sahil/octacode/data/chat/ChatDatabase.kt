package com.sahil.octacode.data.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Chat history, deliberately a **separate database** from `octa_missions`.
 *
 * `MissionDatabase` is version 1, exports no schema, and was built with
 * `fallbackToDestructiveMigration()`. Adding tables to it would bump the
 * version, and with that fallback in place a migration Room could not verify
 * would silently **delete every mission a released user has**. With
 * `exportSchema = false` there is also no schema JSON to validate against, so
 * a broken migration would surface as a crash on first open instead.
 *
 * A new file has neither problem: no existing data to lose, no migration to
 * guess at.
 *
 * Notably this builder does *not* opt into destructive migration. Losing a
 * conversation because a schema change went wrong is exactly the silent data
 * loss this project refuses to ship — if a future migration cannot be proven,
 * it should fail loudly.
 */
@Database(
    entities = [ChatSessionEntity::class, ChatTurnEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        const val NAME = "octa_chats"

        fun build(context: Context): ChatDatabase =
            Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, NAME)
                .build()
    }
}
