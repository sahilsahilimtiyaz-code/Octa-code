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
            throw ProviderHttpException(code, "HTTP $code from $baseUrl")
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

// Real OpenAI adapter: https://api.openai.com/v1 — key from CredentialStore.
class OpenAiAdapter(
    private val http: HttpClient,
    private val credentials: CredentialStore
) : AiProvider {
    override val id = ProviderId.OPENAI
    override val badge = CapabilityBadge.API

    override suspend fun validate(): ProviderStatus {
        val key = credentials.getApiKey(id)
        return when {
            key.isNullOrBlank() ->
                ProviderStatus.MissingKey("OpenAI API key not configured")
            !key.startsWith("sk-") ->
                ProviderStatus.Misconfigured("OpenAI key has unexpected format")
            else ->
                ProviderStatus.Ready("OpenAI key present (format-checked, not network-verified)")
        }
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
        val effective = request.copy(
            model = request.model.takeIf { it.isNotBlank() && it != "default" }
                ?: credentials.getCustomModel()
        )
        return openAiCompatibleStream(http, url, key, effective)
    }
}
