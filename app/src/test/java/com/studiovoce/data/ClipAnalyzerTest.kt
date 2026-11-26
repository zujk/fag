package com.studiovoce.data

import com.studiovoce.data.db.ClipAnalysisEntity
import com.studiovoce.dsp.ContourBlob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Le misure della clip (SPEC §4.4), su segnali sintetici: la parte che calcola
 * non tocca ne' file ne' database, quindi si verifica sulla JVM.
 */
class ClipAnalyzerTest {

    private val sr = 44_100

    private fun sine(hz: Double, seconds: Double, amp: Float = 0.5f) =
        FloatArray((seconds * sr).toInt()) { (amp * sin(2 * PI * hz * it / sr)).toFloat() }

    private fun analisi(
        f0Med: Double? = null,
        f0P10: Double? = null,
        f0P90: Double? = null,
        f1Med: Double? = null,
        f2Med: Double? = null,
        durationS: Double? = null,
        voicedPct: Double? = null,
        contourBlob: ByteArray? = null,
    ) = ClipAnalysisEntity(
        clipId = "c1",
        f0Med = f0Med, f0P10 = f0P10, f0P90 = f0P90,
        f1Med = f1Med, f2Med = f2Med,
        durationS = durationS, voicedPct = voicedPct, contourBlob = contourBlob,
    )

    // --- il calcolo -----------------------------------------------------------

    @Test
    fun `su un seno a 200 Hz le misure dicono 200 Hz`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)

        assertNotNull(m.f0Med)
        assertTrue("mediana ${m.f0Med}", abs(m.f0Med!! - 200.0) / 200.0 < 0.01)
        assertTrue("p10 ${m.f0P10}", abs(m.f0P10!! - 200.0) / 200.0 < 0.01)
        assertTrue("p90 ${m.f0P90}", abs(m.f0P90!! - 200.0) / 200.0 < 0.01)
        assertEquals(100.0, m.voicedPct, 0.01)
    }

    @Test
    fun `la durata e quella del file, non quella della curva`() {
        // La curva finisce una finestra prima della fine del file: la durata della
        // clip resta quella dei campioni.
        val m = ClipAnalyzer.analyze(sine(200.0, 1.5), sr)
        assertEquals(1.5, m.durationS, 1e-6)
    }

    @Test
    fun `nel silenzio non si inventano misure`() {
        val m = ClipAnalyzer.analyze(FloatArray(sr), sr)

        assertNull(m.f0Med)
        assertNull(m.f0P10)
        assertNull(m.f0P90)
        assertEquals(0.0, m.voicedPct, 1e-9)
        assertEquals(1.0, m.durationS, 1e-6)
        // La curva c'e' comunque, tutta muta: serve a disegnare il buco.
        val curva = ContourBlob.decode(m.contourBlob)
        assertTrue(curva.semitones.isNotEmpty())
        assertEquals(0, curva.voicedCount)
    }

    @Test
    fun `una clip mezza muta ha circa meta del parlato`() {
        val voce = sine(200.0, 0.5)
        val s = FloatArray(sr) { if (it < sr / 2) 0f else voce[it - sr / 2] }
        val m = ClipAnalyzer.analyze(s, sr)

        assertTrue("parlato al ${m.voicedPct}%", m.voicedPct in 40.0..60.0)
    }

    @Test
    fun `la curva salvata si rilegge e torna la stessa`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)
        val riletta = ContourBlob.decode(m.contourBlob)

        assertEquals(m.contour.semitones.size, riletta.semitones.size)
        assertEquals(m.contour.medianHz, riletta.medianHz, 1e-4f)
        assertEquals(m.contour.hopMs, riletta.hopMs, 1e-9)
    }

    @Test
    fun `una clip troppo corta non manda in errore`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 0.01), sr)

        assertNull(m.f0Med)
        assertEquals(0.0, m.voicedPct, 1e-9)
        assertEquals(0.01, m.durationS, 1e-6)
        assertEquals(0, ContourBlob.decode(m.contourBlob).semitones.size)
    }

    // --- cosa si sovrascrive e cosa no ---------------------------------------

    @Test
    fun `al primo calcolo le misure sono quelle dell'app`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)
        val riga = ClipAnalyzer.merge("c1", esistente = null, misure = m)

        assertEquals(m.f0Med!!, riga.f0Med!!, 1e-9)
        assertEquals(m.voicedPct, riga.voicedPct!!, 1e-9)
        assertEquals(1.0, riga.durationS!!, 1e-6)
        assertNotNull(riga.contourBlob)
        assertNull("le formanti arrivano con la v3", riga.f1Med)
        assertNull(riga.f2Med)
    }

    @Test
    fun `le misure del foglio non vengono sovrascritte`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)
        val dalFoglio = analisi(f0Med = 188.0, f0P10 = 150.0, f0P90 = 230.0, durationS = 2.2)
        val riga = ClipAnalyzer.merge("c1", dalFoglio, m)

        assertEquals(188.0, riga.f0Med!!, 1e-9)
        assertEquals(150.0, riga.f0P10!!, 1e-9)
        assertEquals(230.0, riga.f0P90!!, 1e-9)
        assertEquals(2.2, riga.durationS!!, 1e-9)
        // Il parlato il foglio non lo dava: lo mette l'app.
        assertEquals(m.voicedPct, riga.voicedPct!!, 1e-9)
    }

    @Test
    fun `le formanti del foglio si conservano`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)
        val riga = ClipAnalyzer.merge("c1", analisi(f1Med = 700.0, f2Med = 1200.0), m)

        assertEquals(700.0, riga.f1Med!!, 1e-9)
        assertEquals(1200.0, riga.f2Med!!, 1e-9)
    }

    @Test
    fun `la curva la mette sempre l'app`() {
        val m = ClipAnalyzer.analyze(sine(200.0, 1.0), sr)
        val vecchia = analisi(contourBlob = byteArrayOf(1, 2, 3))
        val riga = ClipAnalyzer.merge("c1", vecchia, m)

        assertTrue("la curva vecchia non va tenuta", riga.contourBlob!!.size > 3)
        assertEquals(m.contourBlob.size, riga.contourBlob!!.size)
    }

    // --- quando serve ricalcolare -------------------------------------------

    @Test
    fun `serve calcolare se la riga non c'e o non ha la curva`() {
        assertTrue(ClipAnalyzer.needsAnalysis(null))
        assertTrue(ClipAnalyzer.needsAnalysis(analisi(f0Med = 188.0)))
        assertFalse(ClipAnalyzer.needsAnalysis(analisi(contourBlob = byteArrayOf(1))))
    }
}
