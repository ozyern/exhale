/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * the reference design's lyric panel, playing Exhale's lyrics: the same lyrics Exhale already fetched, turned
 * into the reference design's lines, and the player's position handed to the reference design's own lyric clock.
 */
@Composable
fun ExhaleFlowLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = currentLyrics?.lyrics
    val trackKey = mediaMetadata?.id ?: return

    val lines = remember(lyrics) { lyrics?.let(::toFlowLines).orEmpty() }

    // the reference design's playhead: a reading of the player, when it was taken, and a count of the jumps.
    val position = remember { PlaybackPosition() }
    val slider by rememberUpdatedState(sliderPositionProvider)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                position.seeks++
                position.report(player.currentPosition)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        var lastSlider: Long? = null
        while (isActive) {
            val dragged = slider()
            if (dragged != null && dragged != lastSlider) position.seeks++
            lastSlider = dragged
            position.report(dragged ?: player.currentPosition)
            // Twice a second while it plays, as the reference design's player reports: its clock runs the
            // frames in between and is steered by these readings.
            delay(if (dragged != null) 50L else if (player.isPlaying) 250L else 500L)
        }
    }

    LyricsPanel(
        lines = lines,
        trackKey = trackKey,
        playhead = rememberLyricPlayhead(position),
        looking = lyrics == null,
        isPlaying = isPlaying,
        onSeekToLine = { player.seekTo(it) },
        controlsOpen = true,
        onRevealControls = {},
        onHideControls = {},
        modifier = modifier.padding(horizontal = PLAYER_GUTTER),
    )
}

/** Exhale's lyric text, LRC or TTML, as the reference design's lines. */
private fun toFlowLines(lyrics: String): List<LyricLine> {
    if (lyrics == LYRICS_NOT_FOUND) return emptyList()
    val entries: List<LyricsEntry> = when {
        LyricsUtils.isTtml(lyrics) -> LyricsUtils.parseTtml(lyrics)
        lyrics.startsWith("[") -> LyricsUtils.parseLyrics(lyrics)
        // Unsynced: every line at 0, which the reference design shows as a plain, still page.
        else -> return lyrics.lines().filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
    }
    return entries.filter { it.time >= 0 }.map { entry ->
        val all = entry.words.orEmpty()
        val lead = voice(all.filter { !it.isBackground })
        val backing = voice(all.filter { it.isBackground })
        LyricLine(
            timeMs = entry.time,
            text = lead?.first ?: entry.text.trim(),
            words = lead?.second.orEmpty(),
            background = backing?.let { (text, words) ->
                LyricLine(timeMs = words.firstOrNull()?.startMs ?: entry.time, text = text, words = words)
            },
            alignment = if (entry.agent?.lowercase() == "v2") LyricAlignment.End else LyricAlignment.Start,
        )
    }
}

/**
 * One voice's words, as its line of text and its timed words. Exhale keeps the spaces between
 * words as entries of their own; the reference design finds each word in the text, so the text is rebuilt
 * from the pieces in order and the words carry only their letters.
 */
private fun voice(words: List<com.ozyern.exhale.lyrics.WordTimestamp>): Pair<String, List<LyricWord>>? {
    if (words.none { it.text.isNotBlank() }) return null
    // Some sources keep no spaces at all between the words; those are joined with one.
    val spaced = words.any { it.text.isBlank() || it.text.first().isWhitespace() || it.text.last().isWhitespace() }
    val text = if (!spaced) words.joinToString(" ") { it.text } else buildString {
        words.forEach { word ->
            when {
                word.text == "\n" -> append(' ')
                else -> append(word.text)
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
    val timed = words.filter { it.text.isNotBlank() }.map {
        LyricWord(
            startMs = (it.startTime * 1000).toLong(),
            endMs = (it.endTime * 1000).toLong(),
            text = it.text.trim(),
        )
    }
    return text to timed
}
