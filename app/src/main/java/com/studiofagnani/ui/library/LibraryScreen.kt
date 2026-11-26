package com.studiofagnani.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studiofagnani.data.ClipCard
import com.studiofagnani.ui.components.Avviso
import com.studiofagnani.ui.components.Etichetta
import com.studiofagnani.ui.components.FiltroChip
import com.studiofagnani.ui.components.StatoVuoto
import com.studiofagnani.ui.theme.Spazi

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onApriClip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spazi.m, end = Spazi.m, top = Spazi.s, bottom = Spazi.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spazi.s),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spazi.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.clip.size} clip" +
                        if (state.clip.size != state.totale) " su ${state.totale}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.filtri.attivi > 0) {
                    TextButton(onClick = vm::azzeraFiltri) { Text("Mostra tutte") }
                }
            }
        }

        if (state.senzaAudio > 0) {
            item {
                Avviso(
                    "${state.senzaAudio} clip non hanno l'audio e non compaiono qui. " +
                        "Si aggiungono rigenerando gli asset con build_manifest.py.",
                )
            }
        }

        item { FiltriRiga(state, vm) }

        item { HorizontalDivider(Modifier.padding(vertical = Spazi.s)) }

        if (!state.caricamento && state.clip.isEmpty()) {
            item {
                StatoVuoto(
                    if (state.totale == 0) "Nessuna clip nell'archivio."
                    else "Nessuna clip con questi filtri.",
                )
            }
        }

        items(state.clip, key = { it.id }) { card ->
            ClipRiga(card, onClick = { onApriClip(card.id) })
        }
    }
}

@Composable
private fun FiltriRiga(state: LibraryState, vm: LibraryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spazi.xs)) {
        GruppoFiltri("Stato") {
            for (s in FiltroStato.entries) {
                FiltroChip(s.etichetta, state.filtri.stato == s) {
                    vm.setStato(if (state.filtri.stato == s) FiltroStato.TUTTE else s)
                }
            }
        }
        if (state.categorie.isNotEmpty()) {
            GruppoFiltri("Categoria") {
                for (c in state.categorie) {
                    FiltroChip(c, state.filtri.categoria == c) {
                        vm.setCategoria(if (state.filtri.categoria == c) null else c)
                    }
                }
            }
        }
        if (state.contesti.size > 1) {
            GruppoFiltri("Contesto") {
                for (c in state.contesti) {
                    FiltroChip(c, state.filtri.contesto == c) {
                        vm.setContesto(if (state.filtri.contesto == c) null else c)
                    }
                }
            }
        }
        if (state.energie.size > 1) {
            GruppoFiltri("Energia") {
                for (e in state.energie) {
                    FiltroChip(e, state.filtri.energia == e) {
                        vm.setEnergia(if (state.filtri.energia == e) null else e)
                    }
                }
            }
        }
        GruppoFiltri("Livello") {
            for (l in 1..5) {
                FiltroChip("$l", state.filtri.livello == l) {
                    vm.setLivello(if (state.filtri.livello == l) null else l)
                }
            }
        }
    }
}

@Composable
private fun GruppoFiltri(titolo: String, contenuto: @Composable () -> Unit) {
    Column {
        Text(
            titolo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(Spazi.s)) { contenuto() } }
        }
    }
}

@Composable
private fun ClipRiga(card: ClipCard, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(Spazi.m)) {
            Text(
                card.clip.studyNote ?: card.clip.category,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = Spazi.s),
                horizontalArrangement = Arrangement.spacedBy(Spazi.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Etichetta(card.clip.category)
                card.clip.energy?.let { Etichetta(it) }
                Text(
                    testoLivello(card),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (card.suspect) {
                Text(
                    "Da controllare: potrebbe contenere un'altra voce.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spazi.xs),
                )
            }
        }
    }
}

internal fun testoLivello(card: ClipCard): String {
    val l = card.level
    val durata = card.durationS?.let { " · ${"%.0f".format(it)} s" } ?: ""
    return if (l == null) "Mai votata$durata" else "Livello $l$durata"
}
