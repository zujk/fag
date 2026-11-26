package com.studiofagnani.data.manifest

import android.content.Context
import android.util.Log
import com.studiofagnani.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Porta `assets/exercises.json` nella tabella `exercise` (SPEC §8).
 * Si puo' richiamare a ogni avvio: gli esercizi non hanno progressi attaccati.
 */
class ExerciseImporter(
    private val context: Context,
    private val db: AppDatabase,
) {

    data class Report(
        val imported: Int = 0,
        val skipped: List<String> = emptyList(),
        val categoryOrder: List<String> = emptyList(),
        val error: String? = null,
    )

    suspend fun importBundled(): Report = withContext(Dispatchers.IO) {
        val file = try {
            read()
        } catch (e: Exception) {
            Log.e(TAG, "exercises.json non leggibile", e)
            return@withContext Report(error = "Elenco degli esercizi non leggibile: ${e.message}")
        }

        val plan = ExerciseMerge.plan(file)
        if (plan.toUpsert.isNotEmpty()) db.exerciseDao().upsertAll(plan.toUpsert)
        if (plan.skipped.isNotEmpty()) Log.w(TAG, "esercizi scartati: ${plan.skipped}")

        Report(
            imported = plan.toUpsert.size,
            skipped = plan.skipped,
            categoryOrder = plan.categoryOrder,
        )
    }

    fun read(): ExercisesFile =
        context.assets.open(ASSET).bufferedReader().use {
            manifestJson.decodeFromString<ExercisesFile>(it.readText())
        }

    companion object {
        const val ASSET = "exercises.json"
        private const val TAG = "ExerciseImporter"
    }
}
