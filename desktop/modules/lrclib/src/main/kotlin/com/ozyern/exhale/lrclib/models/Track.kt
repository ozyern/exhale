/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

/**
 * Pick the track whose lyrics belong to the song we are actually playing.
 *
 * ### Why this is not "closest duration wins"
 *
 * It used to be exactly that: `minBy { abs(it.duration - duration) }.takeIf { diff <= 2 }`, with
 * the title and artist never consulted on the path that real playback takes (the overload below
 * only looked at them when the duration was unknown, which it almost never is). Everything it was
 * choosing between came out of LRCLIB's `/api/search`, which is a *fuzzy* full-text endpoint — the
 * result list routinely contains covers, remixes, live versions and unrelated songs that merely
 * matched some of the words. Any two three-minute pop songs are within two seconds of each other,
 * so whenever the correct entry was missing or slightly off-length, the first plausible-length
 * stranger in the list won and the player showed another song's words, perfectly in time. That is
 * the "some songs show wrong lyrics" bug, and it was never random: it was the matcher working as
 * written.
 *
 * Duration is a tie-breaker between candidates that are already the same song. It is not an
 * identity check, and it cannot be made into one.
 *
 * ### What replaces it
 *
 * Every candidate is scored on title and artist similarity, penalised by how far its length is
 * from ours, and the winner has to clear [IDENTITY_FLOOR] on identity alone. Below that we return
 * nothing and let the next provider try, because no lyrics is a recoverable state and the wrong
 * lyrics is not.
 *
 * The scoring runs on [normalizeForMatch]ed strings, and that part is load-bearing in the other
 * direction. Titles arriving from YouTube come decorated — `Courtside (Official Audio)`,
 * `... [Lyrical Video]`, `... - Remastered 2011` — against LRCLIB's clean `Courtside`, and a
 * strict floor applied to raw strings would reject a pile of *correct* matches and turn a
 * wrong-lyrics bug into a no-lyrics bug. Stripping the decoration first is what makes the floor
 * safe to impose.
 */
internal fun List<Track>.bestMatchingFor(duration: Int): Track? =
    bestMatchingFor(duration, null, null)

internal fun List<Track>.bestMatchingFor(
    duration: Int,
    trackName: String? = null,
    artistName: String? = null
): Track? {
    if (isEmpty()) return null

    // No identity to check against: the caller genuinely does not know what is playing. Fall back
    // to the old behaviour rather than rejecting everything.
    if (trackName.isNullOrBlank() || artistName.isNullOrBlank()) {
        if (duration == -1) return firstOrNull { it.syncedLyrics != null } ?: firstOrNull()
        return minByOrNull { abs(it.duration.toInt() - duration) }
            ?.takeIf { abs(it.duration.toInt() - duration) <= DURATION_TOLERANCE_SECONDS }
    }

    val wantedTitle = normalizeForMatch(trackName)
    val wantedArtist = normalizeForMatch(artistName)

    val best = maxByOrNull { track ->
        identityScore(track, wantedTitle, wantedArtist) - durationPenalty(track, duration) +
            if (track.syncedLyrics != null) SYNCED_BONUS else 0.0
    } ?: return null

    if (identityScore(best, wantedTitle, wantedArtist) < IDENTITY_FLOOR) return null
    if (duration != -1 && abs(best.duration.toInt() - duration) > DURATION_TOLERANCE_SECONDS) return null
    return best
}

/**
 * How confident we are that [track] *is* the song, in 0..1, before length is considered.
 *
 * Weighted towards the title because that is the field both sides agree on most often: LRCLIB
 * stores one artist string while the player may be carrying four names joined by commas.
 */
internal fun identityScore(track: Track, wantedTitle: String, wantedArtist: String): Double {
    val titleScore = calculateSimilarity(wantedTitle, normalizeForMatch(track.trackName))

    // Compared three ways, and the most generous wins. "Karan Aujla, Signature by SB" against
    // LRCLIB's "Karan Aujla" is the same artist; so is "Karan Aujla" against "Karan Aujla & Ikky".
    // A straight string comparison scores both of those like a mismatch.
    val theirArtist = normalizeForMatch(track.artistName)
    val ourLead = wantedArtist.substringBefore(" and ").substringBefore(",").trim()
    val theirLead = theirArtist.substringBefore(" and ").substringBefore(",").trim()
    val artistScore = maxOf(
        calculateSimilarity(wantedArtist, theirArtist),
        calculateSimilarity(ourLead, theirArtist),
        calculateSimilarity(wantedArtist, theirLead),
        calculateSimilarity(ourLead, theirLead),
    )

    return titleScore * TITLE_WEIGHT + artistScore * (1.0 - TITLE_WEIGHT)
}

/** A soft penalty, not a gate — the hard length check happens once, on the winner. */
private fun durationPenalty(track: Track, duration: Int): Double {
    if (duration == -1) return 0.0
    return when (abs(track.duration.toInt() - duration)) {
        in 0..2 -> 0.0
        in 3..5 -> 0.10
        in 6..12 -> 0.30
        else -> 1.0
    }
}

/**
 * Strips everything a music service adds to a title that is not the title.
 *
 * Bracketed suffixes are only removed when they contain one of the giveaway words, so
 * `Hello (Acoustic)` keeps its bracket — it really is a different recording with different
 * words — while `Hello (Official Music Video)` loses one that never meant anything.
 */
internal fun normalizeForMatch(raw: String): String {
    var text = raw.lowercase()

    text = BRACKETED.replace(text) { match ->
        val inner = match.groupValues[1]
        if (NOISE_WORDS.any { inner.contains(it) }) "" else match.value
    }
    text = TRAILING_NOISE.replace(text, "")
    text = NON_ALNUM.replace(text, " ")
    return text.trim().replace(WHITESPACE, " ")
}

private const val TITLE_WEIGHT = 0.65
private const val IDENTITY_FLOOR = 0.55
private const val SYNCED_BONUS = 0.05
private const val DURATION_TOLERANCE_SECONDS = 2

private val BRACKETED = Regex("""[(\[]([^)\]]*)[)\]]""")
private val TRAILING_NOISE = Regex("""\s[-–—]\s.*(official|video|audio|lyric|remaster|visuali|explicit|hd|4k).*$""")
private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
private val WHITESPACE = Regex("""\s+""")
private val NOISE_WORDS = listOf(
    "official", "video", "audio", "lyric", "visuali", "remaster", "explicit",
    "hd", "4k", "mv", "m/v", "full song", "with lyrics", "color coded",
)

private fun calculateSimilarity(str1: String, str2: String): Double {
    if (str1 == str2) return 1.0
    if (str1.isEmpty() || str2.isEmpty()) return 0.0

    val containsScore = when {
        str1.contains(str2) || str2.contains(str1) -> 0.8
        else -> 0.0
    }

    val maxLength = maxOf(str1.length, str2.length)
    val distance = levenshteinDistance(str1, str2)
    val distanceScore = 1.0 - (distance.toDouble() / maxLength)
    
    return maxOf(containsScore, distanceScore)
}

private fun levenshteinDistance(str1: String, str2: String): Int {
    val len1 = str1.length
    val len2 = str2.length
    val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
    
    for (i in 0..len1) matrix[i][0] = i
    for (j in 0..len2) matrix[0][j] = j
    
    for (i in 1..len1) {
        for (j in 1..len2) {
            val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
            matrix[i][j] = minOf(
                matrix[i - 1][j] + 1,      // deletion
                matrix[i][j - 1] + 1,      // insertion
                matrix[i - 1][j - 1] + cost // substitution
            )
        }
    }
    
    return matrix[len1][len2]
}
