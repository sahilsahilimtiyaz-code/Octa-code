package com.sahil.octacode.core.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT
}

@Serializable
data class ChatMessage(val role: ChatRole, val content: String)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.2
)

// One streaming delta. `done=true` marks the terminal chunk.
data class ChatChunk(val delta: String, val done: Boolean = false)
