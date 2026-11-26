package com.studiovoce.audio

import com.studiovoce.audio.SilenceDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blocchi finti da 20 ms: niente microfono, niente orologio. */
class SilenceDetectorTest {

    private val blockMs = 20L
    private val maxMs = 10_000L

    /** Fa passare [ms] di audio a livello [rms] e restituisce la prima decisione di stop. */
    private fun feed(
        d: SilenceDetector,
        rms: Float,
        ms: Long,
        maxDurationMs: Long = maxMs,
    ): Decision {
        var left = ms
        while (left > 0) {
            val decision = d.onBlock(rms, blockMs, maxDurationMs)
            if (decision != Decision.CONTINUE) return decision
            left -= blockMs
        }
        return Decision.CONTINUE
    }

    @Test
    fun `si ferma dopo un secondo e mezzo di silenzio`() {
        val d = SilenceDetector()
        assertEquals(Decision.CONTINUE, feed(d, 0.002f, 400))  // taratura + stanza quieta
        assertEquals(Decision.CONTINUE, feed(d, 0.2f, 1_000))  // parla
        assertEquals(Decision.CONTINUE, feed(d, 0.001f, 1_400)) // silenzio, ma non basta
        assertEquals(Decision.STOP_AFTER_SPEECH, feed(d, 0.001f, 200))
        assertTrue(d.speechStarted)
    }

    @Test
    fun `una pausa breve in mezzo alla frase non ferma la registrazione`() {
        val d = SilenceDetector()
        feed(d, 0.002f, 400)
        assertEquals(Decision.CONTINUE, feed(d, 0.2f, 500))
        assertEquals(Decision.CONTINUE, feed(d, 0.001f, 700))   // pausa di 0,7 s
        assertEquals(Decision.CONTINUE, feed(d, 0.2f, 500))     // riprende
        assertEquals(Decision.CONTINUE, feed(d, 0.001f, 1_000)) // di nuovo silenzio, ancora poco
        assertEquals(Decision.STOP_AFTER_SPEECH, feed(d, 0.001f, 600))
    }

    @Test
    fun `se non si sente niente si chiude a vuoto`() {
        val d = SilenceDetector()
        assertEquals(Decision.STOP_NO_SPEECH, feed(d, 0.001f, 8_000))
    }

    @Test
    fun `il tetto di durata vince sempre`() {
        val d = SilenceDetector()
        feed(d, 0.002f, 400)
        assertEquals(Decision.STOP_MAX_DURATION, feed(d, 0.3f, 5_000, maxDurationMs = 2_000))
    }

    @Test
    fun `in un ambiente rumoroso la soglia si alza`() {
        val quiet = SilenceDetector()
        feed(quiet, 0.002f, 400)
        val noisy = SilenceDetector()
        feed(noisy, 0.05f, 400)
        assertTrue("in macchina la soglia deve essere piu' alta", noisy.threshold > quiet.threshold)

        // Con il rumore dell'auto a 0,05 un parlato a 0,08 non deve bastare a "parlare".
        noisy.onBlock(0.08f, blockMs, maxMs)
        assertTrue(!noisy.speechStarted)
        noisy.onBlock(0.4f, blockMs, maxMs)
        assertTrue(noisy.speechStarted)
    }

    @Test
    fun `segna inizio e fine del parlato`() {
        val d = SilenceDetector()
        feed(d, 0.001f, 400)
        feed(d, 0.3f, 600)
        feed(d, 0.001f, 100)
        assertTrue("inizio atteso intorno a 400 ms, era ${d.speechStartMs}",
            d.speechStartMs in 380L..440L)
        assertTrue("fine attesa intorno a 1000 ms, era ${d.speechEndMs}",
            d.speechEndMs in 980L..1040L)
    }

    @Test
    fun `reset riporta tutto allo stato iniziale`() {
        val d = SilenceDetector()
        feed(d, 0.002f, 400)
        feed(d, 0.3f, 200)
        assertTrue(d.speechStarted)
        d.reset()
        assertTrue(!d.speechStarted)
        assertEquals(-1L, d.speechStartMs)
        assertEquals(Decision.STOP_NO_SPEECH, feed(d, 0.001f, 8_000))
    }

    @Test
    fun `il tetto di durata segue la lunghezza della clip`() {
        val c = RecordingConfig()
        assertEquals(6_000L, c.maxDurationMs(2.0))   // 2*2 + 2 s
        assertEquals(22_000L, c.maxDurationMs(10.0)) // 10*2 + 2 s
        assertTrue(c.maxDurationMs(0.1) >= 2_000L)
    }
}
