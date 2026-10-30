/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.RandomAccessFile
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.random.Random

/** Where a lossless copy of what's playing can come from. Exhale ships none of these — you add your own. */
enum class LosslessKind {
    /** A folder of files on this PC: FLAC, ALAC, WAV, AIFF, APE, WavPack. */
    FOLDER,

    /** A Subsonic-compatible server: Navidrome, Airsonic, Gonic, Jellyfin's Subsonic endpoint. */
    SUBSONIC,

    /** A source server speaking the addon protocol: /manifest.json, /search?q=, /stream/{id}. */
    ADDON,

    /** The Internet Archive: concerts taped with the band's blessing, and public-domain music. */
    ARCHIVE,

    /** Wikimedia Commons: public-domain and Creative Commons recordings, much of it FLAC or WAV. */
    WIKIMEDIA,

    /** Jamendo's Creative Commons catalogue, through a free client ID of your own. */
    JAMENDO,

    /** A Plex server of yours. */
    PLEX,

    /** A Jellyfin server of yours. */
    JELLYFIN,

    /** Whatever media servers answer on this network: a NAS, Plex's DLNA, Windows media sharing. */
    DLNA,
}

data class LosslessSource(
    val id: String,
    val kind: LosslessKind,
    val name: String,
    /** A folder path, or the server's base URL. */
    val location: String,
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
) {
    val kindLabel: String
        get() = when (kind) {
            LosslessKind.FOLDER -> "Folder on this PC"
            LosslessKind.SUBSONIC -> "Subsonic server"
            LosslessKind.ADDON -> "Addon server"
            LosslessKind.ARCHIVE -> "Internet Archive"
            LosslessKind.WIKIMEDIA -> "Wikimedia Commons"
            LosslessKind.JAMENDO -> "Jamendo"
            LosslessKind.PLEX -> "Plex server"
            LosslessKind.JELLYFIN -> "Jellyfin server"
            LosslessKind.DLNA -> "Servers on this network"
        }
}

sealed interface SourceHealth {
    data object Unknown : SourceHealth
    data object Checking : SourceHealth
    data class Ok(val detail: String) : SourceHealth
    data class Failed(val message: String) : SourceHealth
}

/** One candidate copy of a song, as a source described it. */
data class LosslessTrack(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?,
    val url: String,
    val format: String,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
    val sourceName: String,
) {
    val lossless: Boolean get() = format.lowercase(Locale.US) in LosslessFormats

    /** Bit depth × rate first, then bitrate: how good this copy is against another of the same song. */
    val rank: Long
        get() {
            val depth = (bitDepth ?: if (lossless) 16 else 0).toLong()
            val rate = (sampleRate ?: if (lossless) 44_100 else 0).toLong()
            return (if (lossless) 1L shl 60 else 0L) + depth * rate + (bitrate ?: 0).toLong()
        }
}

private val LosslessFormats = setOf("flac", "alac", "wav", "wave", "aiff", "aif", "ape", "wv", "dsf", "dff", "shn")
/** Dolby Digital, Dolby Digital Plus (the format Atmos music is streamed and sold in) and TrueHD. */
private val DolbyFormats = setOf("ac3", "ec3", "eac3", "thd", "mlp", "mka")
private val AudioExtensions = LosslessFormats + DolbyFormats + setOf("m4a", "mp3", "ogg", "opus", "aac")

/**
 * Lossless and hi-res playback, from sources the listener configures.
 *
 * Every source is theirs: a folder of files they own, a Subsonic server they run, or an addon server
 * they're entitled to use. Exhale has no built-in endpoints and fetches no remote code — a source is
 * only ever an address that was typed in here.
 *
 * When a song starts, every enabled source is asked at once for its own copy. Candidates are matched
 * against the song by title, artist and runtime, the best copy wins, and if nothing matches the song
 * plays from YouTube exactly as before.
 */
object LosslessLibrary {
    private const val KEY = "lossless_sources"
    private const val AUTO_KEY = "lossless_looked_in_music"
    private const val ARCHIVE_KEY = "lossless_archive_removed"
    private const val UserAgent = "Exhale (+https://github.com/ozyern/exhale)"

    /** Sources that cost nothing and need no account, so they're there from the first song. */
    private val BuiltIn = listOf(LosslessKind.ARCHIVE, LosslessKind.WIKIMEDIA, LosslessKind.DLNA)

    private fun removedKey(kind: LosslessKind): String =
        if (kind == LosslessKind.ARCHIVE) ARCHIVE_KEY else "lossless_removed_${kind.name.lowercase(Locale.US)}"

    private fun builtInName(kind: LosslessKind): String = when (kind) {
        LosslessKind.ARCHIVE -> "Internet Archive"
        LosslessKind.WIKIMEDIA -> "Wikimedia Commons"
        else -> "Servers on this network"
    }

    private fun builtInLocation(kind: LosslessKind): String = when (kind) {
        LosslessKind.ARCHIVE -> "archive.org"
        LosslessKind.WIKIMEDIA -> "commons.wikimedia.org"
        else -> "this network"
    }

    private val _sources = MutableStateFlow(load())
    val sources: StateFlow<List<LosslessSource>> = _sources.asStateFlow()

    private val _health = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val health: StateFlow<Map<String, SourceHealth>> = _health.asStateFlow()

    /** What the last resolve found, for the player's badge and the settings page. */
    private val _lastMatch = MutableStateFlow<String?>(null)
    val lastMatch: StateFlow<String?> = _lastMatch.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = _sources.value.any { it.enabled }

