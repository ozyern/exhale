/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The idea — paste a link from any streaming service and get the same music here — follows
 * SpotiFLAC Mobile's link import (github.com/spotiflacapp/SpotiFLAC-Mobile, MIT).
 */

package com.ozyern.exhale.utils

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A link from another music service, pasted into search, turned into the same song or album here.
 *
 * Each service is asked in the way it answers without an account: Apple through the public iTunes
 * lookup, Deezer through its public API, YouTube from the link itself, and everyone else — Spotify,
 * Tidal, SoundCloud, Amazon — from the page's own link-preview tags, the title and artist a chat
 * app shows when the link is shared. The title and artist are then found on YouTube Music.
 */
object MusicLinks {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    private val linkPattern = Regex(
        """^(https?://)?([a-z0-9-]+\.)*(spotify\.com|spotify\.link|apple\.com|deezer\.com|deezer\.page\.link|tidal\.com|soundcloud\.com|amazon\.[a-z.]+|youtube\.com|youtu\.be)(/|$)""",
        RegexOption.IGNORE_CASE,
    )

    /** Whether the query is a link this can follow. */
    fun isLink(query: String): Boolean = linkPattern.containsMatchIn(query.trim())

    private data class Ref(val title: String, val artist: String, val album: Boolean)

    suspend fun resolve(query: String): YTItem? = withContext(Dispatchers.IO) {
        val raw = query.trim().let { if (it.startsWith("http", true)) it else "https://$it" }
        val url = raw.toHttpUrlOrNull() ?: return@withContext null
        val host = url.host.lowercase(Locale.ROOT)

        // YouTube needs no lookup: the link carries the id.
        if (host.endsWith("youtube.com") || host == "youtu.be") {
            val id = url.queryParameter("v") ?: url.pathSegments.firstOrNull()?.takeIf { host == "youtu.be" }
            if (id != null) return@withContext YouTube.queue(listOf(id)).getOrNull()?.firstOrNull()
            return@withContext null
        }

        val ref = runCatching {
            when {
                host.endsWith("apple.com") -> apple(url)
                host.endsWith("deezer.com") -> deezer(url)
                else -> page(url)
            }
        }.getOrNull() ?: return@withContext null
        find(ref)
    }

    private fun apple(url: okhttp3.HttpUrl): Ref? {
        val trackId = url.queryParameter("i")
        val id = trackId ?: url.pathSegments.lastOrNull { it.all(Char::isDigit) } ?: return page(url)
        val body = get("https://itunes.apple.com/lookup?id=$id") ?: return page(url)
        val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return page(url)
        val album = trackId == null && result.optString("wrapperType") == "collection"
        val title = if (album) result.optString("collectionName") else result.optString("trackName")
        return Ref(title, result.optString("artistName"), album).takeIf { it.title.isNotBlank() }
    }

    private fun deezer(url: okhttp3.HttpUrl): Ref? {
        val segments = url.pathSegments
        val kind = segments.firstOrNull { it == "track" || it == "album" } ?: return page(url)
        val id = segments.getOrNull(segments.indexOf(kind) + 1) ?: return page(url)
        val json = JSONObject(get("https://api.deezer.com/$kind/$id") ?: return page(url))
        return Ref(json.optString("title"), json.optJSONObject("artist")?.optString("name").orEmpty(), kind == "album")
            .takeIf { it.title.isNotBlank() }
    }

    /** The link-preview tags every service serves: og:title, og:description. */
    private fun page(url: okhttp3.HttpUrl): Ref? {
        val html = get(url.toString(), mobile = true) ?: return null
        fun meta(name: String): String? =
            Regex("""<meta[^>]+(?:property|name)="$name"[^>]+content="([^"]*)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content="([^"]*)"[^>]+(?:property|name)="$name"""").find(html)?.groupValues?.get(1)
        val ogTitle = meta("og:title")?.let(::unescape)?.takeIf { it.isNotBlank() } ?: return null
        val description = meta("og:description")?.let(::unescape).orEmpty()
        val album = url.encodedPath.contains("/album")
        // "Espresso" + "Sabrina Carpenter · Espresso · Song · 2024" (Spotify), "360 by Charli xcx on
        // the iOS music app", "Song - Artist" and the like elsewhere.
        val byMatch = Regex("""^(.*?) by (.*?)(?: on [A-Z].*)?$""").find(ogTitle)
        return when {
            " · " in description -> Ref(ogTitle, description.substringBefore(" · "), album)
            byMatch != null -> Ref(byMatch.groupValues[1], byMatch.groupValues[2], album)
            " - " in ogTitle -> Ref(ogTitle.substringBefore(" - "), ogTitle.substringAfter(" - ").substringBefore(" | "), album)
            else -> Ref(ogTitle, "", album)
        }
    }

    /** The same thing on YouTube Music: the first result whose title carries this one's. */
    private suspend fun find(ref: Ref): YTItem? {
        val query = "${ref.title} ${ref.artist}".trim()
        val title = norm(ref.title.replace(Regex("""\s*[(\[].*?[)\]]"""), ""))
        val artist = norm(ref.artist).split(' ').filter { it.length > 2 }.toSet()
        fun matches(name: String, by: String): Boolean {
            val n = norm(name)
            val b = norm(by)
            return (n.contains(title) || title.contains(n)) && (artist.isEmpty() || artist.any { it in b })
        }
        return if (ref.album) {
            val albums = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
                ?.filterIsInstance<AlbumItem>().orEmpty()
            albums.firstOrNull { matches(it.title, it.artists?.joinToString(" ") { a -> a.name }.orEmpty()) } ?: albums.firstOrNull()
        } else {
            val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                ?.filterIsInstance<SongItem>().orEmpty()
            songs.take(6).firstOrNull { matches(it.title, it.artists.joinToString(" ") { a -> a.name }) } ?: songs.firstOrNull()
        }
    }

    private fun get(url: String, mobile: Boolean = false): String? = runCatching {
        val request = Request.Builder().url(url)
            .header(
                "User-Agent",
                if (mobile) "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                else "Exhale (https://github.com/ozyern/exhale)",
            )
            .header("Accept-Language", "en")
            .build()
        client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    private fun unescape(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
