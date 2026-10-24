/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The same sources as the Android app's lyrics/OpenLyricsProviders.kt, AppleCatalogLyricsProviders.kt
 * and LrcRedLyricsProvider.kt. The LyricsPlus, Binimum, Unison, Megalobiz, Genius, PaxSenix,
 * Musixmatch and lrc.red clients there follow the reference implementation.
 * Word-timed results come back as TTML, line-timed ones as LRC and the rest as plain text — what
 * [LyricsParser] already reads.
 */

package com.ozyern.exhale.desktop.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Which recording a song is, once something has worked it out: the ISRC that names exactly one
 * recording, and where the search that found it left the lyrics document.
 */
data class Recording(val isrc: String?, val lyricsUrl: String?, val durationSeconds: Int?)

/** One HTTP client and one JSON reader for the online sources. */
internal object OnlineHttp {
    private const val AGENT = "Exhale (https://github.com/ozyern/exhale)"
    const val BROWSER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** The body of a successful GET, or null for anything else — a miss is not an error here. */
    suspend fun get(
        url: String,
        accept: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        agent: String = AGENT,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", agent)
                .header("Accept", accept)
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string().takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }
}

internal data class TimedWord(val startMs: Long, val endMs: Long, val text: String)

internal data class TimedLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<TimedWord> = emptyList(),
    val agent: String? = null,
)

/** Timed lines as the TTML the lyric view reads word by word: a `<p>` per line, a `<span>` per word. */
internal fun timedLinesToTtml(lines: List<TimedLine>): String? {
    if (lines.isEmpty()) return null
    fun t(ms: Long) = String.format(Locale.ROOT, "%.3fs", ms.coerceAtLeast(0L) / 1000.0)
    fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val body = buildString {
        lines.forEach { line ->
            append("<p begin=\"").append(t(line.startMs)).append("\" end=\"").append(t(line.endMs)).append('"')
            line.agent?.let { append(" ttm:agent=\"").append(esc(it)).append('"') }
            append('>')
            if (line.words.isEmpty()) {
                append(esc(line.text))
            } else {
                line.words.forEachIndexed { index, word ->
                    if (index > 0) append(' ')
                    append("<span begin=\"").append(t(word.startMs)).append("\" end=\"")
                        .append(t(word.endMs)).append("\">").append(esc(word.text)).append("</span>")
                }
            }
            append("</p>")
        }
    }
    return "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">" +
        "<body><div>$body</div></body></tt>"
}

private val ENHANCED_WORD = Regex("""<(\d{1,2}):(\d{1,2}(?:\.\d{1,3})?)>""")
private val LRC_LINE = Regex("""^\[(\d{1,2}):(\d{1,2}(?:\.\d{1,3})?)](.*)$""")

private fun clockMs(minutes: String, seconds: String): Long =
    minutes.toLong() * 60_000L + (seconds.toDouble() * 1000.0).toLong()

/**
 * Enhanced LRC — `[00:12.30]<00:12.30>word <00:12.80>word` — as TTML, keeping the word timing. A
 * stamped run with no space after it is a syllable of a word that carries on, and is glued to the
 * next rather than shown as a word of its own.
 */
