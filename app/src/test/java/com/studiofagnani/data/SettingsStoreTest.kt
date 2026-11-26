package com.studiofagnani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataStore tiene stringhe, non liste: la codifica dell'ordine delle categorie
 * va e torna senza perdere pezzi.
 */
class SettingsStoreTest {

    @Test
    fun `l'ordine delle categorie va e torna`() {
        val ordine = listOf("domanda-secca", "calma-seria", "ironia-sarcasmo")
        val scritto = SettingsStore.encodeOrdine(ordine)

        assertEquals(ordine, SettingsStore.decodeOrdine(scritto))
    }

    @Test
    fun `una preferenza mai scritta da una lista vuota`() {
        assertTrue(SettingsStore.decodeOrdine(null).isEmpty())
        assertTrue(SettingsStore.decodeOrdine("").isEmpty())
        assertTrue(SettingsStore.decodeOrdine("\n\n").isEmpty())
    }

    @Test
    fun `spazi e righe vuote non diventano categorie`() {
        assertEquals(
            listOf("risata", "incalzare"),
            SettingsStore.decodeOrdine(" risata \n\n  \n incalzare"),
        )
        assertEquals("risata\nincalzare", SettingsStore.encodeOrdine(listOf(" risata ", "", "incalzare")))
    }

    @Test
    fun `una lista vuota si scrive come stringa vuota`() {
        assertEquals("", SettingsStore.encodeOrdine(emptyList()))
    }
}
