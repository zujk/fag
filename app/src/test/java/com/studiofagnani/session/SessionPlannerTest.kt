package com.studiofagnani.session

import com.studiofagnani.session.SessionPlanner.Esercizio
import com.studiofagnani.session.SessionPlanner.Fatica
import com.studiofagnani.session.SessionPlanner.Guidata
import com.studiofagnani.session.SessionPlanner.Passo
import com.studiofagnani.session.SessionPlanner.Stato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * La composizione della sessione quotidiana (SPEC §5.2), con date finte.
 */
class SessionPlannerTest {

    private val oggi = LocalDate.of(2026, 11, 26).toEpochDay()

    private val categorie = listOf("domanda-secca", "calma-seria", "ironia", "incalzare")

    private val riscaldamento = listOf(
        Esercizio("humming", 40),
        Esercizio("trilli", 40),
        Esercizio("sirena", 40),
        Esercizio("ng-vocali", 40),
    )

    private val eserciziDiFase = listOf(
        Esercizio("risonanza", 120),
        Esercizio("peso", 120),
        Esercizio("pavimento", 120),
    )

    private fun clip(
        id: String,
        categoria: String = "domanda-secca",
        livello: Int? = null,
        scadutaDa: Long = 0,
        hidden: Boolean = false,
        suspect: Boolean = false,
    ) = SpacedRepetition.Candidate(
        clipId = id,
        categoryId = categoria,
        level = livello,
        dueAt = oggi - scadutaDa,
        hidden = hidden,
        suspect = suspect,
        lastPracticedAt = oggi - scadutaDa - 1,
    )

    private fun stato(
        fase: Int,
        settimana: Int,
        clips: List<SpacedRepetition.Candidate> = emptyList(),
        fatica: List<Fatica> = emptyList(),
    ) = Stato(
        phase = fase,
        week = settimana,
        today = oggi,
        riscaldamento = riscaldamento,
        eserciziDiFase = eserciziDiFase,
        clips = clips,
        faticaRecente = fatica,
        ordineCategorie = categorie,
    )

    // --- fase 0: la misura di partenza --------------------------------------

    @Test
    fun `nella fase 0 la sessione e solo la misura e la scheda bersaglio`() {
        val piano = SessionPlanner.plan(stato(0, 1, clips = listOf(clip("c1"))))

        assertEquals(
            listOf(
                Passo.RegistrazioneGuidata(Guidata.BRANO_FISSO),
                Passo.RegistrazioneGuidata(Guidata.MINUTO_LIBERO),
                Passo.RegistrazioneGuidata(Guidata.DOMANDA_FAGNANI),
                Passo.SchedaBersaglio,
                Passo.DomandaFatica,
            ),
            piano.passi,
        )
        assertFalse(piano.leggera)
        assertNull("nella fase 0 non c'e' categoria della settimana", piano.categoriaDellaSettimana)
    }

    // --- fase 1: le clip solo in ascolto ------------------------------------

    @Test
    fun `nella fase 1 le clip si ascoltano e non si ripetono`() {
        val piano = SessionPlanner.plan(
            stato(1, 3, clips = listOf(clip("c1"), clip("c2"))),
        )

        assertTrue("servono gli esercizi di fase", piano.passi.any { it is Passo.EsercizioDiFase })
        assertEquals(
            listOf("c1", "c2"),
            piano.passi.filterIsInstance<Passo.AscoltoClip>().map { it.clipId },
        )
        assertTrue(
            "nella fase 1 non si registra sulle clip",
            piano.passi.none { it is Passo.EsercizioSuClip },
        )
    }

    // --- fase 2: la categoria della settimana e la sovrapposizione ----------

    @Test
    fun `la fase 2 gira sulla categoria della settimana`() {
        // La fase 2 comincia alla settimana 4: la 6 e' la terza della rotazione.
        val piano = SessionPlanner.plan(stato(2, 6))
        assertEquals("ironia", piano.categoriaDellaSettimana)
    }

    @Test
    fun `la rotazione delle categorie torna all'inizio`() {
        // Quattro categorie, otto settimane: la lista si ripercorre due volte.
        val viste = (4..11).map { SessionPlanner.plan(stato(2, it)).categoriaDellaSettimana }

        assertEquals(categorie, viste.take(4))
        assertEquals("dopo quattro settimane si ricomincia", categorie, viste.drop(4))
    }

    @Test
    fun `prima della settimana 4 la fase 2 parte dalla prima categoria`() {
        // Le fasi si sovrappongono: se Ziv passa alla 2 in anticipo, la rotazione
        // comincia dall'inizio invece di andare a indici negativi.
        assertEquals(categorie.first(), SessionPlanner.plan(stato(2, 2)).categoriaDellaSettimana)
        assertEquals(categorie.first(), SessionPlanner.plan(stato(2, 4)).categoriaDellaSettimana)
    }