internal fun enhancedLrcToTtml(lrc: String): String? {
    if (!ENHANCED_WORD.containsMatchIn(lrc)) return null
    val parsed = lrc.lineSequence().mapNotNull { raw ->
        val match = LRC_LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
        clockMs(match.groupValues[1], match.groupValues[2]) to match.groupValues[3]
    }.toList()
    if (parsed.isEmpty()) return null
    val lines = parsed.mapIndexed { index, (start, content) ->
        val nextStart = parsed.getOrNull(index + 1)?.first ?: (start + 5_000L)
        val stamps = ENHANCED_WORD.findAll(content).toList()
        val runs = stamps.mapIndexed { i, stamp ->
            val textEnd = stamps.getOrNull(i + 1)?.range?.first ?: content.length
            val raw = content.substring(stamp.range.last + 1, textEnd)
            val wordStart = clockMs(stamp.groupValues[1], stamp.groupValues[2])
            val end = stamps.getOrNull(i + 1)?.let { clockMs(it.groupValues[1], it.groupValues[2]) }
                ?: minOf(nextStart, wordStart + 3_000L)
            Triple(wordStart, end.coerceAtLeast(wordStart), raw)
        }
        val glue = runs.any { (_, _, raw) -> raw.trim().isNotEmpty() && raw.any(Char::isWhitespace) }
        val words = mutableListOf<TimedWord>()
        var pending: TimedWord? = null
        for ((wordStart, end, raw) in runs) {
            val text = raw.trim()
            if (text.isEmpty()) {
                pending?.let(words::add); pending = null
                continue
            }
            val held = pending
            pending = if (held != null && glue && !raw.first().isWhitespace()) {
                TimedWord(held.startMs, maxOf(held.endMs, end), held.text + text)
            } else {
                held?.let(words::add)
                TimedWord(wordStart, end, text)
            }
            if (!glue || raw.last().isWhitespace()) {
                pending?.let(words::add); pending = null
            }
        }
        pending?.let(words::add)
        TimedLine(
            startMs = start,
            endMs = words.lastOrNull()?.endMs ?: nextStart,
            text = ENHANCED_WORD.replace(content, "").replace(Regex("\\s+"), " ").trim(),
            words = words,
        )
    }.filter { it.text.isNotBlank() }
    return timedLinesToTtml(lines)
}

internal fun isTtml(text: String): Boolean {
    val trimmed = text.trimStart()
    return trimmed.startsWith("<") && (trimmed.contains("<tt", ignoreCase = true) || trimmed.contains("ns/ttml"))
}

/** Whether a fetched text will actually show as lyrics. */
internal fun String.isUsableLyrics(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return false
    return when {
        isTtml(trimmed) || trimmed.startsWith("[") -> LyricsParser.parse(trimmed).any { it.text.isNotBlank() }
        else -> true
    }
}

/** A source the lyrics lookup can ask. [recording] is only used by the sources that can. */
internal interface OnlineSource {
    val name: String

    /** Whether this source answers better once [Binimum] has named the recording. */
    val usesRecording: Boolean get() = false

    suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String?
}

// ============================================================================================

/**
 * Syllable-timed lyrics from LyricsPlus, the open backend behind the YouLy+ extension. Its volunteer
 * mirrors come and go, so all are asked at once and the first real answer wins; the winner is asked
 * first next time.
 */
internal object LyricsPlus : OnlineSource {
    override val name = "LyricsPlus"
    override val usesRecording = true

