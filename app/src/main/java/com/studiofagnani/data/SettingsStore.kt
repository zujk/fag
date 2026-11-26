package com.studiofagnani.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

/**
 * Le impostazioni del percorso (SPEC §7): fase corrente, primo giorno, brano
 * fisso, ordine delle categorie. In DataStore come dice la SPEC §8, non in Room:
 * sono quattro valori, non una tabella.
 *
 * La fase la conferma sempre Ziv (SPEC §5.1): l'app la suggerisce, non la cambia
 * da sola, quindi qui c'e' solo un valore scritto a mano dalle Impostazioni.
 */
class SettingsStore(private val context: Context) {

    data class Impostazioni(
        val fase: Int = FASE_INIZIALE,
        /** Epoch-day del primo giorno di allenamento. `null` = mai cominciato. */
        val primoGiorno: Long? = null,
        /** Il brano che si rilegge a ogni controllo, impostato nella fase 0. */
        val branoFisso: String = "",
        /**
         * Ordine delle categorie della fase 2. Vuoto significa "quello del file
         * `exercises.json`": la preferenza esiste solo se Ziv l'ha cambiato.
         */
        val ordineCategorie: List<String> = emptyList(),
    )

    val flow: Flow<Impostazioni> = context.datastore.data.map { p ->
        Impostazioni(
            fase = p[FASE] ?: FASE_INIZIALE,
            primoGiorno = p[PRIMO_GIORNO],
            branoFisso = p[BRANO_FISSO] ?: "",
            ordineCategorie = decodeOrdine(p[ORDINE_CATEGORIE]),
        )
    }

    suspend fun setFase(fase: Int) {
        context.datastore.edit { it[FASE] = fase.coerceIn(0, 4) }
    }

    suspend fun setPrimoGiorno(epochDay: Long) {
        context.datastore.edit { it[PRIMO_GIORNO] = epochDay }
    }

    suspend fun setBranoFisso(testo: String) {
        context.datastore.edit { it[BRANO_FISSO] = testo }
    }

    suspend fun setOrdineCategorie(ordine: List<String>) {
        context.datastore.edit { it[ORDINE_CATEGORIE] = encodeOrdine(ordine) }
    }

    companion object {

        const val FASE_INIZIALE = 0

        private val FASE = intPreferencesKey("fase")
        private val PRIMO_GIORNO = longPreferencesKey("primo_giorno")
        private val BRANO_FISSO = stringPreferencesKey("brano_fisso")
        private val ORDINE_CATEGORIE = stringPreferencesKey("ordine_categorie")

        /** Separatore a riga nuova: negli id delle categorie non ce ne sono. */
        internal fun encodeOrdine(ordine: List<String>): String =
            ordine.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        internal fun decodeOrdine(grezzo: String?): List<String> =
            grezzo?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}
