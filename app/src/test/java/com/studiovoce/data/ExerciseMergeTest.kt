package com.studiovoce.data

import com.studiovoce.data.manifest.ExerciseDto
import com.studiovoce.data.manifest.ExerciseMerge
import com.studiovoce.data.manifest.ExercisesFile
import com.studiovoce.data.manifest.manifestJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gli esercizi sono dati come le clip (SPEC §8): un errore nel file non deve
 * mandare giu' la sessione, deve solo far scartare la riga sbagliata.
 */
class ExerciseMergeTest {

    private fun dto(
        id: String = "humming",
        phase: Int = 0,
        title: String = "Humming",
        instructions: String = "Labbra chiuse, mmm comodo.",
        durationS: Int = 30,
        audioAsset: String? = null,
    ) = ExerciseDto(id, phase, title, instructions, durationS, audioAsset)

    private fun plan(vararg esercizi: ExerciseDto) =
        ExerciseMerge.plan(ExercisesFile(exercises = esercizi.toList()))

    @Test
    fun `un esercizio valido diventa una riga del database`() {
        val p = plan(dto())
        val e = p.toUpsert.single()

        assertEquals("humming", e.id)
        assertEquals(0, e.phase)
        assertEquals("Humming", e.title)
        assertEquals(30, e.durationS)
        assertNull(e.audioAsset)
        assertTrue(p.skipped.isEmpty())
    }

    @Test
    fun `spazi di troppo non finiscono nel database`() {
        val e = plan(dto(id = "  sirena  ", title = " Sirena ", instructions = " Sali e scendi. "))
            .toUpsert.single()

        assertEquals("sirena", e.id)
        assertEquals("Sirena", e.title)
        assertEquals("Sali e scendi.", e.instructions)
    }

    @Test
    fun `un audio guida vuoto vale come assente`() {
        assertNull(plan(dto(audioAsset = "   ")).toUpsert.single().audioAsset)
        assertEquals(
            "guide/humming.wav",
            plan(dto(audioAsset = "guide/humming.wav")).toUpsert.single().audioAsset,
        )
    }

    @Test
    fun `le righe senza i campi obbligatori si scartano`() {
        val p = plan(
            dto(),
            dto(id = ""),
            dto(id = "senza-titolo", title = " "),
            dto(id = "senza-istruzioni", instructions = ""),
        )

        assertEquals(listOf("humming"), p.toUpsert.map { it.id })
        assertEquals(3, p.skipped.size)
        assertTrue(p.skipped.any { it.contains("senza id") })
        assertTrue(p.skipped.any { it.startsWith("senza-titolo") })
        assertTrue(p.skipped.any { it.startsWith("senza-istruzioni") })
    }

    @Test
    fun `una durata impossibile si scarta`() {
        val p = plan(
            dto(id = "zero", durationS = 0),
            dto(id = "negativa", durationS = -5),
            dto(id = "eterna", durationS = ExerciseMerge.MAX_DURATION_S + 1),
            dto(id = "limite", durationS = ExerciseMerge.MAX_DURATION_S),
        )

        assertEquals(listOf("limite"), p.toUpsert.map { it.id })
        assertEquals(3, p.skipped.size)
    }

    @Test
    fun `una fase negativa si scarta`() {
        val p = plan(dto(id = "sballata", phase = -1))
        assertTrue(p.toUpsert.isEmpty())
        assertTrue(p.skipped.single().contains("fase non valida"))
    }

    @Test
    fun `un id ripetuto entra una volta sola`() {
        val p = plan(dto(title = "Primo"), dto(title = "Secondo"))

        assertEquals("Primo", p.toUpsert.single().title)
        assertTrue(p.skipped.single().contains("ripetuto"))
    }

    @Test
    fun `un file vuoto non fa errore`() {
        val p = ExerciseMerge.plan(ExercisesFile())
        assertTrue(p.toUpsert.isEmpty())
        assertTrue(p.skipped.isEmpty())
        assertTrue(p.categoryOrder.isEmpty())
    }

    @Test
    fun `l'ordine delle categorie arriva pulito`() {
        val p = ExerciseMerge.plan(
            ExercisesFile(phase2CategoryOrder = listOf(" domanda-secca ", "", "risata")),
        )
        assertEquals(listOf("domanda-secca", "risata"), p.categoryOrder)
    }

    @Test
    fun `le domande della fase 0 arrivano pulite`() {
        val p = ExerciseMerge.plan(
            ExercisesFile(
                freeTalkQuestions = listOf(" Com'è andata? ", "", "   "),
                fagnaniQuestions = listOf("Di che cosa si vergogna?"),
            ),
        )
        assertEquals(listOf("Com'è andata?"), p.freeTalkQuestions)
        assertEquals(listOf("Di che cosa si vergogna?"), p.fagnaniQuestions)
    }

