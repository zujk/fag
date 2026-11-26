package com.studiovoce.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class WaveformTest {

    @Test
    fun `l'inviluppo ha una colonna per bucket`() {
        val peaks = Waveform.peaks(FloatArray(1000) { 0.5f }, 64)
        assertEquals(64, peaks.size)
        for (p in peaks) assertEquals(0.5f, p, 1e-6f)
    }

    @Test
    fun `l'inviluppo segue dove sta il suono`() {
        // Prima metà muta, seconda metà a mezzo volume.
        val s = FloatArray(1000) { if (it < 500) 0f else 0.5f }
        val peaks = Waveform.peaks(s, 10)
        for (i in 0 until 5) assertEquals("colonna $i", 0f, peaks[i], 1e-6f)
        for (i in 5 until 10) assertEquals("colonna $i", 0.5f, peaks[i], 1e-6f)
    }

    @Test
    fun `un segnale vuoto da un inviluppo piatto`() {
        val peaks = Waveform.peaks(FloatArray(0), 32)
        assertEquals(32, peaks.size)
        for (p in peaks) assertEquals(0f, p, 0f)
    }

    @Test
    fun `piu bucket che campioni non manda in crisi`() {
        val peaks = Waveform.peaks(FloatArray(3) { 1f }, 100)
        assertEquals(100, peaks.size)
        assertTrue(peaks.all { it in 0f..1f })
        assertTrue(peaks.any { it > 0f })
    }

    @Test
    fun `il valore efficace di un seno e l'ampiezza diviso radice di due`() {
        val sr = 44_100
        val s = FloatArray(sr) { (0.8 * sin(2 * PI * 440 * it / sr)).toFloat() }
        assertEquals(0.8f / 1.41421f, Waveform.rms(s), 1e-3f)
    }

    @Test
    fun `il valore efficace del silenzio e zero`() {
        assertEquals(0f, Waveform.rms(FloatArray(500)), 0f)
        assertEquals(0f, Waveform.rms(FloatArray(0)), 0f)
        assertEquals(0f, Waveform.rms(ShortArray(0)), 0f)
    }

    @Test
    fun `il valore efficace su PCM 16 bit coincide con quello su float`() {
        val sr = 44_100
        val f = FloatArray(2000) { (0.5 * sin(2 * PI * 300 * it / sr)).toFloat() }
        val s = ShortArray(2000) { WavIo.toPcm16(f[it]) }
        assertEquals(Waveform.rms(f), Waveform.rms(s), 1e-3f)
    }

    @Test
    fun `il picco trova il massimo assoluto`() {
        assertEquals(0.9f, Waveform.peak(floatArrayOf(0.1f, -0.9f, 0.3f)), 1e-6f)
        assertEquals(0f, Waveform.peak(FloatArray(0)), 0f)
    }

    @Test
    fun `il valore efficace si puo calcolare su una finestra`() {
        val s = FloatArray(200) { if (it < 100) 0f else 1f }
        assertEquals(0f, Waveform.rms(s, 0, 100), 1e-6f)
        assertEquals(1f, Waveform.rms(s, 100, 100), 1e-6f)
    }
}
