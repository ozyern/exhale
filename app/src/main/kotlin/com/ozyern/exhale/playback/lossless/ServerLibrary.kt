/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback.lossless

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs
import kotlin.random.Random

/** A music server of the listener's own. Ported from the desktop app's lossless library. */
enum class ServerKind(val label: String, val keyLabel: String) {
    /** Navidrome, Airsonic, Gonic, Ampache, LMS — anything speaking the Subsonic API. */
    SUBSONIC("Subsonic / Navidrome", "Password"),

    /** Jellyfin (and Emby), over its own API, with an API key. */
    JELLYFIN("Jellyfin", "API key"),

    /** Plex Media Server, with an X-Plex-Token. */
    PLEX("Plex", "Plex token"),
}

data class MusicServer(
    val id: String,
    val kind: ServerKind,
    val name: String,
    /** The server's base address, e.g. https://music.example.com or http://192.168.1.20:4533. */
    val url: String,
    /** Subsonic only. */
    val username: String = "",
    /** Subsonic: the password. Jellyfin: an API key. Plex: a token. Kept encrypted on the phone. */
    val secret: String = "",
    val enabled: Boolean = true,
)

sealed interface ServerHealth {
    data object Checking : ServerHealth
    data class Ok(val detail: String) : ServerHealth
    data class Failed(val message: String) : ServerHealth
}

/** One copy of a song a server holds. */
data class ServerTrack(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?,
    val url: String,
    val format: String,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val serverName: String,
) {
    val lossless: Boolean get() = format.lowercase(Locale.US) in LosslessFormats

    /** Higher is better: lossless, then FLAC, then a server in the house, then resolution. */
    val rank: Long
        get() = (if (lossless) 1L shl 60 else 0L) +
            (if (PrivateHost.containsMatchIn(url)) 1L shl 58 else 0L) +
            (if (format.equals("flac", ignoreCase = true)) 1L shl 56 else 0L) +
            (bitDepth ?: 16).toLong() * (sampleRate ?: 44_100).toLong()
}

private val LosslessFormats = setOf("flac", "alac", "wav", "wave", "aiff", "aif", "ape", "wv")
private val PrivateHost = Regex("""^https?://(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|[^/:]+\.local\b)""", RegexOption.IGNORE_CASE)

/**
 * The listener's own music servers, asked for a lossless copy of each song before anything else.
 *
 * Exhale ships no servers: every one here is an address the listener typed in, holding music they
 * keep there. A song that matches by title, artist and runtime plays from it, as the file is (no
 * transcoding); anything else plays as before.
 */
object ServerLibrary {
    private const val PREFS = "lossless_servers"
    private const val KEY = "servers"
    private const val UserAgent = "Exhale (+https://github.com/ozyern/exhale)"

