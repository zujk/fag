package com.studiofagnani.audio

/**
 * Decide quando fermare da sola una registrazione (SPEC §5.3: "si ferma da sola dopo
 * 1,5 secondi di silenzio", con un tetto di durata).
 *
 * E' una macchina a stati pura: riceve il livello di ogni blocco audio e risponde
 * cosa fare. Nessuna API Android, nessun orologio: si testa con blocchi finti.
 *
 * Il rumore di fondo si stima dai primi blocchi, cosi' funziona sia in una stanza
 * silenziosa sia in macchina.
 */
class SilenceDetector(private val config: RecordingConfig = RecordingConfig()) {

    enum class Decision {
        /** Continua a registrare. */
        CONTINUE,

        /** Ha parlato e poi ha smesso: fermati e tieni la registrazione. */
        STOP_AFTER_SPEECH,

        /** Tetto di durata raggiunto: fermati. */
        STOP_MAX_DURATION,

        /** Non ha mai parlato entro il tempo di attesa: fermati, non c'e' niente da tenere. */
        STOP_NO_SPEECH,
    }

    private var elapsedMs = 0L
    private var calibrationMs = 0L
    private var noiseSum = 0.0
    private var noiseBlocks = 0
    private var noiseFloor = config.minNoiseFloor
    private var silenceMs = 0L

    /** Vero da quando il livello ha superato la soglia per la prima volta. */
    var speechStarted: Boolean = false
        private set

    /** Istante (ms dall'inizio) in cui il parlato e' cominciato, per rifilare il silenzio iniziale. */
    var speechStartMs: Long = -1L
        private set

    /** Istante dell'ultimo blocco con parlato. */
    var speechEndMs: Long = -1L
        private set

    val threshold: Float
        get() = maxOf(config.minNoiseFloor, noiseFloor * config.noiseFactor)

    /**
     * @param rms livello efficace del blocco, in [0, 1]
     * @param blockMs durata del blocco in millisecondi
     * @param maxDurationMs tetto di durata della registrazione
     */
    fun onBlock(rms: Float, blockMs: Long, maxDurationMs: Long): Decision {
        elapsedMs += blockMs

        // Taratura: i primi blocchi servono solo a misurare il rumore di fondo.
        if (calibrationMs < config.calibrationMs) {
            calibrationMs += blockMs
            noiseSum += rms.toDouble()
            noiseBlocks++
            noiseFloor = (noiseSum / noiseBlocks).toFloat().coerceAtLeast(config.minNoiseFloor)
            return if (elapsedMs >= maxDurationMs) Decision.STOP_MAX_DURATION else Decision.CONTINUE
        }

        val loud = rms >= threshold
        if (loud) {
            if (!speechStarted) {
                speechStarted = true
                speechStartMs = elapsedMs - blockMs
            }
            speechEndMs = elapsedMs
            silenceMs = 0
        } else if (speechStarted) {
            silenceMs += blockMs
        }

        return when {
            elapsedMs >= maxDurationMs -> Decision.STOP_MAX_DURATION
            speechStarted && silenceMs >= config.trailingSilenceMs -> Decision.STOP_AFTER_SPEECH
            !speechStarted && elapsedMs >= config.noSpeechTimeoutMs -> Decision.STOP_NO_SPEECH
            else -> Decision.CONTINUE
        }
    }

    fun reset() {
        elapsedMs = 0
        calibrationMs = 0
        noiseSum = 0.0
        noiseBlocks = 0
        noiseFloor = config.minNoiseFloor
        silenceMs = 0
        speechStarted = false
        speechStartMs = -1
        speechEndMs = -1
    }
}

/**
 * Soglie della registrazione, tutte in un posto solo (come chiede SPEC §5.5 per le
 * soglie del riscontro): non devono finire sparse nel codice.
 */
data class RecordingConfig(
    /** Silenzio dopo il parlato che fa fermare la registrazione. */
    val trailingSilenceMs: Long = 1_500,

    /** Se non si sente niente entro questo tempo, la registrazione si chiude a vuoto. */
    val noSpeechTimeoutMs: Long = 6_000,

    /** Quanto audio si usa per stimare il rumore di fondo prima di decidere qualcosa. */
    val calibrationMs: Long = 300,

    /** Quante volte il rumore di fondo per considerare "parlato". */
    val noiseFactor: Float = 3.0f,

    /** Pavimento assoluto: sotto questo livello non e' mai parlato. */
    val minNoiseFloor: Float = 0.006f,

    /** Secondi aggiunti al doppio della clip per il tetto di durata (SPEC §5.3). */
    val maxDurationExtraS: Double = 2.0,
) {
    /** Tetto di durata per la ripetizione di una clip lunga [clipDurationS] secondi. */
    fun maxDurationMs(clipDurationS: Double): Long =
        ((clipDurationS * 2 + maxDurationExtraS) * 1000).toLong().coerceAtLeast(2_000)
}
