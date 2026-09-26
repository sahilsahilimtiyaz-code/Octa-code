package com.sahil.octacode.core.provider

// Shared default model ids for chat + mission phases (no magic strings elsewhere).
//
// This must stay exhaustive: it is the model that goes on the wire on a user's
// FIRST send to a provider, before the picker has been opened, so a branch that
// returned "default" would ship the literal string "default" to a real endpoint
// and earn HTTP 400 for no reason the user could see.
//
// Each id below is the one the vendor's own docs use in their chat-completions
// example. They are a fallback, not the catalogue — M12.2 replaces this with
// the provider's fetched list when a key is configured.
fun defaultModelFor(id: ProviderId): String = when (id) {
    ProviderId.OPENAI -> "gpt-4o-mini"
    ProviderId.CUSTOM -> "default"
    ProviderId.CLAUDE -> "default"
    ProviderId.GEMINI -> "default"
    // deepseek-chat/reasoner were discontinued on 2026-07-24; the current
    // non-thinking model is deepseek-v4-flash.
    ProviderId.DEEPSEEK -> "deepseek-v4-flash"
    ProviderId.GROQ -> "llama-3.3-70b-versatile"
    ProviderId.MISTRAL -> "mistral-small-latest"
    ProviderId.XAI -> "grok-4.6"
    ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
}
