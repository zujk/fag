package com.studiovoce.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Riproduzione di una clip con velocita' e loop A-B (SPEC §5.3).
 *
 * ExoPlayer vuole essere comandato dal thread principale: tutti i metodi `suspend`
 * passano da `Dispatchers.Main`. Il loop A-B e il taglio su una porzione sono fatti
 * controllando la posizione a intervalli brevi, cosi' funzionano identici sugli asset
 * dell'APK e sui file registrati.
 */
class ClipPlayer(context: Context) {

    data class Progress(val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false)

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var loadedUri: String? = null

    /**
     * Dopo `release()` il player non si tocca piu'. Serve perche' il blocco `finally`
     * di una coroutine annullata puo' girare dopo che la schermata e' stata chiusa.
     */
    @Volatile
    private var released = false

    /** Durata della clip caricata, in millisecondi, o null se non si sa ancora. */
    suspend fun durationMs(uri: String): Long? = withContext(Dispatchers.Main) {
        if (released) return@withContext null
        load(uri)
        // La durata arriva quando il player ha letto l'intestazione.
        repeat(100) {
            val d = player.duration
            if (d != C.TIME_UNSET && d > 0) return@withContext d
            delay(20)
        }
        null
    }

    /**
     * Suona [uri] da [fromMs] a [toMs] e torna quando ha finito.
     * Con [loop] vero continua finche' la coroutine chiamante non viene annullata.
     */
    suspend fun play(
        uri: String,
        fromMs: Long = 0,
        toMs: Long? = null,
        speed: Float = 1f,
        loop: Boolean = false,
    ) = withContext(Dispatchers.Main) {
        if (released) return@withContext
        try {
            load(uri)
            player.setPlaybackSpeed(speed)
            player.seekTo(fromMs.coerceAtLeast(0))
            player.play()

            while (true) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                val position = player.currentPosition
                _progress.value = Progress(position, duration, player.isPlaying)

                val end = toMs ?: duration.takeIf { it > 0 }
                val finished = (end != null && position >= end) ||
                    player.playbackState == Player.STATE_ENDED

                if (finished) {
                    if (!loop) {
                        player.pause()
                        _progress.value = Progress(end ?: position, duration, false)
                        return@withContext
                    }
                    player.seekTo(fromMs.coerceAtLeast(0))
                    player.play()
                }
                delay(TICK_MS)
            }
        } finally {
            // Se la coroutine viene annullata (tocco su "ferma", uscita dalla schermata)
            // il player non deve restare in riproduzione.
            if (!released) player.pause()
            _progress.value = _progress.value.copy(playing = false)
        }
    }

    suspend fun stop() = withContext(Dispatchers.Main) {
        if (!released) {
            player.pause()
            player.seekTo(0)
        }
        _progress.value = Progress()
    }

    suspend fun pause() = withContext(Dispatchers.Main) {
        if (!released) player.pause()
        _progress.value = _progress.value.copy(playing = false)
    }

    /** Pausa di silenzio fra due ascolti (SPEC §5.3: 1 secondo). */
    suspend fun silence(ms: Long) = delay(ms)

    private fun load(uri: String) {
        if (loadedUri == uri && player.playbackState != Player.STATE_IDLE) return
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        loadedUri = uri
    }

    fun release() {
        if (released) return
        released = true
        player.release()
        loadedUri = null
    }

    companion object {
        /** Abbastanza fitto per un loop A-B pulito, abbastanza raro da non pesare. */
        private const val TICK_MS = 30L
    }
}
