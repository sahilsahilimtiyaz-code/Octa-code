package com.sahil.octacode.di

import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.model.DefaultFetchedModelsRepository
import com.sahil.octacode.data.net.KtorHttpFactory
import com.sahil.octacode.data.providers.CustomEndpointAdapter
import com.sahil.octacode.data.providers.DefaultCapabilityRegistry
import com.sahil.octacode.data.providers.OpenAiAdapter
import com.sahil.octacode.data.providers.namedProviderAdapters
import com.sahil.octacode.data.security.CredentialStore
import com.sahil.octacode.domain.model.FetchedModelsRepository
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Koin graph. Adapters are wired AND exposed to Settings UI —
// no orphan code: every binding is consumed by the registry or a screen.
val appModule = module {
    single<HttpClient> { KtorHttpFactory.create() }
    single { CredentialStore(androidContext()) }

    // Reads the shared client and the credentials store so the selector can
    // show a provider's own model list once a key exists — the same two the
    // adapters already use, so a key that can chat can also list.
    single<FetchedModelsRepository> {
        DefaultFetchedModelsRepository(androidContext(), get<HttpClient>(), get<CredentialStore>())
    }

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
