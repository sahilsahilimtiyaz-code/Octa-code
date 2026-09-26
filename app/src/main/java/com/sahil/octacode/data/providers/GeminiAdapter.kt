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

/** Google's Generative Language API host; no version in the base — see below. */
internal const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"

/**
 * The body of `:generateContent`.
 *
 * `systemInstruction` is a `Content` of its own rather than a message with a
 * role: Gemini's roles are `user` and `model` only, and there is no `system`
 * role to use, so an instruction sent as a turn is a 400. Its role is left
 * unset for the same reason.
 */
@Serializable
internal data class GeminiWire(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart>,
    /** Absent on [GeminiWire.systemInstruction], which has no role of its own. */
    val role: String? = null,
)

@Serializable
internal data class GeminiPart(val text: String)

@Serializable
internal data class GeminiGenerationConfig(
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
)

/**
 * Streams one completion from Gemini.
 *
 * Three things differ from the OpenAI path, and each is a reason not to share
 * its implementation: the key goes in `x-goog-api-key`, assistant turns are
 * called `model`, and the response carries no `[DONE]` — the last chunk is
 * simply the one whose `finishReason` is set, and the stream then closes.
 */
internal fun geminiStream(
    http: HttpClient,
    apiKey: String,
    request: ChatRequest,
    retry: RetryPolicy = RetryPolicy()
): Flow<ChatChunk> = flow {
    val system = request.messages
        .filter { it.role == ChatRole.SYSTEM }
        .joinToString("\n") { it.content }
        .ifBlank { null }
    val contents = request.messages
        .filter { it.role != ChatRole.SYSTEM }
        .map {
            GeminiContent(
                role = if (it.role == ChatRole.ASSISTANT) "model" else "user",
                parts = listOf(GeminiPart(it.content)),
            )
        }
    // Gemini's scale reaches 2.0. Clamped to this API's maximum for the same
    // reason Anthropic's is: a setting chosen against another provider must
    // not become a 400 the first time this one sees it.
    val body = GeminiWire(
        contents = contents,
        systemInstruction = system?.let { GeminiContent(parts = listOf(GeminiPart(it))) },
        generationConfig = GeminiGenerationConfig(
            maxOutputTokens = request.maxTokens.coerceAtLeast(1),
            temperature = request.temperature.coerceIn(0.0, 2.0),
        ),
    )
    // `:streamGenerateContent` is a method on the model rather than a path
    // segment, and `alt=sse` is what makes the reply an event stream instead
    // of one JSON array — omit it and there is nothing to parse line by line.
    val url = "$GEMINI_BASE_URL/v1beta/models/${request.model}:streamGenerateContent?alt=sse"
    var attempt = 0
    while (true) {
        attempt++
        val resp = http.post(url) {
            header("x-goog-api-key", apiKey)
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
            val line = channel.readUTF8Line() ?: break
            val delta = SseParser.parseGeminiDelta(line) ?: continue
            if (delta.isNotEmpty()) emit(ChatChunk(delta))
        }
        emit(ChatChunk("", done = true))
        return@flow
    }
}

/**
 * Google's Gemini API.
 *
 * Kept out of [CompatibleProviderAdapter] because that class posts to
 * `/chat/completions` with `Authorization: Bearer`, while this one posts to a
 * `:streamGenerateContent` method with `x-goog-api-key`.
 *
 * @param apiKey reads the stored key, or null when unset. A lambda rather than
 *   a `CredentialStore` so validation can be exercised without an Android
 *   `Context` — this project has no Robolectric.
 */
class GeminiAdapter(
    private val http: HttpClient,
    private val apiKey: () -> String?
) : AiProvider {
    override val id = ProviderId.GEMINI
    override val badge = CapabilityBadge.API
    private val title: String get() = id.title

    override suspend fun validate(): ProviderStatus {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            return ProviderStatus.MissingKey("$title API key not configured")
        }
        foreignProviderHint(key, id)?.let { return ProviderStatus.Misconfigured(it) }
        return ProviderStatus.Ready("$title key present (format-checked, not network-verified)")
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "$title API key not configured — open Settings → Providers and paste it in"
            )
        return geminiStream(http, key, request)
    }
}