    @Test
    fun `fino alla settimana 8 la sessione contiene anche gli esercizi di fase 1`() {
        val sovrapposte = SessionPlanner.plan(stato(2, 6))
        assertTrue(sovrapposte.passi.any { it is Passo.EsercizioDiFase })

        val dopo = SessionPlanner.plan(stato(2, 10))
        assertTrue(
            "dalla settimana 9 restano riscaldamento e clip",
            dopo.passi.none { it is Passo.EsercizioDiFase },
        )
        assertTrue(dopo.passi.any { it is Passo.Riscaldamento })
    }

    @Test
    fun `nella fase 2 le clip si ripetono`() {
        val piano = SessionPlanner.plan(stato(2, 6, clips = listOf(clip("c1", "ironia"))))
        assertEquals(
            listOf("c1"),
            piano.passi.filterIsInstance<Passo.EsercizioSuClip>().map { it.clipId },
        )
    }

    // --- voce affaticata ----------------------------------------------------

    @Test
    fun `due volte affaticata e la sessione successiva e leggera`() {
        val piano = SessionPlanner.plan(
            stato(
                2, 6,
                clips = listOf(clip("c1"), clip("c2")),
                fatica = listOf(Fatica.AFFATICATA, Fatica.AFFATICATA, Fatica.OK),
            ),
        )

        assertTrue(piano.leggera)
        assertTrue("niente clip quando la voce e' affaticata", piano.passi.none {
            it is Passo.EsercizioSuClip || it is Passo.AscoltoClip
        })
        assertTrue("niente esercizi di fase", piano.passi.none { it is Passo.EsercizioDiFase })
        assertTrue("ci vuole l'avviso", Passo.AvvisoVoceAffaticata in piano.passi)
        assertEquals(Passo.DomandaFatica, piano.passi.last())

        // Il riscaldamento leggero e' un minuto: entra un esercizio da 40 s.
        assertEquals(1, piano.passi.filterIsInstance<Passo.Riscaldamento>().size)
    }

    @Test
    fun `l'avviso parla di logopedista senza allarmare`() {
        val m = Passo.AvvisoVoceAffaticata.MESSAGGIO
        assertTrue(m.contains("logopedista"))
        assertTrue(m.endsWith("."))
    }

    @Test
    fun `una volta sola affaticata non riduce la sessione`() {
        val piano = SessionPlanner.plan(
            stato(2, 6, clips = listOf(clip("c1")), fatica = listOf(Fatica.AFFATICATA, Fatica.OK)),
        )
        assertFalse(piano.leggera)
        assertTrue(piano.passi.any { it is Passo.EsercizioSuClip })
    }

    @Test
    fun `affaticata e poi stanca non riduce la sessione`() {
        val piano = SessionPlanner.plan(
            stato(
                2, 6,
                clips = listOf(clip("c1")),
                fatica = listOf(Fatica.AFFATICATA, Fatica.STANCA),
            ),
        )
        assertFalse(piano.leggera)
    }

    @Test
    fun `alla prima sessione non c'e storia della fatica`() {
        val piano = SessionPlanner.plan(stato(2, 6, clips = listOf(clip("c1"))))
        assertFalse(piano.leggera)
    }

    // --- tempi --------------------------------------------------------------

    @Test
    fun `il riscaldamento sta nei due minuti`() {
        val piano = SessionPlanner.plan(stato(2, 10))
        val scelti = piano.passi.filterIsInstance<Passo.Riscaldamento>()

        assertEquals("tre esercizi da 40 s fanno due minuti", 3, scelti.size)
        assertTrue(scelti.sumOf { it.esercizio.durationS } <= 120)
    }

    @Test
    fun `un esercizio piu lungo del tempo ci va comunque`() {
        val piano = SessionPlanner.plan(
            stato(2, 10).copy(riscaldamento = listOf(Esercizio("lunghissimo", 600))),
        )
        assertEquals(1, piano.passi.filterIsInstance<Passo.Riscaldamento>().size)
    }

    @Test
    fun `senza esercizi la sessione ha comunque le clip e la domanda`() {
        val piano = SessionPlanner.plan(
            stato(2, 10, clips = listOf(clip("c1"))).copy(
                riscaldamento = emptyList(),
                eserciziDiFase = emptyList(),
            ),
        )
        assertEquals(
            listOf(Passo.EsercizioSuClip("c1"), Passo.DomandaFatica),
            piano.passi,
        )
    }

    // --- il ripasso delega a SpacedRepetition -------------------------------

    @Test
    fun `il ripasso prende al massimo sei clip`() {
        val tante = (1..12).map { clip("c%02d".format(it)) }
        val piano = SessionPlanner.plan(stato(2, 10, clips = tante))

        assertEquals(6, piano.passi.filterIsInstance<Passo.EsercizioSuClip>().size)
    }