    private val mirrors = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )
    private val lastGood = AtomicReference<String?>(null)

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? =
        coroutineScope {
            val hosts = lastGood.get()?.let { good -> listOf(good) + mirrors.filterNot { it == good } } ?: mirrors
            val pending: MutableList<Pair<String, Deferred<String?>>> = hosts.map { host ->
                host to async(Dispatchers.IO) { fetch(host, title, artist, album, duration, recording?.isrc) }
            }.toMutableList()
            try {
                // The first mirror to answer with something, not the first to answer at all.
                while (pending.isNotEmpty()) {
                    val (host, result) = select<Pair<String, String?>> {
                        pending.forEach { (host, job) -> job.onAwait { host to it } }
                    }
                    pending.removeAll { it.first == host }
                    if (result != null) {
                        lastGood.set(host)
                        return@coroutineScope result
                    }
                }
                null
            } finally {
                pending.forEach { it.second.cancel() }
            }
        }

    private suspend fun fetch(host: String, title: String, artist: String, album: String?, duration: Int, isrc: String?): String? {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                if (duration > 0) addQueryParameter("duration", duration.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
                if (!isrc.isNullOrBlank()) addQueryParameter("isrc", isrc)
            }
            .build()
        val body = OnlineHttp.get(url.toString()) ?: return null
        val response = runCatching { OnlineHttp.json.decodeFromString<Response>(body) }.getOrNull() ?: return null
        return timedLinesToTtml(parse(response))?.takeIf { it.isUsableLyrics() }
    }

    private fun parse(response: Response): List<TimedLine> {
        val agents = response.metadata?.agents.orEmpty()
        return response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            val singer = (line.element as? JsonObject)?.get("singer")?.let { it as? JsonPrimitive }?.contentOrNull
            val agent = singer?.takeIf { it.matches(Regex("v\\d+")) }
                ?: singer?.let { name -> agents.entries.firstOrNull { it.value.alias == name }?.key }
                    ?.takeIf { it.matches(Regex("v\\d+")) }
                ?: if (line.element.saysOpposite()) "v2" else null
            when {
                words.isNotEmpty() -> TimedLine(
                    startMs = minOf(start, words.first().startMs),
                    endMs = maxOf(words.last().endMs, start + (line.duration ?: 0L)),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                    agent = agent,
                )
                !line.text.isNullOrBlank() -> TimedLine(
                    startMs = start,
                    endMs = start + (line.duration?.takeIf { it > 0 } ?: 4_000L),
                    text = line.text.trim(),
                    agent = agent,
                )
                else -> null
            }
        }.sortedBy { it.startMs }
    }

    private fun JsonElement?.saysOpposite(): Boolean {
        val array = runCatching { this?.jsonArray }.getOrNull() ?: return false
        return array.any { entry ->
            val tag = runCatching { entry.jsonPrimitive.contentOrNull }.getOrNull()
            tag == "opposite" || tag == "right"
        }
    }

    /** The API's own spacing is the word boundary: it sends "e" then "nough ", which are one word. */
    private fun mergeSyllables(syllables: List<Syllable>): List<TimedWord> {
        val words = mutableListOf<TimedWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L
        syllables.forEach { syllable ->
            val text = syllable.text ?: return@forEach
            if (text.isBlank()) return@forEach
            val time = syllable.time ?: return@forEach
            if (current.isEmpty()) start = time
            current.append(text.trim())
            end = time + (syllable.duration ?: 0L)
            if (text.last().isWhitespace()) {
                words += TimedWord(start, end, current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words += TimedWord(start, end, current.toString())
        return words
    }

    @Serializable
    private data class Response(val type: String? = null, val lyrics: List<Line>? = null, val metadata: Metadata? = null)

    @Serializable
    private data class Metadata(val agents: Map<String, Agent>? = null)

    @Serializable
    private data class Agent(val type: String? = null, val alias: String? = null)

    @Serializable
    private data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
        val element: JsonElement? = null,
    )

    @Serializable
    private data class Syllable(val time: Long? = null, val duration: Long? = null, val text: String? = null)
}

// ============================================================================================

/**
 * Word-timed TTML from binimum's open index of the Apple catalogue — and the one source that names
 * the recording it matched (its ISRC), which is how lrc.red and LyricsPlus get to ask for the same one.
 */
internal object Binimum : OnlineSource {
    override val name = "Binimum"
    override val usesRecording = true

    private const val BASE = "https://lyrics-api.binimum.org/"

    /** Which recording this is, without fetching its words. */
    suspend fun identify(title: String, artist: String, album: String?, duration: Int, isrc: String? = null): Recording? {
        val url = BASE.toHttpUrl().newBuilder()
            .apply {
                if (!isrc.isNullOrBlank()) {
                    addQueryParameter("isrc", isrc)
                } else {
                    addQueryParameter("track", title)
                    addQueryParameter("artist", artist)
                    if (!album.isNullOrBlank()) addQueryParameter("album", album)
                    if (duration > 0) addQueryParameter("duration", duration.toString())
                }
            }
            .build()
        val body = OnlineHttp.get(url.toString()) ?: return null
        val hit = runCatching { OnlineHttp.json.decodeFromString<Response>(body) }.getOrNull()
            ?.results?.firstOrNull { !it.lyricsUrl.isNullOrBlank() }
            ?: return null
        // A result whose length is far from the song's is another recording of it.
        if (isrc.isNullOrBlank() && duration > 0 && hit.duration != null && hit.duration > 0 && abs(hit.duration - duration) > 12) {
            return null
        }
        return Recording(hit.isrc?.takeIf { it.isNotBlank() }, hit.lyricsUrl, hit.duration)
    }

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val found = recording?.takeIf { !it.lyricsUrl.isNullOrBlank() }
            ?: identify(title, artist, album, duration, recording?.isrc)
            ?: return null
        return OnlineHttp.get(found.lyricsUrl!!, accept = "application/ttml+xml, application/xml, text/xml, */*")
            ?.takeIf { isTtml(it) && it.isUsableLyrics() }
    }

    @Serializable
    private data class Response(val results: List<Hit>? = null)

    @Serializable
    private data class Hit(
        @SerialName("track_name") val trackName: String? = null,
        @SerialName("artist_name") val artistName: String? = null,
        val duration: Int? = null,
        val isrc: String? = null,
        val lyricsUrl: String? = null,
    )
}

