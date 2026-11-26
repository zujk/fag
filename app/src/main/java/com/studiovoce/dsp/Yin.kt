package com.studiovoce.dsp

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Altezza della voce con il metodo YIN (SPEC §4.4): finestra 40 ms, passo 10 ms,
 * ricerca fra 75 e 500 Hz, soglia 0,15. Kotlin puro, nessuna libreria nativa.
 *
 * Restituisce una frequenza per frame, con `NaN` dove non c'e' parlato sonoro.
 * NaN e non 0 perche' "non lo so" e "zero hertz" sono cose diverse: la curva da
 * disegnare deve avere dei buchi, non una linea che crolla a terra.
 *
 * Il segnale viene prima portato intorno a 22 kHz: a noi servono i 75-500 Hz, e
 * la funzione di differenza costa finestra x periodi provati, quindi dimezzare il
 * campionamento taglia di quattro volte il lavoro senza perdere precisione utile.
 */
object Yin {

    /** Valore di un frame senza parlato sonoro. */
    val UNVOICED = Float.NaN

    data class Config(
        val windowMs: Double = 40.0,
        val hopMs: Double = 10.0,
        val minHz: Double = 75.0,
        val maxHz: Double = 500.0,
        val threshold: Double = 0.15,
        /**
         * Sotto questo valore efficace il frame si considera muto. Senza questa
         * soglia YIN trova un periodo anche nel fruscio del microfono.
         */
        val minRms: Float = 0.004f,
        val targetRate: Int = 22_050,
    )

    /** Istante al centro della finestra del frame [index], in millisecondi. */
    fun frameCenterMs(index: Int, config: Config = Config()): Double =
        index * config.hopMs + config.windowMs / 2

    /**
     * Curva di altezza in Hz, un valore ogni [Config.hopMs] millisecondi.
     * Vuota se il segnale e' piu corto di una finestra.
     */
    fun curve(samples: FloatArray, sampleRate: Int, config: Config = Config()): FloatArray {
        require(sampleRate > 0) { "campionamento non valido: $sampleRate" }

        val durationMs = samples.size * 1000.0 / sampleRate
        if (durationMs < config.windowMs) return FloatArray(0)

        val factor = factorFor(sampleRate, config.targetRate)
        val x = decimate(samples, factor)
        val rate = sampleRate.toDouble() / factor

        val window = (config.windowMs / 1000.0 * rate).roundToInt()
        val hopSamples = config.hopMs / 1000.0 * rate
        val tauMin = max(2, (rate / config.maxHz).toInt())
        val tauMax = min(window - 2, ceil(rate / config.minHz).toInt())
        if (window < 4 || hopSamples < 1.0 || tauMax <= tauMin) return FloatArray(0)

        // Il numero di frame si calcola sui tempi, non sui campioni: cosi' non
        // dipende da come e' andato l'arrotondamento del sottocampionamento.
        val frames = ((durationMs - config.windowMs) / config.hopMs + 1e-9).toInt() + 1

        val diff = DoubleArray(tauMax + 2)
        val norm = DoubleArray(tauMax + 2)
        val out = FloatArray(frames)

        for (f in 0 until frames) {
            val offset = (f * hopSamples).roundToInt()
            out[f] = if (offset < 0 || offset + window > x.size) {
                UNVOICED
            } else {
                frameF0(x, offset, window, tauMin, tauMax, rate, config, diff, norm)
            }
        }
        return out
    }

