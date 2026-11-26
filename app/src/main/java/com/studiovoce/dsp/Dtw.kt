package com.studiovoce.dsp

import kotlin.math.abs

/**
 * Allineamento temporale di due curve di altezza (SPEC §5.3).
 *
 * La ripetizione di Ziv non dura quanto la clip di Fagnani: qualche sillaba e'
 * piu' lenta, qualche pausa piu' lunga. Confrontare i due frame numero 37 non
 * avrebbe senso. DTW cerca la corrispondenza che costa meno, muovendosi solo in
 * avanti, e restituisce le coppie di frame da confrontare.
 *
 * Costo in memoria: un byte per ogni coppia di frame, cioe' circa 1 MB per due
 * curve di 10 secondi. Le clip dell'esercizio stanno entro quest'ordine.
 */
object Dtw {

    private const val DIAG: Byte = 0
    private const val UP: Byte = 1
    private const val LEFT: Byte = 2
    private const val START: Byte = 3

    /**
     * Le coppie di frame da confrontare: [a] e [b] hanno la stessa lunghezza e
     * `a[k]` va confrontato con `b[k]`. [cost] e' lo scarto medio per coppia, in
     * semitoni: non la somma, cosi' due curve lunghe e due corte si confrontano.
     */
    class Alignment(val a: IntArray, val b: IntArray, val cost: Double) {

        val size: Int get() = a.size

        companion object {
            val EMPTY = Alignment(IntArray(0), IntArray(0), 0.0)
        }
    }

    /**
     * Allinea le due curve in semitoni. [unvoicedCost] e' quanto costa mettere
     * un frame muto davanti a uno sonoro: una penale fissa, perche' un buco nella
     * curva non e' un salto di intonazione e non deve pesare come tale.
     */
    fun align(a: FloatArray, b: FloatArray, unvoicedCost: Float = 2f): Alignment {
        if (a.isEmpty() || b.isEmpty()) return Alignment.EMPTY

        val n = a.size
        val m = b.size
        val dir = ByteArray(n * m)
        var prev = DoubleArray(m)
        var curr = DoubleArray(m)

        for (i in 0 until n) {
            for (j in 0 until m) {
                val local = delta(a[i], b[j], unvoicedCost)
                var best: Double
                var d: Byte
                when {
                    i == 0 && j == 0 -> {
                        best = 0.0
                        d = START
                    }
                    i == 0 -> {
                        best = curr[j - 1]
                        d = LEFT
                    }
                    j == 0 -> {
                        best = prev[j]
                        d = UP
                    }
                    else -> {
                        val diag = prev[j - 1]
                        val up = prev[j]
                        val left = curr[j - 1]
                        // A pari costo vince la diagonale: due curve uguali devono
                        // allinearsi frame per frame, senza allungare il percorso.
                        if (diag <= up && diag <= left) {
                            best = diag
                            d = DIAG
                        } else if (up <= left) {
                            best = up
                            d = UP
                        } else {
                            best = left
                            d = LEFT
                        }
                    }
                }
                curr[j] = local + best
                dir[i * m + j] = d
            }
            val scambio = prev
            prev = curr
            curr = scambio
        }

        val totale = prev[m - 1]

        // Si torna indietro lungo le scelte fatte, poi si rovescia il percorso.
        val passiA = ArrayList<Int>(n + m)
        val passiB = ArrayList<Int>(n + m)
        var i = n - 1
        var j = m - 1
        while (i >= 0 && j >= 0) {
            passiA.add(i)
            passiB.add(j)
            when (dir[i * m + j]) {
                START -> break
                DIAG -> {
                    i--
                    j--
                }
                UP -> i--
                LEFT -> j--
            }
        }
        passiA.reverse()
        passiB.reverse()

        val cost = if (passiA.isEmpty()) 0.0 else totale / passiA.size
        return Alignment(passiA.toIntArray(), passiB.toIntArray(), cost)
    }

    /**
     * Riporta [source] sui tempi dell'altra curva, per disegnarle sovrapposte.
     * Dove piu' frame di [source] cadono sullo stesso istante si fa la media;
     * dove non ne cade nessuno di sonoro resta `NaN`.
     */
    fun warp(source: FloatArray, alignment: Alignment, targetSize: Int): FloatArray {
        val out = FloatArray(targetSize) { Float.NaN }
        if (alignment.size == 0 || source.isEmpty() || targetSize <= 0) return out

        val somma = DoubleArray(targetSize)
        val quanti = IntArray(targetSize)
        for (k in 0 until alignment.size) {
            val i = alignment.a[k]
            val j = alignment.b[k]
            if (i < 0 || i >= targetSize || j < 0 || j >= source.size) continue
            val v = source[j]
            if (!v.isNaN()) {
                somma[i] += v.toDouble()
                quanti[i]++
            }
        }
        for (i in 0 until targetSize) {
            if (quanti[i] > 0) out[i] = (somma[i] / quanti[i]).toFloat()
        }
        return out
    }

    /** Scarto fra due frame. Un frame muto costa la penale, non la sua distanza da zero. */
    private fun delta(x: Float, y: Float, unvoicedCost: Float): Double = when {
        x.isNaN() && y.isNaN() -> 0.0
        x.isNaN() || y.isNaN() -> unvoicedCost.toDouble()
        else -> abs(x - y).toDouble()
    }
}
