package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.profile.RunProfile
import com.sahil.octacode.core.settings.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L
private const val NOW = 1_700_000_000_000L

/**
 * These four settings were listed as required but had nothing to act on —
 * before chat history existed there was no session to archive and nothing to
 * count against a limit. Now there is, and the rules are pinned here so a
 * later tweak cannot quietly start archiving conversations the user did not
 * expect to lose.
 */
class SessionRetentionTest {

    private val profile = RunProfile(agentId = "octa", runtimeId = "local", modelId = "m")

    private fun session(
        id: String,
        lastMessageAt: Long,
        archivedAt: Long? = null,
    ) = ChatSession(
        id = id,
        title = id,
        profile = profile,
        createdAt = lastMessageAt - DAY,
        lastMessageAt = lastMessageAt,
        archivedAt = archivedAt,
    )

    private fun settings(
        autoArchive: Boolean = false,
        archiveAfterDays: Int = 7,
        limitActiveChats: Boolean = false,
        maxActiveChats: Int = 5,
    ) = Settings(
        autoArchive = autoArchive,
        archiveAfterDays = archiveAfterDays,
        limitActiveChats = limitActiveChats,
        maxActiveChats = maxActiveChats,
    ).sanitized()

    // --- auto archive ---------------------------------------------------------

    @Test
    fun `auto archive off archives nothing however idle the sessions are`() {
        val sessions = listOf(
            session("old", lastMessageAt = NOW - 400 * DAY),
            session("ancient", lastMessageAt = NOW - 4_000 * DAY),
        )

        assertTrue(SessionRetention.stale(sessions, settings(autoArchive = false), NOW).isEmpty())
    }

    @Test
    fun `a session idle past the configured window is stale`() {
        val s = settings(autoArchive = true, archiveAfterDays = 7)

        val stale = SessionRetention.stale(
            listOf(session("idle", lastMessageAt = NOW - 8 * DAY)),
            s,
            NOW,
        )

        assertEquals(listOf("idle"), stale.map { it.id })
    }