// ============================================================================================

/**
 * lrc.red — Apple Music TTML, per syllable, filed by ISRC at `https://lrc.red/s/{ISRC}.ttml`. With
 * the recording known it costs one request and can't come back as the wrong edit; without, its
 * search is asked and every hit has to pass every test (see [best]).
 */
internal object LrcRed : OnlineSource {
    override val name = "lrc.red"
    override val usesRecording = true

    private const val BASE = "https://lrc.red/"
    private const val TTML = "application/ttml+xml, application/xml, text/xml, */*"
    private const val DURATION_TOLERANCE_SECONDS = 3.0
    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")

    private fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC.matches(it) }?.let { "${BASE}s/$it.ttml" }

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val known = recording?.isrc?.let(::documentUrl)
        if (known != null) document(known)?.let { return it }
        // Often filed under a sibling release's code, so a known ISRC that misses still gets a search.
        val hit = search(title, artist, duration) ?: return null
        val found = hit.isrc?.let(::documentUrl)?.takeIf { it != known } ?: return null
        return document(found)
    }

    private suspend fun document(url: String): String? =
        OnlineHttp.get(url, accept = TTML)?.takeIf { isTtml(it) && it.isUsableLyrics() }

    private suspend fun search(title: String, artist: String, duration: Int): Hit? {
        val url = "${BASE}search.json".toHttpUrl().newBuilder().addQueryParameter("q", "$title $artist".trim()).build()
        val body = OnlineHttp.get(url.toString()) ?: return null
        val hits = runCatching { OnlineHttp.json.decodeFromString<Response>(body) }.getOrNull()?.hits ?: return null
        return best(hits, title, artist, duration)
    }

    /**
     * The hit that is this recording, or none: the same title outside its brackets, the same version
     * words, a shared artist and a length within [DURATION_TOLERANCE_SECONDS]. Closest length wins.
     */
    private fun best(hits: List<Hit>, title: String, artist: String, duration: Int): Hit? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)
        fun distance(hit: Hit): Double = if (duration > 0 && hit.duration != null) abs(hit.duration - duration) else 0.0
        return hits.filter { hit ->
            val name = hit.title ?: return@filter false
            hit.isrc?.let(::documentUrl) != null &&
                coreOf(name) == wantedTitle &&
                versionOf(name) == wantedVersion &&
                (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                distance(hit) <= DURATION_TOLERANCE_SECONDS
        }.minByOrNull(::distance)
    }

    private fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    private fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } + " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    private fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )

    @Serializable
    private data class Response(val hits: List<Hit>? = null)

    @Serializable
    private data class Hit(val isrc: String? = null, val title: String? = null, val artist: String? = null, val duration: Double? = null)
}

// ============================================================================================

/** Community-voted lyrics from Unison: TTML, word-stamped LRC, plain LRC or plain text. */
internal object Unison : OnlineSource {
    override val name = "Unison"
    private const val BASE = "https://unison.boidu.dev/lyrics"

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("song", title)
            .addQueryParameter("artist", artist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
                if (duration > 0) addQueryParameter("duration", duration.toString())
            }
            .build()
        val body = OnlineHttp.get(url.toString()) ?: return null
        val data = runCatching { OnlineHttp.json.decodeFromString<Response>(body) }.getOrNull()
            ?.takeIf { it.success == true }?.data ?: return null
        val text = data.lyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lyrics = when {
            data.format.equals("ttml", ignoreCase = true) || isTtml(text) -> text
            data.syncType.equals("plain", ignoreCase = true) -> text
            else -> enhancedLrcToTtml(text) ?: text
        }
        return lyrics.takeIf { it.isUsableLyrics() }
    }

    @Serializable
    private data class Response(val success: Boolean? = null, val data: Entry? = null)

    @Serializable
    private data class Entry(val lyrics: String? = null, val format: String? = null, val syncType: String? = null)
}

