package com.studiovoce.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiovoce.AppContainer
import com.studiovoce.data.ClipRepository
import com.studiovoce.data.SettingsStore
import com.studiovoce.session.SessionPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val caricamento: Boolean = true,
    val fase: Int = 0,
    val settimana: Int = 1,
    val faseSuggerita: Int = 0,
    val primoGiorno: Long? = null,
    val branoFisso: String = "",
    val registrazioni: Int = 0,
    val messaggio: String? = null,
) {
    /** Vero quando l'app propone un passaggio di fase che Ziv non ha ancora fatto. */
    val passaggioDaConfermare: Boolean get() = faseSuggerita > fase
}

/**
 * Le Impostazioni (SPEC §7.5), nella parte che serve alla v1: fase corrente,
 * brano fisso, cancellazione delle registrazioni.
 *
 * La fase la cambia solo Ziv (SPEC §5.1): l'app la suggerisce in base alle
 * settimane e si ferma lì. Orari dei promemoria, dispositivo Bluetooth e backup
 * zip arrivano con le milestone che li usano.
 */
class SettingsViewModel(
    private val repo: ClipRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.flow.collect { impostazioni ->
                val settimana = SessionPlanner.settimanaDa(impostazioni.primoGiorno, repo.today())
                _state.value = _state.value.copy(
                    caricamento = false,
                    fase = impostazioni.fase,
                    settimana = settimana,
                    faseSuggerita = SessionPlanner.faseSuggerita(settimana),
                    primoGiorno = impostazioni.primoGiorno,
                    branoFisso = impostazioni.branoFisso,
                )
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(registrazioni = repo.countRecordings())
        }
    }

    fun setFase(fase: Int) {
        viewModelScope.launch {
            settings.setFase(fase)
            _state.value = _state.value.copy(
                messaggio = "Fase impostata su ${SessionPlanner.nomeFase(fase)}.",
            )
        }
    }

    fun accettaSuggerimento() = setFase(_state.value.faseSuggerita)

    fun setBranoFisso(testo: String) {
        // Si scrive subito: è un campo di testo, e un tasto "salva" in più su una
        // schermata che si apre una volta al mese è solo un tasto da ricordarsi.
        _state.value = _state.value.copy(branoFisso = testo)
        viewModelScope.launch { settings.setBranoFisso(testo) }
    }

    /** Segna oggi come primo giorno, per chi comincia senza passare dalla fase 0. */
    fun cominciaOggi() {
        viewModelScope.launch {
            settings.setPrimoGiorno(repo.today())
            _state.value = _state.value.copy(messaggio = "Primo giorno segnato a oggi.")
        }
    }

    fun cancellaRegistrazioni() {
        viewModelScope.launch {
            val quante = repo.deleteAllRecordings()
            _state.value = _state.value.copy(
                registrazioni = repo.countRecordings(),
                messaggio = if (quante == 0) {
                    "Non c'era niente da cancellare."
                } else {
                    "Cancellate $quante registrazioni."
                },
            )
        }
    }

    fun chiudiMessaggio() {
        _state.value = _state.value.copy(messaggio = null)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(container.repository, container.settings) as T
        }
    }
}
