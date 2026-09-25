package com.sahil.octacode.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Write paths are deliberately narrow. [append] is the only way a turn enters
 * the table, so `lastMessageAt` cannot drift out of step with the history that
 * drives Recent Chats' ordering.
 */
@Dao
interface ChatDao {

    /** Most recently active first. */
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun session(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: String, title: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM chat_turns WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun turns(sessionId: String): List<ChatTurnEntity>

    @Insert
    suspend fun insertTurn(turn: ChatTurnEntity)

    /**
     * Atomic pair: the turn and the session's activity stamp move together.
     * If only the insert landed, a chat would sit at the bottom of Recent Chats
     * while holding new messages.
     */
    @Transaction
    suspend fun append(turn: ChatTurnEntity) {
        insertTurn(turn)
        touchSession(turn.sessionId, turn.createdAt)
    }

    @Query("UPDATE chat_sessions SET lastMessageAt = :at WHERE id = :id")
    suspend fun touchSession(id: String, at: Long)
}
