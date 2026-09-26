package com.sahil.octacode.data.providers

import com.sahil.octacode.core.provider.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a provider writes its list of models. */
internal enum class ModelListFormat {
    /** `{"data":[{"id":…}]}` — OpenAI and Anthropic answer this way. */
    OPENAI,

    /**
     * `{"models":[{"name":"models/…","supportedGenerationMethods":[…]}]}` —
     * Gemini's own shape, which shares no field with the others.
     */
    GEMINI,
}

/**
 * One provider's model list: where it is, how the reply is shaped, and how the
 * key travels.
 *
 * The three parts are together because they are one fact — a URL with the
 * wrong auth or read with the wrong parser fails identically to a wrong URL,
 * and the user would have no way to tell which of the three was off.
 *
 * @param url absolute URL, query string included.
 * @param authHeader header the key goes in. Never a query parameter: URLs are
 *   what gets logged, cached and put into referrers.
 * @param authScheme prefixed to the key — `"Bearer "` for OpenAI-protocol
 *   providers, empty where the header takes the bare key.
 */
internal data class ModelListEndpoint(
    val url: String,
    val format: ModelListFormat,
    val authHeader: String,
    val authScheme: String,
)

/** OpenAI-protocol list: `Authorization: Bearer <key>`, `{"data":[…]}`. */
private fun bearerEndpoint(url: String) = ModelListEndpoint(
    url = url,
    format = ModelListFormat.OPENAI,
    authHeader = HttpHeaders.Authorization,
    authScheme = "Bearer ",
)

/**
 * The list address for an OpenAI-protocol base.
 *
 * Joined here rather than at each call site: a trailing slash would otherwise
 * become `//models`, which answers 404 for a path the user never sees and has
 * no way to correct. Every provider's base passes through this one function,
 * so it can only go wrong in one place.
 */
internal fun listUrlFor(base: String): String = "${base.trimEnd('/')}/models"

/**
 * Where every provider's list is.
 *
 * CUSTOM is absent on purpose: a self-hosted server has no list we could rely
 * on, and its model is a field the user fills in — inventing a `/models` call
 * would be guessing at someone else's server, and failing at it would read as
 * the app being broken.
 *
 * The five named providers are derived from [NAMED_PROVIDER_BASE_URLS] rather
 * than repeated: those bases are already stated once, for streaming, and a
 * second copy would be a second place to edit and one of them to forget.
 */
internal val MODEL_LIST_ENDPOINTS: Map<ProviderId, ModelListEndpoint> =
    buildMap<ProviderId, ModelListEndpoint> {
        put(
            ProviderId.OPENAI,
            bearerEndpoint("$OPENAI_BASE_URL/models"),
        )
        put(
            ProviderId.CLAUDE,
            ModelListEndpoint(
                // Anthropic paginates this list at 20 by default, so asking for
                // the documented maximum keeps a longer list from being fetched
                // as if it were complete.
                url = "$ANTHROPIC_BASE_URL/v1/models?limit=1000",
                format = ModelListFormat.OPENAI,
                authHeader = "x-api-key",
                authScheme = "",
            ),
        )
        put(
            ProviderId.GEMINI,
            ModelListEndpoint(
                url = "$GEMINI_BASE_URL/v1beta/models",
                format = ModelListFormat.GEMINI,
                authHeader = "x-goog-api-key",
                authScheme = "",
            ),
        )
        NAMED_PROVIDER_BASE_URLS.forEach { (id, base) ->
            put(id, bearerEndpoint(listUrlFor(base)))
        }
    }

@Serializable
internal data class ModelListWire(
    @SerialName("data") val models: List<ModelWire> = emptyList(),
)

@Serializable
internal data class ModelWire(val id: String = "")

@Serializable
internal data class GeminiListWire(
    val models: List<GeminiModelWire> = emptyList(),
)

@Serializable
internal data class GeminiModelWire(
    val name: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
)

private val wireJson = Json { ignoreUnknownKeys = true }

/**
 * Ids out of an OpenAI-shaped `{"object":"list","data":[{"id":…}]}` body —
 * which Anthropic's `/v1/models` also uses.
 *
 * Pure so it can be pinned rather than judged by eye. It is allowed to throw
 * on a body it cannot parse: reporting "0 models" for an HTML error page would
 * tell the user the provider has nothing for them, which is the opposite of
 * what happened. The caller turns that throw into a sentence saying the list
 * could not be read.
 *
 * The ids are not filtered against anything this build recognises — that would
 * hide real models behind our own knowledge of them. What the provider listed
 * is what the selector shows.
 */
internal fun decodeModelIds(body: String): List<String> =
    wireJson.decodeFromString<ModelListWire>(body).models
        .map { it.id.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Ids out of Gemini's `{"models":[{"name":"models/…"}]}`.
 *
 * Two corrections to what comes back. The `models/` prefix is not part of the
 * id the chat endpoint accepts, so it is stripped. And a model that says it
 * cannot generate content is dropped: `/v1beta/models` also lists embedding
 * models, and one of those in a chat picker is a row that cannot answer.
 *
 * A model that says *nothing* about its methods is kept rather than dropped.
 * Hiding a working model is the worse of the two errors — this project would
 * rather show a row that fails with a real message than remove one that works.
 */
internal fun decodeGeminiModelIds(body: String): List<String> =
    wireJson.decodeFromString<GeminiListWire>(body).models
        .filter { it.supportedGenerationMethods.isEmpty() || "generateContent" in it.supportedGenerationMethods }
        .map { it.name.removePrefix("models/").trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** `GET`es [endpoint]'s list with the key, returning the ids it offered. */
internal suspend fun fetchModelIds(
    http: HttpClient,
    endpoint: ModelListEndpoint,
    apiKey: String,
): List<String> {
    val response = http.get(endpoint.url) {
        header(endpoint.authHeader, endpoint.authScheme + apiKey)
    }
    // Read once: an error body carries the reason, and Ktor will not hand back
    // the same stream twice.
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
        throw ProviderHttpException(
            response.status.value,
            describeHttpError(response.status.value, endpoint.url, body),
        )
    }
    return when (endpoint.format) {
        ModelListFormat.OPENAI -> decodeModelIds(body)
        ModelListFormat.GEMINI -> decodeGeminiModelIds(body)
    }
}
