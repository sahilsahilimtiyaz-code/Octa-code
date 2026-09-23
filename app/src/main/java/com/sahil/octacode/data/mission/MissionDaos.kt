package com.sahil.octacode.data.mission

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mission: MissionEntity)

    @Update
    suspend fun update(mission: MissionEntity)

    @Upsert
    suspend fun upsert(mission: MissionEntity)

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun getById(id: String): MissionEntity?

    @Query("SELECT * FROM missions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE id = :id")
    fun observeById(id: String): Flow<MissionEntity?>

    @Query("SELECT * FROM missions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MissionEntity>

    // Crash recovery: engine left these RUNNING when process died.
    @Query("SELECT * FROM missions WHERE status = 'RUNNING' ORDER BY updatedAt DESC")
    suspend fun getRecoverable(): List<MissionEntity>

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PhaseRunDao {
    @Upsert
    suspend fun upsert(run: PhaseRunEntity)

    @Query("SELECT * FROM phase_runs WHERE missionId = :missionId ORDER BY phaseIndex ASC")
    suspend fun getForMission(missionId: String): List<PhaseRunEntity>

    @Query("SELECT * FROM phase_runs WHERE missionId = :missionId ORDER BY phaseIndex ASC")
    fun observeForMission(missionId: String): Flow<List<PhaseRunEntity>>

    @Query("DELETE FROM phase_runs WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}

@Dao
interface PhaseEventDao {
    @Insert
    suspend fun insert(event: PhaseEventEntity): Long

    @Query("SELECT * FROM phase_events WHERE missionId = :missionId ORDER BY timestamp ASC, id ASC")
    suspend fun getForMission(missionId: String): List<PhaseEventEntity>

    @Query("SELECT * FROM phase_events WHERE missionId = :missionId ORDER BY timestamp ASC, id ASC")
    fun observeForMission(missionId: String): Flow<List<PhaseEventEntity>>

    @Query("DELETE FROM phase_events WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}

@Dao
interface MissionDiffDao {
    @Upsert
    suspend fun upsert(diff: MissionDiffEntity): Long

    @Query("SELECT * FROM mission_diffs WHERE missionId = :missionId ORDER BY id ASC")
    suspend fun getForMission(missionId: String): List<MissionDiffEntity>

    @Query("UPDATE mission_diffs SET decision = :decision WHERE id = :id")
    suspend fun updateDecision(id: Long, decision: String)

    @Query("DELETE FROM mission_diffs WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}
