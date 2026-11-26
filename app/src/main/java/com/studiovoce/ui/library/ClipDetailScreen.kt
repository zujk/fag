package com.studiovoce.ui.library

import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiovoce.ui.components.Avviso
import com.studiovoce.ui.components.Etichetta
import com.studiovoce.ui.components.SelettoreLivello
import com.studiovoce.ui.components.WaveformView
import com.studiovoce.ui.theme.Spazi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scheda della clip (SPEC §7.3). */
@Composable
fun ClipDetailScreen(
    vm: ClipDetailViewModel,
    onEsercitati: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.caricamento) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val card = state.card ?: run {
        Column(modifier.padding(Spazi.m)) { Avviso(state.messaggio ?: "Clip non trovata.") }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spazi.m),
        verticalArrangement = Arrangement.spacedBy(Spazi.m),
    ) {
        Text(
            card.clip.studyNote ?: card.clip.category,
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
            Etichetta(card.clip.category)
            card.clip.context?.let { Etichetta(it) }
            card.clip.energy?.let { Etichetta(it) }
        }

        state.messaggio?.let { Avviso(it) }

        if (card.suspect) {
            Avviso("Esito del controllo: ${card.clip.checkStatus}. Esclusa dal ripasso.") {
                TextButton(onClick = vm::segnaAscoltataVaBene) { Text("Ascoltata, va bene") }
            }
        }

        WaveformView(
            peaks = state.peaks,
            position = state.posizione,
            selezione = state.selezione,
            onSelezione = vm::setSelezione,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spazi.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { if (state.inRiproduzione) vm.ferma() else vm.suona() },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = !card.hidden,
            ) {
                Text(if (state.inRiproduzione) "Ferma" else "Ascolta")
            }
            FilterChip(
                selected = state.velocita == 0.75f,
                onClick = { vm.setVelocita(if (state.velocita == 0.75f) 1f else 0.75f) },
                label = { Text("0,75x") },
            )
        }

        OutlinedButton(
            onClick = { onEsercitati(card.id) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !card.hidden,
        ) { Text("Esercitati ora") }

        HorizontalDivider()

        Text("Livello", style = MaterialTheme.typography.titleMedium)
        SelettoreLivello(card.level, vm::vota)
        Text(
            testoScadenza(card.dueAt, state.card?.repsTotal ?: 0),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        card.clip.sourceUrl?.let { url ->
            HorizontalDivider()
            Text("Fonte", style = MaterialTheme.typography.titleMedium)
            Text(
                card.clip.sourceLabel?.takeIf { it.isNotBlank() } ?: url,
                style = MaterialTheme.typography.bodyMedium,
            )
            val tempo = card.clip.startS?.let { s ->
                "da ${"%.1f".format(s)} s" + (card.clip.endS?.let { e -> " a ${"%.1f".format(e)} s" } ?: "")
            }
            tempo?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }) { Text("Apri nel browser") }
        }

        card.clip.verification?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Verifica: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        Text("Le tue ripetizioni", style = MaterialTheme.typography.titleMedium)
        if (state.ripetizioni.isEmpty()) {
            Text(
                "Nessuna ripetizione registrata.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (a in state.ripetizioni) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spazi.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(formato.format(Date(a.createdAt)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%.1f s".format(a.durationS) +
                                (a.selfRating?.let { " · voto $it" } ?: "") +
                                etichettaContesto(a.context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.suonaRipetizione(a) }) { Text("Ascolta") }
                }
            }
        }
    }
}

private val formato = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN)

private fun etichettaContesto(contesto: String) = when (contesto) {
    "car" -> " · dalla macchina"
    "train" -> " · dal treno"
    else -> ""
}

private fun testoScadenza(dueAt: Long?, reps: Int): String {
    val oggi = java.time.LocalDate.now().toEpochDay()
    val quando = when {
        dueAt == null -> "non ancora in programma"
        dueAt <= oggi -> "da ripassare oggi"
        dueAt == oggi + 1 -> "torna domani"
        else -> "torna fra ${dueAt - oggi} giorni"
    }
    return "Ripasso: $quando · ripetizioni registrate: $reps"
}
