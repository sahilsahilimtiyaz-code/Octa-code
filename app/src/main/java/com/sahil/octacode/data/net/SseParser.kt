package com.sahil.octacode.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Minimal SSE parser for OpenAI-compatible `data: {...}` streams.
// Returns null for keep-alives / "[DONE]"; throws nothing — malformed lines
// are skipped so adapters never surface fake text.
object SseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseDelta(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith(":")) return null
        val payload = if (t.startsWith("data:")) t.removePrefix("data:").trim() else return null
        if (payload == "[DONE]") return null
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("delta")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNullSafe()
        } catch (_: Exception) {
            null
        }
    }

    fun isDone(line: String): Boolean = line.trim() == "data: [DONE]"

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
