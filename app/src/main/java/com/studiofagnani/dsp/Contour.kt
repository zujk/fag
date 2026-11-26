package com.studiofagnani.dsp

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import kotlin.math.ln
import kotlin.math.pow

/**
 * La curva di altezza in semitoni rispetto alla **propria** mediana (SPEC §5.3).
 *
 * Il riferimento e' la mediana di ciascuna curva, non un valore fisso: due voci
 * diverse stanno su altezze diverse, e confrontarle in hertz direbbe solo che
 * sono due persone diverse. In semitoni resta la forma della melodia, che e' la
 * cosa che si puo' imitare.
 *
 * `NaN` dove non c'e' parlato sonoro: un buco nella curva, non uno zero.
 */
class Contour(
    val semitones: FloatArray,
    val hopMs: Double,
    /** Mediana in hertz, riferimento dei semitoni. `NaN` se non c'e' nulla di sonoro. */
    val medianHz: Float,
) {

    /** I soli frame sonori, ordinati: base di tutti i percentili. */
    private val sonori: FloatArray = semitones.filter { !it.isNaN() }.sorted().toFloatArray()

    val voicedCount: Int get() = sonori.size

    /** Percentuale di frame con parlato sonoro, da 0 a 100. */
    val voicedPct: Double
        get() = if (semitones.isEmpty()) 0.0 else sonori.size * 100.0 / semitones.size

    /** Durata coperta dalla curva. Un frame vale un passo. */
    val durationMs: Double get() = semitones.size * hopMs

    val durationS: Double get() = durationMs / 1000.0

    /**
     * Percentile [p] (da 0 a 1) dei frame sonori, in semitoni, con
     * interpolazione fra i due valori vicini. `NaN` se non c'e' nulla di sonoro.
     */
    fun percentile(p: Double): Float {
        if (sonori.isEmpty()) return Float.NaN
        if (sonori.size == 1) return sonori[0]
        val pos = p.coerceIn(0.0, 1.0) * (sonori.size - 1)
        val basso = pos.toInt()
        val alto = (basso + 1).coerceAtMost(sonori.size - 1)
        val frazione = (pos - basso).toFloat()
        return sonori[basso] + (sonori[alto] - sonori[basso]) * frazione
    }

    /** Lo stesso percentile riportato in hertz. */
    fun percentileHz(p: Double): Float = fromSemitones(percentile(p), medianHz)

    val p10: Float get() = percentile(0.10)
    val p90: Float get() = percentile(0.90)

    /** Escursione fra 10° e 90° percentile: quanto si muove la melodia. */
    val range: Float get() = p90 - p10

    /** Frequenza del frame [index] in hertz, `NaN` se muto. */
    fun hzAt(index: Int): Float = fromSemitones(semitones[index], medianHz)

    companion object {

        private const val PER_SEMITONE = 12.0 / 0.6931471805599453 // 12 / ln(2)

        /**
         * Distanza in semitoni fra [hz] e il riferimento [refHz].
         * `NaN` se uno dei due non e' una frequenza valida.
         */
        fun semitones(hz: Float, refHz: Float): Float {
            if (hz.isNaN() || refHz.isNaN() || hz <= 0f || refHz <= 0f) return Float.NaN
            return (ln(hz.toDouble() / refHz) * PER_SEMITONE).toFloat()
        }

        /** Il contrario: dalla distanza in semitoni alla frequenza. */
        fun fromSemitones(st: Float, refHz: Float): Float {
            if (st.isNaN() || refHz.isNaN() || refHz <= 0f) return Float.NaN
            return (refHz * 2.0.pow(st / 12.0)).toFloat()
        }

        /**
         * Costruisce la curva da una serie di frequenze come quella di [Yin].
         * Frequenze non valide (zero, negative) valgono come frame muti.
         */
        fun fromHz(hz: FloatArray, hopMs: Double = Yin.Config().hopMs): Contour {
            val valide = hz.filter { !it.isNaN() && it > 0f }.sorted()
            val mediana = if (valide.isEmpty()) Float.NaN else valide[valide.size / 2]
            val st = FloatArray(hz.size) { semitones(hz[it], mediana) }
            return Contour(st, hopMs, mediana)
        }
    }
}

/**
 * Codifica della curva per la colonna `contour_blob` (SPEC §8).
 *
 * Non e' solo l'array: il passo e la mediana stanno nell'intestazione, altrimenti
 * una curva riletta dal database sarebbe un elenco di numeri senza scala ne' tempo.
 */
object ContourBlob {

    private const val MAGIC = "SFC1"
    private const val HEADER_BYTES = 16

    fun encode(contour: Contour): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_BYTES + contour.semitones.size * 4)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putFloat(contour.hopMs.toFloat())
        buffer.putInt(contour.semitones.size)
        buffer.putFloat(contour.medianHz)
        for (v in contour.semitones) buffer.putFloat(v)
        return buffer.array()
    }

    fun decode(blob: ByteArray): Contour {
        require(blob.size >= HEADER_BYTES) { "blob troppo corto: ${blob.size} byte" }
        val buffer = ByteBuffer.wrap(blob)
        val firma = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
        require(firma == MAGIC) { "firma della curva non riconosciuta: $firma" }

        val hopMs = buffer.getFloat().toDouble()
        val count = buffer.getInt()
        require(count >= 0) { "numero di frame non valido: $count" }
        require(blob.size == HEADER_BYTES + count * 4) {
            "il blob dice $count frame ma ha ${blob.size} byte"
        }
        val medianHz = buffer.getFloat()
        val st = FloatArray(count)
        try {
            for (i in 0 until count) st[i] = buffer.getFloat()
        } catch (fine: BufferUnderflowException) {
            throw IllegalArgumentException("blob interrotto a meta'", fine)
        }
        return Contour(st, hopMs, medianHz)
    }
}
