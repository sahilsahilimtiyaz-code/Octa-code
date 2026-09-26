package com.sahil.octacode.core.runtime

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * R1 runtime provisioning: a PINNED description of everything the app may download.
 *
 * Every artifact carries the SHA-256 of the exact bytes it will receive, so the app
 * can never execute (or later unpack) a stream it did not verify. Pins are produced
 * by tools/gen_runtime_manifest.py — that script is the provenance for every URL and
 * hash below, and it re-checks each pin against its publisher's own checksum file
 * when it runs.
 *
 * The manifest ships INSIDE the APK. Nothing about what gets downloaded is fetched
 * from the network, so a compromised mirror cannot widen the trust boundary.
 */
@Serializable
data class RuntimeArtifact(
    val id: String,
    val version: String,
    val size: Long,
    val sha256: String,
    val path: String,
    val provides: List<String> = emptyList(),
    /**
     * Absolute URL for an artifact that does not come from [RuntimeManifest.baseUrl].
     * Null means baseUrl + [path], which is every Termux package.
     *
     * The glibc root filesystem states its own URL for the same reason it states
     * its own hash: it is fetched from Canonical, not from the pinned Termux
     * mirror, and pretending otherwise would either 404 or quietly start pulling
     * Ubuntu from a host nobody chose.
     */
    val url: String? = null,
    /** What these bytes are, which is what decides how they are unpacked. */
    val kind: Kind = Kind.DEB
) {
    /**
     * What these bytes are, which is what decides how they are unpacked.
     *
     * The three are not interchangeable: a .deb wants a Termux prefix stripped
     * and relocated into the bionic prefix, a root filesystem is already laid
     * out and must stay whole, and an executable is a single file that belongs
     * in the *guest* — a glibc binary dropped into the bionic prefix would be
     * a 60 MB file that cannot run anywhere.
     */
    enum class Kind { DEB, ROOTFS, EXECUTABLE }

    /** Local filename used in the on-device cache (versioned → upgrades don't collide). */
    val cacheName: String get() = "${id}_${version}.$extension"

    /**
     * Container suffix, read from the path instead of assumed.
     *
     * A hardcoded ".deb" would name a 29 MB gzipped root filesystem
     * "glibc-rootfs_<version>.deb". Nothing collides with that today, which is
     * exactly why it would survive review and then mislead whoever reads the
     * cache directory after that.
     */
    private val extension: String
        get() = when {
            path.endsWith(".tar.gz", ignoreCase = true) -> "tar.gz"
            path.endsWith(".tar.xz", ignoreCase = true) -> "tar.xz"
            else -> path.substringAfterLast('.', "deb").ifBlank { "deb" }
        }

    /** Commands this package will make available once unpacked (plus its own id). */
    val commandNames: List<String> get() = (listOf(id) + provides).distinct()

    /**
     * Where this artifact's command lives inside the glibc guest, or null if
     * it is not a guest executable.
     *
     * `/usr/local/bin` rather than `/usr/bin`: the root filesystem's own
     * binaries belong to the release that shipped them, and writing into it
     * would make an upgraded image a tree assembled from two different ones.
     * It is on the guest PATH, so the result runs by name.
     */
    val guestCommandPath: String?
        get() = if (kind == Kind.EXECUTABLE) "$GUEST_BIN_DIR/$id" else null

    companion object {
        /** Must match the guest PATH built by the proot runner. */
        const val GUEST_BIN_DIR = "usr/local/bin"
    }
}

/**
 * One independently installable slice of the runtime.
 *
 * [items] is the FULL dependency closure, so the group can be installed standalone.
 * Closures overlap (git needs bash), and the app dedupes against what it already
 * holds — the number shown to the user is always the real remaining download.
 */
@Serializable
data class RuntimeGroup(
    val id: String,
    val title: String,
    val description: String,
    val optional: Boolean = false,
    val totalBytes: Long,
    val items: List<RuntimeArtifact>
)

@Serializable
data class RuntimeManifest(
    val schema: Int,
    val name: String,
    val arch: String,
    val abi: String,
    val index: String,
    val baseUrl: String,
    val generated: String,
    val groups: List<RuntimeGroup>,
    val unionBytes: Long,
    val packageCount: Int
) {
    fun group(id: String): RuntimeGroup? = groups.firstOrNull { it.id == id }

    fun urlFor(artifact: RuntimeArtifact): String =
        artifact.url ?: baseUrl.trimEnd('/') + "/" + artifact.path.trimStart('/')

    /** All artifacts, deduped by package id (group closures overlap by design). */
    fun distinctArtifacts(): List<RuntimeArtifact> =
        groups.flatMap { it.items }.distinctBy { it.id }

    /**
     * Exactly what the one-tap install downloads: every non-optional group,
     * counted once.
     *
     * Kept apart from [distinctArtifacts] because the two answer different
     * questions. This is "what will the Install everything button fetch";
     * that is "everything this build knows how to pin". Quoting the second
     * while doing the first would charge the user for 29 MB of glibc root
     * filesystem the button then quietly skips — the app would be pricing
     * work it does not do.
     */
    fun defaultArtifacts(): List<RuntimeArtifact> =
        groups.filterNot { it.optional }.flatMap { it.items }.distinctBy { it.id }

    /** True when the manifest targets this device (arm64-only by policy). */
    fun supportsDevice(deviceAbi: String): Boolean =
        deviceAbi.isBlank() || abi.equals(deviceAbi, ignoreCase = true)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): RuntimeManifest =
            json.decodeFromString(RuntimeManifest.serializer(), text)
    }
}

/** Reads the manifest bundled in APK assets — never downloaded, never remote. */
class BundledRuntimeManifest(private val assets: AssetManager) {
    fun load(): RuntimeManifest =
        assets.open(FILE).bufferedReader().use { RuntimeManifest.parse(it.readText()) }

    companion object {
        const val FILE = "runtime-manifest.json"
    }
}
