/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The PaxSenix and Musixmatch clients follow BitChord's
 * (github.com/kushagrasinghx/BitChord, GPL-3.0), adapted to hand Exhale the formats it stores:
 * word-timed results become TTML, line-timed ones stay LRC.
 */

package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.constants.EnableMusixmatchLyricsKey
import com.ozyern.exhale.constants.EnablePaxSenixLyricsKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** A GET with the headers a web client sends; null for any failure. */
private val catalogClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}

private const val BROWSER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private fun catalogGet(url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
    val request = Request.Builder().url(url)
        .header("User-Agent", BROWSER_AGENT)
        .apply { headers.forEach { (key, value) -> header(key, value) } }
        .build()
    catalogClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string()?.takeIf { it.isNotBlank() } else null
    }
}.getOrNull()

// ============================================================================================

/**
 * Apple Music's own word-timed lyrics, found by asking Apple's catalogue which track this is.
 *
 * The other Apple hosts are asked by name and hope; this one searches the catalogue, scores every
 * candidate on title, artist and length, and asks for the lyrics of the one track id that won —
 * so it rarely comes back with a different edit, and it finds songs the name-matchers miss.
 */
object PaxSenixLyricsProvider : LyricsProvider {
    override val name = "PaxSenix"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxSenixLyricsKey] ?: true

    private const val PUBLIC_PROXY = "https://lyrics.paxsenix.org"
    private const val APPLE_SEARCH = "https://amp-api.music.apple.com/v1/catalog/us/search"
    private const val MINIMUM_MATCH_SCORE = 10

    private val tokenMutex = Mutex()
    private val cachedToken = AtomicReference<String?>(null)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        val trackId = searchTrackId(title, artist, duration * 1000L) ?: return@withContext notFound(name)
        val url = "$PUBLIC_PROXY/apple-music/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("id", trackId)
            .addQueryParameter("ttml", "true")
            .build()
        val body = catalogGet(url.toString(), mapOf("Accept" to "application/json, application/ttml+xml, */*"))
            ?: return@withContext notFound(name)
        val lyrics = ttmlIn(body) ?: timedAppleToTtml(body)
        lyrics?.takeIf { it.isUsableLyrics() }?.let { Result.success(it) } ?: notFound(name)
    }

    private suspend fun searchTrackId(title: String, artist: String, durationMs: Long): String? {
        val token = appleToken() ?: return null
        val url = APPLE_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("term", "$title $artist")
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .addQueryParameter("l", "en-US")
            .build()
        val body = catalogGet(
            url.toString(),
            mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $token",
                // Apple answers a token without these the way it answers a wrong one.
                "Origin" to "https://music.apple.com",
                "Referer" to "https://music.apple.com/",
            ),
        )
        if (body == null) {
            // A token past its life is the likeliest reason; the next song fetches a fresh one.
            cachedToken.set(null)
            return null
        }
        val root = runCatching { OpenLyricsHttp.json.parseToJsonElement(body) }.getOrNull() ?: return null
        val songs = ((root as? JsonObject)?.get("results") as? JsonObject)
            ?.get("songs")?.let { it as? JsonObject }?.get("data") as? JsonArray ?: return null
        return songs.mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            val attributes = song["attributes"] as? JsonObject ?: return@mapNotNull null
            val songId = song.string("id") ?: return@mapNotNull null
            val score = score(
                candidateTitle = attributes.string("name").orEmpty(),
                candidateArtist = attributes.string("artistName").orEmpty(),
                candidateMs = attributes.long("durationInMillis") ?: 0L,
                title = title,
                artist = artist,
                durationMs = durationMs,
            )
            songId to score
        }.maxByOrNull { it.second }?.takeIf { it.second >= MINIMUM_MATCH_SCORE }?.first
    }

    private fun score(
        candidateTitle: String,
        candidateArtist: String,
        candidateMs: Long,
        title: String,
        artist: String,
        durationMs: Long,
    ): Int {
        var score = textScore(candidateTitle, title, 20, 10) + textScore(candidateArtist, artist, 15, 5)
        if (durationMs > 0 && candidateMs > 0) {
            score += when {
                abs(candidateMs - durationMs) < 3_000 -> 10
                abs(candidateMs - durationMs) < 10_000 -> 5
                // A length this far out is another recording, however well the name matches.
                else -> -10
            }
        }
        return score
    }

    private fun textScore(candidate: String, wanted: String, exact: Int, partial: Int): Int = when {
        candidate.isBlank() || wanted.isBlank() -> 0
        candidate.equals(wanted, ignoreCase = true) -> exact
        candidate.contains(wanted, ignoreCase = true) || wanted.contains(candidate, ignoreCase = true) -> partial
        else -> 0
    }

    /** The web player's own developer token, read from the script its page loads. Cached. */
    private suspend fun appleToken(): String? = cachedToken.get() ?: tokenMutex.withLock {
        cachedToken.get() ?: run {
            val page = catalogGet("https://music.apple.com/us/new", mapOf("Accept" to "text/html")) ?: return@run null
            val script = APPLE_INDEX_SCRIPT.find(page)?.value ?: return@run null
            val javascript = catalogGet("https://music.apple.com$script", mapOf("Accept" to "*/*")) ?: return@run null
            APPLE_TOKEN.find(javascript)?.value
        }?.also(cachedToken::set)
    }

    /** A TTML document inside the response, however many JSON envelopes it came in. */
    private fun ttmlIn(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("<")) return trimmed.takeIf { LyricsUtils.isTtml(it) }
        val root = runCatching { OpenLyricsHttp.json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
        return findTtml(root)
    }

    private fun findTtml(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
            ?.let { if (it.contains("&lt;tt", ignoreCase = true)) unescape(it) else it }
            ?.takeIf { it.contains("<tt", ignoreCase = true) }
        is JsonArray -> element.firstNotNullOfOrNull(::findTtml)
        is JsonObject -> element.values.firstNotNullOfOrNull(::findTtml)
    }

    private fun unescape(value: String) = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

    /** PaxSenix's structured payload — rows of timestamped words — as word-timed TTML. */
    private fun timedAppleToTtml(body: String): String? {
        val root = runCatching { OpenLyricsHttp.json.parseToJsonElement(body) }.getOrNull() ?: return null
        val rows = findTimedContent(root)?.mapNotNull { it as? JsonObject } ?: return null
        val lines = rows.mapIndexedNotNull { index, row ->
            val start = row.long("timestamp") ?: return@mapIndexedNotNull null
            val wordRows = row["text"] as? JsonArray ?: return@mapIndexedNotNull null
            val next = rows.getOrNull(index + 1)?.long("timestamp")
            val words = wordRows.mapIndexedNotNull { wordIndex, element ->
                val word = element as? JsonObject ?: return@mapIndexedNotNull null
                val text = word.string("text")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
                val wordStart = word.long("timestamp") ?: return@mapIndexedNotNull null
                val wordEnd = (wordRows.getOrNull(wordIndex + 1) as? JsonObject)?.long("timestamp")
                    ?: next ?: (wordStart + 800)
                TimedWord(wordStart, wordEnd.coerceAtLeast(wordStart), text)
            }
            if (words.isEmpty()) return@mapIndexedNotNull null
            TimedLine(
                startMs = minOf(start, words.first().startMs),
                endMs = maxOf(words.last().endMs, next ?: words.last().endMs),
                text = words.joinToString(" ") { it.text },
                words = words,
            )
        }
        return timedLinesToTtml(lines)
    }

    private fun findTimedContent(element: JsonElement): JsonArray? = when (element) {
        is JsonObject -> (element["content"] as? JsonArray)?.takeIf { array ->
            array.any { (it as? JsonObject)?.get("timestamp") != null }
        } ?: element.values.firstNotNullOfOrNull(::findTimedContent)
        is JsonArray -> element.firstNotNullOfOrNull(::findTimedContent)
        else -> null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private val APPLE_INDEX_SCRIPT = Regex("""/assets/index~[^"]+\.js""")
    private val APPLE_TOKEN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
}

