package com.studiofagnani.ui.today

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiofagnani.ui.components.Avviso
import com.studiofagnani.ui.theme.Spazi
import java.text.DateFormat
import java.util.Date

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onIniziaSessione: () -> Unit,
    onIniziaRipasso: (List<String>) -> Unit,
    onVaiInLibreria: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spazi.m),
        verticalArrangement = Arrangement.spacedBy(Spazi.m),
    ) {
        Text("Oggi", style = MaterialTheme.typography.displaySmall)

        if (!state.caricamento) {
            Text(
                buildString {
                    append(if (state.fase == 0) "Misura di partenza" else "Fase ${state.fase}")
                    append(" · settimana ${state.settimana}")
                    state.categoriaDellaSettimana?.let { append(" · categoria: $it") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            when {
                state.caricamento -> "Sto caricando le clip."
                state.ripasso.isEmpty() && state.clipConAudio == 0 ->
                    "Nessuna clip con audio nell'archivio."
                state.ripasso.isEmpty() ->
                    "Niente da ripassare oggi. Torna domani, o scegli una clip dalla Libreria."
                else ->
                    "${state.ripasso.size} clip da ripassare" +
                        if (state.scaduteTotali > state.ripasso.size)
                            " (${state.scaduteTotali} in attesa in tutto)" else ""
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        // Un tasto grande e uno solo, come chiede SPEC §2.1: l'app decide cosa
        // fare in base a fase, giorno e ripasso.
        Button(
            onClick = onIniziaSessione,
            enabled = !state.caricamento,
            modifier = Modifier.fillMaxWidth().height(88.dp),
        ) {
            Text(
                "Inizia la sessione di oggi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }

        if (state.ripasso.isNotEmpty()) {
            OutlinedButton(
                onClick = { onIniziaRipasso(state.coda) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Solo il ripasso (${state.ripasso.size} clip)") }
        }

        OutlinedButton(
            onClick = onVaiInLibreria,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Scegli una clip dalla Libreria") }

        if (state.senzaAudio > 0) {
            Avviso(
                "${state.senzaAudio} clip del manifest non hanno l'audio negli asset: " +
                    "restano nascoste finché non si rigenerano con build_manifest.py.",
            )
        }

        Spacer(Modifier.height(Spazi.s))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(Spazi.m), verticalArrangement = Arrangement.spacedBy(Spazi.xs)) {
                Text("Archivio", style = MaterialTheme.typography.titleMedium)
                Riga("Clip pronte", "${state.clipConAudio}")
                Riga("Mai votate", "${state.maiVotate}")
                if (state.daControllare > 0) Riga("Da controllare", "${state.daControllare}")
            }
        }

        Text(
            state.ultimoControllo?.let {
                "Ultima misura: " + DateFormat.getDateInstance().format(Date(it))
            } ?: "Nessuna misura di partenza: la fa la sessione della fase 0.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Riga(etichetta: String, valore: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etichetta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valore, style = MaterialTheme.typography.titleMedium)
    }
}
