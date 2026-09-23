package com.sahil.octacode.di

import com.sahil.octacode.domain.chat.ChatEngine
import org.koin.dsl.module

// M4 chat singleton — streams from M2 adapters (OpenAI / Custom).
val chatModule = module {
    single {
        ChatEngine(
            registry = get(),
            providers = get()
        )
    }
}
