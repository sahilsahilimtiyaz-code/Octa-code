package com.sahil.octacode.domain.mission

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditProtocolTest {

    @Test
    fun `parses single full-file replacement block`() {
        val out = """
            ===OCTA_EDIT===
            path: src/Main.kt
            ===CONTENT===
            fun main() {}
            ===END===
        """.trimIndent()
        val edits = EditProtocol.parse(out)
        assertEquals(1, edits.size)
        assertEquals("src/Main.kt", edits[0].path)
        assertEquals("fun main() {}", edits[0].content)
    }

    @Test
    fun `parses multiple blocks and preserves inner newlines`() {
        val out = edit("a.txt", "line1\nline2\n") + edit("b/c.txt", "x")
        val edits = EditProtocol.parse(out)
        assertEquals(listOf("a.txt", "b/c.txt"), edits.map { it.path })
        assertTrue(edits[0].content.contains("line2"))
        assertEquals("x", edits[1].content)
    }

    @Test
    fun `ignores prose without protocol markers`() {
        assertTrue(EditProtocol.parse("I will not edit files today").isEmpty())
    }

    @Test
    fun `drops block missing END marker`() {
        val out = "===OCTA_EDIT===\npath: x.txt\n===CONTENT===\npartial"
        assertTrue(EditProtocol.parse(out).isEmpty())
    }

    private fun edit(path: String, content: String): String =
        "===OCTA_EDIT===\npath: $path\n===CONTENT===\n$content\n===END===\n"
}

class FileDiffTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sha256 is stable and 64 hex chars`() {
        val a = FileDiff.sha256("hello")
        val b = FileDiff.sha256("hello")
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertTrue(FileDiff.sha256("hello") != FileDiff.sha256("hell0"))
    }

    @Test
    fun `path safety rejects traversal and absolute paths`() {
        assertTrue(FileDiff.isSafeRelativePath("app/src/Main.kt"))
        assertFalse(FileDiff.isSafeRelativePath("../evil"))
        assertFalse(FileDiff.isSafeRelativePath("/etc/passwd"))
        assertFalse(FileDiff.isSafeRelativePath(""))
        assertFalse(FileDiff.isSafeRelativePath("a/../../b"))
        assertFalse(FileDiff.isSafeRelativePath("C:\\windows\\system32"))
    }

    @Test
    fun `resolveSafe refuses escape from project root`() {
        val root = tmp.newFolder("proj")
        assertNull(FileDiff.resolveSafe(root, "../outside.txt"))
        val ok = FileDiff.resolveSafe(root, "src/Ok.kt")
        assertEquals(File(root, "src/Ok.kt"), ok)
    }

    @Test
    fun `unified patch marks new files and full replace`() {
        val newPatch = FileDiff.unifiedPatch("n.txt", null, "a\nb")
        assertTrue(newPatch.contains("new file"))
        assertTrue(newPatch.contains("+a"))

        val repl = FileDiff.unifiedPatch("f.txt", "old", "new")
        assertTrue(repl.contains("--- a/f.txt"))
        assertTrue(repl.contains("+++ b/f.txt"))
        assertTrue(repl.contains("-old"))
        assertTrue(repl.contains("+new"))

        assertEquals("", FileDiff.unifiedPatch("f.txt", "same", "same"))
    }

    @Test
    fun `list project tree skips build noise`() {
        val root = tmp.newFolder("tree")
        File(root, "src").mkdirs()
        File(root, "src/Main.kt").writeText("x")
        File(root, "build").mkdirs()
        File(root, "build/out.bin").writeText("bin")
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/pkg.js").writeText("js")
        File(root, "package.json").writeText("{}")
        val tree = FileDiff.listProjectTree(root)
        assertTrue(tree.contains("src/Main.kt"))
        assertTrue(tree.contains("package.json"))
        assertFalse(tree.any { it.startsWith("build/") })
        assertFalse(tree.any { it.startsWith("node_modules/") })
    }
}

class InMemorySnapshotStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `restore rewrites originals and deletes created files`() = kotlinx.coroutines.runBlocking {
        val project = tmp.newFolder("project")
        val store = InMemorySnapshotStore()
        File(project, "Keep.kt").writeText("original")

        // Capture: Keep exists, Brand does not
        store.capture("m1", project, listOf("Keep.kt", "Brand.kt"))
        File(project, "Keep.kt").writeText("mutated")
        File(project, "Brand.kt").writeText("brand new")

        val result = store.restore("m1", project)
        assertEquals("original", File(project, "Keep.kt").readText())
        assertFalse(File(project, "Brand.kt").exists())
        assertEquals(1, result.restored)
        assertEquals(1, result.deletedCreated)
    }

    @Test
    fun `hasSnapshot false when nothing captured`() = kotlinx.coroutines.runBlocking {
        val store = InMemorySnapshotStore()
        assertFalse(store.hasSnapshot("nope"))
    }
}
