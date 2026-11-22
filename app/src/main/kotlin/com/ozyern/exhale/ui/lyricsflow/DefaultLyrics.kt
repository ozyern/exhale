/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND

/**
 * Exhale's default lyrics: the full lyric panel, fed from the stored lyrics and the player.
 *
 * Every line sits down the left in big type, the sung one swept word by word and the rest falling
 * out of focus with distance. The panel follows the song from the moment it opens; scrolling by
 * hand reads ahead, and it snaps back a moment after the finger lifts. A tap on a line plays from
 * it. Moving forward through the lines puts the player's controls away and moving back, or a tap
 * on the lower half, brings them in again.
 */
@Composable
fun ExhaleDefaultLyrics(
    sliderPositionProvider: () -> Long?,
    /** The song's own lyric offset, in ms, added to the playhead. */
    offsetMs: Long,
    /** Null when the reader may not move the song (a Listen Together guest). */
    onSeek: ((Long) -> Unit)?,
    controlsOpen: Boolean,
    onRevealControls: () -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
    centerFocus: Boolean = false,
    /** Whether the reader is choosing lines to share; a long press on a line starts it. */
    picking: Boolean = false,
    /** The chosen lines, as indices into [panelLines]' list. */
    picked: Set<Int> = emptySet(),
    onPickLine: (Int) -> Unit = {},
    onTogglePick: (Int) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val trackKey = mediaMetadata?.id ?: return
    val lyrics = currentLyrics?.lyrics

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList() else toFlowLines(lyrics)
    }
    // The panel's clock reads the offset as the reader's correction, the opposite way round.
    LaunchedEffect(offsetMs) { PlayerSettings.lyricsOffsetMs.value = -offsetMs.toInt() }

    val position = rememberFlowPosition(player, sliderPositionProvider)
    LyricsPanel(
        lines = lines,
        trackKey = trackKey,
        playhead = rememberLyricPlayhead(position),
        looking = currentLyrics == null,
        isPlaying = isPlaying,
        onSeekToLine = { lineMs ->
            onSeek?.invoke(adjustedLyricsSeekTarget(lineMs, PlayerSettings.lyricsOffsetMs.value))
        },
        controlsOpen = controlsOpen,
        onRevealControls = onRevealControls,
        onHideControls = onHideControls,
        centerFocus = centerFocus,
        canPick = true,
        picking = picking,
        picked = picked,
        onPickLine = onPickLine,
        onTogglePick = onTogglePick,
        modifier = modifier,
    )
}

/** The lines the default lyrics draw, for the stored [lyrics]: what picked indices refer to. */
fun panelLines(lyrics: String?): List<LyricLine> =
    if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList() else toFlowLines(lyrics)
