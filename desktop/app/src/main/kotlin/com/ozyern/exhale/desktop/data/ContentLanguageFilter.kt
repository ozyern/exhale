/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.pages.HomePage

/**
 * Port of the Android app's ContentLanguageFilter.
 *
 * `hl`/`gl` and Accept-Language are all sent, and YouTube Music *still* builds parts of its feed
 * from the caller's IP region — so everything that comes from YouTube is checked against the
 * listener's languages on the way in: script of every title/artist/album, regional market names
 * ("India's biggest hits"), and romanized words that give a Latin-script title away ("Tum Hi Ho").
 */
object ContentLanguageFilter {
    private enum class Script { LATIN, DEVANAGARI, BENGALI, GURMUKHI, GUJARATI, TAMIL, TELUGU, KANNADA, MALAYALAM, SINHALA, ARABIC, CYRILLIC, GREEK, HEBREW, THAI, CJK, HANGUL, KANA, OTHER }

    private fun scriptsFor(lang: String): Set<Script> = when (lang.lowercase().substringBefore("-")) {
        "hi", "mr", "ne", "sa" -> setOf(Script.DEVANAGARI, Script.LATIN)
        "bn", "as" -> setOf(Script.BENGALI, Script.LATIN)
        "pa" -> setOf(Script.GURMUKHI, Script.LATIN)
        "gu" -> setOf(Script.GUJARATI, Script.LATIN)
        "ta" -> setOf(Script.TAMIL, Script.LATIN)
        "te" -> setOf(Script.TELUGU, Script.LATIN)
        "kn" -> setOf(Script.KANNADA, Script.LATIN)
        "ml" -> setOf(Script.MALAYALAM, Script.LATIN)
        "si" -> setOf(Script.SINHALA, Script.LATIN)
        "ar", "fa", "ur" -> setOf(Script.ARABIC, Script.LATIN)
        "ru", "uk", "be", "bg", "sr", "mk" -> setOf(Script.CYRILLIC, Script.LATIN)
        "el" -> setOf(Script.GREEK, Script.LATIN)
        "iw", "he" -> setOf(Script.HEBREW, Script.LATIN)
        "th" -> setOf(Script.THAI, Script.LATIN)
        "zh" -> setOf(Script.CJK, Script.LATIN)
        "ja" -> setOf(Script.KANA, Script.CJK, Script.LATIN)
        "ko" -> setOf(Script.HANGUL, Script.LATIN)
        else -> setOf(Script.LATIN)
    }

    private fun scriptOf(codePoint: Int): Script = when (codePoint) {
        in 0x0041..0x024F, in 0x1E00..0x1EFF -> Script.LATIN
        in 0x0900..0x097F -> Script.DEVANAGARI
        in 0x0980..0x09FF -> Script.BENGALI
        in 0x0A00..0x0A7F -> Script.GURMUKHI
        in 0x0A80..0x0AFF -> Script.GUJARATI
        in 0x0B80..0x0BFF -> Script.TAMIL
        in 0x0C00..0x0C7F -> Script.TELUGU
        in 0x0C80..0x0CFF -> Script.KANNADA
        in 0x0D00..0x0D7F -> Script.MALAYALAM
        in 0x0D80..0x0DFF -> Script.SINHALA
        in 0x0600..0x06FF, in 0x0750..0x077F -> Script.ARABIC
        in 0x0400..0x04FF -> Script.CYRILLIC
        in 0x0370..0x03FF -> Script.GREEK
        in 0x0590..0x05FF -> Script.HEBREW
        in 0x0E00..0x0E7F -> Script.THAI
        in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> Script.CJK
        in 0xAC00..0xD7AF -> Script.HANGUL
        in 0x3040..0x30FF -> Script.KANA
        else -> Script.OTHER
    }

