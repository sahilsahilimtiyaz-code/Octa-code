package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.model.ModelCatalog
import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.provider.defaultModelFor
import com.sahil.octacode.core.profile.DefaultChain
import com.sahil.octacode.data.providers.ProviderHttpException
import com.sahil.octacode.domain.model.ModelUserStateRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ChatAuthor { USER, AGENT }

/** One conversation turn (domain — mapped to UI models at the screen). */
data class ChatTurn(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val streaming: Boolean = false,
    val error: String? = null,
    /**
     * Assigned when the turn is created, not when it happens to reach disk.
     * Persisting on coroutine scheduling order would let a late-finishing
     * write sort a message above one the user sent after it; stamping here
     * means stored order always equals the order that was on screen.
     */
    val createdAt: Long = 0L
)

/**
 * M4 chat engine — streams from real M2 adapters.
 * Ready-gate before send; never fabricates assistant text.
 */
data class ChatEngineState(
    val turns: List<ChatTurn> = emptyList(),
    val busy: Boolean = false,
    val streamingText: String? = null,
    val lastError: String? = null,
    val selectedProvider: ProviderId = ProviderId.OPENAI,
    /**
     * Catalog entry to send with, or null to use the default for
     * [selectedProvider].
     *
     * Held as an id rather than a whole `ModelDef` because an id is all that
     * goes on the wire — and resolving it against `ModelCatalog` at send time
     * is what stops a model being sent through an adapter that does not
     * serve it.
     */
    val selectedModelId: String? = null,
    val providerStatus: ProviderStatus? = null,
    /**
     * Session backing the current conversation, or null when history is not
     * being written. Null is not an error — it is the honest signal that
     * nothing is being saved, so no screen can imply otherwise.
     */
    val sessionId: String? = null,
    /**
     * Set when a write to the store failed. Stays set until the session is
     * opened or the conversation is cleared: a later successful write does not
     * make the lost turn reappear, so clearing it would hide real data loss.
     */
    val persistenceError: String? = null,
)

