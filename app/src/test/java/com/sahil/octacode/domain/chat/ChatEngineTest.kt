package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRegistry(
    private val status: ProviderStatus
) : CapabilityRegistry {
    private val _autonomy = MutableStateFlow(AutonomyLevel.ASK)
    override val autonomy: StateFlow<AutonomyLevel> = _autonomy
    override suspend fun providerStatus(id: ProviderId): ProviderStatus = status
    override suspend fun refreshAll(): Map<ProviderId, ProviderStatus> =
        ProviderId.entries.associateWith { status }
    override fun setAutonomy(level: AutonomyLevel) {
        _autonomy.value = level
    }
}

private class LambdaProvider(
    override val id: ProviderId = ProviderId.OPENAI,
    private val onStream: () -> Flow<ChatChunk>
) : AiProvider {
    override val badge = CapabilityBadge.API
    override suspend fun validate(): ProviderStatus = ProviderStatus.Ready("lambda")
    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = onStream()
}

private fun readyRegistry() = FakeRegistry(ProviderStatus.Ready("ok"))
private fun missingKeyRegistry() = FakeRegistry(ProviderStatus.MissingKey("API key not configured"))

private fun chatEngine(
    registry: CapabilityRegistry,
    providers: Map<ProviderId, AiProvider>,
    scope: CoroutineScope
): ChatEngine = ChatEngine(registry = registry, providers = providers, scope = scope)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatEngineTest {

    @Test
    fun `send blocks when provider not ready and does not create assistant turn`() = runTest {
        val engine = chatEngine(
            registry = missingKeyRegistry(),
            providers = mapOf(ProviderId.OPENAI to LambdaProvider { flow { emit(ChatChunk("hi")) } }),
            scope = this
        )
        engine.refreshProviderStatus()
        val accepted = engine.send("hello")
        assertFalse(accepted)
        val state = engine.state.value
        assertEquals(1, state.turns.size)
        assertEquals(ChatAuthor.USER, state.turns.first().author)
        assertTrue(state.lastError!!.contains("Unavailable"))
        assertFalse(state.busy)
    }

    @Test
    fun `send accumulates stream deltas and finalizes on done`() = runTest {
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = mapOf(
                ProviderId.OPENAI to LambdaProvider {
                    flow {
                        emit(ChatChunk("Hel"))
                        emit(ChatChunk("lo"))
                        emit(ChatChunk("", done = true))
                    }
                }
            ),
            scope = this
        )
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceUntilIdle()
        yield()
        testScheduler.advanceUntilIdle()

        val state = engine.state.value
        assertFalse(state.busy)
        assertEquals(2, state.turns.size)
        assertEquals("Hello", state.turns[1].text)
        assertFalse(state.turns[1].streaming)
        assertNull(state.lastError)
    }

    @Test
    fun `stream error surfaces honest failure without fake success text`() = runTest {
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = mapOf(
                ProviderId.OPENAI to LambdaProvider {
                    flow {
                        emit(ChatChunk("partial"))
                        throw IllegalStateException("OpenAI API key not configured")
                    }
                }
            ),
            scope = this
        )
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceUntilIdle()
        yield()
        testScheduler.advanceUntilIdle()

        val state = engine.state.value
        assertFalse(state.busy)
        assertTrue(
            state.lastError!!.contains("not configured") || state.lastError!!.contains("key")
        )
        val agent = state.turns.last()
        assertEquals(ChatAuthor.AGENT, agent.author)
        assertEquals("partial", agent.text)
        assertEquals("OpenAI API key not configured", agent.error)
    }

    @Test
    fun `missing adapter blocks with unavailable reason`() = runTest {
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = emptyMap(),
            scope = this
        )
        engine.refreshProviderStatus()
        val accepted = engine.send("hi")
        assertFalse(accepted)
        assertTrue(engine.state.value.lastError!!.contains("no adapter"))
        assertEquals(1, engine.state.value.turns.size)
    }

    @Test
    fun `stop cancels stream and keeps partial text labeled stopped`() = runTest {
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = mapOf(
                ProviderId.OPENAI to LambdaProvider {
                    flow {
                        emit(ChatChunk("abc"))
                        delay(10_000)
                        emit(ChatChunk("def"))
                        emit(ChatChunk("", done = true))
                    }
                }
            ),
            scope = this
        )
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceTimeBy(1)
        yield()
        engine.stop()
        testScheduler.advanceUntilIdle()
        yield()
        testScheduler.advanceUntilIdle()

        val state = engine.state.value
        assertFalse(state.busy)
        val agent = state.turns.last()
        assertEquals("abc", agent.text)
        assertEquals("stopped", agent.error)
    }

    @Test
    fun `empty draft is rejected`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        assertFalse(engine.send("   "))
        assertTrue(engine.state.value.turns.isEmpty())
    }

    @Test
    fun `select provider ignored while busy`() = runTest {
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = mapOf(
                ProviderId.OPENAI to LambdaProvider {
                    flow {
                        emit(ChatChunk("x"))
                        delay(5_000)
                        emit(ChatChunk("", done = true))
                    }
                }
            ),
            scope = this
        )
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        engine.selectProvider(ProviderId.CUSTOM)
        assertEquals(ProviderId.OPENAI, engine.state.value.selectedProvider)
        engine.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `clear conversation resets turns but keeps provider`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.send("blocked")
        engine.clearConversation()
        assertTrue(engine.state.value.turns.isEmpty())
        assertEquals(ProviderId.OPENAI, engine.state.value.selectedProvider)
    }
}
