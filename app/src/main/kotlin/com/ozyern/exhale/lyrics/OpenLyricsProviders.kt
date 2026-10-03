/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The LyricsPlus, BiniLyrics, Unison, Megalobiz and Genius clients follow BitChord's
 * (github.com/kushagrasinghx/BitChord, GPL-3.0), adapted to hand Exhale the formats it already
 * stores: word-timed results become TTML, line-timed ones stay LRC, and plain text stays plain.
 */

package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.constants.EnableBinimumLyricsKey
import com.ozyern.exhale.constants.EnableGeniusLyricsKey
import com.ozyern.exhale.constants.EnableLyricsPlusKey
import com.ozyern.exhale.constants.EnableMegalobizLyricsKey
import com.ozyern.exhale.constants.EnableUnisonLyricsKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** One HTTP client and one JSON reader for the open lyrics sources. */
internal object OpenLyricsHttp {
    private const val AGENT = "Exhale (https://github.com/ozyern/exhale)"

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
    suspend fun get(url: String, accept: String = "application/json"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", AGENT)
                .header("Accept", accept)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }
}

/** A word inside a timed line, in milliseconds. */
internal data class TimedWord(val startMs: Long, val endMs: Long, val text: String)

/** A line with its own start and end, and its words where the source timed them. */
internal data class TimedLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<TimedWord> = emptyList(),
    val agent: String? = null,
)

/**
 * Writes timed lines as the TTML Exhale's lyric view already reads for word-by-word highlighting:
 * one `<p>` per line and one `<span>` per word, with single spaces between the spans.
 */
internal fun timedLinesToTtml(lines: List<TimedLine>): String? {
    if (lines.isEmpty()) return null
    fun t(ms: Long) = String.format(Locale.ROOT, "%.3fs", ms.coerceAtLeast(0L) / 1000.0)
    fun esc(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
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
 * Enhanced LRC — `[00:12.30]<00:12.30>word <00:12.80>word` — as TTML, keeping the word timing a
 * plain LRC reader would throw away. Null when the text carries no word stamps at all.
 */
internal fun enhancedLrcToTtml(lrc: String): String? {
    if (!ENHANCED_WORD.containsMatchIn(lrc)) return null
    val parsed = lrc.lineSequence().mapNotNull { raw ->
        val match = LRC_LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
        val start = clockMs(match.groupValues[1], match.groupValues[2])
        start to match.groupValues[3]
    }.toList()
    if (parsed.isEmpty()) return null
    val lines = parsed.mapIndexed { index, (start, content) ->
        val nextStart = parsed.getOrNull(index + 1)?.first ?: (start + 5_000L)
        val stamps = ENHANCED_WORD.findAll(content).toList()
        val words = stamps.mapIndexedNotNull { i, stamp ->
            val textEnd = stamps.getOrNull(i + 1)?.range?.first ?: content.length
            val text = content.substring(stamp.range.last + 1, textEnd).trim()
            if (text.isEmpty()) return@mapIndexedNotNull null
            val wordStart = clockMs(stamp.groupValues[1], stamp.groupValues[2])
            val wordEnd = stamps.getOrNull(i + 1)
                ?.let { clockMs(it.groupValues[1], it.groupValues[2]) }
                ?: nextStart
            TimedWord(wordStart, wordEnd.coerceAtLeast(wordStart), text)
        }
        val text = ENHANCED_WORD.replace(content, "").replace(Regex("\\s+"), " ").trim()
        TimedLine(
            startMs = start,
            endMs = words.lastOrNull()?.endMs ?: nextStart,
            text = text,
            words = words,
        )
    }.filter { it.text.isNotBlank() }
    return timedLinesToTtml(lines)
}

/** Whether a fetched text will actually show as lyrics once stored. */
internal fun String.isUsableLyrics(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return false
    return when {
        LyricsUtils.isTtml(trimmed) -> LyricsUtils.parseTtml(trimmed).isNotEmpty()
        trimmed.startsWith("[") -> LyricsUtils.parseLyrics(trimmed).any { it.text.isNotBlank() }
        else -> true
    }
}

internal fun notFound(source: String): Result<String> = Result.failure(NoSuchElementException("$source: no match"))

// ============================================================================================

/**
 * Syllable-timed lyrics from LyricsPlus, the open backend behind the YouLy+ extension.
 *
 * It runs on volunteer mirrors, and at any moment several are rate-limited or gone, so all of them
 * are asked at once and the first real answer wins. The winner is remembered, so the next song
 * goes straight to a mirror that was up a minute ago.
 */
object LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLyricsPlusKey] ?: true

    private val mirrors = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )
    private val lastGood = AtomicReference<String?>(null)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = getLyrics(id, title, artist, album, duration, recording = null)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        recording: Recording?,
    ): Result<String> {
        val isrc = recording?.isrc
        val ttml = coroutineScope {
            val hosts = lastGood.get()?.let { good -> listOf(good) + mirrors.filterNot { it == good } } ?: mirrors
            val pending: MutableList<Pair<String, Deferred<String?>>> = hosts.map { host ->
                host to async(Dispatchers.IO) { fetch(host, title, artist, album, duration, isrc) }
            }.toMutableList()
            try {
                // The first mirror to answer with something, not the first to answer at all: one
                // that 404s this song must not beat one that has it.
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
        return ttml?.let { Result.success(it) } ?: notFound(name)
    }

    private suspend fun fetch(host: String, title: String, artist: String, album: String?, duration: Int, isrc: String?): String? {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                if (duration > 0) addQueryParameter("duration", duration.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
                // Names one recording, so a single and its album cut can't be confused.
                if (!isrc.isNullOrBlank()) addQueryParameter("isrc", isrc)
            }
            .build()
        val body = OpenLyricsHttp.get(url.toString()) ?: return null
        val response = runCatching { OpenLyricsHttp.json.decodeFromString<Response>(body) }.getOrNull() ?: return null
        return timedLinesToTtml(parse(response))?.takeIf { it.isUsableLyrics() }
    }

    private fun parse(response: Response): List<TimedLine> {
        val agents = response.metadata?.agents.orEmpty()
        return response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            val singer = singerOf(line.element)
            // Apple's own voice ids (v1, v2, v1000) pass straight through; anything else is looked
            // up by alias. The lyric view lays v1 left, v2 right and groups in the middle.
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

    private fun singerOf(element: JsonElement?): String? =
        (element as? JsonObject)?.get("singer")?.let { it as? JsonPrimitive }?.contentOrNull

    private fun JsonElement?.saysOpposite(): Boolean {
        val array = runCatching { this?.jsonArray }.getOrNull() ?: return false
        return array.any { entry ->
            val tag = runCatching { entry.jsonPrimitive.contentOrNull }.getOrNull()
            tag == "opposite" || tag == "right"
        }
    }

    /**
     * Glues syllables back into words. The API's own spacing is the word boundary: it sends "e"
     * then "nough ", and the trailing space is the only thing saying those are one word.
     */
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
    private data class Response(
        val type: String? = null,
        val lyrics: List<Line>? = null,
        val metadata: Metadata? = null,
    )

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
        /** Raw: an object with a `singer` on the current API, an array of tags on the older one. */
        val element: JsonElement? = null,
    )

    @Serializable
    private data class Syllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
    )
}

