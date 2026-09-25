package com.sahil.octacode.core.runtime.extract

/**
 * What should happen to one archive path.
 *
 * Three outcomes, not two, because collapsing them hides a real distinction:
 * the `./data/data/com.termux/files/...` chain that leads to every package's
 * prefix is present in every single package and is perfectly normal, whereas a
 * path that simply does not belong in this install is worth counting and
 * showing. Reporting both as "skipped" would either alarm the user about
 * nothing or bury a genuine anomaly in noise.
 */
sealed interface PathMapping {
    /** Put it here, under the install root. */
    data class Place(val relative: String) : PathMapping

    /** Package scaffolding we deliberately do not materialise. */
    object Ignore : PathMapping

    /** Outside the prefix, or otherwise refused: dropped and counted. */
    object Refuse : PathMapping
}
