/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import java.util.Locale

/**
 * Whether a source's answer is really this song: the checks the Windows app puts every answer through.
 *
 * A source that matched on length alone, or searched loosely, hands back well-timed lyrics of another
 * song, and without these the highest-placed word-timed answer was shown whatever it said.
 */
internal object LyricsJudge {
    /** Fewer sung lines than this, once the paperwork is out, and it was never a song. */
    private const val MINIMUM_LINES = 4

    /** Two answers sharing this much of the smaller one's words are the same song. */
    private const val AGREEMENT = 0.5

    private val creditLine = Regex(
        """(?i)(publishing|publisher|warner|chappell|universal music|sony/?atv|kobalt|songwriter|composer|lyricist|arranger|all rights reserved|℗|©|作词|作曲|编曲|制作人)""",
    )
    private val writerList = Regex("""[^\s/]{2,}(/[^\s/]{2,}){2,}""")
    private val wordSplit = Regex("""[^\p{L}\p{N}']+""")
    private val phraseNoise = Regex("""[^\p{L}\p{N}' ]+""")
    private val spaces = Regex("""\s+""")
    private val stopwords = setOf(
        "this", "that", "with", "your", "from", "have", "what", "when", "they", "them", "just",
        "like", "about", "into", "over", "been", "were", "will", "part", "remix", "live", "version",
    )
    private val minorWords = setOf("the", "and", "feat", "with", "from", "for", "you", "your")

    private fun isPaperwork(line: String): Boolean {
        val text = line.trim()
        return creditLine.containsMatchIn(text) || writerList.matches(text) ||
            (text.length < 48 && (text.endsWith('：') || text.endsWith(':')))
    }

    /** False when what came back is too short to be a song, or nothing but its credits. */
    fun isSong(lines: List<String>): Boolean = lines.count { it.isNotBlank() && !isPaperwork(it) } >= MINIMUM_LINES

    /** Most songs say their own name. Not all do, so failing this only ever demotes an answer. */
    fun echoesTitle(text: String, title: String): Boolean {
        val words = title.lowercase(Locale.US).split(wordSplit).filter { it.length >= 4 && it !in stopwords }
        if (words.isEmpty()) return true
        val body = text.lowercase(Locale.US)
        return words.any { it in body }
    }

    /**
     * The stricter test for a source that searches loosely: the title as a phrase, or every word of it,
     * somewhere in the lyrics. Such a source has to pass this to be considered at all.
     */
    fun soundsLikeTitle(text: String, title: String): Boolean {
        val body = text.lowercase(Locale.US)
        val phrase = title.lowercase(Locale.US).replace(phraseNoise, " ").replace(spaces, " ").trim()
        if (phrase.isNotEmpty() && phrase in body.replace(phraseNoise, " ").replace(spaces, " ")) return true
        val words = phrase.split(' ').filter { it.length >= 3 && it !in minorWords }
        if (words.isEmpty()) return true
        val sung = body.split(wordSplit).toSet()
        return words.all { it in sung }
    }

    /** The distinct words of an answer, to hold one source's against another's. */
    fun vocabulary(text: String): Set<String> =
        text.lowercase(Locale.US).split(wordSplit).filter { it.length >= 3 }.toSet()

    /**
     * Which answers some other answer backs up. Sources that heard the same song say much the same
     * words, so one agreeing with nobody, while others agree among themselves, is singing something else.
     */
    fun agreeing(answers: Map<Int, Set<String>>): Set<Int> =
        answers.keys.filterTo(HashSet()) { index ->
            answers.any { (other, words) -> other != index && overlap(answers.getValue(index), words) >= AGREEMENT }
        }

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (small, large) = if (a.size <= b.size) a to b else b to a
        return small.count { it in large }.toDouble() / small.size
    }
}
