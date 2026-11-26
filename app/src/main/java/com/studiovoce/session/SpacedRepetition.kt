package com.studiovoce.session

/**
 * Ripasso a intervalli, SPEC §5.4. Logica pura: nessuna dipendenza da Android,
 * nessun orologio interno. Le date sono epoch-day (giorni dal 1970-01-01), così
 * i test possono usare date finte.
 */
object SpacedRepetition {

    /** Quante clip scadute entrano in una sessione. */
    const val DEFAULT_LIMIT = 6

    /**
     * Una clip appena vista torna dopo tanti giorni quanto dice il suo Livello.
     * Livello mancante, 1 o 2 → il giorno dopo.
     */
    fun intervalDays(level: Int?): Long = when {
        level == null -> 1L
        level <= 2 -> 1L
        level == 3 -> 3L
        level == 4 -> 7L
        else -> 14L
    }

    /** Il giorno in cui la clip torna nel ripasso, dato il voto di oggi. */
    fun nextDueDay(level: Int?, today: Long): Long = today + intervalDays(level)

    /**
     * Stato di una clip visto dal ripasso. `suspect` è vero per le clip con esito
     * SOSPETTA/MISTA non ancora confermate da Ziv; `hidden` per quelle senza audio.
     */
    data class Candidate(
        val clipId: String,
        val categoryId: String,
        val level: Int?,
        val dueAt: Long?,
        val hidden: Boolean = false,
        val suspect: Boolean = false,
        val lastPracticedAt: Long? = null,
    )

    fun isEligible(c: Candidate): Boolean = !c.hidden && !c.suspect

    fun isDue(c: Candidate, today: Long): Boolean =
        isEligible(c) && c.dueAt != null && c.dueAt <= today

    /** Tutte le clip scadute oggi, nell'ordine in cui conviene farle. */
    fun due(
        candidates: List<Candidate>,
        today: Long,
        preferredCategoryId: String? = null,
    ): List<Candidate> =
        candidates.filter { isDue(it, today) }.sortedWith(order(preferredCategoryId))

    /**
     * Le clip della sessione di oggi: fino a [limit] clip scadute, con la precedenza
     * alla categoria della settimana. Se non ce ne sono abbastanza, completa con clip
     * mai provate di quella categoria.
     */
    fun selectForReview(
        candidates: List<Candidate>,
        today: Long,
        limit: Int = DEFAULT_LIMIT,
        preferredCategoryId: String? = null,
    ): List<Candidate> {
        if (limit <= 0) return emptyList()
        val chosen = due(candidates, today, preferredCategoryId).take(limit).toMutableList()
        if (chosen.size < limit && preferredCategoryId != null) {
            val taken = chosen.mapTo(mutableSetOf()) { it.clipId }
            val fresh = candidates
                .filter {
                    isEligible(it) &&
                        it.categoryId == preferredCategoryId &&
                        it.clipId !in taken &&
                        it.lastPracticedAt == null
                }
                .sortedBy { it.clipId }
            chosen += fresh.take(limit - chosen.size)
        }
        return chosen
    }

    /**
     * Prima la categoria della settimana, poi le più in ritardo, poi i Livelli bassi
     * (una clip mai votata viene prima di una da 5), infine l'id per avere un ordine
     * stabile fra sessioni.
     */
    private fun order(preferredCategoryId: String?): Comparator<Candidate> =
        compareBy<Candidate> { if (preferredCategoryId != null && it.categoryId == preferredCategoryId) 0 else 1 }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenBy { it.level ?: 0 }
            .thenBy { it.clipId }
}
