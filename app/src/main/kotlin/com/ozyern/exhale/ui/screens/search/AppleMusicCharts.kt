/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The Suggestion tab's data follows Convx 1.5.2 (github.com/cosmictaserdev-creator/Convx, GPL-3.0):
 * Apple's public most-played charts, read from its marketing RSS feed.
 */

package com.ozyern.exhale.ui.screens.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One entry of a chart: a song, an album or a video, with its artwork. */
data class ChartEntry(
    val name: String,
    val artist: String,
    val artworkUrl: String?,
)

/** What the Suggestion tab shows: the most-played songs, albums and videos in a country. */
data class ChartPage(
    val songs: List<ChartEntry>,
    val albums: List<ChartEntry>,
    val videos: List<ChartEntry>,
) {
    /** The artists the top songs are by, most-charted first. */
    val artists: List<ChartEntry>
        get() = songs.groupBy { it.artist.substringBefore(",").substringBefore(" & ").trim() }
            .entries
            .sortedByDescending { it.value.size }
            .take(12)
            .map { (artist, entries) -> ChartEntry(artist, artist, entries.first().artworkUrl) }
}

object AppleMusicCharts {
    @Volatile
    private var cached: Pair<String, ChartPage>? = null

    /** The charts for the phone's own country, falling back to the US where Apple has none. */
    suspend fun load(country: String = Locale.getDefault().country.lowercase(Locale.ROOT).ifBlank { "us" }): ChartPage? =
        withContext(Dispatchers.IO) {
            cached?.takeIf { it.first == country }?.second?.let { return@withContext it }
            val page = fetch(country) ?: if (country != "us") fetch("us") else null
            if (page != null) cached = country to page
            page
        }

    private fun fetch(country: String): ChartPage? {
        val songs = feed(country, "songs", 50)
        if (songs.isEmpty()) return null
        return ChartPage(
            songs = songs,
            albums = feed(country, "albums", 25),
            videos = feed(country, "music-videos", 20),
        )
    }

    private fun feed(country: String, kind: String, count: Int): List<ChartEntry> = runCatching {
        val url = URL("https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/$count/$kind.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            if (connection.responseCode != 200) return@runCatching emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).getJSONObject("feed").getJSONArray("results")
            List(results.length()) { i ->
                val item = results.getJSONObject(i)
                ChartEntry(
                    name = item.optString("name"),
                    artist = item.optString("artistName"),
                    // Apple serves any size from the same path; 100x100 is far too small for a tile.
                    artworkUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() }
                        ?.replace("100x100bb", "400x400bb"),
                )
            }.filter { it.name.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())
}
