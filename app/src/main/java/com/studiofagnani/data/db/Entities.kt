package com.studiofagnani.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema di SPEC §8. Tutte le tabelle sono definite dalla v0 anche se alcune
 * colonne servono solo dalla v1: così le milestone successive non hanno bisogno
 * di migrazioni che rischino di toccare i progressi.
 */

const val ORIGIN_BUNDLED = "bundled"
const val ORIGIN_USER = "user"

const val CONTEXT_HOME = "home"
const val CONTEXT_TRAIN = "train"
const val CONTEXT_CAR = "car"

@Entity(tableName = "clip")
data class ClipEntity(
    @PrimaryKey val id: String,
    /** Nome del file. Per `origin = bundled` sta in assets/clips/, per `user` in filesDir/clips/. */
    val file: String,
    val origin: String,
    val category: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val context: String?,
    val energy: String?,
    @ColumnInfo(name = "study_note") val studyNote: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "start_s") val startS: Double?,
    @ColumnInfo(name = "end_s") val endS: Double?,
    /** Nota di verifica scritta a mano nel foglio. */
    val verification: String?,
    /** `analysis.check` del manifest: "OK…", "SOSPETTA…", "MISTA…". Vedi SPEC §5.4. */
    @ColumnInfo(name = "check_status") val checkStatus: String?,
    /** Ziv ha ascoltato una clip sospetta e l'ha segnata "va bene". */
    @ColumnInfo(name = "user_ok_override") val userOkOverride: Boolean = false,
    /** Nascosta perché l'audio manca: si vede solo nell'avviso della Libreria. */
    val hidden: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "clip_analysis",
    foreignKeys = [ForeignKey(
        entity = ClipEntity::class,
        parentColumns = ["id"],
        childColumns = ["clip_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ClipAnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "clip_id") val clipId: String,
    @ColumnInfo(name = "f0_med") val f0Med: Double?,
    @ColumnInfo(name = "f0_p10") val f0P10: Double?,
    @ColumnInfo(name = "f0_p90") val f0P90: Double?,
    @ColumnInfo(name = "f1_med") val f1Med: Double?,
    @ColumnInfo(name = "f2_med") val f2Med: Double?,
    @ColumnInfo(name = "duration_s") val durationS: Double?,
    @ColumnInfo(name = "voiced_pct") val voicedPct: Double?,
    /** Curva di altezza in semitoni, FloatArray codificata. Calcolata dalla v1. */
    @ColumnInfo(name = "contour_blob", typeAffinity = ColumnInfo.BLOB) val contourBlob: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is ClipAnalysisEntity && clipId == other.clipId
    override fun hashCode(): Int = clipId.hashCode()
}

@Entity(
    tableName = "clip_progress",
    foreignKeys = [ForeignKey(
        entity = ClipEntity::class,
        parentColumns = ["id"],
        childColumns = ["clip_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("due_at")],
)
data class ClipProgressEntity(
    @PrimaryKey @ColumnInfo(name = "clip_id") val clipId: String,
    /** Voto 1-5 di Ziv. `null` = mai votata. */
    val level: Int?,
    @ColumnInfo(name = "last_practiced_at") val lastPracticedAt: Long?,
    /** Giorno in cui la clip torna nel ripasso, come epoch-day (vedi SpacedRepetition). */
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "reps_total") val repsTotal: Int = 0,
)

@Entity(
    tableName = "attempt",
    foreignKeys = [ForeignKey(
        entity = ClipEntity::class,
        parentColumns = ["id"],
        childColumns = ["clip_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("clip_id"), Index("created_at")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "clip_id") val clipId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** home | train | car */
    val context: String,
    @ColumnInfo(name = "wav_path") val wavPath: String,
    @ColumnInfo(name = "duration_s") val durationS: Double,
    @ColumnInfo(name = "contour_blob", typeAffinity = ColumnInfo.BLOB) val contourBlob: ByteArray? = null,
    @ColumnInfo(name = "f0_med") val f0Med: Double? = null,
    /** Codici delle regole di riscontro scattate (SPEC §5.5), separati da virgola. */
    @ColumnInfo(name = "feedback_codes") val feedbackCodes: String? = null,
    @ColumnInfo(name = "self_rating") val selfRating: Int? = null,
) {
    override fun equals(other: Any?): Boolean = other is AttemptEntity && id == other.id
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    val phase: Int,
    /** home | train | car */
    val mode: String,
    /** ok | tired | strained */
    val fatigue: String?,
    val note: String?,
)

@Entity(tableName = "checkpoint")
data class CheckpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "reading_wav") val readingWav: String?,
    @ColumnInfo(name = "free_wav") val freeWav: String?,
    @ColumnInfo(name = "f0_med") val f0Med: Double?,
    @ColumnInfo(name = "f0_p10") val f0P10: Double?,
    @ColumnInfo(name = "f0_p90") val f0P90: Double?,
    @ColumnInfo(name = "f1_med") val f1Med: Double?,
    @ColumnInfo(name = "f2_med") val f2Med: Double?,
)

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val phase: Int,
    val title: String,
    val instructions: String,
    @ColumnInfo(name = "duration_s") val durationS: Int,
    @ColumnInfo(name = "audio_asset") val audioAsset: String?,
)
