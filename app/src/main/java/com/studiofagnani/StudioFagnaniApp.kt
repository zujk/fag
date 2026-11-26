package com.studiofagnani

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.studiofagnani.data.manifest.ManifestImporter

class StudioFagnaniApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _importReport = MutableStateFlow<ManifestImporter.Report?>(null)

    /** Esito dell'ultimo import del manifest: la Libreria lo usa per l'avviso. */
    val importReport: StateFlow<ManifestImporter.Report?> = _importReport.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Import del manifest a ogni avvio: aggiorna i metadati per id,
        // non tocca mai Livelli, storico e registrazioni (SPEC §4.1).
        scope.launch {
            val report = container.repository.importManifest()
            _importReport.value = report
            Log.i(
                TAG,
                "Manifest: ${report.total} clip, ${report.inserted} nuove, " +
                    "${report.updated} aggiornate, ${report.hidden.size} senza audio",
            )
        }
    }

    companion object {
        private const val TAG = "StudioFagnani"
    }
}
