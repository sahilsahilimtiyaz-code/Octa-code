package com.sahil.octacode.domain.mission

// M3a: mission lifecycle. Persisted — never inferred from UI state.
enum class MissionStatus {
    DRAFT,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETE,
    CANCELLED
}

// Per-phase outcome inside a mission.
enum class PhaseStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELLED
}

// Diff review decision (phase 8 / apply gate). PENDING blocks mission advance past review when required.
enum class DiffDecision {
    PENDING,
    ACCEPTED,
    REJECTED
}

// Append-only event kinds written to phase_events before/after transitions.
enum class EventKind {
    PHASE_STARTED,
    PHASE_PROGRESS,
    PHASE_SUCCEEDED,
    PHASE_FAILED,
    PHASE_SKIPPED,
    PHASE_CANCELLED,
    CHECKPOINT_REQUIRED,
    CHECKPOINT_APPROVED,
    CHECKPOINT_REJECTED,
    MISSION_PAUSED,
    MISSION_RESUMED,
    MISSION_COMPLETED,
    MISSION_FAILED,
    MISSION_CANCELLED,
    ROLLBACK_PERFORMED
}
