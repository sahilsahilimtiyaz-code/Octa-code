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

/**
 * Which base URL a model list is asked for.
 *
 * CUSTOM is absent on purpose: a self-hosted server has no list we could rely
 * on, and its model is a field the user fills in — inventing a `/models` call
 * would be guessing at someone else's server, and failing at it would read as
 * the app being broken.
 *
 * OpenAI joins the five named providers here even though it does not use the
 * shared adapter: its endpoint answers the same question the same way.
 */
internal val MODEL_LIST_BASE_URLS: Map<ProviderId, String> = linkedMapOf(
    ProviderId.OPENAI to OPENAI_BASE_URL,
) + NAMED_PROVIDER_BASE_URLS

@Serializable
internal data class ModelListWire(
    @SerialName("data") val models: List<ModelWire> = emptyList(),
)

@Serializable
internal data class ModelWire(val id: String = "")

private val wireJson = Json { ignoreUnknownKeys = true }

/**
 * Ids out of an OpenAI-shaped `{"object":"list","data":[{"id":…}]}` body.
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

/** `GET {base}/models` with the key, returning the ids it offered. */
internal suspend fun fetchModelIds(
    http: HttpClient,
    baseUrl: String,
    apiKey: String,
): List<String> {
    val response = http.get("${baseUrl.trimEnd('/')}/models") {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
    }
    // Read once: an error body carries the reason, and Ktor will not hand back
    // the same stream twice.
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
        throw ProviderHttpException(
            response.status.value,
            describeHttpError(response.status.value, baseUrl, body),
        )
    }
    return decodeModelIds(body)
}
