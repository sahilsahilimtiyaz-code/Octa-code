package com.sahil.octacode.data.workspace

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.sahil.octacode.core.workspace.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The folders the agent works in, held as preferences rather than a database.
 *
 * A workspace is a handful of strings the user picked once; there is no
 * relation to model or history worth a schema, and keeping it here means the
 * store is a `StateFlow` the picker screen can render off directly — adding a
 * folder has to show up in the same frame, not on the next screen open.
 *
 * The grant is the part that cannot be trusted from disk. A workspace is a
 * `content://` tree the user handed us through ACTION_OPEN_DOCUMENT_TREE, and
 * Android lets them revoke it from outside the app entirely. So
 * [persisted] is re-derived from [android.content.ContentResolver]
 * .persistedUriPermissions on every load instead of believed from the file: a
 * row that claimed to be usable while its permission had been withdrawn would
 * be the failure this app refuses to ship — the folder would look present and
 * then fail the first time anyone opened it.
 *
 * Not encrypted. These are folder paths, not secrets; API keys stay in
 * `CredentialStore`.
 */
class WorkspaceStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _workspaces = MutableStateFlow(load())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    /**
     * Which folder new conversations are recorded against, or null when none
     * is chosen — the state [com.sahil.octacode.core.profile.RunProfile] was
     * written to allow, and until now the only state that ever occurred,
     * because nothing could set it.
     *
     * Held as an id rather than a snapshot of the workspace, so the row a
     * caller reads cannot drift from the list it belongs to after a rename,
     * a star or a grant being withdrawn behind our back.
     */
    private val _activeId = MutableStateFlow(readActive())
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    /**
     * Adds the tree the user just chose and takes the persistable grant for
     * it. Returns whether that grant succeeded, because failing to take it is
     * the one case where the row exists but would not work — the caller needs
     * to be able to say so rather than let the workspace appear healthy.
     *
     * Re-adding the same tree replaces it instead of duplicating it: two rows
     * pointing at one folder would double every "remove" and make the list
     * disagree with itself about what is where.
     */
    fun add(uri: Uri): Boolean {
        val tree = uri.toString()
        val granted = takeGrant(uri)
        // Picking a tree that is already listed re-grants it rather than
        // creating a second row. Keeping the old id is what stops the choice
        // from being orphaned: a fresh UUID here would leave activeId naming
        // a record that no longer exists, and the selection would silently
        // read back as none. The star and last-used stamp survive for the
        // same reason — they describe the folder, which has not changed.
        val previous = _workspaces.value.firstOrNull { it.uri == tree }
        val workspace = Workspace(
            id = previous?.id ?: UUID.randomUUID().toString(),
            displayName = nameFor(uri),
            uri = tree,
            runtimeId = previous?.runtimeId,
            lastUsedAt = previous?.lastUsedAt,
            isFavorite = previous?.isFavorite ?: false,
            persisted = granted,
        )
        setAll(_workspaces.value.filter { it.uri != tree } + workspace)
        return granted
    }

    /** Forgets a folder and releases its grant — access nobody listed should be held. */
    fun remove(id: String) {
        val gone = _workspaces.value.firstOrNull { it.id == id } ?: return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                // Workspace.uri is stored as a string — it has to be, to
                // round-trip through SharedPreferences — so parse it back
                // at the one boundary that wants a Uri.
                Uri.parse(gone.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // Clearing the choice first, so the write that follows persists both
        // facts together. Leaving a dangling id would name a folder that is
        // no longer listed, and every consumer would read it as missing.
        if (_activeId.value == id) _activeId.value = null
        setAll(_workspaces.value.filterNot { it.id == id })
    }

    fun setFavorite(id: String, favorite: Boolean) = update(id) {
        it.copy(isFavorite = favorite)
    }

    /**
     * Drops the choice without touching the list. Worth having separately:
     * [setActive] deliberately refuses an id it is already holding, so using
     * it to unselect would be a call that returns having changed nothing —
     * a tap that silently does nothing.
     */
    fun clearActive() {
        if (_activeId.value == null) return
        _activeId.value = null
        persist()
    }

    /**
     * Chooses the folder this conversation works in, and records the moment
     * as the folder's last use.
     *
     * Deliberately does not go through [setAll]: this writes two facts at
     * once, and a folder whose last-used stamp happened to land on the same
     * millisecond would leave the list unchanged — [setAll] would then find
     * nothing to do and drop the selection on the floor.
     *
     * Selecting a folder that is not listed is refused rather than stored: an
     * id pointing at nothing would render as "no folder selected" while
     * still being truthfully present on disk, which is worse than either.
     */
    fun setActive(id: String) {
        if (_workspaces.value.none { it.id == id }) return
        if (_activeId.value == id) return
        val now = System.currentTimeMillis()
        _activeId.value = id
        _workspaces.value = _workspaces.value.map {
            if (it.id == id) it.copy(lastUsedAt = now) else it
        }
        persist()
    }

    private fun update(id: String, transform: (Workspace) -> Workspace) {
        val held = _workspaces.value.firstOrNull { it.id == id } ?: return
        val next = transform(held)
        if (next == held) return
        setAll(_workspaces.value.map { if (it.id == id) next else it })
    }

    private fun setAll(next: List<Workspace>) {
        if (next == _workspaces.value) return
        _workspaces.value = next
        persist()
    }

    /**
     * Re-reads from disk and checks every folder against the live grant list.
     * Decoding gives us what was true when we last wrote; only the resolver
     * knows what is true now.
     */
    private fun load(): List<Workspace> = WorkspaceCodec.decode(prefs.all)
        .map { it.copy(persisted = holdsGrant(it.uri)) }

    /**
     * The stored choice, dropped if the folder it names is gone. An id with
     * nothing behind it would otherwise read as a selection the app cannot
     * show — the chat pill and the composer's folder chip would both stay
     * silent while disk insisted a folder was chosen.
     */
    private fun readActive(): String? = prefs.getString(KEY_ACTIVE, null)
        ?.takeIf { id -> _workspaces.value.any { it.id == id } }

    private fun holdsGrant(uri: String): Boolean = runCatching {
        appContext.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == uri }
    }.getOrDefault(false)

    private fun takeGrant(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    /**
     * Turns the tree URI into something a person recognises. Provider trees
     * are addressed like `.../tree/primary:Documents`, where the part after
     * the last colon is the folder's own name; falling back to the raw
     * segment and then to "Folder" keeps the row readable even for a provider
     * that does not follow that shape.
     */
    private fun nameFor(uri: Uri): String {
        val segment = uri.lastPathSegment ?: return "Folder"
        val withoutScheme = segment.substringAfterLast(':')
        return withoutScheme.ifBlank { segment }
    }

    /**
     * Writes both facts as one transaction. Clearing first keeps the file
     * authoritative: a removed folder cannot linger as keys from a previous
     * write and reappear on restart, and the selection cannot outlive it
     * either — they are written together or not at all.
     */
    private fun persist() {
        val editor = prefs.edit().clear()
        WorkspaceCodec.encode(_workspaces.value).forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                null -> editor.remove(key)
                else -> error(
                    "WorkspaceCodec.encode produced an unsupported type for '$key': " +
                        value::class.qualifiedName,
                )
            }
        }
        // Not encoded by WorkspaceCodec: the choice is one value about the
        // list as a whole, not a fact belonging to any single workspace.
        _activeId.value?.let { editor.putString(KEY_ACTIVE, it) }
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "octa_workspaces"

        /** Sits outside the codec's `ws.*` namespace so decode ignores it. */
        const val KEY_ACTIVE = "active_workspace"
    }
}
