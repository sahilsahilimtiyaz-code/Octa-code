package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.model.ModelCatalog
import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.model.ModelUserState
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.provider.defaultModelFor
import com.sahil.octacode.domain.model.ModelUserStateRepository
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
import org.junit.Assert.assertNotNull
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
    scope: CoroutineScope,
    modelState: ModelUserStateRepository? = null,
    clock: () -> Long = { System.currentTimeMillis() }
): ChatEngine = ChatEngine(
    registry = registry,
    providers = providers,
    modelState = modelState,
    clock = clock,
    scope = scope
)

/**
 * Captures the request the engine actually builds, so "what goes on the wire"
 * can be asserted rather than inferred from state.
 *
 * [streamDelayMs] holds the response open, which is how a test gets the engine
 * into `busy` without a real network round trip.
 */
private class RecordingProvider(
    override val id: ProviderId = ProviderId.OPENAI,
    private val streamDelayMs: Long = 0L,
) : AiProvider {
    val requests = mutableListOf<ChatRequest>()
    override val badge = CapabilityBadge.API
    override suspend fun validate(): ProviderStatus = ProviderStatus.Ready("recording")
    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        requests += request
        return flow {
            emit(ChatChunk("ok"))
            if (streamDelayMs > 0) delay(streamDelayMs)
            emit(ChatChunk("", done = true))
        }
    }
}

private class FakeModelUserStateRepository : ModelUserStateRepository {
    private val _states = MutableStateFlow<Map<String, ModelUserState>>(emptyMap())
    override val states: StateFlow<Map<String, ModelUserState>> = _states

    /** Every (model, at) pair that was marked used, in order. */
    val used = mutableListOf<Pair<String, Long>>()

    override fun setFavorite(modelId: String, favorite: Boolean) {
        val current = _states.value[modelId] ?: ModelUserState(modelId = modelId)
        _states.value = _states.value + (modelId to current.copy(isFavorite = favorite))
    }

