/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
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
