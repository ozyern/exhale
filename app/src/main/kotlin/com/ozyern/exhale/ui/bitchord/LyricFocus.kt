/*
 * Ported from BitChord (github.com/kushagrasinghx/BitChord), GPL-3.0, by its authors.
 * Exhale is GPL-3.0 as well. Package and platform glue changed for Exhale; the lyric
 * timing, layout and animation are BitChord's own.
 */

package com.ozyern.exhale.ui.bitchord


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