    private val regionalMarkers: Map<String, List<String>> = mapOf(
        "hi" to listOf("hindi", "bollywood", "india", "indian", "desi", "filmi"),
        "pa" to listOf("punjabi", "bhangra"),
        "ta" to listOf("tamil", "kollywood"),
        "te" to listOf("telugu", "tollywood"),
        "bn" to listOf("bengali", "bangla"),
        "mr" to listOf("marathi"),
        "gu" to listOf("gujarati"),
        "kn" to listOf("kannada"),
        "ml" to listOf("malayalam", "mollywood"),
        "bho" to listOf("bhojpuri"),
        "ur" to listOf("urdu", "pakistani", "pakistan"),
        "ar" to listOf("arabic", "khaleeji"),
        "es" to listOf("latino", "reggaeton", "música mexicana"),
        "pt" to listOf("sertanejo", "funk brasileiro", "brasil hits"),
        "ko" to listOf("k-pop", "kpop", "korean"),
        "ja" to listOf("j-pop", "jpop", "japanese"),
        "zh" to listOf("c-pop", "cpop", "mandopop", "cantopop"),
        "tr" to listOf("turkish", "türkçe"),
        "th" to listOf("thai", "t-pop"),
        "vi" to listOf("v-pop", "vpop", "vietnamese"),
        "id" to listOf("dangdut", "indonesian"),
        "ru" to listOf("russian", "russkaya"),
    )

    private val romanizedMarkers: Map<String, List<String>> = mapOf(
        "hi" to listOf(
            "ishq", "pyaar", "pyar", "mohabbat", "dil", "dilbar", "deewana", "deewani", "bewafa",
            "zindagi", "sanam", "saathiya", "jaanam", "tere", "tera", "teri", "mera", "meri",
            "mere", "tum", "tumhe", "tujhe", "mujhe", "humko", "tumko", "yaad", "raat", "chaand",
            "sapna", "aashiqui", "kabhi", "dhadkan", "jaan", "aankhen", "aankhon", "baarish",
            "chaleya", "kesariya", "judaai", "intezaar", "khuda", "maahi", "mahiya", "piya",
            "sajna", "sajni", "bulleya", "naina", "saiyaan", "banna", "rangisari",
        ),
        "pa" to listOf(
            "yaara", "kudi", "munda", "sohna", "sohni", "laung", "gallan", "nachde", "jatt",
            "jatti", "pind", "gabru", "chann", "ranjha", "heer", "satrangi", "laachi", "sardaar",
        ),
        "ur" to listOf("aashiq", "dilruba", "bekhayali", "qarar", "tanha", "wafa"),
    )

    private val wordBoundary = Regex("[^\\p{L}\\p{N}]+")

    private fun mentionsForeignLanguageWord(text: String, languages: Set<String>): Boolean {
        val words = text.lowercase().split(wordBoundary).filterTo(HashSet()) { it.isNotEmpty() }
        if (words.isEmpty()) return false
        return romanizedMarkers.any { (lang, markers) -> lang !in languages && markers.any { it in words } }
    }

    private fun mentionsForeignRegion(text: String, languages: Set<String>): Boolean {
        val lower = text.lowercase()
        return regionalMarkers.any { (lang, markers) -> lang !in languages && markers.any { it in lower } }
    }

    fun textMatches(text: String, languages: Set<String>): Boolean {
        if (languages.isEmpty()) return true
        if (mentionsForeignRegion(text, languages)) return false
        if (mentionsForeignLanguageWord(text, languages)) return false
        val allowed = languages.flatMapTo(HashSet()) { scriptsFor(it) }
        var matched = 0
        var lettered = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val script = scriptOf(cp)
            if (script != Script.OTHER) {
                lettered++
                if (script in allowed) matched++
            }
            i += Character.charCount(cp)
        }
        if (lettered == 0) return true
        return matched * 10 >= lettered * 7
    }

    private fun itemMatches(item: YTItem, languages: Set<String>): Boolean {
        if (!textMatches(item.title, languages)) return false
        return when (item) {
            is SongItem -> item.artists.all { textMatches(it.name, languages) } &&
                (item.album?.name?.let { textMatches(it, languages) } != false)
            is AlbumItem -> item.artists.orEmpty().all { textMatches(it.name, languages) }
            is PlaylistItem -> (item.author?.name?.let { textMatches(it, languages) } != false) &&
                (item.description?.let { textMatches(it, languages) } != false)
            is ArtistItem -> true
        }
    }

    fun <T : YTItem> filterItems(items: List<T>, languages: Set<String>): List<T> =
        if (languages.isEmpty()) items else items.filter { itemMatches(it, languages) }

    fun filterHomeSections(sections: List<HomePage.Section>, languages: Set<String>): List<HomePage.Section> {
        if (languages.isEmpty()) return sections
        return sections.mapNotNull { section ->
            if (!textMatches(section.title, languages)) return@mapNotNull null
            val kept = filterItems(section.items, languages)
            if (kept.isEmpty()) null else section.copy(items = kept)
        }
    }
}