// ============================================================================================

/**
 * Word-timed TTML from binimum's open lyrics index — the Apple catalogue, and the only source here
 * that answers to a recording rather than a name. Its search reports the ISRC of what it matched,
 * which is how every other source gets to ask for the same recording (see [LyricsHelper]).
 */
object BinimumLyricsProvider : LyricsProvider {
    override val name = "Binimum"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBinimumLyricsKey] ?: true

    private const val BASE = "https://lyrics-api.binimum.org/"

    /**
     * Which recording this is, without fetching its words: a couple of hundred bytes. By ISRC when
     * one is known — nothing else is worth sending then — and by name, album and length otherwise.
     */
    internal suspend fun identify(
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        isrc: String? = null,
    ): Recording? {
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
        val body = OpenLyricsHttp.get(url.toString()) ?: return null
        val hit = runCatching { OpenLyricsHttp.json.decodeFromString<Response>(body) }.getOrNull()
            ?.results?.firstOrNull { !it.lyricsUrl.isNullOrBlank() }
            ?: return null
        // A result whose length is far from the song's is another recording of it.
        if (isrc.isNullOrBlank() && duration > 0 && hit.duration != null && hit.duration > 0 &&
            kotlin.math.abs(hit.duration - duration) > 12
        ) {
            return null
        }
        return Recording(hit.isrc?.takeIf { it.isNotBlank() }, hit.lyricsUrl, hit.duration)
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = getLyrics(id, title, artist, album, duration, recording = null)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        recording: Recording?,
    ): Result<String> {
        // What the identifying search already found saves a second search.
        val found = recording?.takeIf { !it.lyricsUrl.isNullOrBlank() }
            ?: identify(title, artist, album, duration, recording?.isrc)
            ?: return notFound(name)
        val ttml = OpenLyricsHttp.get(found.lyricsUrl!!, accept = "application/ttml+xml, application/xml, text/xml, */*")
            ?.takeIf { LyricsUtils.isTtml(it) && it.isUsableLyrics() }
            ?: return notFound(name)
        return Result.success(ttml)
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

/** Community-voted lyrics from Unison: TTML, word-stamped LRC, plain LRC or plain text. */
object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonLyricsKey] ?: true

    private const val BASE = "https://unison.boidu.dev/lyrics"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("song", title)
            .addQueryParameter("artist", artist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
                if (duration > 0) addQueryParameter("duration", duration.toString())
            }
            .build()
        val body = OpenLyricsHttp.get(url.toString()) ?: return notFound(name)
        val response = runCatching { OpenLyricsHttp.json.decodeFromString<Response>(body) }.getOrNull()
            ?.takeIf { it.success == true }
            ?: return notFound(name)
        val data = response.data ?: return notFound(name)
        val text = data.lyrics?.trim()?.takeIf { it.isNotBlank() } ?: return notFound(name)
        val lyrics = when {
            data.format.equals("ttml", ignoreCase = true) || LyricsUtils.isTtml(text) -> text
            data.syncType.equals("plain", ignoreCase = true) -> text
            else -> enhancedLrcToTtml(text) ?: text
        }
        return if (lyrics.isUsableLyrics()) Result.success(lyrics) else notFound(name)
    }

    @Serializable
    private data class Response(val success: Boolean? = null, val data: Entry? = null)

    @Serializable
    private data class Entry(
        val lyrics: String? = null,
        val format: String? = null,
        val syncType: String? = null,
    )
}

