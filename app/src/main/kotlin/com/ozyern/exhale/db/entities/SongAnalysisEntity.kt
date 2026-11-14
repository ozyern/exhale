/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/** Cached per-track beat, key, and energy analysis. Rows use id lookup because [energyProfile] is a mutable array. */
@Entity(tableName = "song_analysis")
data class SongAnalysisEntity(
    /** Media id, matching `SongEntity.id` / the player's `mediaId`. */
    @PrimaryKey val id: String,
    val bpm: Float,
    /** Absolute track time of one real beat; the grid is this + n * beat. */
    val anchorBeatMs: Float,
    val bpmConfidence: Float,
    /** Pitch class 0 = C .. 11 = B, or -1 when key detection found nothing. */
    val keyIndex: Int = -1,
    val keyIsMinor: Boolean = false,
    val keyConfidence: Float = 0f,
    /** RMS energy per second, stored as one unsigned byte; enough detail to choose transition style and mix-out point. */
    val energyProfile: ByteArray = ByteArray(0),
    val analyzedAt: LocalDateTime = LocalDateTime.now(),
    /** Rows from older analysis versions are ignored and recalculated. */
    val analysisVersion: Int = CURRENT_ANALYSIS_VERSION,
) {
    companion object {
        const val CURRENT_ANALYSIS_VERSION = 1
    }
}
