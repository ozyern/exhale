/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * What to search the lyrics sites for. YouTube titles carry things no lyrics database has in its
 * title — "(feat. Don Toliver)", "[Official Video]", "- 2011 Remaster" — and a search that includes
 * them often finds nothing, or a different song. The same cleaning the Windows app uses.
 */
internal object SongQuery {
    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val noise = Regex("""(?i)\b(feat\.?|ft\.?|featuring|with|official|video|audio|lyrics?|lyrical|visuali[sz]er|m/?v|hd|hq|4k|explicit|clean|remaster(ed)?|prod\.?|song|full|teaser|promo|title\s+track)\b""")
    private val trailingFeature = Regex("""(?i)\s+(feat\.?|ft\.?|featuring)\s.*$""")
    private val trailingRemaster = Regex("""(?i)\s+-\s+[^-]*remaster[^-]*$""")

    /** An upload's tag after a dash rather than in brackets: "Kesariya – Full Video". */
    private val trailingDashNoise =
        Regex("""(?i)\s+[-–—]\s+[^-–—]*\b(full|official|video|audio|lyrics?|lyrical|song|teaser|promo|visuali[sz]er)\b[^-–—]*$""")

    /**
     * What to search for. A song uploaded as a plain video is titled the way a channel titles a video —
     * "Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | T-Series" — where everything after
     * the first bar is credits, and searching the whole string finds nothing at all.
     */
    fun cleanTitle(title: String): String {
        val head = title.substringBefore('|').trim().ifBlank { title }
        var cleaned = bracketed.replace(head) { match -> if (noise.containsMatchIn(match.groupValues[1])) "" else match.value }
        cleaned = trailingFeature.replace(cleaned, "")
        cleaned = trailingRemaster.replace(cleaned, "")
        cleaned = trailingDashNoise.replace(cleaned, "")
        return cleaned.replace(Regex("""\s+"""), " ").trim().ifBlank { head.trim() }
    }

    /**
     * The artist as a lyrics site would have it. Uploads credit the label ("T-Series"), and a bar-
     * separated title usually names the singer itself — the segment after the song is a better guess
     * than the channel.
     */
    fun creditedArtist(title: String, artists: List<String>): String? {
        val lead = artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val segments = title.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return lead
        return segments.drop(1).firstOrNull { segment ->
            segment.length in 2..40 && !segment.equals(lead, ignoreCase = true) && !noise.containsMatchIn(segment)
        } ?: lead
    }
}
