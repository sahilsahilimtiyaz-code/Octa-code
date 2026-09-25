package com.sahil.octacode.domain.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome of the retention pass this process ran, if any. */
sealed interface RetentionOutcome {

    /** Nothing has run yet — the normal case for a launch where no rule fired. */
    data object NotRun : RetentionOutcome

    /** The pass ran and moved these conversations out of the active list. */
    data class Archived(val sessions: List<ChatSession>) : RetentionOutcome

    /**
     * The pass was attempted and failed.
     *
     * Kept as a state rather than a log line because the alternative is a
     * setting that says "auto-archive" while silently not archiving — the user
     * would have no way to tell the difference between "nothing was stale" and
     * "the rule never ran".
     */
    data class Failed(val reason: String) : RetentionOutcome
}

/**
 * What the startup retention pass did, for as long as this process lives.
 *
 * [SessionRetention.apply] deliberately returns what it changed so the caller
 * can report it. This is that report: the Sessions page shows it above the
 * Archived list, so conversations that left the active list are explained at
 * the moment someone goes looking for them rather than left to be discovered
 * missing.
 *
 * Not persisted — it describes one pass, not a history. A record of every past
 * pass would need its own schema and would outlive its usefulness.
 */
class RetentionReport {

    private val _outcome = MutableStateFlow<RetentionOutcome>(RetentionOutcome.NotRun)
    val outcome: StateFlow<RetentionOutcome> = _outcome.asStateFlow()

    fun publish(result: RetentionOutcome) {
        _outcome.value = result
    }

    /** Dismissed by the reader; the archived conversations themselves remain. */
    fun clear() {
        _outcome.value = RetentionOutcome.NotRun
    }
}
