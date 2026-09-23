package com.sahil.octacode.di

import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.net.KtorHttpFactory
import com.sahil.octacode.data.providers.CustomEndpointAdapter
import com.sahil.octacode.data.providers.DefaultCapabilityRegistry
import com.sahil.octacode.data.providers.OpenAiAdapter
import com.sahil.octacode.data.security.CredentialStore
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Koin graph. Adapters are wired AND exposed to Settings UI —
// no orphan code: every binding is consumed by the registry or a screen.
val appModule = module {
    single<HttpClient> { KtorHttpFactory.create() }
    single { CredentialStore(androidContext()) }

    single<OpenAiAdapter> { OpenAiAdapter(get(), get()) }
    single<CustomEndpointAdapter> { CustomEndpointAdapter(get(), get()) }

    single<Map<ProviderId, AiProvider>> {
        mapOf(
            ProviderId.OPENAI to get<OpenAiAdapter>(),
            ProviderId.CUSTOM to get<CustomEndpointAdapter>()
            // CLAUDE / GEMINI intentionally absent in M2 → registry reports Unavailable.
        )
    }

    single<CapabilityRegistry> { DefaultCapabilityRegistry(get()) }
}
