package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.settings.Settings
import kotlinx.coroutines.flow.first

/**
 * Which conversations should leave the active list, and why.
 *
 * This is the logic behind four settings that would otherwise be decorative:
 * auto-archive, archive-after, limit-active-chats and max-active-chats. It is
 * deliberately a pure object over `List<ChatSession>` and `Settings` — no
 * repository, no clock, no Context — so every rule below can be pinned by a
 * test with hand-built timestamps instead of judged by watching the app.
 *
 * Both rules are opt-in and both skip already-archived sessions: a policy that
 * silently archived on its own, or re-archived every launch, would be moving
 * the user's conversations without being asked.
 */
object SessionRetention {

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Active sessions that have been idle longer than the configured window.
     *
     * Strictly less than the cutoff: a session that was last used exactly
     * [Settings.archiveAfterDays] days ago is still within the window it was
     * promised, and archiving it would cut the window short by a millisecond.
     */
    fun stale(
        sessions: List<ChatSession>,
        settings: Settings,
        now: Long,
    ): List<ChatSession> {
        if (!settings.autoArchive) return emptyList()
        val cutoff = now - settings.archiveAfterDays * MILLIS_PER_DAY
        return sessions.filter { session ->
            session.isActive && session.lastMessageAt < cutoff
        }
    }

    /**
     * Active sessions beyond the cap — the oldest ones that have to go to get
     * back under it.
     *
     * Newest are kept: [ChatSession.lastMessageAt] is the best available
     * signal for "still in use", and dropping the conversation you had open
     * yesterday to keep one from last month would be the wrong trade.
     */
    fun overLimit(
        sessions: List<ChatSession>,
        settings: Settings,
    ): List<ChatSession> {
        if (!settings.limitActiveChats) return emptyList()
        val active = sessions.filter { it.isActive }
        if (active.size <= settings.maxActiveChats) return emptyList()
        return active.sortedBy { it.lastMessageAt }.dropLast(settings.maxActiveChats)
    }

    /**
     * Runs both rules and archives whatever they flag.
     *
     * Returned so the caller can report what actually happened — archiving
     * silently would be the app moving conversations without saying so, which
     * is the failure mode this whole class exists to avoid.
     */
    suspend fun apply(
        repo: ChatRepository,
        settings: Settings,
        now: Long,
    ): List<ChatSession> {
        val sessions = repo.sessions.first()
        val targets = (stale(sessions, settings, now) + overLimit(sessions, settings))
            .distinctBy { it.id }
        if (targets.isNotEmpty()) {
            repo.setArchived(targets.map { it.id }, now)
        }
        return targets
    }
}
