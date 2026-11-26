package com.studiofagnani.ui.practice

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiofagnani.AppContainer
import com.studiofagnani.audio.ClipPlayer
import com.studiofagnani.audio.RecordingConfig
import com.studiofagnani.audio.VoiceRecorder
import com.studiofagnani.data.ClipCard
import com.studiofagnani.data.ClipRepository
import com.studiofagnani.ui.components.Selezione
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** I passi dell'esercizio su una clip (SPEC §5.3). */
enum class Passo { ASCOLTA, RIPETI, CONFRONTA, VOTO }

data class PracticeState(
    val caricamento: Boolean = true,
    val card: ClipCard? = null,
    val passo: Passo = Passo.ASCOLTA,
    val indiceInCoda: Int = 0,
    val clipInCoda: Int = 1,
    val ascoltiFatti: Int = 0,
    val peaksClip: FloatArray? = null,
    val peaksRipetizione: FloatArray? = null,
    /** Posizione di riproduzione come frazione 0..1. */
    val posizione: Float? = null,
    val velocita: Float = 1f,
    val selezione: Selezione? = null,
    val inRiproduzione: Boolean = false,
    val registrando: Boolean = false,
    val livelloMicrofono: Float = 0f,
    val durataRipetizione: Double? = null,
    val livello: Int? = null,
    val messaggio: String? = null,
    val finita: Boolean = false,
) {
    val ultimaDellaCoda: Boolean get() = indiceInCoda >= clipInCoda - 1

    // peaks sono FloatArray: equals/hashCode generati non vanno bene per Compose.
    override fun equals(other: Any?): Boolean =
        other is PracticeState &&
            caricamento == other.caricamento &&
            card == other.card &&
            passo == other.passo &&
            indiceInCoda == other.indiceInCoda &&
            clipInCoda == other.clipInCoda &&
            ascoltiFatti == other.ascoltiFatti &&
            peaksClip === other.peaksClip &&
            peaksRipetizione === other.peaksRipetizione &&
            posizione == other.posizione &&
            velocita == other.velocita &&
            selezione == other.selezione &&
            inRiproduzione == other.inRiproduzione &&
            registrando == other.registrando &&
            livelloMicrofono == other.livelloMicrofono &&
            durataRipetizione == other.durataRipetizione &&
            livello == other.livello &&
            messaggio == other.messaggio &&
            finita == other.finita

    override fun hashCode(): Int =
        listOf(
            caricamento, card, passo, indiceInCoda, clipInCoda, ascoltiFatti,
            posizione, velocita, selezione, inRiproduzione, registrando,
            livelloMicrofono, durataRipetizione, livello, messaggio, finita,
        ).hashCode()
}

