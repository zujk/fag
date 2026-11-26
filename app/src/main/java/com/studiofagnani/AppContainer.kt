package com.studiofagnani

import android.content.Context
import com.studiofagnani.audio.ClipPlayer
import com.studiofagnani.audio.RecordingConfig
import com.studiofagnani.audio.VoiceRecorder
import com.studiofagnani.data.ClipAnalysisRepository
import com.studiofagnani.data.ClipFiles
import com.studiofagnani.data.ClipRepository
import com.studiofagnani.data.SettingsStore
import com.studiofagnani.data.db.AppDatabase
import com.studiofagnani.data.manifest.ExerciseImporter
import com.studiofagnani.data.manifest.ManifestImporter

/**
 * Iniezione delle dipendenze fatta a mano, come chiede SPEC §3 (niente Hilt).
 * Una sola istanza, creata dall'Application e presa dalle schermate.
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val clipFiles: ClipFiles by lazy { ClipFiles(appContext) }

    val recordingConfig = RecordingConfig()

    private val importer: ManifestImporter by lazy {
        ManifestImporter(appContext, database, clipFiles)
    }

    private val exerciseImporter: ExerciseImporter by lazy {
        ExerciseImporter(appContext, database)
    }

    /** Le misure delle clip, calcolate una volta e tenute in cache (SPEC §4.4). */
    val analisi: ClipAnalysisRepository by lazy {
        ClipAnalysisRepository(database, clipFiles)
    }

    /** Fase, primo giorno, brano fisso: quattro valori, in DataStore (SPEC §8). */
    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val repository: ClipRepository by lazy {
        ClipRepository(database, clipFiles, importer, exerciseImporter, analisi)
    }

    /** Il registratore non tiene stato fra una ripetizione e l'altra: uno basta. */
    val recorder: VoiceRecorder by lazy { VoiceRecorder(appContext, recordingConfig) }

    /** Ogni schermata che suona si tiene il suo player e lo rilascia quando esce. */
    fun newPlayer(): ClipPlayer = ClipPlayer(appContext)
}
