package com.studiovoce.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiovoce.AppContainer
import com.studiovoce.data.ClipCard
import com.studiovoce.data.ClipRepository
import com.studiovoce.data.SettingsStore
import com.studiovoce.session.SessionPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TodayState(
    val caricamento: Boolean = true,
    val fase: Int = 0,
    val settimana: Int = 1,
    val categoriaDellaSettimana: String? = null,
    val ultimoControllo: Long? = null,
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
 * La schermata "Oggi" (SPEC §7.1): un tasto grande per la sessione, e sotto fase,
 * settimana, categoria della settimana e clip in ripasso.
 */
class TodayViewModel(
    private val repo: ClipRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cards.collect { tutte ->
                val oggi = repo.today()
                val impostazioni = settings.flow.first()
                val settimana = SessionPlanner.settimanaDa(impostazioni.primoGiorno, oggi)
                val ordine = impostazioni.ordineCategorie.ifEmpty {
                    runCatching { repo.phase2CategoryOrder() }.getOrDefault(emptyList())
                }
                // La categoria della settimana la decide il pianificatore: qui si
                // mostra, non si ricalcola, cosi' schermata e sessione non divergono.
                val piano = SessionPlanner.plan(
                    SessionPlanner.Stato(
                        phase = impostazioni.fase,
                        week = settimana,
                        today = oggi,
                        ordineCategorie = ordine,
                    ),
                )

                _state.value = TodayState(
                    caricamento = false,
                    fase = piano.phase,
                    settimana = settimana,
                    categoriaDellaSettimana = piano.categoriaDellaSettimana,
                    ultimoControllo = repo.lastCheckpoint()?.createdAt,
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
                TodayViewModel(container.repository, container.settings) as T
        }
    }
}
