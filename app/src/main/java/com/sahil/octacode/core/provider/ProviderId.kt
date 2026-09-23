package com.sahil.octacode.core.provider

// M2 provider ids. Claude/Gemini are declared but have NO adapter yet —
// UI must show them as "Unavailable: not implemented in M2" (honesty principle).
enum class ProviderId(val title: String) {
    OPENAI("OpenAI"),
    CUSTOM("Custom endpoint"),
    CLAUDE("Claude"),
    GEMINI("Gemini")
}
