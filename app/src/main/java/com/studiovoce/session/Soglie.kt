package com.studiovoce.session

/**
 * Tutte le soglie dell'allenamento in un posto solo, come chiede la SPEC §5.5.
 *
 * Sparse nel codice sarebbero impossibili da ritarare: qui si cambiano in un
 * punto, e le Impostazioni avanzate (SPEC §7) potranno scriverle senza toccare
 * la logica.
 */

/** Soglie del riscontro a parole dopo una ripetizione (SPEC §5.5). */
data class SoglieRiscontro(
    /** Quanta parte finale della frase si guarda per giudicare la chiusura. */
    val chiusuraMs: Double = 300.0,
    /** La sua chiusura "scende" se perde piu' di questi semitoni. */
    val suaChiusuraScende: Float = -2f,
    /** La tua "sale o resta piatta" se non scende almeno di tanto. */
    val tuaChiusuraNonScende: Float = -1f,
    /** La tua escursione e' poca sotto questa frazione della sua. */
    val escursionePoca: Float = 0.60f,
    /** ...ed e' troppa sopra quest'altra. */
    val escursioneTroppa: Float = 1.60f,
    /** La tua durata e' corta sotto questa frazione della sua. */
    val durataCorta: Float = 0.75f,
    /** ...e lunga sopra quest'altra. */
    val durataLunga: Float = 1.40f,
    /** Quanti messaggi al massimo: due, mai una lezione. */
    val massimoMessaggi: Int = 2,
)

/** Soglie che compongono la sessione quotidiana (SPEC §5.2). */
data class SoglieSessione(
    /** Riscaldamento: due minuti. */
    val riscaldamentoS: Int = 120,
    /** Blocco della fase: quattro minuti e mezzo. */
    val bloccoDiFaseS: Int = 270,
    /** Riscaldamento leggero dopo due sessioni con la voce affaticata. */
    val riscaldamentoLeggeroS: Int = 60,
    /** Quante clip entrano nel ripasso. */
    val ripassoMax: Int = SpacedRepetition.DEFAULT_LIMIT,
    /** Quante clip nella sessione breve di mantenimento della fase 4. */
    val mantenimentoMax: Int = 3,
    /** Quante risposte "Affaticata" di fila fanno scattare la sessione leggera. */
    val faticaDiFila: Int = 2,
    /** Settimana in cui comincia la fase 2, da cui ruota la categoria. */
    val settimanaInizioFase2: Int = 4,
    /** Ultima settimana in cui la sessione contiene ancora gli esercizi di fase 1. */
    val settimanaFineFase1: Int = 8,
)
