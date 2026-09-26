package com.sahil.octacode.domain.model

import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.provider.ProviderId
import kotlinx.coroutines.flow.StateFlow

/**
 * What a provider reported as reachable with this device's key.
 *
 * Kept apart from `ModelCatalog` because they answer different questions: the
 * catalog is what this build can name without talking to anyone, this is what
 * an endpoint said when asked. Separating them is what lets a fetched list be
 * refetched or discarded without touching the list that ships with the app.
 *
 * Also kept out of the credentials' own store: a key is a secret and lives in
 * `CredentialStore`, while these ids are ordinary state and belong in
 * preferences where the selector can read them without unlocking anything.
 */
interface FetchedModelsRepository {

    /** Every provider's fetched ids, flattened. Stable enough to render. */
    val models: StateFlow<List<ModelDef>>

    /**
     * Asks [provider] what it serves for the stored key and keeps the answer.
     *
     * Never throws. The Settings screen is the one asking, and a screen that
     * has to catch is a screen that will eventually forget to — the failure is
     * returned already phrased, the same way every other status in this app
     * carries an honest reason rather than an exception type.
     */
    suspend fun refresh(provider: ProviderId): FetchResult
}

/** Outcome of one fetch, written for display rather than for branching on text. */
data class FetchResult(val succeeded: Boolean, val message: String)
