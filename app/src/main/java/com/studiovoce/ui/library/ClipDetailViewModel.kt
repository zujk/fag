package com.studiovoce.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiovoce.AppContainer
import com.studiovoce.audio.ClipPlayer
import com.studiovoce.data.ClipCard
import com.studiovoce.data.ClipRepository
import com.studiovoce.data.db.AttemptEntity
import com.studiovoce.ui.components.Selezione
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClipDetailState(
    val caricamento: Boolean = true,
    val card: ClipCard? = null,
    val peaks: FloatArray? = null,
    val ripetizioni: List<AttemptEntity> = emptyList(),
    val posizione: Float? = null,
    val inRiproduzione: Boolean = false,
    val velocita: Float = 1f,
    val selezione: Selezione? = null,
    val messaggio: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ClipDetailState &&
            caricamento == other.caricamento && card == other.card &&
            peaks === other.peaks && ripetizioni == other.ripetizioni &&
            posizione == other.posizione && inRiproduzione == other.inRiproduzione &&
            velocita == other.velocita && selezione == other.selezione &&
            messaggio == other.messaggio

    override fun hashCode(): Int = listOf(
        caricamento, card, ripetizioni, posizione, inRiproduzione, velocita, selezione, messaggio,
    ).hashCode()
}

class ClipDetailViewModel(
    private val repo: ClipRepository,
    private val player: ClipPlayer,
    private val clipId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ClipDetailState())
    val state: StateFlow<ClipDetailState> = _state.asStateFlow()

    private var lavoro: Job? = null
    private var durataMs: Long = 0

    init {
        viewModelScope.launch {
            repo.cards.collect { tutte ->
                val card = tutte.firstOrNull { it.id == clipId }
                val peaks = _state.value.peaks ?: card?.let { repo.waveform(it.clip, BUCKETS) }
                if (card != null && durataMs == 0L) {
                    durataMs = player.durationMs(repo.mediaUri(card.clip))
                        ?: ((card.durationS ?: 0.0) * 1000).toLong()
                }
                _state.value = _state.value.copy(
                    caricamento = false,
                    card = card,
                    peaks = peaks,
                    messaggio = when {
                        card == null -> "Clip non trovata."
                        card.hidden -> "Questa clip non ha l'audio negli asset."
                        peaks == null -> "Audio non leggibile."
                        else -> null
                    },
                )
            }
        }
        viewModelScope.launch {
            repo.attemptsForClip(clipId).collect {
                _state.value = _state.value.copy(ripetizioni = it)
            }
        }
    }

    fun suona() {
        val card = _state.value.card ?: return
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            val sel = _state.value.selezione?.takeIf { it.valida }
            val da = sel?.let { (it.from * durataMs).toLong() } ?: 0L
            val a = sel?.let { (it.to * durataMs).toLong() }
            _state.value = _state.value.copy(inRiproduzione = true)
            seguiPosizione()
            try {
                player.play(
                    repo.mediaUri(card.clip), da, a, _state.value.velocita,
                    loop = sel != null,
                )
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

    fun suonaRipetizione(attempt: AttemptEntity) {
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            _state.value = _state.value.copy(inRiproduzione = true)
            try {
                player.play(java.io.File(attempt.wavPath).toURI().toString())
            } finally {
                _state.value = _state.value.copy(inRiproduzione = false)
            }
        }
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

    fun setVelocita(v: Float) { _state.value = _state.value.copy(velocita = v) }
    fun setSelezione(s: Selezione?) { _state.value = _state.value.copy(selezione = s) }

    fun vota(livello: Int) {
        viewModelScope.launch { repo.rate(clipId, livello) }
    }

    fun segnaAscoltataVaBene() {
        viewModelScope.launch { repo.markListenedOk(clipId) }
    }

    override fun onCleared() {
        lavoro?.cancel()
        player.release()
    }

    companion object {
        const val BUCKETS = 220

        fun factory(container: AppContainer, clipId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ClipDetailViewModel(container.repository, container.newPlayer(), clipId) as T
        }
    }
}
