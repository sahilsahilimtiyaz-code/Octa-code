package com.sahil.octacode.data.model

import android.content.Context
import android.content.SharedPreferences
import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.providers.MODEL_LIST_BASE_URLS
import com.sahil.octacode.data.providers.explainFetchFailure
import com.sahil.octacode.data.providers.fetchModelIds
import com.sahil.octacode.data.security.CredentialStore
import com.sahil.octacode.domain.model.FetchResult
import com.sahil.octacode.domain.model.FetchedModelsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks a provider what it serves for the stored key, then keeps the answer.
 *
 * Storage and the request live together because there is exactly one caller
 * of each and neither is useful alone: a fetcher with nowhere to put the list
 * would re-ask the endpoint on every app start, and a store nothing could
 * refresh would show a list nobody was ever asked for.
 *
 * Not encrypted. These are public model ids, not credentials — the key that
 * fetched them stays in `CredentialStore` and is never echoed into a result
 * message.
 */
class DefaultFetchedModelsRepository(
    context: Context,
    private val http: HttpClient,
    private val credentials: CredentialStore,
) : FetchedModelsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _models = MutableStateFlow(FetchedModelsCodec.decode(prefs.all))
    override val models: StateFlow<List<ModelDef>> = _models.asStateFlow()

    override suspend fun refresh(provider: ProviderId): FetchResult {
        val baseUrl = MODEL_LIST_BASE_URLS[provider]
            ?: return FetchResult(
                succeeded = false,
                message = "Nothing to fetch — this endpoint's model is the one you type in " +
                    "Settings → Providers.",
            )
        // Checked here as well as in the card's disabled state: the button is
        // a convenience, and the request must not go out unauthenticated just
        // because the screen was stale.
        val key = credentials.getApiKey(provider)
            ?: return FetchResult(false, "Save a ${provider.title} API key first.")

        val ids = try {
            fetchModelIds(http, baseUrl, key)
        } catch (cancelled: CancellationException) {
            // Not a failure to report — the caller went away. Turning this
            // into a Failure would leave "Fetching…" on a screen whose
            // coroutine is already over.
            throw cancelled
        } catch (t: Throwable) {
            return FetchResult(false, explainFetchFailure(t, provider.title))
        }

        if (ids.isEmpty()) {
            return FetchResult(
                succeeded = false,
                message = "${provider.title} answered, but listed no models for this key.",
            )
        }

        val (storedFor, value) = FetchedModelsCodec.entry(provider, ids)
        prefs.edit().putString(storedFor, value).apply()
        // Re-decoded from the file rather than appended in memory, so what the
        // selector is showing now is what a restart would also show.
        _models.value = FetchedModelsCodec.decode(prefs.all)
        return FetchResult(true, "${ids.size} ${provider.title} models fetched.")
    }

    private companion object {
        const val FILE_NAME = "octa_fetched_models"
    }
}
