package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.profile.RunProfile
import kotlinx.coroutines.flow.Flow

/**
 * Chat persistence. Rooms exist so that Sessions settings, the drawer's Recent
 * Chats and "reopen the same conversation after a restart" are real instead of
 * decorative — before this existed, [ChatEngine] kept turns in a StateFlow and
 * `clearConversation()` was the only way out.
 *
 * Implementations must never fabricate a session or a turn. A failure to read
 * surfaces as an exception the caller reports, not as an empty conversation
 * that looks like a fresh start.
 */
interface ChatRepository {

    /** Most recently active first — this is the order Recent Chats renders in. */
    val sessions: Flow<List<ChatSession>>

    suspend fun createSession(profile: RunProfile, title: String, now: Long): ChatSession

    suspend fun session(id: String): ChatSession?

    /** Turns in chronological order, which is the order they are replayed in. */
    suspend fun turns(sessionId: String): List<StoredTurn>

    /** Appends one turn and advances [ChatSession.lastMessageAt] together. */
    suspend fun append(turn: StoredTurn)

    /** Titles start as an excerpt of the first message; users may rename later. */
    suspend fun renameSession(sessionId: String, title: String)

    /** Deleting a session deletes its turns — no orphaned history is kept. */
    suspend fun deleteSession(sessionId: String)
}