    @Test
    fun `le clip nascoste o sospette non entrano nella sessione`() {
        val piano = SessionPlanner.plan(
            stato(
                2, 10,
                clips = listOf(
                    clip("buona"),
                    clip("senza-audio", hidden = true),
                    clip("due-voci", suspect = true),
                ),
            ),
        )
        assertEquals(
            listOf("buona"),
            piano.passi.filterIsInstance<Passo.EsercizioSuClip>().map { it.clipId },
        )
    }

    @Test
    fun `la categoria della settimana passa davanti nel ripasso`() {
        val piano = SessionPlanner.plan(
            stato(
                2, 6, // settimana della categoria "ironia"
                clips = listOf(
                    clip("altra", categoria = "calma-seria", scadutaDa = 5),
                    clip("giusta", categoria = "ironia", scadutaDa = 0),
                ),
            ),
        )
        assertEquals(
            listOf("giusta", "altra"),
            piano.passi.filterIsInstance<Passo.EsercizioSuClip>().map { it.clipId },
        )
    }

    @Test
    fun `una clip non ancora scaduta non entra`() {
        val piano = SessionPlanner.plan(
            stato(2, 10, clips = listOf(clip("domani", scadutaDa = -3))),
        )
        assertTrue(piano.passi.none { it is Passo.EsercizioSuClip })
    }

    // --- fasi 3 e 4 ---------------------------------------------------------

    @Test
    fun `la fase 3 finisce con l'intervista libera`() {
        val piano = SessionPlanner.plan(stato(3, 14, clips = listOf(clip("c1"))))
        assertTrue(Passo.IntervistaLibera in piano.passi)
        assertEquals(Passo.DomandaFatica, piano.passi.last())
        assertNull(piano.categoriaDellaSettimana)
    }

    @Test
    fun `la fase 4 e breve e chiede una missione`() {
        val tante = (1..12).map { clip("c%02d".format(it)) }
        val piano = SessionPlanner.plan(stato(4, 30, clips = tante))

        assertEquals(3, piano.passi.filterIsInstance<Passo.EsercizioSuClip>().size)
        assertTrue(Passo.MissioneDiTrasferimento in piano.passi)
    }

    // --- robustezza ---------------------------------------------------------

    @Test
    fun `ogni sessione finisce con la domanda sulla fatica`() {
        val casi = listOf(
            stato(0, 1),
            stato(1, 3, clips = listOf(clip("c1"))),
            stato(2, 6, clips = listOf(clip("c1"))),
            stato(3, 14),
            stato(4, 30),
            stato(2, 6, fatica = listOf(Fatica.AFFATICATA, Fatica.AFFATICATA)),
        )
        for (s in casi) {
            val piano = SessionPlanner.plan(s)
            assertEquals(
                "fase ${s.phase}: manca la domanda finale",
                Passo.DomandaFatica,
                piano.passi.last(),
            )
        }
    }

    @Test
    fun `una fase fuori scala non manda la sessione per aria`() {
        val piano = SessionPlanner.plan(stato(7, 50, clips = listOf(clip("c1"))))
        assertEquals(4, piano.phase)
        assertTrue(piano.passi.isNotEmpty())
        assertEquals(Passo.DomandaFatica, piano.passi.last())
    }

    @Test
    fun `senza ordine delle categorie il ripasso non ha preferenze`() {
        val piano = SessionPlanner.plan(
            stato(2, 6, clips = listOf(clip("c1"))).copy(ordineCategorie = emptyList()),
        )
        assertNull(piano.categoriaDellaSettimana)
        assertTrue(piano.passi.any { it is Passo.EsercizioSuClip })
    }

    // --- dal calendario al numero di settimana ------------------------------

    @Test
    fun `la settimana si conta dal primo giorno`() {
        assertEquals(1, SessionPlanner.settimanaDa(oggi, oggi))
        assertEquals(1, SessionPlanner.settimanaDa(oggi - 6, oggi))
        assertEquals(2, SessionPlanner.settimanaDa(oggi - 7, oggi))
        assertEquals(2, SessionPlanner.settimanaDa(oggi - 13, oggi))
        assertEquals(3, SessionPlanner.settimanaDa(oggi - 14, oggi))
        assertEquals(6, SessionPlanner.settimanaDa(oggi - 35, oggi))
    }

    @Test
    fun `senza un primo giorno siamo alla settimana uno`() {
        assertEquals(1, SessionPlanner.settimanaDa(null, oggi))
    }

    @Test
    fun `un primo giorno nel futuro non da settimane negative`() {
        // Puo' succedere cambiando la data del telefono: meglio la settimana 1
        // che un numero che poi finisce in un indice.
        assertEquals(1, SessionPlanner.settimanaDa(oggi + 10, oggi))
    }

    @Test
    fun `il piano riporta fase e settimana`() {
        val piano = SessionPlanner.plan(stato(2, 6))
        assertEquals(2, piano.phase)
        assertEquals(6, piano.week)
    }
}
