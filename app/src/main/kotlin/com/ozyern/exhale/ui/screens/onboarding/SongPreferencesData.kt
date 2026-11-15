/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.onboarding

import androidx.compose.runtime.Immutable

/**
 * Static data backing the two-step Song Preferences onboarding flow.
 *
 * Step 1 presents [OnboardingLanguages]; the language codes reuse the app-wide
 * [com.ozyern.exhale.constants.LanguageCodeToName] vocabulary so a saved selection round-trips
 * cleanly into content-language settings. Step 2 is *contextual*: the union of
 * [TrendingArtistsByLanguage] for the chosen languages is shown as a selectable grid.
 *
 * Artist artwork is intentionally URL-driven (Coil handles it) so the catalog stays a pure data
 * table with no drawable resources to ship — trending rosters change often and are cheap to edit.
 */

@Immutable
data class OnboardingLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
)

@Immutable
data class OnboardingArtist(
    val name: String,
    val imageUrl: String?,
    val languageCode: String,
)

/** Curated, ordered list of languages surfaced in Step 1 (headline set). */
val OnboardingLanguages: List<OnboardingLanguage> = listOf(
    OnboardingLanguage("en", "English", "English", "🇺🇸"),
    OnboardingLanguage("hi", "Hindi", "हिन्दी", "🇮🇳"),
    OnboardingLanguage("es", "Spanish", "Español", "🇪🇸"),
    OnboardingLanguage("ko", "Korean", "한국어", "🇰🇷"),
    OnboardingLanguage("ja", "Japanese", "日本語", "🇯🇵"),
    OnboardingLanguage("pt", "Portuguese", "Português", "🇧🇷"),
    OnboardingLanguage("fr", "French", "Français", "🇫🇷"),
    OnboardingLanguage("de", "German", "Deutsch", "🇩🇪"),
    OnboardingLanguage("ar", "Arabic", "العربية", "🇸🇦"),
    OnboardingLanguage("it", "Italian", "Italiano", "🇮🇹"),
    OnboardingLanguage("ru", "Russian", "Русский", "🇷🇺"),
    OnboardingLanguage("tr", "Turkish", "Türkçe", "🇹🇷"),
    OnboardingLanguage("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
    OnboardingLanguage("th", "Thai", "ไทย", "🇹🇭"),
    OnboardingLanguage("zh", "Chinese", "中文", "🇨🇳"),
    OnboardingLanguage("pl", "Polish", "Polski", "🇵🇱"),
)

/**
 * Trending artists per language. Kept deliberately small & recognizable per language so Step 2
 * feels curated rather than exhaustive. `imageUrl` may be null — the tile degrades to a monogram.
 */
val TrendingArtistsByLanguage: Map<String, List<OnboardingArtist>> = mapOf(
    "en" to listOf(
        OnboardingArtist("Sabrina Carpenter", null, "en"),
        OnboardingArtist("Taylor Swift", null, "en"),
        OnboardingArtist("The Weeknd", null, "en"),
        OnboardingArtist("Billie Eilish", null, "en"),
        OnboardingArtist("Drake", null, "en"),
        OnboardingArtist("Ariana Grande", null, "en"),
        OnboardingArtist("Kendrick Lamar", null, "en"),
        OnboardingArtist("Dua Lipa", null, "en"),
        OnboardingArtist("Post Malone", null, "en"),
        OnboardingArtist("Olivia Rodrigo", null, "en"),
        OnboardingArtist("Ed Sheeran", null, "en"),
        OnboardingArtist("Bruno Mars", null, "en"),
    ),
    "hi" to listOf(
        OnboardingArtist("Arijit Singh", null, "hi"),
        OnboardingArtist("Shreya Ghoshal", null, "hi"),
        OnboardingArtist("A.R. Rahman", null, "hi"),
        OnboardingArtist("Neha Kakkar", null, "hi"),
        OnboardingArtist("Pritam", null, "hi"),
        OnboardingArtist("Badshah", null, "hi"),
        OnboardingArtist("Jubin Nautiyal", null, "hi"),
        OnboardingArtist("Sonu Nigam", null, "hi"),
        OnboardingArtist("Diljit Dosanjh", null, "hi"),
        OnboardingArtist("Sunidhi Chauhan", null, "hi"),
    ),
    "es" to listOf(
        OnboardingArtist("Bad Bunny", null, "es"),
        OnboardingArtist("Karol G", null, "es"),
        OnboardingArtist("Peso Pluma", null, "es"),
        OnboardingArtist("Shakira", null, "es"),
        OnboardingArtist("Rauw Alejandro", null, "es"),
        OnboardingArtist("Feid", null, "es"),
        OnboardingArtist("Rosalía", null, "es"),
        OnboardingArtist("J Balvin", null, "es"),
        OnboardingArtist("Peso Pluma", null, "es"),
        OnboardingArtist("Maluma", null, "es"),
    ),
    "ko" to listOf(
        OnboardingArtist("BTS", null, "ko"),
        OnboardingArtist("BLACKPINK", null, "ko"),
        OnboardingArtist("NewJeans", null, "ko"),
        OnboardingArtist("SEVENTEEN", null, "ko"),
        OnboardingArtist("Stray Kids", null, "ko"),
        OnboardingArtist("IVE", null, "ko"),
        OnboardingArtist("aespa", null, "ko"),
        OnboardingArtist("TWICE", null, "ko"),
        OnboardingArtist("IU", null, "ko"),
        OnboardingArtist("LE SSERAFIM", null, "ko"),
    ),
    "ja" to listOf(
        OnboardingArtist("YOASOBI", null, "ja"),
        OnboardingArtist("Kenshi Yonezu", null, "ja"),
        OnboardingArtist("Ado", null, "ja"),
        OnboardingArtist("Fujii Kaze", null, "ja"),
        OnboardingArtist("Official HIGE DANdism", null, "ja"),
        OnboardingArtist("King Gnu", null, "ja"),
        OnboardingArtist("Aimer", null, "ja"),
        OnboardingArtist("LiSA", null, "ja"),
    ),
    "pt" to listOf(
        OnboardingArtist("Anitta", null, "pt"),
        OnboardingArtist("Luísa Sonza", null, "pt"),
        OnboardingArtist("Marília Mendonça", null, "pt"),
        OnboardingArtist("Henrique & Juliano", null, "pt"),
        OnboardingArtist("Jorge & Mateus", null, "pt"),
        OnboardingArtist("Ludmilla", null, "pt"),
    ),
    "fr" to listOf(
        OnboardingArtist("Aya Nakamura", null, "fr"),
        OnboardingArtist("Gims", null, "fr"),
        OnboardingArtist("Ninho", null, "fr"),
        OnboardingArtist("Angèle", null, "fr"),
        OnboardingArtist("Stromae", null, "fr"),
        OnboardingArtist("PNL", null, "fr"),
    ),
    "de" to listOf(
        OnboardingArtist("Rammstein", null, "de"),
        OnboardingArtist("Apache 207", null, "de"),
        OnboardingArtist("RAF Camora", null, "de"),
        OnboardingArtist("Peter Fox", null, "de"),
        OnboardingArtist("Ayliva", null, "de"),
    ),
    "ar" to listOf(
        OnboardingArtist("Amr Diab", null, "ar"),
        OnboardingArtist("Elissa", null, "ar"),
        OnboardingArtist("Nancy Ajram", null, "ar"),
        OnboardingArtist("Tamer Hosny", null, "ar"),
    ),
    "it" to listOf(
        OnboardingArtist("Måneskin", null, "it"),
        OnboardingArtist("Lazza", null, "it"),
        OnboardingArtist("Geolier", null, "it"),
        OnboardingArtist("Marco Mengoni", null, "it"),
    ),
    "ru" to listOf(
        OnboardingArtist("Miyagi", null, "ru"),
        OnboardingArtist("Morgenshtern", null, "ru"),
        OnboardingArtist("Zivert", null, "ru"),
        OnboardingArtist("Egor Kreed", null, "ru"),
    ),
    "tr" to listOf(
        OnboardingArtist("Tarkan", null, "tr"),
        OnboardingArtist("Sezen Aksu", null, "tr"),
        OnboardingArtist("Murda", null, "tr"),
        OnboardingArtist("Mabel Matiz", null, "tr"),
    ),
    "id" to listOf(
        OnboardingArtist("Tulus", null, "id"),
        OnboardingArtist("Raisa", null, "id"),
        OnboardingArtist("Rizky Febian", null, "id"),
        OnboardingArtist("Mahalini", null, "id"),
    ),
    "th" to listOf(
        OnboardingArtist("Bodyslam", null, "th"),
        OnboardingArtist("Three Man Down", null, "th"),
        OnboardingArtist("Bowkylion", null, "th"),
    ),
    "zh" to listOf(
        OnboardingArtist("Jay Chou", null, "zh"),
        OnboardingArtist("JJ Lin", null, "zh"),
        OnboardingArtist("Eason Chan", null, "zh"),
        OnboardingArtist("G.E.M.", null, "zh"),
    ),
    "pl" to listOf(
        OnboardingArtist("Sanah", null, "pl"),
        OnboardingArtist("Dawid Podsiadło", null, "pl"),
        OnboardingArtist("Mata", null, "pl"),
    ),
)

/** Union of trending artists for the given [languageCodes], de-duplicated by name, order-stable. */
fun artistsForLanguages(languageCodes: Collection<String>): List<OnboardingArtist> {
    val seen = HashSet<String>()
    val out = ArrayList<OnboardingArtist>()
    for (code in languageCodes) {
        for (artist in TrendingArtistsByLanguage[code].orEmpty()) {
            if (seen.add(artist.name)) out.add(artist)
        }
    }
    return out
}

/**
 * Process-lifetime cache of resolved artist artwork URLs, keyed by artist name.
 *
 * The static [TrendingArtistsByLanguage] table ships no artwork (rosters change too often to
 * hand-maintain image URLs), so Step 2 resolves each artist's real thumbnail on demand via the
 * InnerTube artist search and memoizes the result here. A cached value of `null` means "looked up,
 * none found" and stops us from re-hitting the network for that artist within the session.
 */
object OnboardingArtistImageCache {
    // Backed by a plain map; access is confined to the main thread from the composable resolver.
    private val cache = HashMap<String, String?>()

    fun contains(name: String): Boolean = cache.containsKey(name)

    fun get(name: String): String? = cache[name]

    fun put(name: String, url: String?) {
        cache[name] = url
    }
}
