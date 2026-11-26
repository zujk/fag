package com.studiofagnani.data.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Schema del manifest, SPEC §4.2. `ignoreUnknownKeys` è obbligatorio:
 * i campi che non conosciamo si ignorano senza errore.
 */
val manifestJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

@Serializable
data class ClipsManifest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val categories: List<String> = emptyList(),
    val clips: List<ClipDto> = emptyList(),
)

@Serializable
data class ClipDto(
    val id: String,
    val file: String,
    @SerialName("audio_present") val audioPresent: Boolean = true,
    val category: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val context: String? = null,
    val energy: String? = null,
    @SerialName("study_note") val studyNote: String? = null,
    val level: Int? = null,
    val source: SourceDto? = null,
    val verification: String? = null,
    val analysis: AnalysisDto? = null,
    val origin: String? = null,
)

@Serializable
data class SourceDto(
    val url: String? = null,
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("start_s") val startS: Double? = null,
    @SerialName("end_s") val endS: Double? = null,
    val label: String? = null,
)

@Serializable
data class AnalysisDto(
    @SerialName("f0_median_hz") val f0MedianHz: Double? = null,
    @SerialName("f0_p10_hz") val f0P10Hz: Double? = null,
    @SerialName("f0_p90_hz") val f0P90Hz: Double? = null,
    @SerialName("f1_median_hz") val f1MedianHz: Double? = null,
    @SerialName("f2_median_hz") val f2MedianHz: Double? = null,
    @SerialName("speaker_similarity") val speakerSimilarity: Double? = null,
    val check: String? = null,
)

fun slugify(s: String): String =
    s.lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
        .joinToString("")
        .split('-').filter { it.isNotEmpty() }
        .joinToString("-")
