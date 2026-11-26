package com.studiofagnani.session

/**
 * Cosa fa la sessione di oggi (SPEC §5.1 e §5.2).
 *
 * Funzione pura: stato in ingresso, lista di passi in uscita. Nessun orologio,
 * nessun database, nessuna dipendenza da Android — e' la parte che decide come
 * va una giornata di allenamento, e deve essere verificabile a tavolino.
 */
object SessionPlanner {

    /** Come e' andata la voce alla fine di una sessione (SPEC §5.2, chiusura). */
    enum class Fatica { OK, STANCA, AFFATICATA }

    /** Le tre registrazioni guidate della fase 0 (SPEC §5.1). */
    enum class Guidata { BRANO_FISSO, MINUTO_LIBERO, DOMANDA_FAGNANI }

    /** Un esercizio come arriva da `assets/exercises.json`. */
    data class Esercizio(val id: String, val durationS: Int)

    sealed interface Passo {

        data class Riscaldamento(val esercizio: Esercizio) : Passo

        data class EsercizioDiFase(val esercizio: Esercizio) : Passo

        /** Ascolta, ripeti, confronta (SPEC §5.3). */
        data class EsercizioSuClip(val clipId: String) : Passo

        /** Nella fase 1 le clip di Fagnani si ascoltano soltanto. */
        data class AscoltoClip(val clipId: String) : Passo

        data class RegistrazioneGuidata(val tipo: Guidata) : Passo

        data object SchedaBersaglio : Passo

        /** Fase 3: "intervista alla Belve" libera, registrata. */
        data object IntervistaLibera : Passo

        /** Fase 4: una missione di trasferimento nella vita di tutti i giorni. */
        data object MissioneDiTrasferimento : Passo

        data object AvvisoVoceAffaticata : Passo {
            const val MESSAGGIO =
                "Se la stanchezza continua per qualche giorno, conviene sentire un logopedista."
        }

        /** "Com'e' la voce adesso?": c'e' alla fine di ogni sessione. */
        data object DomandaFatica : Passo
    }

    /**
     * Tutto quello che serve per decidere. [faticaRecente] ha la risposta piu'
     * recente per prima; [clips] e' l'intera libreria, il filtro lo fa
     * [SpacedRepetition].
     */
    data class Stato(
        val phase: Int,
        val week: Int,
        val today: Long,
        val riscaldamento: List<Esercizio> = emptyList(),
        val eserciziDiFase: List<Esercizio> = emptyList(),
        val clips: List<SpacedRepetition.Candidate> = emptyList(),
        val faticaRecente: List<Fatica> = emptyList(),
        val ordineCategorie: List<String> = emptyList(),
    )

    data class Piano(
        val passi: List<Passo>,
        val phase: Int,
        val week: Int,
        /** La categoria su cui gira la settimana, solo nella fase 2. */
        val categoriaDellaSettimana: String?,
        /** Vero quando la sessione e' ridotta al riscaldamento leggero. */
        val leggera: Boolean,
    )

    fun plan(stato: Stato, soglie: SoglieSessione = SoglieSessione()): Piano {
        val fase = stato.phase.coerceIn(0, 4)
        val categoria = categoriaDellaSettimana(fase, stato, soglie)

        // La sicurezza vocale viene prima del volume di lavoro (SPEC §2.5): due
        // "Affaticata" di fila e la sessione si riduce, senza chiedere il permesso.
        if (vocePerStanca(stato, soglie)) {
            return Piano(
                passi = entroIlTempo(stato.riscaldamento, soglie.riscaldamentoLeggeroS)
                    .map { Passo.Riscaldamento(it) } +
                    listOf(Passo.AvvisoVoceAffaticata, Passo.DomandaFatica),
                phase = fase,
                week = stato.week,
                categoriaDellaSettimana = categoria,
                leggera = true,
            )
        }

        val passi = mutableListOf<Passo>()

        if (fase == 0) {
            // Settimana 1: solo la misura di partenza, niente esercizi e niente clip.
            for (tipo in Guidata.entries) passi += Passo.RegistrazioneGuidata(tipo)
            passi += Passo.SchedaBersaglio
            passi += Passo.DomandaFatica
            return Piano(passi, fase, stato.week, categoria, leggera = false)
        }

        passi += entroIlTempo(stato.riscaldamento, soglie.riscaldamentoS)
            .map { Passo.Riscaldamento(it) }

        // Le fasi 1 e 2 si sovrappongono: dalla settimana 4 la sessione contiene
        // entrambe, e gli esercizi di fase 1 restano fino alla settimana 8.
        if (fase == 1 || (fase == 2 && stato.week <= soglie.settimanaFineFase1)) {
            passi += entroIlTempo(stato.eserciziDiFase, soglie.bloccoDiFaseS)
                .map { Passo.EsercizioDiFase(it) }
        }

        val quante = if (fase == 4) soglie.mantenimentoMax else soglie.ripassoMax
        val scelte = SpacedRepetition.selectForReview(
            candidates = stato.clips,
            today = stato.today,
            limit = quante,
            preferredCategoryId = categoria,
        )

        // Nella fase 1 le clip di Fagnani si ascoltano e non si ripetono ancora.
        passi += if (fase == 1) {
            scelte.map { Passo.AscoltoClip(it.clipId) }
        } else {
            scelte.map { Passo.EsercizioSuClip(it.clipId) }
        }

        if (fase == 3) passi += Passo.IntervistaLibera
        if (fase == 4) passi += Passo.MissioneDiTrasferimento

        passi += Passo.DomandaFatica
        return Piano(passi, fase, stato.week, categoria, leggera = false)
    }

    /** Due risposte "Affaticata" di fila. */
    private fun vocePerStanca(stato: Stato, soglie: SoglieSessione): Boolean {
        if (soglie.faticaDiFila <= 0) return false
        val ultime = stato.faticaRecente.take(soglie.faticaDiFila)
        return ultime.size == soglie.faticaDiFila && ultime.all { it == Fatica.AFFATICATA }
    }

    /**
     * La categoria su cui gira la settimana, nell'ordine della SPEC §5.1.
     * Gira solo nella fase 2: nelle altre il ripasso non ha una preferenza.
     */
    private fun categoriaDellaSettimana(
        fase: Int,
        stato: Stato,
        soglie: SoglieSessione,
    ): String? {
        if (fase != 2 || stato.ordineCategorie.isEmpty()) return null
        val settimaneDentro = (stato.week - soglie.settimanaInizioFase2).coerceAtLeast(0)
        return stato.ordineCategorie[settimaneDentro % stato.ordineCategorie.size]
    }

    /**
     * Gli esercizi che entrano nel tempo a disposizione, nell'ordine dato.
     * Almeno uno ci va comunque: una sessione senza riscaldamento non e' una
     * sessione, anche se l'unico esercizio disponibile e' piu' lungo del budget.
     */
    private fun entroIlTempo(esercizi: List<Esercizio>, budgetS: Int): List<Esercizio> {
        if (esercizi.isEmpty()) return emptyList()
        val scelti = mutableListOf<Esercizio>()
        var totale = 0
        for (e in esercizi) {
            if (scelti.isNotEmpty() && totale + e.durationS > budgetS) break
            scelti += e
            totale += e.durationS
        }
        return scelti
    }
}
