package com.studiovoce.data

import com.studiovoce.data.db.ClipAnalysisEntity
import com.studiovoce.data.db.ClipEntity
import com.studiovoce.data.db.ClipProgressEntity
import com.studiovoce.data.manifest.isSuspect
import com.studiovoce.session.SpacedRepetition

/** Una clip con il suo stato, nella forma che serve alla Libreria e alla sessione. */
data class ClipCard(
    val clip: ClipEntity,
    val progress: ClipProgressEntity? = null,
    val analysis: ClipAnalysisEntity? = null,
) {
    val id: String get() = clip.id
    val level: Int? get() = progress?.level
    val dueAt: Long? get() = progress?.dueAt
    val repsTotal: Int get() = progress?.repsTotal ?: 0
    val lastPracticedAt: Long? get() = progress?.lastPracticedAt
    val hidden: Boolean get() = clip.hidden
    val suspect: Boolean get() = clip.isSuspect()

    /** Durata nota: dal manifest (end - start) o dalle misure calcolate. */
    val durationS: Double?
        get() = analysis?.durationS
            ?: clip.endS?.let { end -> clip.startS?.let { start -> (end - start).takeIf { it > 0 } } }

    fun isDue(today: Long): Boolean = SpacedRepetition.isDue(toCandidate(), today)

    fun toCandidate() = SpacedRepetition.Candidate(
        clipId = clip.id,
        categoryId = clip.categoryId,
        level = level,
        dueAt = dueAt,
        hidden = hidden,
        suspect = suspect,
        lastPracticedAt = lastPracticedAt,
    )
}
