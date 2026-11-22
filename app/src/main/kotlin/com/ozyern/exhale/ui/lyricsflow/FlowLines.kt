/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Exhale master's bridge from the stored lyrics to the lyric styles' lines and playhead
 * (ExhaleFlowLyrics.kt there), fitted to this build's lyric parser.
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The player's position as the lyric clock wants it: a reading, when it was taken, and a count of
 * the jumps. Shared by every lyric style drawn off that clock.
 */
@Composable
internal fun rememberFlowPosition(player: Player, sliderPositionProvider: () -> Long?): PlaybackPosition {
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
                position.report(player.currentPosition, isPlaying = player.isPlaying)
            }

            // Paused or resumed: the reading at that moment, not up to half a second later.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                position.report(player.currentPosition, isPlaying = player.isPlaying)
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
            position.report(dragged ?: player.currentPosition, isPlaying = dragged == null && player.isPlaying)
            // Ten times a second while it plays: the clock runs the frames in between and is
            // steered by these readings.
            delay(if (dragged != null) 50L else if (player.isPlaying) 100L else 500L)
        }
    }
    return position
}

/** The stored lyric text (LRC, with or without word timing) as the lyric styles' lines. */
internal fun toFlowLines(lyrics: String): List<LyricLine> {
    if (lyrics == LYRICS_NOT_FOUND) return emptyList()
    if (!lyrics.trimStart().startsWith("[")) {
        // Unsynced: every line at 0, shown as a plain, still page.
        return lyrics.lines().filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
    }
    val entries: List<LyricsEntry> = LyricsUtils.parseLyrics(lyrics)
    val timed = entries.filter { it.time >= 0 && !it.isBackground }
    val backing = entries.filter { it.time >= 0 && it.isBackground }

    // Each backing vocal either rides under the lead line it is sung against, or, when it comes
    // after that line has finished (or before the first one), stands as a line of its own, so the
    // panel moves on to it instead of holding the line above. A lead line carries one at most;
    // any more are lines of their own rather than dropped.
    val riding = HashMap<Int, LyricsEntry>()
    val standalone = ArrayList<LyricsEntry>()
    for (back in backing) {
        val owner = timed.indexOfLast { it.time <= back.time }
        val lead = timed.getOrNull(owner)
        if (lead == null || owner in riding) {
            standalone += back
            continue
        }
        val nextMs = timed.getOrNull(owner + 1)?.time
        val leadEnd = voice(lead.words.orEmpty())?.second?.maxOfOrNull { it.endMs }
            ?: estimatedWords(lead.text.trim(), lead.time, nextMs).lastOrNull()?.endMs
            ?: lead.time
        if (back.time < leadEnd + BACKING_JOIN_SLACK_MS) riding[owner] = back else standalone += back
    }
    // Where each line's sweep must have finished: the next line of either kind.
    val starts = (timed.map { it.time } + standalone.map { it.time }).sorted()
    fun nextAfter(ms: Long): Long? = starts.firstOrNull { it > ms }

    val leads = timed.mapIndexed { index, entry ->
        val nextMs = nextAfter(entry.time)
        val lead = voice(entry.words.orEmpty())
        val text = lead?.first ?: entry.text.trim()
        LyricLine(
            timeMs = entry.time,
            text = text,
            words = lead?.second ?: estimatedWords(text, entry.time, nextMs),
            background = riding[index]?.let { back ->
                val voiced = voice(back.words.orEmpty())
                val backText = voiced?.first ?: back.text.trim()
                LyricLine(
                    timeMs = voiced?.second?.firstOrNull()?.startMs ?: back.time,
                    text = backText,
                    words = voiced?.second ?: estimatedWords(backText, back.time, nextMs),
                )
            },
            alignment = if (entry.agent?.lowercase() == "v2") LyricAlignment.End else LyricAlignment.Start,
        )
    }
    val own = standalone.map { back ->
        val nextMs = nextAfter(back.time)
        val voiced = voice(back.words.orEmpty())
        val text = voiced?.first ?: back.text.trim()
        LyricLine(
            timeMs = back.time,
            text = text,
            words = voiced?.second ?: estimatedWords(text, back.time, nextMs),
            alignment = if (back.agent?.lowercase() == "v2") LyricAlignment.End else LyricAlignment.Start,
        )
    }
    return (leads + own).sortedBy { it.timeMs }
}

/** A backing vocal starting this soon after its lead line's last word still belongs to that line. */
private const val BACKING_JOIN_SLACK_MS = 250L

/**
 * Word timings for a line that only has a start time, so it sweeps word by word like a word-timed
 * one instead of lighting all at once. The line's time is shared out by length, and the sweep ends
 * a little before the next line, where singers breathe.
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

private const val BREATH = 0.9
private const val MS_PER_LETTER = 75.0
private const val MS_PER_WORD = 60.0
private const val MIN_LINE_SWEEP_MS = 450.0
private const val LONE_LINE_MS = 4_000L

/** One voice's words, as its line of text and its timed words. */
private fun voice(words: List<com.ozyern.exhale.lyrics.WordTimestamp>): Pair<String, List<LyricWord>>? {
    if (words.none { it.text.isNotBlank() }) return null
    val spaced = words.any { it.text.isBlank() || it.text.first().isWhitespace() || it.text.last().isWhitespace() }
    val text = if (!spaced) words.joinToString(" ") { it.text } else buildString {
        words.forEach { word -> if (word.text == "\n") append(' ') else append(word.text) }
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
