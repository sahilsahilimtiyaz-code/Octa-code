package com.sahil.octacode.data.chat

import com.sahil.octacode.core.profile.RunProfile
import com.sahil.octacode.domain.chat.ChatRepository
import com.sahil.octacode.domain.chat.ChatSession
import com.sahil.octacode.domain.chat.StoredTurn
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [ChatRepository].
 *
 * Thin on purpose: every decision that could be wrong lives in the pure
 * mappers declared beside the entities, or in the DAO's single transactional
 * write path — both testable without an Android device. This class only
 * transports.
 */
class RoomChatRepository(
    private val dao: ChatDao,
) : ChatRepository {

    override val sessions: Flow<List<ChatSession>> =
        dao.observeSessions().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createSession(
        profile: RunProfile,
        title: String,
        now: Long,
    ): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            profile = profile,
            createdAt = now,
            // Stamp creation so an empty chat still sorts correctly among
            // recent ones instead of appearing older than everything.
            lastMessageAt = now,
        )
        dao.upsertSession(session.toEntity())
        return session
    }

    override suspend fun session(id: String): ChatSession? = dao.session(id)?.toDomain()

    override suspend fun turns(sessionId: String): List<StoredTurn> =
        dao.turns(sessionId).map { it.toDomain() }

    override suspend fun append(turn: StoredTurn) {
        dao.append(turn.toEntity())
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        dao.renameSession(sessionId, title)
    }

    override suspend fun setArchived(sessionId: String, at: Long?) {
        dao.setArchived(sessionId, at)
    }

    override suspend fun setArchived(sessionIds: Collection<String>, at: Long) {
        if (sessionIds.isEmpty()) return
        dao.setArchivedMany(sessionIds, at)
    }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }
}
