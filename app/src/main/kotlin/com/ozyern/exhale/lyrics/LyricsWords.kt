/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

object LyricsWords {
    /** See [sameWords]: below this, two copies are different songs. */
    private const val SAME_WORDS_SHARE = 0.3

    /**
     * Whether two copies of lyrics are the same song's words, however each is timed or transcribed:
     * the share of distinct words (three letters and up) the two have in common. Two transcriptions
     * of one song share most of theirs; two different songs, little beyond the commonest words.
     */
    fun sameWords(a: String, b: String): Boolean {
        val first = wordsOf(a)
        val second = wordsOf(b)
        if (first.size < 8 || second.size < 8) return true
        val shared = first.intersect(second).size.toDouble()
        return shared / (first.size + second.size - shared) >= SAME_WORDS_SHARE
    }

    /**
     * How much later [b] runs than [a], in milliseconds: the median gap across the lines both have.
     * Null when they share too few lines to say. Two copies with the same words can still be timed for
     * different cuts, and word comparison alone kept the badly timed one for good.
     */
    fun timingDriftMs(a: String, b: String): Long? {
        val first = timesByLine(a)
        val second = timesByLine(b)
        val gaps = first.keys.intersect(second.keys).map { second.getValue(it) - first.getValue(it) }.sorted()
        return if (gaps.size < MIN_SHARED_LINES) null else gaps[gaps.size / 2]
    }

    private const val MIN_SHARED_LINES = 4

    /** Where each distinct line is first sung. Empty for untimed lyrics. */
    private fun timesByLine(lyrics: String): Map<String, Long> {
        val entries = when {
            LyricsUtils.isTtml(lyrics.trimStart()) -> LyricsUtils.parseTtml(lyrics)
            lyrics.trimStart().startsWith("[") -> LyricsUtils.parseLyrics(lyrics)
            else -> return emptyMap()
        }
        val times = LinkedHashMap<String, Long>()
        entries.forEach { entry ->
            val line = SongQuery.normalized(entry.text)
            if (line.isNotEmpty()) times.putIfAbsent(line, entry.time)
        }
        return times
    }

    /** The sung lines of any format, without their timing. */
    fun linesOf(lyrics: String): List<String> = when {
        LyricsUtils.isTtml(lyrics.trimStart()) -> LyricsUtils.parseTtml(lyrics).map { it.text }
        lyrics.trimStart().startsWith("[") -> LyricsUtils.parseLyrics(lyrics).map { it.text }
        else -> lyrics.lines()
    }

    private fun wordsOf(lyrics: String): Set<String> =
        SongQuery.normalized(linesOf(lyrics).joinToString(" ")).split(' ').filter { it.length >= 3 }.toSet()
}
