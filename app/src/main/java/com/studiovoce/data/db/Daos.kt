package com.studiovoce.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Le query restano volutamente semplici: la voice bank è di qualche decina di clip,
 * quindi i filtri della Libreria e la scelta del ripasso si fanno in Kotlin su liste
 * (logica pura, testabile) invece che in SQL dinamico.
 */
@Dao
interface ClipDao {

    @Query("SELECT * FROM clip ORDER BY category_id, id")
    fun observeClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clip_progress")
    fun observeProgress(): Flow<List<ClipProgressEntity>>

    @Query("SELECT * FROM clip_analysis")
    fun observeAnalyses(): Flow<List<ClipAnalysisEntity>>

    @Query("SELECT * FROM clip WHERE id = :id")
    suspend fun getClip(id: String): ClipEntity?

    @Query("SELECT * FROM clip")
    suspend fun getAllClips(): List<ClipEntity>

    @Query("SELECT * FROM clip_progress WHERE clip_id = :clipId")
    suspend fun getProgress(clipId: String): ClipProgressEntity?

    @Query("SELECT * FROM clip_progress")
    suspend fun getAllProgress(): List<ClipProgressEntity>

    @Query("SELECT * FROM clip_analysis WHERE clip_id = :clipId")
    suspend fun getAnalysis(clipId: String): ClipAnalysisEntity?

    @Query("SELECT * FROM clip_analysis")
    suspend fun getAllAnalyses(): List<ClipAnalysisEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Update
    suspend fun updateClips(clips: List<ClipEntity>)

    @Upsert
    suspend fun upsertProgress(progress: ClipProgressEntity)

    @Upsert
    suspend fun upsertAnalysis(analysis: ClipAnalysisEntity)

    @Query("UPDATE clip SET user_ok_override = :ok WHERE id = :clipId")
    suspend fun setUserOkOverride(clipId: String, ok: Boolean)
}

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempt WHERE clip_id = :clipId ORDER BY created_at DESC")
    fun observeForClip(clipId: String): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempt WHERE id = :id")
    suspend fun get(id: Long): AttemptEntity?

    @Query("UPDATE attempt SET self_rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("SELECT * FROM attempt WHERE self_rating IS NULL AND context = 'car' ORDER BY created_at")
    suspend fun unratedCarAttempts(): List<AttemptEntity>

    @Query("SELECT wav_path FROM attempt")
    suspend fun allWavPaths(): List<String>

    @Query("DELETE FROM attempt")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM session ORDER BY started_at DESC LIMIT 1")
    suspend fun last(): SessionEntity?

    @Query("SELECT * FROM session ORDER BY started_at DESC LIMIT :n")
    suspend fun recent(n: Int): List<SessionEntity>
}

@Dao
interface CheckpointDao {

    @Insert
    suspend fun insert(checkpoint: CheckpointEntity): Long

    @Query("SELECT * FROM checkpoint ORDER BY created_at")
    fun observeAll(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoint ORDER BY created_at DESC LIMIT 1")
    suspend fun last(): CheckpointEntity?
}

@Dao
interface ExerciseDao {

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE phase = :phase ORDER BY id")
    suspend fun forPhase(phase: Int): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?
}
