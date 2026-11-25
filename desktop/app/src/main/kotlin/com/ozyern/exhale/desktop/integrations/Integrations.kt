/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.integrations

import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.Settings
import com.ozyern.exhale.desktop.data.UpdateChecker
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.lastfm.LastFM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs

/** Everything that listens to playback from the outside: scrobblers, Discord, lyrics preloading, update checks. */
object Integrations {
    fun start(player: DesktopPlayer, scope: CoroutineScope) {
        LastFmAuth.init()
        val tracker = ListenTracker(scope)
        var preloadedFor: String? = null

        scope.launch(Dispatchers.IO) {
            combine(player.state, AppSettings.state) { playback, settings -> playback to settings }
                .conflate()
                .collect { (playback, settings) ->
                    tracker.onState(playback, settings)
                    DiscordPresence.update(playback, settings, player.position.value)

                    // Lyrics for the next song, so opening them after a skip is instant.
                    val current = playback.current?.id
                    if (settings.preloadLyrics && current != null && current != preloadedFor) {
                        preloadedFor = current
                        playback.queue.getOrNull(playback.index + 1)?.let { next ->
                            scope.launch(Dispatchers.IO) { runCatching { LyricsRepository.lyricsFor(next, next.duration ?: 0) } }
                        }
                    }
                }
        }

        // Presence is otherwise only touched when the player's state changes. A connection that failed while
        // paused would stay dark until someone pressed play, so it is looked at again now and then.
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(15_000)
                DiscordPresence.update(player.state.value, AppSettings.current, player.position.value)
            }
        }

        if (AppSettings.current.checkUpdates) {
            scope.launch(Dispatchers.IO) {
                UpdateChecker.latest().onSuccess { if (UpdateChecker.isNewer(it.tag)) UpdateChecker.available.value = it }
            }
        }
    }
}

/** Last.fm sign-in, with the API key the build was given. Same flow as the Android settings screen. */
object LastFmAuth {
    val isConfigured: Boolean get() = LastFM.isInitialized()

    fun init() {
        val key = System.getProperty("exhale.lastfm.key").orEmpty()
        val secret = System.getProperty("exhale.lastfm.secret").orEmpty()
        if (key.isNotBlank() && secret.isNotBlank()) LastFM.initialize(apiKey = key, secret = secret)
    }

    suspend fun signIn(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        LastFM.getMobileSession(username.trim(), password).map { auth ->
            AppSettings.update { it.copy(lastFmEnabled = true, lastFmUsername = auth.session.name, lastFmSession = auth.session.key) }
            auth.session.name
        }
    }

    fun signOut() = AppSettings.update { it.copy(lastFmEnabled = false, lastFmUsername = null, lastFmSession = null) }
}

/**
 * Now-playing and scrobble timing for Last.fm and ListenBrainz, from the Android ScrobbleManager:
 * a song counts once it has actually been *played* (paused time doesn't count) for the lesser of
 * a percentage of its length and a fixed delay, and only if it's long enough to count at all.
 */
private class ListenTracker(private val scope: CoroutineScope) {
    private var songId: String? = null
    private var startedAt = 0L
    private var playedMs = 0L
    private var lastTick = 0L
    private var nowPlayingSent = false
    private var scrobbled = false

    fun onState(playback: PlayerState, settings: Settings) {
        val song = playback.current
        val now = System.currentTimeMillis()
        if (song?.id != songId) {
            songId = song?.id
            startedAt = now / 1000
            playedMs = 0
            nowPlayingSent = false
            scrobbled = false
            lastTick = now
        }
        if (song == null) return
        if (playback.isPlaying) playedMs += (now - lastTick).coerceIn(0, 2_000)
        lastTick = now

        val duration = playback.durationSeconds.toInt().takeIf { it > 0 } ?: song.duration ?: 0
        if (playback.isPlaying && !nowPlayingSent) {
            nowPlayingSent = true
            scope.launch(Dispatchers.IO) { nowPlaying(song, duration, settings) }
        }
        val threshold = minOf(duration * settings.scrobblePercent / 100.0, settings.scrobbleMaxDelay.toDouble()) * 1000
        if (!scrobbled && duration >= settings.scrobbleMinDuration && playedMs >= threshold) {
            scrobbled = true
            val timestamp = startedAt
            scope.launch(Dispatchers.IO) { scrobble(song, duration, timestamp, settings) }
        }
    }

    private suspend fun nowPlaying(song: SongItem, duration: Int, settings: Settings) {
        val artist = song.artists.joinToString(", ") { it.name }
        if (settings.lastFmEnabled && settings.lastFmSession != null && LastFM.isInitialized()) {
            LastFM.sessionKey = settings.lastFmSession
            LastFM.updateNowPlaying(artist = artist, track = song.title, album = song.album?.name, duration = duration)
        }
        if (settings.listenBrainzEnabled && settings.listenBrainzToken.isNotBlank()) {
            ListenBrainz.submit(settings.listenBrainzToken, "playing_now", song, duration, listenedAt = null)
        }
    }

