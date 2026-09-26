package com.sahil.octacode.data.model

import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.provider.ProviderId

/**
 * Fetched ids, one preference key per provider — `models|GROQ` → the ids Groq
 * returned.
 *
 * The same rule `ModelStateCodec` works to: a preferences file is a set of
 * independently readable facts, not one blob whose shape a future refactor can
 * silently break. A half-written value here costs at most that provider's
 * list; a blob costs every provider's at once, and reads as "no models".
 *
 * Ids only. `displayName` is the id, because `/models` does not supply a name
 * and inventing a prettier one means inventing one that may not match what the
 * user sees on the provider's own console. Nothing else is stored either:
 * `/models` does not report capabilities, so storing a guess would put a
 * fabricated capability badge on a row that then claims it.
 *
 * Pure — no Context, no SharedPreferences type — so the layout can be pinned.
 */
internal object FetchedModelsCodec {

    private const val KEY_PREFIX = "models|"

    /** What to write for one provider. Blank and repeated ids are dropped here. */
    fun entry(provider: ProviderId, ids: List<String>): Pair<String, String> =
        KEY_PREFIX + provider.name to
            ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

    /**
     * Every stored provider's models, in enum order.
     *
     * Iterating [ProviderId.entries] rather than the map is what makes the
     * result deterministic: a key map has no order, so two providers offering
     * the same id would otherwise take turns being the one that resolves.
     *
     * Keys written by anything else are skipped, and a value that is not a
     * String is skipped rather than coerced — one bad entry must not cost the
     * whole file.
     */
    fun decode(all: Map<String, *>): List<ModelDef> {
        val decoded = ArrayList<ModelDef>()
        for (provider in ProviderId.entries) {
            val raw = all[KEY_PREFIX + provider.name] as? String ?: continue
            for (id in raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                decoded += ModelDef(
                    id = id,
                    displayName = id,
                    provider = provider.title,
                    adapter = provider,
                )
            }
        }
        return decoded
    }
}
