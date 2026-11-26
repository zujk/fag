package com.studiofagnani.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studiofagnani.AppContainer
import com.studiofagnani.audio.VoiceRecorder
import com.studiofagnani.data.ClipAnalyzer
import com.studiofagnani.data.ClipRepository
import com.studiofagnani.data.SettingsStore
import com.studiofagnani.data.db.FATIGUE_OK
import com.studiofagnani.data.db.FATIGUE_STRAINED
import com.studiofagnani.data.db.FATIGUE_TIRED
import com.studiofagnani.session.SessionPlanner
import com.studiofagnani.session.SoglieSessione
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class SessionState(
    val caricamento: Boolean = true,
    val piano: SessionPlanner.Piano? = null,
    val indice: Int = 0,
    /** Titolo e istruzioni dell'esercizio del passo corrente, se e' un esercizio. */
    val titolo: String = "",
    val istruzioni: String = "",
    /** Secondi che restano al timer dell'esercizio, `null` se non e' partito. */
    val secondiRimasti: Int? = null,
    val domanda: String = "",
    val registrando: Boolean = false,
    val livelloMicrofono: Float = 0f,
    val registrazioniFatte: Int = 0,
    val misureBersaglio: ClipAnalyzer.Misure? = null,
    val messaggio: String? = null,
    val finita: Boolean = false,
) {
    val passo: SessionPlanner.Passo? get() = piano?.passi?.getOrNull(indice)
    val totale: Int get() = piano?.passi?.size ?: 0
    val avanzamento: Float get() = if (totale == 0) 0f else indice.toFloat() / totale

    /** Gli id delle clip del blocco di ripetizione, nell'ordine del piano. */
    val codaClip: List<String>
        get() = piano?.passi.orEmpty().mapNotNull {
            (it as? SessionPlanner.Passo.EsercizioSuClip)?.clipId
        }
}

/**
 * La sessione di oggi (SPEC §5.2): prende il piano da [SessionPlanner] e lo fa
 * scorrere un passo per volta.
 *
 * Il blocco di ripetizione sulle clip non sta qui: si esce verso la schermata
 * dell'esercizio (SPEC §5.3) con tutta la coda, e al rientro la sessione riprende
 * dal passo dopo. Cosi' non ci sono due schermate che fanno la stessa cosa.
 */
