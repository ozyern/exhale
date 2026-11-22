/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import com.ozyern.exhale.lyrics.providers.LyricLine
import com.ozyern.exhale.lyrics.providers.artistForLyricsSearch
import com.ozyern.exhale.lyrics.providers.forLyricsSearch

/**
 * What the lyrics sites are asked for, and whether what they answer can be this recording: the same
 * two rules Exhale's Windows app (and master's Android app) puts every source through, which is why
 * songs that found nothing here, or the wrong words, found the right ones there.
 *
 * Plain Kotlin throughout: this module also builds for iOS.
 */
object LyricsSearch {
    /**
     * The song and artist as a lyrics site has them, from a YouTube title and its artists.
     *
     * - "Re Bawri (Song) | Alia Bhatt | Sona Mohapatra | T-Series": everything after the first bar is
     *   credits, and the singer is named in them rather than the label that uploaded it.
     * - "Eminem - Without Me (Official Music Video)" by EminemVEVO: the part before the dash is the
     *   channel, so it is the artist, and the rest is the song.
     * - "Kesariya – Full Video", "Song - 2011 Remaster", "[Explicit]": how it was uploaded, not its name.
     */
    fun query(title: String, artists: String): Pair<String, String> {
        val artistList = artists.split(", ").map(String::trim).filter(String::isNotEmpty)
        val (song, artist) = songAndArtist(title, artistList)
        return song.forLyricsSearch() to artist.artistForLyricsSearch()
    }

    /**
     * Every way worth asking a lyrics site for this song, best first, without repeats: as a lyrics
     * site files it ([query]), then with the singer the title credits instead of the uploader,
     * then exactly as written on YouTube with every artist. A source that misses on one phrasing
     * often has the song under another, which is most of what Exhale for Windows finds here that a
     * single question did not.
     */
    fun phrasings(title: String, artists: String): List<Pair<String, String>> {
        val artistList = artists.split(", ").map(String::trim).filter(String::isNotEmpty)
        val primary = query(title, artists)
        val credited = creditedArtist(title, artistList)
            ?.let(::artistOfChannel)
            ?.artistForLyricsSearch()
            ?.let { primary.first to it }
        val asWritten = title.trim() to artistList.joinToString(", ")
        return listOfNotNull(primary, credited, asWritten)
            .filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
            .distinctBy { (song, artist) -> song.lowercase() to artist.lowercase() }
    }

    /**
     * Whether timed lyrics could belong to the recording playing, judged by their timestamps against
     * its length. Another cut's timings (a radio edit, a live take, a video with an intro) run past the
     * end, start late or stop far short, and scroll out of step from the first verse; turned away, the
     * next source's copy is shown instead. Untimed lyrics and unknown lengths always pass.
     */
    fun fits(lines: List<LyricLine>, durationMs: Long, title: String? = null): Boolean {
        val durationS = durationMs / 1000.0
        if (durationS < MIN_DURATION_S) return true
        val times = lines.filter { it.text.isNotBlank() }.map { it.timeMs }.filter { it >= 0 }.sorted()
        if (times.size < 4 || times.none { it > 0 }) return true
        val first = times.first() / 1000.0
        val last = times.last() / 1000.0
        // Past the end: another, longer cut.
        if (last > durationS + 10) return false
        // Starting most of the way through: timed for something else entirely.
        if (first > durationS * 0.6) return false
        // Finished well before the end: a shorter edit's timing. A live take runs longer, so one
        // song's studio timings must cover most of it before they are trusted.
        val floor = if (title != null && oneOff.containsMatchIn(title)) 0.75 else 0.4
        if (durationS > 90 && last < durationS * floor) return false
        return true
    }

    private const val MIN_DURATION_S = 30

    private val oneOff = Regex("""(?i)\b(live|concert|session|medley|mashup|unplugged|tour)\b""")

    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val noise = Regex(
        """(?i)\b(feat\.?|ft\.?|featuring|with|official|video|audio|lyrics?|lyrical|visuali[sz]er|m/?v|hd|hq|4k|explicit|clean|remaster(ed)?|prod\.?|song|full|teaser|promo|title\s+track)\b""",
    )
    private val trailingFeature = Regex("""(?i)\s+(feat\.?|ft\.?|featuring)\s.*$""")
    private val trailingRemaster = Regex("""(?i)\s+-\s+[^-]*remaster[^-]*$""")
    private val trailingDashNoise =
        Regex("""(?i)\s+[-–—]\s+[^-–—]*\b(full|official|video|audio|lyrics?|lyrical|song|teaser|promo|visuali[sz]er)\b[^-–—]*$""")
    private val channelSuffix = Regex("""(?i)(\s*-\s*topic|\s*vevo)\s*$""")
    private val titleDash = Regex("""\s+[-–—]\s+""")
    private val spaces = Regex("""\s+""")

    internal fun cleanTitle(title: String): String {
        val head = title.substringBefore('|').trim().ifBlank { title }
        var cleaned = bracketed.replace(head) { match -> if (noise.containsMatchIn(match.groupValues[1])) "" else match.value }
        cleaned = trailingFeature.replace(cleaned, "")
        cleaned = trailingRemaster.replace(cleaned, "")
        cleaned = trailingDashNoise.replace(cleaned, "")
        return cleaned.replace(spaces, " ").trim().ifBlank { head.trim() }
    }

    /** Uploads credit the label; a bar-separated title usually names the singer after the song. */
    internal fun creditedArtist(title: String, artists: List<String>): String? {
        val lead = artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val segments = title.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return lead
        return segments.drop(1).firstOrNull { segment ->
            segment.length in 2..40 && !segment.equals(lead, ignoreCase = true) && !noise.containsMatchIn(segment)
        } ?: lead
    }

    /** "EminemVEVO" is Eminem, "Shakira - Topic" is Shakira. */
    internal fun artistOfChannel(channel: String): String = channelSuffix.replace(channel, "").trim().ifBlank { channel.trim() }

    internal fun songAndArtist(title: String, artists: List<String>): Pair<String, String> {
        val cleaned = cleanTitle(title)
        val channel = artistOfChannel(creditedArtist(title, artists).orEmpty())
        val dash = titleDash.find(cleaned) ?: return cleaned to channel
        val before = cleaned.substring(0, dash.range.first).trim()
        val after = cleaned.substring(dash.range.last + 1).trim()
        val own = squeezed(channel)
        fun isChannel(part: String): Boolean {
            val named = squeezed(part)
            return named.isNotEmpty() && own.isNotEmpty() && (named == own || named.startsWith(own) || own.startsWith(named))
        }
        if (isChannel(before) && after.isNotEmpty()) return after to before
        if (isChannel(after) && before.isNotEmpty()) return before to after
        return cleaned to channel
    }

    /** Letters and digits only, lower case: enough to tell a channel from its artist. */
    private fun squeezed(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
}
