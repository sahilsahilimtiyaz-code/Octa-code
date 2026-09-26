package com.sahil.octacode.di

import com.sahil.octacode.core.model.ModelCatalog
import com.sahil.octacode.data.workspace.WorkspaceStore
import com.sahil.octacode.domain.chat.ChatEngine
import com.sahil.octacode.domain.model.FetchedModelsRepository
import org.koin.dsl.module

// M4 chat singleton — streams from M2 adapters (OpenAI / Custom).
// Singleton matters here: there is one active conversation, and switching
// happens through ChatEngine.openSession rather than a new engine per screen,
// so an in-flight stream cannot be orphaned by navigating away.
val chatModule = module {
    single {
        // Resolved once here rather than inside the lambda: Koin's get() is
        // meant to run while this block does, and the lambda runs whenever a
        // session happens to be created.
        val workspaces = get<WorkspaceStore>()
        val fetched = get<FetchedModelsRepository>()
        ChatEngine(
            registry = get(),
            providers = get(),
            repository = get(),
            // Records a model as used only once a request is actually issued,
            // which is the one moment the engine knows about.
            modelState = get(),
            // Read at creation time, so a folder picked after this
            // conversation started still becomes the next one's workspace.
            activeWorkspaceId = { workspaces.activeId.value },
            // Merged on every lookup rather than resolved once here: a fetch
            // that lands mid-conversation has to be selectable at once, and a
            // snapshot taken at construction would pin this engine to whatever
            // the list held at startup for as long as the process lived.
            catalog = { id ->
                ModelCatalog.mergedWith(fetched.models.value).firstOrNull { it.id == id }
            }
        )
    }
}
