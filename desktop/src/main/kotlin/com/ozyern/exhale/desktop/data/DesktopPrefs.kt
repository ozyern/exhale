/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.Album
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YouTubeLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.Properties

data class PreferredArtist(val id: String, val name: String, val thumbnail: String?)

data class SavedQueue(val songs: List<SongItem>, val index: Int, val positionSeconds: Double)

/**
 * The desktop app's stored state, in `%APPDATA%\Exhale\settings.properties`: onboarding picks,
 * history, the session, and the raw values behind [AppSettings]. Small enough that every write
 * rewrites the file.
 */
object DesktopPrefs {
    private const val RECORD = ''
    private const val FIELD = ''
    private const val LIST = ''
    private const val PAIR = ''

    private val file: File = File(
        System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config"),
        "Exhale/settings.properties",
    )

    /** `%APPDATA%\Exhale` — caches and the sign-in browser live beside the settings. */
    val dataDir: File get() = file.parentFile

    private val props = Properties().apply {
        runCatching { if (file.exists()) file.inputStream().use(::load) }
    }

    internal fun raw(key: String): String? = props.getProperty(key)

    @Synchronized
    internal fun putAll(values: Map<String, String?>) {
        if (values.isEmpty()) return
        values.forEach { (key, value) -> if (value == null) props.remove(key) else props.setProperty(key, value) }
        save()
    }

    private fun put(key: String, value: String?) = putAll(mapOf(key to value))

