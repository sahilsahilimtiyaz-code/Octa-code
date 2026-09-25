package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.profile.RunProfile
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [ChatRepository] mirroring the Room contract, so engine tests
 * exercise the same behaviour production has: sessions ordered most-recently
 * active first, turns in chronological order, and deleting a session deleting
 * its turns.
 *
 * Not a "does nothing" stub — a test that passes here would pass against the
 * real database's semantics.
 */
internal class FakeChatRepository : ChatRepository {

    private val sessionOrder = mutableListOf<String>()
    private val sessionById = mutableMapOf<String, ChatSession>()
    private val turnsById = linkedMapOf<String, MutableList<StoredTurn>>()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    override val sessions: Flow<List<ChatSession>> = _sessions.asStateFlow()

    /** When set, every write throws this message — so failure paths are testable. */
    var failWritesWith: String? = null

    /** Sessions currently held, for asserting on lifecycle rather than ordering. */
    val sessionCount: Int
        get() = sessionById.size

    override suspend fun createSession(
        profile: RunProfile,
        title: String,
        now: Long,
    ): ChatSession {
        failWritesWith?.let { throw IllegalStateException(it) }
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            profile = profile,
            createdAt = now,
            lastMessageAt = now,
        )
        sessionById[session.id] = session
        sessionOrder += session.id
        turnsById[session.id] = mutableListOf()
        publish()
        return session
    }

    override suspend fun session(id: String): ChatSession? = sessionById[id]

    override suspend fun turns(sessionId: String): List<StoredTurn> =
        turnsById[sessionId].orEmpty().toList()

    override suspend fun append(turn: StoredTurn) {
        failWritesWith?.let { throw IllegalStateException(it) }
        val existing = turnsById[turn.sessionId]
            ?: throw IllegalStateException("No session ${turn.sessionId} to append to")
        existing += turn
        val session = sessionById.getValue(turn.sessionId)
        sessionById[turn.sessionId] = session.copy(lastMessageAt = turn.createdAt)
        publish()
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        failWritesWith?.let { throw IllegalStateException(it) }
        val session = sessionById[sessionId] ?: return
        sessionById[sessionId] = session.copy(title = title)
        publish()
    }

    override suspend fun setArchived(sessionId: String, at: Long?) {
        failWritesWith?.let { throw IllegalStateException(it) }
        val session = sessionById[sessionId] ?: return
        sessionById[sessionId] = session.copy(archivedAt = at)
        publish()
    }

    override suspend fun setArchived(sessionIds: Collection<String>, at: Long) {
        if (sessionIds.isEmpty()) return
        failWritesWith?.let { throw IllegalStateException(it) }
        sessionIds.forEach { id ->
            sessionById[id]?.let { sessionById[id] = it.copy(archivedAt = at) }
        }
        publish()
    }

    override suspend fun deleteSession(sessionId: String) {
        failWritesWith?.let { throw IllegalStateException(it) }
        sessionById.remove(sessionId)
        turnsById.remove(sessionId)
        sessionOrder.remove(sessionId)
        publish()
    }

    /** Reproduces `ORDER BY lastMessageAt DESC`. */
    private fun publish() {
        _sessions.value = sessionOrder
            .mapNotNull { sessionById[it] }
            .sortedByDescending { it.lastMessageAt }
    }

    /**
     * Insert a fully-specified session. Policy tests need control over
     * `lastMessageAt` and `archivedAt`, which the public API generates.
     */
    suspend fun seed(session: ChatSession) {
        failWritesWith?.let { throw IllegalStateException(it) }
        sessionById[session.id] = session
        if (session.id !in sessionOrder) sessionOrder += session.id
        turnsById.putIfAbsent(session.id, mutableListOf())
        publish()
    }
}
