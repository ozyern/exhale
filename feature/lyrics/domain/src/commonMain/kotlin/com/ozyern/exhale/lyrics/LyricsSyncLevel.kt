package com.ozyern.exhale.lyrics

/** Capabilities come from real timestamps, never from a provider's advertised name. */
enum class LyricsSyncLevel(val label: String) {
    PLAIN("Plain lyrics"), LINE("Line synced"), WORD("Word by word");

    companion object {
        private val lineStamp = Regex("""^\[(\d{1,3}):(\d{2})\.(\d{2,3})]""")
        private val wordStamp = Regex("""<(\d{1,3}):(\d{2})\.(\d{2,3})(?:\|(\d{1,3}):(\d{2})\.(\d{2,3}))?>([^<]+)""")
        private fun MatchResult.time(offset: Int = 1): Long =
            groupValues[offset].toLong() * 60_000 + groupValues[offset + 1].toLong() * 1000 +
                groupValues[offset + 2].padEnd(3, '0').toLong()

        fun detect(lyrics: String): LyricsSyncLevel {
            val lineTimes = hashSetOf<Long>()
            for (raw in lyrics.lineSequence()) {
                val line = raw.trim()
                lineStamp.find(line)?.let { lineTimes.add(it.time()) }
                val words = wordStamp.findAll(line).filter { word ->
                    word.groupValues[7].isNotBlank() &&
                        (word.groupValues[4].isBlank() || word.time(4) > word.time())
                }.map { it.time() }.toList()
                if (words.size > 1 && words.distinct().size > 1 && words.zipWithNext().all { it.first <= it.second }) return WORD
                // Older Exhale providers store word runs on a separate <word:start:end> row.
                if (line.startsWith('<') && line.endsWith('>') && !line.contains("</")) {
                    val starts = line.removeSurrounding("<", ">").split('|').mapNotNull { run ->
                        val pieces = run.split(':')
                        if (pieces.size != 3 || pieces[0].isBlank()) return@mapNotNull null
                        val start = pieces[1].toDoubleOrNull() ?: return@mapNotNull null
                        val end = pieces[2].toDoubleOrNull() ?: return@mapNotNull null
                        start.takeIf { it >= 0 && end > it && it.isFinite() && end.isFinite() }
                    }
                    if (starts.size > 1 && starts.distinct().size > 1 && starts.zipWithNext().all { it.first <= it.second }) return WORD
                }
            }
            return if (lineTimes.size > 1 || lineTimes.any { it > 0 }) LINE else PLAIN
        }
    }
}

data class LyricsUpgradeCandidate(val provider: String, val priority: Int, val lyrics: String, val sync: LyricsSyncLevel)

/**
 * Picks the lyrics to show as sources answer.
 *
 * Without a [title] it is a monotonic quality ladder: better timing wins, and the listener's source
 * priority breaks ties within one timing level.
 *
 * With the song's [title] it judges the answers against each other the way Exhale for Windows does,
 * which is why the desktop app shows the right words where this one used to show another song's:
 * sources that heard the same song say much the same words, so one that agrees with none of the
 * others (when the others agree among themselves) is demoted; so is one that never says a word of
 * the song's name, and one with its words bleeped out. Timing and priority settle the rest.
 */
class LyricsUpgradePolicy(private val title: String? = null) {
    var best: LyricsUpgradeCandidate? = null
        private set

    private val seen = mutableListOf<Judged>()

    private class Judged(val candidate: LyricsUpgradeCandidate, title: String?) {
        val words: Set<String> = vocabulary(candidate.lyrics)
        val echoesTitle: Boolean = title == null || echoes(candidate.lyrics, title)
        val censored: Boolean = masked.containsMatchIn(candidate.lyrics)
    }

    fun consider(provider: String, priority: Int, lyrics: String): LyricsUpgradeCandidate? {
        if (lyrics.isBlank()) return null
        val candidate = LyricsUpgradeCandidate(provider, priority, lyrics, LyricsSyncLevel.detect(lyrics))
        if (title == null) {
            val current = best
            if (current != null && (candidate.sync < current.sync ||
                    (candidate.sync == current.sync && candidate.priority >= current.priority))) return null
            best = candidate
            return candidate
        }
        seen += Judged(candidate, title)
        val agreeing = seen.filter { one -> seen.any { other -> other !== one && overlap(one.words, other.words) >= 0.5 } }.toSet()
        val pick = seen.minWithOrNull(
            compareBy<Judged>(
                { if (agreeing.isNotEmpty() && it !in agreeing) 1 else 0 },
                { if (it.echoesTitle) 0 else 1 },
                { if (it.censored) 1 else 0 },
                { -it.candidate.sync.ordinal },
                { it.candidate.priority },
            ),
        )?.candidate
        if (pick == null || pick === best) return null
        best = pick
        return pick
    }

    private companion object {
        // Closing brackets escaped: Android's ICU engine rejects a bare "}" (a crash on load there),
        // where the JVM's own engine reads it as a literal.
        private val tags = Regex("""\[[^\]]*\]|<[^>]*>|\{[^}]*\}""")
        private val split = Regex("""[^\p{L}\p{N}']+""")
        private val masked = Regex("""\p{L}\*{2,}|\*{2,}\p{L}|\p{L}\*+\p{L}|(?<!\S)\*{3,}(?!\S)""")
        private val stopwords = setOf(
            "this", "that", "with", "your", "from", "have", "what", "when", "they", "them", "just",
            "like", "about", "into", "over", "been", "were", "will", "part", "remix", "live", "version",
        )

        fun text(lyrics: String): String = tags.replace(lyrics, " ").lowercase()

        fun vocabulary(lyrics: String): Set<String> = text(lyrics).split(split).filter { it.length >= 3 }.toSet()

        /** Most songs say their own name; only distinctive words of it count. */
        fun echoes(lyrics: String, title: String): Boolean {
            val words = title.lowercase().split(split).filter { it.length >= 4 && it !in stopwords }
            if (words.isEmpty()) return true
            val body = text(lyrics)
            return words.any { it in body }
        }

        fun overlap(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val (small, large) = if (a.size <= b.size) a to b else b to a
            return small.count { it in large }.toDouble() / small.size
        }
    }
}
