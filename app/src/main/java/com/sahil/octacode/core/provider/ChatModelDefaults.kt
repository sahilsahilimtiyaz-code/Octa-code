package com.sahil.octacode.core.provider

// Shared default model ids for chat + mission phases (no magic strings elsewhere).
fun defaultModelFor(id: ProviderId): String = when (id) {
    ProviderId.OPENAI -> "gpt-4o-mini"
    ProviderId.CUSTOM -> "default"
    ProviderId.CLAUDE -> "default"
    ProviderId.GEMINI -> "default"
}