    @Test
    fun `senza domande nel file le liste restano vuote`() {
        val p = ExerciseMerge.plan(ExercisesFile())
        assertTrue(p.freeTalkQuestions.isEmpty())
        assertTrue(p.fagnaniQuestions.isEmpty())
    }

    @Test
    fun `il file degli asset porta le domande della fase 0`() {
        val file = manifestJson.decodeFromString<ExercisesFile>(asset("exercises.json").readText())
        val p = ExerciseMerge.plan(file)

        assertTrue("servono domande per il minuto libero", p.freeTalkQuestions.size >= 3)
        assertTrue("servono domande alla Fagnani", p.fagnaniQuestions.size >= 3)
        for (d in p.freeTalkQuestions + p.fagnaniQuestions) {
            assertTrue("domanda troppo lunga: $d", d.length <= 90)
        }
    }

    @Test
    fun `i campi sconosciuti nel file non fanno errore`() {
        val json = """
            {
              "schema_version": 1,
              "qualcosa_di_nuovo": true,
              "exercises": [{
                "id": "humming", "phase": 0, "title": "Humming",
                "instructions": "Mmm comodo.", "duration_s": 30,
                "campo_futuro": [1, 2]
              }]
            }
        """.trimIndent()
        val file = manifestJson.decodeFromString<ExercisesFile>(json)

        assertEquals(1, file.exercises.size)
        assertEquals(1, ExerciseMerge.plan(file).toUpsert.size)
    }

    // --- il file vero degli asset -------------------------------------------

    /**
     * Gli asset sono nel repo, quindi se non si trovano il test deve fallire, non
     * uscire in silenzio: un test che non asserisce niente e' peggio di nessun test.
     * Il percorso dipende da dove gira Gradle, quindi si provano i due soliti.
     */
    private fun asset(nome: String): File =
        listOf(File("src/main/assets/$nome"), File("app/src/main/assets/$nome"))
            .firstOrNull { it.isFile }
            ?: throw AssertionError(
                "asset non trovato: $nome (cartella di lavoro ${File(".").absolutePath})",
            )

    @Test
    fun `il file degli esercizi degli asset e completo e sensato`() {
        val f = asset("exercises.json")
        val file = manifestJson.decodeFromString<ExercisesFile>(f.readText())
        val p = ExerciseMerge.plan(file)

        assertTrue("nessun esercizio scartato: ${p.skipped}", p.skipped.isEmpty())
        assertTrue("la SPEC §8 chiede almeno 8 esercizi, sono ${p.toUpsert.size}",
            p.toUpsert.size >= 8)

        val riscaldamento = p.toUpsert.filter { it.phase == 0 }
        val fase1 = p.toUpsert.filter { it.phase == 1 }
        assertTrue("servono esercizi di riscaldamento", riscaldamento.size >= 3)
        assertTrue("servono esercizi della fase 1", fase1.size >= 3)

        // Il riscaldamento dura due minuti: ci devono stare almeno tre esercizi.
        assertTrue(
            "riscaldamento troppo lungo per due minuti",
            riscaldamento.take(3).sumOf { it.durationS } <= 120,
        )

        for (e in p.toUpsert) {
            assertTrue("${e.id}: titolo troppo lungo", e.title.length <= 40)
            assertTrue("${e.id}: istruzioni troppo corte", e.instructions.length >= 40)
            // Testi brevi e concreti, nessun gergo tecnico verso l'utente.
            for (gergo in listOf("risonator", "formant", "fonazion", "LPC", "YIN", "hertz", "Hz")) {
                assertTrue(
                    "${e.id}: le istruzioni parlano di '$gergo'",
                    !e.instructions.contains(gergo, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `l'ordine delle categorie degli asset copre le categorie delle clip`() {
        val esercizi = asset("exercises.json")
        val clips = asset("clips/clips.json")

        val ordine = manifestJson
            .decodeFromString<ExercisesFile>(esercizi.readText())
            .phase2CategoryOrder
        val dalleClip = manifestJson
            .decodeFromString<com.studiovoce.data.manifest.ClipsManifest>(clips.readText())
            .clips
            .mapNotNull { it.categoryId }
            .toSet()

        assertEquals(
            "l'ordine della fase 2 deve coprire esattamente le categorie delle clip",
            dalleClip.sorted(),
            ordine.sorted(),
        )
    }
}
