package com.studiofagnani.data

import com.studiofagnani.data.db.ClipAnalysisEntity
import com.studiofagnani.data.db.ClipEntity
import com.studiofagnani.data.db.ORIGIN_BUNDLED
import com.studiofagnani.data.db.ORIGIN_USER
import com.studiofagnani.data.manifest.AnalysisDto
import com.studiofagnani.data.manifest.ClipDto
import com.studiofagnani.data.manifest.ClipsManifest
import com.studiofagnani.data.manifest.ManifestMerge
import com.studiofagnani.data.manifest.SourceDto
import com.studiofagnani.data.manifest.isSuspect
import com.studiofagnani.data.manifest.manifestJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ManifestMergeTest {

    private val now = 1_700_000_000_000L
    private val today = LocalDate.of(2026, 11, 25).toEpochDay()

    private fun dto(
        id: String = "domanda-secca_01_corona",
        level: Int? = null,
        audioPresent: Boolean = true,
        check: String? = null,
        note: String? = "nota",
    ) = ClipDto(
        id = id,
        file = "$id.wav",
        audioPresent = audioPresent,
        category = "Domanda secca",
        categoryId = "domanda-secca",
        context = "In scena",
        energy = "Calma",
        studyNote = note,
        level = level,
        source = SourceDto(url = "https://example.invalid/v", startS = 1.0, endS = 3.0, label = "Belve"),
        verification = "OK - pulito",
        analysis = check?.let { AnalysisDto(f0MedianHz = 190.0, check = it) },
        origin = ORIGIN_BUNDLED,
    )

    private fun plan(
        clips: List<ClipDto>,
        existing: List<ClipEntity> = emptyList(),
        analyses: List<ClipAnalysisEntity> = emptyList(),
        origin: String = ORIGIN_BUNDLED,
        audioPresent: (ClipDto) -> Boolean = { it.audioPresent },
    ) = ManifestMerge.plan(
        manifest = ClipsManifest(clips = clips),
        existingClips = existing,
        existingAnalyses = analyses,
        origin = origin,
        audioPresent = audioPresent,
        now = now,
        today = today,
    )

    @Test
    fun `al primo import tutte le clip entrano con il livello del manifest`() {
        val p = plan(listOf(dto(level = 3), dto(id = "risata_01", level = null)))
        assertEquals(2, p.toInsert.size)
        assertEquals(2, p.newProgress.size)
        assertTrue(p.toUpdate.isEmpty())

        val progress = p.newProgress.associateBy { it.clipId }
        assertEquals(3, progress.getValue("domanda-secca_01_corona").level)
        assertNull(progress.getValue("risata_01").level)
        // Una clip nuova e' da fare subito.
        for (pr in p.newProgress) assertEquals(today, pr.dueAt)
    }

    @Test
    fun `un import successivo non crea ne tocca i progressi`() {
        val first = plan(listOf(dto(level = 1)))
        val existing = first.toInsert

        // Il manifest nuovo cambia la nota e riporta level 1, ma Ziv e' arrivata a 4.
        val second = plan(listOf(dto(level = 1, note = "nota aggiornata")), existing = existing)
        assertTrue("non deve inserire di nuovo", second.toInsert.isEmpty())
        assertTrue("non deve creare progressi", second.newProgress.isEmpty())
        assertEquals(1, second.toUpdate.size)
        assertEquals("nota aggiornata", second.toUpdate.first().studyNote)
    }

    @Test
    fun `un import che non cambia niente non scrive niente`() {
        val existing = plan(listOf(dto())).toInsert
        val again = plan(listOf(dto()), existing = existing)
        assertTrue(again.toInsert.isEmpty())
        assertTrue(again.toUpdate.isEmpty())
        assertTrue(again.newProgress.isEmpty())
    }

    @Test
    fun `l'aggiornamento conserva data di creazione e conferma di Ziv`() {
        val existing = listOf(
            plan(listOf(dto())).toInsert.first()
                .copy(createdAt = 111L, userOkOverride = true),
        )
        val p = plan(listOf(dto(note = "altra nota")), existing = existing)
        val updated = p.toUpdate.single()
        assertEquals(111L, updated.createdAt)
        assertTrue(updated.userOkOverride)
    }

    @Test
    fun `una clip senza audio si importa ma resta nascosta`() {
        val p = plan(listOf(dto(audioPresent = false)))
        val c = p.toInsert.single()
        assertTrue(c.hidden)
        assertEquals(listOf(c.id), p.hiddenIds)
    }

    @Test
    fun `il manifest dice che l'audio c'e ma il file manca comunque`() {
        val p = plan(listOf(dto(audioPresent = true)), audioPresent = { false })
        assertTrue(p.toInsert.single().hidden)
    }

    @Test
    fun `una clip torna visibile quando l'audio arriva`() {
        val existing = plan(listOf(dto(audioPresent = false))).toInsert
        assertTrue(existing.single().hidden)
        val p = plan(listOf(dto(audioPresent = true)), existing = existing)
        assertFalse(p.toUpdate.single().hidden)
    }

    @Test
    fun `un import bundled non sovrascrive una clip aggiunta da Ziv`() {
        val mine = plan(listOf(dto()), origin = ORIGIN_USER).toInsert.single()
        assertEquals(ORIGIN_USER, mine.origin)
        val p = plan(listOf(dto(note = "dal manifest")), existing = listOf(mine))
        assertTrue(p.toInsert.isEmpty())
        assertTrue(p.toUpdate.isEmpty())
        assertEquals(listOf(mine.id), p.skipped)
    }

    @Test
    fun `le misure del foglio arrivano nel database`() {
        val p = plan(listOf(dto(check = "OK")))
        val a = p.analyses.single()
        assertEquals(190.0, a.f0Med!!, 1e-9)
        assertEquals("OK", p.toInsert.single().checkStatus)
    }

    @Test
    fun `le misure calcolate dall'app non vengono cancellate dal foglio`() {
        val existing = plan(listOf(dto(check = "OK"))).toInsert
        val mine = ClipAnalysisEntity(
            clipId = existing.single().id,
            f0Med = 150.0, f0P10 = null, f0P90 = null, f1Med = null, f2Med = null,
            durationS = 2.2, voicedPct = 68.0, contourBlob = byteArrayOf(1, 2, 3),
        )
        val p = plan(listOf(dto(check = "OK")), existing = existing, analyses = listOf(mine))
        val merged = p.analyses.single()
        assertEquals(190.0, merged.f0Med!!, 1e-9)   // il foglio aggiorna f0
        assertEquals(2.2, merged.durationS!!, 1e-9) // durata e curva restano
        assertEquals(68.0, merged.voicedPct!!, 1e-9)
        assertEquals(3, merged.contourBlob!!.size)
    }

    @Test
    fun `le clip sospette sono escluse finche Ziv non le conferma`() {
        val sospetta = plan(listOf(dto(check = "SOSPETTA - due voci"))).toInsert.single()
        assertTrue(sospetta.isSuspect())
        assertTrue(sospetta.copy(checkStatus = "MISTA (2 parlanti)").isSuspect())
        assertFalse(sospetta.copy(userOkOverride = true).isSuspect())
        assertFalse(sospetta.copy(checkStatus = "OK - pulito").isSuspect())
        assertFalse(sospetta.copy(checkStatus = null).isSuspect())
    }

    @Test
    fun `i campi sconosciuti nel manifest non fanno errore`() {
        val json = """
            {
              "schema_version": 1,
              "qualcosa_di_nuovo": {"a": 1},
              "categories": ["Domanda secca"],
              "clips": [{
                "id": "x_01", "file": "x_01.wav", "audio_present": true,
                "category": "Domanda secca", "category_id": "domanda-secca",
                "campo_futuro": [1, 2, 3],
                "source": {"url": "https://example.invalid", "altro": true}
              }]
            }
        """.trimIndent()
        val m = manifestJson.decodeFromString<ClipsManifest>(json)
        assertEquals(1, m.clips.size)
        assertEquals("x_01", m.clips.single().id)
        assertNull(m.clips.single().level)
    }

    /**
     * I WAV non stanno nel repo (sono audio televisivo): in CI questo test gira con i
     * soli metadati. L'asserzione resta vera in entrambi i casi, perché controlla che
     * le clip nascoste siano esattamente quelle il cui file manca davvero.
     */
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
    fun `il manifest della voice bank si legge e descrive 40 clip`() {
        val f = asset("clips/clips.json")
        val m = manifestJson.decodeFromString<ClipsManifest>(f.readText())
        assertEquals(1, m.schemaVersion)
        assertEquals(8, m.categories.size)
        assertEquals(40, m.clips.size)

        val dir = f.parentFile
        val conFile = m.clips.filter { File(dir, it.file).isFile }
        val p = plan(m.clips, audioPresent = { File(dir, it.file).isFile })

        assertEquals(40, p.toInsert.size)
        assertEquals(
            "nascoste devono essere solo le clip senza WAV",
            (m.clips - conFile.toSet()).map { it.id }.sorted(),
            p.hiddenIds.sorted(),
        )
        for (c in p.toInsert) {
            assertTrue("categoria mancante in ${c.id}", c.category.isNotBlank())
            assertTrue("nota mancante in ${c.id}", !c.studyNote.isNullOrBlank())
            assertTrue("id e file non combaciano in ${c.id}", c.file.startsWith(c.id))
        }
        // Con i WAV presenti (sviluppo in locale) nessuna clip deve restare nascosta.
        if (conFile.size == 40) assertTrue(p.hiddenIds.isEmpty())
    }
}
