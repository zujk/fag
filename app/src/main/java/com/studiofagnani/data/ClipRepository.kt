package com.studiofagnani.data

import com.studiofagnani.data.db.AppDatabase
import com.studiofagnani.data.db.AttemptEntity
import com.studiofagnani.data.db.ClipEntity
import com.studiofagnani.data.db.ClipProgressEntity
import com.studiofagnani.data.db.CONTEXT_HOME
import com.studiofagnani.data.manifest.ExerciseImporter
import com.studiofagnani.data.manifest.ManifestImporter
import com.studiofagnani.dsp.WavIo
import com.studiofagnani.dsp.Waveform
import com.studiofagnani.session.SpacedRepetition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ClipRepository(
    private val db: AppDatabase,
    private val files: ClipFiles,
    private val importer: ManifestImporter,
    private val exerciseImporter: ExerciseImporter,
    private val analisi: ClipAnalysisRepository,
) {

    private val clipDao = db.clipDao()
    private val attemptDao = db.attemptDao()

    /** Tutte le clip con Livello e misure. Qualche decina di righe: si combina in memoria. */
    val cards: Flow<List<ClipCard>> = combine(
        clipDao.observeClips(),
        clipDao.observeProgress(),
        clipDao.observeAnalyses(),
    ) { clips, progress, analyses ->
        val p = progress.associateBy { it.clipId }
        val a = analyses.associateBy { it.clipId }
        clips.map { ClipCard(it, p[it.id], a[it.id]) }
    }

    /**
     * L'ordine delle categorie viene dal manifest, non dal codice: se il manifest non
     * si legge la lista resta vuota e la Libreria usa le categorie delle clip in archivio.
     */
    suspend fun categories(): List<String> = withContext(Dispatchers.IO) {
        runCatching { importer.readBundledManifest().categories }.getOrDefault(emptyList())
    }

    suspend fun importManifest() = importer.importBundled()

    /** Gli esercizi del riscaldamento e della fase 1 (SPEC §8). */
    suspend fun importExercises() = exerciseImporter.importBundled()

    /**
     * L'ordine delle categorie della fase 2 come lo dà `exercises.json`.
     * Le Impostazioni possono sovrascriverlo; vuoto significa "quello del file".
     */
    suspend fun phase2CategoryOrder(): List<String> = withContext(Dispatchers.IO) {
        runCatching { exerciseImporter.read().phase2CategoryOrder }.getOrDefault(emptyList())
    }

    suspend fun exercisesForPhase(phase: Int) = withContext(Dispatchers.IO) {
        db.exerciseDao().forPhase(phase)
    }

    /** La curva di altezza della clip, calcolata al primo uso (SPEC §4.4). */
    suspend fun contour(clip: ClipEntity) = analisi.contour(clip)

    fun mediaUri(clip: ClipEntity) = files.mediaUri(clip)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Le clip del ripasso di oggi (SPEC §5.4). */
    suspend fun reviewToday(
        cards: List<ClipCard>,
        preferredCategoryId: String? = null,
        limit: Int = SpacedRepetition.DEFAULT_LIMIT,
        today: Long = today(),
    ): List<ClipCard> = withContext(Dispatchers.Default) {
        val byId = cards.associateBy { it.id }
        SpacedRepetition
            .selectForReview(cards.map { it.toCandidate() }, today, limit, preferredCategoryId)
            .mapNotNull { byId[it.clipId] }
    }

    /**
     * Salva il voto di Ziv e sposta la scadenza del ripasso.
     * `reps_total` cresce a ogni ripetizione registrata, non a ogni voto.
     */
    suspend fun rate(
        clipId: String,
        level: Int,
        now: Long = System.currentTimeMillis(),
        today: Long = today(),
    ) = withContext(Dispatchers.IO) {
        val old = clipDao.getProgress(clipId)
        clipDao.upsertProgress(
            ClipProgressEntity(
                clipId = clipId,
                level = level,
                lastPracticedAt = now,
                dueAt = SpacedRepetition.nextDueDay(level, today),
                repsTotal = old?.repsTotal ?: 0,
            ),
        )
    }

    /** Segna una clip sospetta come "ascoltata, va bene" (SPEC §5.4). */
    suspend fun markListenedOk(clipId: String, ok: Boolean = true) = withContext(Dispatchers.IO) {
        clipDao.setUserOkOverride(clipId, ok)
    }

    suspend fun saveAttempt(
        clipId: String,
        wavPath: String,
        durationS: Double,
        context: String = CONTEXT_HOME,
        now: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        val id = attemptDao.insert(
            AttemptEntity(
                clipId = clipId,
                createdAt = now,
                context = context,
                wavPath = wavPath,
                durationS = durationS,
            ),
        )
        val old = clipDao.getProgress(clipId)
        clipDao.upsertProgress(
            (old ?: ClipProgressEntity(clipId, null, null, today(), 0)).copy(
                lastPracticedAt = now,
                repsTotal = (old?.repsTotal ?: 0) + 1,
            ),
        )
        id
    }

    fun attemptsForClip(clipId: String): Flow<List<AttemptEntity>> =
        attemptDao.observeForClip(clipId)

    suspend fun setAttemptRating(attemptId: Long, rating: Int) = withContext(Dispatchers.IO) {
        attemptDao.setRating(attemptId, rating)
    }

    fun newAttemptFile(clipId: String, now: Long = System.currentTimeMillis()) =
        files.newAttemptFile(clipId, now)

    /**
     * Forma d'onda della clip per il disegno. Le misure di §4.4 le calcola
     * [ClipAnalysisRepository] al primo uso, non questa funzione: disegnare una
     * forma d'onda e misurare l'altezza sono due lavori di costo molto diverso.
     */
    suspend fun waveform(clip: ClipEntity, buckets: Int): FloatArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val audio = files.openClip(clip).buffered().use { WavIo.read(it) }
                val peaks = Waveform.peaks(audio.samples, buckets)
                peaks
            }.getOrNull()
        }

    suspend fun waveformOf(file: java.io.File, buckets: Int): FloatArray? =
        withContext(Dispatchers.IO) {
            runCatching { Waveform.peaks(WavIo.read(file).samples, buckets) }.getOrNull()
        }

    suspend fun deleteAllRecordings(): Int = withContext(Dispatchers.IO) {
        db.attemptDao().deleteAll()
        files.deleteAllAttempts()
    }
}
