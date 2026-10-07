/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Follows BitChord's lrc.red client (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.constants.EnableLrcRedLyricsKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * lrc.red — Apple Music TTML, per-syllable, filed by ISRC.
 *
 * Every document lives at `https://lrc.red/s/{ISRC}.ttml`, so a song whose recording is already
 * known costs one request and can't come back as the wrong edit — the clean cut's words on the
 * explicit one, or the album version's timing on the single. That is the usual case: Binimum
 * answers out of this same catalogue and names the ISRC it matched (see [LyricsHelper]).
 *
 * Without one, lrc.red's own search is asked, and its hits are matched here rather than trusted in
 * order: it is a free-text search and happily ranks a remix or a live cut above the song. See [best].
 */
object LrcRedLyricsProvider : LyricsProvider {
    override val name = "lrc.red"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcRedLyricsKey] ?: true

    private const val BASE = "https://lrc.red/"
    private const val TTML = "application/ttml+xml, application/xml, text/xml, */*"

    /** YouTube reports whole seconds; a different recording is usually further off than this. */
    private const val DURATION_TOLERANCE_SECONDS = 3.0

    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")

    private fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC.matches(it) }?.let { "${BASE}s/$it.ttml" }

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
        val known = recording?.isrc?.let(::documentUrl)
        if (known != null) document(known)?.let { return Result.success(it) }
        // A recording lrc.red doesn't file under that code is often there under a sibling
        // release's, so a known ISRC that misses still gets a search.
        val hit = search(title, artist, duration) ?: return notFound(name)
        val found = hit.isrc?.let(::documentUrl)?.takeIf { it != known } ?: return notFound(name)
        return document(found)?.let { Result.success(it) } ?: notFound(name)
    }

    private suspend fun document(url: String): String? =
        OpenLyricsHttp.get(url, accept = TTML)?.takeIf { LyricsUtils.isTtml(it) && it.isUsableLyrics() }

    private suspend fun search(title: String, artist: String, duration: Int): Hit? {
        val url = "${BASE}search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist".trim())
            .build()
        val body = OpenLyricsHttp.get(url.toString()) ?: return null
        val hits = runCatching { OpenLyricsHttp.json.decodeFromString<Response>(body) }.getOrNull()?.hits
            ?: return null
        return best(hits, title, artist, duration)
    }

    /**
     * The hit that is this recording, or null if none is sure to be. A miss is recoverable — the
     * sources behind this one get their turn — and the wrong words in time with the right song is
     * not, so every test is a requirement rather than a score: the same title outside its brackets,
     * the same version words (live, remix…), a shared artist, and a length within
     * [DURATION_TOLERANCE_SECONDS]. Of those that pass, the closest in length wins.
     */
    private fun best(hits: List<Hit>, title: String, artist: String, duration: Int): Hit? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)

        fun distance(hit: Hit): Double =
            if (duration > 0 && hit.duration != null) abs(hit.duration - duration) else 0.0

        return hits
            .filter { hit ->
                val name = hit.title ?: return@filter false
                hit.isrc?.let(::documentUrl) != null &&
                    coreOf(name) == wantedTitle &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                    distance(hit) <= DURATION_TOLERANCE_SECONDS
            }
            .minByOrNull(::distance)
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
    private data class Hit(
        val isrc: String? = null,
        val title: String? = null,
        val artist: String? = null,
        /** Seconds, fractional. */
        val duration: Double? = null,
    )
}
