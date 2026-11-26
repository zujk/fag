package com.studiovoce.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * YIN si verifica solo su segnali di cui si conosce l'altezza esatta: a orecchio,
 * in emulatore, un errore del 5% non si sente. I criteri della SPEC §10 sono i
 * primi due test; gli altri coprono i modi in cui un rilevatore di altezza
 * sbaglia di solito, cioe' l'errore di ottava e l'altezza trovata nel silenzio.
 */
class YinTest {

    private val sr = 44_100

    // --- segnali sintetici ---------------------------------------------------

    private fun sine(hz: Double, seconds: Double, rate: Int = sr, amp: Float = 0.5f) =
        FloatArray((seconds * rate).toInt()) {
            (amp * sin(2 * PI * hz * it / rate)).toFloat()
        }

    /** Seno che scivola da [from] a [to] in modo lineare (chirp). */
    private fun glide(from: Double, to: Double, seconds: Double, rate: Int = sr) =
        FloatArray((seconds * rate).toInt()) {
            val t = it / rate.toDouble()
            val fase = 2 * PI * (from * t + (to - from) * t * t / (2 * seconds))
            (0.5 * sin(fase)).toFloat()
        }

    /** Onda ricca di armoniche: e' qui che nascono gli errori di ottava. */
    private fun harmonics(hz: Double, seconds: Double, n: Int, rate: Int = sr) =
        FloatArray((seconds * rate).toInt()) { i ->
            var v = 0.0
            for (k in 1..n) v += sin(2 * PI * k * hz * i / rate) / k
            (0.4 * v).toFloat()
        }

    private fun noise(seconds: Double, amp: Float = 0.3f, rate: Int = sr): FloatArray {
        val r = Random(20_261_126L)
        return FloatArray((seconds * rate).toInt()) { (r.nextGaussian() * amp).toFloat() }
    }

    private fun FloatArray.sonori(): List<Float> = filter { !it.isNaN() }

    private fun mediana(v: List<Float>): Float = v.sorted()[v.size / 2]

    private fun rms(v: FloatArray): Double {
        var s = 0.0
        for (x in v) s += x.toDouble() * x
        return sqrt(s / v.size)
    }

    // --- criteri di accettazione della SPEC §10 ------------------------------

    @Test
    fun `su seni a 100, 180 e 250 Hz l'errore sta sotto l'uno per cento`() {
        for (hz in listOf(100.0, 180.0, 250.0)) {
            val curva = Yin.curve(sine(hz, 1.0), sr)
            val sonori = curva.sonori()
            assertTrue(
                "a $hz Hz troppi frame muti: ${sonori.size} su ${curva.size}",
                sonori.size > 80,
            )

            val med = mediana(sonori)
            assertTrue("a $hz Hz la mediana e' $med", abs(med - hz) / hz < 0.01)

            val entro = sonori.count { abs(it - hz) / hz < 0.01 }
            assertTrue(
                "a $hz Hz solo $entro frame su ${sonori.size} entro l'1%",
                entro >= sonori.size * 9 / 10,
            )
        }
    }

    @Test
    fun `su un seno che scivola da 150 a 250 Hz la curva e monotona`() {
        val curva = Yin.curve(glide(150.0, 250.0, 2.0), sr)
        val sonori = curva.sonori()
        assertTrue("troppi frame muti: ${sonori.size}", sonori.size > 150)

        assertTrue("parte da ${sonori.first()}", abs(sonori.first() - 150f) / 150f < 0.05)
        assertTrue("arriva a ${sonori.last()}", abs(sonori.last() - 250f) / 250f < 0.05)

        val cali = (1 until sonori.size).filter { sonori[it] < sonori[it - 1] - 1f }
        assertEquals("la curva deve salire sempre, cala agli indici $cali", 0, cali.size)
    }

    // --- come sbaglia un rilevatore di altezza ------------------------------

    @Test
    fun `su un'onda ricca di armoniche non sbaglia l'ottava`() {
        val curva = Yin.curve(harmonics(120.0, 1.0, 10), sr)
        val sonori = curva.sonori()
        assertTrue("troppi frame muti: ${sonori.size}", sonori.size > 80)
        // Sbagliare l'ottava darebbe 60 o 240 Hz: lo scarto sarebbe enorme, non dell'1%.
        for (f in sonori) assertTrue("trovato $f invece di 120", abs(f - 120f) / 120f < 0.02)
    }

    @Test
    fun `nel silenzio non trova nessuna altezza`() {
        val curva = Yin.curve(FloatArray(sr), sr)
        assertTrue("la curva e' vuota", curva.isNotEmpty())
        assertEquals("nel silenzio non ci sono frame sonori", 0, curva.sonori().size)
    }

