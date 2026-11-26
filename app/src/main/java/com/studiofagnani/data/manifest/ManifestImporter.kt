package com.studiofagnani.data.manifest

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.studiofagnani.data.ClipFiles
import com.studiofagnani.data.db.AppDatabase
import com.studiofagnani.data.db.ORIGIN_BUNDLED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Porta il manifest degli asset nel database (SPEC §4.1). Si puo' richiamare a ogni
 * avvio: aggiorna i metadati per `id` e non tocca mai progressi e registrazioni.
 */
class ManifestImporter(
    private val context: Context,
    private val db: AppDatabase,
    private val files: ClipFiles,
) {

    data class Report(
        val total: Int = 0,
        val inserted: Int = 0,
        val updated: Int = 0,
        val hidden: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val changed: Boolean get() = inserted > 0 || updated > 0
    }

    suspend fun importBundled(
        now: Long = System.currentTimeMillis(),
        today: Long = LocalDate.now().toEpochDay(),
    ): Report = withContext(Dispatchers.IO) {
        val manifest = try {
            readBundledManifest()
        } catch (e: Exception) {
            Log.e(TAG, "Manifest degli asset non leggibile", e)
            return@withContext Report(error = "Manifest delle clip non leggibile: ${e.message}")
        }

        val present = files.bundledFiles()
        val dao = db.clipDao()
        val plan = ManifestMerge.plan(
            manifest = manifest,
            existingClips = dao.getAllClips(),
            existingAnalyses = dao.getAllAnalyses(),
            origin = ORIGIN_BUNDLED,
            audioPresent = { it.file in present },
            now = now,
            today = today,
        )

        db.withTransaction {
            if (plan.toInsert.isNotEmpty()) dao.insertClips(plan.toInsert)
            if (plan.toUpdate.isNotEmpty()) dao.updateClips(plan.toUpdate)
            for (p in plan.newProgress) dao.upsertProgress(p)
            for (a in plan.analyses) dao.upsertAnalysis(a)
        }

        Report(
            total = manifest.clips.size,
            inserted = plan.toInsert.size,
            updated = plan.toUpdate.size,
            hidden = plan.hiddenIds,
            skipped = plan.skipped,
        )
    }

    fun readBundledManifest(): ClipsManifest =
        context.assets.open(ClipFiles.MANIFEST_ASSET).bufferedReader().use {
            manifestJson.decodeFromString<ClipsManifest>(it.readText())
        }

    companion object {
        private const val TAG = "ManifestImporter"
    }
}