    private suspend fun scrobble(song: SongItem, duration: Int, timestamp: Long, settings: Settings) {
        val artist = song.artists.joinToString(", ") { it.name }
        if (settings.lastFmEnabled && settings.lastFmSession != null && LastFM.isInitialized()) {
            LastFM.sessionKey = settings.lastFmSession
            LastFM.scrobble(artist = artist, track = song.title, timestamp = timestamp, album = song.album?.name, duration = duration)
        }
        if (settings.listenBrainzEnabled && settings.listenBrainzToken.isNotBlank()) {
            ListenBrainz.submit(settings.listenBrainzToken, "single", song, duration, listenedAt = timestamp)
        }
    }
}

/** ListenBrainz submit-listens, with the same payload the Android ListenBrainzManager sends. */
object ListenBrainz {
    private val http = OkHttpClient()

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun submit(token: String, type: String, song: SongItem, duration: Int, listenedAt: Long?): Boolean = runCatching {
        val artists = escape(song.artists.joinToString(" & ") { it.name })
        val release = song.album?.name?.let { "\"release_name\":\"${escape(it)}\"," }.orEmpty()
        val listened = listenedAt?.let { "\"listened_at\":$it," }.orEmpty()
        val body = "{\"listen_type\":\"$type\",\"payload\":[{$listened\"track_metadata\":{\"artist_name\":\"$artists\"," +
            "\"track_name\":\"${escape(song.title)}\",$release\"additional_info\":{\"duration_ms\":${duration * 1000L}," +
            "\"submission_client\":\"Exhale for Windows\",\"origin_url\":\"https://music.youtube.com/watch?v=${song.id}\"}}}]}"
        val request = Request.Builder()
            .url("https://api.listenbrainz.org/1/submit-listens")
            .header("Authorization", "Token ${token.trim()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Checks a token before it's saved. */
    fun validate(token: String): Boolean = runCatching {
        val request = Request.Builder()
            .url("https://api.listenbrainz.org/1/validate-token")
            .header("Authorization", "Token ${token.trim()}")
            .build()
        http.newCall(request).execute().use { response ->
            response.isSuccessful && Regex("\"valid\"\\s*:\\s*true").containsMatchIn(response.body.string())
        }
    }.getOrDefault(false)
}

/**
 * Discord Rich Presence. With Discord running on this PC it goes through Discord's own local connection,
 * which needs nothing from the listener ([DiscordIpc]). With Discord closed it falls back to Kizzy, the
 * Android app's gateway client, driven by the account token from the login window. Only re-sent when
 * something Discord shows actually changes.
 */
object DiscordPresence {
    /** The application the local connection speaks as; the name Discord displays is set per activity. */
    private const val ApplicationId = "1165706613961789445"

    /** Resent this often over the local connection, which is also how Discord quitting gets noticed. */
    private const val IpcRefreshMs = 60_000L

    private val ipc = DiscordIpc(ApplicationId)
    private var shownLocally = false

    private val _status = MutableStateFlow<String?>(null)

    /** What's happening right now, in words, for Settings. Failures used to go unmentioned anywhere. */
    val status: StateFlow<String?> = _status.asStateFlow()

    /**
     * Exhale's own mark, as the badge on the artwork — the dark one, which sits in a Discord card
     * without a yellow tile around it. Discord serves an application's uploaded assets by id, which
     * would need an application registered to this account; an external image is proxied instead.
     */
    private const val AppIconUrl = "https://raw.githubusercontent.com/ozyern/Exhale/refs/heads/master/assets/icon_dark.png"

    /** Where the mark leads when someone clicks it. */
    private const val WebsiteUrl = "https://exhale.ozyern.me/"

    /**
     * The player's position and the wall clock are read from two different places, so the start time
     * worked back from them wobbles by a fraction of a second on every state change. Only a jump
     * larger than that is a seek; anything smaller is the same playthrough.
     */
    private const val SeekToleranceMs = 3_000L

    /**
     * How often a seek may be re-sent. Discord counts presence updates and quietly drops the ones past
     * its allowance, so a flurry costs the update that mattered. A new song or a pause still goes out
     * at once — those are what someone is looking at.
     */
    private const val MinIntervalMs = 4_000L

    private var rpc: KizzyRPC? = null
    private var rpcToken: String? = null

    /** What Discord is currently showing, so it is only told about a real change. */
    private var shownSong: String? = null
    private var shownPaused = false
    private var shownStartMs = 0L
    private var sentAt = 0L

    private val lock = Mutex()

    suspend fun update(playback: PlayerState, settings: Settings, positionSeconds: Double) = lock.withLock {
        send(playback, settings, positionSeconds)
    }

    private suspend fun send(playback: PlayerState, settings: Settings, positionSeconds: Double) {
        val token = settings.discordToken
        val song = playback.current
        val paused = !playback.isPlaying && !playback.isLoading
        val show = settings.discordEnabled && song != null && (!paused || settings.discordShowPaused)

        if (!show || song == null) {
            if (shownSong != null) {
                if (ipc.isConnected) ipc.set(null)
                // Only worth clearing if there is still a connection to clear it over; reconnecting to
                // say nothing would hold this up for ten seconds at shutdown.
                if (rpc?.isRpcRunning() == true) runCatching { rpc?.stopActivity() }
                shownSong = null
            }
            if (!settings.discordEnabled) {
                close()
                _status.value = null
            }
            return
        }

        // Not while a song is still loading: it has no position yet, so the timestamps would be wrong by
        // however long the load takes — and skipping through songs would send an update for every one.
        if (playback.isLoading) return

        val now = System.currentTimeMillis()
        val startMs = now - (positionSeconds * 1000).toLong()
        val local = ipc.connect()
        val changed = song.id != shownSong || paused != shownPaused || local != shownLocally
        val seeked = !paused && abs(startMs - shownStartMs) > SeekToleranceMs
        val refresh = local && now - sentAt >= IpcRefreshMs
        if (!changed && !seeked && !refresh) return
        // A seek waits its turn; a song or a pause doesn't.
        if (!changed && !refresh && now - sentAt < MinIntervalMs) return

        val duration = (playback.durationSeconds.takeIf { it > 0 } ?: song.duration?.toDouble() ?: 0.0) * 1000

        if (local) {
            // One voice at a time: the fallback's connection is dropped once Discord itself is there.
            closeGateway()
            val sent = ipc.set(
                DiscordActivity(
                    name = "Exhale",
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    image = song.thumbnail.takeIf { it.isNotBlank() }?.resizedThumbnail(544),
                    imageText = song.album?.name ?: song.title,
                    badge = AppIconUrl,
                    badgeText = if (paused) "Paused" else "Exhale",
                    startMs = if (paused) null else startMs,
                    endMs = if (paused || duration <= 0) null else startMs + duration.toLong(),
                ),
            )
            if (sent) {
                shownSong = song.id
                shownPaused = paused
                shownStartMs = startMs
                shownLocally = true
                sentAt = now
                _status.value = "Showing on your profile through the Discord app on this PC."
                return
            }
            // Discord just quit. The next pass, moments away, takes the fallback below.
            shownLocally = false
            shownSong = null
            return
        }

        if (token == null) {
            shownSong = null
            shownLocally = false
            _status.value = "Open the Discord app to show what you're listening to, or log in below to show it without Discord open."
            return
        }

        val client = rpc.takeIf { rpcToken == token } ?: run {
            closeGateway()
            KizzyRPC(token).also {
                rpc = it
                rpcToken = token
            }
        }
        runCatching {
            client.buildActivity(
                // What Discord writes after "Listening to". No application id goes with it: an id makes
                // Discord show the name the application was registered under instead of this one.
                name = "Exhale",
                state = song.artists.joinToString(", ") { it.name }.ifBlank { null },
                details = song.title,
                largeImage = RpcImage.ExternalImage(song.thumbnail.resizedThumbnail(544)),
                smallImage = RpcImage.ExternalImage(AppIconUrl),
                largeText = song.album?.name ?: song.title,
                smallText = if (paused) "Paused" else "Exhale",
                // The mark leads home. Older clients ignore the field and simply don't make it clickable.
                smallUrl = WebsiteUrl,
                startTime = if (paused) null else startMs,
                endTime = if (paused || duration <= 0) null else startMs + duration.toLong(),
                type = KizzyRPC.Type.LISTENING,
            )
        }.onSuccess {
            shownSong = song.id
            shownPaused = paused
            shownStartMs = startMs
            shownLocally = false
            sentAt = now
            _status.value = "Showing on your profile through your Discord log-in. If it doesn't appear, open the Discord app."
        }.onFailure {
            // A socket that failed once goes on failing. Drop it so the next song reconnects.
            println("Exhale Discord: gateway failed — ${it.message}")
            closeGateway()
            _status.value = "Discord didn't accept the saved log-in. Open the Discord app, or log out and log in again."
        }
    }

    private fun closeGateway() {
        runCatching { rpc?.closeRPC() }
        rpc = null
        rpcToken = null
    }

    fun close() {
        closeGateway()
        ipc.close()
        shownSong = null
        shownLocally = false
        sentAt = 0
    }
}
