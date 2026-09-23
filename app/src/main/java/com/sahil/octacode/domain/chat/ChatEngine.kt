package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.provider.defaultModelFor
import com.sahil.octacode.data.providers.ProviderHttpException
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

enum class ChatAuthor { USER, AGENT }

/** One conversation turn (domain — mapped to UI models at the screen). */
data class ChatTurn(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val streaming: Boolean = false,
    val error: String? = null
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
    val providerStatus: ProviderStatus? = null
)

class ChatEngine(
    private val registry: CapabilityRegistry,
    private val providers: Map<ProviderId, AiProvider>,
    private val modelFor: (ProviderId) -> String = ::defaultModelFor,
    scope: CoroutineScope? = null
) {
    private val _state = MutableStateFlow(ChatEngineState())
    val state: StateFlow<ChatEngineState> = _state

    private val scope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamJob: Job? = null

    suspend fun refreshProviderStatus() {
        val id = _state.value.selectedProvider
        val status = runCatching { registry.providerStatus(id) }
            .getOrElse { ProviderStatus.Unavailable("Probe failed: ${it.message ?: it::class.java.simpleName}") }
        _state.update { it.copy(providerStatus = status) }
    }

    fun selectProvider(id: ProviderId) {
        if (_state.value.busy) return
        _state.update { it.copy(selectedProvider = id, providerStatus = null, lastError = null) }
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
        _state.value = ChatEngineState(selectedProvider = _state.value.selectedProvider)
    }

    /**
     * Send one user turn. Returns false when blocked (error in state).
     * No assistant turn is created unless Ready + adapter exist.
     */
    fun send(userText: String): Boolean {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.busy) return false

        val userTurn = ChatTurn(id = UUID.randomUUID().toString(), author = ChatAuthor.USER, text = text)
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
            return false
        }

        val assistantId = UUID.randomUUID().toString()
        val history = (_state.value.turns + userTurn).map { turn ->
            val role = if (turn.author == ChatAuthor.USER) ChatRole.USER else ChatRole.ASSISTANT
            ChatMessage(role, turn.text)
        }
        val request = ChatRequest(messages = history, model = modelFor(providerId))

        _state.update {
            it.copy(
                turns = it.turns + userTurn + ChatTurn(
                    id = assistantId,
                    author = ChatAuthor.AGENT,
                    text = "",
                    streaming = true
                ),
                busy = true,
                streamingText = "",
                lastError = null,
                providerStatus = status
            )
        }

        streamJob = scope.launch {
            val buffer = StringBuilder()
            try {
                provider.chatStream(request).collect { chunk ->
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

    private fun finalize(assistantId: String, text: String, error: String?, stopped: Boolean) {
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
    }

    fun shutdown() {
        streamJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    private object StopRequest : CancellationException("stop")
}
