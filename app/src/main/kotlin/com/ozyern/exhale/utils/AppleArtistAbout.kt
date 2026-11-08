/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * What the iOS music app's artist About page says: where they are from, when they were born (or the
 * band formed), their genres and Apple's own biography.
 *
 * [born] is already written out for display ("11 May 1999", or a year alone). Any field Apple does
 * not give is null; the page leaves its row out.
 */
data class AppleArtistAbout(
    val origin: String?,
    val born: String?,
    val isGroup: Boolean,
    val genres: List<String>,
    val bio: String?,
) {
    val isEmpty: Boolean get() = origin == null && born == null && genres.isEmpty() && bio.isNullOrBlank()
}

/**
 * Finds [AppleArtistAbout] from an artist's name, the same way [AppleArtistArtRepository] finds the
 * header art.
 *
 *  1. The public iTunes search turns the name into Apple's artist id and primary genre, and only an
 *     answer whose name is the name asked for is taken.
 *  2. The music.apple.com artist page carries the biography in the JSON it embeds.
 *  3. Apple's catalogue, on the token the app already uses for Apple Music, adds the origin, the
 *     birth or formation date and the full genre list. Without a token those rows are simply absent.
 */
object AppleArtistAboutRepository {
    private const val CACHE_SIZE = 48
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    }

    private class Entry(val about: AppleArtistAbout?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    /** What is already known for [artistName], without a request. */
    fun cached(artistName: String): AppleArtistAbout? =
        synchronized(cache) { cache[artistName.normalized()] }?.about

    /** The About page for [artistName], or null when Apple has nothing. Never throws. */
    suspend fun aboutFor(artistName: String): AppleArtistAbout? {
        val key = artistName.normalized()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[key] }?.let { return it.about }
        // Null here is a network hiccup, not an answer: only a settled result is remembered.
        val entry = withContext(Dispatchers.IO) { runCatching { lookup(artistName, key) }.getOrNull() } ?: return null
        synchronized(cache) { cache[key] = entry }
        return entry.about
    }

    private suspend fun lookup(artistName: String, key: String): Entry? {
        val search = get(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}&entity=musicArtist&limit=5",
        ) ?: return null
        val results = JSONObject(search).optJSONArray("results") ?: JSONArray()
        var match: JSONObject? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").normalized() == key) {
                match = r
                break
            }
        }
        match ?: return Entry(null)
        val artistId = match.optLong("artistId").takeIf { it > 0 } ?: return Entry(null)
        val primaryGenre = match.optString("primaryGenreName").takeIf { it.isNotBlank() }
        val page = match.optString("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
            ?: "https://music.apple.com/us/artist/$artistId"

        val pageBio = runCatching { get(page)?.let(::bioFromPage) }.getOrNull()
        val catalog = runCatching { catalogAttributes(artistId) }.getOrNull()

        val genres = catalog?.optJSONArray("genreNames")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { g -> g.isNotBlank() && g != "Music" } }
        }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(primaryGenre)
        val isGroup = catalog?.optBoolean("isGroup", false) ?: false
        val about = AppleArtistAbout(
            origin = catalog?.optString("origin")?.takeIf { it.isNotBlank() },
            born = catalog?.optString("bornOrFormed")?.takeIf { it.isNotBlank() }?.let(::formatDate),
            isGroup = isGroup,
            genres = genres,
            bio = (pageBio ?: catalog?.optString("artistBio"))?.trim()?.takeIf { it.isNotBlank() },
        )
        return Entry(about.takeUnless { it.isEmpty })
    }

    /** The artist's attributes from Apple's catalogue, with the About fields the web player asks for. */
    private suspend fun catalogAttributes(artistId: Long): JSONObject? {
        val token = com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicProvider.currentDevToken()
            ?: return null
        val url = "https://amp-api.music.apple.com/v1/catalog/us/artists/$artistId" +
            "?extend=artistBio,bornOrFormed,origin,isGroup&l=en-US"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            // Apple answers a token without these the way it answers a wrong one.
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .build()
        val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        return JSONObject(body).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes")
    }

    /** The biography the artist page embeds for hydration: the first `bio` string anywhere in it. */
    private fun bioFromPage(html: String): String? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        return findBio(JSONObject("{\"d\":${html.substring(start, end)}}").get("d"))
    }

    private fun findBio(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optString("bio").takeIf { node.opt("bio") is String && it.isNotBlank() }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findBio(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findBio(node.opt(i))?.let { return it }
        }
        return null
    }

    /** "1999-05-11" as "11 May 1999"; a bare year stays a year. */
    internal fun formatDate(raw: String): String = runCatching {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }.getOrElse { raw.take(4).takeIf { year -> year.all(Char::isDigit) } ?: raw }

    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    /** Case, accents and punctuation are not what makes two names different. */
    private fun String.normalized(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
}
