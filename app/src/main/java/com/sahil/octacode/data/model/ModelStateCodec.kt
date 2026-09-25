package com.sahil.octacode.data.model

import com.sahil.octacode.core.model.ModelUserState

/**
 * Maps model state to and from `SharedPreferences`.
 *
 * Pure and Context-free so the round trip is pinned by a unit test rather than
 * trusted: an encoding that drops favorites would look exactly like "I never
 * starred that model" until the user starred it twice.
 *
 * One key per fact, prefixed, rather than one serialized blob — SharedPreferences
 * gives no partial update on a blob, so a "mark used" during a stream would
 * rewrite every favorite at the same time.
 */
internal object ModelStateCodec {

    private const val FAVORITE_PREFIX = "favorite|"
    private const val USED_PREFIX = "used|"

    fun encode(states: Map<String, ModelUserState>): Map<String, Any?> {
        if (states.isEmpty()) return emptyMap()
        val out = HashMap<String, Any?>(states.size * 2)
        for ((id, state) in states) {
            out[FAVORITE_PREFIX + id] = state.isFavorite
            state.lastUsedAt?.let { out[USED_PREFIX + id] = it }
        }
        return out
    }

    /**
     * Tolerates what it did not write. A key from a future version, or a value
     * of the wrong type, is skipped rather than thrown on — prefs are the one
     * place a hand-edit or an old release can legitimately leave debris.
     *
     * Each half of a model's state is decoded independently and merged onto
     * whatever is already held, because the two facts are written under two
     * different keys and arrive in whatever order the preferences happen to
     * enumerate them in.
     */
    fun decode(all: Map<String, *>): Map<String, ModelUserState> {
        if (all.isEmpty()) return emptyMap()
        val byId = HashMap<String, ModelUserState>()
        for ((key, value) in all) {
            when {
                key.startsWith(FAVORITE_PREFIX) -> {
                    val favorite = value as? Boolean ?: continue
                    val id = key.removePrefix(FAVORITE_PREFIX)
                    val held = byId[id] ?: ModelUserState(modelId = id)
                    byId[id] = held.copy(isFavorite = favorite)
                }
                key.startsWith(USED_PREFIX) -> {
                    val at = (value as? Number)?.toLong() ?: continue
                    val id = key.removePrefix(USED_PREFIX)
                    val held = byId[id] ?: ModelUserState(modelId = id)
                    byId[id] = held.copy(lastUsedAt = at)
                }
            }
        }
        return byId
    }
}
