package com.sahil.octacode.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// SSE parser. The line mechanics — the `data:` prefix, keep-alive comments,
// OpenAI's `[DONE]` marker — are shared; only the JSON path the text sits at
// differs, which is the one thing each provider owns.
// Returns null for lines with nothing to say; throws nothing — malformed
// lines are skipped so adapters never surface fake text.
object SseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The JSON payload of a `data:` line, or null when there is nothing to
     * parse: blank lines, `:` comment lines, fields other than `data:`, and
     * `[DONE]` all land here.
     */
    fun dataPayload(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith(":")) return null
        if (!t.startsWith("data:")) return null
        return t.removePrefix("data:").trim().takeIf { it.isNotEmpty() && it != "[DONE]" }
    }

    /** OpenAI and the six providers that speak its protocol. */
    fun parseDelta(line: String): String? {
        val payload = dataPayload(line) ?: return null
        return try {
            json.parseToJsonElement(payload).jsonObject
                .get("choices")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("delta")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNullSafe()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Anthropic. The prose lives only on a `text_delta`; `thinking_delta`,
     * `signature_delta` and `input_json_delta` arrive in the same event shape
     * and are other things entirely — reading them as text would print a
     * model's private reasoning into the conversation as if it had said it.
     *
     * Every other event (`message_start`, `content_block_stop`, `ping`, …)
     * carries no delta of that shape, so it falls through as null.
     */
    fun parseAnthropicDelta(line: String): String? {
        val payload = dataPayload(line) ?: return null
        return try {
            anthropicText(payload)
        } catch (_: Exception) {
            null
        }
    }

    private fun anthropicText(payload: String): String? {
        val root = json.parseToJsonElement(payload).jsonObject
        if (root.optString("type") != "content_block_delta") return null
        val delta = root["delta"]?.jsonObject ?: return null
        if (delta.optString("type") != "text_delta") return null
        return delta.optString("text")
    }

    /**
     * Gemini. All text parts of the chunk are appended — a chunk can carry
     * more than one, and the last one often carries only a `thoughtSignature`
     * with no `text` at all, which must be skipped rather than read as the
     * four letters "null".
     */
    fun parseGeminiDelta(line: String): String? {
        val payload = dataPayload(line) ?: return null
        return try {
            geminiText(payload)
        } catch (_: Exception) {
            null
        }
    }

    private fun geminiText(payload: String): String? {
        val root = json.parseToJsonElement(payload).jsonObject
        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return null
        return buildString {
            for (part in parts) {
                val text = part.jsonObject.optString("text")
                if (!text.isNullOrEmpty()) append(text)
            }
        }.takeIf { it.isNotEmpty() }
    }

    fun isDone(line: String): Boolean = line.trim() == "data: [DONE]"

    private fun JsonObject.optString(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNullSafe()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
