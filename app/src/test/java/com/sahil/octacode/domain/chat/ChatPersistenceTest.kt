package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.profile.DefaultChain
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXED_CLOCK = 1_000L

private class StubRegistry(private val status: ProviderStatus) : CapabilityRegistry {
    private val _autonomy = MutableStateFlow(AutonomyLevel.ASK)
    override val autonomy: StateFlow<AutonomyLevel> = _autonomy
    override suspend fun providerStatus(id: ProviderId): ProviderStatus = status
    override suspend fun refreshAll(): Map<ProviderId, ProviderStatus> =
        ProviderId.entries.associateWith { status }
    override fun setAutonomy(level: AutonomyLevel) {
        _autonomy.value = level
    }
}

private class StubProvider(
    override val id: ProviderId = ProviderId.OPENAI,
    private val chunks: () -> Flow<ChatChunk>,
) : AiProvider {
    override val badge = CapabilityBadge.API
    override suspend fun validate(): ProviderStatus = ProviderStatus.Ready("ok")
    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = chunks()
}

private fun readyProvider(chunks: () -> Flow<ChatChunk>): Map<ProviderId, AiProvider> =
    mapOf(ProviderId.OPENAI to StubProvider(chunks = chunks))

private fun quickStream(): Flow<ChatChunk> = flow {
    emit(ChatChunk("Hel"))
    emit(ChatChunk("lo"))
    emit(ChatChunk("", done = true))
}