    @Synchronized
    private fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.outputStream().use { props.store(it, "Exhale desktop") }
        }
    }

    var onboardingCompleted: Boolean
        get() = props.getProperty("onboarding_completed") == "true"
        set(value) = put("onboarding_completed", value.toString())

    /** In the order the user picked them; the first one decides the region. */
    var languages: List<String>
        get() = props.getProperty("languages").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = put("languages", value.joinToString(","))

    private val _artists = MutableStateFlow(readArtists())
    val artists: StateFlow<List<PreferredArtist>> = _artists.asStateFlow()

    fun setArtists(value: List<PreferredArtist>) {
        putRecords("artists", value.map { listOf(it.id, it.name, it.thumbnail.orEmpty()) })
        _artists.value = value
    }

    var visitorData: String?
        get() = props.getProperty("visitor_data")
        set(value) = put("visitor_data", value)

    /** The `hl|gl` the stored visitorData was minted under. */
    var visitorDataLocale: String?
        get() = props.getProperty("visitor_data_locale")
        set(value) = put("visitor_data_locale", value)

    /** The player's volume — kept out of [Settings] so dragging the slider doesn't restyle the app. */
    var volume: Int
        get() = props.getProperty("volume")?.toIntOrNull()?.coerceIn(0, 100) ?: 100
        set(value) = put("volume", value.toString())

    // History

    private val _history = MutableStateFlow(decodeSongs(records("history")))
    val history: StateFlow<List<SongItem>> = _history.asStateFlow()

    fun recordPlayed(song: SongItem) {
        if (AppSettings.current.pauseHistory) return
        // Enough to make a library of for listeners who never sign in.
        val updated = (listOf(song) + _history.value.filterNot { it.id == song.id }).take(300)
        putRecords("history", encodeSongs(updated))
        _history.value = updated
    }

    fun clearHistory() {
        put("history", null)
        _history.value = emptyList()
    }

    // Favourites — the Music app's Favorite Songs, and what Library's Songs, Albums, Artists and
    // Recently Added are made of when no account is signed in. Newest first.

    private val _favorites = MutableStateFlow(decodeSongs(records("favorites")))
    val favorites: StateFlow<List<SongItem>> = _favorites.asStateFlow()

    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    fun toggleFavorite(song: SongItem) {
        val updated = if (isFavorite(song.id)) _favorites.value.filterNot { it.id == song.id } else listOf(song) + _favorites.value
        putRecords("favorites", encodeSongs(updated))
        _favorites.value = updated
    }

    private val _searchHistory = MutableStateFlow(readSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun recordSearch(query: String) {
        if (AppSettings.current.pauseSearchHistory) return
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = (listOf(q) + _searchHistory.value.filterNot { it.equals(q, ignoreCase = true) }).take(20)
        put("search_history", updated.joinToString(RECORD.toString()))
        _searchHistory.value = updated
    }

    fun removeSearch(query: String) {
        val updated = _searchHistory.value - query
        put("search_history", updated.joinToString(RECORD.toString()).ifEmpty { null })
        _searchHistory.value = updated
    }

    fun clearSearchHistory() {
        put("search_history", null)
        _searchHistory.value = emptyList()
    }

    // Queue — so the app reopens where it was left.

    fun saveQueue(queue: List<SongItem>, index: Int, positionSeconds: Double) {
        if (queue.isEmpty() || index !in queue.indices) {
            clearSavedQueue()
            return
        }
        // Keep the window around the current song; a 500-song radio doesn't need to be on disk.
        val start = (index - 50).coerceAtLeast(0)
        val window = queue.subList(start, (start + 200).coerceAtMost(queue.size))
        putAll(
            mapOf(
                "queue" to encodeSongs(window).joinToString(RECORD.toString()) { it.joinToString(FIELD.toString()) },
                "queue_index" to (index - start).toString(),
                "queue_position" to positionSeconds.toString(),
            ),
        )
    }

    fun savedQueue(): SavedQueue? {
        val songs = decodeSongs(records("queue")).takeIf { it.isNotEmpty() } ?: return null
        val index = props.getProperty("queue_index")?.toIntOrNull()?.takeIf { it in songs.indices } ?: return null
        return SavedQueue(songs, index, props.getProperty("queue_position")?.toDoubleOrNull() ?: 0.0)
    }

    fun clearSavedQueue() = putAll(mapOf("queue" to null, "queue_index" to null, "queue_position" to null))

    // Account — the YouTube Music session captured by the sign-in window. See AccountSession.

    var cookie: String?
        get() = props.getProperty("yt_cookie")
        set(value) = put("yt_cookie", value)
    var dataSyncId: String?
        get() = props.getProperty("yt_data_sync_id")
        set(value) = put("yt_data_sync_id", value)
    var accountName: String?
        get() = props.getProperty("account_name")
        set(value) = put("account_name", value)
    var accountEmail: String?
        get() = props.getProperty("account_email")
        set(value) = put("account_email", value)
    var accountHandle: String?
        get() = props.getProperty("account_handle")
        set(value) = put("account_handle", value)
    var accountAvatar: String?
        get() = props.getProperty("account_avatar")
        set(value) = put("account_avatar", value)
    var useAccountForHome: Boolean
        get() = props.getProperty("use_account_for_home") != "false"
        set(value) = put("use_account_for_home", value.toString())

    // Shortcuts into AppSettings for code that only reads one value.

    val hideExplicit: Boolean get() = AppSettings.current.hideExplicit
    val hideVideos: Boolean get() = AppSettings.current.hideVideos
    fun isLyricsProviderEnabled(name: String): Boolean = name !in AppSettings.current.lyricsDisabled

    // Backup

    fun exportTo(target: File) {
        synchronized(this) { save() }
        file.copyTo(target, overwrite = true)
    }

    /** Replaces everything with the backup's contents. The account session comes with it. */
    fun importFrom(source: File) {
        val incoming = Properties().apply { source.inputStream().use(::load) }
        synchronized(this) {
            props.clear()
            props.putAll(incoming)
            save()
        }
        _artists.value = readArtists()
        _history.value = decodeSongs(records("history"))
        _searchHistory.value = readSearchHistory()
        AppSettings.reload()
    }

    private fun readArtists(): List<PreferredArtist> =
        records("artists").mapNotNull { f ->
            if (f.size < 2) null else PreferredArtist(f[0], f[1], f.getOrNull(2)?.ifBlank { null })
        }

    private fun readSearchHistory(): List<String> =
        props.getProperty("search_history").orEmpty().split(RECORD).filter { it.isNotBlank() }

    private fun encodeSongs(songs: List<SongItem>): List<List<String>> = songs.map { s ->
        listOf(
            s.id,
            s.title,
            s.artists.joinToString(LIST.toString()) { "${it.name}$PAIR${it.id.orEmpty()}" },
            s.thumbnail,
            s.duration?.toString().orEmpty(),
            s.album?.name.orEmpty(),
            s.album?.id.orEmpty(),
        )
    }

    private fun decodeSongs(rows: List<List<String>>): List<SongItem> = rows.mapNotNull { f ->
        if (f.size < 4) return@mapNotNull null
        SongItem(
            id = f[0],
            title = f[1],
            artists = f[2].split(LIST).filter { it.isNotEmpty() }.map {
                Artist(name = it.substringBefore(PAIR), id = it.substringAfter(PAIR, "").ifBlank { null })
            },
            thumbnail = f[3],
            duration = f.getOrNull(4)?.toIntOrNull(),
            album = f.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { name ->
                f.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { Album(name, it) }
            },
        )
    }

    private fun records(key: String): List<List<String>> =
        props.getProperty(key).orEmpty().split(RECORD).filter { it.isNotEmpty() }.map { it.split(FIELD) }

    private fun putRecords(key: String, rows: List<List<String>>) =
        put(key, rows.joinToString(RECORD.toString()) { it.joinToString(FIELD.toString()) })
}