class ChatEngine(
    private val registry: CapabilityRegistry,
    private val providers: Map<ProviderId, AiProvider>,
    private val modelFor: (ProviderId) -> String = ::defaultModelFor,
    /**
     * Null means "this instance does not persist" — the tests' setup, never
     * production, which always receives the Room-backed implementation.
     */
    private val repository: ChatRepository? = null,
    /**
     * Where "this model was actually used" gets recorded. Null means no store
     * wired — tests' setup; production receives the preferences-backed one.
     * Optional only so it can default: a request without it still sends, it
     * just does not appear in Recent.
     */
    private val modelState: ModelUserStateRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    scope: CoroutineScope? = null
) {
    private val _state = MutableStateFlow(ChatEngineState())
    val state: StateFlow<ChatEngineState> = _state

    private val scope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamJob: Job? = null

    /** Serialises writes so turns cannot land out of order. */
    private val writeMutex = Mutex()

    /** Guarantees strictly increasing stamps, so ordering survives timestamp ties. */
    private var lastStamp = 0L

    /**
     * Wall clock, floored to be strictly increasing. Two turns written in the
     * same millisecond must not compare equal, or their relative order would be
     * decided by a UUID — effectively at random.
     */
    @Synchronized
    private fun nextStamp(): Long {
        val now = clock()
        val stamp = if (now > lastStamp) now else lastStamp + 1
        lastStamp = stamp
        return stamp
    }

    suspend fun refreshProviderStatus() {
        val id = _state.value.selectedProvider
        val status = runCatching { registry.providerStatus(id) }
            .getOrElse { ProviderStatus.Unavailable("Probe failed: ${it.message ?: it::class.java.simpleName}") }
        _state.update { it.copy(providerStatus = status) }
    }

    fun selectProvider(id: ProviderId) {
        if (_state.value.busy) return
        // A picked model belongs to exactly one adapter. Moving providers
        // drops any selection that does not belong here, rather than leaving
        // it to be resolved against a transport that does not serve it.
        val selected = _state.value.selectedModelId
        val stillApplies = selected != null && ModelCatalog.byId(selected)?.adapter == id
        _state.update {
            it.copy(
                selectedProvider = id,
                selectedModelId = if (stillApplies) selected else null,
                providerStatus = null,
                lastError = null,
            )
        }
    }

    /**
     * Switches to [model].
     *
     * The probe is dropped unconditionally, exactly as [selectProvider] does:
     * `Ready` was established for one provider and this call may be moving to
     * another, so carrying it across would let a request through on a green
     * light that was taken somewhere else. The chat screen re-probes
     * immediately after a pick, so the user does not see the gap.
     *
     * Refuses anything not in [ModelCatalog]. Everything downstream resolves
     * the id back against the catalog, so accepting a row from anywhere else
     * would show a selection that can never reach the wire — the decorative
     * control this project does not ship.
     */
    fun selectModel(model: ModelDef) {
        if (_state.value.busy) return
        if (ModelCatalog.byId(model.id) != model) return
        _state.update {
            it.copy(
                selectedModelId = model.id,
                selectedProvider = model.adapter,
                providerStatus = null,
                lastError = null,
            )
        }
    }

    /**
     * The id that goes on the wire: the catalog row the user picked, while it
     * still belongs to [providerId]; otherwise [modelFor]'s default.
     *
     * The custom endpoint deliberately has no catalog row — what its server
     * accepts is whatever the user configured — so it falls through to
     * [modelFor] here and picks the stored model up downstream, which is what
     * keeps that already-working path working.
     */
    private fun modelOnWire(providerId: ProviderId): String {
        val picked = _state.value.selectedModelId
            ?.let { ModelCatalog.byId(it) }
            ?.takeIf { it.adapter == providerId }
        return picked?.id ?: modelFor(providerId)
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    /** Cancel in-flight stream; partial assistant text is kept and labeled stopped. */
    fun stop() {
        streamJob?.cancel(StopRequest)
    }

    fun clearConversation() {
        if (_state.value.busy) return
        // Detaching the session is what makes the next send open a fresh one.
        // The conversation just left stays on disk — "clear" must not mean
        // "destroy the history you can see in Recent Chats".
        //
        // The probe is carried over deliberately: clearing text says nothing
        // about the API key, and dropping it here meant the very next send was
        // refused with "Provider not probed yet" for no reason the user could
        // see.
        _state.value = ChatEngineState(
            selectedProvider = _state.value.selectedProvider,
            selectedModelId = _state.value.selectedModelId,
            providerStatus = _state.value.providerStatus,
        )
    }

    /**
     * Send one user turn. Returns false when blocked (error in state).
     * No assistant turn is created unless Ready + adapter exist.
     */
    fun send(userText: String): Boolean {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.busy) return false

        val userTurn = ChatTurn(
            id = UUID.randomUUID().toString(),
            author = ChatAuthor.USER,
            text = text,
            createdAt = nextStamp(),
        )
        val providerId = _state.value.selectedProvider
        // Prefer cached probe; if never probed, refuse honestly until refreshProviderStatus runs.
        val status = _state.value.providerStatus
            ?: ProviderStatus.Unavailable("Provider not probed yet — tap refresh")

        if (status !is ProviderStatus.Ready) {
            _state.update {
                it.copy(
                    providerStatus = status,
                    lastError = "Unavailable — ${status.reason}",
                    turns = it.turns + userTurn
                )
            }
            persistAsync(userTurn)
            return false
        }

        val provider = providers[providerId]
        if (provider == null) {
            _state.update {
                it.copy(
                    lastError = "Unavailable — no adapter registered for ${providerId.title}",
                    turns = it.turns + userTurn
                )
            }
            persistAsync(userTurn)
            return false
        }

        val assistantId = UUID.randomUUID().toString()
        val history = (_state.value.turns + userTurn).map { turn ->
            val role = if (turn.author == ChatAuthor.USER) ChatRole.USER else ChatRole.ASSISTANT
            ChatMessage(role, turn.text)
        }
        val modelId = modelOnWire(providerId)
        val request = ChatRequest(messages = history, model = modelId)

        // Hoisted out of the update lambda: MutableStateFlow.update may retry
        // that lambda, and stamping inside it would burn a stamp per attempt.
        val assistantTurn = ChatTurn(
            id = assistantId,
            author = ChatAuthor.AGENT,
            text = "",
            streaming = true,
            createdAt = nextStamp(),
        )

        _state.update {
            it.copy(
                turns = it.turns + userTurn + assistantTurn,
                busy = true,
                streamingText = "",
                lastError = null,
                providerStatus = status
            )
        }

        streamJob = scope.launch {
            val buffer = StringBuilder()
            try {
                // Persisted before the stream so the user's message cannot be
                // lost to a network failure that arrives first.
                persist(userTurn)
                val chunks = provider.chatStream(request)
                // Recorded here rather than when a model is picked in the
                // picker: both guards above passed and the adapter has taken
                // the request, so this is the model being used. Tapping
                // through a list to see what is in it must not fill Recent
                // with entries that never carried a token.
                modelState?.markUsed(modelId, clock())
                chunks.collect { chunk ->
                    if (chunk.delta.isNotEmpty()) {
                        buffer.append(chunk.delta)
                        val partial = buffer.toString()
                        _state.update { s ->
                            s.copy(
                                streamingText = partial,
                                turns = s.turns.map { t ->
                                    if (t.id == assistantId) t.copy(text = partial) else t
                                }
                            )
                        }
                    }
                    if (chunk.done) {
                        finalize(assistantId, buffer.toString(), error = null, stopped = false)
                    }
                }
                if (_state.value.busy) {
                    finalize(assistantId, buffer.toString(), error = null, stopped = false)
                }
            } catch (c: CancellationException) {
                if (c === StopRequest) {
                    finalize(
                        assistantId,
                        buffer.toString(),
                        error = if (buffer.isEmpty()) "Stopped before any tokens" else null,
                        stopped = true
                    )
                } else {
                    throw c
                }
            } catch (t: Throwable) {
                val reason = when (t) {
                    is ProviderHttpException -> "Provider error ${t.status}: ${t.message}"
                    is IllegalStateException -> t.message ?: "Provider not ready"
                    else -> t.message ?: t::class.java.simpleName
                }
                finalize(assistantId, buffer.toString(), error = reason, stopped = false)
            }
        }
        return true
    }

    private suspend fun finalize(assistantId: String, text: String, error: String?, stopped: Boolean) {
        _state.update { s ->
            s.copy(
                busy = false,
                streamingText = null,
                lastError = error,
                turns = s.turns.map { t ->
                    if (t.id != assistantId) return@map t
                    when {
                        stopped && text.isEmpty() -> t.copy(
                            text = "— generation stopped —",
                            streaming = false,
                            error = "stopped"
                        )
                        stopped -> t.copy(text = text, streaming = false, error = "stopped")
                        error != null && text.isEmpty() -> t.copy(
                            text = "— request failed —",
                            streaming = false,
                            error = error
                        )
                        error != null -> t.copy(text = text, streaming = false, error = error)
                        else -> t.copy(text = text, streaming = false, error = null)
                    }
                }
            )
        }

        // Store what the reader will actually see — the stopped/failed label,
        // not the raw arguments — so a reopened chat renders identically.
        _state.value.turns.firstOrNull { it.id == assistantId }?.let { persist(it) }
    }

    // --- persistence ----------------------------------------------------------

    /** Non-suspend entry point for call sites inside `send`. */
    private fun persistAsync(turn: ChatTurn) {
        if (repository == null) return
        scope.launch { persist(turn) }
    }

    private suspend fun persist(turn: ChatTurn) {
        val repo = repository ?: return
        writeMutex.withLock {
            try {
                val sessionId = _state.value.sessionId ?: createSessionLocked(repo, turn)
                repo.append(
                    StoredTurn(
                        id = turn.id,
                        sessionId = sessionId,
                        author = turn.author,
                        text = turn.text,
                        error = turn.error,
                        createdAt = turn.createdAt.takeIf { it > 0 } ?: nextStamp(),
                    )
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // A failed write must be visible, not swallowed: the chat keeps
                // working from memory, but this turn will not be there after a
                // restart and the user has to be told rather than discover it.
                _state.update { s ->
                    s.copy(persistenceError = t.message ?: t::class.java.simpleName)
                }
            }
        }
    }

    private suspend fun createSessionLocked(repo: ChatRepository, hint: ChatTurn): String {
        _state.value.sessionId?.let { return it }
        val created = repo.createSession(
            profile = DefaultChain.of(modelOnWire(_state.value.selectedProvider)),
            title = titleFor(hint),
            now = hint.createdAt.takeIf { it > 0 } ?: nextStamp(),
        )
        _state.update { it.copy(sessionId = created.id) }
        return created.id
    }

    private fun titleFor(hint: ChatTurn): String {
        val source = if (hint.author == ChatAuthor.USER) {
            hint.text
        } else {
            _state.value.turns.firstOrNull { it.author == ChatAuthor.USER }?.text.orEmpty()
        }
        val oneLine = source.replace(WHITESPACE_RUN, " ").trim()
        return oneLine.take(TITLE_MAX_LENGTH).ifEmpty { NEW_CHAT_TITLE }
    }

    /**
     * Reopen a stored conversation, replacing what is on screen.
     *
     * Refuses while a stream is in flight: swapping history under an active
     * stream would interleave two conversations in both the view and the store.
     *
     * The provider selection is deliberately left alone. A session records a
     * model id, not an adapter, and model → adapter is not reliably invertible
     * (several adapters can serve one id), so guessing would silently move the
     * conversation to a different endpoint than it ran on.
     */
    suspend fun openSession(sessionId: String): Boolean {
        val repo = repository ?: return false
        if (_state.value.busy) return false
        return writeMutex.withLock {
            val session = repo.session(sessionId)
            if (session == null) {
                _state.update { it.copy(lastError = "That conversation is no longer available") }
                return@withLock false
            }
            val restored = repo.turns(sessionId).map { stored ->
                ChatTurn(
                    id = stored.id,
                    author = stored.author,
                    text = stored.text,
                    streaming = false,
                    error = stored.error,
                    createdAt = stored.createdAt,
                )
            }
            _state.update {
                it.copy(
                    turns = restored,
                    sessionId = sessionId,
                    lastError = null,
                    streamingText = null,
                    persistenceError = null,
                )
            }
            true
        }
    }

    fun shutdown() {
        streamJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    private object StopRequest : CancellationException("stop")

    private companion object {
        const val TITLE_MAX_LENGTH = 60
        const val NEW_CHAT_TITLE = "New chat"
        val WHITESPACE_RUN = Regex("\\s+")
    }
}