    private lateinit var prefs: SharedPreferences
    private val _servers = MutableStateFlow<List<MusicServer>>(emptyList())
    val servers: StateFlow<List<MusicServer>> = _servers.asStateFlow()

    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())
    val health: StateFlow<Map<String, ServerHealth>> = _health.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = _servers.value.any { it.enabled }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _servers.value = load()
    }

    fun newId(): String = Random.nextLong().toULong().toString(16)

    fun save(server: MusicServer) {
        _servers.value = _servers.value.filterNot { it.id == server.id } + server
        _health.value = _health.value - server.id
        persist()
    }

    fun remove(id: String) {
        _servers.value = _servers.value.filterNot { it.id == id }
        _health.value = _health.value - id
        persist()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _servers.value = _servers.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persist()
    }

    // ---- Lookup --------------------------------------------------------------------------------

    /**
     * The best lossless copy of [song] on any enabled server, or null. Every server is asked at once;
     * a slow or unreachable one costs the song nothing past [timeoutMs].
     */
    suspend fun find(song: MediaMetadata, timeoutMs: Long = 8_000): ServerTrack? {
        val enabled = _servers.value.filter { it.enabled }
        if (enabled.isEmpty() || song.title.isBlank()) return null
        return withContext(Dispatchers.IO) {
            supervisorScope {
                enabled.map { server ->
                    async {
                        withTimeoutOrNull(timeoutMs) {
                            runCatching { search(server, song) }.getOrDefault(emptyList())
                        }.orEmpty()
                    }
                }.awaitAll().flatten()
            }
                .filter { it.lossless && TrackMatch.matches(song.title, song.artists.map { a -> a.name }, song.duration, it) }
                .maxByOrNull { it.rank }
        }
    }

    private fun search(server: MusicServer, song: MediaMetadata): List<ServerTrack> = when (server.kind) {
        ServerKind.SUBSONIC -> subsonicSearch(server, song)
        ServerKind.JELLYFIN -> jellyfinSearch(server, song)
        ServerKind.PLEX -> plexSearch(server, song)
    }

    /** Can the server be reached with these details? */
    suspend fun check(server: MusicServer): ServerHealth {
        _health.value = _health.value + (server.id to ServerHealth.Checking)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (server.kind) {
                    ServerKind.SUBSONIC -> {
                        val response = getJson(subsonicUrl(server, "ping.view", emptyMap())).optJSONObject("subsonic-response")
                        if (response?.optString("status") == "ok") {
                            ServerHealth.Ok("Connected — Subsonic ${response.optString("version", "")}".trim())
                        } else {
                            ServerHealth.Failed(response?.optJSONObject("error")?.optString("message") ?: "The server turned that sign-in down.")
                        }
                    }
                    ServerKind.JELLYFIN -> {
                        val name = getJson("${server.url.trimEnd('/')}/System/Info", mapOf("X-Emby-Token" to server.secret))
                            .optString("ServerName").takeIf { it.isNotBlank() }
                        if (name == null) ServerHealth.Failed("No answer, or the API key is wrong.") else ServerHealth.Ok("Connected to $name")
                    }
                    ServerKind.PLEX -> {
                        val container = getJson("${server.url.trimEnd('/')}/identity", mapOf("X-Plex-Token" to server.secret))
                            .optJSONObject("MediaContainer")
                        if (container == null) ServerHealth.Failed("That Plex server didn't answer.") else ServerHealth.Ok("Connected — Plex ${container.optString("version", "")}".trim())
                    }
                }
            }.getOrElse { ServerHealth.Failed(it.message ?: "Couldn't reach it.") }
        }
        _health.value = _health.value + (server.id to result)
        return result
    }

    // ---- Subsonic ------------------------------------------------------------------------------

    private fun subsonicUrl(server: MusicServer, method: String, params: Map<String, String>): String {
        // Token auth: the password itself never travels, only md5(password + salt).
        val salt = Random.nextLong().toULong().toString(16)
        val query = buildMap {
            put("u", server.username)
            put("t", md5(server.secret + salt))
            put("s", salt)
            put("v", "1.16.1")
            put("c", "Exhale")
            put("f", "json")
            putAll(params)
        }.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        return "${server.url.trimEnd('/')}/rest/$method?$query"
    }

    private fun subsonicSearch(server: MusicServer, song: MediaMetadata): List<ServerTrack> {
        val query = listOfNotNull(song.title, song.artists.firstOrNull()?.name).joinToString(" ")
        val songs = getJson(
            subsonicUrl(server, "search3.view", mapOf("query" to query, "songCount" to "30", "albumCount" to "0", "artistCount" to "0")),
        ).optJSONObject("subsonic-response")?.optJSONObject("searchResult3")?.optJSONArray("song") ?: JSONArray()
        return (0 until songs.length()).mapNotNull { index ->
            val entry = songs.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ServerTrack(
                title = entry.optString("title"),
                artist = entry.optString("artist").takeIf { it.isNotBlank() },
                album = entry.optString("album").takeIf { it.isNotBlank() },
                durationSeconds = entry.optDouble("duration", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                // format=raw: the file as it is, never transcoded on the way.
                url = subsonicUrl(server, "stream.view", mapOf("id" to id, "format" to "raw", "maxBitRate" to "0")),
                format = entry.optString("suffix").lowercase(Locale.US),
                sampleRate = entry.optInt("samplingRate").takeIf { it > 0 },
                bitDepth = entry.optInt("bitDepth").takeIf { it > 0 },
                serverName = server.name,
            )
        }
    }

    // ---- Jellyfin ------------------------------------------------------------------------------

    private fun jellyfinSearch(server: MusicServer, song: MediaMetadata): List<ServerTrack> {
        val base = server.url.trimEnd('/')
        val items = getJson(
            "$base/Items?searchTerm=${encode(song.title)}&IncludeItemTypes=Audio&Recursive=true&Limit=15&Fields=${encode("MediaSources")}",
            mapOf("X-Emby-Token" to server.secret),
        ).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val entry = items.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val media = entry.optJSONArray("MediaSources")?.optJSONObject(0)
            val audio = media?.optJSONArray("MediaStreams")?.let { streams ->
                (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.firstOrNull { it.optString("Type") == "Audio" }
            }
            ServerTrack(
                title = entry.optString("Name"),
                artist = entry.optJSONArray("Artists")?.optString(0)?.takeIf { it.isNotBlank() }
                    ?: entry.optString("AlbumArtist").takeIf { it.isNotBlank() },
                album = entry.optString("Album").takeIf { it.isNotBlank() },
                durationSeconds = entry.optLong("RunTimeTicks").takeIf { it > 0 }?.div(10_000_000.0),
                url = "$base/Audio/$id/stream?static=true&api_key=${encode(server.secret)}",
                format = (media?.optString("Container") ?: entry.optString("Container")).lowercase(Locale.US),
                sampleRate = audio?.optInt("SampleRate")?.takeIf { it > 0 },
                bitDepth = audio?.optInt("BitDepth")?.takeIf { it > 0 },
                serverName = server.name,
            )
        }
    }

    // ---- Plex ----------------------------------------------------------------------------------

    private fun plexSearch(server: MusicServer, song: MediaMetadata): List<ServerTrack> {
        val base = server.url.trimEnd('/')
        val items = getJson(
            "$base/search?query=${encode(song.title)}&type=10&limit=20",
            mapOf("X-Plex-Token" to server.secret),
        ).optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val entry = items.optJSONObject(index) ?: return@mapNotNull null
            val media = entry.optJSONArray("Media")?.optJSONObject(0) ?: return@mapNotNull null
            val part = media.optJSONArray("Part")?.optJSONObject(0) ?: return@mapNotNull null
            val key = part.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ServerTrack(
                title = entry.optString("title"),
                artist = entry.optString("grandparentTitle").takeIf { it.isNotBlank() },
                album = entry.optString("parentTitle").takeIf { it.isNotBlank() },
                durationSeconds = entry.optLong("duration").takeIf { it > 0 }?.div(1000.0),
                url = "$base$key?X-Plex-Token=${encode(server.secret)}",
                format = (part.optString("container").takeIf { it.isNotBlank() } ?: media.optString("audioCodec")).lowercase(Locale.US),
                serverName = server.name,
            )
        }
    }

    // ---- Shared --------------------------------------------------------------------------------

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

    // ---- Storage: secrets encrypted with a key that never leaves the phone's keystore ----------

    private fun persist() {
        val array = JSONArray()
        _servers.value.forEach { server ->
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("kind", server.kind.name)
                    .put("name", server.name)
                    .put("url", server.url)
                    .put("username", server.username)
                    .put("secret", Secrets.encrypt(server.secret))
                    .put("enabled", server.enabled),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<MusicServer> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            MusicServer(
                id = entry.optString("id"),
                kind = runCatching { ServerKind.valueOf(entry.optString("kind")) }.getOrNull() ?: return@mapNotNull null,
                name = entry.optString("name"),
                url = entry.optString("url"),
                username = entry.optString("username"),
                secret = Secrets.decrypt(entry.optString("secret")).orEmpty(),
                enabled = entry.optBoolean("enabled", true),
            )
        }
    }.getOrDefault(emptyList())

    private object Secrets {
        private const val Alias = "exhale_server_secrets"

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getEntry(Alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
        }

        fun encrypt(plain: String): String {
            if (plain.isEmpty()) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
            return Base64.encodeToString(sealed, Base64.NO_WRAP)
        }

        fun decrypt(sealed: String): String? = runCatching {
            if (sealed.isEmpty()) return@runCatching ""
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12))
        }.getOrNull()
    }
}

