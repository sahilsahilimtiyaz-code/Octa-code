package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// M1+M2 glue: probes every KNOWN provider. Claude/Gemini have no adapter in M2,
// so they honestly report Unavailable instead of faking activity.
class DefaultCapabilityRegistry(
    private val adapters: Map<ProviderId, AiProvider>
) : CapabilityRegistry {
    private val _autonomy = MutableStateFlow(AutonomyLevel.ASK)
    override val autonomy: StateFlow<AutonomyLevel> = _autonomy

    override suspend fun providerStatus(id: ProviderId): ProviderStatus {
        val adapter = adapters[id] ?: return ProviderStatus.Unavailable(
            // Reachable only for a provider added to the enum without being
            // wired. Every current entry has an adapter, so saying "not
            // implemented in M2" here would be a version number attached to a
            // state the app is no longer in.
            "No adapter registered for $id"
        )
        return try {
            adapter.validate()
        } catch (e: Exception) {
            ProviderStatus.Unavailable("Probe failed: ${e.message}")
        }
    }

    override suspend fun refreshAll(): Map<ProviderId, ProviderStatus> =
        ProviderId.entries.associateWith { providerStatus(it) }

    override fun setAutonomy(level: AutonomyLevel) {
        _autonomy.value = level
    }
}
