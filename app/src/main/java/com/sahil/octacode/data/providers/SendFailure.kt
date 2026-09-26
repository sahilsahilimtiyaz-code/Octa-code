package com.sahil.octacode.data.providers

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException

/**
 * Turns whatever interrupted a chat send into one sentence a person can act on.
 *
 * The send path used to fall back to `t.message ?: t::class.java.simpleName`,
 * which is how a failed send reached the screen as a bare exception string
 * ("Failed to connect to api.openai.com/…") or, when the exception carried no
 * message at all, as nothing but a class name. The bubble meanwhile read
 * "— request failed —", so there was a *failure* on screen and no *reason*.
 *
 * Two rules it follows:
 *
 *  - Say what to do next, not merely what happened. "HTTP 401" tells a person
 *    nothing; "the API key was rejected — update it in Settings → Providers"
 *    tells them where to go.
 *  - Never echo the provider's raw error body for auth-shaped failures. Those
 *    bodies routinely quote back part of the key that was presented, and this
 *    text is written into a conversation that is then stored. The status alone
 *    already settles what happened in those cases.
 *
 * Pure by construction: no Context, no clock, no network — so every branch is
 * unit-testable, and it cannot itself fail while reporting a failure.
 *
 * @param providerTitle human name of the provider being talked to, for messages
 *   that have to name it ("OpenAI", "Custom endpoint", …).
 * @param modelId the model on the wire; null means the caller had none to name.
 */
fun explainSendFailure(
    cause: Throwable,
    providerTitle: String,
    modelId: String?
): String {
    val model = modelId?.let { "\"$it\"" } ?: "the selected model"

    // Order matters: every branch below is an IOException, so the specific
    // types must be tested before the general one or they never match.
    return when (cause) {
        is ProviderHttpException -> explainHttp(cause, providerTitle, model)
        is UnknownHostException ->
            "No connection to $providerTitle — the device looks to be offline, or " +
                "that host cannot be resolved. Check the network and send again."
        is SocketTimeoutException ->
            "$providerTitle did not answer in time. It may be busy or unreachable; " +
                "send again in a moment."
        is ConnectException ->
            "Could not connect to $providerTitle. Check the network — and the base " +
                "URL in Settings → Providers if you use a custom endpoint."
        is SSLException ->
            "The secure connection to $providerTitle failed. If you use a custom " +
                "endpoint, check that its URL starts with https://."
        is SerializationException ->
            "$providerTitle sent a reply this app could not read as a model " +
                "response, which usually means it returned an error page instead. " +
                "The detail was: ${cause.message ?: "none"}"
        is IOException ->
            "Network problem while talking to $providerTitle: " +
                (cause.message ?: "no detail given")
        // Last, so an adapter's own pre-flight message ("no API key configured",
        // "endpoint is blank") survives untouched — those already say exactly
        // what is missing and where to put it.
        else -> cause.message?.takeIf { it.isNotBlank() }
            ?: cause::class.java.simpleName
    }
}

/**
 * Classifies on the status code alone and writes its own sentence, rather than
 * forwarding [ProviderHttpException.message]. That field is built by
 * `describeHttpError`, which is `"HTTP n from <baseUrl>: <raw body>"` — it both
 * repeats the URL and reproduces whatever the provider chose to echo, including
 * fragments of the presented key on a 401.
 */
private fun explainHttp(
    cause: ProviderHttpException,
    providerTitle: String,
    model: String
): String = when (cause.status) {
    401 ->
        "$providerTitle rejected the API key (HTTP 401). Open Settings → Providers " +
            "and check the key for that provider."
    402 ->
        "$providerTitle reports the account is out of credit (HTTP 402). Top it up, " +
            "then send again."
    403 ->
        "$providerTitle will not let this key use $model (HTTP 403). Check what the " +
            "key is allowed to reach, or pick another model."
    404 ->
        "$providerTitle has no $model at that address (HTTP 404). Check the base URL " +
            "in Settings → Providers, or pick another model."
    429 ->
        "$providerTitle is rate-limiting this key (HTTP 429). Wait a moment and send " +
            "again."
    in 500..599 ->
        "$providerTitle is having trouble (HTTP ${cause.status}). Send again in a " +
            "moment."
    else -> {
        // Safe to surface here: this branch is for statuses with no known cause,
        // so the body is far less likely to be echoing credentials back — and it
        // is the only remaining way to convey what the provider actually said.
        val detail = cause.message
            ?.substringAfter(": ", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("<") }
        if (detail != null) {
            "$providerTitle refused the request (HTTP ${cause.status}): $detail"
        } else {
            "$providerTitle refused the request (HTTP ${cause.status})."
        }
    }
}
