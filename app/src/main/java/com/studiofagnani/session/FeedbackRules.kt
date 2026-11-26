package com.studiofagnani.session

import com.studiofagnani.dsp.Contour

/**
 * Il riscontro a parole dopo una ripetizione (SPEC §5.5).
 *
 * Regole deterministiche, niente punteggi: al massimo due frasi, sempre su cosa
 * fare la prossima volta. Mai un giudizio sulla voce di Ziv, mai un verdetto
 * sull'identita' (SPEC §11).
 *
 * Tutte le misure sono in semitoni rispetto alla mediana di ciascuna curva, e la
 * durata e' quella del solo parlato: cosi' si confronta la forma della melodia e
 * non l'altezza o il silenzio intorno.
 */
enum class FeedbackCode {
    CHIUSURA,
    ESCURSIONE_POCA,
    ESCURSIONE_TROPPA,
    DURATA_CORTA,
    DURATA_LUNGA,
    VICINA,
    NON_VALUTABILE,
}

data class Riscontro(val code: FeedbackCode, val messaggio: String)

object FeedbackRules {

    /** I messaggi, esattamente come stanno nella SPEC §5.5. */
    private val MESSAGGI = mapOf(
        FeedbackCode.CHIUSURA to "La sua chiusura scende, la tua no: finisci più in basso.",
        FeedbackCode.ESCURSIONE_POCA to "La sua melodia si muove di più: esagera un po' gli accenti.",
        FeedbackCode.ESCURSIONE_TROPPA to "Stai cantando un po': più asciutta.",
        FeedbackCode.DURATA_CORTA to "Vai più piano, lascia le pause.",
        FeedbackCode.DURATA_LUNGA to "Più sciolta: stai trascinando.",
        FeedbackCode.VICINA to "Forma molto vicina. Prova a votarti.",
        // Non sta nella tabella della SPEC: serve perche' una registrazione muta
        // non e' una ripetizione "molto vicina", e dirlo sarebbe una bugia.
        FeedbackCode.NON_VALUTABILE to "Non ho sentito abbastanza voce: prova a ripetere.",
    )

    fun messaggio(code: FeedbackCode): String = MESSAGGI.getValue(code)

    /**
     * Confronta la clip di Fagnani [sua] con la ripetizione [tua] e restituisce
     * da uno a [SoglieRiscontro.massimoMessaggi] messaggi, nell'ordine di
     * priorita' della tabella.
     */
    fun evaluate(
        sua: Contour,
        tua: Contour,
        soglie: SoglieRiscontro = SoglieRiscontro(),
    ): List<Riscontro> {
        if (sua.voicedCount < 2 || tua.voicedCount < 2) {
            return listOf(riscontro(FeedbackCode.NON_VALUTABILE))
        }

        val scattate = mutableListOf<FeedbackCode>()

        if (chiusuraDaSistemare(sua, tua, soglie)) scattate += FeedbackCode.CHIUSURA

        val suaEscursione = sua.range
        val tuaEscursione = tua.range
        if (suaEscursione > 0f && !tuaEscursione.isNaN()) {
            if (tuaEscursione < suaEscursione * soglie.escursionePoca) {
                scattate += FeedbackCode.ESCURSIONE_POCA
            } else if (tuaEscursione > suaEscursione * soglie.escursioneTroppa) {
                scattate += FeedbackCode.ESCURSIONE_TROPPA
            }
        }

        val suaDurata = sua.voicedSpanMs
        val tuaDurata = tua.voicedSpanMs
        if (suaDurata > 0.0) {
            if (tuaDurata < suaDurata * soglie.durataCorta) {
                scattate += FeedbackCode.DURATA_CORTA
            } else if (tuaDurata > suaDurata * soglie.durataLunga) {
                scattate += FeedbackCode.DURATA_LUNGA
            }
        }

        if (scattate.isEmpty()) return listOf(riscontro(FeedbackCode.VICINA))
        return scattate.take(soglie.massimoMessaggi).map { riscontro(it) }
    }

    /** La sua chiusura scende con decisione e la tua no. */
    private fun chiusuraDaSistemare(
        sua: Contour,
        tua: Contour,
        soglie: SoglieRiscontro,
    ): Boolean {
        val suaFine = sua.closingDelta(soglie.chiusuraMs)
        val tuaFine = tua.closingDelta(soglie.chiusuraMs)
        if (suaFine.isNaN() || tuaFine.isNaN()) return false
        return suaFine < soglie.suaChiusuraScende && tuaFine > soglie.tuaChiusuraNonScende
    }

    private fun riscontro(code: FeedbackCode) = Riscontro(code, messaggio(code))

    /** Per la colonna `attempt.feedback_codes` (SPEC §8). */
    fun encodeCodes(riscontri: List<Riscontro>): String =
        riscontri.joinToString(",") { it.code.name }

    /** Codici sconosciuti si ignorano: un domani la tabella potra' cambiare. */
    fun decodeCodes(encoded: String?): List<FeedbackCode> =
        encoded?.split(',')
            ?.mapNotNull { pezzo ->
                val nome = pezzo.trim()
                FeedbackCode.entries.firstOrNull { it.name == nome }
            }
            ?: emptyList()
}
