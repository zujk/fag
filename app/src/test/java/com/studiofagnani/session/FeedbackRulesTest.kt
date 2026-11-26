package com.studiofagnani.session

import com.studiofagnani.dsp.Contour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le sei regole della SPEC §5.5, una per test, su curve costruite a mano.
 *
 * Ogni caso e' fatto perche' scatti **una sola** regola: se un test passasse
 * perche' ne sono scattate due, non direbbe niente su quella che voleva provare.
 */
class FeedbackRulesTest {

    private val nan = Float.NaN

    /** Curva in semitoni, con la mediana fissata: qui conta solo la forma. */
    private fun curva(st: FloatArray, hopMs: Double = 10.0) = Contour(st, hopMs, 200f)

    /** Zig-zag di ampiezza [ampiezza]: escursione prevedibile, chiusura in discesa. */
    private fun zigzag(frame: Int, ampiezza: Float) =
        curva(FloatArray(frame) { if (it % 2 == 0) ampiezza else -ampiezza })

    private fun codici(sua: Contour, tua: Contour, soglie: SoglieRiscontro = SoglieRiscontro()) =
        FeedbackRules.evaluate(sua, tua, soglie).map { it.code }

    // --- una regola per volta ------------------------------------------------

    @Test
    fun `la chiusura scatta se la sua scende e la tua sale`() {
        val sua = curva(FloatArray(40) { if (it < 30) 0f else -(it - 29) * 0.5f })
        val tua = curva(FloatArray(40) { if (it < 30) 0f else (it - 29) * 0.5f })

        assertEquals(listOf(FeedbackCode.CHIUSURA), codici(sua, tua))
    }

    @Test
    fun `se scendono entrambe non c'e niente da dire sulla chiusura`() {
        val sua = curva(FloatArray(40) { if (it < 30) 0f else -(it - 29) * 0.5f })
        val tua = curva(FloatArray(40) { if (it < 30) 0f else -(it - 29) * 0.5f })

        assertEquals(listOf(FeedbackCode.VICINA), codici(sua, tua))
    }

    @Test
    fun `escursione poca quando la tua melodia si muove meno della sua`() {
        val sua = zigzag(40, 3f)
        val tua = zigzag(40, 1f)

        assertEquals(listOf(FeedbackCode.ESCURSIONE_POCA), codici(sua, tua))
    }

    @Test
    fun `escursione troppa quando la tua melodia si muove troppo`() {
        val sua = zigzag(40, 1f)
        val tua = zigzag(40, 3f)

        assertEquals(listOf(FeedbackCode.ESCURSIONE_TROPPA), codici(sua, tua))
    }

    @Test
    fun `durata corta quando la ripetizione e piu svelta della clip`() {
        val sua = zigzag(40, 1f)
        val tua = zigzag(20, 1f)

        assertEquals(listOf(FeedbackCode.DURATA_CORTA), codici(sua, tua))
    }

    @Test
    fun `durata lunga quando la ripetizione trascina`() {
        val sua = zigzag(40, 1f)
        val tua = zigzag(60, 1f)

        assertEquals(listOf(FeedbackCode.DURATA_LUNGA), codici(sua, tua))
    }

    @Test
    fun `se nessuna regola scatta si dice che la forma e vicina`() {
        val sua = zigzag(40, 1f)
        assertEquals(listOf(FeedbackCode.VICINA), codici(sua, zigzag(40, 1f)))
    }

    // --- quante frasi e in che ordine ---------------------------------------

    @Test
    fun `al massimo due messaggi, nell'ordine di priorita`() {
        // Qui scattano chiusura, escursione poca e durata corta: le prime due.
        val sua = curva(
            FloatArray(60) {
                if (it < 40) (if (it % 2 == 0) 4f else -4f) else -4f - (it - 39) * 0.3f
            },
        )
        val tua = curva(FloatArray(20) { 0f })

        assertEquals(
            listOf(FeedbackCode.CHIUSURA, FeedbackCode.ESCURSIONE_POCA),
            codici(sua, tua),
        )
    }

    @Test
    fun `un solo messaggio se si chiede un solo messaggio`() {
        val sua = curva(
            FloatArray(60) {
                if (it < 40) (if (it % 2 == 0) 4f else -4f) else -4f - (it - 39) * 0.3f
            },
        )
        val tua = curva(FloatArray(20) { 0f })
        val soglie = SoglieRiscontro(massimoMessaggi = 1)

        assertEquals(listOf(FeedbackCode.CHIUSURA), codici(sua, tua, soglie))
    }

