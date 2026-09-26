package com.sahil.octacode.core.provider

// Provider ids. CLAUDE/GEMINI are declared but have NO adapter — the UI shows
// them as unavailable rather than faking a probe (honesty principle).
//
// The five below all speak the OpenAI wire protocol, so one adapter class
// serves them; see `NamedProviders`. Their base URLs and default model ids are
// taken from each vendor's own API documentation rather than remembered, and
// the defaults are what goes on the wire before the user has picked a model —
// `ChatModelDefaults` is exhaustive over this enum for that reason.
enum class ProviderId(val title: String) {
    OPENAI("OpenAI"),
    CUSTOM("Custom endpoint"),
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    DEEPSEEK("DeepSeek"),
    GROQ("Groq"),
    MISTRAL("Mistral"),
    XAI("xAI"),
    OPENROUTER("OpenRouter")
}
