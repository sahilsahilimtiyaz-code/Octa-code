package com.sahil.octacode.data.model

import android.content.Context
import android.content.SharedPreferences
import com.sahil.octacode.core.model.ModelUserState
import com.sahil.octacode.domain.model.ModelUserStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favorites and recents, per model — the two facts the selector needs about
 * *this* user, kept apart from the immutable catalog.
 *
 * In memory as a `StateFlow` because the selector renders straight off it:
 * starring a model has to move it into the Favorites section in the same
 * frame, and a store that only wrote to disk would leave the row where it was
 * until the screen was reopened.
 *
 * Not encrypted. These are preferences, not secrets — API keys stay in
 * `CredentialStore`.
 */
class ModelUserStateStore(context: Context) : ModelUserStateRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _states = MutableStateFlow(ModelStateCodec.decode(prefs.all))

    override val states: StateFlow<Map<String, ModelUserState>> = _states.asStateFlow()

    override fun setFavorite(modelId: String, favorite: Boolean) {
        update(modelId) { it.copy(isFavorite = favorite) }
    }

    override fun markUsed(modelId: String, at: Long) {
        update(modelId) { it.copy(lastUsedAt = at) }
    }

    private fun update(modelId: String, transform: (ModelUserState) -> ModelUserState) {
        val held = _states.value[modelId] ?: ModelUserState(modelId = modelId)
        val next = transform(held)
        if (next == held) return
        _states.value = _states.value + (modelId to next)
        write(_states.value)
    }

    private fun write(states: Map<String, ModelUserState>) {
        // clear() first keeps the file authoritative: a favorite turned off
        // cannot linger as a key from a previous write and reappear on restart.
        val editor = prefs.edit().clear()
        ModelStateCodec.encode(states).forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                null -> editor.remove(key)
                else -> error(
                    "ModelStateCodec.encode produced an unsupported type for '$key': " +
                        value::class.qualifiedName
                )
            }
        }
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "octa_model_state"
    }
}
