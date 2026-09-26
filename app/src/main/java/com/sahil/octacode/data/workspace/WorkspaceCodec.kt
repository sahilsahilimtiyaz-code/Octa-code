package com.sahil.octacode.data.workspace

import com.sahil.octacode.core.workspace.Workspace

/**
 * Maps workspaces to and from `SharedPreferences`.
 *
 * Pure and Context-free so the round trip is pinned by a unit test rather than
 * trusted: an encoding that dropped a URI would look exactly like "I never
 * chose that folder" until the user chose it twice.
 *
 * One prefixed key per fact rather than one serialized blob, for the reason
 * [com.sahil.octacode.data.model.ModelStateCodec] gives: SharedPreferences has
 * no partial update on a blob, so starring one workspace would otherwise
 * rewrite every other workspace's URI in the same operation.
 *
 * [Workspace.persisted] is written even though the store re-derives it from
 * the real grant list on load. Keeping the codec a faithful serializer means
 * `decode(encode(x)) == x` holds for every field; correctness at runtime comes
 * from that verification, not from withholding a value here.
 */
internal object WorkspaceCodec {

    private const val NAME = "ws.name|"
    private const val URI = "ws.uri|"
    private const val RUNTIME = "ws.runtime|"
    private const val USED = "ws.used|"
    private const val FAVORITE = "ws.fav|"
    private const val PERSISTED = "ws.persisted|"

    fun encode(workspaces: List<Workspace>): Map<String, Any?> {
        if (workspaces.isEmpty()) return emptyMap()
        // Six facts per workspace is a reasonable guess at the growth of a
        // map we are about to fill; HashMap tolerates being wrong.
        val out = HashMap<String, Any?>(workspaces.size * 6)
        for (workspace in workspaces) {
            out[NAME + workspace.id] = workspace.displayName
            out[URI + workspace.id] = workspace.uri
            workspace.runtimeId?.let { out[RUNTIME + workspace.id] = it }
            workspace.lastUsedAt?.let { out[USED + workspace.id] = it }
            out[FAVORITE + workspace.id] = workspace.isFavorite
            out[PERSISTED + workspace.id] = workspace.persisted
        }
        return out
    }

    /**
     * Tolerates what it did not write. Unknown keys, values of the wrong type
     * and half-written records are skipped rather than thrown on — prefs are
     * the one place a hand-edit or an old release can legitimately leave
     * debris, and refusing to read them would hide every workspace the user
     * still has.
     *
     * Fields arrive in whatever order the preferences enumerate them in, so
     * each workspace is assembled from a holder rather than read field by
     * field in sequence. One is emitted only once it has the two facts it
     * cannot exist without: a [Workspace.uri] and a [Workspace.displayName].
     * A record missing either is not a folder the app could open, and
     * inventing a URI would be inventing access.
     *
     * The result is sorted by id purely so decode is deterministic.
     * Preferences carry no order of their own, and a list whose order changed
     * between reads would move rows around between recompositions.
     */
    fun decode(all: Map<String, *>): List<Workspace> {
        if (all.isEmpty()) return emptyList()
        val holders = HashMap<String, Holder>()
        for ((key, value) in all) {
            if (!key.startsWith("ws.")) continue
            val split = key.indexOf('|')
            if (split < 0) continue
            val id = key.substring(split + 1)
            if (id.isEmpty()) continue
            val holder = holders.getOrPut(id) { Holder(id) }
            when (key.substring(0, split + 1)) {
                NAME -> (value as? String)?.let { holder.displayName = it }
                URI -> (value as? String)?.let { holder.uri = it }
                RUNTIME -> (value as? String)?.let { holder.runtimeId = it }
                USED -> (value as? Number)?.let { holder.lastUsedAt = it.toLong() }
                FAVORITE -> (value as? Boolean)?.let { holder.isFavorite = it }
                PERSISTED -> (value as? Boolean)?.let { holder.persisted = it }
            }
        }
        return holders.values.mapNotNull { it.build() }.sortedBy { it.id }
    }

    /** Mutable staging for one record; fields land in enumeration order. */
    private class Holder(val id: String) {
        var displayName: String? = null
        var uri: String? = null
        var runtimeId: String? = null
        var lastUsedAt: Long? = null
        var isFavorite: Boolean = false
        var persisted: Boolean = false

        fun build(): Workspace? {
            val name = displayName ?: return null
            val location = uri ?: return null
            return Workspace(
                id = id,
                displayName = name,
                uri = location,
                runtimeId = runtimeId,
                lastUsedAt = lastUsedAt,
                isFavorite = isFavorite,
                persisted = persisted,
            )
        }
    }
}