    override fun markUsed(modelId: String, at: Long) {
        val current = _states.value[modelId] ?: ModelUserState(modelId = modelId)
        _states.value = _states.value + (modelId to current.copy(lastUsedAt = at))
        used += modelId to at
    }
}

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

    // --- model selection ----------------------------------------------------

    @Test
    fun `with nothing picked the provider default goes on the wire`() = runTest {
        val provider = RecordingProvider()
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceUntilIdle()
        assertEquals(defaultModelFor(ProviderId.OPENAI), provider.requests.single().model)
    }

    @Test
    fun `picking a model puts that model on the wire`() = runTest {
        val provider = RecordingProvider()
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        // The pick re-probes, as the chat screen does after every selection.
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceUntilIdle()
        assertEquals("gpt-4.1", provider.requests.single().model)
    }

    @Test
    fun `picking a model drops the probe so a stale green light is not reused`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.refreshProviderStatus()
        assertNotNull(engine.state.value.providerStatus)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        assertEquals(ProviderId.OPENAI, engine.state.value.selectedProvider)
        assertNull(engine.state.value.providerStatus)
    }

    @Test
    fun `a model outside the catalog is refused`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.refreshProviderStatus()
        engine.selectModel(
            ModelDef(
                id = "not-in-the-catalog",
                displayName = "Impostor",
                provider = "Nowhere",
                adapter = ProviderId.CUSTOM
            )
        )
        assertNull(engine.state.value.selectedModelId)
        assertNotNull(engine.state.value.providerStatus)
    }

    @Test
    fun `switching provider drops a model that does not belong there`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        engine.selectProvider(ProviderId.CUSTOM)
        assertNull(engine.state.value.selectedModelId)
    }

    @Test
    fun `switching provider keeps a model that does belong there`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        engine.selectProvider(ProviderId.OPENAI)
        assertEquals("gpt-4.1", engine.state.value.selectedModelId)
    }

    @Test
    fun `clear conversation keeps the picked model`() = runTest {
        val engine = chatEngine(readyRegistry(), emptyMap(), this)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        engine.clearConversation()
        assertEquals("gpt-4.1", engine.state.value.selectedModelId)
        assertEquals(ProviderId.OPENAI, engine.state.value.selectedProvider)
    }

    @Test
    fun `sending records the model as used`() = runTest {
        val modelState = FakeModelUserStateRepository()
        val provider = RecordingProvider()
        val engine = chatEngine(
            registry = readyRegistry(),
            providers = mapOf(ProviderId.OPENAI to provider),
            scope = this,
            modelState = modelState,
            clock = { 1_700_000_000_000L }
        )
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        engine.refreshProviderStatus()
        assertTrue(engine.send("hi"))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("gpt-4.1" to 1_700_000_000_000L), modelState.used)
    }

    @Test
    fun `picking a model does not record it as used`() = runTest {
        val modelState = FakeModelUserStateRepository()
        val engine = chatEngine(readyRegistry(), emptyMap(), this, modelState = modelState)
        engine.selectModel(ModelCatalog.byId("gpt-4.1")!!)
        assertTrue(modelState.used.isEmpty())
        assertTrue(modelState.states.value.isEmpty())
    }

    // --- queue while streaming ------------------------------------------------

    @Test
    fun `queue mode holds a message typed during a stream instead of refusing it`() = runTest {
        val provider = RecordingProvider(streamDelayMs = 5_000)
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.refreshProviderStatus()
        assertTrue(engine.send("first"))
        assertTrue(engine.state.value.busy)
        // The stream runs on the test scheduler, not inline with send(): until
        // time advances, nothing has reached the provider yet.
        testScheduler.advanceTimeBy(1)
        yield()

        assertTrue(engine.send("second", queueIfBusy = true))
        assertEquals("second", engine.state.value.queuedText)

        // Held, not started: a second request in flight would interleave two
        // responses into the one turn list on screen.
        assertEquals(1, provider.requests.size)
        assertEquals(
            1,
            engine.state.value.turns.count { it.author == ChatAuthor.USER }
        )

        engine.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `without queue mode a send during a stream is still refused`() = runTest {
        val provider = RecordingProvider(streamDelayMs = 5_000)
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.refreshProviderStatus()
        assertTrue(engine.send("first"))
        testScheduler.advanceTimeBy(1)
        yield()

        assertFalse(engine.send("second"))
        assertNull(engine.state.value.queuedText)
        assertEquals(1, provider.requests.size)

        engine.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `queued message goes out once the stream finishes`() = runTest {
        val provider = RecordingProvider(streamDelayMs = 5_000)
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.refreshProviderStatus()
        assertTrue(engine.send("first"))
        testScheduler.advanceTimeBy(1)
        yield()
        assertEquals(1, provider.requests.size)

        assertTrue(engine.send("second", queueIfBusy = true))
        // Queued but not issued — still exactly one request on the wire.
        assertEquals(1, provider.requests.size)

        testScheduler.advanceUntilIdle()

        assertEquals(2, provider.requests.size)
        assertNull(engine.state.value.queuedText)
        assertFalse(engine.state.value.busy)
        assertEquals(
            listOf("first", "second"),
            engine.state.value.turns
                .filter { it.author == ChatAuthor.USER }
                .map { it.text }
        )
        // The queued text went out as a real turn, not merely as a side effect:
        // it is on screen in the order it was typed.
        assertEquals(
            "second",
            provider.requests.last().messages.last().content
        )
    }

    @Test
    fun `queue mode does not delay a send when nothing is streaming`() = runTest {
        val provider = RecordingProvider()
        val engine = chatEngine(readyRegistry(), mapOf(ProviderId.OPENAI to provider), this)
        engine.refreshProviderStatus()

        assertTrue(engine.send("first", queueIfBusy = true))

        // Nothing is streaming, so queue mode must not defer anything: the turn
        // is taken straight away with no queued copy held back.
        assertNull(engine.state.value.queuedText)
        assertEquals(
            listOf("first"),
            engine.state.value.turns
                .filter { it.author == ChatAuthor.USER }
                .map { it.text }
        )

        testScheduler.advanceUntilIdle()
        assertEquals(1, provider.requests.size)
    }
}
