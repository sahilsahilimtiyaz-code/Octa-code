package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.net.RetryPolicy
import com.sahil.octacode.data.net.SseParser
import com.sahil.octacode.data.security.CredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatCompletionsWire(
    val model: String,
    val messages: List<WireMsg>,
    val stream: Boolean = true,
    val max_tokens: Int,
    val temperature: Double
)

@Serializable
internal data class WireMsg(val role: String, val content: String)

class ProviderHttpException(val status: Int, message: String) : Exception(message)

// Shared OpenAI-compatible streaming implementation used by OpenAiAdapter
// and CustomEndpointAdapter (same wire protocol, different base URL / key slot).
internal fun openAiCompatibleStream(
    http: HttpClient,
    baseUrl: String,
    apiKey: String,
    request: ChatRequest,
    retry: RetryPolicy = RetryPolicy()
): Flow<ChatChunk> = flow {
    val url = baseUrl.trimEnd('/') + "/chat/completions"
    val body = ChatCompletionsWire(
        model = request.model,
        messages = request.messages.map { WireMsg(it.role.name.lowercase(), it.content) },
        max_tokens = request.maxTokens,
        temperature = request.temperature
    )
    var attempt = 0
    while (true) {
        attempt++
        val resp = http.post(url) {
            if (apiKey.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!resp.status.isSuccess()) {
            val code = resp.status.value
            if (retry.shouldRetry(code, attempt)) {
                delay(retry.delayForAttempt(attempt))
                continue
            }
            // The status line only ever says "401". Providers put the sentence
            // that actually explains the failure in the body, so read it — this
            // is the difference between a fixable message and a guessing game.
            throw ProviderHttpException(code, describeHttpError(code, baseUrl, resp.bodyAsText()))
        }
        val channel = resp.bodyAsChannel()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (SseParser.isDone(line)) break
            val delta = SseParser.parseDelta(line) ?: continue
            if (delta.isNotEmpty()) emit(ChatChunk(delta))
        }
        emit(ChatChunk("", done = true))
        return@flow
    }
}

/**
 * Turns a failed HTTP response into a message a user can act on, keeping the
 * provider's own explanation instead of the bare status code.
 */
internal fun describeHttpError(code: Int, baseUrl: String, body: String): String {
    val detail = providerMessage(body)?.replace('\n', ' ')?.take(300)
    return if (detail.isNullOrBlank()) {
        "HTTP $code from $baseUrl"
    } else {
        "HTTP $code from $baseUrl: $detail"
    }
}

/** Pulls the human-readable reason out of an OpenAI/OpenRouter-shaped error body. */
internal fun providerMessage(body: String): String? {
    // {"error":{"message":"…"}} (OpenAI, OpenRouter) or {"message":"…"} (some proxies).
    val quoted = MESSAGE_FIELD.find(body)
    if (quoted != null) return unescapeJson(quoted.groupValues[1])
    // Not JSON we recognise — fall back to a trimmed snippet, but never to markup.
    val raw = body.trim()
    if (raw.isEmpty() || raw.startsWith("<")) return null
    return raw
}

private val MESSAGE_FIELD =
    Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

private fun unescapeJson(value: String): String = buildString(value.length) {
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c != '\\' || i + 1 >= value.length) {
            append(c)
            i++
            continue
        }
        when (val next = value[i + 1]) {
            'n' -> { append('\n'); i += 2 }
            'r' -> { append('\r'); i += 2 }
            't' -> { append('\t'); i += 2 }
            'u' -> {
                val hex = value.substring(i + 2, minOf(i + 6, value.length))
                val code = hex.toIntOrNull(16)
                if (code == null) {
                    append(next)
                    i += 2
                } else {
                    append(Char(code))
                    i += 2 + hex.length
                }
            }
            else -> { append(next); i += 2 }
        }
    }
}

/**
 * Key prefixes that survive a bare `startsWith("sk-")` check but belong to a
 * different provider. An OpenRouter key (`sk-or-…`) passes OpenAI's format
 * validation and is only rejected once the request is sent — which reads to the
 * user as the app being broken rather than the wrong box being filled in.
 */
internal fun foreignProviderHint(key: String): String? = when {
    key.startsWith("sk-or-") ->
        "This is an OpenRouter key — paste it into Custom endpoint instead of OpenAI"
    key.startsWith("sk-ant-") || key.startsWith("sk-claude") ->
        "This is an Anthropic key — Claude is not implemented in this build"
    key.startsWith("AIza") || key.startsWith("sk-google") ->
        "This is a Google AI key — Gemini is not implemented in this build"
    else -> null
}

// Real OpenAI adapter: https://api.openai.com/v1 — key from CredentialStore.
class OpenAiAdapter(
    private val http: HttpClient,
    private val credentials: CredentialStore
) : AiProvider {
    override val id = ProviderId.OPENAI
    override val badge = CapabilityBadge.API

    override suspend fun validate(): ProviderStatus {
        val key = credentials.getApiKey(id)
        if (key.isNullOrBlank()) {
            return ProviderStatus.MissingKey("OpenAI API key not configured")
        }
        if (!key.startsWith("sk-")) {
            return ProviderStatus.Misconfigured("OpenAI key has unexpected format")
        }
        foreignProviderHint(key)?.let { return ProviderStatus.Misconfigured(it) }
        return ProviderStatus.Ready("OpenAI key present (format-checked, not network-verified)")
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        val key = credentials.getApiKey(id)
            ?: throw IllegalStateException("OpenAI API key not configured")
        return openAiCompatibleStream(http, "https://api.openai.com/v1", key, request)
    }
}

// Real custom-endpoint adapter: any OpenAI-compatible server (Ollama, vLLM,
// self-hosted gateway). Base URL + optional key + model are user-configured.
class CustomEndpointAdapter(
    private val http: HttpClient,
    private val credentials: CredentialStore
) : AiProvider {
    override val id = ProviderId.CUSTOM
    override val badge = CapabilityBadge.REMOTE

    override suspend fun validate(): ProviderStatus {
        val url = credentials.getCustomBaseUrl()
        if (url.isNullOrBlank()) {
            return ProviderStatus.MissingKey("Custom base URL not configured")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ProviderStatus.Misconfigured("Custom base URL must start with http(s)://")
        }
        return ProviderStatus.Ready("Custom endpoint configured: $url")
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        val url = credentials.getCustomBaseUrl()
            ?: throw IllegalStateException("Custom base URL not configured")
        val key = credentials.getApiKey(id) ?: ""
        val model = resolveCustomModel(request.model, credentials.getCustomModel())
            ?: throw IllegalStateException(
                "No model set for the custom endpoint — open Settings → Custom endpoint " +
                    "and enter one (for example openai/gpt-4o-mini)"
            )
        return openAiCompatibleStream(http, url, key, request.copy(model = model))
    }
}

/**
 * The model that actually goes on the wire. Returns null when nothing usable is
 * configured: "default" is our own placeholder for "unset", and sending that
 * literal string earns HTTP 400 from every real server.
 */
internal fun resolveCustomModel(requestModel: String, configuredModel: String): String? =
    requestModel.takeIf { it.isNotBlank() && it != "default" }
        ?: configuredModel.takeIf { it.isNotBlank() && it != "default" }
