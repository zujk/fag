package com.studiofagnani.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiofagnani.ui.components.Avviso
import com.studiofagnani.ui.components.AvvisoMicrofonoNegato
import com.studiofagnani.ui.components.PermessoMicrofono
import com.studiofagnani.ui.components.rememberPermessoMicrofono
import com.studiofagnani.ui.components.Etichetta
import com.studiofagnani.ui.components.SelettoreLivello
import com.studiofagnani.ui.components.CurvaAltezza
import com.studiofagnani.ui.components.CurveSovrapposte
import com.studiofagnani.ui.components.Riscontri
import com.studiofagnani.ui.components.WaveformConfronto
import com.studiofagnani.ui.components.WaveformView
import com.studiofagnani.ui.theme.Spazi

/**
 * L'esercizio su una clip: ascolta, ripeti, confronta, vota (SPEC §5.3).
 * Nella v0 il confronto è l'ascolto in fila con le due forme d'onda; le curve di
 * altezza allineate con DTW e il riscontro in parole arrivano nella v1.
 */
@Composable
fun PracticeScreen(
    vm: PracticeViewModel,
    onFine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val permesso = rememberPermessoMicrofono(onConcesso = vm::registra)

    LaunchedEffect(state.finita) { if (state.finita) onFine() }

    if (state.caricamento) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val card = state.card
    if (card == null) {
        Column(modifier.fillMaxSize().padding(Spazi.m)) {
            Avviso(state.messaggio ?: "Clip non disponibile.")
            Button(onClick = onFine, Modifier.padding(top = Spazi.m)) { Text("Torna indietro") }
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spazi.m),
        verticalArrangement = Arrangement.spacedBy(Spazi.m),
    ) {
        if (state.clipInCoda > 1) {
            Column {
                Text(
                    "Clip ${state.indiceInCoda + 1} di ${state.clipInCoda}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (state.indiceInCoda + 1f) / state.clipInCoda },
                    modifier = Modifier.fillMaxWidth().padding(top = Spazi.xs),
                )
            }
        }

        Text(
            card.clip.studyNote ?: card.clip.category,
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
            Etichetta(card.clip.category)
            card.clip.context?.let { Etichetta(it) }
            card.clip.energy?.let { Etichetta(it) }
        }

        if (card.suspect) {
            Avviso("Questa clip potrebbe contenere un'altra voce.") {
                TextButton(onClick = vm::segnaAscoltataVaBene) {
                    Text("Ascoltata, va bene")
                }
            }
        }

        // Forma d'onda e curva di altezza sullo stesso riquadro: la curva sta
        // "in sovrimpressione" come chiede la SPEC §5.3, non in un grafico a parte.
        Box {
            WaveformView(
                peaks = state.peaksClip,
                position = state.posizione,
                selezione = state.selezione,
                onSelezione = vm::setSelezione,
            )
            CurvaAltezza(
                curva = state.curvaClip,
                colore = MaterialTheme.colorScheme.tertiary,
                descrizione = "Curva dell'altezza della clip",
            )
        }
        Text(
            if (state.selezione?.valida == true)
                "Pezzo selezionato: suona in loop. Tocca la forma d'onda per annullare."
            else "Trascina sulla forma d'onda per ripetere solo un pezzo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ControlliVelocita(state.velocita, vm::setVelocita)

        HorizontalDivider()

        state.messaggio?.let { Avviso(it) }

        when (state.passo) {
            Passo.ASCOLTA -> PassoAscolta(state, vm)
            Passo.RIPETI -> PassoRipeti(
                state = state,
                permesso = permesso,
                onFermaRegistrazione = vm::fermaRegistrazione,
            )
            Passo.CONFRONTA -> PassoConfronta(state, vm)
            Passo.VOTO -> PassoVoto(state, vm, onFine)
        }
    }
}

@Composable
private fun ControlliVelocita(velocita: Float, onVelocita: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spazi.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Velocità", style = MaterialTheme.typography.labelMedium)
        FilterChip(
            selected = velocita == 0.75f,
            onClick = { onVelocita(0.75f) },
            label = { Text("0,75x") },
        )
        FilterChip(
            selected = velocita == 1f,
            onClick = { onVelocita(1f) },
            label = { Text("1x") },
        )
    }
}