    @Test
    fun `non si restituisce mai una lista vuota`() {
        val casi = listOf(
            zigzag(40, 1f) to zigzag(40, 1f),
            zigzag(40, 3f) to zigzag(10, 1f),
            curva(FloatArray(5) { nan }) to zigzag(40, 1f),
        )
        for ((sua, tua) in casi) {
            assertTrue("nessun messaggio", FeedbackRules.evaluate(sua, tua).isNotEmpty())
        }
    }

    // --- quando non c'e' niente da misurare ---------------------------------

    @Test
    fun `una registrazione muta non e una ripetizione vicina`() {
        val sua = zigzag(40, 1f)
        val muta = curva(FloatArray(40) { nan })

        assertEquals(listOf(FeedbackCode.NON_VALUTABILE), codici(sua, muta))
    }

    @Test
    fun `un solo frame sonoro non basta per giudicare`() {
        val sua = zigzag(40, 1f)
        val quasiMuta = curva(FloatArray(40) { if (it == 5) 0f else nan })

        assertEquals(listOf(FeedbackCode.NON_VALUTABILE), codici(sua, quasiMuta))
    }

    @Test
    fun `una clip senza misure non fa giudicare la ripetizione`() {
        val tua = zigzag(40, 1f)
        assertEquals(
            listOf(FeedbackCode.NON_VALUTABILE),
            codici(curva(FloatArray(40) { nan }), tua),
        )
    }

    // --- le soglie stanno nella configurazione ------------------------------

    @Test
    fun `cambiare la soglia cambia il giudizio`() {
        val sua = zigzag(40, 3f)
        val tua = zigzag(40, 2f) // escursione al 66% della sua

        // Con la soglia di fabbrica (60%) va bene.
        assertEquals(listOf(FeedbackCode.VICINA), codici(sua, tua))
        // Alzandola al 70% la stessa ripetizione diventa troppo piatta.
        assertEquals(
            listOf(FeedbackCode.ESCURSIONE_POCA),
            codici(sua, tua, SoglieRiscontro(escursionePoca = 0.70f)),
        )
    }

    @Test
    fun `la finestra della chiusura decide cosa si guarda`() {
        // Frase che scende a meta' e poi resta bassa e piatta.
        val sua = curva(
            FloatArray(40) {
                when {
                    it < 10 -> 0f
                    it < 20 -> -(it - 9) * 0.5f
                    else -> -5f
                }
            },
        )
        val tua = curva(FloatArray(40) { 0f })

        // Con la finestra di fabbrica, 300 ms, la discesa ci sta dentro.
        assertTrue(FeedbackCode.CHIUSURA in codici(sua, tua))
        // Con 100 ms si guarda solo la coda, che e' piatta: niente da dire sulla chiusura.
        assertTrue(
            FeedbackCode.CHIUSURA !in codici(sua, tua, SoglieRiscontro(chiusuraMs = 100.0)),
        )
    }

    // --- messaggi e codici ---------------------------------------------------

    @Test
    fun `ogni codice ha una frase in italiano`() {
        for (code in FeedbackCode.entries) {
            val m = FeedbackRules.messaggio(code)
            assertTrue("$code senza messaggio", m.isNotBlank())
            assertTrue("$code: la frase deve finire con un punto", m.trim().endsWith("."))
        }
    }

    @Test
    fun `i codici vanno nel database e tornano`() {
        val riscontri = FeedbackRules.evaluate(
            curva(FloatArray(60) {
                if (it < 40) (if (it % 2 == 0) 4f else -4f) else -4f - (it - 39) * 0.3f
            }),
            curva(FloatArray(20) { 0f }),
        )
        val scritti = FeedbackRules.encodeCodes(riscontri)

        assertEquals("CHIUSURA,ESCURSIONE_POCA", scritti)
        assertEquals(riscontri.map { it.code }, FeedbackRules.decodeCodes(scritti))
    }

    @Test
    fun `codici assenti o sconosciuti non fanno errore`() {
        assertEquals(emptyList<FeedbackCode>(), FeedbackRules.decodeCodes(null))
        assertEquals(emptyList<FeedbackCode>(), FeedbackRules.decodeCodes(""))
        assertEquals(
            listOf(FeedbackCode.VICINA),
            FeedbackRules.decodeCodes("REGOLA_DI_DOMANI, VICINA"),
        )
    }
}
