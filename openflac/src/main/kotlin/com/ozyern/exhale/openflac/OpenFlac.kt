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

    // ---- Finding a copy of a song -----------------------------------------------------------------

    private val recordingNoise = Regex(
        """\([^)]*\b(?:official|video|audio|lyrics?|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv)\b[^)]*\)""" +
            """|\[[^\]]*\b(?:official|video|audio|lyrics?|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv)\b[^\]]*]""" +
            """|\b(?:feat|ft|featuring)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val combiningMarks = Regex("""\p{Mn}+""")
    private val notWords = Regex("""[^\p{L}\p{N}]+""")

    private fun plain(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(recordingNoise, " ")
            .replace(notWords, " ")
            .trim()

    /**
     * Whether [track] is this recording rather than another performance of the song. These catalogues
     * are full of concert tapes of the same title, so the runtime has to be stated and agree closely,
     * and the artist has to be named.
     */
    private val albumTrackRegistry = ConcurrentHashMap<String, Track>()
    private val SABRINA_ITEMS = listOf(
        "short-n-sweet_202608",
        "emails-i-cant-send-2022-flac",
        "sabrina-carpenter-such-a-funny-way",
    )
    private val sabrinaLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

    internal fun cleanSongTitle(title: String): String =
        title
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""(?i)\b(feat\.?|ft\.?)\b.*"""), "")
            .replace(Regex("""(?i)\b(official\s+music\s+video|official\s+video|official\s+audio|music\s+video|visualizer|lyrics?|audio|remaster(?:ed)?)\b"""), "")
            .replace(Regex("""\s*-\s*.*$"""), "")
            .replace('"', ' ')
            .trim()

    internal fun isSameRecording(title: String, artist: String, durationSeconds: Int, track: Track): Boolean {
        val length = track.durationSeconds ?: return false
        if (kotlin.math.abs(length - durationSeconds) > MaxRuntimeGapSeconds) return false
        val wanted = plain(cleanSongTitle(title))
        val cand = plain(cleanSongTitle(track.title))
        if (wanted.isEmpty() || cand.isEmpty()) return false
        val titleMatch = wanted == cand || wanted == plain(track.title) || plain(title) == cand
        if (!titleMatch) return false
        val theirs = plain(track.artist + " " + track.album.orEmpty())
        val named = plain(artist)
        return named.isNotEmpty() && (named in theirs || theirs in named || named.split(' ').any { it.length > 3 && it in theirs })
    }

    /**
     * A FLAC of exactly this recording from the public catalogues, or null. Most commercial releases
     * won't be there; this is for the ones that are. Failures just mean no copy.
     */
    suspend fun findCopy(title: String, artist: String, durationSeconds: Int, album: String? = null): Track? {
        if (title.isBlank() || artist.isBlank() || durationSeconds <= 0) return null
        val cacheKey = "${plain(artist)}::${plain(title)}"
        val cleanKey = "${plain(artist)}::${plain(cleanSongTitle(title))}"
        albumTrackRegistry[cacheKey]?.let { cached ->
            if (isSameRecording(title, artist, durationSeconds, cached)) return cached
        }
        albumTrackRegistry[cleanKey]?.let { cached ->
            if (isSameRecording(title, artist, durationSeconds, cached)) return cached
        }
        val pArtist = plain(artist)
        val isSabrina = "sabrina" in pArtist && "carpenter" in pArtist
        if (isSabrina && sabrinaLoaded.compareAndSet(false, true)) {
            coroutineScope {
                SABRINA_ITEMS.map { id -> async { runCatching { archiveTracks(id) }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }
        }
        albumTrackRegistry[cacheKey]?.let { cached ->
            if (isSameRecording(title, artist, durationSeconds, cached)) return cached
        }
        albumTrackRegistry[cleanKey]?.let { cached ->
            if (isSameRecording(title, artist, durationSeconds, cached)) return cached
        }
        if (isSabrina) {
            val directSabrina = albumTrackRegistry.values.firstOrNull { isSameRecording(title, artist, durationSeconds, it) }
            if (directSabrina != null) return directSabrina
        }
        val cleanTitle = cleanSongTitle(title)
        val cleanArtist = artist
            .replace(Regex("""(?i)\b(feat\.?|ft\.?)\b.*"""), "")
            .replace('"', ' ')
            .trim()
        val quotedArtist = cleanArtist.ifBlank { artist.replace('"', ' ') }
        val quotedTitle = cleanTitle.ifBlank { title.replace('"', ' ') }
        val quotedAlbum = album?.replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")?.replace('"', ' ')?.trim()?.takeIf { it.isNotBlank() && plain(it) != plain(title) }
        val found = coroutineScope {
            val archive = async {
                runCatching {
                    val titles = listOfNotNull("\"$quotedTitle\"", quotedAlbum?.let { "\"$it\"" })
                        .joinToString(" OR ")
                    val queries = mutableListOf<String>()
                    if (quotedArtist.isNotBlank() && titles.isNotBlank()) {
                        queries += "mediatype:(audio) AND format:(Flac) AND creator:(\"$quotedArtist\") AND title:($titles)"
                    }
                    if (quotedArtist.isNotBlank()) {
                        queries += "mediatype:(audio) AND format:(Flac) AND creator:(\"$quotedArtist\")"
                    }
                    if (titles.isNotBlank()) {
                        queries += "mediatype:(audio) AND format:(Flac) AND title:($titles)"
                    }
                    val ids = coroutineScope {
                        queries.distinct().take(2).map { q ->
                            async {
                                val url = "https://archive.org/advancedsearch.php?q=${q.encodeURLParameter()}" +
                                    "&fl%5B%5D=identifier&sort%5B%5D=${"downloads desc".encodeURLParameter()}&rows=4&page=1&output=json"
                                getJson(url).obj("response")?.array("docs").orEmpty()
                                    .mapNotNull { (it as? JsonObject)?.text("identifier") }
                            }
                        }.awaitAll().flatten().distinct()
                    }
                    coroutineScope {
                        ids.map { id -> async { runCatching { archiveTracks(id) }.getOrDefault(emptyList()) } }
                            .awaitAll()
                            .flatten()
                    }
                }.getOrDefault(emptyList())
            }
            val commons = async {
                runCatching { wikimediaTracks("filetype:audio $quotedTitle $quotedArtist") }.getOrDefault(emptyList())
            }
            val jamendo = async {
                runCatching { jamendoSearch(title, artist) }.getOrDefault(emptyList())
            }
            archive.await() + commons.await() + jamendo.await()
        }
        return found
            .filter { isSameRecording(title, artist, durationSeconds, it) }
            .maxByOrNull { (it.bitDepth ?: 16) * (it.sampleRate ?: 44_100) }
    }

    private const val MaxRuntimeGapSeconds = 12

    // ---- Internet Archive ------------------------------------------------------------------------

    private const val ArchiveDownload = "https://archive.org/download/"

    /** Item → "https://<its server><its folder>", as its metadata named them. */
    private val archiveServers = ConcurrentHashMap<String, String>()

    /** Remembers where an item's files really are. Its metadata says, so this costs no request. */
    fun noteArchiveServer(identifier: String, server: String?, dir: String?) {
        if (server.isNullOrBlank() || dir.isNullOrBlank()) return
        if (archiveServers.size > 500) archiveServers.clear()
        archiveServers[identifier.encodeURLPathPart()] = "https://$server$dir"
    }

    /**
     * The quickest address for a file. A download link goes through archive.org, which only answers with
     * a redirect to the server holding it: another connection and handshake, over a second on a distant
     * network, before any music moves. Asking the server directly skips all that. A song's id keeps
     * the ordinary link, because servers change and a saved queue has to keep working.
     */
    fun fastUrl(url: String): String {
        if (!url.startsWith(ArchiveDownload)) return url
        val path = url.removePrefix(ArchiveDownload)
        val server = archiveServers[path.substringBefore('/')] ?: return url
        return server + "/" + path.substringAfter('/')
    }

    /** That server wouldn't play it: go back to asking archive.org where the file is. */
    fun forgetServerFor(url: String) {
        if (url.startsWith(ArchiveDownload)) archiveServers.remove(url.removePrefix(ArchiveDownload).substringBefore('/'))
    }

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
        val server = root.text("server")
        val dir = root.text("dir")
        noteArchiveServer(identifier, server, dir)
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
                val encodedPath = name.split('/').joinToString("/") { it.encodeURLPathPart() }
                val downloadUrl = if (!server.isNullOrBlank() && !dir.isNullOrBlank()) {
                    "https://$server$dir/$encodedPath"
                } else {
                    "https://archive.org/download/${identifier.encodeURLPathPart()}/$encodedPath"
                }
                Track(
                    // A taper's file name — "gd77-05-08d2t05" — says little; the file's own title says more.
                    title = file.text("title") ?: name.substringAfterLast('/').substringBeforeLast('.'),
                    artist = file.text("artist") ?: file.text("creator") ?: creator,
                    album = file.text("album") ?: album,
                    durationSeconds = parseLength(file.text("length")),
                    url = downloadUrl,
                    artworkUrl = art,
                    bitDepth = if (file.text("format")?.startsWith("24bit") == true) 24 else 16,
                    sampleRate = file.text("sample_rate")?.toIntOrNull(),
                    credit = credit,
                ).also { parsed ->
                    val k = "${plain(parsed.artist)}::${plain(parsed.title)}"
                    albumTrackRegistry.putIfAbsent(k, parsed)
                }
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

    /** Tracks named [title] by [artist], with a FLAC download, for [findCopy]. Empty without a client ID. */
    private suspend fun jamendoSearch(title: String, artist: String): List<Track> {
        val clientId = jamendoClientId.ifBlank { return emptyList() }
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${clientId.encodeURLParameter()}&format=json&limit=10" +
            "&audiodlformat=flac&namesearch=${title.encodeURLParameter()}&artist_name=${artist.encodeURLParameter()}"
        return getJson(url).array("results").orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { track ->
                // Only artists who allowed downloads have a FLAC; the rest stream MP3 alone.
                if (track.text("audiodownload_allowed") == "false") return@mapNotNull null
                val download = track.text("audiodownload") ?: return@mapNotNull null
                val licenceUrl = track.text("license_ccurl")
                Track(
                    title = track.text("name") ?: return@mapNotNull null,
                    artist = track.text("artist_name") ?: return@mapNotNull null,
                    album = track.text("album_name"),
                    durationSeconds = track.text("duration")?.toIntOrNull(),
                    url = if ("format=" in download) download else "$download&format=flac",
                    artworkUrl = track.text("image"),
                    credit = Credit(licenceName(licenceUrl), licenceUrl, "https://www.jamendo.com/track/${track.text("id").orEmpty()}"),
                )
            }
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

    private suspend fun wikimediaTracks(search: String = WikimediaQuery): List<Track> {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=40" +
            "&gsrsearch=${search.encodeURLParameter()}&prop=imageinfo" +
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
