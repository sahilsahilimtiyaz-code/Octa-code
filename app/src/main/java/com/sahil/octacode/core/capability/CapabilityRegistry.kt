package com.sahil.octacode.core.capability

import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.flow.StateFlow

// M1: central registry. Implementations must PROBE (key present? endpoint valid?)
// and never assume a provider works. Claude/Gemini/etc report Unavailable until
// real adapters exist.
interface CapabilityRegistry {
    val autonomy: StateFlow<AutonomyLevel>
    suspend fun providerStatus(id: ProviderId): ProviderStatus
    suspend fun refreshAll(): Map<ProviderId, ProviderStatus>
    fun setAutonomy(level: AutonomyLevel)
}
