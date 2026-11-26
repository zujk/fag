package com.studiofagnani.data.manifest

import com.studiofagnani.data.db.ClipAnalysisEntity
import com.studiofagnani.data.db.ClipEntity
import com.studiofagnani.data.db.ClipProgressEntity
import com.studiofagnani.data.db.ORIGIN_BUNDLED

/**
 * Decide cosa fare di un manifest rispetto a quello che c'è già nel database.
 * Funzione pura: nessun accesso a Room, nessun I/O. SPEC §4.1.
 *
 * Regole ferme:
 * - l'import **non tocca mai** i progressi di una clip che esiste già
 *   (Livello, storico, scadenza del ripasso, registrazioni);
 * - per una clip nuova il `level` del manifest diventa il Livello di partenza;
 * - una clip con `audio_present: false` o con il file mancante si importa ma
 *   resta `hidden`;
 * - un import `bundled` aggiorna solo clip `bundled`: le clip aggiunte da Ziv
 *   non vengono sovrascritte nemmeno se l'id coincide.
 */
object ManifestMerge {

    data class Plan(
        val toInsert: List<ClipEntity> = emptyList(),
        val toUpdate: List<ClipEntity> = emptyList(),
        val newProgress: List<ClipProgressEntity> = emptyList(),
        val analyses: List<ClipAnalysisEntity> = emptyList(),
        val skipped: List<String> = emptyList(),
    ) {
        val hiddenIds: List<String>
            get() = (toInsert + toUpdate).filter { it.hidden }.map { it.id }
    }

    /**
     * @param audioPresent dice se il file audio della clip esiste davvero
     *        (negli asset o in filesDir), al di là di quanto dichiara il manifest.
     * @param today epoch-day di oggi: le clip nuove sono da ripassare subito.
     */
    fun plan(
        manifest: ClipsManifest,
        existingClips: List<ClipEntity>,
        existingAnalyses: List<ClipAnalysisEntity> = emptyList(),
        origin: String = ORIGIN_BUNDLED,
        audioPresent: (ClipDto) -> Boolean,
        now: Long,
        today: Long,
    ): Plan {
        val byId = existingClips.associateBy { it.id }
        val analysisById = existingAnalyses.associateBy { it.clipId }

        val toInsert = mutableListOf<ClipEntity>()
        val toUpdate = mutableListOf<ClipEntity>()
        val newProgress = mutableListOf<ClipProgressEntity>()
        val analyses = mutableListOf<ClipAnalysisEntity>()
        val skipped = mutableListOf<String>()

        for (dto in manifest.clips) {
            val category = dto.category?.takeIf { it.isNotBlank() } ?: "Senza categoria"
            val hidden = !dto.audioPresent || !audioPresent(dto)
            val existing = byId[dto.id]

            if (existing != null && existing.origin != origin) {
                // Non sovrascriviamo una clip di origine diversa (es. aggiunta da Ziv).
                skipped += dto.id
                continue
            }

            val entity = ClipEntity(
                id = dto.id,
                file = dto.file,
                // L'origine la decide chi importa, non il manifest: un manifest
                // puo' arrivare da chiunque, ma una clip gia' in archivio tiene la sua.
                origin = existing?.origin ?: origin,
                category = category,
                categoryId = dto.categoryId?.takeIf { it.isNotBlank() } ?: slugify(category),
                context = dto.context,
                energy = dto.energy,
                studyNote = dto.studyNote,
                sourceUrl = dto.source?.url,
                sourceLabel = dto.source?.label?.takeIf { it.isNotBlank() },
                startS = dto.source?.startS,
                endS = dto.source?.endS,
                verification = dto.verification,
                checkStatus = dto.analysis?.check,
                // Scelte di Ziv e data di creazione si conservano.
                userOkOverride = existing?.userOkOverride ?: false,
                hidden = hidden,
                createdAt = existing?.createdAt ?: now,
            )

            if (existing == null) {
                toInsert += entity
                newProgress += ClipProgressEntity(
                    clipId = dto.id,
                    level = dto.level,
                    lastPracticedAt = null,
                    dueAt = today,
                    repsTotal = 0,
                )
            } else if (entity != existing) {
                toUpdate += entity
            }

            val a = dto.analysis
            if (a != null) {
                val old = analysisById[dto.id]
                // Le misure del foglio aggiornano f0/F1/F2; durata, percentuale di
                // parlato e curva calcolate dall'app restano quelle che sono.
                val merged = ClipAnalysisEntity(
                    clipId = dto.id,
                    f0Med = a.f0MedianHz ?: old?.f0Med,
                    f0P10 = a.f0P10Hz ?: old?.f0P10,
                    f0P90 = a.f0P90Hz ?: old?.f0P90,
                    f1Med = a.f1MedianHz ?: old?.f1Med,
                    f2Med = a.f2MedianHz ?: old?.f2Med,
                    durationS = old?.durationS,
                    voicedPct = old?.voicedPct,
                    contourBlob = old?.contourBlob,
                )
                if (merged.hasAnyValue()) analyses += merged
            }
        }

        return Plan(toInsert, toUpdate, newProgress, analyses, skipped)
    }

    private fun ClipAnalysisEntity.hasAnyValue(): Boolean =
        f0Med != null || f0P10 != null || f0P90 != null || f1Med != null ||
            f2Med != null || durationS != null || voicedPct != null || contourBlob != null
}

/**
 * Una clip con esito di verifica sospetto è esclusa dal ripasso finché Ziv non la
 * segna "ascoltata, va bene" (SPEC §5.4).
 */
fun ClipEntity.isSuspect(): Boolean {
    if (userOkOverride) return false
    val c = checkStatus?.trim()?.uppercase() ?: return false
    return c.startsWith("SOSPETTA") || c.startsWith("MISTA")
}
