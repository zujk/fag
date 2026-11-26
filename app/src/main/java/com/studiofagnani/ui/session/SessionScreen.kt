package com.studiofagnani.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiofagnani.session.SessionPlanner
import com.studiofagnani.ui.components.Avviso
import com.studiofagnani.ui.components.AvvisoMicrofonoNegato
import com.studiofagnani.ui.components.PermessoMicrofono
import com.studiofagnani.ui.components.rememberPermessoMicrofono
import com.studiofagnani.ui.components.StatoVuoto
import com.studiofagnani.ui.theme.Spazi

/**
 * La sessione di oggi, passo per passo con la barra di avanzamento (SPEC §7.2).
 *
 * Le ripetizioni sulle clip non si fanno qui: quando il piano arriva al blocco
 * delle clip si esce verso l'esercizio (SPEC §5.3) con tutta la coda, e al rientro
 * la sessione riprende dal passo dopo.
 */
@Composable
fun SessionScreen(
    vm: SessionViewModel,
    onEsercizioSuClip: (List<String>) -> Unit,
    onFine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Le registrazioni guidate della fase 0 hanno bisogno del microfono, e il
    // permesso va chiesto a runtime: senza, AudioRecord nasce non inizializzato.
    val permesso = rememberPermessoMicrofono(onConcesso = vm::registraGuidata)

    LaunchedEffect(state.finita) {
        if (state.finita) onFine()
    }

    // Il blocco delle clip lo fa la schermata dell'esercizio: si esce una volta
    // sola, con tutta la coda, e la sessione si porta avanti da sé.
    LaunchedEffect(state.indice, state.piano) {
        if (state.passo is SessionPlanner.Passo.EsercizioSuClip) {
            val coda = state.codaClip
            vm.saltaBloccoClip()
            if (coda.isNotEmpty()) onEsercizioSuClip(coda)
        }
    }

    if (state.caricamento) {
        Column(
            modifier.fillMaxSize().padding(Spazi.xl),
            verticalArrangement = Arrangement.spacedBy(Spazi.m),
        ) {
            CircularProgressIndicator()
            Text("Sto preparando la sessione.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val piano = state.piano
    if (piano == null || piano.passi.isEmpty()) {
        StatoVuoto("Niente da fare oggi.", modifier)
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spazi.m),
        verticalArrangement = Arrangement.spacedBy(Spazi.m),
    ) {
        LinearProgressIndicator(
            progress = { state.avanzamento },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Passo ${state.indice + 1} di ${state.totale}" +
                (piano.categoriaDellaSettimana?.let { " · categoria: $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.messaggio?.let { Avviso(it) }

        when (val passo = state.passo) {
            is SessionPlanner.Passo.Riscaldamento ->
                SchedaEsercizio(state, vm, "Riscaldamento")

            is SessionPlanner.Passo.EsercizioDiFase ->
                SchedaEsercizio(state, vm, "Esercizio della fase")

            is SessionPlanner.Passo.AscoltoClip ->
                SchedaAscolto(passo.clipId, vm)

            is SessionPlanner.Passo.RegistrazioneGuidata ->
                SchedaRegistrazione(passo.tipo, state, vm, permesso)

            SessionPlanner.Passo.SchedaBersaglio -> SchedaBersaglio(state, vm)

            SessionPlanner.Passo.IntervistaLibera -> SchedaTesto(
                titolo = "Intervista libera",
                testo = "Registra un pezzo di intervista come se fossi tu a condurla. " +
                    "Serve a mettere insieme quello che hai provato.",
                vm = vm,
            )

            SessionPlanner.Passo.MissioneDiTrasferimento -> SchedaTesto(
                titolo = "Missione di oggi",
                testo = "Porta una domanda alla Fagnani in una conversazione vera, oggi. " +
                    "Una sola, detta come l'hai provata.",
                vm = vm,
            )

            SessionPlanner.Passo.AvvisoVoceAffaticata -> SchedaVoceAffaticata(vm)

            SessionPlanner.Passo.DomandaFatica -> SchedaFatica(vm)

            is SessionPlanner.Passo.EsercizioSuClip, null ->
                Text("Un momento.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SchedaEsercizio(state: SessionState, vm: SessionViewModel, sezione: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text(sezione, style = MaterialTheme.typography.labelLarge)
        Text(state.titolo, style = MaterialTheme.typography.headlineSmall)
        Text(state.istruzioni, style = MaterialTheme.typography.bodyLarge)

        state.secondiRimasti?.let { secondi ->
            Text(
                "%d:%02d".format(secondi / 60, secondi % 60),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
                OutlinedButton(onClick = vm::avviaTimer, Modifier.weight(1f)) { Text("Via") }
                OutlinedButton(onClick = vm::fermaTimer, Modifier.weight(1f)) { Text("Pausa") }
            }
        }

        BottoneAvanti(vm, "Fatto")
    }
}

@Composable
private fun SchedaAscolto(clipId: String, vm: SessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text("Ascolto", style = MaterialTheme.typography.labelLarge)
        Text("Ascolta e basta", style = MaterialTheme.typography.headlineSmall)
        Text(
            "In questa fase le clip si ascoltano, non si ripetono: serve a farsi " +
                "l'orecchio prima di provare.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            clipId,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BottoneAvanti(vm, "Ascoltata")
    }
}

@Composable
private fun SchedaRegistrazione(
    tipo: SessionPlanner.Guidata,
    state: SessionState,
    vm: SessionViewModel,
    permesso: PermessoMicrofono,
) {
    // Si rilegge a ogni cambio di `registrazioniFatte`, che e' nello stato:
    // la mappa dentro il ViewModel da sola non farebbe ricomporre niente.
    val fatta = state.registrazioniFatte > 0 && vm.registrazioneFatta(tipo)
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text("Misura di partenza", style = MaterialTheme.typography.labelLarge)
        Text(state.titolo, style = MaterialTheme.typography.headlineSmall)
        Text(state.istruzioni, style = MaterialTheme.typography.bodyLarge)

        if (state.domanda.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    state.domanda,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(Spazi.m),
                )
            }
        }

        Button(
            onClick = {
                if (state.registrando) vm.fermaRegistrazione() else permesso.chiediEProcedi()
            },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text(
                if (state.registrando) "Ho finito" else "Registra",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        AvvisoMicrofonoNegato(permesso)

        if (state.registrando) {
            Text(
                "Sto registrando.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BottoneAvanti(vm, if (fatta) "Avanti" else "Salta")
        if (!fatta) {
            Text(
                "Si può saltare, ma la scheda bersaglio resterà incompleta.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SchedaBersaglio(state: SessionState, vm: SessionViewModel) {
    LaunchedEffect(Unit) { vm.preparaBersaglio() }

    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text("Scheda bersaglio", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Queste sono le misure della tua voce di oggi. Non c'è un voto: servono " +
                "da riferimento per vedere i cambiamenti nelle settimane.",
            style = MaterialTheme.typography.bodyLarge,
        )

        val m = state.misureBersaglio
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                Modifier.padding(Spazi.m),
                verticalArrangement = Arrangement.spacedBy(Spazi.xs),
            ) {
                if (m == null) {
                    Text(
                        "Nessuna registrazione da misurare.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    RigaMisura("Altezza media", m.f0Med?.let { "%.0f Hz".format(it) })
                    RigaMisura("Più in basso (10%)", m.f0P10?.let { "%.0f Hz".format(it) })
                    RigaMisura("Più in alto (90%)", m.f0P90?.let { "%.0f Hz".format(it) })
                    RigaMisura("Durata", "%.1f s".format(m.durationS))
                    RigaMisura("Parlato", "%.0f%%".format(m.voicedPct))
                }
            }
        }

        Text(
            "Le misure di risonanza (F1 e F2) arrivano con una versione successiva.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BottoneAvanti(vm, "Avanti")
    }
}

@Composable
private fun RigaMisura(etichetta: String, valore: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(etichetta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valore ?: "—", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SchedaTesto(titolo: String, testo: String, vm: SessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text(titolo, style = MaterialTheme.typography.headlineSmall)
        Text(testo, style = MaterialTheme.typography.bodyLarge)
        BottoneAvanti(vm, "Fatto")
    }
}

@Composable
private fun SchedaVoceAffaticata(vm: SessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text("Oggi solo riscaldamento", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Le ultime due volte hai detto che la voce era affaticata, quindi oggi " +
                "la sessione si ferma qui.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Avviso(SessionPlanner.Passo.AvvisoVoceAffaticata.MESSAGGIO)
        BottoneAvanti(vm, "Va bene")
    }
}

/** "Com'è la voce adesso?" (SPEC §5.2, chiusura). */
@Composable
private fun SchedaFatica(vm: SessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.m)) {
        Text("Com'è la voce adesso?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Serve a decidere la sessione di domani, non a darti un voto.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(Spazi.s))

        for (f in SessionPlanner.Fatica.entries) {
            Button(
                onClick = { vm.rispondiFatica(f) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(
                    when (f) {
                        SessionPlanner.Fatica.OK -> "Bene"
                        SessionPlanner.Fatica.STANCA -> "Un po' stanca"
                        SessionPlanner.Fatica.AFFATICATA -> "Affaticata"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun BottoneAvanti(vm: SessionViewModel, testo: String) {
    Button(
        onClick = vm::avanti,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(testo, style = MaterialTheme.typography.titleMedium) }
}