    @Test
    fun `nel rumore quasi tutti i frame restano muti`() {
        val curva = Yin.curve(noise(1.0), sr)
        val sonori = curva.sonori().size
        assertTrue("$sonori frame su ${curva.size} hanno un'altezza", sonori < curva.size * 3 / 10)
    }

    @Test
    fun `distingue la parte muta da quella sonora`() {
        // Mezzo secondo di silenzio, poi mezzo secondo a 200 Hz.
        val voce = sine(200.0, 0.5)
        val s = FloatArray(sr) { if (it < sr / 2) 0f else voce[it - sr / 2] }
        val curva = Yin.curve(s, sr)

        // Frame che finiscono prima dell'inizio della voce: indice * 10 ms + 40 ms < 500 ms.
        for (i in 0 until 45) {
            assertTrue("il frame $i cade nel silenzio ma vale ${curva[i]}", curva[i].isNaN())
        }
        // Frame che cadono dentro la voce.
        val dentro = (52 until curva.size).map { curva[it] }.filter { !it.isNaN() }
        assertTrue("troppi frame muti sulla voce: ${dentro.size}", dentro.size > 30)
        assertTrue("mediana ${mediana(dentro)}", abs(mediana(dentro) - 200f) / 200f < 0.01)
    }

    @Test
    fun `un'altezza fuori dall'intervallo cercato non viene inventata`() {
        // 60 Hz sta sotto il minimo di 75 Hz: meglio nessuna risposta che una sbagliata.
        val curva = Yin.curve(sine(60.0, 1.0), sr)
        assertTrue(
            "trovati ${curva.sonori().size} frame sonori a 60 Hz",
            curva.sonori().size < curva.size / 10,
        )
    }

    // --- forma della curva e indipendenza dal campionamento -----------------

    @Test
    fun `funziona anche su un file a 22050 Hz`() {
        val curva = Yin.curve(sine(200.0, 1.0, rate = 22_050), 22_050)
        val sonori = curva.sonori()
        assertTrue("troppi frame muti: ${sonori.size}", sonori.size > 80)
        assertTrue("mediana ${mediana(sonori)}", abs(mediana(sonori) - 200f) / 200f < 0.01)
    }

    @Test
    fun `un segnale piu corto di una finestra da una curva vuota`() {
        assertEquals(0, Yin.curve(sine(200.0, 0.02), sr).size)
        assertEquals(0, Yin.curve(FloatArray(0), sr).size)
    }

    @Test
    fun `il tempo di un frame e il centro della sua finestra`() {
        // Finestra 40 ms, passo 10 ms: il primo frame e' centrato a 20 ms.
        assertEquals(20.0, Yin.frameCenterMs(0), 1e-9)
        assertEquals(30.0, Yin.frameCenterMs(1), 1e-9)
        // Un secondo di audio con finestra 40 ms e passo 10 ms: 97 frame.
        assertEquals(97, Yin.curve(sine(200.0, 1.0), sr).size)
    }

    // --- sottocampionamento --------------------------------------------------

    @Test
    fun `il fattore di sottocampionamento porta vicino a 22 kHz`() {
        assertEquals(2, Yin.factorFor(44_100, 22_050))
        assertEquals(2, Yin.factorFor(48_000, 22_050))
        assertEquals(1, Yin.factorFor(22_050, 22_050))
        assertEquals(1, Yin.factorFor(16_000, 22_050))
        assertEquals(4, Yin.factorFor(96_000, 22_050))
    }

    @Test
    fun `sottocampionare dimezza i campioni e conserva l'altezza`() {
        val s = sine(200.0, 1.0)
        val giu = Yin.decimate(s, 2)
        assertEquals(s.size / 2, giu.size)
        val curva = Yin.curve(giu, sr / 2)
        assertTrue(
            "mediana ${mediana(curva.sonori())}",
            abs(mediana(curva.sonori()) - 200f) / 200f < 0.01,
        )
    }

    @Test
    fun `sottocampionare taglia le frequenze che non entrano`() {
        // 15 kHz non sta sotto la nuova Nyquist di 11 kHz: senza filtro si
        // ripiegherebbe su una frequenza bassa e falserebbe l'altezza.
        val alta = sine(15_000.0, 0.5)
        val giu = Yin.decimate(alta, 2)
        assertTrue(
            "attenuazione insufficiente: ${rms(giu)} contro ${rms(alta)}",
            rms(giu) < rms(alta) * 0.1,
        )

        // Una frequenza che ci sta non deve essere toccata.
        val bassa = sine(200.0, 0.5)
        val bassaGiu = Yin.decimate(bassa, 2)
        assertEquals(rms(bassa), rms(bassaGiu), rms(bassa) * 0.05)
    }
}
