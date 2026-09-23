package com.sahil.octacode.data.security

// Redacts secrets from any string before it reaches logs (non-negotiable).
object SecretRedactor {
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-~+/=]+")
    private val apiKeyJson = Regex("(?i)(\"api[_-]?key\"\\s*:\\s*\")[^\"]+(\")")
    private val skLive = Regex("sk-[A-Za-z0-9-_]{8,}")
    private val xApiKey = Regex("(?i)(x-api-key\\s*:\\s*)\\S+")
    private val githubPat = Regex("github_pat_[A-Za-z0-9_]{8,}")

    fun redact(input: String): String {
        var out = input
        out = bearer.replace(out, "Bearer ***")
        out = apiKeyJson.replace(out, "$1***$2")
        out = skLive.replace(out, "sk-***")
        out = xApiKey.replace(out, "$1***")
        out = githubPat.replace(out, "github_pat_***")
        return out
    }
}
