/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.openflac

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Music that is free to play in FLAC: concert tapes shared with the band's blessing, netlabel
 * releases, 78s transferred by archivists, Creative Commons albums and public-domain recordings.
 *
 * The listener only ever sees "FLAC" — where a release comes from is kept to its credit line, which
 * is there because the licences ask for it. Every address here is a public catalogue; nothing needs
 * an account except Jamendo, whose client ID the build supplies ([jamendoClientId]) and which is left
 * out when there is none.
 */
object OpenFlac {
    /** Set once at start-up from the build's secrets. Blank leaves the Creative Commons shelf out. */
    @Volatile var jamendoClientId: String = ""

    data class Shelf(
        val title: String,
        val releases: List<Release>,
    )

    data class Release(
        /** "ia:<identifier>", "jm:<album id>" or "wm:<composer>" — what [tracks] takes. */
        val key: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val year: String? = null,
    )

    data class Track(
        val title: String,
        val artist: String,
        val album: String?,
        val durationSeconds: Int?,
        /** The FLAC file itself, ready to stream. */
        val url: String,
        val artworkUrl: String?,
        val bitDepth: Int? = null,
        val sampleRate: Int? = null,
        val credit: Credit,
    )

    /** What the licence asks to be shown: the licence's name and where the recording lives. */
    data class Credit(
        val licence: String?,
        val licenceUrl: String?,
        val pageUrl: String,
    )

