package com.studiofagnani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studiofagnani.session.Riscontro
import com.studiofagnani.ui.theme.Spazi

/** Etichetta piatta: categoria, contesto, energia. */
@Composable
fun Etichetta(testo: String, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(testo, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
    )
}

@Composable
fun FiltroChip(testo: String, selezionato: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selezionato,
        onClick = onClick,
        label = { Text(testo, style = MaterialTheme.typography.labelMedium) },
    )
}

/**
 * Voto 1-5 ("Quanto ti è venuta?", SPEC §5.3). Nessun punteggio calcolato dall'app:
 * il numero lo mette Ziv.
 */
@Composable
fun SelettoreLivello(
    livello: Int?,
    onLivello: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spazi.s),
    ) {
        for (v in 1..5) {
            OutlinedButton(
                onClick = { onLivello(v) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Voto $v su 5" },
                colors = if (livello == v) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = Spazi.s,
                ),
            ) {
                Text("$v", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Avviso giallo: clip senza audio, microfono negato, errori di import. */
@Composable
fun Avviso(testo: String, modifier: Modifier = Modifier, azione: (@Composable () -> Unit)? = null) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(Spazi.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = "Attenzione", Modifier.size(24.dp))
            Column(Modifier.padding(start = Spazi.m)) {
                Text(testo, style = MaterialTheme.typography.bodyMedium)
                azione?.invoke()
            }
        }
    }
}

/**
 * Il riscontro a parole dopo una ripetizione (SPEC §5.3 punto 4 e §5.5).
 *
 * Al massimo due frasi, nessun punteggio: il numero lo mette Ziv col voto. Le
 * frasi dicono cosa provare la prossima volta, mai com'è andata in generale.
 */
@Composable
fun Riscontri(riscontri: List<Riscontro>, modifier: Modifier = Modifier) {
    if (riscontri.isEmpty()) return
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            Modifier.padding(Spazi.m),
            verticalArrangement = Arrangement.spacedBy(Spazi.s),
        ) {
            for (r in riscontri) {
                Text(r.messaggio, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun StatoVuoto(testo: String, modifier: Modifier = Modifier) {
    Text(
        testo,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(Spazi.xl),
    )
}