    fun add(source: LosslessSource) {
        _sources.value = _sources.value.filterNot { it.id == source.id } + source
        save()
    }

    fun remove(id: String) {
        val removed = _sources.value.firstOrNull { it.id == id }
        _sources.value = _sources.value.filterNot { it.id == id }
        _health.value = _health.value - id
        FolderIndex.forget(id)
        // Taken away on purpose: don't put it back on the next start.
        if (removed != null && removed.kind in BuiltIn) DesktopPrefs.putAll(mapOf(removedKey(removed.kind) to "removed"))
        save()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _sources.value = _sources.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        save()
    }

    fun move(id: String, by: Int) {
        val list = _sources.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val to = (from + by).coerceIn(0, list.size - 1)
        if (to == from) return
        list.add(to, list.removeAt(from))
        _sources.value = list
        save()
    }

    fun newId(): String = Random.nextLong().toULong().toString(16)

    /**
     * First run: if there's music on this PC, the Music folder becomes a source without anyone being
     * asked. Someone who has never heard the word "lossless" still gets their own FLACs played in place
     * of YouTube's stream. It happens once — remove the source and it stays removed.
     */
    suspend fun ensureDefaultSource() {
        BuiltIn.forEach { kind ->
            if (_sources.value.none { it.kind == kind } && DesktopPrefs.raw(removedKey(kind)) != "removed") {
                add(LosslessSource(id = kind.name.lowercase(Locale.US), kind = kind, name = builtInName(kind), location = builtInLocation(kind)))
            }
        }
        if (DesktopPrefs.raw(AUTO_KEY) != "done") {
            DesktopPrefs.putAll(mapOf(AUTO_KEY to "done"))
            addMusicFolders()
        }
        warm()
    }

    /**
     * Music already on this PC: the Music folder, and the same folder on any other fixed drive — a
     * library on D: is where most people with FLACs actually keep them.
     */
    private suspend fun addMusicFolders() {
        val home = System.getProperty("user.home")?.let { File(it, "Music") }
        val drives = withContext(Dispatchers.IO) { File.listRoots().orEmpty().toList() }
        val candidates = (listOfNotNull(home) + drives.map { File(it, "Music") })
            .distinctBy { it.absolutePath.lowercase(Locale.US) }
            // Never a OneDrive folder: reading a placeholder there would pull the whole library down.
            .filter { it.isDirectory && !it.absolutePath.contains("onedrive", ignoreCase = true) }
        candidates.forEach { folder ->
            val hasAudio = withContext(Dispatchers.IO) {
                runCatching {
                    folder.walkTopDown().maxDepth(4).any { it.isFile && it.extension.lowercase(Locale.US) in AudioExtensions }
                }.getOrDefault(false)
            }
            if (hasAudio) {
                val label = if (folder == home) "My music" else "Music on ${folder.absolutePath.take(2)}"
                add(LosslessSource(id = newId(), kind = LosslessKind.FOLDER, name = label, location = folder.absolutePath))
            }
        }
    }

    /** Reads folder sources before the first song asks for one, so no song waits on the disk. */
    suspend fun warm() {
        _sources.value.filter { it.enabled && it.kind == LosslessKind.FOLDER }.forEach { source ->
            runCatching { FolderIndex.tracks(source) }
        }
    }

