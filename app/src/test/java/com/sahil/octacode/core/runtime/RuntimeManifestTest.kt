package com.sahil.octacode.core.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        }
        assertEquals(manifest.packageCount, manifest.distinctArtifacts().size)
        assertTrue(manifest.unionBytes > 0)
    }

    @Test
    fun `a deb comes from the pinned mirror and a rootfs names its own host`() {
        // Two rules, because there are two sources. A .deb resolved against the
        // Termux base URL is the pinning story working. A ROOTFS that did the
        // same would be asking Canonical's path of packages.termux.dev, which
        // does not exist — and "it 404s" is not a check.
        manifest.distinctArtifacts().forEach { artifact ->
            when (artifact.kind) {
                RuntimeArtifact.Kind.DEB -> {
                    assertTrue(
                        "${artifact.id}: a deb must come from the mirror, not name a url",
                        artifact.url == null
                    )
                    assertTrue("${artifact.id}: not a deb path", artifact.path.endsWith(".deb"))
                    assertTrue("${artifact.id}: path must stay under pool/", artifact.path.startsWith("pool/"))
                }
                RuntimeArtifact.Kind.EXECUTABLE -> {
                    val url = artifact.url
                    assertTrue("${artifact.id}: an agent must name its own url", url != null)
                    assertTrue("${artifact.id}: agent url must be https", url!!.startsWith("https://"))
                    assertTrue(
                        "${artifact.id}: agent url must not be built from the Termux base",
                        !url.startsWith(manifest.baseUrl)
                    )
                    // A versioned release URL, never "latest": "latest" would
                    // point at a different archive tomorrow while the pin
                    // still named these bytes, so every fetch would fail the
                    // hash check for no reason the user could see.
                    assertFalse(
                        "${artifact.id}: must pin a version, not latest: $url",
                        url.contains("/releases/latest/")
                    )
                    assertTrue(
                        "${artifact.id}: the url must carry the pinned version: $url",
                        url.contains("/download/v${artifact.version}/")
                    )
                }
                RuntimeArtifact.Kind.ROOTFS -> {
                    val url = artifact.url
                    assertTrue("${artifact.id}: a rootfs must name its own url", url != null)
                    assertTrue("${artifact.id}: rootfs url must be https", url!!.startsWith("https://"))
                    assertTrue(
                        "${artifact.id}: rootfs must not be built from the Termux base",
                        !url.startsWith(manifest.baseUrl)
                    )
                    assertTrue("${artifact.id}: rootfs must be a tarball", artifact.path.endsWith(".tar.gz"))
                }
            }
        }
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
    fun `the rootfs url is its own, not the mirror plus a path`() {
        val rootfs = manifest.group("glibc")!!.items.single()
        assertEquals(rootfs.url, manifest.urlFor(rootfs))
        assertFalse(
            "must not be resolved against the Termux mirror",
            manifest.urlFor(rootfs).startsWith(manifest.baseUrl)
        )
    }

    @Test
    fun `the cache name says what the file actually is`() {
        // A gzipped rootfs stored as "….deb" collides with nothing today and
        // misleads whoever reads the cache directory after that.
        val rootfs = manifest.group("glibc")!!.items.single()
        assertEquals("glibc-rootfs_${rootfs.version}.tar.gz", rootfs.cacheName)
        manifest.distinctArtifacts()
            .filter { it.kind == RuntimeArtifact.Kind.DEB }
            .forEach { assertTrue("${it.id}: ${it.cacheName}", it.cacheName.endsWith(".deb")) }
        manifest.distinctArtifacts()
            .filter { it.kind == RuntimeArtifact.Kind.EXECUTABLE }
            .forEach { assertTrue("${it.id}: ${it.cacheName}", it.cacheName.endsWith(".tar.gz")) }
    }

    @Test
    fun `groups cover the requested runtime with self contained closures`() {
        assertEquals(
            listOf(
                "base", "proot", "node", "python", "devtools", "rust", "glibc", "opencode"
            ),
            manifest.groups.map { it.id }
        )

        // Seeds the user asked for must be present in their own group.
        assertTrue(manifest.group("base")!!.items.any { it.id == "bash" })
        assertTrue(manifest.group("proot")!!.items.any { it.id == "proot" })
        assertTrue(manifest.group("node")!!.items.any { it.id == "nodejs-lts" })
        assertTrue(manifest.group("python")!!.items.any { it.id == "python" })
        assertTrue(manifest.group("devtools")!!.items.any { it.id == "git" })
        assertTrue(manifest.group("glibc")!!.items.any { it.id == "glibc-rootfs" })
        assertTrue(manifest.group("opencode")!!.items.any { it.id == "opencode" })

        // Every group but the glibc root filesystem is part of the default
        // install. Rust was promoted in, so "optional" now has to mean
        // something: the glibc userland is 29 MB that nothing in the default
        // runtime needs, and it is opt-in rather than quietly added to the
        // one-tap button's bill.
        assertEquals(
            listOf(false, false, false, false, false, false, true, true),
            manifest.groups.map { it.optional }
        )
    }

    @Test
    fun `the default install is quoted without the optional group`() {
        val glibc = manifest.group("glibc")!!
        val defaults = manifest.defaultArtifacts()

        assertFalse("the glibc rootfs must not be in the default set", defaults.any { it.id == "glibc-rootfs" })
        assertFalse("nor the agent", defaults.any { it.id == "opencode" })
        // Everything that is not optional is, and the two sets differ by
        // exactly the optional artifacts.
        val optionalOnly = manifest.distinctArtifacts().map { it.id } - defaults.map { it.id }
        assertEquals(setOf("glibc-rootfs", "opencode"), optionalOnly.toSet())
        assertTrue("the glibc group is not free: ${glibc.totalBytes}", glibc.totalBytes > 0)
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

    @Test
    fun `an agent is a guest executable, and says where it goes`() {
        val agent = manifest.group("opencode")!!.items.single()
        assertEquals(RuntimeArtifact.Kind.EXECUTABLE, agent.kind)
        assertEquals("usr/local/bin/opencode", agent.guestCommandPath)
        assertTrue(
            "must land somewhere on the guest PATH",
            com.sahil.octacode.core.shell.ProotRunner.GUEST_PATH.contains("/usr/local/bin")
        )
        // A .deb has no place in the guest, and claiming one would send the
        // installer looking for a path no package will ever occupy.
        assertNull(manifest.group("base")!!.items.first().guestCommandPath)
    }

    @Test
    fun `the glibc rootfs is a real linux userland, not a stub`() {
        // The manifest alone cannot say whether the image is usable, so this
        // only asserts the two things the manifest can know: it is an arm64
        // Ubuntu root, and it is a rootfs kind the installer will unpack
        // somewhere other than the bionic prefix.
        val rootfs = manifest.group("glibc")!!.items.single()
        assertEquals(RuntimeArtifact.Kind.ROOTFS, rootfs.kind)
        assertEquals("aarch64", manifest.arch)
        assertEquals("24.04-20260924", rootfs.version)
        // Sharing a cache filename with a package would let one artifact's
        // bytes be installed as the other.
        val names = manifest.distinctArtifacts().map { it.cacheName }
        assertEquals(names.size, names.distinct().size)
    }
}
