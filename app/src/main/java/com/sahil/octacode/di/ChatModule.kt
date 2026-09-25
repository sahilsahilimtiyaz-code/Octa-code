package com.sahil.octacode.di

import com.sahil.octacode.domain.chat.ChatEngine
import org.koin.dsl.module

// M4 chat singleton — streams from M2 adapters (OpenAI / Custom).
// Singleton matters here: there is one active conversation, and switching
// happens through ChatEngine.openSession rather than a new engine per screen,
// so an in-flight stream cannot be orphaned by navigating away.
val chatModule = module {
    single {
        ChatEngine(
            registry = get(),
            providers = get(),
            repository = get(),
            // Records a model as used only once a request is actually issued,
            // which is the one moment the engine knows about.
            modelState = get()
        )
    }
}