    @Test
    fun `a session inside the window stays active`() {
        val s = settings(autoArchive = true, archiveAfterDays = 7)

        val stale = SessionRetention.stale(
            listOf(session("fresh", lastMessageAt = NOW - 6 * DAY)),
            s,
            NOW,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a session at exactly the cutoff is not archived - the window is honoured in full`() {
        val s = settings(autoArchive = true, archiveAfterDays = 7)

        val stale = SessionRetention.stale(
            listOf(session("boundary", lastMessageAt = NOW - 7 * DAY)),
            s,
            NOW,
        )

        assertTrue("archiving at the boundary cuts the promised window short", stale.isEmpty())
    }

    @Test
    fun `one millisecond past the cutoff is enough`() {
        val s = settings(autoArchive = true, archiveAfterDays = 7)

        val stale = SessionRetention.stale(
            listOf(session("just-over", lastMessageAt = NOW - 7 * DAY - 1)),
            s,
            NOW,
        )

        assertEquals(1, stale.size)
    }

    @Test
    fun `an archived session is never flagged again`() {
        val s = settings(autoArchive = true, archiveAfterDays = 7)

        val stale = SessionRetention.stale(
            listOf(session("already", lastMessageAt = NOW - 999 * DAY, archivedAt = NOW - DAY)),
            s,
            NOW,
        )

        assertTrue("re-archiving would keep rewriting archivedAt forever", stale.isEmpty())
    }

    // --- active chat limit ----------------------------------------------------

    @Test
    fun `the limit does nothing until it is switched on`() {
        val sessions = (1..50).map { session("s$it", lastMessageAt = NOW - it * DAY) }

        val over = SessionRetention.overLimit(sessions, settings(limitActiveChats = false))

        assertTrue(over.isEmpty())
    }

    @Test
    fun `being at the limit is still within the limit`() {
        val sessions = (1..5).map { session("s$it", lastMessageAt = NOW - it * DAY) }

        val over = SessionRetention.overLimit(
            sessions,
            settings(limitActiveChats = true, maxActiveChats = 5),
        )

        assertTrue(over.isEmpty())
    }

    @Test
    fun `over the limit the oldest are archived and the newest kept`() {
        val sessions = (1..7).map { session("s$it", lastMessageAt = NOW - it * DAY) }

        val over = SessionRetention.overLimit(
            sessions,
            settings(limitActiveChats = true, maxActiveChats = 5),
        )

        assertEquals(
            "s7 is the oldest of seven against a cap of five",
            listOf("s7", "s6"),
            over.map { it.id },
        )
    }

    @Test
    fun `already archived sessions do not count against the active limit`() {
        val active = (1..5).map { session("a$it", lastMessageAt = NOW - it * DAY) }
        val archived = (1..40).map {
            session("h$it", lastMessageAt = NOW - 100 * DAY, archivedAt = NOW - DAY)
        }

        val over = SessionRetention.overLimit(
            active + archived,
            settings(limitActiveChats = true, maxActiveChats = 5),
        )

        assertTrue("40 archived sessions must not consume active slots", over.isEmpty())
    }

    @Test
    fun `archived sessions are excluded from both rules`() {
        val s = settings(
            autoArchive = true,
            archiveAfterDays = 7,
            limitActiveChats = true,
            maxActiveChats = 1,
        )
        val sessions = listOf(
            // 900 days idle — far past the 7-day window — but already archived.
            session("gone", lastMessageAt = NOW - 900 * DAY, archivedAt = NOW - DAY),
            session("live1", lastMessageAt = NOW - 30 * DAY),
            session("live2", lastMessageAt = NOW - 40 * DAY),
        )

        val stale = SessionRetention.stale(sessions, s, NOW)
        val overLimit = SessionRetention.overLimit(sessions, s)

        assertEquals("the stale rule must not touch an archived session",
            listOf("live1", "live2"), stale.map { it.id })
        assertTrue("re-flagging would rewrite archivedAt on every pass",
            stale.none { it.id == "gone" })
        assertEquals("the limit must not count archived sessions as active",
            listOf("live2"), overLimit.map { it.id })
        assertTrue(overLimit.none { it.id == "gone" })
    }

    // --- apply ----------------------------------------------------------------

    @Test
    fun `apply archives what the rules flag and reports it`() = runTest {
        val repo = FakeChatRepository()
        repo.seed(session("idle", lastMessageAt = NOW - 30 * DAY))
        repo.seed(session("busy", lastMessageAt = NOW))

        val applied = SessionRetention.apply(
            repo,
            settings(autoArchive = true, archiveAfterDays = 7),
            NOW,
        )

        assertEquals(listOf("idle"), applied.map { it.id })
        assertEquals("archiving records when it happened", NOW, repo.session("idle")!!.archivedAt)
        assertEquals(null, repo.session("busy")!!.archivedAt)
    }

    @Test
    fun `apply touches nothing when no session qualifies`() = runTest {
        val repo = FakeChatRepository()
        repo.seed(session("busy", lastMessageAt = NOW))

        val applied = SessionRetention.apply(
            repo,
            settings(autoArchive = true, archiveAfterDays = 7),
            NOW,
        )

        assertTrue(applied.isEmpty())
        assertEquals(null, repo.session("busy")!!.archivedAt)
    }

    @Test
    fun `an archived session is left exactly as it was on the next pass`() = runTest {
        val repo = FakeChatRepository()
        val archivedAt = NOW - DAY
        repo.seed(session("gone", lastMessageAt = NOW - 900 * DAY, archivedAt = archivedAt))

        val applied = SessionRetention.apply(
            repo,
            settings(autoArchive = true, archiveAfterDays = 7),
            NOW,
        )

        assertTrue(applied.isEmpty())
        assertEquals("archivedAt must not be rewritten each launch",
            archivedAt, repo.session("gone")!!.archivedAt)
    }

    @Test
    fun `a session flagged by both rules is archived exactly once`() = runTest {
        val repo = FakeChatRepository()
        // All three are stale, and three active against a cap of one means the
        // limit rule flags two of them as well.
        repo.seed(session("a", lastMessageAt = NOW - 30 * DAY))
        repo.seed(session("b", lastMessageAt = NOW - 40 * DAY))
        repo.seed(session("c", lastMessageAt = NOW - 50 * DAY))

        val applied = SessionRetention.apply(
            repo,
            settings(
                autoArchive = true,
                archiveAfterDays = 7,
                limitActiveChats = true,
                maxActiveChats = 1,
            ),
            NOW,
        )

        assertEquals("the union must be de-duplicated by session id",
            3, applied.size)
        applied.forEach { flagged ->
            assertEquals(NOW, repo.session(flagged.id)!!.archivedAt)
        }
    }
}