/**
 * Chat history only became real with ChatRepository — until then turns lived
 * in a StateFlow and a restart erased them. These pin the promises that make
 * Sessions settings and Recent Chats trustworthy: writes actually land, order
 * matches what was on screen, a cleared thread starts a new session instead of
 * quietly rewriting the old one, and a failed write says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPersistenceTest {

    private fun engineFor(
        repo: ChatRepository,
        scope: CoroutineScope,
        registry: CapabilityRegistry = StubRegistry(ProviderStatus.Ready("ok")),
        providers: Map<ProviderId, AiProvider> = readyProvider { quickStream() },
    ) = ChatEngine(
        registry = registry,
        providers = providers,
        repository = repo,
        clock = { FIXED_CLOCK },
        scope = scope,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.settle() {
        testScheduler.advanceUntilIdle()
        yield()
        testScheduler.advanceUntilIdle()
    }

    // --- writes land ----------------------------------------------------------

    @Test
    fun `a completed exchange is stored with the user turn first`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        settle()

        val sessionId = engine.state.value.sessionId
        assertNotNull(sessionId)

        val turns = repo.turns(sessionId!!)
        assertEquals(2, turns.size)
        assertEquals(ChatAuthor.USER, turns[0].author)
        assertEquals("hi", turns[0].text)
        assertEquals(ChatAuthor.AGENT, turns[1].author)
        assertEquals("Hello", turns[1].text)
        assertTrue(
            "stored order must match the order that was on screen",
            turns[0].createdAt < turns[1].createdAt,
        )
        assertNull("a completed write leaves no error", engine.state.value.persistenceError)
    }

    @Test
    fun `a message that could never be answered is still remembered`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(
            repo = repo,
            scope = this,
            registry = StubRegistry(ProviderStatus.MissingKey("API key not configured")),
        )
        engine.refreshProviderStatus()
        assertFalse(engine.send("hello"))
        settle()

        val sessionId = engine.state.value.sessionId
        assertNotNull("even a refused message must be persisted", sessionId)
        val turns = repo.turns(sessionId!!)
        assertEquals(1, turns.size)
        assertEquals("hello", turns[0].text)
    }

    @Test
    fun `repeated sends append to one session rather than one per message`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()

        engine.send("first")
        settle()
        val sessionId = engine.state.value.sessionId

        engine.send("second")
        settle()

        assertEquals(sessionId, engine.state.value.sessionId)
        assertEquals(1, repo.sessionCount)
        assertEquals(4, repo.turns(sessionId!!).size)
    }

    // --- session identity -----------------------------------------------------

    @Test
    fun `the session records the chain this conversation ran on`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        engine.send("hi")
        settle()

        val session = repo.session(engine.state.value.sessionId!!)!!
        assertEquals(DefaultChain.AGENT_ID, session.profile.agentId)
        assertEquals(DefaultChain.RUNTIME_ID, session.profile.runtimeId)
        assertEquals("gpt-4o-mini", session.profile.modelId)
        assertNull("no folder was chosen, so none is claimed", session.profile.workspaceId)
    }

    @Test
    fun `the session title comes from what the user actually typed`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        engine.send("  explain   this   bug  ")
        settle()

        val session = repo.session(engine.state.value.sessionId!!)!!
        assertEquals("explain this bug", session.title)
    }

    @Test
    fun `an over long message is truncated to a readable title`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        engine.send("x".repeat(200))
        settle()

        val session = repo.session(engine.state.value.sessionId!!)!!
        assertEquals(60, session.title.length)
    }

    // --- clearing is not destroying -------------------------------------------

    @Test
    fun `clearing starts a new conversation without erasing the old one`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()

        engine.send("first")
        settle()
        val firstSession = engine.state.value.sessionId

        engine.clearConversation()
        assertTrue(engine.state.value.turns.isEmpty())
        assertNull("clearing detaches, so the next send opens a new session",
            engine.state.value.sessionId)

        engine.send("second")
        settle()
        val secondSession = engine.state.value.sessionId

        assertNotEquals(firstSession, secondSession)
        assertEquals("the earlier conversation stays in Recent Chats", 2, repo.sessionCount)
        assertEquals(2, repo.turns(firstSession!!).size)
        assertEquals(2, repo.turns(secondSession!!).size)
    }

    // --- reopening -------------------------------------------------------------

    @Test
    fun `reopening a conversation restores its history`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        engine.send("hi")
        settle()
        val sessionId = engine.state.value.sessionId!!

        engine.clearConversation()
        assertTrue(engine.state.value.turns.isEmpty())

        assertTrue(engine.openSession(sessionId))
        assertEquals(2, engine.state.value.turns.size)
        assertEquals(sessionId, engine.state.value.sessionId)
        assertEquals("hi", engine.state.value.turns.first().text)
        assertEquals("Hello", engine.state.value.turns.last().text)
        assertFalse(engine.state.value.turns.any { it.streaming })
    }

    @Test
    fun `reopening a missing conversation reports it instead of showing an empty thread`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(repo, this)

        assertFalse(engine.openSession("no-such-session"))
        assertNotNull(engine.state.value.lastError)
        assertTrue(engine.state.value.turns.isEmpty())
        assertNull(engine.state.value.sessionId)
    }

    @Test
    fun `a conversation cannot be swapped out mid stream`() = runTest {
        val repo = FakeChatRepository()
        val engine = engineFor(
            repo = repo,
            scope = this,
            providers = readyProvider {
                flow {
                    emit(ChatChunk("partial"))
                    delay(10_000)
                    emit(ChatChunk("", done = true))
                }
            },
        )
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        // Let the write land and the stream begin; busy stays true throughout.
        testScheduler.advanceTimeBy(1)
        assertFalse(engine.openSession("whatever"))
        engine.stop()
        settle()
    }

    // --- failure is visible ---------------------------------------------------

    @Test
    fun `a failed write is reported rather than passing for saved`() = runTest {
        val repo = FakeChatRepository().apply { failWritesWith = "disk full" }
        val engine = engineFor(repo, this)
        engine.refreshProviderStatus()
        engine.send("hi")
        settle()

        val failure = engine.state.value.persistenceError
        assertNotNull("silent data loss is not acceptable", failure)
        assertTrue(failure!!.contains("disk full"))
        assertEquals(0, repo.sessionCount)
    }

    @Test
    fun `no repository means no session id, so nothing implies it was saved`() = runTest {
        val engine = ChatEngine(
            registry = StubRegistry(ProviderStatus.Ready("ok")),
            providers = readyProvider { quickStream() },
            repository = null,
            scope = this,
        )
        engine.refreshProviderStatus()
        engine.send("hi")
        settle()

        assertNull(engine.state.value.sessionId)
        assertNull(engine.state.value.persistenceError)
    }
}
