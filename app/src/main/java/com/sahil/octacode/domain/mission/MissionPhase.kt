package com.sahil.octacode.domain.mission

/**
 * M3a: the fixed 16-phase pipeline (spec M3).
 * Order is the contract — engine advances only via [next] / explicit set.
 * Each phase must be observable, cancellable, recoverable, persisted.
 */
enum class MissionPhase(
    val index: Int,
    val title: String,
    val requiresApproval: Boolean,
    val maySkip: Boolean
) {
    UNDERSTAND_TASK(1, "Understand task", requiresApproval = false, maySkip = false),
    DETECT_PROJECT(2, "Detect project type", requiresApproval = false, maySkip = false),
    SELECT_TEAM(3, "Select agent team", requiresApproval = false, maySkip = false),
    SELECT_PROVIDER(4, "Select provider/runtime", requiresApproval = false, maySkip = false),
    PLAN_APPROACH(5, "Plan approach", requiresApproval = false, maySkip = false),
    USER_CHECKPOINT(6, "User checkpoint", requiresApproval = true, maySkip = false),
    IMPLEMENT(7, "Implement changes", requiresApproval = false, maySkip = false),
    REVIEW_DIFF(8, "Review diff", requiresApproval = false, maySkip = false),
    FORMAT(9, "Format code", requiresApproval = false, maySkip = true),
    ANALYZE_IMPACT(10, "Analyze impact", requiresApproval = false, maySkip = false),
    TEST(11, "Test changes", requiresApproval = false, maySkip = true),
    BUILD(12, "Build", requiresApproval = true, maySkip = true),
    INSTALL(13, "Install APK", requiresApproval = true, maySkip = true),
    VERIFY(14, "Verify success", requiresApproval = false, maySkip = false),
    ROLLBACK(15, "Rollback if failed", requiresApproval = false, maySkip = true),
    COMPLETE(16, "Mission complete", requiresApproval = false, maySkip = false);

    fun next(): MissionPhase? = entries.firstOrNull { it.index == index + 1 }

    fun prev(): MissionPhase? = entries.firstOrNull { it.index == index - 1 }

    companion object {
        val ORDERED: List<MissionPhase> = entries.sortedBy { it.index }
        const val TOTAL: Int = 16

        fun fromIndex(index: Int): MissionPhase? =
            entries.firstOrNull { it.index == index }

        /**
         * Phases that hard-gate on autonomy (spec: dangerous actions need policy).
         * ASK/BALANCED require explicit approval; GUIDED auto-approves non-install;
         * HIGH_AUTONOMY auto-approves all approval phases.
         */
        fun approvalRequired(phase: MissionPhase, autonomy: com.sahil.octacode.core.capability.AutonomyLevel): Boolean {
            if (!phase.requiresApproval) return false
            return when (autonomy) {
                com.sahil.octacode.core.capability.AutonomyLevel.ASK -> true
                com.sahil.octacode.core.capability.AutonomyLevel.BALANCED ->
                    phase == INSTALL || phase == USER_CHECKPOINT
                com.sahil.octacode.core.capability.AutonomyLevel.GUIDED ->
                    phase == INSTALL
                com.sahil.octacode.core.capability.AutonomyLevel.HIGH_AUTONOMY -> false
            }
        }
    }
}
