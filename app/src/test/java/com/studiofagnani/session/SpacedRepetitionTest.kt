package com.studiofagnani.session

import com.studiofagnani.session.SpacedRepetition.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Date finte: tutto in epoch-day, nessun orologio di sistema. */
class SpacedRepetitionTest {

    private val today = LocalDate.of(2026, 11, 25).toEpochDay()

    private fun c(
        id: String,
        category: String = "domanda-secca",
        level: Int? = null,
        dueAt: Long? = today,
        hidden: Boolean = false,
        suspect: Boolean = false,
        lastPracticedAt: Long? = null,
    ) = Candidate(id, category, level, dueAt, hidden, suspect, lastPracticedAt)

    @Test
    fun `gli intervalli seguono i livelli della spec`() {
        assertEquals(1L, SpacedRepetition.intervalDays(null))
        assertEquals(1L, SpacedRepetition.intervalDays(1))
        assertEquals(1L, SpacedRepetition.intervalDays(2))
        assertEquals(3L, SpacedRepetition.intervalDays(3))
        assertEquals(7L, SpacedRepetition.intervalDays(4))
        assertEquals(14L, SpacedRepetition.intervalDays(5))
    }

    @Test
    fun `la prossima scadenza si calcola dal giorno dato`() {
        assertEquals(today + 1, SpacedRepetition.nextDueDay(2, today))
        assertEquals(today + 3, SpacedRepetition.nextDueDay(3, today))
        assertEquals(today + 7, SpacedRepetition.nextDueDay(4, today))
        assertEquals(today + 14, SpacedRepetition.nextDueDay(5, today))
    }

    @Test
    fun `una clip votata 4 non torna prima di sette giorni`() {
        val due = SpacedRepetition.nextDueDay(4, today)
        val clip = c("x", level = 4, dueAt = due)
        for (d in 0..6) assertFalse("giorno +$d", SpacedRepetition.isDue(clip, today + d))
        assertTrue(SpacedRepetition.isDue(clip, today + 7))
        assertTrue(SpacedRepetition.isDue(clip, today + 8))
    }

    @Test
    fun `prende al massimo sei clip`() {
        val all = (1..10).map { c("clip_%02d".format(it)) }
        assertEquals(6, SpacedRepetition.selectForReview(all, today).size)
    }

    @Test
    fun `salta le clip non scadute`() {
        val all = listOf(
            c("a", dueAt = today),
            c("b", dueAt = today - 3),
            c("c", dueAt = today + 1),
            c("d", dueAt = null),
        )
        val ids = SpacedRepetition.selectForReview(all, today).map { it.clipId }
        assertEquals(listOf("b", "a"), ids)
    }

    @Test
    fun `salta le clip nascoste e quelle sospette`() {
        val all = listOf(
            c("muta", hidden = true),
            c("sospetta", suspect = true),
            c("buona"),
        )
        assertEquals(listOf("buona"), SpacedRepetition.selectForReview(all, today).map { it.clipId })
    }

    @Test
    fun `la categoria della settimana ha la precedenza`() {
        val all = listOf(
            c("altra_1", category = "risata", dueAt = today - 5),
            c("ironia_1", category = "ironia-sarcasmo", dueAt = today),
            c("altra_2", category = "risata", dueAt = today - 4),
            c("ironia_2", category = "ironia-sarcasmo", dueAt = today),
        )
        val ids = SpacedRepetition.selectForReview(all, today, preferredCategoryId = "ironia-sarcasmo")
            .map { it.clipId }
        assertEquals(listOf("ironia_1", "ironia_2", "altra_1", "altra_2"), ids)
    }

    @Test
    fun `se le scadute non bastano aggiunge clip nuove della categoria`() {
        val all = listOf(
            c("ironia_1", category = "ironia-sarcasmo", dueAt = today),
            // mai provate, non ancora in scadenza: sono il riempimento giusto
            c("ironia_9", category = "ironia-sarcasmo", dueAt = today + 30, lastPracticedAt = null),
            c("ironia_8", category = "ironia-sarcasmo", dueAt = today + 30, lastPracticedAt = null),
            // già provata in passato: non si ripesca prima della sua scadenza
            c("ironia_7", category = "ironia-sarcasmo", dueAt = today + 30, lastPracticedAt = 1L),
            // altra categoria: non entra come riempimento
            c("risata_1", category = "risata", dueAt = today + 30, lastPracticedAt = null),
        )
        val ids = SpacedRepetition.selectForReview(all, today, limit = 3, preferredCategoryId = "ironia-sarcasmo")
            .map { it.clipId }
        assertEquals(listOf("ironia_1", "ironia_8", "ironia_9"), ids)
    }

    @Test
    fun `senza categoria della settimana non aggiunge clip non scadute`() {
        val all = listOf(
            c("a", dueAt = today),
            c("b", dueAt = today + 10, lastPracticedAt = null),
        )
        assertEquals(listOf("a"), SpacedRepetition.selectForReview(all, today).map { it.clipId })
    }

    @Test
    fun `fra pari scadenza vengono prima i livelli bassi`() {
        val all = listOf(
            c("alta", level = 5, dueAt = today),
            c("mai", level = null, dueAt = today),
            c("bassa", level = 2, dueAt = today),
        )
        assertEquals(
            listOf("mai", "bassa", "alta"),
            SpacedRepetition.selectForReview(all, today).map { it.clipId },
        )
    }

    @Test
    fun `al primo avvio tutte le clip sono da fare e la selezione e stabile`() {
        val all = (1..40).map { c("clip_%02d".format(it), dueAt = today) }
        val first = SpacedRepetition.selectForReview(all, today).map { it.clipId }
        val second = SpacedRepetition.selectForReview(all.shuffled(), today).map { it.clipId }
        assertEquals(first, second)
    }

    @Test
    fun `limite zero non restituisce nulla`() {
        assertTrue(SpacedRepetition.selectForReview(listOf(c("a")), today, limit = 0).isEmpty())
    }
}