/**
 * Whether a server's track is the song playing. YouTube titles carry things a library's don't —
 * "(Official Video)", "feat." — so both are reduced to plain words, and the runtime settles it.
 * The desktop app's matcher, unchanged in its rules.
 */
internal object TrackMatch {
    private val Noise = Regex(
        "\\((?:[^)]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv|version|edit)\\b[^)]*)\\)" +
            "|\\[(?:[^\\]]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv)\\b[^\\]]*)\\]" +
            "|\\b(?:feat|ft|featuring|with)\\b[^-]*",
        RegexOption.IGNORE_CASE,
    )

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Noise, " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun matches(title: String, artists: List<String>, durationSeconds: Int, candidate: ServerTrack): Boolean {
        val wanted = normalize(title)
        val found = normalize(candidate.title)
        if (wanted.isEmpty() || found.isEmpty()) return false
        val sameTitle = wanted == found ||
            (wanted.length > 6 && found.length > 6 && (found.startsWith(wanted) || wanted.startsWith(found)))
        if (!sameTitle) return false

        val wantedDuration = durationSeconds.takeIf { it > 0 }?.toDouble()
        val foundDuration = candidate.durationSeconds
        // Both runtimes known and more than four seconds apart: another recording, live or extended.
        if (wantedDuration != null && foundDuration != null && abs(wantedDuration - foundDuration) > 4.0) return false

        val names = artists.map { normalize(it) }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return true
        val theirs = listOfNotNull(candidate.artist, candidate.album).joinToString(" ") { normalize(it) }
        // A file tagged with no artist still counts when the title and the runtime already agree.
        if (theirs.isBlank()) return foundDuration != null && wantedDuration != null
        return names.any { name -> theirs.contains(name) || name.split(' ').any { it.length > 3 && theirs.contains(it) } }
    }
}