// ============================================================================================

/** Line-timed LRC files from Megalobiz's library. */
object MegalobizLyricsProvider : LyricsProvider {
    override val name = "Megalobiz"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMegalobizLyricsKey] ?: true

    private const val BASE = "https://www.megalobiz.com"
    private val lrcLink = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val lrcBody = Regex(
        """id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val search = "$BASE/searchall".toHttpUrl().newBuilder()
            .addQueryParameter("qry", "$artist $title".trim())
            .build()
        val results = OpenLyricsHttp.get(search.toString(), accept = "text/html") ?: return notFound(name)
        // The first hit has to be about this song: its link carries the title as a slug.
        val slug = title.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        val path = lrcLink.findAll(results).map { it.groupValues[1] }
            .firstOrNull { slug.isEmpty() || it.lowercase(Locale.ROOT).contains(slug.take(24)) }
            ?: return notFound(name)
        val page = OpenLyricsHttp.get(BASE + path.replace("&amp;", "&"), accept = "text/html") ?: return notFound(name)
        val raw = lrcBody.find(page)?.groupValues?.get(1) ?: return notFound(name)
        val lrc = Parser.unescapeEntities(
            raw.replace(Regex("""(?i)<br\s*/?>"""), "\n").replace(Regex("""<[^>]+>"""), ""),
            false,
        ).lines().map { it.trim() }.filter { LRC_LINE.matches(it) }.joinToString("\n")
        return if (lrc.isUsableLyrics()) Result.success(lrc) else notFound(name)
    }
}

// ============================================================================================

/** Plain lyrics from Genius, for the songs no timed source has. */
object GeniusLyricsProvider : LyricsProvider {
    override val name = "Genius"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableGeniusLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val query = "$artist $title".trim()
        val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val body = OpenLyricsHttp.get(url.toString()) ?: return notFound(name)
        val songUrl = runCatching { bestMatch(body, title, artist) }.getOrNull() ?: return notFound(name)
        val html = OpenLyricsHttp.get(songUrl, accept = "text/html") ?: return notFound(name)
        val text = withContext(Dispatchers.Default) { runCatching { parseHtml(html) }.getOrNull() }
            ?: return notFound(name)
        return Result.success(text)
    }

    private fun bestMatch(body: String, targetTitle: String, targetArtist: String): String? {
        val root = OpenLyricsHttp.json.parseToJsonElement(body).jsonObject
        val sections = root["response"]?.jsonObject?.get("sections")?.jsonArray ?: return null
        val songs = sections.firstOrNull {
            (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "song"
        }?.jsonObject?.get("hits")?.jsonArray ?: return null
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
            .maxByOrNull { it.second }
            ?.first
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
        val lines = text
            .replace(' ', ' ')
            .replace("​", "")
            .replace(Regex("""\d*You might also like""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d*Embed\s*$""", RegexOption.IGNORE_CASE), "")
            .lines()
            .map { it.trim() }
            // Section labels ("[Chorus]") are Genius's notes, not lyrics — and a text that opened on
            // one would be taken for LRC and shown as nothing.
            .filterNot { it.startsWith("[") && it.endsWith("]") }
        val collapsed = mutableListOf<String>()
        lines.forEach { line -> if (line.isNotEmpty() || collapsed.lastOrNull()?.isNotEmpty() == true) collapsed += line }
        return collapsed.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }
}
