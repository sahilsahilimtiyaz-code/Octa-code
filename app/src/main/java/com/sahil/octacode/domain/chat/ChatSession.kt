package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.profile.RunProfile

/**
 * A persisted conversation.
 *
 * Carrying [profile] is the point: a session is not "a list of messages" but
 * one instance of the Agent → Runtime → Model → Workspace chain. Reopening a
 * session therefore restores *where it ran and what it ran on*, not just its
 * text — which is what makes the drawer's Recent Chats meaningful rather than
 * a list of orphaned transcripts.
 */
data class ChatSession(
    val id: String,
    val title: String,
    val profile: RunProfile,
    val createdAt: Long,
    val lastMessageAt: Long,
)

/**
 * One turn as stored. Separate from [ChatTurn], which is the live rendering
 * state (`streaming`, partial text): history does not need those, and mixing
 * them would mean persisting "streaming=true" if the app died mid-response —
 * a state that is no longer true when the app comes back.
 *
 * [error] is kept because a failed turn must still be recognisably failed
 * after a restart; dropping it would rewrite a failure as a blank message.
 */
data class StoredTurn(
    val id: String,
    val sessionId: String,
    val author: ChatAuthor,
    val text: String,
    val error: String? = null,
    val createdAt: Long,
)