@Composable
private fun PassoAscolta(state: PracticeState, vm: PracticeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.s)) {
        Text("1. Ascolta", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.selezione?.valida == true) "Il pezzo scelto suona in loop."
            else "La clip suona 3 volte. Ascoltata ${state.ascoltiFatti} volte su ${PracticeViewModel.ASCOLTI}.",
            style = MaterialTheme.typography.bodyMedium,
        )
        BottoneGrande(
            testo = if (state.inRiproduzione) "Ferma" else "Ascolta",
            onClick = { if (state.inRiproduzione) vm.ferma() else vm.ascolta() },
        )
        OutlinedButton(
            onClick = vm::vaiARipeti,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Passa a ripetere") }
    }
}

@Composable
private fun PassoRipeti(
    state: PracticeState,
    permesso: PermessoMicrofono,
    onFermaRegistrazione: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.s)) {
        Text("2. Ripeti", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.registrando) "Sto registrando. Si ferma da sola quando smetti di parlare."
            else "Un beep, poi parla. La registrazione si ferma da sola.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!permesso.concesso) {
            Text(
                "Serve il permesso del microfono: lo chiedo al primo tocco.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AvvisoMicrofonoNegato(permesso)
        AnimatedVisibility(state.registrando) {
            LinearProgressIndicator(
                progress = { (state.livelloMicrofono * 8f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp),
            )
        }
        BottoneGrande(
            testo = if (state.registrando) "Ho finito" else "Registra",
            onClick = { if (state.registrando) onFermaRegistrazione() else permesso.chiediEProcedi() },
        )
    }
}

@Composable
private fun PassoConfronta(state: PracticeState, vm: PracticeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.s)) {
        Text("3. Confronta", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sopra la clip, sotto la tua ripetizione" +
                (state.durataRipetizione?.let { " (%.1f s)".format(it) } ?: "") + ".",
            style = MaterialTheme.typography.bodyMedium,
        )
        WaveformConfronto(state.peaksClip, state.peaksRipetizione)

        // Le due curve in semitoni, allineate con DTW: qui si guarda la forma
        // della melodia, non l'altezza, che fra due voci e' sempre diversa.
        CurveSovrapposte(sua = state.curvaClip, tua = state.curvaTua)
        Riscontri(state.riscontri)

        BottoneGrande(
            testo = if (state.inRiproduzione) "Ferma" else "Suona clip e ripetizione",
            onClick = { if (state.inRiproduzione) vm.ferma() else vm.confronta() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
            OutlinedButton(onClick = vm::riascoltaRipetizione, Modifier.weight(1f)) {
                Text("Solo la mia")
            }
            OutlinedButton(onClick = vm::registra, Modifier.weight(1f)) {
                Text("Registra di nuovo")
            }
        }
        Button(onClick = vm::vaiAlVoto, Modifier.fillMaxWidth()) { Text("Vota") }
    }
}

@Composable
private fun PassoVoto(state: PracticeState, vm: PracticeViewModel, onFine: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.s)) {
        Text("Quanto ti è venuta?", style = MaterialTheme.typography.titleMedium)
        SelettoreLivello(state.livello, vm::vota)
        Text(
            when (state.livello) {
                null -> "1 = niente da fare, 5 = uguale a lei."
                1, 2 -> "La rivedi domani."
                3 -> "La rivedi fra 3 giorni."
                4 -> "La rivedi fra una settimana."
                else -> "La rivedi fra due settimane."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
            OutlinedButton(onClick = vm::ancora, Modifier.weight(1f)) { Text("Ancora") }
            Button(
                onClick = { if (state.ultimaDellaCoda) onFine() else vm.prossima() },
                modifier = Modifier.weight(1f),
                enabled = state.livello != null,
            ) {
                Text(if (state.ultimaDellaCoda) "Ho finito" else "Prossima")
            }
        }
    }
}

/** Un tasto grande: si usa in piedi, a volte senza guardare (SPEC §2.1). */
@Composable
private fun BottoneGrande(testo: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(testo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
