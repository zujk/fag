package com.studiovoce.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiovoce.AppContainer
import com.studiovoce.data.ClipCard
import com.studiovoce.data.ClipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filtri della Libreria (SPEC §7.3). Nessun filtro attivo = tutto. */
data class Filtri(
    val categoria: String? = null,
    val contesto: String? = null,
    val energia: String? = null,
    val livello: Int? = null,
    /** "da ripassare oggi", "mai votate", "da controllare" */
    val stato: FiltroStato = FiltroStato.TUTTE,
) {
    val attivi: Int
        get() = listOfNotNull(categoria, contesto, energia, livello).size +
            if (stato != FiltroStato.TUTTE) 1 else 0
}

enum class FiltroStato(val etichetta: String) {
    TUTTE("Tutte"),
    DA_RIPASSARE("Da ripassare oggi"),
    MAI_VOTATE("Mai votate"),
    DA_CONTROLLARE("Da controllare"),
}

data class LibraryState(
    val caricamento: Boolean = true,
    val clip: List<ClipCard> = emptyList(),
    val categorie: List<String> = emptyList(),
    val contesti: List<String> = emptyList(),
    val energie: List<String> = emptyList(),
    val filtri: Filtri = Filtri(),
    val senzaAudio: Int = 0,
    val totale: Int = 0,
)

class LibraryViewModel(private val repo: ClipRepository) : ViewModel() {

    private val filtri = MutableStateFlow(Filtri())
    private val categorieManifest = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<LibraryState> =
        combine(repo.cards, filtri, categorieManifest) { tutte, f, categorie ->
            val oggi = repo.today()
            val visibili = tutte.filterNot { it.hidden }
            LibraryState(
                caricamento = false,
                clip = applica(visibili, f, oggi),
                // L'ordine delle categorie viene dal manifest; in coda quelle che
                // compaiono solo nelle clip (categorie aggiunte da Ziv).
                categorie = (categorie + visibili.map { it.clip.category })
                    .distinct()
                    .filter { cat -> visibili.any { it.clip.category == cat } },
                contesti = visibili.mapNotNull { it.clip.context }.distinct().sorted(),
                energie = visibili.mapNotNull { it.clip.energy }.distinct().sorted(),
                filtri = f,
                senzaAudio = tutte.count { it.hidden },
                totale = visibili.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    init {
        viewModelScope.launch { categorieManifest.value = repo.categories() }
    }

    fun setCategoria(v: String?) { filtri.value = filtri.value.copy(categoria = v) }
    fun setContesto(v: String?) { filtri.value = filtri.value.copy(contesto = v) }
    fun setEnergia(v: String?) { filtri.value = filtri.value.copy(energia = v) }
    fun setLivello(v: Int?) { filtri.value = filtri.value.copy(livello = v) }
    fun setStato(v: FiltroStato) { filtri.value = filtri.value.copy(stato = v) }
    fun azzeraFiltri() { filtri.value = Filtri() }

    private fun applica(clip: List<ClipCard>, f: Filtri, oggi: Long): List<ClipCard> =
        clip.filter { c ->
            (f.categoria == null || c.clip.category == f.categoria) &&
                (f.contesto == null || c.clip.context == f.contesto) &&
                (f.energia == null || c.clip.energy == f.energia) &&
                (f.livello == null || c.level == f.livello) &&
                when (f.stato) {
                    FiltroStato.TUTTE -> true
                    FiltroStato.DA_RIPASSARE -> c.isDue(oggi)
                    FiltroStato.MAI_VOTATE -> c.level == null
                    FiltroStato.DA_CONTROLLARE -> c.suspect
                }
        }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(container.repository) as T
        }
    }
}
