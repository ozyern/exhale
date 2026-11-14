/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.delay

/**
 * Stages track changes across artwork, text, details, and ambient layers so their work
 * does not land in one frame. Updates to the same track propagate immediately.
 */
@Stable
class TrackChoreography internal constructor(
    /** The cover melt. */
    val artwork: State<MediaMetadata?>,
    /** Title / artist. */
    val text: State<MediaMetadata?>,
    /** Like glyph and other small per-track chrome. */
    val detail: State<MediaMetadata?>,
    /** Background colours, frost, mesh, canvas video. */
    val ambient: State<MediaMetadata?>,
)

/** Beats between stages (ms after the track change). Touch feedback owns 0..ARTWORK. */
internal const val STAGE_ARTWORK_MS = 90L
internal const val STAGE_TEXT_MS = 200L
internal const val STAGE_DETAIL_MS = 280L
internal const val STAGE_AMBIENT_MS = 360L

@Composable
fun rememberTrackChoreography(metadata: MediaMetadata?): TrackChoreography {
    val artwork = remember { mutableStateOf(metadata) }
    val text = remember { mutableStateOf(metadata) }
    val detail = remember { mutableStateOf(metadata) }
    val ambient = remember { mutableStateOf(metadata) }
    LaunchedEffect(metadata) {
        val sameTrack = ambient.value?.id == metadata?.id
        if (sameTrack || ambient.value == null || metadata == null) {
            artwork.value = metadata
            text.value = metadata
            detail.value = metadata
            ambient.value = metadata
            return@LaunchedEffect
        }
        // Cancelled by the next skip: rapid skips jump every stage straight to the newest
        // track on the next run instead of replaying the ladder for each one in between.
        delay(STAGE_ARTWORK_MS)
        artwork.value = metadata
        delay(STAGE_TEXT_MS - STAGE_ARTWORK_MS)
        text.value = metadata
        delay(STAGE_DETAIL_MS - STAGE_TEXT_MS)
        detail.value = metadata
        delay(STAGE_AMBIENT_MS - STAGE_DETAIL_MS)
        ambient.value = metadata
    }
    return remember { TrackChoreography(artwork, text, detail, ambient) }
}
