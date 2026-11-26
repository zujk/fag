package com.studiofagnani.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La curva si confronta in semitoni rispetto alla propria mediana (SPEC §5.3):
 * cosi' si guarda la forma della melodia e non l'altezza assoluta, che fra due
 * voci diverse e' sempre diversa e non dice niente.
 */
class ContourTest {

    private val nan = Float.NaN

    @Test
    fun `una curva piatta sta tutta a zero semitoni`() {
        val c = Contour.fromHz(floatArrayOf(200f, 200f, 200f))
        assertEquals(200f, c.medianHz, 1e-4f)
        for (v in c.semitones) assertEquals(0f, v, 1e-4f)
    }

    @Test
    fun `un'ottava sopra la mediana fa piu dodici semitoni`() {
        // Mediana 200: 400 e' un'ottava sopra, 100 un'ottava sotto.
        val c = Contour.fromHz(floatArrayOf(100f, 200f, 400f))
        assertEquals(200f, c.medianHz, 1e-4f)
        assertEquals(-12f, c.semitones[0], 1e-3f)
        assertEquals(0f, c.semitones[1], 1e-3f)
        assertEquals(12f, c.semitones[2], 1e-3f)
    }

    @Test
    fun `i frame muti restano muti e non contano`() {
        val c = Contour.fromHz(floatArrayOf(nan, 200f, nan, 200f, nan))
        assertTrue(c.semitones[0].isNaN())
        assertTrue(c.semitones[2].isNaN())
        assertEquals(2, c.voicedCount)
        assertEquals(40.0, c.voicedPct, 1e-9)
        assertEquals(200f, c.medianHz, 1e-4f)
    }

    @Test
    fun `una curva senza parlato sonoro non ha mediana`() {
        val c = Contour.fromHz(floatArrayOf(nan, nan, nan))
        assertTrue(c.medianHz.isNaN())
        assertEquals(0, c.voicedCount)
        assertEquals(0.0, c.voicedPct, 1e-9)
        assertTrue(c.percentile(0.10).isNaN())
        assertTrue(c.range.isNaN())
        for (v in c.semitones) assertTrue(v.isNaN())
    }

    @Test
    fun `i percentili si calcolano sui soli frame sonori`() {
        // Undici valori a distanza di un semitono, mediana al centro.
        val hz = FloatArray(11) { (200.0 * Math.pow(2.0, (it - 5) / 12.0)).toFloat() }
        val c = Contour.fromHz(hz)
        assertEquals(-5f, c.percentile(0.0), 1e-3f)
        assertEquals(0f, c.percentile(0.5), 1e-3f)
        assertEquals(5f, c.percentile(1.0), 1e-3f)
        assertEquals(-4f, c.p10, 1e-3f)
        assertEquals(4f, c.p90, 1e-3f)
        assertEquals(8f, c.range, 1e-3f)
    }

    @Test
    fun `i percentili esistono anche in hertz`() {
        val c = Contour.fromHz(floatArrayOf(100f, 200f, 400f))
        assertEquals(200f, c.percentileHz(0.5), 1e-2f)
        assertEquals(100f, c.percentileHz(0.0), 1e-2f)
        assertEquals(400f, c.percentileHz(1.0), 1e-2f)
    }

    @Test
    fun `un solo frame sonoro da percentili tutti uguali`() {
        val c = Contour.fromHz(floatArrayOf(nan, 180f, nan))
        assertEquals(0f, c.p10, 1e-4f)
        assertEquals(0f, c.p90, 1e-4f)
        assertEquals(0f, c.range, 1e-4f)
        assertEquals(180f, c.medianHz, 1e-4f)
    }

    @Test
    fun `la durata viene dal passo fra i frame`() {
        val c = Contour.fromHz(FloatArray(100) { 200f }, hopMs = 10.0)
        assertEquals(1000.0, c.durationMs, 1e-9)
        assertEquals(1.0, c.durationS, 1e-9)
    }

    // --- durata del parlato e chiusura --------------------------------------

    @Test
    fun `la durata del parlato ignora il silenzio davanti e dietro`() {
        // Trenta frame in tutto, parlato dal 10 al 19: trecento millisecondi.
        val hz = FloatArray(30) { if (it in 10..19) 200f else nan }
        val c = Contour.fromHz(hz, hopMs = 10.0)

        assertEquals(10, c.firstVoicedIndex)
        assertEquals(19, c.lastVoicedIndex)
        assertEquals(100.0, c.voicedSpanMs, 1e-9)
        assertEquals(0.1, c.voicedSpanS, 1e-9)
        // La durata del file invece li conta tutti.
        assertEquals(300.0, c.durationMs, 1e-9)
    }

    @Test
    fun `senza parlato la durata del parlato e zero`() {
        val c = Contour.fromHz(FloatArray(20) { nan })
        assertEquals(-1, c.firstVoicedIndex)
        assertEquals(-1, c.lastVoicedIndex)
        assertEquals(0.0, c.voicedSpanMs, 1e-9)
        assertTrue(c.closingDelta(300.0).isNaN())
    }

    @Test
    fun `la chiusura dice di quanto scende la fine della frase`() {
        // Mediana 200. Gli ultimi 300 ms (30 frame) scendono di un'ottava.
        val hz = FloatArray(60) { if (it < 30) 200f else 200f * Math.pow(2.0, -(it - 29) / 12.0 / 2.5).toFloat() }
        val c = Contour.fromHz(hz, hopMs = 10.0)
        val delta = c.closingDelta(300.0)
        assertTrue("la chiusura deve scendere, vale $delta", delta < -2f)
    }

