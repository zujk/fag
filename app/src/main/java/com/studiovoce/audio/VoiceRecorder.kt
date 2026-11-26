package com.studiovoce.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.studiovoce.dsp.WavIo
import com.studiovoce.dsp.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Registra la ripetizione di Ziv in WAV PCM 16 bit mono 44.100 Hz (SPEC §3) e si ferma
 * da sola dopo 1,5 s di silenzio (SPEC §5.3). La decisione di fermarsi sta tutta in
 * [SilenceDetector], che e' logica pura e testata a parte.
 *
 * Il permesso del microfono va chiesto prima: qui si presume concesso.
 */
class VoiceRecorder(
    private val context: Context,
    private val config: RecordingConfig = RecordingConfig(),
) {

    sealed interface Result {
        /** Registrazione riuscita, con il file e la durata vera. */
        data class Saved(
            val file: File,
            val durationS: Double,
            val stoppedBy: Stop,
            val peak: Float,
        ) : Result

        /** Non si e' sentito niente: il file viene cancellato. */
        data object NoSpeech : Result

        data class Failed(val message: String) : Result
    }

    enum class Stop { SILENZIO, TETTO_DI_DURATA, TOCCO }

    @Volatile
    private var stopRequested = false

    @Volatile
    private var recording = false

    val isRecording: Boolean get() = recording

    /** Ferma la registrazione tenendo quello che e' stato registrato (il tocco di SPEC §5.3). */
    fun requestStop() {
        stopRequested = true
    }

    /**
     * @param target file WAV da scrivere
     * @param maxDurationMs tetto di durata (vedi [RecordingConfig.maxDurationMs])
     * @param onLevel livello del blocco corrente, per la barra sullo schermo
     */
    @SuppressLint("MissingPermission")
    suspend fun record(
        target: File,
        maxDurationMs: Long,
        onLevel: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        stopRequested = false
        val sampleRate = WavIo.DEFAULT_SAMPLE_RATE
        val blockSamples = sampleRate * BLOCK_MS / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return@withContext Result.Failed(
                "Questo telefono non accetta la registrazione a $sampleRate Hz.",
            )
        }

        val record = try {
            AudioRecord(
                audioSource(),
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, blockSamples * 2 * 4),
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord non creato", e)
            return@withContext Result.Failed("Microfono non disponibile")
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            // Succede quando manca il permesso del microfono (le schermate lo
            // chiedono prima, ma il messaggio deve dire cosa fare anche se un
            // giorno qualcuno chiama qui senza passare da loro) oppure quando
            // un'altra app lo sta tenendo occupato.
            return@withContext Result.Failed(
                "Il microfono non si è avviato. Controlla che Studio Voce abbia il " +
                    "permesso del microfono e che non lo stia usando un'altra app.",
            )
        }

        val detector = SilenceDetector(config)
        val buffer = ShortArray(blockSamples)
        var stop = Stop.TETTO_DI_DURATA
        var peak = 0f
        var writer: WavIo.WavWriter? = null

        try {
            recording = true
            record.startRecording()
            val w = WavIo.WavWriter(target, sampleRate)
            writer = w

            while (true) {
                if (stopRequested) {
                    stop = Stop.TOCCO
                    break
                }
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        return@withContext Result.Failed("Lettura dal microfono non riuscita")
                    }
                    continue
                }
                w.write(buffer, read)

                val rms = Waveform.rms(buffer, read)
                peak = maxOf(peak, rms)
                onLevel(rms)

                val blockMs = read.toLong() * 1000 / sampleRate
                when (detector.onBlock(rms, blockMs, maxDurationMs)) {
                    SilenceDetector.Decision.CONTINUE -> Unit
                    SilenceDetector.Decision.STOP_AFTER_SPEECH -> {
                        stop = Stop.SILENZIO
                        break
                    }
                    SilenceDetector.Decision.STOP_MAX_DURATION -> {
                        stop = Stop.TETTO_DI_DURATA
                        break
                    }
                    SilenceDetector.Decision.STOP_NO_SPEECH -> {
                        w.close()
                        writer = null
                        target.delete()
                        return@withContext Result.NoSpeech
                    }
                }
            }

            val durationS = w.durationS
            w.close()
            writer = null
            Result.Saved(target, durationS, stop, peak)
        } catch (e: Exception) {
            Log.e(TAG, "Registrazione non riuscita", e)
            Result.Failed(e.message ?: "Registrazione non riuscita")
        } finally {
            recording = false
            stopRequested = false
            runCatching { writer?.close() }
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * Per l'analisi dell'altezza conviene l'audio grezzo: se il telefono offre
     * `UNPROCESSED` lo usiamo, altrimenti il microfono normale.
     */
    private fun audioSource(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessed = am
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.MIC
    }

    companion object {
        private const val TAG = "VoiceRecorder"

        /** Blocchi da 20 ms: abbastanza fini per il rilevamento del silenzio. */
        const val BLOCK_MS = 20
    }
}
