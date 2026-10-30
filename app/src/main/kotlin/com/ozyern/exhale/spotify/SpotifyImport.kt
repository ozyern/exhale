/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Playlist import from a pasted link follows SpotiFLAC Mobile's import
 * (github.com/spotiflacapp/SpotiFLAC-Mobile, MIT).
 */

package com.ozyern.exhale.spotify

import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.PlaylistEntity
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * A Spotify playlist, brought into the library as a playlist of its own.
 *
 * No Spotify account is needed: the playlist's public embed page carries its name and, for each
 * track, the title, the artists and the length. Each is then found on YouTube Music — the result
 * whose title and artist agree and whose length is within a few seconds, so a live cut or a
 * different version is not taken for the record — and the playlist is saved with every match.
 */
object SpotifyImport {
    data class Track(val title: String, val artists: String, val durationMs: Long)
    data class Source(val name: String, val tracks: List<Track>)
    data class Result(val playlistId: String, val name: String, val matched: Int, val total: Int)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    }

    private val playlistPattern = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(?:embed/)?playlist/([A-Za-z0-9]{10,})""")

    /** The playlist id in a Spotify link, or null when it is not a playlist link. */
    fun playlistId(link: String): String? = playlistPattern.find(link)?.groupValues?.get(1)

    suspend fun fetch(playlistId: String): Source? = withContext(Dispatchers.IO) {
        runCatching {
            val html = client.newCall(
                Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$playlistId")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
                    .header("Accept-Language", "en")
                    .build(),
            ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@runCatching null
            val json = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: return@runCatching null
            val entity = JSONObject(json).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("state").getJSONObject("data").getJSONObject("entity")
            val list = entity.optJSONArray("trackList") ?: return@runCatching null
            val tracks = (0 until list.length()).mapNotNull { i ->
                val t = list.optJSONObject(i) ?: return@mapNotNull null
                val title = t.optString("title")
                if (title.isBlank()) null else Track(title, t.optString("subtitle"), t.optLong("duration"))
            }
            Source(entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Spotify playlist" }, tracks)
        }.getOrNull()
    }

    /**
     * Finds every track and saves the playlist. [onProgress] hears (done, total) as tracks are
     * looked up, four at a time.
     */
    suspend fun import(
        database: MusicDatabase,
        source: Source,
        onProgress: (Int, Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val done = AtomicInteger(0)
        val gate = Semaphore(4)
        val matches = coroutineScope {
            source.tracks.map { track ->
                async {
                    gate.withPermit {
                        runCatching { match(track) }.getOrNull().also {
                            onProgress(done.incrementAndGet(), source.tracks.size)
                        }
                    }
                }
            }.awaitAll()
        }
        // In the playlist's own order, once each.
        val songs = matches.filterNotNull().distinctBy { it.id }
        val metadata = songs.map { it.toMediaMetadata() }
        // Saved, so it is in the Library: unsaved, the import made it and the Library never listed it.
        val playlist = PlaylistEntity(name = source.name, bookmarkedAt = java.time.LocalDateTime.now())
        database.transaction {
            metadata.forEach(::insert)
            insert(playlist)
        }
        database.playlist(playlist.id).firstOrNull()?.let { database.addSongToPlaylist(it, metadata.map { m -> m.id }) }
        Result(playlist.id, source.name, songs.size, source.tracks.size)
    }

    private suspend fun match(track: Track): SongItem? {
        val artist = track.artists.split(',', '&').firstOrNull()?.trim().orEmpty()
        val results = YouTube.search("${track.title} $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>().orEmpty()
        val title = norm(track.title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").substringBefore(" - "))
        val artistWords = norm(track.artists).split(' ').filter { it.length > 2 }.toSet()
        val seconds = track.durationMs / 1000
        fun score(song: SongItem): Int {
            val name = norm(song.title)
            val by = norm(song.artists.joinToString(" ") { it.name })
            var s = 0
            if (name == title) s += 3 else if (name.contains(title) || title.contains(name)) s += 2 else return -1
            if (artistWords.isEmpty() || artistWords.any { it in by }) s += 2
            val d = song.duration
            if (d != null && seconds > 0) {
                val off = abs(d - seconds)
                if (off <= 3) s += 2 else if (off <= 10) s += 1 else if (off > 45) s -= 2
            }
            return s
        }
        return results.take(6).map { it to score(it) }.filter { it.second >= 3 }.maxByOrNull { it.second }?.first
    }

    private fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
