package com.sahil.octacode.data.workspace

import com.sahil.octacode.core.workspace.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCodecTest {

    @Test
    fun `round trip keeps every field of every workspace`() {
        val workspaces = listOf(
            Workspace(
                id = "a-1",
                displayName = "Documents",
                uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
                runtimeId = "node",
                lastUsedAt = 1_700_000_000_000L,
                isFavorite = true,
                persisted = true,
            ),
            Workspace(
                id = "b-2",
                displayName = "Download",
                uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
                runtimeId = null,
                lastUsedAt = null,
                isFavorite = false,
                persisted = false,
            ),
        )

        assertEquals(workspaces, WorkspaceCodec.decode(WorkspaceCodec.encode(workspaces)))
    }

    @Test
    fun `an empty list encodes to nothing rather than to placeholders`() {
        assertTrue(WorkspaceCodec.encode(emptyList()).isEmpty())
        // Explicit type: decode's `Map<String, *>` leaves the compiler nothing
        // to infer from an untyped emptyMap.
        assertTrue(WorkspaceCodec.decode(emptyMap<String, Any?>()).isEmpty())
    }

    @Test
    fun `a record with no uri is dropped rather than given one`() {
        // A workspace without a location is not a folder the app could open.
        // Inventing a URI here would be inventing access to something nobody
        // chose, which is worse than showing one row fewer.
        val decoded = WorkspaceCodec.decode(
            mapOf<String, Any?>(
                "ws.name|a" to "Documents",
                "ws.fav|a" to true,
            ),
        )

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `a record with no name is dropped`() {
        val decoded = WorkspaceCodec.decode(
            mapOf<String, Any?>("ws.uri|a" to "content://example/tree/primary%3ADocuments"),
        )

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `a wrong typed field falls back without losing the workspace`() {
        // Prefs are the one place a hand-edit or an old release can leave
        // debris; refusing the whole list over one bad value would hide every
        // folder the user still has.
        val decoded = WorkspaceCodec.decode(
            mapOf<String, Any?>(
                "ws.name|a" to "Documents",
                "ws.uri|a" to "content://example/tree/primary%3ADocuments",
                "ws.used|a" to "yesterday",
                "ws.fav|a" to "yes",
            ),
        )

        val one = decoded.single()
        assertEquals("Documents", one.displayName)
        assertNull(one.lastUsedAt)
        assertFalse(one.isFavorite)
    }

    @Test
    fun `keys written by a future version are ignored`() {
        val decoded = WorkspaceCodec.decode(
            mapOf<String, Any?>(
                "ws.name|a" to "Documents",
                "ws.uri|a" to "content://example/tree/primary%3ADocuments",
                "ws.color|a" to "#00D4FF",
                "unrelated.key" to 1,
            ),
        )

        assertEquals(1, decoded.size)
        assertEquals("Documents", decoded.single().displayName)
    }

    @Test
    fun `decode orders by id so rows cannot shuffle between reads`() {
        // Preferences enumerate in no particular order. Without an order of
        // its own, decode would hand the UI a different sequence on every
        // read, and rows would move between recompositions.
        fun record(id: String): Map<String, Any?> = mapOf(
            "ws.name|$id" to "Workspace $id",
            "ws.uri|$id" to "content://example/tree/$id",
        )

        val decoded = WorkspaceCodec.decode(record("z") + record("a") + record("m"))

        assertEquals(listOf("a", "m", "z"), decoded.map { it.id })
    }

    @Test
    fun `a folder that was never used writes no used key`() {
        val encoded = WorkspaceCodec.encode(
            listOf(
                Workspace(
                    id = "a",
                    displayName = "Documents",
                    uri = "content://example/tree/primary%3ADocuments",
                ),
            ),
        )

        assertFalse(encoded.containsKey("ws.used|a"))
        assertFalse(encoded.containsKey("ws.runtime|a"))
        // The two facts a row cannot exist without are always written.
        assertTrue(encoded.containsKey("ws.name|a"))
        assertTrue(encoded.containsKey("ws.uri|a"))
    }

    @Test
    fun `an id containing the separator still round trips`() {
        // Ids are internal, but a separator inside one must not truncate it
        // into a second, unrelated record.
        val workspaces = listOf(
            Workspace(
                id = "ws|pipe",
                displayName = "Documents",
                uri = "content://example/tree/primary%3ADocuments",
            ),
        )

        assertEquals(workspaces, WorkspaceCodec.decode(WorkspaceCodec.encode(workspaces)))
    }
}
