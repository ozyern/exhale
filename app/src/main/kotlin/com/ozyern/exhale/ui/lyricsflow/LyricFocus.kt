/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.lyricsflow


/** Keep every unfinished vocal visible, including overlaps spanning more than two rows. */
internal fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest || (!line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs)
    }
}

/** The last line whose stamp has passed at [positionMs], or -1 before the first. Allocates nothing. */
internal fun latestLyricRow(lines: List<LyricLine>, positionMs: Long): Int {
    var index = lines.size - 1
    while (index >= 0) {
        if (lines[index].timeMs <= positionMs) return index
        index--
    }
    return -1
}

/**
 * [activeLyricRows] for a clock that is asked on every frame.
 *
 * The answer changes a few times a line and was worked out sixty times a second, each time as a
 * new list filtered out of a boxed range of every line so far: a few hundred short-lived objects a
 * second for the whole of playback, and the collector's pauses landed in the middle of a sweep.
 * Here the rows are counted into an array kept between frames, and the list handed back is the
 * same one until what it says changes.
 */
internal class ActiveLyricRows(private val lines: List<LyricLine>) {
    private var rows = IntArray(4)
    private var count = -1
    private var current: List<Int> = emptyList()

    fun at(positionMs: Long): List<Int> {
        val latest = latestLyricRow(lines, positionMs)
        var found = 0
        var same = count >= 0
        for (index in 0..latest) {
            val line = lines[index]
            val active = index == latest || (!line.isGap &&
                (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
                line.timeMs <= positionMs && positionMs < line.endMs)
            if (!active) continue
            if (found == rows.size) rows = rows.copyOf(found * 2)
            if (same && (found >= count || rows[found] != index)) same = false
            rows[found++] = index
        }
        if (same && found == count) return current
        count = found
        current = if (found == 0) emptyList() else List(found) { rows[it] }
        return current
    }
}
