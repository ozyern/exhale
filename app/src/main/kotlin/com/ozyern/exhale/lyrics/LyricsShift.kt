/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/** Moves every timestamp in LRC, enhanced LRC or TTML lyrics later by a number of milliseconds. */
object LyricsShift {
    private val lrcTag = Regex("""([\[<])(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?([\]>])""")
    private val ttmlTime = Regex("""\b(begin|end)="([^"]+)"""")

    fun shift(lyrics: String, offsetMs: Long): String {
        if (offsetMs == 0L) return lyrics
        return if (LyricsUtils.isTtml(lyrics)) shiftTtml(lyrics, offsetMs) else shiftLrc(lyrics, offsetMs)
    }

    private fun shiftLrc(lyrics: String, offsetMs: Long): String =
        lrcTag.replace(lyrics) { m ->
            val (open, min, sec, frac, close) = m.destructured
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            val total = (min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs + offsetMs).coerceAtLeast(0L)
            "%s%02d:%02d.%03d%s".format(open, total / 60_000, (total % 60_000) / 1000, total % 1000, close)
        }

    private fun shiftTtml(lyrics: String, offsetMs: Long): String =
        ttmlTime.replace(lyrics) { m ->
            val seconds = parseClock(m.groupValues[2]) ?: return@replace m.value
            val total = (seconds * 1000 + offsetMs).coerceAtLeast(0.0) / 1000.0
            """${m.groupValues[1]}="${"%.3f".format(java.util.Locale.ROOT, total)}s""""
        }

    private fun parseClock(value: String): Double? {
        val v = value.trim()
        if (v.endsWith("ms")) return v.dropLast(2).toDoubleOrNull()?.div(1000)
        if (v.endsWith("s")) return v.dropLast(1).toDoubleOrNull()
        val parts = v.split(":")
        return runCatching {
            parts.fold(0.0) { acc, part -> acc * 60 + part.toDouble() }
        }.getOrNull()
    }
}

/** Set by the playback service: measures how far into a video its song begins. */
object VideoLyricsAlignment {
    @Volatile
    var aligner: (suspend (videoId: String, songId: String) -> Long?)? = null
}