/**
 * What YouTube is told about the listener. Without this, `hl`/`gl` came from the JVM default and
 * the visitor id was minted with nothing else to go on — so YouTube localised Home by IP.
 * Same rules as the Android app's `applyStrictLocale` + `remintVisitorDataIfLocaleChanged`,
 * including its explicit Content language / country overrides.
 */
object ContentLocale {
    fun apply(languages: List<String> = DesktopPrefs.languages) {
        val settings = AppSettings.current
        val lang = settings.contentLanguage.takeIf { it != "AUTO" } ?: languages.firstOrNull() ?: "en"
        val region = settings.contentCountry.takeIf { it != "AUTO" } ?: anchorRegionFor(lang)
        YouTube.locale = YouTubeLocale(gl = region, hl = lang)
    }

    /** Reuses the stored visitor id only if it was minted under the current locale. */
    suspend fun ensureVisitorData() {
        val signature = "${YouTube.locale.hl}|${YouTube.locale.gl}"
        val saved = DesktopPrefs.visitorData
        // Signed in, the visitor id is bound to the session — reminting it would break the login.
        if (saved != null && (DesktopPrefs.visitorDataLocale == signature || DesktopPrefs.cookie != null)) {
            YouTube.visitorData = saved
            return
        }
        // An old id would introduce the new locale with the old identity.
        YouTube.visitorData = null
        YouTube.visitorData().onSuccess {
            YouTube.visitorData = it
            DesktopPrefs.visitorData = it
            DesktopPrefs.visitorDataLocale = signature
        }
    }

    /** Canonical region per language — overrides the IP-derived one. Same table as the Android app. */
    fun anchorRegionFor(lang: String): String = when (lang.lowercase().substringBefore("-")) {
        "en" -> "US"
        "es" -> "ES"
        "fr" -> "FR"
        "de" -> "DE"
        "it" -> "IT"
        "pt" -> "BR"
        "ru" -> "RU"
        "ja" -> "JP"
        "ko" -> "KR"
        "zh" -> "TW"
        "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "mr" -> "IN"
        "ar" -> "SA"
        "tr" -> "TR"
        "nl" -> "NL"
        "pl" -> "PL"
        "sv" -> "SE"
        "no", "nb" -> "NO"
        "da" -> "DK"
        "fi" -> "FI"
        "id" -> "ID"
        "vi" -> "VN"
        "th" -> "TH"
        "uk" -> "UA"
        "el" -> "GR"
        "he", "iw" -> "IL"
        "cs" -> "CZ"
        "hu" -> "HU"
        "ro" -> "RO"
        else -> "US"
    }
}

/** The Content → Proxy setting, pushed into innertube (and, through the player, mpv and stream probes). */
object NetworkProxy {
    fun apply() {
        val settings = AppSettings.current
        YouTube.proxy = if (settings.proxyEnabled) parse(settings.proxyType, settings.proxyUrl) else null
    }

    fun parse(type: String, url: String): Proxy? = runCatching {
        val uri = URI(if ("://" in url) url.trim() else "http://${url.trim()}")
        val host = uri.host ?: return null
        val kind = if (type == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
        Proxy(kind, InetSocketAddress(host, if (uri.port > 0) uri.port else if (kind == Proxy.Type.SOCKS) 1080 else 8080))
    }.getOrNull()
}
