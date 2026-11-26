package com.studiovoce.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiovoce.session.SessionPlanner
import com.studiovoce.ui.components.Avviso
import com.studiovoce.ui.theme.Spazi
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Impostazioni (SPEC §7.5): fase corrente, brano fisso, cancellazione delle
 * registrazioni.
 *
 * La fase è la cosa importante di questa schermata: la SPEC §5.1 dice che il
 * passaggio lo conferma sempre Ziv, quindi l'app lo propone e si ferma lì.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    var chiedeConferma by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spazi.m),
        verticalArrangement = Arrangement.spacedBy(Spazi.m),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.displaySmall)

        state.messaggio?.let { testo ->
            Avviso(testo) {
                TextButton(onClick = vm::chiudiMessaggio) { Text("Va bene") }
            }
        }

        // --- fase -----------------------------------------------------------

        Text("Fase del percorso", style = MaterialTheme.typography.titleMedium)
        Text(
            "Siamo alla settimana ${state.settimana}. " +
                "Il passaggio di fase lo decidi tu: l'app lo propone e aspetta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.passaggioDaConfermare) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Column(
                    Modifier.padding(Spazi.m),
                    verticalArrangement = Arrangement.spacedBy(Spazi.s),
                ) {
                    Text(
                        "Per la settimana ${state.settimana} toccherebbe la fase " +
                            SessionPlanner.nomeFase(state.faseSuggerita) + ".",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = vm::accettaSuggerimento) {
                        Text("Passa a questa fase")
                    }
                }
            }
        }

        for (fase in 0..4) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spazi.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.fase == fase, onClick = { vm.setFase(fase) })
                Column(Modifier.padding(start = Spazi.s)) {
                    Text(
                        SessionPlanner.nomeFase(fase),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        descrizioneFase(fase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()

        // --- primo giorno ---------------------------------------------------

        Text("Primo giorno", style = MaterialTheme.typography.titleMedium)
        Text(
            state.primoGiorno?.let {
                "Hai cominciato il " + DateFormat.getDateInstance().format(
                    Date.from(
                        LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ) + "."
            } ?: "Non c'è ancora un primo giorno: lo segna la misura di partenza.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.primoGiorno == null) {
            OutlinedButton(onClick = vm::cominciaOggi) { Text("Comincia da oggi") }
        }

        HorizontalDivider()

        // --- brano fisso ----------------------------------------------------

        Text("Brano fisso", style = MaterialTheme.typography.titleMedium)
        Text(
            "Il testo che rileggi a ogni misura. Sempre lo stesso: è l'unico modo " +
                "per confrontare la voce di oggi con quella di fra un mese.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.branoFisso,
            onValueChange = vm::setBranoFisso,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            placeholder = { Text("Incolla qui qualche riga da leggere.") },
        )

        HorizontalDivider()

        // --- registrazioni ---------------------------------------------------

        Text("Registrazioni", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.registrazioni == 0) {
                "Nessuna ripetizione salvata."
            } else {
                "${state.registrazioni} ripetizioni salvate sul telefono."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.registrazioni > 0) {
            OutlinedButton(onClick = { chiedeConferma = true }) {
                Text("Cancella tutte le registrazioni")
            }
        }

        Text(
            "Backup e ripristino in zip, promemoria e modalità macchina arrivano con " +
                "le versioni successive.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (chiedeConferma) {
        AlertDialog(
            onDismissRequest = { chiedeConferma = false },
            title = { Text("Cancellare le registrazioni?") },
            text = {
                Text(
                    "Si cancellano ${state.registrazioni} file audio delle tue " +
                        "ripetizioni. I Livelli e lo storico restano. Non si torna indietro.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chiedeConferma = false
                        vm.cancellaRegistrazioni()
                    },
                ) { Text("Cancella") }
            },
            dismissButton = {
                TextButton(onClick = { chiedeConferma = false }) { Text("Lascia stare") }
            },
        )
    }
}

/** Una riga per fase, con le parole della SPEC §5.1 e senza gergo. */
private fun descrizioneFase(fase: Int): String = when (fase) {
    0 -> "Tre registrazioni guidate e la scheda bersaglio"
    1 -> "Riscaldamento ed esercizi; le clip solo in ascolto"
    2 -> "Ripetizione di clip corte, una categoria a settimana"
    3 -> "Shadowing sulle clip lunghe e intervista libera"
    else -> "Sessione breve di mantenimento e missioni"
}
