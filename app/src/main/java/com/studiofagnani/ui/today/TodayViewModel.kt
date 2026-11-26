package com.studiofagnani.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiofagnani.AppContainer
import com.studiofagnani.data.ClipCard
import com.studiofagnani.data.ClipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TodayState(
    val caricamento: Boolean = true,
    val ripasso: List<ClipCard> = emptyList(),
    val scaduteTotali: Int = 0,
    val clipConAudio: Int = 0,
    val senzaAudio: Int = 0,
    val maiVotate: Int = 0,
    val daControllare: Int = 0,
) {
    val coda: List<String> get() = ripasso.map { it.id }
}

/**
 * La schermata "Oggi" della v0: il ripasso a intervalli (SPEC §5.4).
 * Riscaldamento, blocco della fase e domanda sulla fatica arrivano nella v1.
 */
class TodayViewModel(private val repo: ClipRepository) : ViewModel() {

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cards.collect { tutte ->
                val oggi = repo.today()
                _state.value = TodayState(
                    caricamento = false,
                    ripasso = repo.reviewToday(tutte, today = oggi),
                    scaduteTotali = tutte.count { it.isDue(oggi) },
                    clipConAudio = tutte.count { !it.hidden },
                    senzaAudio = tutte.count { it.hidden },
                    maiVotate = tutte.count { !it.hidden && it.level == null },
                    daControllare = tutte.count { it.suspect },
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TodayViewModel(container.repository) as T
        }
    }
}
