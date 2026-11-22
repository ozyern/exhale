/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND

/**
 * The line being sung, one row directly above the scrubber, swept word by word on the same clock
 * as the lyrics page. The next line takes its place as it starts; a tap opens the lyrics. Nothing
 * at all for a song with no lyrics, so the scrubber keeps its place.
 */
@Composable
fun CurrentLyricStrip(
    sliderPositionProvider: () -> Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val trackKey = mediaMetadata?.id ?: return
    val lyrics = currentLyrics?.lyrics
    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList() else toFlowLines(lyrics)
    }
    if (lines.none { !it.isGap }) return
    val position = rememberFlowPosition(playerConnection.player, sliderPositionProvider)
    Box(modifier) {
        CurrentLyricStrip(
            lines = lines,
            trackKey = trackKey,
            playhead = rememberLyricPlayhead(position),
            isPlaying = isPlaying,
            durationMs = (mediaMetadata?.duration ?: 0) * 1000L,
            lyricsUnavailable = false,
            loadingText = "",
            onClick = onClick,
        )
    }
}
