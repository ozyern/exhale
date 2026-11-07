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

    private val channelSuffix = Regex("""(?i)(\s*-\s*topic|\s*vevo)\s*$""")
    private val titleDash = Regex("""\s+[-–—]\s+""")

    /** A channel as the artist it stands for: "EminemVEVO" is Eminem, "Shakira - Topic" is Shakira. */
    fun artistOfChannel(channel: String): String = channelSuffix.replace(channel, "").trim().ifBlank { channel.trim() }

    private fun squeezed(value: String) = normalized(value).replace(" ", "")

    /**
     * The song's title and artist as a lyrics site has them. Older catalogues live on YouTube as video
     * uploads, "Eminem - Without Me (Official Music Video)" by EminemVEVO, and no site has a song of that
     * title or an artist of that name, so every source came back empty. When the part before the dash
     * is the channel, that part is the artist and the rest is the song.
     */
    fun songAndArtist(title: String, artists: List<String>): Pair<String, String> {
        val cleaned = cleanTitle(title)
        val channel = artistOfChannel(creditedArtist(title, artists).orEmpty())
        val dash = titleDash.find(cleaned) ?: return cleaned to channel
        val before = cleaned.substring(0, dash.range.first).trim()
        val after = cleaned.substring(dash.range.last + 1).trim()
        val named = squeezed(before)
        val own = squeezed(channel)
        val isChannel = named.isNotEmpty() && own.isNotEmpty() && (named == own || named.startsWith(own) || own.startsWith(named))
        if (isChannel && after.isNotEmpty()) return after to before
        val afterNamed = squeezed(after)
        val isAfterChannel = afterNamed.isNotEmpty() && own.isNotEmpty() && (afterNamed == own || afterNamed.startsWith(own) || own.startsWith(afterNamed))
        if (isAfterChannel && before.isNotEmpty()) return before to after
        return cleaned to channel
    }

    /** Words that make a hit another recording of the song: another language, another mix, another take. */
    private val otherVersionWords = setOf(
        "version", "versao", "remix", "mix", "mixed", "live", "acoustic", "acustico", "acustica", "instrumental",
        "karaoke", "cover", "demo", "sped", "slowed", "reverb", "edit", "portuguese", "portugues", "spanish",
        "espanol", "english", "ingles", "french", "italian", "german", "mandarin", "korean", "japanese", "hindi",
    )

    /**
     * Whether a hit's brackets or dash make it a different recording from the one asked for. Asked for
     * "Despacito", the catalogue's first hit is "Despacito (Versión Portugués)", of the same length, and
     * it won on the strength of everything outside its brackets: the song played in Spanish while
     * Portuguese lyrics scrolled by.
     */
    fun isOtherVersion(wantedTitle: String, hitTitle: String): Boolean {
        val wanted = normalized(wantedTitle).split(' ').toSet()
        val extras = bracketedPart.findAll(hitTitle).joinToString(" ") { it.value } + " " +
            hitTitle.replace(bracketedPart, " ").substringAfter(" - ", "")
        return normalized(extras).split(' ').any { it in otherVersionWords && it !in wanted }
    }

    private val bracketedPart = Regex("""[(\[][^)\]]*[)\]]""")
    private val combiningMarks = Regex("""\p{Mn}+""")
    private val artistSeparators = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Letters and digits only, lower case, accents dropped: "Don’t Smile" and "Don't Smile" agree. */
    fun normalized(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(java.util.Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun coreOf(title: String): String =
        normalized(cleanTitle(title).replace(bracketedPart, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    /**
     * Whether a lyrics index's hit is the song asked for: the same title outside its brackets, and
     * an artist in common. An index that searches by name ranks by its own idea of close, and
     * "Don't Smile" by somebody else, the same length give or take, came back first and was shown
     * as Sabrina Carpenter's. A hit that names no artist is judged on the title alone.
     */
    fun sameSong(title: String, artist: String, hitTitle: String?, hitArtist: String?): Boolean {
        val wanted = coreOf(title)
        val got = coreOf(hitTitle ?: return false)
        if (wanted.isEmpty() || got.isEmpty()) return false
        if (isOtherVersion(title, hitTitle)) return false
        val titleMatches = wanted == got ||
            (minOf(wanted.length, got.length) >= 4 && (wanted.startsWith(got) || got.startsWith(wanted)))
        if (!titleMatches) return false
        if (hitArtist.isNullOrBlank() || artist.isBlank()) return true
        val wantedArtists = artist.split(artistSeparators).map(::normalized).filter { it.isNotEmpty() }
        val gotArtists = hitArtist.split(artistSeparators).map(::normalized).filter { it.isNotEmpty() }
        return wantedArtists.any { a -> gotArtists.any { b -> a == b || a.contains(b) || b.contains(a) } }
    }
}
