/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Finding a song from a line of it: "his palms are sweaty" → Lose Yourself.
 *
 * Genius's search reads lyrics as well as titles, and tried on lines typed the way people remember
 * them — misspelt, unpunctuated, Hindi in Latin letters — it put the right song first every time.
 * Each match is then found on YouTube Music, so what comes back plays like any other result.
 */
object LyricSearch {
    /** A song whose lyrics matched, and the line that did when Genius says which. */
    data class Match(val song: SongItem, val line: String?)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    }

    /** Only a phrase is worth asking about: one or two words are a title or a name. */
    fun looksLikeLyrics(query: String): Boolean = query.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 3

    suspend fun search(query: String, limit: Int = 3): List<Match> = withContext(Dispatchers.IO) {
        val hits = geniusHits(query).take(limit + 2)
        if (hits.isEmpty()) return@withContext emptyList()
        coroutineScope {
            hits.map { hit -> async { withTimeoutOrNull(6_000) { resolve(hit) } } }.awaitAll()
        }.filterNotNull().distinctBy { it.song.id }.take(limit)
    }

    private data class Hit(val title: String, val artist: String, val line: String?)

    private fun geniusHits(query: String): List<Hit> = runCatching {
        val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        val body = client.newCall(
            Request.Builder().url(url).header("User-Agent", "Exhale (https://github.com/ozyern/exhale)").build(),
        ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@runCatching emptyList()
        val sections = JSONObject(body).getJSONObject("response").getJSONArray("sections")
        val found = LinkedHashMap<String, Hit>()
        val typed = norm(query).split(' ').filter { it.length > 1 }.toSet()
        // Top hit first, then the lyric matches (which carry the matching line), then songs.
        for (wanted in listOf("top_hit", "lyric", "song")) {
            for (s in 0 until sections.length()) {
                val section = sections.getJSONObject(s)
                if (section.optString("type") != wanted) continue
                val list = section.optJSONArray("hits") ?: continue
                for (h in 0 until list.length()) {
                    val hit = list.getJSONObject(h)
                    val result = hit.optJSONObject("result") ?: continue
                    if (result.optString("_type") != "song") continue
                    // The highlight is a few lines around the match; the one shown is the line that
                    // shares the most words with what was typed, not simply the first of them.
                    val line = hit.optJSONArray("highlights")?.optJSONObject(0)?.optString("value")
                        ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList()
                        ?.maxByOrNull { candidate -> norm(candidate).split(' ').count { it in typed } }
                    val (title, artist) = unwrap(result.optString("title"), result.optString("artist_names"))
                    if (title.isBlank()) continue
                    val key = norm("$title $artist")
                    val previous = found[key]
                    if (previous == null) found[key] = Hit(title, artist, line)
                    else if (previous.line == null && line != null) found[key] = previous.copy(line = line)
                }
            }
        }
        found.values.toList()
    }.getOrDefault(emptyList())

    /**
     * Genius files transliterations under its own account, the song's credits folded into the title:
     * "Arijit Singh - Tum Hi Ho (Romanized)" by "Genius Romanizations". Unfolded, that is Tum Hi Ho by
     * Arijit Singh, which is what YouTube Music has.
     */
    private fun unwrap(title: String, artist: String): Pair<String, String> {
        if (!artist.startsWith("Genius ", ignoreCase = true) || " - " !in title) return title to artist
        val credits = title.substringBefore(" - ").trim()
        val song = title.substringAfter(" - ")
            .replace(Regex("""\s*[(\[](Romanized|English Translation|Traducción.*?|Traduction.*?)[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
        return song to credits.split(Regex("""\s*(,|&)\s*""")).first()
    }

    /** The match on YouTube Music: a song whose title carries the hit's, by someone in its credits. */
    private suspend fun resolve(hit: Hit): Match? {
        val results = YouTube.search("${hit.title} ${hit.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>().orEmpty()
        val title = norm(hit.title.replace(Regex("""\s*[(\[].*?[)\]]"""), ""))
        val artists = norm(hit.artist).split(' ').filter { it.length > 2 }.toSet()
        val song = results.take(5).firstOrNull { song ->
            val name = norm(song.title)
            val by = norm(song.artists.joinToString(" ") { it.name })
            (name.contains(title) || title.contains(name)) && (artists.isEmpty() || artists.any { it in by })
        } ?: return null
        return Match(song, hit.line)
    }

    private fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
