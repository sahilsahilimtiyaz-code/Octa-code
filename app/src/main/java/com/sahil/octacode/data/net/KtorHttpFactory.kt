package com.sahil.octacode.data.net

import com.sahil.octacode.data.security.SecretRedactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

// Single Ktor client: JSON content negotiation + redacting logger + sane timeouts.
// Retries for 429/5xx are handled per-request in adapters via RetryPolicy
// (so streaming bodies are never silently replayed with fake data).
object KtorHttpFactory {
    fun create(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            @OptIn(ExperimentalSerializationApi::class)
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    android.util.Log.i("OctaNet", SecretRedactor.redact(message))
                }
            }
            sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
        }
    }

    /**
     * Client for runtime artifact downloads (R1).
     *
     * Deliberately separate from [create]: Ktor's requestTimeout covers the whole
     * response BODY, so the default 90s would kill a 125MB download mid-flight.
     * Here there is no overall cap — a download ends when the bytes end, and a dead
     * link is detected by the idle socket timeout instead.
     */
    fun createDownloadClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 0L // size is unbounded; no wall-clock cap
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000 // no bytes for 60s → link is dead
        }
    }
}
