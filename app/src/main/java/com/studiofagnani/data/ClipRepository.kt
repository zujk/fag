package com.studiofagnani.data

import com.studiofagnani.data.db.AppDatabase
import com.studiofagnani.data.db.AttemptEntity
import com.studiofagnani.data.db.ClipEntity
import com.studiofagnani.data.db.ClipProgressEntity
import com.studiofagnani.data.db.CONTEXT_HOME
import com.studiofagnani.data.db.CheckpointEntity
import com.studiofagnani.data.db.FATIGUE_STRAINED
import com.studiofagnani.data.db.FATIGUE_TIRED
import com.studiofagnani.data.db.SessionEntity
import com.studiofagnani.data.manifest.ExerciseImporter
import com.studiofagnani.data.manifest.ManifestImporter
import com.studiofagnani.dsp.Contour
import com.studiofagnani.dsp.Dtw
import com.studiofagnani.dsp.WavIo
import com.studiofagnani.dsp.Waveform
import com.studiofagnani.session.FeedbackRules
import com.studiofagnani.session.Riscontro
import com.studiofagnani.session.SessionPlanner
import com.studiofagnani.session.SpacedRepetition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
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

    suspend fun exercise(id: String) = withContext(Dispatchers.IO) { db.exerciseDao().get(id) }

    /**
     * Il file degli esercizi cosi' com'e': serve per le cose che non stanno in
     * tabella, cioe' l'ordine delle categorie e le domande della fase 0.
     */
    suspend fun exerciseFile() = withContext(Dispatchers.IO) { exerciseImporter.read() }

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
        contourBlob: ByteArray? = null,
        f0Med: Double? = null,
        feedbackCodes: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val id = attemptDao.insert(
            AttemptEntity(
                clipId = clipId,
                createdAt = now,
                context = context,
                wavPath = wavPath,
                durationS = durationS,
                contourBlob = contourBlob,
                f0Med = f0Med,
                feedbackCodes = feedbackCodes,
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

    suspend fun waveformOf(file: File, buckets: Int): FloatArray? =
        withContext(Dispatchers.IO) {
            runCatching { Waveform.peaks(WavIo.read(file).samples, buckets) }.getOrNull()
        }

    /**
     * Esito del confronto fra la clip e una ripetizione (SPEC §5.3 punti 3 e 4).
     * [tuaAllineata] e' la tua curva riportata sui tempi della clip: e' quella da
     * disegnare sovrapposta, perche' i due frame numero 37 non sono lo stesso istante.
     */
    class Confronto(
        val sua: Contour?,
        val tua: Contour?,
        val tuaAllineata: FloatArray?,
        val riscontri: List<Riscontro>,
        val costo: Double?,
    ) {
        val completo: Boolean get() = sua != null && tua != null
    }

    /**
     * Misura la ripetizione, la allinea alla clip con DTW e ne ricava il riscontro
     * a parole. Sta qui e non nella schermata perche' la modalita' macchina (v2)
     * fara' la stessa cosa senza nessuna schermata davanti.
     */
    suspend fun confronta(
        clip: ClipEntity,
        ripetizione: File,
    ): Confronto = withContext(Dispatchers.IO) {
        val sua = analisi.contour(clip)
        val tua = runCatching {
            val audio = WavIo.read(ripetizione)
            ClipAnalyzer.analyze(audio.samples, audio.sampleRate).contour
        }.getOrNull()

        if (sua == null || tua == null) {
            return@withContext Confronto(sua, tua, null, emptyList(), null)
        }

        val allineamento = Dtw.align(sua.semitones, tua.semitones)
        Confronto(
            sua = sua,
            tua = tua,
            tuaAllineata = Dtw.warp(tua.semitones, allineamento, sua.semitones.size),
            riscontri = FeedbackRules.evaluate(sua, tua),
            costo = allineamento.cost.takeIf { allineamento.size > 0 },
        )
    }

    // --- la sessione quotidiana (SPEC §5.2) ---------------------------------

    /** Apre la riga della sessione: la chiusura arriva con la risposta sulla fatica. */
    suspend fun startSession(
        phase: Int,
        mode: String = CONTEXT_HOME,
        now: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        db.sessionDao().insert(
            SessionEntity(
                startedAt = now,
                endedAt = null,
                phase = phase,
                mode = mode,
                fatigue = null,
                note = null,
            ),
        )
    }

    suspend fun endSession(
        sessionId: Long,
        fatigue: String,
        note: String? = null,
        now: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val dao = db.sessionDao()
        val righe = dao.recent(20)
        val riga = righe.firstOrNull { it.id == sessionId } ?: return@withContext
        dao.update(riga.copy(endedAt = now, fatigue = fatigue, note = note))
    }

    /**
     * Le ultime risposte sulla fatica, la piu' recente per prima. Le sessioni senza
     * risposta non contano: sono quelle lasciate a meta', non un "va bene".
     */
    suspend fun recentFatigue(quante: Int): List<SessionPlanner.Fatica> =
        withContext(Dispatchers.IO) {
            db.sessionDao().recent(quante * 3)
                .mapNotNull { it.fatigue }
                .map {
                    when (it) {
                        FATIGUE_STRAINED -> SessionPlanner.Fatica.AFFATICATA
                        FATIGUE_TIRED -> SessionPlanner.Fatica.STANCA
                        else -> SessionPlanner.Fatica.OK
                    }
                }
                .take(quante)
        }

    suspend fun lastCheckpoint() = withContext(Dispatchers.IO) { db.checkpointDao().last() }

    /** Salva la scheda bersaglio della fase 0 (SPEC §5.1). */
    suspend fun saveCheckpoint(
        readingWav: String?,
        freeWav: String?,
        misure: ClipAnalyzer.Misure?,
        now: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        db.checkpointDao().insert(
            CheckpointEntity(
                createdAt = now,
                readingWav = readingWav,
                freeWav = freeWav,
                f0Med = misure?.f0Med,
                f0P10 = misure?.f0P10,
                f0P90 = misure?.f0P90,
                // Formanti: servono le LPC, che la SPEC §10 mette nella v3.
                f1Med = null,
                f2Med = null,
            ),
        )
    }

    fun newCheckpointFile(tipo: String, now: Long = System.currentTimeMillis()) =
        files.newCheckpointFile(tipo, now)

    /** Misura una registrazione guidata. `null` se il file non si legge. */
    suspend fun misura(file: File): ClipAnalyzer.Misure? = withContext(Dispatchers.IO) {
        runCatching {
            val audio = WavIo.read(file)
            ClipAnalyzer.analyze(audio.samples, audio.sampleRate)
        }.getOrNull()
    }

    /** Quante ripetizioni sono salvate: le Impostazioni lo dicono prima di cancellare. */
    suspend fun countRecordings(): Int = withContext(Dispatchers.IO) {
        attemptDao.allWavPaths().size
    }

    suspend fun deleteAllRecordings(): Int = withContext(Dispatchers.IO) {
        db.attemptDao().deleteAll()
        files.deleteAllAttempts()
    }
}
