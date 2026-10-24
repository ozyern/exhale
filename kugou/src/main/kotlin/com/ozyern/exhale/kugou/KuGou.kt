/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.kugou

import com.ozyern.exhale.kugou.models.DownloadLyricsResponse
import com.ozyern.exhale.kugou.models.Keyword
import com.ozyern.exhale.kugou.models.SearchLyricsResponse
import com.ozyern.exhale.kugou.models.SearchSongResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.lang.Integer.min
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

@OptIn(ExperimentalSerializationApi::class)
private val client = HttpClient {
    expectSuccess = true

    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        json(json)
        json(json, ContentType.Text.Html)
        json(json, ContentType.Text.Plain)
    }

    install(ContentEncoding) {
        gzip()
        deflate()
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 15000
    }
}

private const val PAGE_SIZE = 8
private const val HEAD_CUT_LIMIT = 30

/**
 * KuGou Lyrics Library
 */
@OptIn(ExperimentalEncodingApi::class)
object KuGou {
    var useTraditionalChinese: Boolean = false

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> =
        runCatching {
            val keyword = generateKeyword(title, artist)
            getLyricsCandidate(keyword, duration)?.let { candidate ->
                Base64.Default.decode(
                    downloadLyrics(candidate.id, candidate.accesskey).content
                ).decodeToString().normalize()
            } ?: throw IllegalStateException("No lyrics candidate")
        }

    suspend fun getAllPossibleLyricsOptions(
        title: String, artist: String, duration: Int, callback: (String) -> Unit
    ) {
        val keyword = generateKeyword(title, artist)
        searchSongs(keyword).data.info.forEach {
            if ((duration == -1 || abs(it.duration - duration) <= DURATION_TOLERANCE) && it.isSongOf(keyword)) {
                searchLyricsByHash(it.hash).candidates.firstOrNull()?.let { candidate ->
                    Base64.Default.decode(
                        downloadLyrics(candidate.id, candidate.accesskey).content
                    ).decodeToString().normalize().let(callback)
                }
            }
        }
        searchLyricsByKeyword(keyword, duration).candidates.filter { it.isSongOf(keyword) }.forEach { candidate ->
            Base64.Default.decode(
                downloadLyrics(candidate.id, candidate.accesskey).content
            ).decodeToString().normalize().let(callback)
        }
    }

    suspend fun getLyricsCandidate(
        keyword: Keyword, duration: Int
    ): SearchLyricsResponse.Candidate? {
        // A hit has to be this title by this artist, not just a song of about this length: matched on
        // length alone, a song KuGou doesn't have came back with whichever namesake (or stranger)
        // happened to run as long, and those words were shown as this song's.
        searchSongs(keyword).data.info.forEach { song ->
            if ((duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE) && song.isSongOf(keyword)) {
                val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
                if (candidate != null) return candidate
            }
        }
        return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull { it.isSongOf(keyword) }
    }

    private fun SearchSongResponse.Data.Info.isSongOf(keyword: Keyword): Boolean {
        // "Artist - Title" is how KuGou names a file when the separate fields are missing.
        val fromFile = filename?.split(" - ", limit = 2)?.takeIf { it.size == 2 }
        return sameSong(keyword, songname ?: fromFile?.get(1), singername ?: fromFile?.get(0))
    }

    private fun SearchLyricsResponse.Candidate.isSongOf(keyword: Keyword): Boolean =
        sameSong(keyword, song, singer)

    private fun simplified(text: String): String =
        normalizeTitle(text).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val artistSplit = Regex("[、,&/;]|\\s+(?:feat\\.?|ft\\.?|x|and)\\s+", RegexOption.IGNORE_CASE)

    /** The same title, and at least one artist in common when KuGou names one. */
    private fun sameSong(keyword: Keyword, title: String?, artist: String?): Boolean {
        val wanted = simplified(keyword.title)
        val got = simplified(title ?: return false)
        if (wanted.isEmpty() || got.isEmpty()) return false
        val titleMatches = wanted == got ||
            (minOf(wanted.length, got.length) >= 4 && (wanted.startsWith(got) || got.startsWith(wanted)))
        if (!titleMatches) return false
        if (artist.isNullOrBlank() || keyword.artist.isBlank()) return true
        val wantedArtists = keyword.artist.split(artistSplit).map(::simplified).filter { it.isNotEmpty() }
        val gotArtists = artist.split(artistSplit).map(::simplified).filter { it.isNotEmpty() }
        return wantedArtists.any { a -> gotArtists.any { b -> a == b || a.contains(b) || b.contains(a) } }
    }

    suspend fun searchSongs(keyword: Keyword) =
        client.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", PAGE_SIZE)
            parameter("showtype", 0)
            url.encodedParameters.append(
                "keyword",
                "${keyword.title} - ${keyword.artist}".encodeURLParameter(spaceToPlus = false)
            )
        }.body<SearchSongResponse>()

    private suspend fun searchLyricsByKeyword(keyword: Keyword, duration: Int) =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter(
                "duration", duration.takeIf { it != -1 }?.times(1000)
            ) // if duration == -1, we don't care duration
            url.encodedParameters.append(
                "keyword",
                "${keyword.title} - ${keyword.artist}".encodeURLParameter(spaceToPlus = false)
            )
        }.body<SearchLyricsResponse>()

    private suspend fun searchLyricsByHash(hash: String) =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("hash", hash)
        }.body<SearchLyricsResponse>()

    private suspend fun downloadLyrics(id: Long, accessKey: String) =
        client.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accessKey)
        }.body<DownloadLyricsResponse>()

    private fun normalizeTitle(title: String) =
        title.replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "").replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "").replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "").replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) =
        artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")

    fun generateKeyword(title: String, artist: String) =
        Keyword(normalizeTitle(title), normalizeArtist(artist))

    private fun String.normalize(): String =
        replace("&apos;", "'").lines().filter { line -> line.matches(ACCEPTED_REGEX) }
            .let { lines ->
                // Remove useless information such as singer, writer, composer, guitar, etc.
                var headCutLine = 0
                for (i in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[i].matches(BANNED_REGEX)) {
                        headCutLine = i + 1
                        break
                    }
                }
                val filteredLines = lines.drop(headCutLine)

                var tailCutLine = 0
                for (i in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) {
                        tailCutLine = i + 1
                        break
                    }
                }
                val finalLines = filteredLines.dropLast(tailCutLine)

                return@let finalLines.joinToString("\n")
            }

    @Suppress("RegExpRedundantEscape")
    private val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
    private val BANNED_REGEX = ".+].+[:：].+".toRegex()

    private const val DURATION_TOLERANCE = 8
}
