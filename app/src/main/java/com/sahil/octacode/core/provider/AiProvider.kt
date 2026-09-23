package com.sahil.octacode.core.provider

import com.sahil.octacode.core.capability.ProviderStatus
import kotlinx.coroutines.flow.Flow

// M2 adapter contract. All network errors surface as exceptions;
// adapters never emit fake content.
interface AiProvider {
    val id: ProviderId
    val badge: CapabilityBadge

    // Probe configuration WITHOUT network side effects or assumptions.
    suspend fun validate(): ProviderStatus

    // Real streaming chat. Must throw on auth/rate-limit/network errors.
    fun chatStream(request: ChatRequest): Flow<ChatChunk>
}
