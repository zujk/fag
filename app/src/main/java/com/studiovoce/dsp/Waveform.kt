package com.studiovoce.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Riduzione di un segnale a una forma d'onda da disegnare, e misure di energia
 * condivise fra il registratore e il ritaglio. Kotlin puro, testabile sulla JVM.
 */
object Waveform {

    /**
     * Inviluppo a [buckets] colonne: per ogni colonna il picco assoluto.
     * Valori in [0, 1]. Con un segnale vuoto restituisce tutti zeri.
     */
    fun peaks(samples: FloatArray, buckets: Int): FloatArray {
        require(buckets > 0) { "buckets deve essere positivo" }
        val out = FloatArray(buckets)
        if (samples.isEmpty()) return out
        for (i in 0 until buckets) {
            val from = (i.toLong() * samples.size / buckets).toInt()
            val to = ((i + 1).toLong() * samples.size / buckets).toInt().coerceAtLeast(from + 1)
            var peak = 0f
            for (j in from until minOf(to, samples.size)) {
                peak = max(peak, abs(samples[j]))
            }
            out[i] = peak.coerceAtMost(1f)
        }
        return out
    }

    /** Valore efficace (RMS) di una finestra di campioni float in [-1, 1]. */
    fun rms(samples: FloatArray, from: Int = 0, count: Int = samples.size - from): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        val end = minOf(from + count, samples.size)
        for (i in from until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        val n = end - from
        return if (n <= 0) 0f else sqrt(sum / n).toFloat()
    }

    /** Lo stesso per i blocchi che arrivano da AudioRecord (PCM 16 bit). */
    fun rms(buffer: ShortArray, count: Int = buffer.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        val end = minOf(count, buffer.size)
        for (i in 0 until end) {
            val v = buffer[i] / 32768.0
            sum += v * v
        }
        return if (end <= 0) 0f else sqrt(sum / end).toFloat()
    }

    /** Picco assoluto, per capire se una registrazione e' muta. */
    fun peak(samples: FloatArray): Float {
        var p = 0f
        for (v in samples) p = max(p, abs(v))
        return p
    }
}
