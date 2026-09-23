package com.sahil.octacode.data.mission

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// M3a Room schema v1 — source of truth for crash recovery / resume.

@Entity(
    tableName = "missions",
    indices = [Index(value = ["status"]), Index(value = ["updatedAt"])]
)
data class MissionEntity(
    @PrimaryKey val id: String,
    val goal: String,
    val projectPath: String,
    val projectType: String,
    val providerId: String,
    val autonomy: String,
    val status: String,
    val currentPhaseIndex: Int?,
    val failureReason: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "phase_runs",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["missionId"]), Index(value = ["missionId", "phaseIndex"])]
)
data class PhaseRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val phaseIndex: Int,
    val status: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val outputSummary: String?,
    val errorReason: String?
)

@Entity(
    tableName = "phase_events",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["missionId"]), Index(value = ["missionId", "timestamp"])]
)
data class PhaseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val phaseIndex: Int,
    val timestamp: Long,
    val kind: String,
    val payloadJson: String
)

@Entity(
    tableName = "mission_diffs",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["missionId"])]
)
data class MissionDiffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val phaseIndex: Int,
    val path: String,
    val beforeHash: String,
    val afterHash: String,
    val patchText: String,
    val decision: String
)
