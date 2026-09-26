package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.security.CredentialStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

/**
 * Base URLs of the named providers that speak the OpenAI wire protocol.
 *
 * Taken from each vendor's own API documentation rather than from memory,
 * because a wrong host fails identically to a wrong key and the user would
 * have no way to tell the two apart.
 *
 * `openAiCompatibleStream` appends `/chat/completions`, so these must be the
 * *documented* bases and nothing more:
 *
 *  - DeepSeek documents `https://api.deepseek.com` and posts to
 *    `/chat/completions` on it directly — there is no `/v1` in their example,
 *    so adding one here would be inventing a path.
 *  - Groq's base already carries `/openai/v1`; Mistral, xAI and OpenRouter
 *    carry `/v1`, `/v1` and `/api/v1`.
 */
internal val NAMED_PROVIDER_BASE_URLS: Map<ProviderId, String> = linkedMapOf(
    ProviderId.DEEPSEEK to "https://api.deepseek.com",
    ProviderId.GROQ to "https://api.groq.com/openai/v1",
    ProviderId.MISTRAL to "https://api.mistral.ai/v1",
    ProviderId.XAI to "https://api.x.ai/v1",
    ProviderId.OPENROUTER to "https://openrouter.ai/api/v1",
)

/**
 * One adapter for every named provider that speaks the OpenAI protocol — same
 * request body, same `Authorization: Bearer` header, same streaming shape;
 * only the host and the key slot differ.
 *
 * Sharing a single class keeps five more providers honest: there is one place
 * where a key is read, one place where a missing key becomes a
 * [ProviderStatus] the Settings screen can render, and one streaming
 * implementation to get an HTTP failure right in.
 *
 * @param apiKey reads the stored key for this provider, or null when unset.
 *   A lambda rather than a `CredentialStore` so the validation rules can be
 *   exercised without an Android `Context` — this project has no Robolectric,
 *   and a rule nobody can test is a rule nobody will keep.
 */
class CompatibleProviderAdapter(
    override val id: ProviderId,
    private val baseUrl: String,
    private val http: HttpClient,
    private val apiKey: () -> String?,
) : AiProvider {
    override val badge = CapabilityBadge.API
    private val title: String get() = id.title

    override suspend fun validate(): ProviderStatus {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            return ProviderStatus.MissingKey("$title API key not configured")
        }
        // A prefix is a diagnostic, never a gate. `sk-` alone is shared by
        // OpenAI, DeepSeek and others, so rejecting on a missing prefix would
        // turn valid keys into "misconfigured" — foreignProviderHint only
        // speaks up when a prefix is unique to one OTHER provider.
        foreignProviderHint(key, id)?.let { return ProviderStatus.Misconfigured(it) }
        return ProviderStatus.Ready("$title key present (format-checked, not network-verified)")
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> {
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "$title API key not configured — open Settings → Providers and paste it in"
            )
        return openAiCompatibleStream(http, baseUrl, key, request)
    }
}

/**
 * Builds one adapter per entry in [NAMED_PROVIDER_BASE_URLS].
 *
 * Each adapter closes over its own id rather than the whole store, so a key
 * can only ever be read for the provider being asked about.
 */
internal fun namedProviderAdapters(
    http: HttpClient,
    credentials: CredentialStore
): Map<ProviderId, AiProvider> = NAMED_PROVIDER_BASE_URLS.mapValues { (id, url) ->
    CompatibleProviderAdapter(id, url, http) { credentials.getApiKey(id) }
}
