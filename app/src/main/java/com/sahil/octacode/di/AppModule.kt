package com.sahil.octacode.di

import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.net.KtorHttpFactory
import com.sahil.octacode.data.providers.CustomEndpointAdapter
import com.sahil.octacode.data.providers.DefaultCapabilityRegistry
import com.sahil.octacode.data.providers.OpenAiAdapter
import com.sahil.octacode.data.providers.namedProviderAdapters
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
        // Two bespoke adapters plus every provider that speaks the OpenAI
        // protocol (DeepSeek, Groq, Mistral, xAI, OpenRouter) — one shared
        // class each, differing only in base URL and key slot.
        mapOf<ProviderId, AiProvider>(
            ProviderId.OPENAI to get<OpenAiAdapter>(),
            ProviderId.CUSTOM to get<CustomEndpointAdapter>()
        ) + namedProviderAdapters(get<HttpClient>(), get<CredentialStore>())
        // CLAUDE / GEMINI still absent → the registry reports them Unavailable.
    }

    single<CapabilityRegistry> { DefaultCapabilityRegistry(get()) }
}
