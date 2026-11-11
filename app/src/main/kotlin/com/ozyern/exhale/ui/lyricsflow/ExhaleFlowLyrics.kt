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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    /** A small second line under each lyric (translation or pronunciation), index for index; null for none. */
    subTexts: List<String?>? = null,
    picking: Boolean = false,
    picked: Set<Int> = emptySet(),
    /** Long-press on a line; null leaves line picking off. */
    onPickLine: ((Int) -> Unit)? = null,
    onTogglePick: (Int) -> Unit = {},
    /** Room kept clear at the bottom for controls drawn over the panel. */
    bottomInset: Dp = 0.dp,
    /** Multiple of the panel's own type size. */
    textScale: Float = 1f,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = currentLyrics?.lyrics
    val trackKey = mediaMetadata?.id ?: return

    val lines = remember(lyrics) { lyrics?.let(::toFlowLines).orEmpty() }

    val position = rememberFlowPosition(player, sliderPositionProvider)

    // A line with no translation yet carries its own text, which the panel recognises and skips.
    val subLines = remember(lines, subTexts) {
        subTexts?.let { texts ->
            lines.mapIndexed { i, line -> LyricLine(timeMs = line.timeMs, text = texts.getOrNull(i) ?: line.text) }
        }
    }

    LyricsPanel(
        lines = lines,
        subLines = subLines,
        canPick = onPickLine != null,
        picking = picking,
        picked = picked,
        onPickLine = onPickLine ?: {},
        onTogglePick = onTogglePick,
        returnButtonInset = bottomInset,
        textScale = textScale,
        trackKey = trackKey,
        playhead = rememberLyricPlayhead(position),
        looking = lyrics == null,
        isPlaying = isPlaying,
        onSeekToLine = { player.seekTo(it) },
        controlsOpen = true,
        onRevealControls = {},
        onHideControls = {},
        // Exhale's lyric hosts already sit inside a margin of their own (12-16dp), so the panel
        // takes 16dp more rather than its full 30dp gutter: the words then start ~32dp from the
        // screen's edge, where Apple's do.
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/**
 * The player's position as the lyric clock wants it: a reading, when it was taken, and a count of
 * the jumps. Shared by every lyric style drawn off that clock.
 */
@Composable
internal fun rememberFlowPosition(player: Player, sliderPositionProvider: () -> Long?): PlaybackPosition {
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

            // Paused or resumed: the reading at that moment, not up to half a second later on the
            // paused loop's clock — the words used to set off late after every resume.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                position.report(player.currentPosition)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        var lastSlider: Long? = null
        while (isActive) {
            // Asleep with the app: these readings only steer what is drawn.
            com.ozyern.exhale.utils.awaitAppVisible()
            val dragged = slider()
            if (dragged != null && dragged != lastSlider) position.seeks++
            lastSlider = dragged
            position.report(dragged ?: player.currentPosition)
            // Ten times a second while it plays: the clock runs the frames in between and is steered
            // by these readings. At four a second it heard late about the audio output's own clock
            // settling in the first moments of a song (its position is coarse until the output's
            // timestamps arrive), and the words trailed the voice through the opening lines. A
            // reading is one Long written to state that only the draw phase reads.
            delay(if (dragged != null) 50L else if (player.isPlaying) 100L else 500L)
        }
    }
    return position
}

/** Exhale's lyric text, LRC or TTML, as the reference design's lines. */
internal fun toFlowLines(lyrics: String): List<LyricLine> {
    if (lyrics == LYRICS_NOT_FOUND) return emptyList()
    val entries: List<LyricsEntry> = when {
        LyricsUtils.isTtml(lyrics) -> LyricsUtils.parseTtml(lyrics)
        lyrics.startsWith("[") -> LyricsUtils.parseLyrics(lyrics)
        // Unsynced: every line at 0, which the reference design shows as a plain, still page.
        else -> return lyrics.lines().filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
    }
    val timed = entries.filter { it.time >= 0 }
    return timed.mapIndexed { index, entry ->
        val nextMs = timed.getOrNull(index + 1)?.time
        val all = entry.words.orEmpty()
        val lead = voice(all.filter { !it.isBackground })
        val backing = voice(all.filter { it.isBackground })
        val text = lead?.first ?: entry.text.trim()
        LyricLine(
            timeMs = entry.time,
            text = text,
            words = lead?.second ?: estimatedWords(text, entry.time, nextMs),
            background = backing?.let { (text, words) ->
                LyricLine(timeMs = words.firstOrNull()?.startMs ?: entry.time, text = text, words = words)
            },
            alignment = if (entry.agent?.lowercase() == "v2") LyricAlignment.End else LyricAlignment.Start,
        )
    }
}

/**
 * Word timings for a line that only has a start time, so it sweeps word by word like a word-timed
 * one instead of lighting all at once. A sung word takes roughly as long as it has letters, so the
 * line's time is shared out by length (plus a beat per word for the consonants at its edges), and
 * the sweep ends a little before the next line, where singers breathe.
 *
 * An estimate: a held note finishes its sweep early. Lyrics with real word timing never come here.
 */
private fun estimatedWords(text: String, startMs: Long, nextMs: Long?): List<LyricWord> {
    val tokens = text.split(' ').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val letters = tokens.sumOf { it.length }
    val gap = (nextMs ?: (startMs + LONE_LINE_MS)) - startMs
    val span = minOf(gap * BREATH, letters * MS_PER_LETTER + tokens.size * MS_PER_WORD)
        .coerceAtLeast(MIN_LINE_SWEEP_MS)
        .coerceAtMost(gap.toDouble().coerceAtLeast(1.0))
    val perUnit = span / (letters + tokens.size)
    var at = startMs.toDouble()
    return tokens.map { token ->
        val length = (token.length + 1) * perUnit
        LyricWord(startMs = at.toLong(), endMs = (at + length).toLong(), text = token).also { at += length }
    }
}

/** How much of the gap to the next line a sweep may take: the rest is the breath before it. */
private const val BREATH = 0.9

/** About how long a letter takes to sing, and the extra beat each word adds. */
private const val MS_PER_LETTER = 75.0
private const val MS_PER_WORD = 60.0

/** No sweep faster than this, however short the line. */
private const val MIN_LINE_SWEEP_MS = 450.0

/** The last line has no next one to end at: given this long. */
private const val LONE_LINE_MS = 4_000L

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