// ============================================================================================

/**
 * Musixmatch, the biggest lyrics database there is: its word-timed "rich sync" where a track has
 * one, and its line-timed subtitles otherwise. Asked the way its own web client asks, with a user
 * token and a signed request.
 */
object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMusixmatchLyricsKey] ?: true

    private const val BASE = "https://apic.musixmatch.com/ws/1.1"
    private const val APP_ID = "mobile-app-v1.0"
    private const val SEARCH_PAGE = "https://www.musixmatch.com/search"

    /** Used only if the page that carries the rotating key is briefly unavailable. */
    private const val FALLBACK_SIGNING_SECRET = "f09016176ba43a1cfd1031fbd6b3d26c"

    private val tokenMutex = Mutex()
    private val cachedSecret = AtomicReference<String?>(null)
    private val cachedToken = AtomicReference<String?>(null)
    private val processGuid = UUID.randomUUID().toString()

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        val track = searchTrack(title, artist)?.maxByOrNull { score(it, title, artist, duration) }
            ?.takeIf { score(it, title, artist, duration) >= 60.0 }
            ?: return@withContext notFound(name)

        if ((track.hasRichSync ?: 0) != 0) {
            val rich = fetchRichSync(track.trackId)?.let(::richSyncToTtml)?.takeIf { it.isUsableLyrics() }
            if (rich != null) return@withContext Result.success(rich)
        }
        val lrc = if ((track.hasSubtitles ?: 0) != 0) fetchSubtitle(track.trackId)?.let(::subtitleToLrc) else null
        lrc?.takeIf { it.isNotBlank() && it.isUsableLyrics() }?.let { Result.success(it) } ?: notFound(name)
    }

    private fun score(track: Track, title: String, artist: String, seconds: Int): Double {
        var score = 0.0
        val name = track.trackName.trim().lowercase(Locale.ROOT)
        val wanted = title.trim().lowercase(Locale.ROOT)
        score += when {
            name == wanted -> 80.0
            name.contains(wanted) || wanted.contains(name) -> 40.0
            else -> 0.0
        }
        if (track.artistName.trim().lowercase(Locale.ROOT).contains(artist.trim().lowercase(Locale.ROOT))) score += 40.0
        track.trackLength?.let { length ->
            if (seconds > 0) {
                score += when (abs(length - seconds)) {
                    in 0..2 -> 30.0
                    in 3..5 -> 15.0
                    in 6..10 -> 5.0
                    else -> -20.0
                }
            }
        }
        return score
    }

    private suspend fun searchTrack(title: String, artist: String): List<Track>? {
        val response = signedGet { token ->
            "$BASE/track.search".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("format", "json")
                .addQueryParameter("q_track", title)
                .addQueryParameter("q_artist", artist)
                .addQueryParameter("f_has_lyrics", "1")
                .addQueryParameter("s_track_rating", "desc")
                .addQueryParameter("quorum_factor", "1")
                .addQueryParameter("page_size", "10")
                .addQueryParameter("page", "1")
                .addQueryParameter("usertoken", token)
                .build()
        } ?: return null
        return runCatching { OpenLyricsHttp.json.decodeFromString<Envelope<TrackSearchBody>>(response) }
            .getOrNull()?.message?.body?.trackList?.map { it.track }
    }

    private suspend fun fetchSubtitle(trackId: Long): String? {
        val response = signedGet { token ->
            "$BASE/track.subtitle.get".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("format", "json")
                .addQueryParameter("track_id", trackId.toString())
                .addQueryParameter("subtitle_format", "mxm")
                .addQueryParameter("usertoken", token)
                .build()
        } ?: return null
        return runCatching { OpenLyricsHttp.json.decodeFromString<Envelope<SubtitleBody>>(response) }
            .getOrNull()?.message?.body?.subtitle?.subtitleBody
    }

    private suspend fun fetchRichSync(trackId: Long): String? {
        val response = signedGet { token ->
            "$BASE/track.richsync.get".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("format", "json")
                .addQueryParameter("track_id", trackId.toString())
                .addQueryParameter("usertoken", token)
                .build()
        } ?: return null
        return runCatching { OpenLyricsHttp.json.decodeFromString<Envelope<RichSyncBody>>(response) }
            .getOrNull()?.message?.body?.richsync?.richsyncBody
    }

    /** Rich sync as word-timed TTML: fragments joined into words at the spaces between them. */
    private fun richSyncToTtml(body: String): String? {
        val entries = runCatching { OpenLyricsHttp.json.decodeFromString<List<RichSyncEntry>>(body) }.getOrNull()
            ?: return null
        val lines = entries.mapNotNull { entry ->
            val lineStart = (entry.startSeconds * 1000).toLong()
            val lineEnd = maxOf(lineStart, (entry.endSeconds * 1000).toLong())
            val words = ArrayList<TimedWord>()
            val current = StringBuilder()
            var currentStart = lineStart
            var currentEnd = lineStart
            fun flush() {
                val text = current.toString().trim()
                current.setLength(0)
                if (text.isNotEmpty()) words += TimedWord(currentStart, maxOf(currentStart, currentEnd), text)
            }
            entry.fragments.forEachIndexed { index, fragment ->
                val raw = fragment.text
                if (raw.isEmpty()) return@forEachIndexed
                val start = maxOf(lineStart, ((entry.startSeconds + fragment.offsetSeconds) * 1000).toLong())
                val next = entry.fragments.getOrNull(index + 1)
                    ?.let { ((entry.startSeconds + it.offsetSeconds) * 1000).toLong() } ?: lineEnd
                val end = maxOf(start, minOf(lineEnd, next))
                if (raw.first().isWhitespace()) flush()
                val content = raw.trim()
                if (content.isNotEmpty()) {
                    if (current.isEmpty()) currentStart = start
                    current.append(content)
                    currentEnd = end
                }
                if (raw.last().isWhitespace()) flush()
            }
            flush()
            val text = entry.text.trim().ifEmpty { words.joinToString(" ") { it.text } }
            if (text.isEmpty()) return@mapNotNull null
            TimedLine(startMs = minOf(lineStart, words.firstOrNull()?.startMs ?: lineStart), endMs = lineEnd, text = text, words = words)
        }.sortedBy { it.startMs }
        return timedLinesToTtml(lines)
    }

    private fun subtitleToLrc(subtitleBody: String): String {
        val lines = runCatching { OpenLyricsHttp.json.decodeFromString<List<SubtitleLine>>(subtitleBody) }.getOrNull()
            ?: return ""
        return buildString {
            for (line in lines) {
                if (line.text.isBlank()) continue
                val totalMs = (line.time.total * 1000).toLong()
                appendLine(
                    "[" + String.format(Locale.US, "%02d:%02d.%02d", totalMs / 60_000, (totalMs / 1000) % 60, (totalMs % 1000) / 10) +
                        "]" + line.text,
                )
            }
        }.trim()
    }

    /** Signs and sends; a refused token refreshes both moving credentials once. */
    private suspend fun signedGet(buildUrl: (token: String) -> HttpUrl): String? {
        val secret = secret()
        val token = token(secret) ?: return null
        val first = catalogGet(sign(buildUrl(token).toString(), secret), mapOf("Accept" to "application/json"))
        if (first != null && !unauthorized(first)) return first
        cachedToken.set(null)
        cachedSecret.set(null)
        val freshSecret = secret()
        val freshToken = token(freshSecret) ?: return null
        return catalogGet(sign(buildUrl(freshToken).toString(), freshSecret), mapOf("Accept" to "application/json"))
    }

    private fun unauthorized(body: String): Boolean =
        runCatching { OpenLyricsHttp.json.decodeFromString<Envelope<JsonElement>>(body) }
            .getOrNull()?.message?.header?.statusCode?.let { it == 401 || it == 402 } ?: false

    private suspend fun token(secret: String): String? = cachedToken.get() ?: tokenMutex.withLock {
        cachedToken.get() ?: run {
            val url = "$BASE/token.get".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("guid", processGuid)
                .addQueryParameter("format", "json")
                .build()
            catalogGet(sign(url.toString(), secret), mapOf("Accept" to "application/json"))
                ?.let { runCatching { OpenLyricsHttp.json.decodeFromString<Envelope<TokenBody>>(it) }.getOrNull() }
                ?.message?.body?.userToken
        }?.also(cachedToken::set)
    }

    /** The web client's current signing key, from the script its search page loads. */
    private fun secret(): String = cachedSecret.get() ?: runCatching {
        val page = catalogGet(SEARCH_PAGE, mapOf("Accept" to "text/html", "Cookie" to "mxm_bab=AB"))!!
        val script = APP_SCRIPT.find(page)!!.groupValues[1]
        val scriptUrl = SEARCH_PAGE.toHttpUrl().resolve(script)!!.toString()
        val javascript = catalogGet(scriptUrl, mapOf("Accept" to "*/*", "Cookie" to "mxm_bab=AB"))!!
        val encoded = ENCODED_SECRET.find(javascript)!!.groupValues[1]
        String(Base64.getDecoder().decode(encoded.reversed()), Charsets.UTF_8).takeIf { it.isNotBlank() }!!
    }.getOrElse { FALLBACK_SIGNING_SECRET }.also(cachedSecret::set)

    private fun sign(url: String, secret: String): String {
        val normalized = url.replace("%20", "+").replace(" ", "+")
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal("$normalized$date".toByteArray(Charsets.UTF_8)))
        return "$normalized&signature=${java.net.URLEncoder.encode(signature, "UTF-8")}&signature_protocol=sha256"
    }

    @Serializable
    private data class Envelope<T>(val message: Message<T>)

    @Serializable
    private data class Message<T>(val header: Header, val body: T? = null)

    @Serializable
    private data class Header(@SerialName("status_code") val statusCode: Int = 0)

    @Serializable
    private data class TokenBody(@SerialName("user_token") val userToken: String)

    @Serializable
    private data class TrackSearchBody(@SerialName("track_list") val trackList: List<TrackWrapper> = emptyList())

    @Serializable
    private data class TrackWrapper(val track: Track)

    @Serializable
    private data class Track(
        @SerialName("track_id") val trackId: Long,
        @SerialName("track_name") val trackName: String,
        @SerialName("artist_name") val artistName: String = "",
        @SerialName("track_length") val trackLength: Int? = null,
        @SerialName("has_subtitles") val hasSubtitles: Int? = null,
        @SerialName("has_richsync") val hasRichSync: Int? = null,
    )

    @Serializable
    private data class SubtitleBody(val subtitle: Subtitle? = null)

    @Serializable
    private data class Subtitle(@SerialName("subtitle_body") val subtitleBody: String)

    @Serializable
    private data class SubtitleLine(val text: String, val time: SubtitleTime)

    @Serializable
    private data class SubtitleTime(val total: Double)

    @Serializable
    private data class RichSyncBody(val richsync: RichSync? = null)

    @Serializable
    private data class RichSync(@SerialName("richsync_body") val richsyncBody: String? = null)

    @Serializable
    private data class RichSyncEntry(
        @SerialName("ts") val startSeconds: Double,
        @SerialName("te") val endSeconds: Double,
        @SerialName("l") val fragments: List<RichSyncFragment> = emptyList(),
        @SerialName("x") val text: String = "",
    )

    @Serializable
    private data class RichSyncFragment(
        @SerialName("c") val text: String,
        @SerialName("o") val offsetSeconds: Double,
    )

    private val APP_SCRIPT = Regex("""src=["']([^"']*/_next/static/chunks/pages/_app-[^"']+\.js)["']""", RegexOption.IGNORE_CASE)
    private val ENCODED_SECRET = Regex("""from\(\s*["']([^"']+)["']\s*\.split""")
}
