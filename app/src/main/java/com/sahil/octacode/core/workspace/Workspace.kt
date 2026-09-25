package com.sahil.octacode.core.workspace

/**
 * A folder the agent works in.
 *
 * [uri] is the SAF `content://` the user actually chose through
 * ACTION_OPEN_DOCUMENT_TREE — never a typed path, because a typed path is a
 * string the app has no right to read. [persisted] records whether we still
 * hold the grant, so a workspace whose permission was revoked can say so on
 * the row instead of failing silently the first time the agent touches it.
 */
data class Workspace(
    val id: String,
    val displayName: String,
    val uri: String,
    val runtimeId: String? = null,
    val lastUsedAt: Long? = null,
    val isFavorite: Boolean = false,
    /** True once takePersistableUriPermission has succeeded for [uri]. */
    val persisted: Boolean = false,
)