    /** Can this source be reached, and does it hold anything? Also refreshes a folder's index. */
    suspend fun check(source: LosslessSource): SourceHealth {
        _health.value = _health.value + (source.id to SourceHealth.Checking)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (source.kind) {
                    LosslessKind.FOLDER -> {
                        val folder = File(source.location)
                        if (!folder.isDirectory) return@runCatching SourceHealth.Failed("That folder doesn't exist.")
                        val tracks = FolderIndex.rescan(source)
                        val lossless = tracks.count { it.lossless }
                        if (tracks.isEmpty()) {
                            SourceHealth.Failed("No audio files in that folder.")
                        } else {
                            SourceHealth.Ok("$lossless lossless of ${tracks.size} files")
                        }
                    }
                    LosslessKind.SUBSONIC -> {
                        val json = getJson(subsonicUrl(source, "ping.view", emptyMap()))
                        val response = json.optJSONObject("subsonic-response")
                        val status = response?.optString("status")
                        if (status == "ok") {
                            SourceHealth.Ok("Subsonic ${response.optString("version", "server")} answered")
                        } else {
                            SourceHealth.Failed(response?.optJSONObject("error")?.optString("message") ?: "The server rejected that sign-in.")
                        }
                    }
                    LosslessKind.ARCHIVE -> {
                        val found = archiveIdentifiers("mediatype:(audio) AND format:(Flac)")
                        if (found.isEmpty()) SourceHealth.Failed("archive.org didn't answer.") else SourceHealth.Ok("archive.org answered")
                    }
                    LosslessKind.WIKIMEDIA -> {
                        val pages = getJson(
                            "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=1" +
                                "&gsrsearch=${encode("filetype:audio flac")}&prop=imageinfo&iiprop=url&format=json",
                        ).optJSONObject("query")?.optJSONObject("pages")
                        if (pages == null) SourceHealth.Failed("Commons didn't answer.") else SourceHealth.Ok("commons.wikimedia.org answered")
                    }
                    LosslessKind.JAMENDO -> {
                        if (source.username.isBlank()) {
                            SourceHealth.Failed("Add your Jamendo client ID.")
                        } else {
                            val json = getJson("https://api.jamendo.com/v3.0/tracks/?client_id=${encode(source.username)}&format=json&limit=1")
                            val code = json.optJSONObject("headers")?.optInt("code", -1) ?: -1
                            if (code == 0) {
                                SourceHealth.Ok("Jamendo accepted the client ID")
                            } else {
                                SourceHealth.Failed(json.optJSONObject("headers")?.optString("error_message")?.takeIf { it.isNotBlank() } ?: "Jamendo rejected that client ID.")
                            }
                        }
                    }
                    LosslessKind.PLEX -> {
                        val container = getJson("${source.location.trimEnd('/')}/identity", mapOf("X-Plex-Token" to source.password)).optJSONObject("MediaContainer")
                        if (container == null) SourceHealth.Failed("That Plex server didn't answer.") else SourceHealth.Ok("Plex ${container.optString("version", "server")} answered")
                    }
                    LosslessKind.JELLYFIN -> {
                        val info = getJson("${source.location.trimEnd('/')}/System/Info", mapOf("X-Emby-Token" to source.password))
                        val name = info.optString("ServerName").takeIf { it.isNotBlank() }
                        if (name == null) SourceHealth.Failed("That Jellyfin server didn't answer, or the API key is wrong.") else SourceHealth.Ok("$name answered")
                    }
                    LosslessKind.DLNA -> {
                        val servers = discoverDlna()
                        dlnaServers = System.currentTimeMillis() to servers
                        if (servers.isEmpty()) {
                            SourceHealth.Failed("No media servers answered on this network.")
                        } else {
                            SourceHealth.Ok("${servers.size} media server${if (servers.size == 1) "" else "s"} on this network")
                        }
                    }
                    LosslessKind.ADDON -> {
                        val manifest = getJson(source.location.trimEnd('/') + "/manifest.json")
                        val name = manifest.optString("name").ifBlank { "Addon" }
                        val resources = manifest.optJSONArray("resources")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty()
                        if (resources.isEmpty() || "search" in resources) {
                            SourceHealth.Ok("$name ${manifest.optString("version", "")}".trim())
                        } else {
                            SourceHealth.Failed("That addon doesn't offer search.")
                        }
                    }
                }
            }.getOrElse { SourceHealth.Failed(it.message ?: "Couldn't reach it.") }
        }
        _health.value = _health.value + (source.id to result)
        return result
    }

    /**
     * The best copy of [song] any source holds, or null to let YouTube play it. Sources are asked at
     * once, not in turn: a folder answers in milliseconds where a server takes a second, and a slow
     * source shouldn't hold up a song that's already started.
     */
    suspend fun resolve(song: SongItem): ResolvedStream? {
        val enabled = _sources.value.filter { it.enabled }
        if (enabled.isEmpty()) return null
        val candidates = withContext(Dispatchers.IO) {
            supervisorScope {
                enabled
                    .map { source -> async { withTimeoutOrNull(9_000) { runCatching { search(source, song) }.getOrDefault(emptyList()) }.orEmpty() } }
                    .awaitAll()
                    .flatten()
            }
        }
        val best = candidates
            .filter { TrackMatch.matches(song, it) }
            .maxByOrNull { it.rank }
            ?: run {
                _lastMatch.value = null
                return null
            }
        // A lossy copy from someone's server is no better than YouTube's own stream.
        if (!best.lossless) {
            _lastMatch.value = null
            return null
        }
        _lastMatch.value = "${best.title} — ${best.sourceName}"
        return ResolvedStream(
            url = best.url,
            headers = StreamHeaders(userAgent = "Exhale", origin = null, referer = null),
            mimeType = "audio/${best.format.lowercase(Locale.US)}",
            bitrate = best.bitrate ?: 0,
            clientName = best.sourceName,
            loudnessDb = null,
            sampleRate = best.sampleRate,
            bitDepth = best.bitDepth,
            lossless = true,
            sourceLabel = best.sourceName,
        )
    }

    private suspend fun search(source: LosslessSource, song: SongItem): List<LosslessTrack> = when (source.kind) {
        LosslessKind.FOLDER -> FolderIndex.tracks(source)
        LosslessKind.SUBSONIC -> subsonicSearch(source, song)
        LosslessKind.ADDON -> addonSearch(source, song)
        LosslessKind.ARCHIVE -> archiveSearch(source, song)
        LosslessKind.WIKIMEDIA -> wikimediaSearch(source, song)
        LosslessKind.JAMENDO -> jamendoSearch(source, song)
        LosslessKind.PLEX -> plexSearch(source, song)
        LosslessKind.JELLYFIN -> jellyfinSearch(source, song)
        LosslessKind.DLNA -> dlnaSearch(source, song)
    }

    // ---- Wikimedia Commons -------------------------------------------------------------------

    /** Public-domain and Creative Commons recordings, served straight from upload.wikimedia.org. */
    private fun wikimediaSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val query = listOf("filetype:audio", song.title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=8" +
            "&gsrsearch=${encode(query)}&prop=imageinfo&iiprop=${encode("url|mime|metadata|extmetadata")}&format=json"
        val pages = getJson(url).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return pages.keys().asSequence().mapNotNull { key ->
            val page = pages.optJSONObject(key) ?: return@mapNotNull null
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val fileUrl = info.optString("url").substringBefore('?').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val format = fileUrl.substringAfterLast('.', "").lowercase(Locale.US)
            if (format !in LosslessFormats) return@mapNotNull null
            LosslessTrack(
                title = page.optString("title").removePrefix("File:").substringBeforeLast('.').replace('_', ' '),
                artist = stripHtml(info.optJSONObject("extmetadata")?.optJSONObject("Artist")?.optString("value").orEmpty()).takeIf { it.isNotBlank() },
                album = null,
                durationSeconds = metadataValue(info.optJSONArray("metadata"), "length"),
                url = fileUrl,
                format = format,
                sourceName = source.name,
            )
        }.toList()
    }

    /** MediaWiki's metadata comes as a list of {name, value} pairs rather than an object. */
    private fun metadataValue(metadata: JSONArray?, name: String): Double? {
        val list = metadata ?: return null
        for (index in 0 until list.length()) {
            val entry = list.optJSONObject(index) ?: continue
            if (entry.optString("name").equals(name, ignoreCase = true)) {
                return entry.optDouble("value", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            }
        }
        return null
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), " ").replace(Regex("&[a-z]+;"), " ").replace(Regex("\\s+"), " ").trim()

    // ---- Jamendo -----------------------------------------------------------------------------

    /** Creative Commons music, in FLAC where the artist allowed a download. Needs a free client ID. */
    private fun jamendoSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val clientId = source.username.ifBlank { return emptyList() }
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${encode(clientId)}&format=json&limit=10" +
            "&audioformat=flac&namesearch=${encode(song.title)}" +
            if (artist.isBlank()) "" else "&artist_name=${encode(artist)}"
        val results = getJson(url).optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).mapNotNull { index ->
            val entry = results.optJSONObject(index) ?: return@mapNotNull null
            if (!entry.optBoolean("audiodownload_allowed", true)) return@mapNotNull null
            val download = entry.optString("audiodownload").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LosslessTrack(
                title = entry.optString("name"),
                artist = entry.optString("artist_name").takeIf { it.isNotBlank() },
                album = entry.optString("album_name").takeIf { it.isNotBlank() },
                durationSeconds = entry.optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                url = if ("format=" in download) download else "$download&format=flac",
                format = "flac",
                sourceName = source.name,
            )
        }
    }

    // ---- Plex and Jellyfin -------------------------------------------------------------------

    /** A Plex server's own library, streamed as the file is. The token stays on this PC. */
    private fun plexSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val base = source.location.trimEnd('/')
        val token = source.password
        val container = getJson(
            "$base/search?query=${encode(song.title)}&type=10&limit=20",
            mapOf("X-Plex-Token" to token),
        ).optJSONObject("MediaContainer") ?: return emptyList()
        val items = container.optJSONArray("Metadata") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val entry = items.optJSONObject(index) ?: return@mapNotNull null
            val media = entry.optJSONArray("Media")?.optJSONObject(0) ?: return@mapNotNull null
            val part = media.optJSONArray("Part")?.optJSONObject(0) ?: return@mapNotNull null
            val key = part.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val format = (part.optString("container").takeIf { it.isNotBlank() } ?: media.optString("audioCodec")).lowercase(Locale.US)
            LosslessTrack(
                title = entry.optString("title"),
                artist = entry.optString("grandparentTitle").takeIf { it.isNotBlank() },
                album = entry.optString("parentTitle").takeIf { it.isNotBlank() },
                durationSeconds = entry.optLong("duration").takeIf { it > 0 }?.div(1000.0),
                url = "$base$key?X-Plex-Token=${encode(token)}",
                format = format,
                bitrate = media.optInt("bitrate").takeIf { it > 0 }?.times(1_000),
                sourceName = source.name,
            )
        }
    }

    /** The same for Jellyfin, over its own API rather than the Subsonic one. */
    private fun jellyfinSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val base = source.location.trimEnd('/')
        val key = source.password
        val items = getJson(
            "$base/Items?searchTerm=${encode(song.title)}&IncludeItemTypes=Audio&Recursive=true&Limit=15&Fields=${encode("MediaSources")}",
            mapOf("X-Emby-Token" to key),
        ).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val entry = items.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val media = entry.optJSONArray("MediaSources")?.optJSONObject(0)
            val audio = media?.optJSONArray("MediaStreams")?.let { streams ->
                (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.firstOrNull { it.optString("Type") == "Audio" }
            }
            LosslessTrack(
                title = entry.optString("Name"),
                artist = entry.optJSONArray("Artists")?.optString(0)?.takeIf { it.isNotBlank() } ?: entry.optString("AlbumArtist").takeIf { it.isNotBlank() },
                album = entry.optString("Album").takeIf { it.isNotBlank() },
                // Jellyfin counts in ticks of 100 nanoseconds.
                durationSeconds = entry.optLong("RunTimeTicks").takeIf { it > 0 }?.div(10_000_000.0),
                url = "$base/Audio/$id/stream?static=true&api_key=${encode(key)}",
                format = (media?.optString("Container") ?: entry.optString("Container")).lowercase(Locale.US),
                sampleRate = audio?.optInt("SampleRate")?.takeIf { it > 0 },
                bitDepth = audio?.optInt("BitDepth")?.takeIf { it > 0 },
                bitrate = media?.optInt("Bitrate")?.takeIf { it > 0 },
                sourceName = source.name,
            )
        }
    }

    // ---- Media servers on this network -------------------------------------------------------

    @Volatile private var dlnaServers: Pair<Long, List<String>> = 0L to emptyList()

    /**
     * No address to type: Exhale shouts on the local network (SSDP) and whatever media server answers —
     * a NAS, Plex's DLNA side, Windows media sharing — is asked for the song by name.
     */
    private fun dlnaSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val (found, servers) = dlnaServers
        val fresh = if (System.currentTimeMillis() - found < 5 * 60_000L && servers.isNotEmpty()) {
            servers
        } else {
            discoverDlna().also { dlnaServers = System.currentTimeMillis() to it }
        }
        return fresh.flatMap { description ->
            runCatching { dlnaTracks(description, song, source.name) }.getOrDefault(emptyList())
        }
    }

    /** The SSDP shout itself: every media server on the network answers with its description URL. */
    private fun discoverDlna(): List<String> {
        val message = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n")
        }.toByteArray()
        val found = LinkedHashSet<String>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1_200
                socket.send(DatagramPacket(message, message.size, InetAddress.getByName("239.255.255.250"), 1900))
                val buffer = ByteArray(4096)
                val deadline = System.currentTimeMillis() + 2_500
                while (System.currentTimeMillis() < deadline && found.size < 4) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                    Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)?.let { found += it }
                }
            }
        }
        return found.toList()
    }

    /** A server's ContentDirectory, asked for the song by title; DIDL-Lite comes back escaped inside. */
    private fun dlnaTracks(descriptionUrl: String, song: SongItem, sourceName: String): List<LosslessTrack> {
        val description = httpText(descriptionUrl, emptyMap()) ?: return emptyList()
        val document = parseXml(description) ?: return emptyList()
        val services = document.getElementsByTagName("service")
        var control: String? = null
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val type = service.getElementsByTagName("serviceType").item(0)?.textContent.orEmpty()
            if ("ContentDirectory" in type) {
                control = service.getElementsByTagName("controlURL").item(0)?.textContent
                break
            }
        }
        val controlUrl = control?.let { URL(URL(descriptionUrl), it).toString() } ?: return emptyList()
        val criteria = "(upnp:class derivedfrom \"object.item.audioItem\") and (dc:title contains \"${song.title.replace('"', ' ')}\")"
        val envelope = """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:Search xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
<ContainerID>0</ContainerID><SearchCriteria>${escapeXml(criteria)}</SearchCriteria><Filter>*</Filter>
<StartingIndex>0</StartingIndex><RequestedCount>20</RequestedCount><SortCriteria></SortCriteria>
</u:Search></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(controlUrl)
            .header("User-Agent", UserAgent)
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Search\"")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        val response = http.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null } ?: return emptyList()
        val result = parseXml(response)?.getElementsByTagName("Result")?.item(0)?.textContent ?: return emptyList()
        return didlTracks(result, sourceName)
    }

    /** DIDL-Lite: one <item> per track, each with a <res> that is the URL and carries its own details. */
    internal fun didlTracks(didl: String, sourceName: String): List<LosslessTrack> {
        val document = parseXml(didl) ?: return emptyList()
        val items = document.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { index ->
            val item = items.item(index) as? Element ?: return@mapNotNull null
            fun text(tag: String) = item.getElementsByTagName(tag).item(0)?.textContent?.takeIf { it.isNotBlank() }
            val resources = item.getElementsByTagName("res")
            var best: Element? = null
            for (resourceIndex in 0 until resources.length) {
                val resource = resources.item(resourceIndex) as? Element ?: continue
                val protocol = resource.getAttribute("protocolInfo").lowercase(Locale.US)
                val url = resource.textContent.orEmpty()
                val format = url.substringAfterLast('.', "").substringBefore('?').lowercase(Locale.US)
                if (format in LosslessFormats || "flac" in protocol || "wav" in protocol || "alac" in protocol) {
                    best = resource
                    break
                }
            }
            val resource = best ?: return@mapNotNull null
            val url = resource.textContent?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val protocol = resource.getAttribute("protocolInfo").lowercase(Locale.US)
            val format = url.substringAfterLast('.', "").substringBefore('?').lowercase(Locale.US)
                .takeIf { it in LosslessFormats }
                ?: LosslessFormats.firstOrNull { it in protocol }
                ?: return@mapNotNull null
            LosslessTrack(
                title = text("dc:title") ?: text("title") ?: return@mapNotNull null,
                artist = text("upnp:artist") ?: text("artist") ?: text("dc:creator"),
                album = text("upnp:album") ?: text("album"),
                durationSeconds = parseLength(resource.getAttribute("duration")),
                url = url,
                format = format,
                sampleRate = resource.getAttribute("sampleFrequency").toIntOrNull()?.takeIf { it > 0 },
                bitDepth = resource.getAttribute("bitsPerSample").toIntOrNull()?.takeIf { it > 0 },
                bitrate = resource.getAttribute("bitrate").toIntOrNull()?.takeIf { it > 0 }?.times(8),
                sourceName = sourceName,
            )
        }
    }

    private fun parseXml(xml: String): org.w3c.dom.Document? = runCatching {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Nothing here should ever fetch a DTD from the network.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
        }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }.getOrNull()

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun httpText(url: String, headers: Map<String, String>): String? = runCatching {
        val builder = Request.Builder().url(url).header("User-Agent", UserAgent)
        headers.forEach { (name, value) -> builder.header(name, value) }
        http.newCall(builder.build()).execute().use { if (it.isSuccessful) it.body.string() else null }
    }.getOrNull()

    // ---- Internet Archive --------------------------------------------------------------------

    private val archiveCache = ConcurrentHashMap<String, List<LosslessTrack>>()

    /**
     * archive.org: the one catalogue on the open internet handing out lossless with no account — the
     * etree collection of concerts taped with the band's blessing, and public-domain recordings.
     *
     * Items are found by artist and title, then each one's file list is read for a FLAC whose own title
     * and runtime match the song. Matching is strict here: a 1977 audience tape is not the studio
     * single, and only a runtime that agrees within a few seconds says otherwise.
     */
    private suspend fun archiveSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        archiveCache[song.id]?.let { return it }
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val queries = if (artist.isBlank()) {
            listOf("mediatype:(audio) AND format:(Flac) AND title:(${quoted(song.title)})")
        } else {
            listOf(
                "mediatype:(audio) AND format:(Flac) AND creator:(${quoted(artist)}) AND title:(${quoted(song.title)})",
                "mediatype:(audio) AND format:(Flac) AND creator:(${quoted(artist)})",
            )
        }
        val identifiers = supervisorScope {
            queries.map { query -> async { runCatching { archiveIdentifiers(query) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().distinct().take(4)
        val tracks = supervisorScope {
            identifiers.map { id -> async { runCatching { archiveTracks(id, source.name) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().filter { TrackMatch.matches(song, it, strict = true) }
        if (archiveCache.size > 300) archiveCache.clear()
        archiveCache[song.id] = tracks
        return tracks
    }

    private fun quoted(value: String): String = "\"" + value.replace('"', ' ') + "\""

    private fun archiveIdentifiers(query: String): List<String> {
        val url = "https://archive.org/advancedsearch.php?q=${encode(query)}" +
            "&fl%5B%5D=identifier&sort%5B%5D=${encode("downloads desc")}&rows=4&page=1&output=json"
        val docs = getJson(url).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        return (0 until docs.length()).mapNotNull { docs.optJSONObject(it)?.optString("identifier")?.takeIf(String::isNotBlank) }
    }

    /** One archive item's lossless files, as tracks. */
    private fun archiveTracks(identifier: String, sourceName: String): List<LosslessTrack> {
        val json = getJson("https://archive.org/metadata/${encode(identifier)}")
        val metadata = json.optJSONObject("metadata")
        val creator = metadata?.opt("creator")?.let { if (it is JSONArray) it.optString(0) else it.toString() }?.takeIf { it.isNotBlank() }
        val itemTitle = metadata?.optString("title")?.takeIf { it.isNotBlank() }
        val files = json.optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).mapNotNull { index ->
            val file = files.optJSONObject(index) ?: return@mapNotNull null
            val name = file.optString("name")
            val format = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (format !in LosslessFormats) return@mapNotNull null
            // A taper's file name — "gd77-05-08d2t05" — says nothing; the file's own title does.
            val title = file.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LosslessTrack(
                title = title,
                artist = file.optString("artist").takeIf { it.isNotBlank() } ?: creator,
                album = file.optString("album").takeIf { it.isNotBlank() } ?: itemTitle,
                durationSeconds = parseLength(file.optString("length")),
                url = "https://archive.org/download/${encode(identifier)}/" +
                    name.split('/').joinToString("/") { encode(it).replace("+", "%20") },
                format = format,
                sampleRate = file.optInt("sample_rate").takeIf { it > 0 },
                sourceName = sourceName,
            )
        }
    }

    /** Lengths come as "274.45", as "4:34", or as a DLNA clock — "0:04:33.000". */
    internal fun parseLength(raw: String): Double? {
        if (raw.isBlank()) return null
        raw.trim().toDoubleOrNull()?.let { return it.takeIf { seconds -> seconds > 0 } }
        val parts = raw.trim().split(':').map { it.trim().toDoubleOrNull() ?: return null }
        if (parts.isEmpty()) return null
        return parts.fold(0.0) { total, part -> total * 60 + part }.takeIf { it > 0 }
    }

    // ---- Subsonic ----------------------------------------------------------------------------

    /** Subsonic's salted-token auth: the password never goes over the wire, only md5(password + salt). */
    private fun subsonicUrl(source: LosslessSource, method: String, params: Map<String, String>): String {
        val salt = Random.nextLong().toULong().toString(16)
        val token = md5(source.password + salt)
        val query = buildMap {
            put("u", source.username)
            put("t", token)
            put("s", salt)
            put("v", "1.16.1")
            put("c", "Exhale")
            put("f", "json")
            putAll(params)
        }.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        return "${source.location.trimEnd('/')}/rest/$method?$query"
    }

    private fun subsonicSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val query = listOfNotNull(song.title, song.artists.firstOrNull()?.name).joinToString(" ")
        val url = subsonicUrl(
            source,
            "search3.view",
            mapOf("query" to query, "songCount" to "30", "albumCount" to "0", "artistCount" to "0"),
        )
        val songs = getJson(url).optJSONObject("subsonic-response")?.optJSONObject("searchResult3")?.optJSONArray("song") ?: JSONArray()
        return (0 until songs.length()).mapNotNull { index ->
            val entry = songs.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val suffix = entry.optString("suffix").lowercase(Locale.US)
            LosslessTrack(
                title = entry.optString("title"),
                artist = entry.optString("artist").takeIf { it.isNotBlank() },
                album = entry.optString("album").takeIf { it.isNotBlank() },
                durationSeconds = entry.optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                // format=raw: the server sends the file as it is, with no transcoding on the way.
                url = subsonicUrl(source, "stream.view", mapOf("id" to id, "format" to "raw", "maxBitRate" to "0")),
                format = suffix,
                sampleRate = entry.optInt("samplingRate").takeIf { it > 0 },
                bitDepth = entry.optInt("bitDepth").takeIf { it > 0 },
                bitrate = entry.optInt("bitRate").takeIf { it > 0 }?.times(1_000),
                sourceName = source.name,
            )
        }
    }

    // ---- Addon protocol ----------------------------------------------------------------------

    private fun addonSearch(source: LosslessSource, song: SongItem): List<LosslessTrack> {
        val base = source.location.trimEnd('/')
        val query = listOfNotNull(song.title, song.artists.firstOrNull()?.name).joinToString(" ")
        val tracks = getJson("$base/search?q=${encode(query)}").optJSONArray("tracks") ?: JSONArray()
        return (0 until tracks.length()).mapNotNull { index ->
            val entry = tracks.optJSONObject(index) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = entry.optString("artist").takeIf { it.isNotBlank() }
            val duration = entry.optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            val candidate = LosslessTrack(
                title = title,
                artist = artist,
                album = entry.optString("album").takeIf { it.isNotBlank() },
                durationSeconds = duration,
                url = entry.optString("streamURL"),
                format = entry.optString("format").lowercase(Locale.US),
                sourceName = source.name,
            )
            // Only the matching track's stream is asked for, so a search doesn't hammer the server.
            if (!TrackMatch.matches(song, candidate)) return@mapNotNull null
            val id = entry.optString("id").takeIf { it.isNotBlank() }
            val stream = if (candidate.url.isNotBlank() || id == null) null else runCatching { getJson("$base/stream/${encode(id)}") }.getOrNull()
            val url = candidate.url.takeIf { it.isNotBlank() } ?: stream?.optString("url").orEmpty()
            if (url.isBlank()) return@mapNotNull null
            // Anything under DRM can't be played here, and shouldn't be asked for twice.
            val encrypted = stream?.opt("encrypted")
            if (encrypted == true || (encrypted is String && encrypted.isNotBlank() && encrypted != "none")) return@mapNotNull null
            candidate.copy(
                url = url,
                format = (stream?.optString("format")?.takeIf { it.isNotBlank() } ?: stream?.optString("codec")?.takeIf { it.isNotBlank() } ?: candidate.format).lowercase(Locale.US),
                sampleRate = stream?.optInt("sampleRate")?.takeIf { it > 0 },
                bitDepth = stream?.optInt("bitDepth")?.takeIf { it > 0 },
                bitrate = stream?.optInt("bitrate")?.takeIf { it > 0 },
            )
        }
    }

    // ---- Shared ------------------------------------------------------------------------------

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val builder = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", UserAgent)
        headers.forEach { (name, value) -> builder.header(name, value) }
        http.newCall(builder.build()).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return JSONObject(body)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun load(): List<LosslessSource> = runCatching {
        val raw = DesktopPrefs.raw(KEY)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            LosslessSource(
                id = entry.optString("id").ifBlank { return@mapNotNull null },
                kind = runCatching { LosslessKind.valueOf(entry.optString("kind")) }.getOrNull() ?: return@mapNotNull null,
                name = entry.optString("name").ifBlank { "Source" },
                location = entry.optString("location"),
                username = entry.optString("username"),
                password = entry.optString("password"),
                enabled = entry.optBoolean("enabled", true),
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        _sources.value.forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("kind", source.kind.name)
                    .put("name", source.name)
                    .put("location", source.location)
                    .put("username", source.username)
                    .put("password", source.password)
                    .put("enabled", source.enabled),
            )
        }
        DesktopPrefs.putAll(mapOf(KEY to array.toString()))
    }
}

/**
 * A folder of music, read once and kept. FLAC files carry their own tags, sample rate and bit depth in
 * a header a few hundred bytes long, so a library indexes as fast as the disk can list it; other
 * formats fall back to what the file and folder names say.
 */
private object FolderIndex {
    private val cached = HashMap<String, List<LosslessTrack>>()
    private val lock = Mutex()

    suspend fun tracks(source: LosslessSource): List<LosslessTrack> = lock.withLock {
        cached[source.id] ?: scan(source).also { cached[source.id] = it }
    }

    suspend fun rescan(source: LosslessSource): List<LosslessTrack> = lock.withLock {
        scan(source).also { cached[source.id] = it }
    }

    fun forget(id: String) {
        cached.remove(id)
    }

    private fun scan(source: LosslessSource): List<LosslessTrack> {
        val root = File(source.location)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .maxDepth(8)
            .filter { it.isFile && it.extension.lowercase(Locale.US) in AudioExtensions }
            .take(40_000)
            .map { file -> read(file, source.name) }
            .toList()
    }

    private fun read(file: File, sourceName: String): LosslessTrack {
        val extension = file.extension.lowercase(Locale.US)
        val flac = if (extension == "flac") runCatching { readFlac(file) }.getOrNull() else null
        val fromName = titleAndArtistFromPath(file)
        return LosslessTrack(
            title = flac?.tags?.get("TITLE") ?: fromName.first,
            artist = flac?.tags?.get("ARTIST") ?: flac?.tags?.get("ALBUMARTIST") ?: fromName.second,
            album = flac?.tags?.get("ALBUM") ?: file.parentFile?.name,
            durationSeconds = flac?.duration,
            url = file.absolutePath,
            format = extension,
            sampleRate = flac?.sampleRate,
            bitDepth = flac?.bitDepth,
            bitrate = flac?.let { info -> info.duration?.takeIf { it > 0 }?.let { (file.length() * 8 / it).toInt() } },
            sourceName = sourceName,
        )
    }

    /** "Artist/Album/03 - Title.flac", or "Artist - Title.flac": whatever the names give away. */
    private fun titleAndArtistFromPath(file: File): Pair<String, String?> {
        val base = file.nameWithoutExtension.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "").trim()
        val split = base.split(" - ", limit = 2)
        return if (split.size == 2 && split[0].isNotBlank()) {
            split[1].trim() to split[0].trim()
        } else {
            base to file.parentFile?.parentFile?.name
        }
    }

    private class FlacInfo(val tags: Map<String, String>, val sampleRate: Int?, val bitDepth: Int?, val duration: Double?)

    private fun readFlac(file: File): FlacInfo? {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "fLaC") return null
            var tags = emptyMap<String, String>()
            var sampleRate: Int? = null
            var bitDepth: Int? = null
            var duration: Double? = null
            var last = false
            while (!last && raf.filePointer < raf.length()) {
                val header = ByteArray(4)
                raf.readFully(header)
                last = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
                if (length < 0 || length > 10_000_000) return null
                when (type) {
                    // STREAMINFO: 20 bits of sample rate, 5 of bit depth and 36 of total samples, packed.
                    0 -> {
                        val body = ByteArray(length)
                        raf.readFully(body)
                        if (body.size < 18) return null
                        val rate = ((body[10].toInt() and 0xFF) shl 12) or ((body[11].toInt() and 0xFF) shl 4) or ((body[12].toInt() and 0xF0) shr 4)
                        val depth = (((body[12].toInt() and 0x01) shl 4) or ((body[13].toInt() and 0xF0) shr 4)) + 1
                        val samples = ((body[13].toLong() and 0x0FL) shl 32) or
                            ((body[14].toLong() and 0xFFL) shl 24) or
                            ((body[15].toLong() and 0xFFL) shl 16) or
                            ((body[16].toLong() and 0xFFL) shl 8) or
                            (body[17].toLong() and 0xFFL)
                        sampleRate = rate.takeIf { it > 0 }
                        bitDepth = depth.takeIf { it > 1 }
                        duration = if (rate > 0 && samples > 0) samples.toDouble() / rate else null
                    }
                    // VORBIS_COMMENT: the tags, as little-endian length-prefixed "KEY=value" strings.
                    4 -> {
                        val body = ByteArray(length)
                        raf.readFully(body)
                        tags = parseVorbisComment(body)
                    }
                    else -> raf.seek(raf.filePointer + length)
                }
            }
            return FlacInfo(tags, sampleRate, bitDepth, duration)
        }
    }

    private fun parseVorbisComment(body: ByteArray): Map<String, String> {
        fun int(at: Int): Int =
            (body[at].toInt() and 0xFF) or ((body[at + 1].toInt() and 0xFF) shl 8) or
                ((body[at + 2].toInt() and 0xFF) shl 16) or ((body[at + 3].toInt() and 0xFF) shl 24)

        return runCatching {
            var cursor = 0
            val vendor = int(cursor)
            cursor += 4 + vendor
            val count = int(cursor)
            cursor += 4
            val tags = HashMap<String, String>()
            repeat(count.coerceIn(0, 512)) {
                val length = int(cursor)
                cursor += 4
                if (length < 0 || cursor + length > body.size) return tags
                val entry = String(body, cursor, length, Charsets.UTF_8)
                cursor += length
                val split = entry.indexOf('=')
                if (split > 0) tags[entry.substring(0, split).uppercase(Locale.US)] = entry.substring(split + 1)
            }
            tags
        }.getOrDefault(emptyMap())
    }
}

/**
 * Whether a source's track is the song that's playing. YouTube titles carry things a library's don't —
 * "(Official Video)", "feat." — so both sides are reduced to their plain words first, and the runtime
 * settles it: two songs of the same name a minute apart are not the same recording.
 */
internal object TrackMatch {
    private val Noise = Regex(
        "\\((?:[^)]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv|version|edit)\\b[^)]*)\\)" +
            "|\\[(?:[^\\]]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv)\\b[^\\]]*)\\]" +
            "|\\b(?:feat|ft|featuring|with)\\b[^-]*",
        RegexOption.IGNORE_CASE,
    )

    fun normalize(value: String): String {
        val stripped = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Noise, " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun matches(song: SongItem, candidate: LosslessTrack, strict: Boolean = false): Boolean {
        val wanted = normalize(song.title)
        val found = normalize(candidate.title)
        if (wanted.isEmpty() || found.isEmpty()) return false
        val sameTitle = wanted == found || (wanted.length > 6 && found.length > 6 && (found.startsWith(wanted) || wanted.startsWith(found)))
        if (!sameTitle) return false

        val wantedDuration = song.duration?.toDouble()
        val foundDuration = candidate.durationSeconds
        // Both runtimes known and more than four seconds apart: a different recording, live or extended.
        if (wantedDuration != null && foundDuration != null && abs(wantedDuration - foundDuration) > 4.0) return false
        // Strict (a public catalogue full of live takes of the same song): the runtime has to be there,
        // and it has to agree closely, or this is another performance rather than another copy.
        if (strict) {
            if (wantedDuration == null || foundDuration == null) return false
            if (abs(wantedDuration - foundDuration) > 2.5) return false
        }

        val artists = song.artists.map { normalize(it.name) }.filter { it.isNotEmpty() }
        if (artists.isEmpty()) return !strict
        val theirs = listOfNotNull(candidate.artist, candidate.album).joinToString(" ") { normalize(it) }
        // A file with no artist anywhere still counts when the title and runtime already agree.
        if (theirs.isBlank()) return !strict && foundDuration != null && wantedDuration != null
        return artists.any { artist -> theirs.contains(artist) || artist.split(' ').any { it.length > 3 && theirs.contains(it) } }
    }
}
