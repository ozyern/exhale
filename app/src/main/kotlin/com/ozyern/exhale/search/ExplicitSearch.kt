/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.search

import com.music.innertube.models.SongItem
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage

/**
 * YouTube Music censors search: its suggestions come back as "a$$ like that", and the top result
 * for "ass like that" is the "A** Like That (Super Clean Version)" video while the album track
 * waits further down. These put back what the user typed and lead with the explicit song. Callers
 * skip them when the user has chosen to hide explicit content.
 */
object ExplicitSearch {
    private val maskChars = setOf('*', '$', '@', '#', '!')
    private val cleanTag = Regex("""(?i)\b(super\s+clean|clean|censored|radio\s+edit)\b""")
    private val trailingTag = Regex("""\s*[(\[][^)\]]*[)\]]""")

    private fun isMasked(word: String) = word.length >= 2 && word.any { it in maskChars } && word.any { it.isLetter() }

    /** [masked] is [word] with some letters starred out ("a$$" / "ass", "f**k" / "fuck"). */
    private fun fits(masked: String, word: String): Boolean =
        masked.length == word.length && word.none { it in maskChars } &&
            masked.indices.all { i -> masked[i] in maskChars || masked[i].equals(word[i], ignoreCase = true) }

    /**
     * The suggestion with each starred-out word replaced by the word the user typed that it stands
     * for. A word with no typed counterpart stays as it is: nothing is guessed.
     */
    fun uncensor(suggestion: String, typed: String): String {
        val typedWords = typed.split(' ').filter { it.isNotBlank() }
        if (typedWords.isEmpty()) return suggestion
        return suggestion.split(' ').joinToString(" ") { token ->
            if (!isMasked(token)) token else typedWords.firstOrNull { fits(token, it) }?.lowercase() ?: token
        }
    }

    /** A clean or censored edit: tagged so, or with a starred-out word in its title. */
    fun isCleanEdit(title: String): Boolean =
        cleanTag.containsMatchIn(title) || title.split(' ').any(::isMasked)

    /** Same song apart from tags and starred-out letters: "A** Like That (Clean)" / "Ass Like That". */
    private fun sameTitle(clean: String, explicit: String): Boolean {
        val a = trailingTag.replace(clean, "").trim().split(Regex("""\s+"""))
        val b = trailingTag.replace(explicit, "").trim().split(Regex("""\s+"""))
        return a.size == b.size && a.indices.all { i -> a[i].equals(b[i], ignoreCase = true) || fits(a[i], b[i]) }
    }

    /**
     * When the top result is a clean edit and the page also has the explicit song of the same name,
     * that song leads instead (and is not listed twice).
     */
    fun preferExplicit(page: SearchSummaryPage): SearchSummaryPage {
        val top = page.summaries.firstOrNull() ?: return page
        val lead = top.items.firstOrNull() as? SongItem ?: return page
        if (!isCleanEdit(lead.title)) return page
        val explicit = page.summaries.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<SongItem>()
            .firstOrNull { it.explicit && !isCleanEdit(it.title) && sameTitle(lead.title, it.title) }
            ?: return page
        val reordered = page.summaries.mapIndexedNotNull { index, summary ->
            val rest = summary.items.filter { it.id != explicit.id }
            val items = if (index == 0) listOf(explicit) + rest else rest
            if (items.isEmpty()) null else SearchSummary(summary.title, items)
        }
        return page.copy(summaries = reordered)
    }
}
