package com.sahil.octacode.core.provider

// Shared default model ids for chat + mission phases (no magic strings elsewhere).
//
// This must stay exhaustive: it is the model that goes on the wire on a user's
// FIRST send to a provider, before the picker has been opened, so a branch that
// returned "default" would ship the literal string "default" to a real endpoint
// and earn HTTP 400 for no reason the user could see.
//
// Each id below is the one the vendor's own docs use in their simplest example.
// They are a fallback, not the catalogue: what the picker offers is the bundled
// catalog plus whatever the provider listed for this key once one was saved.
fun defaultModelFor(id: ProviderId): String = when (id) {
    ProviderId.OPENAI -> "gpt-4o-mini"
    // Only CUSTOM has no model of its own to name — `resolveCustomModel` turns
    // this placeholder into the one the user typed, or refuses to send.
    ProviderId.CUSTOM -> "default"
    ProviderId.CLAUDE -> "claude-opus-5"
    ProviderId.GEMINI -> "gemini-3.7-flash"
    // deepseek-chat/reasoner were discontinued on 2026-07-24; the current
    // non-thinking model is deepseek-v4-flash.
    ProviderId.DEEPSEEK -> "deepseek-v4-flash"
    ProviderId.GROQ -> "llama-3.3-70b-versatile"
    ProviderId.MISTRAL -> "mistral-small-latest"
    ProviderId.XAI -> "grok-4.6"
    ProviderId.OPENROUTER -> "openai/gpt-4o-mini"
}
