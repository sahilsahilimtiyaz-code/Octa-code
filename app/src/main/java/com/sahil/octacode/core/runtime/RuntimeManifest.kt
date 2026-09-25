package com.sahil.octacode.core.runtime

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * R1 runtime provisioning: a PINNED description of everything the app may download.
 *
 * Every artifact carries the SHA-256 of the exact bytes it will receive, so the app
 * can never execute (or later unpack) a stream it did not verify. Pins are produced
 * by tools/gen_runtime_manifest.py from the live Termux apt index — that script is
 * the provenance for every URL/hash below.
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
    val provides: List<String> = emptyList()
) {
    /** Local filename used in the on-device cache (versioned → upgrades don't collide). */
    val cacheName: String get() = "${id}_${version}.deb"

    /** Commands this package will make available once unpacked (plus its own id). */
    val commandNames: List<String> get() = (listOf(id) + provides).distinct()
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
        baseUrl.trimEnd('/') + "/" + artifact.path.trimStart('/')

    /** All artifacts, deduped by package id (group closures overlap by design). */
    fun distinctArtifacts(): List<RuntimeArtifact> =
        groups.flatMap { it.items }.distinctBy { it.id }

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
