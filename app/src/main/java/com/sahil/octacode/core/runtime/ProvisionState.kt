package com.sahil.octacode.core.runtime

/** Live progress of a single group install. All byte counts are real, never estimated. */
data class GroupProgress(
    val groupId: String,
    val itemId: String,
    /** 1-based position of the current artifact within this run. */
    val itemIndex: Int,
    val itemCount: Int,
    val itemBytesDone: Long,
    val itemBytesTotal: Long,
    /** Bytes verified so far across the whole group run. */
    val groupBytesDone: Long,
    val groupBytesTotal: Long
) {
    val itemFraction: Float
        get() = if (itemBytesTotal <= 0) 0f else (itemBytesDone.toFloat() / itemBytesTotal).coerceIn(0f, 1f)

    val groupFraction: Float
        get() = if (groupBytesTotal <= 0) 0f else (groupBytesDone.toFloat() / groupBytesTotal).coerceIn(0f, 1f)
}

/** Why a group install stopped. Always shown to the user — no silent failure. */
data class ProvisionError(
    /** null = a problem that belongs to no single group (e.g. a damaged ledger). */
    val groupId: String?,
    val itemId: String?,
    val reason: String,
    val retryable: Boolean
)

data class ProvisionerState(
    val busy: Boolean = false,
    val activeGroupId: String? = null,
    val progress: GroupProgress? = null,
    /** artifact id → verification record (mirrors the ledger). */
    val fetched: Map<String, ArtifactRecord> = emptyMap(),
    val lastError: ProvisionError? = null
) {
    fun isFetched(artifactId: String): Boolean = fetched.containsKey(artifactId)

    /** Verified AND unpacked — strictly stronger than [isFetched], never implied by it. */
    fun isInstalled(artifactId: String): Boolean = fetched[artifactId]?.isInstalled == true

    fun installedCount(): Int = fetched.values.count { it.isInstalled }

    fun installedCount(group: RuntimeGroup): Int = group.items.count { isInstalled(it.id) }

    /** Every artifact of the group is unpacked, not merely downloaded. */
    fun isInstalled(group: RuntimeGroup): Boolean = installedCount(group) == group.items.size

    fun fetchedCount(group: RuntimeGroup): Int = group.items.count { fetched.containsKey(it.id) }

    fun totalFetchedBytes(): Long = fetched.values.sumOf { it.bytes }

    /** Bytes still to download for a group — what the button will actually cost. */
    fun pendingBytes(group: RuntimeGroup): Long =
        group.items.filterNot { fetched.containsKey(it.id) }.sumOf { it.size }

    fun pendingCount(group: RuntimeGroup): Int = group.items.count { !fetched.containsKey(it.id) }

    fun isComplete(group: RuntimeGroup): Boolean = pendingCount(group) == 0

    /**
     * Cost of installing EVERYTHING still missing, counting packages shared
     * between group closures once — this is what the full-install button charges.
     */
    fun pendingBytes(manifest: RuntimeManifest): Long =
        manifest.distinctArtifacts().filterNot { fetched.containsKey(it.id) }.sumOf { it.size }

    fun pendingCount(manifest: RuntimeManifest): Int =
        manifest.distinctArtifacts().count { !fetched.containsKey(it.id) }

    fun isComplete(manifest: RuntimeManifest): Boolean = pendingCount(manifest) == 0
}
