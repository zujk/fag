package com.studiofagnani.data

import android.content.Context
import com.studiofagnani.data.db.ClipEntity
import com.studiofagnani.data.db.ORIGIN_BUNDLED
import java.io.File

/**
 * Dove stanno i file audio. Le clip della voice bank sono negli asset dell'APK,
 * quelle aggiunte da Ziv in `filesDir/clips/`, le ripetizioni in `filesDir/attempts/`.
 */
class ClipFiles(private val context: Context) {

    val userClipsDir: File get() = File(context.filesDir, "clips").apply { mkdirs() }
    val attemptsDir: File get() = File(context.filesDir, "attempts").apply { mkdirs() }

    /** Le registrazioni guidate della fase 0 e dei controlli (SPEC §5.1 e §5.6). */
    val checkpointsDir: File get() = File(context.filesDir, "checkpoints").apply { mkdirs() }

    /** URI che ExoPlayer sa aprire, sia dagli asset sia dai file locali. */
    fun mediaUri(clip: ClipEntity): String =
        if (clip.origin == ORIGIN_BUNDLED) "asset:///$ASSET_DIR/${clip.file}"
        else File(userClipsDir, clip.file).toURI().toString()

    /** Il file vero, quando serve leggerne i campioni (forma d'onda, misure). */
    fun openClip(clip: ClipEntity) =
        if (clip.origin == ORIGIN_BUNDLED) context.assets.open("$ASSET_DIR/${clip.file}")
        else File(userClipsDir, clip.file).inputStream()

    fun clipExists(clip: ClipEntity): Boolean =
        if (clip.origin == ORIGIN_BUNDLED) bundledFiles().contains(clip.file)
        else File(userClipsDir, clip.file).isFile

    /** Nomi dei WAV presenti negli asset: serve all'import per sapere cosa c'e' davvero. */
    fun bundledFiles(): Set<String> = cachedAssetNames ?: runCatching {
        context.assets.list(ASSET_DIR)?.toSet() ?: emptySet()
    }.getOrDefault(emptySet()).also { cachedAssetNames = it }

    fun newAttemptFile(clipId: String, timestamp: Long): File =
        File(attemptsDir, "${clipId}_$timestamp.wav")

    /**
     * Le tre registrazioni di un controllo condividono il timestamp nel nome, cosi'
     * stanno insieme anche se la tabella `checkpoint` ha solo due colonne per i file:
     * la terza (la risposta alla domanda) si ritrova accanto alle altre due.
     */
    fun newCheckpointFile(tipo: String, timestamp: Long): File =
        File(checkpointsDir, "checkpoint_${timestamp}_$tipo.wav")

    fun deleteAllAttempts(): Int {
        val files = attemptsDir.listFiles().orEmpty()
        var n = 0
        for (f in files) if (f.delete()) n++
        return n
    }

    private var cachedAssetNames: Set<String>? = null

    companion object {
        const val ASSET_DIR = "clips"
        const val MANIFEST_ASSET = "clips/clips.json"
    }
}
