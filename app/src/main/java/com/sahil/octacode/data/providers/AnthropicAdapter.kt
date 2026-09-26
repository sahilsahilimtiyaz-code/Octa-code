package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.net.RetryPolicy
import com.sahil.octacode.data.net.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/** Anthropic's documented base; each endpoint appends its own path. */
internal const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"

/**
 * Required on every Messages request. An absent `anthropic-version` is a bad
 * request rather than an implicit "latest", so it cannot be left off — and
 * forgetting it would surface as a 400 that says nothing about the key.
 */
internal const val ANTHROPIC_VERSION = "2023-06-01"

/**
 * The body of `POST /v1/messages`.
 *
 * `system` is a top-level field, not a message: Anthropic accepts `user` and
 * `assistant` in `messages` only, so a system turn sent inline is a 400. The
 * conversion happens in [anthropicStream].
 *
 * `max_tokens` has no default to omit it for — the API requires it, so a
 * request without one is refused before it is read.
 */
@Serializable
internal data class AnthropicWire(
    val model: String,
    val max_tokens: Int,
    val messages: List<WireMsg>,
    // Required, not defaulted, for the same reason as `ChatCompletionsWire`:
    // this client's `Json` does not encode defaults, so a defaulted `stream`
    // would leave the field out and Anthropic would answer with one message
    // object instead of an event stream.
    val stream: Boolean,
    val temperature: Double? = null,
    val system: String? = null,
)

/**
 * Streams one completion from Anthropic.
 *
 * Shares the OpenAI path's *shape* — same retry loop, same "read the error
 * body, the status line only says 401" rule — but not its wire format, because
 * forcing a different protocol through `openAiCompatibleStream` would mean
 * either lying about the body or branching inside it per provider.
 */
internal fun anthropicStream(
    http: HttpClient,
    apiKey: String,
    request: ChatRequest,
    retry: RetryPolicy = RetryPolicy()
): Flow<ChatChunk> = flow {
    val url = "$ANTHROPIC_BASE_URL/v1/messages"
    val system = request.messages
        .filter { it.role == ChatRole.SYSTEM }
        .joinToString("\n") { it.content }
        .ifBlank { null }
    val messages = request.messages
        .filter { it.role != ChatRole.SYSTEM }
        .map { WireMsg(it.role.name.lowercase(), it.content) }
    // Anthropic takes 0.0–1.0 while OpenAI's scale reaches 2.0, so a setting
    // chosen there would be a 400 on first contact here. Clamped to this API's
    // documented maximum rather than sent and refused.
    val body = AnthropicWire(
        model = request.model,
        max_tokens = request.maxTokens.coerceAtLeast(1),
        messages = messages,
        stream = true,
        temperature = request.temperature.coerceIn(0.0, 1.0),
        system = system,
    )
    var attempt = 0
    while (true) {
        attempt++
        val resp = http.post(url) {
            header("x-api-key", apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!resp.status.isSuccess()) {
            val code = resp.status.value
            if (retry.shouldRetry(code, attempt)) {
                delay(retry.delayForAttempt(attempt))
                continue
            }
            throw ProviderHttpException(code, describeHttpError(code, url, resp.bodyAsText()))
        }
        val channel = resp.bodyAsChannel()
        while (true) {
            // No `[DONE]` here — Anthropic ends with `event: message_stop` and
            // then closes, so this loop's terminator is the stream itself.
            val line = channel.readUTF8Line() ?: break
            val delta = SseParser.parseAnthropicDelta(line) ?: continue
            if (delta.isNotEmpty()) emit(ChatChunk(delta))
        }
        emit(ChatChunk("", done = true))
        return@flow
    }
}

/**
 * Anthropic's Messages API.
 *
 * Separate from [CompatibleProviderAdapter] because that class posts to
 * `/chat/completions` with `Authorization: Bearer`, and neither half is true
 * of this endpoint: the path is `/v1/messages`, and the key goes in `x-api-key`
 * beside an `anthropic-version` header.
 *
 * @param apiKey reads the stored key, or null when unset. A lambda rather than
 *   a `CredentialStore` so validation can be exercised without an Android
 *   `Context` — this project has no Robolectric, and a rule nobody can test is
 *   a rule nobody will keep.
 */
class AnthropicAdapter(
    private val http: HttpClient,
    private val apiKey: () -> String?
) : AiProvider {
    override val id = ProviderId.CLAUDE
    override val badge = CapabilityBadge.API
    private val title: String get() = id.title

    override suspend fun validate(): ProviderStatus {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            return ProviderStatus.MissingKey("$title API key not configured")
        }
        // No prefix gate. Anthropic keys start with `sk-ant-`, but refusing
        // anything else would turn a valid key into "misconfigured" the moment
        // the spelling changed — and the hint only speaks when a prefix is
        // unique to a *different* provider, which is the mistake worth naming.
        foreignProviderHint(key, id)?.let { return ProviderStatus.Misconfigured(it) }
        return ProviderStatus.Ready("$title key present (format-checked, not network-verified)")
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "$title API key not configured — open Settings → Providers and paste it in"
            )
        return anthropicStream(http, key, request)
    }
}
