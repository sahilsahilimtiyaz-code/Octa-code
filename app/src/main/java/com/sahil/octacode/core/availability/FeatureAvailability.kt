package com.sahil.octacode.core.availability

sealed interface FeatureAvailability {
    data object Available : FeatureAvailability
    data class Unavailable(val reason: UnavailableReason) : FeatureAvailability
}
