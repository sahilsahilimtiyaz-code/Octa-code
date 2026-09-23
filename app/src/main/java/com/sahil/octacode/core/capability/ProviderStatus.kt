package com.sahil.octacode.core.capability

// M1/M2 shared: every provider status carries an honest reason — never fake "ready".
sealed interface ProviderStatus {
    val configured: Boolean
    val reason: String

    data class Ready(override val reason: String = "ok") : ProviderStatus {
        override val configured: Boolean = true
    }
    data class MissingKey(override val reason: String = "API key not configured") : ProviderStatus {
        override val configured: Boolean = false
    }
    data class Misconfigured(override val reason: String) : ProviderStatus {
        override val configured: Boolean = false
    }
    data class Unavailable(override val reason: String) : ProviderStatus {
        override val configured: Boolean = false
    }
}
