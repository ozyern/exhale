/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.innertube.models

import com.ozyern.exhale.innertube.utils.parseTime
import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null ||
        (getOrNull(0)?.getOrNull(0)?.text?.contains(regex = Regex("[&,]"))) != false
    ) {
        this
    } else {
        this.drop(1)
    }

/**
 * "1.2M plays", "3.4M views", "12K listeners" — and the same in other languages, where the number and
 * its magnitude are written the same way and only the word after them changes.
 */
private val CountRun = Regex("""^\s*\d[\d.,]*\s?(?:[KMBkmb]|Mio\.?|Mrd\.?|Mil|lakh|crore|万|億|억|만|천)\+?(?:\s.*)?$""")

private fun Run.isSeparator() = text.trim() == "•"

/** The same written out in full, thousands grouped: "1,234,567 plays", "1.234.567 Aufrufe", "1 234 567 views". */
private val GroupedCount = Regex("""^\s*\d{1,3}(?:[.,   ]\d{3})+\s+\S.*$""")

/**
 * The runs of a subtitle without its play count. A subtitle reads `Artist • 1.2M plays`, and taking
 * every other run as an artist — which is what [oddElements] does — made the count a second artist, or
 * the only one, so the mini player showed how often a song had been played where its artist belonged.
 *
 * A count has no link and reads as a number with a magnitude ("1.2M") or with grouped thousands. An
 * artist whose name starts with a digit ("2 Chainz", "21 Savage", "50 Cent") has neither, so is never
 * touched. A runtime goes the same way — "3:45" is not a name either, and a row that carries one puts
 * it in the same column. The separator beside a removed run goes with it, or what is left would swap
 * places and the wrong run would be read as the artist.
 */
private fun List<Run>.withoutPlayCounts(): List<Run> {
    val kept = ArrayList<Run>(size)
    var i = 0
    while (i < size) {
        val run = this[i]
        val looksLikeCount = run.navigationEndpoint == null && !run.isSeparator() &&
            (CountRun.matches(run.text) || GroupedCount.matches(run.text) || run.text.parseTime() != null)
        if (looksLikeCount) {
            if (kept.isNotEmpty() && kept.last().isSeparator()) kept.removeAt(kept.lastIndex)
            else if (i + 1 < size && this[i + 1].isSeparator()) i++
        } else {
            kept.add(run)
        }
        i++
    }
    return kept
}

fun List<Run>.oddElements() =
    withoutPlayCounts().filterIndexed { index, _ ->
        index % 2 == 0
    }

/**
 * Which "•" group of a subtitle holds the artists.
 *
 * A song reads `Song • Artist • 2:56`, an upload `Channel • 1.2M views`, a localised row may drop the
 * type label altogether — so "the second group" is the artist only some of the time, and taking it on
 * trust put a view count where the artist belongs, or nothing at all. The group that links to a channel
 * is the artist wherever it sits; failing that, the first group that is not a lone type label, a count
 * or a runtime.
 */
fun List<List<Run>>.artistGroupRuns(): List<Run> {
    fun linksToArtist(run: Run): Boolean {
        val browse = run.navigationEndpoint?.browseEndpoint ?: return false
        val type = browse.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        return browse.browseId.startsWith("UC") || type == "MUSIC_PAGE_TYPE_ARTIST" || type == "MUSIC_PAGE_TYPE_USER_CHANNEL"
    }
    firstOrNull { group -> group.any(::linksToArtist) }?.let { return it.oddElements() }
    // A leading single unlinked word ("Song", "Video", "गाना") is the row's type, not who made it —
    // even when nothing else in the row is a name. Better no artist, filled in from the player's own
    // answer when the song starts, than "Video" shown where the artist belongs.
    val body = if (size >= 2 && first().size == 1 && first().first().navigationEndpoint == null) drop(1) else this
    return body.firstOrNull { group -> group.oddElements().isNotEmpty() }?.oddElements().orEmpty()
}
