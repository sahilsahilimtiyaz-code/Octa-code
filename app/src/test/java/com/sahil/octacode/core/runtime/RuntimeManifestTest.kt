package com.sahil.octacode.core.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the SHIPPED pin list. If a refresh script ever emits a short hash, a
 * non-pinned URL or a group missing its seed package, this fails the build.
 */
class RuntimeManifestTest {

    private val asset: File = listOf(
        File("src/main/assets/runtime-manifest.json"),
        File("app/src/main/assets/runtime-manifest.json")
    ).firstOrNull { it.isFile }
        ?: error("runtime-manifest.json not found next to the module under test")

    private val manifest: RuntimeManifest by lazy { RuntimeManifest.parse(asset.readText()) }

    @Test
    fun `bundled manifest parses and declares arm64 only`() {
        assertEquals(1, manifest.schema)
        assertEquals("aarch64", manifest.arch)
        assertEquals("arm64-v8a", manifest.abi)
        assertEquals("octa-runtime", manifest.name)
        assertTrue("generated date missing", manifest.generated.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertTrue(manifest.index.startsWith("https://"))
        assertTrue(manifest.baseUrl.startsWith("https://"))
    }

    @Test
    fun `every artifact carries a well formed pin`() {
        val pins = Regex("[0-9a-f]{64}")
        manifest.distinctArtifacts().forEach { artifact ->
            assertTrue("${artifact.id}: bad sha256 '${artifact.sha256}'", pins.matches(artifact.sha256))
            assertTrue("${artifact.id}: size must be > 0", artifact.size > 0)
            assertTrue("${artifact.id}: not a deb path", artifact.path.endsWith(".deb"))
            assertTrue("${artifact.id}: path must stay under pool/", artifact.path.startsWith("pool/"))
        }
        assertEquals(manifest.packageCount, manifest.distinctArtifacts().size)
        assertTrue(manifest.unionBytes > 0)
    }

    @Test
    fun `urls resolve against the pinned mirror`() {
        val bash = manifest.group("base")!!.items.first { it.id == "bash" }
        val url = manifest.urlFor(bash)
        assertEquals(manifest.baseUrl + bash.path, url)
        assertTrue(url.startsWith("https://"))
        assertFalse("double slash in $url", url.contains("//pool"))
    }

    @Test
    fun `groups cover the requested runtime with self contained closures`() {
        assertEquals(listOf("base", "proot", "node", "python", "devtools", "rust"),
            manifest.groups.map { it.id })

        // Seeds the user asked for must be present in their own group.
        assertTrue(manifest.group("base")!!.items.any { it.id == "bash" })
        assertTrue(manifest.group("proot")!!.items.any { it.id == "proot" })
        assertTrue(manifest.group("node")!!.items.any { it.id == "nodejs-lts" })
        assertTrue(manifest.group("python")!!.items.any { it.id == "python" })
        assertTrue(manifest.group("devtools")!!.items.any { it.id == "git" })

        // No group is opt-in: Rust was promoted into the default install set,
        // so every group is fetched by the full-runtime action.
        assertEquals(
            listOf(false, false, false, false, false, false),
            manifest.groups.map { it.optional }
        )
        assertTrue(
            "union size was ${manifest.unionBytes}",
            manifest.unionBytes in 260_000_000L..270_000_000L
        )
    }

    @Test
    fun `closures contain no duplicate packages`() {
        manifest.groups.forEach { group ->
            val ids = group.items.map { it.id }
            assertEquals("duplicates in ${group.id}", ids.size, ids.distinct().size)
            assertEquals(group.totalBytes, group.items.sumOf { it.size })
        }
    }

    @Test
    fun `python exposes python3 to the which() lookup`() {
        val python = manifest.group("python")!!.items.first { it.id == "python" }
        assertEquals(listOf("python3"), python.provides)
        assertTrue(python.commandNames.contains("python3"))
    }

    @Test
    fun `device support check rejects other abis`() {
        assertTrue(manifest.supportsDevice("arm64-v8a"))
        assertFalse(manifest.supportsDevice("armeabi-v7a"))
        assertTrue("unknown ABI should not hard-block", manifest.supportsDevice(""))
    }
}