    /** Altezza di una sola finestra, oppure [UNVOICED]. */
    private fun frameF0(
        x: FloatArray,
        offset: Int,
        window: Int,
        tauMin: Int,
        tauMax: Int,
        rate: Double,
        config: Config,
        diff: DoubleArray,
        norm: DoubleArray,
    ): Float {
        var energy = 0.0
        for (j in 0 until window) {
            val v = x[offset + j].toDouble()
            energy += v * v
        }
        if (sqrt(energy / window) < config.minRms) return UNVOICED

        // Funzione di differenza: quanto il segnale somiglia a se stesso spostato
        // di tau campioni. Un minimo vicino a zero significa "periodo trovato".
        for (tau in 0..tauMax) {
            var sum = 0.0
            for (j in 0 until window - tau) {
                val d = x[offset + j] - x[offset + j + tau]
                sum += d.toDouble() * d
            }
            diff[tau] = sum
        }

        // Normalizzazione cumulativa: e' il passaggio che distingue YIN
        // dall'autocorrelazione semplice e che evita di prendere l'ottava sotto.
        norm[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += diff[tau]
            norm[tau] = if (running > 0.0) diff[tau] * tau / running else 1.0
        }

        // Primo periodo che scende sotto la soglia, poi si segue la discesa fino
        // in fondo al minimo. Prendere il primo e non il minimo assoluto e' cio'
        // che tiene l'altezza sulla fondamentale invece che su un suo multiplo.
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (norm[tau] < config.threshold) {
                while (tau + 1 <= tauMax && norm[tau + 1] < norm[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return UNVOICED

        val refined = parabolic(norm, found, tauMin, tauMax)
        if (refined <= 0.0) return UNVOICED
        val f0 = rate / refined
        // Un margine stretto: l'interpolazione puo' uscire di poco dall'intervallo.
        return if (f0 >= config.minHz * 0.95 && f0 <= config.maxHz * 1.05) f0.toFloat() else UNVOICED
    }

    /**
     * Vertice della parabola per i tre punti attorno al minimo: porta la stima
     * sotto il campione, che è ciò che serve per stare dentro l'1% a 250 Hz.
     */
    private fun parabolic(v: DoubleArray, tau: Int, lo: Int, hi: Int): Double {
        if (tau <= lo || tau >= hi) return tau.toDouble()
        val a = v[tau - 1]
        val b = v[tau]
        val c = v[tau + 1]
        val den = a - 2 * b + c
        if (den == 0.0) return tau.toDouble()
        val shift = 0.5 * (a - c) / den
        return if (shift > -1.0 && shift < 1.0) tau + shift else tau.toDouble()
    }

    /** Di quanto sottocampionare per avvicinarsi a [targetRate] restando interi. */
    internal fun factorFor(sampleRate: Int, targetRate: Int): Int =
        max(1, sampleRate / targetRate)

    /**
     * Tiene un campione ogni [factor], dopo un passa-basso: senza il filtro le
     * frequenze alte si ripiegherebbero su quelle basse e falserebbero l'altezza.
     */
    internal fun decimate(samples: FloatArray, factor: Int, taps: Int = 31): FloatArray {
        require(factor >= 1) { "fattore non valido: $factor" }
        if (factor == 1 || samples.isEmpty()) return samples

        val h = lowPass(taps, 0.45 / factor)
        val half = taps / 2
        val out = FloatArray(samples.size / factor)
        for (i in out.indices) {
            val center = i * factor
            var acc = 0.0
            for (k in 0 until taps) {
                val idx = center + k - half
                if (idx >= 0 && idx < samples.size) acc += h[k] * samples[idx]
            }
            out[i] = acc.toFloat()
        }
        return out
    }

    /** Passa-basso FIR, sinc finestrato con Hamming. [cutoff] in cicli al campione. */
    private fun lowPass(taps: Int, cutoff: Double): DoubleArray {
        val h = DoubleArray(taps)
        val half = taps / 2
        var sum = 0.0
        for (k in 0 until taps) {
            val n = k - half
            val sinc = if (n == 0) 2 * cutoff else sin(2 * PI * cutoff * n) / (PI * n)
            val win = 0.54 - 0.46 * cos(2 * PI * k / (taps - 1))
            h[k] = sinc * win
            sum += h[k]
        }
        for (k in 0 until taps) h[k] /= sum
        return h
    }
}
