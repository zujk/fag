package com.studiovoce.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * DTW serve a mettere le due curve sugli stessi tempi prima di confrontarle
 * (SPEC §5.3): una ripetizione piu' lenta o piu' veloce dell'originale ha la
 * stessa forma, e il confronto deve vederlo.
 */
class DtwTest {

    private val nan = Float.NaN

    private fun rampa(n: Int, da: Float = 0f, passo: Float = 1f) =
        FloatArray(n) { da + it * passo }

    /** Ogni valore ripetuto [volte] volte: la stessa melodia, piu' lenta. */
    private fun dilata(v: FloatArray, volte: Int) =
        FloatArray(v.size * volte) { v[it / volte] }

    @Test
    fun `due curve identiche si allineano in diagonale`() {
        val a = rampa(12)
        val al = Dtw.align(a, a.copyOf())

        assertEquals(12, al.size)
        assertEquals(0.0, al.cost, 1e-9)
        for (k in 0 until al.size) {
            assertEquals("coppia $k", al.a[k], al.b[k])
            assertEquals("coppia $k", k, al.a[k])
        }
    }

    @Test
    fun `il percorso parte dall'inizio e finisce alla fine`() {
        val a = rampa(9)
        val b = rampa(14, passo = 0.5f)
        val al = Dtw.align(a, b)

        assertEquals(0, al.a.first())
        assertEquals(0, al.b.first())
        assertEquals(8, al.a.last())
        assertEquals(13, al.b.last())
    }

    @Test
    fun `il percorso non torna mai indietro e non salta`() {
        val a = floatArrayOf(0f, 2f, 4f, 3f, 1f, 0f, -2f)
        val b = floatArrayOf(0f, 1f, 3f, 4f, 4f, 2f, 0f, -1f, -2f, -2f)
        val al = Dtw.align(a, b)

        for (k in 1 until al.size) {
            val passoA = al.a[k] - al.a[k - 1]
            val passoB = al.b[k] - al.b[k - 1]
            assertTrue("indietro su a al passo $k", passoA >= 0)
            assertTrue("indietro su b al passo $k", passoB >= 0)
            assertTrue("salto su a al passo $k", passoA <= 1)
            assertTrue("salto su b al passo $k", passoB <= 1)
            assertTrue("passo fermo su entrambi a $k", passoA + passoB >= 1)
        }
        // Ogni frame di entrambe le curve deve comparire almeno una volta.
        assertEquals(a.indices.toSet(), al.a.toSet())
        assertEquals(b.indices.toSet(), al.b.toSet())
    }

    @Test
    fun `una curva dilatata nel tempo si riporta sulla forma originale`() {
        val a = rampa(20)
        val lenta = dilata(a, 2)
        val al = Dtw.align(a, lenta)

        val riportata = Dtw.warp(lenta, al, a.size)
        assertEquals(a.size, riportata.size)
        for (i in a.indices) {
            assertTrue("frame $i: ${riportata[i]} invece di ${a[i]}",
                abs(riportata[i] - a[i]) < 1f)
        }
    }

    @Test
    fun `uno scarto costante di tre semitoni costa tre`() {
        // Una gobba non si puo' far combaciare con la stessa gobba piu' in alto
        // spostandola nel tempo: lo scarto resta, e la diagonale e' la via migliore.
        val gobba = floatArrayOf(0f, 1f, 2f, 3f, 2f, 1f, 0f)
        val piuSu = FloatArray(gobba.size) { gobba[it] + 3f }

        assertEquals(0.0, Dtw.align(gobba, gobba.copyOf()).cost, 1e-9)
        assertEquals(3.0, Dtw.align(gobba, piuSu).cost, 1e-9)
    }