    @Test
    fun `una chiusura che sale da un valore positivo`() {
        val su = Contour.fromHz(FloatArray(40) { 200f + it * 5f }, hopMs = 10.0)
        assertTrue("vale ${su.closingDelta(200.0)}", su.closingDelta(200.0) > 0f)

        val piatta = Contour.fromHz(FloatArray(40) { 200f }, hopMs = 10.0)
        assertEquals(0f, piatta.closingDelta(200.0), 1e-4f)
    }

    @Test
    fun `la chiusura si misura dall'ultimo frame sonoro, non dalla fine del file`() {
        // Frase che scende, poi mezzo secondo di coda muta: la coda non conta.
        val frase = FloatArray(30) { 300f - it * 5f }
        val conCoda = FloatArray(80) { if (it < 30) frase[it] else nan }
        val senzaCoda = Contour.fromHz(frase, hopMs = 10.0)

        assertEquals(
            senzaCoda.closingDelta(200.0),
            Contour.fromHz(conCoda, hopMs = 10.0).closingDelta(200.0),
            1e-4f,
        )
    }

    @Test
    fun `un solo frame sonoro non ha una chiusura`() {
        val c = Contour.fromHz(floatArrayOf(nan, 200f, nan), hopMs = 10.0)
        assertTrue(c.closingDelta(300.0).isNaN())
    }

    @Test
    fun `una curva vuota non fa errore`() {
        val c = Contour.fromHz(FloatArray(0))
        assertEquals(0, c.semitones.size)
        assertEquals(0.0, c.durationMs, 1e-9)
        assertEquals(0.0, c.voicedPct, 1e-9)
        assertTrue(c.medianHz.isNaN())
    }

    @Test
    fun `le frequenze non valide si trattano come frame muti`() {
        // Uno zero o un negativo in ingresso sarebbe un logaritmo di zero.
        val c = Contour.fromHz(floatArrayOf(0f, -5f, 200f, 200f))
        assertTrue(c.semitones[0].isNaN())
        assertTrue(c.semitones[1].isNaN())
        assertEquals(2, c.voicedCount)
    }

    // --- codifica per il database (SPEC §8, contour_blob) --------------------

    @Test
    fun `la curva va nel database e torna identica`() {
        val c = Contour.fromHz(floatArrayOf(nan, 150f, 200f, 300f, nan), hopMs = 10.0)
        val tornata = ContourBlob.decode(ContourBlob.encode(c))

        assertEquals(c.hopMs, tornata.hopMs, 1e-9)
        assertEquals(c.medianHz, tornata.medianHz, 1e-6f)
        assertEquals(c.semitones.size, tornata.semitones.size)
        for (i in c.semitones.indices) {
            if (c.semitones[i].isNaN()) {
                assertTrue("il frame $i doveva restare muto", tornata.semitones[i].isNaN())
            } else {
                assertEquals(c.semitones[i], tornata.semitones[i], 1e-6f)
            }
        }
    }

    @Test
    fun `una curva vuota si codifica e si rilegge`() {
        val tornata = ContourBlob.decode(ContourBlob.encode(Contour.fromHz(FloatArray(0))))
        assertEquals(0, tornata.semitones.size)
        assertTrue(tornata.medianHz.isNaN())
    }

    @Test
    fun `la codifica ha la dimensione attesa`() {
        // Intestazione di 16 byte piu' quattro byte per frame.
        val c = Contour.fromHz(FloatArray(10) { 200f })
        assertEquals(16 + 10 * 4, ContourBlob.encode(c).size)
    }

    @Test
    fun `un blob rovinato non passa per buono`() {
        val buono = ContourBlob.encode(Contour.fromHz(floatArrayOf(200f, 300f)))

        // Troppo corto.
        assertThrows { ContourBlob.decode(buono.copyOf(7)) }
        // Firma sbagliata.
        val firma = buono.copyOf()
        firma[0] = 'X'.code.toByte()
        assertThrows { ContourBlob.decode(firma) }
        // Numero di frame che non corrisponde ai byte presenti.
        assertThrows { ContourBlob.decode(buono.copyOf(buono.size - 2)) }
    }

    @Test
    fun `i semitoni si ricavano anche da due frequenze qualsiasi`() {
        assertEquals(12f, Contour.semitones(400f, 200f), 1e-4f)
        assertEquals(0f, Contour.semitones(200f, 200f), 1e-4f)
        assertEquals(-12f, Contour.semitones(100f, 200f), 1e-4f)
        assertTrue(Contour.semitones(0f, 200f).isNaN())
        assertTrue(Contour.semitones(200f, 0f).isNaN())
    }

    @Test
    fun `i valori sonori sopravvivono a un giro completo sulle frequenze`() {
        val hz = floatArrayOf(137f, 211f, 298f, 190f)
        val c = Contour.fromHz(hz)
        val rifatte = FloatArray(hz.size) { c.hzAt(it) }
        assertArrayEquals(hz, rifatte, 1e-2f)
    }

    private fun assertThrows(blocco: () -> Unit) {
        try {
            blocco()
        } catch (atteso: IllegalArgumentException) {
            return
        }
        throw AssertionError("doveva rifiutare il blob")
    }
}