class PracticeViewModel(
    private val repo: ClipRepository,
    private val player: ClipPlayer,
    private val recorder: VoiceRecorder,
    private val config: RecordingConfig,
    private val coda: List<String>,
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeState(clipInCoda = coda.size.coerceAtLeast(1)))
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    private var indice = 0
    private var lavoro: Job? = null
    private var fileRipetizione: File? = null
    private var attemptId: Long? = null
    private var durataClipMs: Long = 0

    init {
        caricaClip(0)
    }

    // --- caricamento ---

    private fun caricaClip(i: Int) {
        indice = i
        lavoro?.cancel()
        _state.value = PracticeState(
            caricamento = true,
            indiceInCoda = i,
            clipInCoda = coda.size.coerceAtLeast(1),
        )
        fileRipetizione = null
        attemptId = null

        viewModelScope.launch {
            val id = coda.getOrNull(i)
            if (id == null) {
                _state.value = _state.value.copy(caricamento = false, finita = true)
                return@launch
            }
            val card = repo.cards.first().firstOrNull { it.id == id }
            if (card == null) {
                _state.value = _state.value.copy(
                    caricamento = false,
                    messaggio = "Clip non trovata.",
                )
                return@launch
            }
            val peaks = repo.waveform(card.clip, BUCKETS)
            durataClipMs = player.durationMs(repo.mediaUri(card.clip))
                ?: ((card.durationS ?: 0.0) * 1000).toLong()
            _state.value = _state.value.copy(
                caricamento = false,
                card = card,
                livello = card.level,
                peaksClip = peaks,
                messaggio = if (peaks == null) "Audio della clip non leggibile." else null,
            )
        }
    }

    // --- 1. Ascolta ---

    /** La clip suona 3 volte con una pausa di 1 s (SPEC §5.3), o in loop se c'è una selezione. */
    fun ascolta(volte: Int = ASCOLTI) {
        val card = _state.value.card ?: return
        lavoro?.cancel()
        _state.value = _state.value.copy(ascoltiFatti = 0, passo = Passo.ASCOLTA)
        lavoro = viewModelScope.launch {
            val uri = repo.mediaUri(card.clip)
            val sel = _state.value.selezione?.takeIf { it.valida }
            val da = sel?.let { (it.from * durataClipMs).toLong() } ?: 0L
            val a = sel?.let { (it.to * durataClipMs).toLong() }
            _state.value = _state.value.copy(inRiproduzione = true)
            seguiPosizione()
            try {
                if (sel != null) {
                    // Loop A-B: va avanti finché non si tocca "ferma".
                    player.play(uri, da, a, _state.value.velocita, loop = true)
                } else {
                    for (n in 1..volte) {
                        player.play(uri, 0, null, _state.value.velocita)
                        _state.value = _state.value.copy(ascoltiFatti = n)
                        if (n < volte) player.silence(PAUSA_MS)
                    }
                }
            } finally {
                _state.value = _state.value.copy(inRiproduzione = false, posizione = null)
            }
        }
    }

    fun ferma() {
        lavoro?.cancel()
        viewModelScope.launch { player.stop() }
        _state.value = _state.value.copy(inRiproduzione = false, posizione = null)
    }

    private fun seguiPosizione() {
        viewModelScope.launch {
            while (_state.value.inRiproduzione) {
                val p = player.progress.value
                if (p.durationMs > 0) {
                    _state.value = _state.value.copy(
                        posizione = (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f),
                    )
                }
                delay(40)
            }
        }
    }

    fun setVelocita(v: Float) {
        _state.value = _state.value.copy(velocita = v)
    }

    fun setSelezione(s: Selezione?) {
        _state.value = _state.value.copy(selezione = s)
    }

    // --- 2. Ripeti ---

    fun vaiARipeti() {
        ferma()
        _state.value = _state.value.copy(passo = Passo.RIPETI, messaggio = null)
    }

    /** Beep, poi registrazione con stop automatico sul silenzio. */
    fun registra() {
        val card = _state.value.card ?: return
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            beep()
            val file = repo.newAttemptFile(card.id)
            _state.value = _state.value.copy(registrando = true, messaggio = null)
            val durataClipS = (durataClipMs / 1000.0).takeIf { it > 0 } ?: (card.durationS ?: 3.0)
            val esito = recorder.record(
                target = file,
                maxDurationMs = config.maxDurationMs(durataClipS),
                onLevel = { l -> _state.value = _state.value.copy(livelloMicrofono = l) },
            )
            _state.value = _state.value.copy(registrando = false, livelloMicrofono = 0f)

            when (esito) {
                is VoiceRecorder.Result.Saved -> {
                    fileRipetizione = esito.file
                    attemptId = repo.saveAttempt(
                        clipId = card.id,
                        wavPath = esito.file.absolutePath,
                        durationS = esito.durationS,
                    )
                    _state.value = _state.value.copy(
                        peaksRipetizione = repo.waveformOf(esito.file, BUCKETS),
                        durataRipetizione = esito.durationS,
                        passo = Passo.CONFRONTA,
                    )
                }
                VoiceRecorder.Result.NoSpeech -> _state.value = _state.value.copy(
                    messaggio = "Non ho sentito niente. Riprova più vicina al telefono.",
                )
                is VoiceRecorder.Result.Failed -> _state.value = _state.value.copy(
                    messaggio = esito.message,
                )
            }
        }
    }

    fun fermaRegistrazione() = recorder.requestStop()

    private fun beep() {
        runCatching {
            val tono = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tono.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
            viewModelScope.launch {
                delay(BEEP_MS.toLong() + 120)
                tono.release()
            }
        }
    }

    // --- 3. Confronta ---

    /** Suonano in fila la clip e la tua ripetizione (SPEC §5.3 punto 3). */
    fun confronta() {
        val card = _state.value.card ?: return
        val mia = fileRipetizione ?: return
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            _state.value = _state.value.copy(inRiproduzione = true, passo = Passo.CONFRONTA)
            try {
                seguiPosizione()
                player.play(repo.mediaUri(card.clip), 0, null, _state.value.velocita)
                player.silence(PAUSA_MS)
                player.play(mia.toURI().toString(), 0, null, 1f)
            } finally {
                _state.value = _state.value.copy(inRiproduzione = false, posizione = null)
            }
        }
    }

    fun riascoltaRipetizione() {
        val mia = fileRipetizione ?: return
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            _state.value = _state.value.copy(inRiproduzione = true)
            try {
                player.play(mia.toURI().toString(), 0, null, 1f)
            } finally {
                _state.value = _state.value.copy(inRiproduzione = false)
            }
        }
    }

    fun vaiAlVoto() {
        ferma()
        _state.value = _state.value.copy(passo = Passo.VOTO)
    }

    // --- 4. Voto ---

    fun vota(livello: Int) {
        val card = _state.value.card ?: return
        _state.value = _state.value.copy(livello = livello)
        viewModelScope.launch {
            repo.rate(card.id, livello)
            attemptId?.let { repo.setAttemptRating(it, livello) }
        }
    }

    /** "Ancora": stessa clip, si riparte dall'ascolto. */
    fun ancora() {
        ferma()
        _state.value = _state.value.copy(
            passo = Passo.ASCOLTA,
            ascoltiFatti = 0,
            peaksRipetizione = null,
            durataRipetizione = null,
            messaggio = null,
        )
        fileRipetizione = null
        attemptId = null
    }

    /** "Prossima": clip successiva della coda, o fine. */
    fun prossima() {
        ferma()
        if (indice + 1 < coda.size) caricaClip(indice + 1)
        else _state.value = _state.value.copy(finita = true)
    }

    fun segnaAscoltataVaBene() {
        val card = _state.value.card ?: return
        viewModelScope.launch { repo.markListenedOk(card.id) }
    }

    override fun onCleared() {
        lavoro?.cancel()
        recorder.requestStop()
        player.release()
    }

    companion object {
        /** Colonne della forma d'onda: abbastanza per vedere le pause. */
        const val BUCKETS = 220
        const val ASCOLTI = 3
        const val PAUSA_MS = 1_000L
        const val BEEP_MS = 180

        fun factory(container: AppContainer, coda: List<String>) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PracticeViewModel(
                        repo = container.repository,
                        player = container.newPlayer(),
                        recorder = container.recorder,
                        config = container.recordingConfig,
                        coda = coda,
                    ) as T
            }
    }
}