// ============================================================================================

/** Line-timed LRC files from Megalobiz's library. */
internal object Megalobiz : OnlineSource {
    override val name = "Megalobiz"
    private const val BASE = "https://www.megalobiz.com"
    private val lrcLink = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val lrcBody = Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val search = "$BASE/searchall".toHttpUrl().newBuilder().addQueryParameter("qry", "$artist $title".trim()).build()
        val results = OnlineHttp.get(search.toString(), accept = "text/html") ?: return null
        // The first hit has to be about this song: its link carries the title as a slug.
        val slug = title.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        val path = lrcLink.findAll(results).map { it.groupValues[1] }
            .firstOrNull { slug.isEmpty() || it.lowercase(Locale.ROOT).contains(slug.take(24)) }
            ?: return null
        val page = OnlineHttp.get(BASE + path.replace("&amp;", "&"), accept = "text/html") ?: return null
        val raw = lrcBody.find(page)?.groupValues?.get(1) ?: return null
        val lrc = Parser.unescapeEntities(raw.replace(Regex("""(?i)<br\s*/?>"""), "\n").replace(Regex("""<[^>]+>"""), ""), false)
            .lines().map { it.trim() }.filter { LRC_LINE.matches(it) }.joinToString("\n")
        return lrc.takeIf { it.isUsableLyrics() }
    }
}

// ============================================================================================

/** Plain lyrics from Genius, for the songs no timed source has. */
internal object Genius : OnlineSource {
    override val name = "Genius"

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder().addQueryParameter("q", "$artist $title".trim()).build()
        val body = OnlineHttp.get(url.toString()) ?: return null
        val songUrl = runCatching { bestMatch(body, title, artist) }.getOrNull() ?: return null
        val html = OnlineHttp.get(songUrl, accept = "text/html") ?: return null
        return withContext(Dispatchers.Default) { runCatching { parseHtml(html) }.getOrNull() }
    }

    private fun bestMatch(body: String, targetTitle: String, targetArtist: String): String? {
        val root = OnlineHttp.json.parseToJsonElement(body).jsonObject
        val sections = root["response"]?.jsonObject?.get("sections")?.jsonArray ?: return null
        val songs = sections.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "song" }
            ?.jsonObject?.get("hits")?.jsonArray ?: return null
        val title = targetTitle.lowercase(Locale.ROOT)
        val artist = targetArtist.lowercase(Locale.ROOT)
        return songs.mapNotNull { (it as? JsonObject)?.get("result")?.jsonObject }
            .mapNotNull { item ->
                val t = item["title"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT).orEmpty()
                val a = item["artist_names"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT).orEmpty()
                val path = item["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val titleHit = t.isNotBlank() && (t == title || t.contains(title) || title.contains(t))
                val artistHit = a.isNotBlank() && (a == artist || a.contains(artist) || artist.contains(a))
                // Both, or it is somebody else's song with the same name.
                if (!titleHit || !artistHit) return@mapNotNull null
                var score = if (t == title) 50 else 25
                score += if (a == artist) 40 else 20
                if (path.contains("translation", ignoreCase = true)) score -= 60
                if (path.contains("tracklist", ignoreCase = true) || path.contains("album-art", ignoreCase = true)) score -= 60
                item["url"]?.jsonPrimitive?.contentOrNull?.takeIf { score > 0 }?.let { it to score }
            }
            .maxByOrNull { it.second }?.first
    }

    private fun parseHtml(html: String): String? {
        val doc = Jsoup.parse(html)
        val containers = doc.select("div[data-lyrics-container=true]").ifEmpty { doc.select("div.lyrics") }
        if (containers.isEmpty()) return null
        val text = buildString {
            containers.forEach { container ->
                container.select(
                    "[data-exclude-from-selection=true], .LyricsHeader__Container, " +
                        ".SongBioPreview__Container, .InreadAd__Container, button, script, style",
                ).remove()
                container.select("br").forEach { it.replaceWith(TextNode("\n")) }
                append(container.wholeText()).append('\n')
            }
        }
        val lines = text.replace(' ', ' ').replace("​", "")
            .replace(Regex("""\d*You might also like""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d*Embed\s*$""", RegexOption.IGNORE_CASE), "")
            .lines().map { it.trim() }
            // Section labels ("[Chorus]") are Genius's notes, not lyrics.
            .filterNot { it.startsWith("[") && it.endsWith("]") }
        val collapsed = mutableListOf<String>()
        lines.forEach { line -> if (line.isNotEmpty() || collapsed.lastOrNull()?.isNotEmpty() == true) collapsed += line }
        return collapsed.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }
}