    private const val UserAgent = "Exhale (+https://github.com/ozyern/exhale)"
    private const val ShelfSize = 20

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            defaultRequest { header("User-Agent", UserAgent) }
            expectSuccess = true
        }
    }

    /** A release's tracks, kept for the session: opening the same album twice asks once. */
    private val trackCache = ConcurrentHashMap<String, List<Track>>()

    @Volatile private var shelfCache: Pair<Long, List<Shelf>>? = null
    private const val ShelfTtlMs = 30 * 60_000L

    /** The FLAC page's shelves, each fetched on its own so one slow catalogue doesn't empty the page. */
    suspend fun shelves(): Result<List<Shelf>> = runCatching {
        shelfCache?.takeIf { System.currentTimeMillis() - it.first < ShelfTtlMs }?.let { return@runCatching it.second }
        val shelves = coroutineScope {
            listOf(
                async { archiveShelf("Live in Concert", "collection:(etree) AND format:(Flac)") },
                async { jamendoShelf() },
                async { archiveShelf("Independent Labels", "collection:(netlabels) AND format:(Flac)") },
                async { archiveShelf("Vintage 78s", "collection:(georgeblood) AND format:(Flac OR \"24bit Flac\")") },
                async { wikimediaShelf() },
            ).awaitAll()
        }.filterNotNull().filter { it.releases.isNotEmpty() }
        check(shelves.isNotEmpty()) { "No FLAC shelves answered" }
        shelfCache = System.currentTimeMillis() to shelves
        shelves
    }

    /** Every playable FLAC in [key]'s release, in running order. */
    suspend fun tracks(key: String): Result<List<Track>> = runCatching {
        trackCache[key]?.let { return@runCatching it }
        val tracks = when {
            key.startsWith("ia:") -> archiveTracks(key.removePrefix("ia:"))
            key.startsWith("jm:") -> jamendoTracks(key.removePrefix("jm:"))
            key.startsWith("wm:") -> wikimediaAll().filter { it.artist == key.removePrefix("wm:") }
            else -> error("Unknown release $key")
        }
        if (trackCache.size > 200) trackCache.clear()
        trackCache[key] = tracks
        tracks
    }

    // ---- Internet Archive ------------------------------------------------------------------------

    private suspend fun archiveShelf(title: String, query: String): Shelf? = runCatching {
        val url = "https://archive.org/advancedsearch.php?q=${query.encodeURLParameter()}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=date" +
            "&sort%5B%5D=${"downloads desc".encodeURLParameter()}&rows=$ShelfSize&page=1&output=json"
        val docs = getJson(url).obj("response")?.array("docs").orEmpty()
        Shelf(
            title = title,
            releases = docs.mapNotNull { element ->
                val doc = element as? JsonObject ?: return@mapNotNull null
                val id = doc.text("identifier") ?: return@mapNotNull null
                Release(
                    key = "ia:$id",
                    title = doc.text("title") ?: id,
                    artist = doc.text("creator") ?: "Various Artists",
                    artworkUrl = "https://archive.org/services/img/${id.encodeURLPathPart()}",
                    year = doc.text("date")?.take(4),
                )
            },
        )
    }.getOrNull()

    private suspend fun archiveTracks(identifier: String): List<Track> {
        val root = getJson("https://archive.org/metadata/${identifier.encodeURLPathPart()}")
        val metadata = root.obj("metadata")
        val creator = metadata?.text("creator") ?: "Various Artists"
        val album = metadata?.text("title")
        val licenceUrl = metadata?.text("licenseurl")
        val art = "https://archive.org/services/img/${identifier.encodeURLPathPart()}"
        val credit = Credit(licenceName(licenceUrl), licenceUrl, "https://archive.org/details/$identifier")
        return root.array("files").orEmpty()
            .mapNotNull { it as? JsonObject }
            // By name, not by format: "Flac FingerPrint" files (.ffp) say Flac too.
            .filter { file -> file.text("name")?.endsWith(".flac", ignoreCase = true) == true }
            .sortedWith(compareBy({ it.text("track")?.substringBefore('/')?.trim()?.toIntOrNull() ?: Int.MAX_VALUE }, { it.text("name") }))
            .mapNotNull { file ->
                val name = file.text("name") ?: return@mapNotNull null
                Track(
                    // A taper's file name — "gd77-05-08d2t05" — says little; the file's own title says more.
                    title = file.text("title") ?: name.substringAfterLast('/').substringBeforeLast('.'),
                    artist = file.text("artist") ?: file.text("creator") ?: creator,
                    album = file.text("album") ?: album,
                    durationSeconds = parseLength(file.text("length")),
                    url = "https://archive.org/download/${identifier.encodeURLPathPart()}/" +
                        name.split('/').joinToString("/") { it.encodeURLPathPart() },
                    artworkUrl = art,
                    bitDepth = if (file.text("format")?.startsWith("24bit") == true) 24 else 16,
                    sampleRate = file.text("sample_rate")?.toIntOrNull(),
                    credit = credit,
                )
            }
    }

    // ---- Jamendo --------------------------------------------------------------------------------

    private suspend fun jamendoShelf(): Shelf? {
        val clientId = jamendoClientId.ifBlank { return null }
        return runCatching {
            val url = "https://api.jamendo.com/v3.0/albums/?client_id=${clientId.encodeURLParameter()}&format=json" +
                "&limit=$ShelfSize&order=popularity_week&imagesize=600"
            Shelf(
                title = "Creative Commons",
                releases = getJson(url).array("results").orEmpty().mapNotNull { element ->
                    val album = element as? JsonObject ?: return@mapNotNull null
                    Release(
                        key = "jm:${album.text("id") ?: return@mapNotNull null}",
                        title = album.text("name") ?: return@mapNotNull null,
                        artist = album.text("artist_name") ?: "Various Artists",
                        artworkUrl = album.text("image"),
                        year = album.text("releasedate")?.take(4),
                    )
                },
            )
        }.getOrNull()
    }

    private suspend fun jamendoTracks(albumId: String): List<Track> {
        val clientId = jamendoClientId.ifBlank { error("No Jamendo client ID") }
        val url = "https://api.jamendo.com/v3.0/albums/tracks/?client_id=${clientId.encodeURLParameter()}&format=json" +
            "&id=${albumId.encodeURLParameter()}&audiodlformat=flac&imagesize=600"
        val album = getJson(url).array("results")?.firstOrNull() as? JsonObject ?: return emptyList()
        val art = album.text("image")
        return album.array("tracks").orEmpty()
            .mapNotNull { it as? JsonObject }
            .sortedBy { it.text("position")?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { track ->
                // Only artists who allowed downloads have a FLAC; the rest stream MP3 alone.
                if (track.text("audiodownload_allowed") == "false") return@mapNotNull null
                val download = track.text("audiodownload") ?: return@mapNotNull null
                val licenceUrl = track.text("license_ccurl")
                Track(
                    title = track.text("name") ?: return@mapNotNull null,
                    artist = album.text("artist_name") ?: "Various Artists",
                    album = album.text("name"),
                    durationSeconds = track.text("duration")?.toIntOrNull(),
                    url = if ("format=" in download) download else "$download&format=flac",
                    artworkUrl = art,
                    credit = Credit(licenceName(licenceUrl), licenceUrl, "https://www.jamendo.com/album/$albumId"),
                )
            }
    }

    // ---- Wikimedia Commons ----------------------------------------------------------------------

    /** Musopen's recordings: public-domain performances, uploaded with tidy "Composer - Work" names. */
    private const val WikimediaQuery = "filetype:audio flac musopen"
    private val PerformerNote = Regex("""\s*\(Musopen[^)]*\)""", RegexOption.IGNORE_CASE)

    /** Commons has no albums, so its recordings are gathered by composer, one release each. */
    private suspend fun wikimediaShelf(): Shelf? = runCatching {
        Shelf(
            title = "Classical Recordings",
            releases = wikimediaAll().groupBy { it.artist }.entries
                .sortedByDescending { it.value.size }
                .map { (composer, _) -> Release(key = "wm:$composer", title = composer, artist = "Classical", artworkUrl = null) },
        )
    }.getOrNull()

    @Volatile private var wikimediaCache: List<Track>? = null

    private suspend fun wikimediaAll(): List<Track> =
        wikimediaCache ?: wikimediaTracks().also { wikimediaCache = it }

    private suspend fun wikimediaTracks(): List<Track> {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=40" +
            "&gsrsearch=${WikimediaQuery.encodeURLParameter()}&prop=imageinfo" +
            "&iiprop=${"url|mime|metadata|extmetadata".encodeURLParameter()}&format=json"
        val pages = getJson(url).obj("query")?.obj("pages") ?: return emptyList()
        return pages.values.mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            val info = page.array("imageinfo")?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            val fileUrl = info.text("url")?.substringBefore('?') ?: return@mapNotNull null
            if (!fileUrl.endsWith(".flac", ignoreCase = true)) return@mapNotNull null
            val meta = info.obj("extmetadata")
            // The file's own tags, read by getID3: a list of {name, value} pairs, the stream's nested.
            val metadata = info.array("metadata") ?: JsonArray(emptyList())
            val audio = metadata.nested("audio")
            fun ext(name: String) = meta?.obj(name)?.text("value")?.let(::stripHtml)?.takeIf { it.isNotBlank() }
            // "Beethoven - Egmont Overture, Op. 84 (Musopen Symphony)": the composer, then the work.
            val name = page.text("title").orEmpty().removePrefix("File:").substringBeforeLast('.').replace('_', ' ').replace(PerformerNote, "")
            val composer = name.substringBefore(" - ", "").trim().takeIf { it.isNotEmpty() }
            Track(
                title = if (composer != null) name.substringAfter(" - ").trim() else name,
                artist = composer ?: ext("Artist")?.take(80) ?: "Unknown artist",
                album = null,
                durationSeconds = (metadata.field("playtime_seconds") ?: metadata.field("length"))?.toDoubleOrNull()?.toInt(),
                url = fileUrl,
                artworkUrl = null,
                bitDepth = audio.field("bits_per_sample")?.toIntOrNull(),
                sampleRate = audio.field("sample_rate")?.toIntOrNull(),
                credit = Credit(
                    licence = ext("LicenseShortName"),
                    licenceUrl = ext("LicenseUrl"),
                    pageUrl = info.text("descriptionurl") ?: fileUrl,
                ),
            )
        }.filter { (it.durationSeconds ?: 0) >= 30 }.sortedWith(compareBy({ it.artist }, { it.title }))
    }

    // ---- Shared ---------------------------------------------------------------------------------

    private suspend fun getJson(url: String): JsonObject =
        json.parseToJsonElement(client.get(url).bodyAsText()).jsonObject

    /** "https://creativecommons.org/licenses/by-nc-sa/3.0/" → "CC BY-NC-SA 3.0". */
    internal fun licenceName(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if ("publicdomain" in url) return "Public Domain"
        val match = Regex("""licenses/([a-z-]+)/([\d.]+)""").find(url) ?: return null
        return "CC ${match.groupValues[1].uppercase()} ${match.groupValues[2]}"
    }

    /** Lengths come as "274.45" or as "4:34". */
    internal fun parseLength(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toDoubleOrNull()?.let { return it.toInt().takeIf { seconds -> seconds > 0 } }
        val parts = raw.trim().split(':').map { it.trim().toDoubleOrNull() ?: return null }
        return parts.fold(0.0) { total, part -> total * 60 + part }.toInt().takeIf { it > 0 }
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), " ").replace(Regex("&[a-z]+;"), " ").replace(Regex("\\s+"), " ").trim()

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

    /** MediaWiki's metadata comes as a list of {name, value} pairs rather than an object. */
    private fun JsonArray.entry(name: String): JsonObject? =
        mapNotNull { it as? JsonObject }.firstOrNull { it.text("name").equals(name, ignoreCase = true) }

    private fun JsonArray.field(name: String): String? = entry(name)?.text("value")

    private fun JsonArray.nested(name: String): JsonArray = entry(name)?.get("value") as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

    /** A field as text, whether the catalogue sent a string, a number or a list of names. */
    private fun JsonObject.text(name: String): String? = when (val value: JsonElement? = this[name]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.contentOrNull
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
}
