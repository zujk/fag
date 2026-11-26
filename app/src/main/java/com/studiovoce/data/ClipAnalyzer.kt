package com.studiovoce.data

import android.util.Log
import com.studiovoce.data.db.AppDatabase
import com.studiovoce.data.db.ClipAnalysisEntity
import com.studiovoce.data.db.ClipEntity
import com.studiovoce.dsp.Contour
import com.studiovoce.dsp.ContourBlob
import com.studiovoce.dsp.WavIo
import com.studiovoce.dsp.Yin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le misure di una clip (SPEC §4.4), calcolate una volta sola e tenute in cache.
 *
 * Nella v1 solo quelle di altezza: le formanti F1 e F2 richiedono l'LPC, che la
 * SPEC §10 mette nella v3. I campi corrispondenti restano come li ha lasciati il
 * foglio di Ziv.
 */
object ClipAnalyzer {

    /** Quello che si riesce a misurare da un segnale, senza database di mezzo. */
    data class Misure(
        val f0Med: Double?,
        val f0P10: Double?,
        val f0P90: Double?,
        val durationS: Double,
        val voicedPct: Double,
        val contourBlob: ByteArray,
        val contour: Contour,
    )

    /**
     * Misura un segnale. Puro: nessun file, nessun database, quindi si verifica
     * con segnali sintetici.
     *
     * La durata e' quella del file, perche' e' la durata della clip; la curva
     * porta con se' anche quella del solo parlato, che serve al confronto.
     */
    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        config: Yin.Config = Yin.Config(),
    ): Misure {
        val hz = Yin.curve(samples, sampleRate, config)
        val contour = Contour.fromHz(hz, config.hopMs)
        val haParlato = contour.voicedCount > 0

        return Misure(
            f0Med = if (haParlato) contour.medianHz.toDouble() else null,
            f0P10 = if (haParlato) contour.percentileHz(0.10).toDouble() else null,
            f0P90 = if (haParlato) contour.percentileHz(0.90).toDouble() else null,
            durationS = samples.size.toDouble() / sampleRate,
            voicedPct = contour.voicedPct,
            contourBlob = ContourBlob.encode(contour),
            contour = contour,
        )
    }

    /**
     * Mette le misure calcolate accanto a quelle che c'erano gia'.
     *
     * Le misure del foglio hanno la precedenza: `analizza_clip.py` lavora sul file
     * originale, l'app su quello ritagliato, e sovrascriverle perderebbe un lavoro
     * fatto fuori senza guadagnare niente. La curva invece la mette sempre l'app:
     * nel foglio non c'e'.
     */
    fun merge(
        clipId: String,
        esistente: ClipAnalysisEntity?,
        misure: Misure,
    ): ClipAnalysisEntity = ClipAnalysisEntity(
        clipId = clipId,
        f0Med = esistente?.f0Med ?: misure.f0Med,
        f0P10 = esistente?.f0P10 ?: misure.f0P10,
        f0P90 = esistente?.f0P90 ?: misure.f0P90,
        // Formanti: v3. Qui si conservano, non si inventano.
        f1Med = esistente?.f1Med,
        f2Med = esistente?.f2Med,
        durationS = esistente?.durationS ?: misure.durationS,
        voicedPct = esistente?.voicedPct ?: misure.voicedPct,
        contourBlob = misure.contourBlob,
    )

    /** Vero se la riga nel database ha ancora bisogno di essere calcolata. */
    fun needsAnalysis(esistente: ClipAnalysisEntity?): Boolean =
        esistente?.contourBlob == null
}

/**
 * La parte che tocca i file e il database: legge il WAV, misura, salva.
 * Separata da [ClipAnalyzer] cosi' il calcolo resta verificabile sulla JVM.
 */
class ClipAnalysisRepository(
    private val db: AppDatabase,
    private val files: ClipFiles,
) {

    /**
     * Restituisce le misure della clip, calcolandole al primo uso.
     * `null` se il file audio non c'e' o non si riesce a leggere: in quel caso la
     * clip resta senza curva e la schermata lo mostra, senza far cadere la sessione.
     */
    suspend fun ensure(clip: ClipEntity): ClipAnalysisEntity? = withContext(Dispatchers.IO) {
        val dao = db.clipDao()
        val esistente = dao.getAnalysis(clip.id)
        if (!ClipAnalyzer.needsAnalysis(esistente)) return@withContext esistente

        val audio = try {
            files.openClip(clip).buffered().use { WavIo.read(it) }
        } catch (e: Exception) {
            Log.w(TAG, "misure non calcolabili per ${clip.id}", e)
            return@withContext esistente
        }

        val riga = ClipAnalyzer.merge(
            clipId = clip.id,
            esistente = esistente,
            misure = ClipAnalyzer.analyze(audio.samples, audio.sampleRate),
        )
        dao.upsertAnalysis(riga)
        riga
    }

    /** La curva pronta da disegnare, se c'e'. */
    suspend fun contour(clip: ClipEntity): Contour? {
        val blob = ensure(clip)?.contourBlob ?: return null
        return runCatching { ContourBlob.decode(blob) }.getOrNull()
    }

    private companion object {
        const val TAG = "ClipAnalysis"
    }
}