// ============================================================================================

/** The web player's own developer token, read from the script its page loads. Shared, cached. */
internal object AppleWebToken {
    private val mutex = Mutex()
    private val cached = AtomicReference<String?>(null)
    private val INDEX_SCRIPT = Regex("""/assets/index[~-][^"]+\.js""")
    private val TOKEN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    suspend fun get(): String? = cached.get() ?: mutex.withLock {
        cached.get() ?: run {
            val page = OnlineHttp.get("https://music.apple.com/us/new", accept = "text/html", agent = OnlineHttp.BROWSER_AGENT) ?: return@run null
            val script = INDEX_SCRIPT.find(page)?.value ?: return@run null
            val javascript = OnlineHttp.get("https://music.apple.com$script", accept = "*/*", agent = OnlineHttp.BROWSER_AGENT) ?: return@run null
            TOKEN.find(javascript)?.value
        }?.also(cached::set)
    }

    /** A token past its life is the likeliest reason for a refusal; the next call fetches a fresh one. */
    fun forget() = cached.set(null)
}

/**
 * Apple Music's own word-timed lyrics, found by asking Apple's catalogue which track this is —
 * every candidate scored on title, artist and length — then fetched for that one track id.
 */
internal object PaxSenix : OnlineSource {
    override val name = "PaxSenix"

