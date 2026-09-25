package com.sahil.octacode.data.chat

import com.sahil.octacode.core.profile.RunProfile
import com.sahil.octacode.domain.chat.ChatAuthor
import com.sahil.octacode.domain.chat.ChatSession
import com.sahil.octacode.domain.chat.StoredTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Room cannot be exercised on the JVM here (no Robolectric), so the mapping
 * layer carries the risk: a field dropped between domain and table shows up
 * only when someone reopens a conversation. These pin the round trip, the
 * RunProfile chain in particular — a session that comes back pointing at the
 * wrong runtime or model is worse than one that comes back empty.
 */
class ChatMappersTest {

    private val profile = RunProfile(
        agentId = "opencode",
        runtimeId = "local",
        modelId = "mimo-v2.6-flash",
        workspaceId = "ws-42",
    )

    // --- session / chain ------------------------------------------------------

    @Test
    fun `a session round trips with its entire chain intact`() {
        val session = ChatSession(
            id = "s-1",
            title = "Fix the parser",
            profile = profile,
            createdAt = 1_000L,
            lastMessageAt = 9_000L,
        )

        assertEquals(session, session.toEntity().toDomain())
    }

    @Test
    fun `a session with no workspace keeps it null rather than inventing a path`() {
        val session = ChatSession(
            id = "s-2",
            title = "Untitled",
            profile = profile.copy(workspaceId = null),
            createdAt = 5L,
            lastMessageAt = 5L,
        )

        val restored = session.toEntity().toDomain()
        assertNull(restored.profile.workspaceId)
        assertEquals(session, restored)
    }

    @Test
    fun `every chain axis survives independently`() {
        val entity = ChatSession(
            id = "s-3",
            title = "t",
            profile = profile,
            createdAt = 1L,
            lastMessageAt = 2L,
        ).toEntity()

        assertEquals("opencode", entity.agentId)
        assertEquals("local", entity.runtimeId)
        assertEquals("mimo-v2.6-flash", entity.modelId)
        assertEquals("ws-42", entity.workspaceId)
    }

    @Test
    fun `activity stamp and creation time are not swapped on the way back`() {
        val session = ChatSession(
            id = "s-4",
            title = "t",
            profile = profile,
            createdAt = 1_111L,
            lastMessageAt = 9_999L,
        ).toEntity().toDomain()

        assertEquals(1_111L, session.createdAt)
        assertEquals(9_999L, session.lastMessageAt)
    }

    @Test
    fun `archivedAt survives the round trip so a retention decision is not lost`() {
        val session = ChatSession(
            id = "s-5",
            title = "t",
            profile = profile,
            createdAt = 1L,
            lastMessageAt = 2L,
            archivedAt = 4_242L,
        )

        val restored = session.toEntity().toDomain()

        assertEquals(4_242L, restored.archivedAt)
        assertFalse("an archived session must not report as active", restored.isActive)
        assertEquals(session, restored)
    }

    @Test
    fun `a session with no archivedAt round trips as null rather than a default timestamp`() {
        val restored = ChatSession(
            id = "s-6",
            title = "t",
            profile = profile,
            createdAt = 1L,
            lastMessageAt = 2L,
        ).toEntity().toDomain()

        assertNull(restored.archivedAt)
        assertTrue(restored.isActive)
    }

    // --- turns ----------------------------------------------------------------

    @Test
    fun `a turn round trips with author error and timestamps`() {
        val turn = StoredTurn(
            id = "t-1",
            sessionId = "s-1",
            author = ChatAuthor.AGENT,
            text = "— request failed —",
            error = "HTTP 401: invalid_api_key",
            createdAt = 4_242L,
        )

        assertEquals(turn, turn.toEntity().toDomain())
    }

    @Test
    fun `a successful turn keeps a null error rather than an empty one`() {
        val turn = StoredTurn(
            id = "t-2",
            sessionId = "s-1",
            author = ChatAuthor.USER,
            text = "hello",
            error = null,
            createdAt = 7L,
        )

        val restored = turn.toEntity().toDomain()
        assertNull(restored.error)
        assertEquals(turn, restored)
    }

    @Test
    fun `both authors survive so history does not invert who spoke`() {
        ChatAuthor.entries.forEach { author ->
            val turn = StoredTurn(
                id = "t-$author",
                sessionId = "s-1",
                author = author,
                text = "x",
                createdAt = 1L,
            )
            assertEquals(author, turn.toEntity().toDomain().author)
        }
    }

    @Test
    fun `an unknown author is refused rather than attributed to someone`() {
        val row = ChatTurnEntity(
            id = "t-x",
            sessionId = "s-1",
            author = "SYSTEM",
            text = "trust me",
            error = null,
            createdAt = 1L,
        )

        try {
            row.toDomain()
            fail("an unrecognised author must not be silently attributed")
        } catch (expected: IllegalStateException) {
            assertEquals(
                "Stored turn t-x has an unknown author 'SYSTEM' — refusing to guess who said it",
                expected.message,
            )
        }
    }

    @Test
    fun `author is stored as its name so adding an author cannot renumber old rows`() {
        val stored = StoredTurn(
            id = "t-9",
            sessionId = "s-1",
            author = ChatAuthor.AGENT,
            text = "x",
            createdAt = 1L,
        ).toEntity()

        assertEquals("AGENT", stored.author)
    }
}
