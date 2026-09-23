package com.sahil.octacode.core.provider

// Honest capability badges shown in UI (spec M2). No badge lies about locality.
enum class CapabilityBadge(val label: String) {
    API("API"),
    LOCAL("LOCAL"),
    REMOTE("REMOTE"),
    UNAVAILABLE("UNAVAILABLE")
}
