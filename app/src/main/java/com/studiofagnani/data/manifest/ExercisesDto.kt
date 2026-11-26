package com.studiofagnani.data.manifest

import com.studiofagnani.data.db.ExerciseEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schema di `assets/exercises.json` (SPEC §8): gli esercizi del riscaldamento e
 * della fase 1 sono dati, non codice, come le clip.
 *
 * Convenzione sul campo `phase`, che il file documenta a sua volta:
 * - `0` = riscaldamento, va bene in qualunque fase;
 * - `1` = blocco di esercizi della fase 1 (settimane 2-8).
 *
 * `phase2_category_order` sta qui e non in `clips.json` perche' quel file lo
 * rigenera `build_manifest.py` e l'ordine andrebbe perso a ogni rigenerazione.
 */
@Serializable
data class ExercisesFile(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("phase2_category_order") val phase2CategoryOrder: List<String> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
)

@Serializable
data class ExerciseDto(
    val id: String = "",
    val phase: Int = 0,
    val title: String = "",
    val instructions: String = "",
    @SerialName("duration_s") val durationS: Int = 0,
    @SerialName("audio_asset") val audioAsset: String? = null,
)

/**
 * Da file a righe del database. Puro, senza I/O: e' la parte che decide cosa
 * entra e cosa si scarta, quindi si prova a tavolino.
 *
 * Gli esercizi non hanno progressi attaccati, quindi un import nuovo puo'
 * sovrascriverli senza rischi: l'unica cosa che si perderebbe sono i testi, che
 * arrivano dal file stesso.
 */
object ExerciseMerge {

    /** Durata massima accettata: oltre, il file ha quasi certamente un errore. */
    const val MAX_DURATION_S = 600

    data class Plan(
        val toUpsert: List<ExerciseEntity> = emptyList(),
        /** Id (o posizione, se manca l'id) di cio' che e' stato scartato, con il perche'. */
        val skipped: List<String> = emptyList(),
        val categoryOrder: List<String> = emptyList(),
    )

    fun plan(file: ExercisesFile): Plan {
        val buoni = mutableListOf<ExerciseEntity>()
        val scartati = mutableListOf<String>()
        val visti = mutableSetOf<String>()

        for ((posizione, dto) in file.exercises.withIndex()) {
            val id = dto.id.trim()
            val motivo = when {
                id.isEmpty() -> "senza id"
                dto.title.isBlank() -> "senza titolo"
                dto.instructions.isBlank() -> "senza istruzioni"
                dto.durationS <= 0 -> "durata non valida (${dto.durationS})"
                dto.durationS > MAX_DURATION_S -> "durata troppo lunga (${dto.durationS})"
                dto.phase < 0 -> "fase non valida (${dto.phase})"
                !visti.add(id) -> "id ripetuto"
                else -> null
            }
            if (motivo != null) {
                scartati += "${id.ifEmpty { "posizione $posizione" }}: $motivo"
                continue
            }
            buoni += ExerciseEntity(
                id = id,
                phase = dto.phase,
                title = dto.title.trim(),
                instructions = dto.instructions.trim(),
                durationS = dto.durationS,
                audioAsset = dto.audioAsset?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        return Plan(
            toUpsert = buoni,
            skipped = scartati,
            categoryOrder = file.phase2CategoryOrder.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}