class SessionViewModel(
    private val repo: ClipRepository,
    private val settings: SettingsStore,
    private val recorder: VoiceRecorder,
    private val soglie: SoglieSessione = SoglieSessione(),
) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var sessionId: Long? = null
    private var timer: Job? = null
    private var lavoro: Job? = null

    /** Le registrazioni della fase 0, nell'ordine in cui si fanno. */
    private val registrazioni = mutableMapOf<SessionPlanner.Guidata, File>()
    private var domandeLibere: List<String> = emptyList()
    private var domandeFagnani: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val impostazioni = settings.flow.first()
            val oggi = repo.today()
            val settimana = SessionPlanner.settimanaDa(impostazioni.primoGiorno, oggi)

            val fileEsercizi = runCatching { repo.exerciseFile() }.getOrNull()
            domandeLibere = fileEsercizi?.freeTalkQuestions.orEmpty()
            domandeFagnani = fileEsercizi?.fagnaniQuestions.orEmpty()

            val ordine = impostazioni.ordineCategorie.ifEmpty {
                fileEsercizi?.phase2CategoryOrder.orEmpty()
            }

            val piano = SessionPlanner.plan(
                SessionPlanner.Stato(
                    phase = impostazioni.fase,
                    week = settimana,
                    today = oggi,
                    riscaldamento = repo.exercisesForPhase(0).map {
                        SessionPlanner.Esercizio(it.id, it.durationS)
                    },
                    eserciziDiFase = repo.exercisesForPhase(1).map {
                        SessionPlanner.Esercizio(it.id, it.durationS)
                    },
                    clips = repo.cards.first().map { it.toCandidate() },
                    faticaRecente = repo.recentFatigue(soglie.faticaDiFila),
                    ordineCategorie = ordine,
                ),
                soglie = soglie,
            )

            sessionId = repo.startSession(piano.phase)
            _state.value = SessionState(caricamento = false, piano = piano)
            preparaPasso()
        }
    }

    // --- scorrimento dei passi ----------------------------------------------

    fun avanti() {
        timer?.cancel()
        val piano = _state.value.piano ?: return
        val prossimo = _state.value.indice + 1
        if (prossimo >= piano.passi.size) {
            _state.value = _state.value.copy(finita = true)
            return
        }
        _state.value = _state.value.copy(indice = prossimo, secondiRimasti = null, messaggio = null)
        preparaPasso()
    }

    /**
     * Si salta tutto il blocco delle clip in una volta: le ripetizioni le fa la
     * schermata dell'esercizio, e al rientro la sessione e' già oltre.
     */
    fun saltaBloccoClip() {
        val piano = _state.value.piano ?: return
        var i = _state.value.indice
        while (i < piano.passi.size && piano.passi[i] is SessionPlanner.Passo.EsercizioSuClip) i++
        if (i >= piano.passi.size) {
            _state.value = _state.value.copy(finita = true)
        } else {
            _state.value = _state.value.copy(indice = i, secondiRimasti = null)
            preparaPasso()
        }
    }

    /** Titolo, istruzioni e domanda del passo corrente. */
    private fun preparaPasso() {
        val passo = _state.value.passo
        viewModelScope.launch {
            when (passo) {
                is SessionPlanner.Passo.Riscaldamento -> mostraEsercizio(passo.esercizio.id)
                is SessionPlanner.Passo.EsercizioDiFase -> mostraEsercizio(passo.esercizio.id)
                is SessionPlanner.Passo.RegistrazioneGuidata -> mostraGuidata(passo.tipo)
                else -> _state.value = _state.value.copy(titolo = "", istruzioni = "", domanda = "")
            }
        }
    }

    private suspend fun mostraEsercizio(id: String) {
        val e = repo.exercise(id)
        _state.value = _state.value.copy(
            titolo = e?.title ?: "Esercizio",
            istruzioni = e?.instructions ?: "",
            secondiRimasti = e?.durationS,
            domanda = "",
        )
    }

    private fun mostraGuidata(tipo: SessionPlanner.Guidata) {
        val corrente = _state.value
        val domanda = when (tipo) {
            SessionPlanner.Guidata.BRANO_FISSO -> ""
            SessionPlanner.Guidata.MINUTO_LIBERO -> scegli(domandeLibere)
            SessionPlanner.Guidata.DOMANDA_FAGNANI -> scegli(domandeFagnani)
        }
        _state.value = corrente.copy(
            titolo = when (tipo) {
                SessionPlanner.Guidata.BRANO_FISSO -> "Leggi il brano"
                SessionPlanner.Guidata.MINUTO_LIBERO -> "Un minuto libero"
                SessionPlanner.Guidata.DOMANDA_FAGNANI -> "Rispondi alla domanda"
            },
            istruzioni = when (tipo) {
                SessionPlanner.Guidata.BRANO_FISSO ->
                    "Leggi il brano che hai scelto, al tuo passo. Sarà sempre lo stesso: " +
                        "serve a confrontare la voce di oggi con quella di fra un mese."
                SessionPlanner.Guidata.MINUTO_LIBERO ->
                    "Parla per circa un minuto, come viene. Non serve che sia ordinato."
                SessionPlanner.Guidata.DOMANDA_FAGNANI ->
                    "Rispondi come risponderesti a lei: senza prepararti troppo."
            },
            domanda = domanda,
            secondiRimasti = null,
        )
    }

    /** Una domanda a caso dall'elenco (SPEC §5.6). Vuoto se l'elenco non c'è. */
    private fun scegli(elenco: List<String>): String =
        if (elenco.isEmpty()) "" else elenco.random()

    // --- timer degli esercizi -----------------------------------------------

    fun avviaTimer() {
        val secondi = _state.value.secondiRimasti ?: return
        timer?.cancel()
        timer = viewModelScope.launch {
            var restano = secondi
            while (restano > 0) {
                delay(1_000)
                restano--
                _state.value = _state.value.copy(secondiRimasti = restano)
            }
        }
    }

    fun fermaTimer() {
        timer?.cancel()
    }

    // --- registrazioni guidate della fase 0 ---------------------------------

    fun registraGuidata() {
        val passo = _state.value.passo as? SessionPlanner.Passo.RegistrazioneGuidata ?: return
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            val file = repo.newCheckpointFile(passo.tipo.name.lowercase())
            _state.value = _state.value.copy(registrando = true, messaggio = null)
            val esito = recorder.record(
                target = file,
                maxDurationMs = MAX_GUIDATA_MS,
                onLevel = { l -> _state.value = _state.value.copy(livelloMicrofono = l) },
            )
            _state.value = _state.value.copy(registrando = false, livelloMicrofono = 0f)

            when (esito) {
                is VoiceRecorder.Result.Saved -> {
                    registrazioni[passo.tipo] = esito.file
                    _state.value = _state.value.copy(registrazioniFatte = registrazioni.size)
                }
                VoiceRecorder.Result.NoSpeech -> _state.value = _state.value.copy(
                    messaggio = "Non ho sentito niente. Riprova più vicina al telefono.",
                )
                is VoiceRecorder.Result.Failed -> _state.value =
                    _state.value.copy(messaggio = esito.message)
            }
        }
    }

    fun fermaRegistrazione() = recorder.requestStop()

    fun registrazioneFatta(tipo: SessionPlanner.Guidata): Boolean = registrazioni.containsKey(tipo)

    /** Calcola e salva la scheda bersaglio dalle registrazioni appena fatte. */
    fun preparaBersaglio() {
        lavoro?.cancel()
        lavoro = viewModelScope.launch {
            val brano = registrazioni[SessionPlanner.Guidata.BRANO_FISSO]
            val libero = registrazioni[SessionPlanner.Guidata.MINUTO_LIBERO]
            // Le misure di riferimento vengono dal minuto libero: e' il parlato più
            // simile a come Ziv parla di solito. Il brano serve al confronto nel tempo.
            val misure = (libero ?: brano)?.let { repo.misura(it) }

            _state.value = _state.value.copy(misureBersaglio = misure)
            if (brano != null || libero != null) {
                repo.saveCheckpoint(
                    readingWav = brano?.absolutePath,
                    freeWav = libero?.absolutePath,
                    misure = misure,
                )
                settings.setPrimoGiornoSeMancante(repo.today())
            }
        }
    }

    // --- chiusura ------------------------------------------------------------

    /** "Com'è la voce adesso?" (SPEC §5.2). */
    fun rispondiFatica(fatica: SessionPlanner.Fatica, nota: String? = null) {
        val id = sessionId
        viewModelScope.launch {
            if (id != null) {
                repo.endSession(
                    sessionId = id,
                    fatigue = when (fatica) {
                        SessionPlanner.Fatica.OK -> FATIGUE_OK
                        SessionPlanner.Fatica.STANCA -> FATIGUE_TIRED
                        SessionPlanner.Fatica.AFFATICATA -> FATIGUE_STRAINED
                    },
                    note = nota,
                )
            }
            _state.value = _state.value.copy(finita = true)
        }
    }

    override fun onCleared() {
        timer?.cancel()
        lavoro?.cancel()
        recorder.requestStop()
    }

    companion object {
        /** Tetto delle registrazioni guidate: il minuto libero più un margine. */
        const val MAX_GUIDATA_MS = 90_000L

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SessionViewModel(
                    repo = container.repository,
                    settings = container.settings,
                    recorder = container.recorder,
                ) as T
        }
    }
}
