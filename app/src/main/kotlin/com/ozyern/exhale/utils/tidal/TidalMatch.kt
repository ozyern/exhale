/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils.tidal

import kotlin.math.abs

/**
 * Turns the YouTube Music view of a song into TIDAL searches and judges TIDAL's answers.
 *
 * YouTube metadata varies with where a song is played from: an album row gives "Espresso" by
 * "Sabrina Carpenter", while a video, a search card or a localised feed can give "Sabrina Carpenter
 * - Espresso (Official Video)" by "SabrinaCarpenterVEVO", or the artist written in Devanagari. TIDAL's
 * search returns nothing when a query holds a word it does not know, so the queries are cleaned and
 * loosened in turn, and a match must agree on the title and on the artist or the length.
 */
internal object TidalMatch {

    data class Candidate(
        val id: Long,
        val title: String,
        val artists: List<String>,
        val durationSec: Int?,
        val lossless: Boolean,
    )

    /** Bracketed or dashed tails YouTube adds to a song title that TIDAL never has. */
    private val VideoNoise = Regex(
        """\s*[(\[](?:[^)\]]*\b(?:official|video|audio|lyrics?|lyric video|visuali[sz]er|music video|mv|hd|4k|explicit|clean|color coded)\b[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val TrailingNoise = Regex(
        """\s*[|｜]\s*.*$|\s+-\s+(?:official|lyrics?|audio|video)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val ChannelSuffix = Regex("""\s*(?:-\s*topic|vevo|official)\s*$""", RegexOption.IGNORE_CASE)

    /** The title without video tags ("(Official Video)", "[Lyrics]", "| Live at…"). */
    fun cleanTitle(title: String): String =
        title.replace(VideoNoise, "").replace(TrailingNoise, "").trim().ifBlank { title.trim() }

    /** An artist without channel decorations (" - Topic", "VEVO"), or null when nothing is left. */
    fun cleanArtist(artist: String): String? =
        artist.replace(ChannelSuffix, "").trim().takeIf { it.isNotEmpty() }

    /** Latin letters, so TIDAL's search can use the name; a name only in another script cannot help. */
    private fun searchable(text: String): Boolean = text.any { it in 'A'..'Z' || it in 'a'..'z' }

    /**
     * The song title on its own: "Sabrina Carpenter - Espresso" becomes "Espresso" when the part
     * before the dash names one of the artists (or there are no usable artists to check against).
     */
    fun songTitle(title: String, artists: List<String>): String {
        val clean = cleanTitle(title)
        val dash = clean.indexOf(" - ")
        if (dash <= 0) return clean
        val head = normalize(clean.substring(0, dash))
        val usable = artists.mapNotNull(::cleanArtist).map(::normalize).filter { it.isNotEmpty() }
        val headIsArtist = usable.isEmpty() || usable.any { head.contains(it) || it.contains(head) }
        return if (headIsArtist) clean.substring(dash + 3).trim().ifBlank { clean } else clean
    }

    /** Searches to try in order: title with artist, then the title alone. */
    fun queries(title: String, artists: List<String>): List<String> {
        val song = songTitle(title, artists)
        val artist = artists.mapNotNull(::cleanArtist).firstOrNull(::searchable)
        return listOfNotNull(
            artist?.let { "$song $it" },
            song,
            cleanTitle(title).takeIf { it != song },
        ).distinct()
    }

    /**
     * Matches worth playing, best first; a lossless version ranks above its lossy twin. The title
     * must agree, and so must either an artist or the length (within [DURATION_TOLERANCE_SEC]), so
     * a title-only search for "Taste" cannot pick a stranger's song.
     */
    fun rank(
        candidates: List<Candidate>,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): List<Candidate> {
        val wantedTitle = normalize(songTitle(title, artists))
        val wantedArtists = artists.mapNotNull(::cleanArtist).map(::normalize).filter { it.length >= 2 }
        val wantedVersion = versionWords(cleanTitle(title))
        return candidates.mapNotNull { candidate ->
            val candTitle = normalize(cleanTitle(candidate.title))
            val titleScore = when {
                candTitle.isEmpty() || wantedTitle.isEmpty() -> 0
                candTitle == wantedTitle -> 50
                minOf(candTitle.length, wantedTitle.length) >= 3 &&
                    (candTitle.contains(wantedTitle) || wantedTitle.contains(candTitle)) -> 25
                else -> 0
            }
            if (titleScore == 0) return@mapNotNull null
            // A live take, remix or sped-up edit is a different recording from the album track.
            if (versionWords(candidate.title) != wantedVersion) return@mapNotNull null
            val artistMatch = candidate.artists.map(::normalize).filter { it.length >= 2 }.any { cand ->
                wantedArtists.any { want -> cand.contains(want) || want.contains(cand) }
            }
            val lengthMatch = durationSec != null && candidate.durationSec != null &&
                abs(candidate.durationSec - durationSec) <= DURATION_TOLERANCE_SEC
            if (!artistMatch && !lengthMatch) return@mapNotNull null
            val score = titleScore + (if (artistMatch) 30 else 0) + (if (lengthMatch) 20 else 0)
            candidate to score
        }.sortedWith(
            compareByDescending<Pair<Candidate, Int>> { it.second }.thenByDescending { it.first.lossless },
        ).map { it.first }
    }

    /** Words that make a title a different recording; both sides must carry the same ones. */
    private val VersionWords = setOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "slowed", "sped", "reverb",
        "nightcore", "cover", "demo", "edit", "unplugged", "orchestral", "piano",
    )

    private val NonWord = Regex("""[^\p{L}\p{N}]+""")

    fun versionWords(title: String): Set<String> =
        title.lowercase().split(NonWord).filterTo(HashSet()) { it in VersionWords }

    /** Lowercase letters and digits only, so punctuation, spacing and case never decide a match. */
    fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private const val DURATION_TOLERANCE_SEC = 5
}