    @Test
    fun `su una curva che sale sempre uno scarto di altezza somiglia a uno di tempo`() {
        // Su una rampa, cantare 3 semitoni piu' in alto da' la stessa curva
        // qualche istante dopo: DTW la fa combaciare quasi a costo zero.
        // E' il motivo per cui il riscontro a parole non guarda questo costo, ma
        // chiusura, escursione e durata (SPEC §5.5).
        val a = rampa(15)
        val piuSu = FloatArray(15) { a[it] + 3f }
        val costo = Dtw.align(a, piuSu).cost

        assertTrue("costo $costo, doveva restare basso", costo < 1.0)
        assertTrue("costo $costo, non puo' essere nullo", costo > 0.0)
    }

    @Test
    fun `il costo e la media per coppia, non la somma`() {
        // Due curve costanti a 2 semitoni di distanza: la media e' 2 qualunque
        // sia la lunghezza, mentre la somma crescerebbe con i frame.
        val corta = Dtw.align(FloatArray(5) { 0f }, FloatArray(5) { 2f }).cost
        val lunga = Dtw.align(FloatArray(50) { 0f }, FloatArray(50) { 2f }).cost
        assertEquals(2.0, corta, 1e-6)
        assertEquals(2.0, lunga, 1e-6)
    }

    // --- frame muti ----------------------------------------------------------

    @Test
    fun `i frame muti non si confrontano come se fossero zero`() {
        // Se NaN valesse 0, questa curva sembrerebbe scendere a zero in mezzo e
        // il costo esploderebbe. Il frame muto costa una penale fissa.
        val a = floatArrayOf(5f, 5f, 5f, 5f)
        val conBuco = floatArrayOf(5f, nan, nan, 5f)
        val costo = Dtw.align(a, conBuco, unvoicedCost = 2f).cost

        assertTrue("costo $costo", costo.isFinite())
        assertTrue("costo $costo, doveva stare entro la penale", costo <= 2.0)
    }

    @Test
    fun `due curve mute fra loro non costano niente`() {
        val a = FloatArray(6) { nan }
        assertEquals(0.0, Dtw.align(a, a.copyOf()).cost, 1e-9)
    }

    @Test
    fun `riportare una curva lascia muti i frame senza corrispondenza`() {
        val a = floatArrayOf(0f, 0f, 0f, 0f)
        val b = floatArrayOf(nan, nan, nan, nan)
        val riportata = Dtw.warp(b, Dtw.align(a, b), a.size)
        for (i in riportata.indices) assertTrue("frame $i", riportata[i].isNaN())
    }

    @Test
    fun `riportare fa la media dei frame che cadono sullo stesso istante`() {
        // Due frame di b finiscono sul frame 1 di a: 10 e 20 danno 15.
        val a = floatArrayOf(0f, 15f, 30f)
        val b = floatArrayOf(0f, 10f, 20f, 30f)
        val riportata = Dtw.warp(b, Dtw.align(a, b), a.size)
        assertEquals(0f, riportata[0], 1e-4f)
        assertEquals(15f, riportata[1], 1e-4f)
        assertEquals(30f, riportata[2], 1e-4f)
    }

    // --- casi limite ---------------------------------------------------------

    @Test
    fun `due curve vuote danno un allineamento vuoto`() {
        val al = Dtw.align(FloatArray(0), FloatArray(0))
        assertEquals(0, al.size)
        assertEquals(0.0, al.cost, 1e-9)
    }

    @Test
    fun `se una curva e vuota non c'e niente da allineare`() {
        assertEquals(0, Dtw.align(rampa(10), FloatArray(0)).size)
        assertEquals(0, Dtw.align(FloatArray(0), rampa(10)).size)

        // Riportare qualcosa che non c'e' da' una curva tutta muta, non una vuota:
        // chi disegna si aspetta sempre un valore per frame.
        val riportata = Dtw.warp(FloatArray(0), Dtw.align(rampa(3), FloatArray(0)), 3)
        assertEquals(3, riportata.size)
        for (v in riportata) assertTrue(v.isNaN())
    }

    @Test
    fun `una curva di un solo frame si allinea con tutto`() {
        val al = Dtw.align(floatArrayOf(1f), rampa(5))
        assertEquals(5, al.size)
        for (k in 0 until al.size) assertEquals(0, al.a[k])
        assertEquals((0..4).toList(), al.b.toList())
    }
}