    private const val PUBLIC_PROXY = "https://lyrics.paxsenix.org"
    private const val APPLE_SEARCH = "https://amp-api.music.apple.com/v1/catalog/us/search"
    private const val MINIMUM_MATCH_SCORE = 10

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val trackId = searchTrackId(title, artist, duration * 1000L) ?: return null
        val url = "$PUBLIC_PROXY/apple-music/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("id", trackId)
            .addQueryParameter("ttml", "true")
            .build()
        val body = OnlineHttp.get(url.toString(), accept = "application/json, application/ttml+xml, */*", agent = OnlineHttp.BROWSER_AGENT)
            ?: return null
        return (ttmlIn(body) ?: timedAppleToTtml(body))?.takeIf { it.isUsableLyrics() }
    }

    private suspend fun searchTrackId(title: String, artist: String, durationMs: Long): String? {
        val token = AppleWebToken.get() ?: return null
        val url = APPLE_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("term", "$title $artist")
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .addQueryParameter("l", "en-US")
            .build()
        val body = OnlineHttp.get(
            url.toString(),
            headers = mapOf(
                "Authorization" to "Bearer $token",
                // Apple answers a token without these the way it answers a wrong one.
                "Origin" to "https://music.apple.com",
                "Referer" to "https://music.apple.com/",
            ),
            agent = OnlineHttp.BROWSER_AGENT,
        )
        if (body == null) {
            AppleWebToken.forget()
            return null
        }
        val root = runCatching { OnlineHttp.json.parseToJsonElement(body) }.getOrNull() ?: return null
        val songs = ((root as? JsonObject)?.get("results") as? JsonObject)
            ?.get("songs")?.let { it as? JsonObject }?.get("data") as? JsonArray ?: return null
        return songs.mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            val attributes = song["attributes"] as? JsonObject ?: return@mapNotNull null
            val songId = song.string("id") ?: return@mapNotNull null
            var score = textScore(attributes.string("name").orEmpty(), title, 20, 10) +
                textScore(attributes.string("artistName").orEmpty(), artist, 15, 5)
            val candidateMs = attributes.long("durationInMillis") ?: 0L
            if (durationMs > 0 && candidateMs > 0) {
                score += when {
                    abs(candidateMs - durationMs) < 3_000 -> 10
                    abs(candidateMs - durationMs) < 10_000 -> 5
                    // A length this far out is another recording, however well the name matches.
                    else -> -10
                }
            }
            songId to score
        }.maxByOrNull { it.second }?.takeIf { it.second >= MINIMUM_MATCH_SCORE }?.first
    }

    private fun textScore(candidate: String, wanted: String, exact: Int, partial: Int): Int = when {
        candidate.isBlank() || wanted.isBlank() -> 0
        candidate.equals(wanted, ignoreCase = true) -> exact
        candidate.contains(wanted, ignoreCase = true) || wanted.contains(candidate, ignoreCase = true) -> partial
        else -> 0
    }

    private fun ttmlIn(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("<")) return trimmed.takeIf { isTtml(it) }
        val root = runCatching { OnlineHttp.json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
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
        val root = runCatching { OnlineHttp.json.parseToJsonElement(body) }.getOrNull() ?: return null
        val rows = findTimedContent(root)?.mapNotNull { it as? JsonObject } ?: return null
        val lines = rows.mapIndexedNotNull { index, row ->
            val start = row.long("timestamp") ?: return@mapIndexedNotNull null
            val wordRows = row["text"] as? JsonArray ?: return@mapIndexedNotNull null
            val next = rows.getOrNull(index + 1)?.long("timestamp")
            val words = wordRows.mapIndexedNotNull { wordIndex, element ->
                val word = element as? JsonObject ?: return@mapIndexedNotNull null
                val text = word.string("text")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
                val wordStart = word.long("timestamp") ?: return@mapIndexedNotNull null
                val wordEnd = (wordRows.getOrNull(wordIndex + 1) as? JsonObject)?.long("timestamp") ?: next ?: (wordStart + 800)
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
}

// ============================================================================================

/**
 * Musixmatch: its word-timed "rich sync" where a track has one, line-timed subtitles otherwise.
 * Asked the way its own web client asks, with a user token and a signed request.
 */
internal object Musixmatch : OnlineSource {
    override val name = "Musixmatch"

    private const val BASE = "https://apic.musixmatch.com/ws/1.1"
    private const val APP_ID = "mobile-app-v1.0"
    private const val SEARCH_PAGE = "https://www.musixmatch.com/search"

    /** Used only if the page that carries the rotating key is briefly unavailable. */
    private const val FALLBACK_SIGNING_SECRET = "f09016176ba43a1cfd1031fbd6b3d26c"

    private val tokenMutex = Mutex()
    private val cachedSecret = AtomicReference<String?>(null)
    private val cachedToken = AtomicReference<String?>(null)
    private val processGuid = UUID.randomUUID().toString()

    override suspend fun lyrics(title: String, artist: String, album: String?, duration: Int, recording: Recording?): String? {
        val track = searchTrack(title, artist)?.maxByOrNull { score(it, title, artist, duration) }
            ?.takeIf { score(it, title, artist, duration) >= 60.0 }
            ?: return null
        if ((track.hasRichSync ?: 0) != 0) {
            fetchRichSync(track.trackId)?.let(::richSyncToTtml)?.takeIf { it.isUsableLyrics() }?.let { return it }
        }
        val lrc = if ((track.hasSubtitles ?: 0) != 0) fetchSubtitle(track.trackId)?.let(::subtitleToLrc) else null
        return lrc?.takeIf { it.isNotBlank() && it.isUsableLyrics() }
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
        return runCatching { OnlineHttp.json.decodeFromString<Envelope<TrackSearchBody>>(response) }
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
        return runCatching { OnlineHttp.json.decodeFromString<Envelope<SubtitleBody>>(response) }
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
        return runCatching { OnlineHttp.json.decodeFromString<Envelope<RichSyncBody>>(response) }
            .getOrNull()?.message?.body?.richsync?.richsyncBody
    }

    /** Rich sync as word-timed TTML: fragments joined into words at the spaces between them. */
    private fun richSyncToTtml(body: String): String? {
        val entries = runCatching { OnlineHttp.json.decodeFromString<List<RichSyncEntry>>(body) }.getOrNull() ?: return null
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
                val next = entry.fragments.getOrNull(index + 1)?.let { ((entry.startSeconds + it.offsetSeconds) * 1000).toLong() } ?: lineEnd
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
        val lines = runCatching { OnlineHttp.json.decodeFromString<List<SubtitleLine>>(subtitleBody) }.getOrNull() ?: return ""
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
        val first = mxmGet(sign(buildUrl(token).toString(), secret))
        if (first != null && !unauthorized(first)) return first
        cachedToken.set(null)
        cachedSecret.set(null)
        val freshSecret = secret()
        val freshToken = token(freshSecret) ?: return null
        return mxmGet(sign(buildUrl(freshToken).toString(), freshSecret))
    }

    private suspend fun mxmGet(url: String, accept: String = "application/json", cookie: Boolean = false): String? =
        OnlineHttp.get(url, accept = accept, headers = if (cookie) mapOf("Cookie" to "mxm_bab=AB") else emptyMap(), agent = OnlineHttp.BROWSER_AGENT)

    private fun unauthorized(body: String): Boolean =
        runCatching { OnlineHttp.json.decodeFromString<Envelope<JsonElement>>(body) }
            .getOrNull()?.message?.header?.statusCode?.let { it == 401 || it == 402 } ?: false

    private suspend fun token(secret: String): String? = cachedToken.get() ?: tokenMutex.withLock {
        cachedToken.get() ?: run {
            val url = "$BASE/token.get".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("guid", processGuid)
                .addQueryParameter("format", "json")
                .build()
            mxmGet(sign(url.toString(), secret))
                ?.let { runCatching { OnlineHttp.json.decodeFromString<Envelope<TokenBody>>(it) }.getOrNull() }
                ?.message?.body?.userToken
        }?.also(cachedToken::set)
    }

    /** The web client's current signing key, from the script its search page loads. */
    private suspend fun secret(): String = cachedSecret.get() ?: (
        runCatching {
            val page = mxmGet(SEARCH_PAGE, accept = "text/html", cookie = true)!!
            val script = APP_SCRIPT.find(page)!!.groupValues[1]
            val scriptUrl = SEARCH_PAGE.toHttpUrl().resolve(script)!!.toString()
            val javascript = mxmGet(scriptUrl, accept = "*/*", cookie = true)!!
            val encoded = ENCODED_SECRET.find(javascript)!!.groupValues[1]
            String(Base64.getDecoder().decode(encoded.reversed()), Charsets.UTF_8).takeIf { it.isNotBlank() }!!
        }.getOrNull() ?: FALLBACK_SIGNING_SECRET
        ).also(cachedSecret::set)

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
    private data class RichSyncFragment(@SerialName("c") val text: String, @SerialName("o") val offsetSeconds: Double)

    private val APP_SCRIPT = Regex("""src=["']([^"']*/_next/static/chunks/pages/_app-[^"']+\.js)["']""", RegexOption.IGNORE_CASE)
    private val ENCODED_SECRET = Regex("""from\(\s*["']([^"']+)["']\s*\.split""")
}

/** Which recording each song is, once Binimum has said. Remembered for the session. */
internal object Recordings {
    private val known = ConcurrentHashMap<String, Recording>()

    suspend fun identify(songId: String, title: String, artist: String, album: String?, duration: Int): Recording? {
        known[songId]?.let { return it }
        return runCatching { Binimum.identify(title, artist, album, duration) }.getOrNull()?.also { known[songId] = it }
    }
}
