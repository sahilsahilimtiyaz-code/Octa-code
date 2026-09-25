package com.sahil.octacode.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sahil.octacode.core.profile.RunProfile
import com.sahil.octacode.domain.chat.ChatAuthor
import com.sahil.octacode.domain.chat.ChatSession
import com.sahil.octacode.domain.chat.StoredTurn

/**
 * The RunProfile chain flattened to columns. Room cannot store a nested
 * object, and re-deriving the chain from four columns is deliberate: a session
 * row is readable with plain SQL, so a chat can be inspected or recovered
 * without running the app.
 *
 * [workspaceId] stays nullable — a conversation may have been started before a
 * folder was chosen, and inventing a default path would claim a working
 * directory the agent never had.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val agentId: String,
    val runtimeId: String,
    val modelId: String,
    val workspaceId: String?,
    val createdAt: Long,
    val lastMessageAt: Long,
)

/**
 * Turns hang off their session with a CASCADE delete, so removing a chat
 * cannot strand history with a dangling sessionId.
 *
 * The composite index does double duty: its leading column satisfies the
 * foreign-key index requirement, and the trailing column answers
 * `WHERE sessionId = ? ORDER BY createdAt` in one seek.
 */
@Entity(
    tableName = "chat_turns",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["sessionId", "createdAt"])],
)
data class ChatTurnEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** [ChatAuthor.name] — stored as text so an added author cannot renumber and corrupt old rows. */
    val author: String,
    val text: String,
    val error: String?,
    val createdAt: Long,
)

// --- mappers: pure, so field loss is caught by a unit test rather than by a
// user discovering a conversation that came back with the wrong chain.

internal fun ChatSession.toEntity() = ChatSessionEntity(
    id = id,
    title = title,
    agentId = profile.agentId,
    runtimeId = profile.runtimeId,
    modelId = profile.modelId,
    workspaceId = profile.workspaceId,
    createdAt = createdAt,
    lastMessageAt = lastMessageAt,
)

internal fun ChatSessionEntity.toDomain() = ChatSession(
    id = id,
    title = title,
    profile = RunProfile(
        agentId = agentId,
        runtimeId = runtimeId,
        modelId = modelId,
        workspaceId = workspaceId,
    ),
    createdAt = createdAt,
    lastMessageAt = lastMessageAt,
)

internal fun StoredTurn.toEntity() = ChatTurnEntity(
    id = id,
    sessionId = sessionId,
    author = author.name,
    text = text,
    error = error,
    createdAt = createdAt,
)

/**
 * An unreadable author means the row was written by something we no longer
 * understand. Returning it as an agent turn would misattribute a message, so
 * refuse rather than guess.
 */
internal fun ChatTurnEntity.toDomain(): StoredTurn {
    val stored = author
    val resolved = ChatAuthor.entries.firstOrNull { it.name == stored }
        ?: throw IllegalStateException(
            "Stored turn $id has an unknown author '$stored' — refusing to guess who said it"
        )
    return StoredTurn(
        id = id,
        sessionId = sessionId,
        author = resolved,
        text = text,
        error = error,
        createdAt = createdAt,
    )
}
