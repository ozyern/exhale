/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.ListeningLog
import com.ozyern.exhale.desktop.data.Settings
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.YTItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<SongItem> = emptyList(),
    val index: Int = -1,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val durationSeconds: Double = 0.0,
    /** Bumped on every seek, so what follows this state rather than [DesktopPlayer.position] (Discord) still hears about jumps. */
    val seekGeneration: Int = 0,
    val streamInfo: String? = null,
    /** The immersive format the music itself is in — Dolby Atmos, TrueHD… — or null for ordinary stereo. */
    val immersive: ImmersiveFormat? = null,
    /** True while a Dolby track goes out to the receiver as it is, decoded there rather than here. */
    val passthrough: Boolean = false,
    val error: String? = null,
    val volume: Int = 100,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    val current: SongItem? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index + 1 < queue.size || (repeat == RepeatMode.ALL && queue.isNotEmpty())
    val hasPrevious: Boolean get() = index > 0
}

/** Queue + transport on top of [Mpv], with the Player & audio settings applied. [scope] must run on the UI dispatcher. */
class DesktopPlayer(private val scope: CoroutineScope) : AutoCloseable {
    private val _state = MutableStateFlow(
        PlayerState(volume = if (AppSettings.current.rememberVolume) DesktopPrefs.volume else 100),
    )
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /**
     * The playback position, kept out of [state] on purpose. mpv reports it many times a second, and
     * as part of [state] every report recomposed every page, card and control holding it — the whole
     * app, continuously, for as long as music played. Mpv reads it five times a second; the lyrics
     * clock runs smoothly between readings on its own.
     */
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()

    private val _buffered = MutableStateFlow(0.0)

    /** How far the stream has been fetched, in seconds. Everything past it is not there to seek to yet. */
    val buffered: StateFlow<Double> = _buffered.asStateFlow()

    private var loadJob: Job? = null
    private var queueJob: Job? = null
    private var skipJob: Job? = null
    private var volumeSaveJob: Job? = null

    /** The song mpv actually has loaded — differs from the queue's current song after a restore. */
    @Volatile private var loadedSongId: String? = null
    @Volatile private var pendingSeek: Double? = null
    @Volatile private var normalizationDb = 0.0
    @Volatile private var fade = 1.0
    private var lastPersistAt = 0L
    private var unshuffled: List<SongItem>? = null

    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** When the sleep timer pauses playback, or null. */
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private val _sleepAtEndOfSong = MutableStateFlow(false)
    val sleepAtEndOfSong: StateFlow<Boolean> = _sleepAtEndOfSong.asStateFlow()
    private var sleepJob: Job? = null

    private var appliedFilters: String? = null
    /** The track mpv has open, once it's loaded: what [AudioEffects.filterChain] must leave alone. */
    @Volatile private var sourceAudio: SourceAudio? = null
    private var appliedPassthrough: Boolean? = null
    private var appliedSpeed: Float? = null
    private var appliedPitchCorrection: Boolean? = null
    private var appliedProxy: String? = null
    private var appliedGain: Double? = null
    private var appliedChannels: String? = null
    private var appliedExclusive: Boolean? = null
    private var appliedResampler: String? = null
    private var probeJob: Job? = null

    /** The song already given one fresh-URL retry after mpv failed on it; see onError. */
    @Volatile private var retriedSongId: String? = null

    // Your Sound Chem: how long the loaded song has actually been heard — playing time, not position,
    // so seeking around a song doesn't count as listening to it.
    @Volatile private var listenSong: SongItem? = null
    @Volatile private var listenedMs = 0L
    @Volatile private var lastListenTick = 0L

    private val mpvListener = object : Mpv.Listener {
        // load() always unpauses, and mpv only reports pause when it *changes* — so a song that
        // starts while already unpaused never sends one.
        override fun onFileLoaded() {
            pendingSeek?.let {
                pendingSeek = null
                mpv.seekTo(it)
            }
            _state.update { it.copy(isLoading = false, isPlaying = true) }
            WindowsMediaSession.setPlaying(true)
            scope.launch {
                probeSource()
                probeOutput()
            }
        }

        override fun onEndOfFile() {
            flushListen()
            scope.launch { onTrackEnded() }
        }

        override fun onError(message: String) {
            // A cached or prefetched YouTube URL can go stale or get throttled when many songs are
            // started in a row; mpv then reads nothing from it. Ask for a fresh URL once before giving up.
            val failed = loadedSongId
            if (failed != null && failed != retriedSongId && failed == _state.value.current?.id && !_videoMode.value) {
                retriedSongId = failed
                StreamResolver.forget(failed)
                val at = _position.value.takeIf { it > 1.0 }
                println("Exhale: retrying $failed with a fresh stream — $message")
                scope.launch { load(_state.value.index, at) }
                return
            }
            _state.update { it.copy(isLoading = false, isPlaying = false, error = message) }
            scope.launch { scheduleAutoSkip() }
        }

        override fun onPositionChanged(seconds: Double) {
            // Only called while playing, a few times a second; a gap longer than a second is a stall
            // or a resume, not listening.
            if (listenSong != null) {
                val now = System.nanoTime()
                if (lastListenTick != 0L) listenedMs += ((now - lastListenTick) / 1_000_000L).coerceIn(0L, 1_000L)
                lastListenTick = now
            }
            // mpv's clock is the video's in video mode; everything else (the bar, the lyrics) runs on
            // the song's, which starts the length of the video's intro later.
            val songSeconds = (seconds - videoOffsetSec).coerceAtLeast(0.0)
            _position.value = songSeconds
            _buffered.value = ((mpv.bufferedTo() ?: 0.0) - videoOffsetSec).coerceAtLeast(0.0)
            updateFade(songSeconds)
            val now = System.currentTimeMillis()
            if (now - lastPersistAt > 10_000) {
                lastPersistAt = now
                scope.launch { persistQueue() }
            }
        }

        override fun onDurationChanged(seconds: Double) = _state.update { it.copy(durationSeconds = seconds) }
        override fun onPauseChanged(paused: Boolean) {
            lastListenTick = 0L
            _state.update { it.copy(isPlaying = !paused && it.current != null) }
            WindowsMediaSession.setPlaying(!paused)
        }
    }

    // Rebuilt when the song switches between sound and picture: mpv's window is fixed once it starts.
    @Volatile private var mpv: Mpv = Mpv(mpvListener)

    private val _videoMode = MutableStateFlow(false)

    /** Whether this song is playing as a video rather than as sound alone. */
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    private val _videoQualities = MutableStateFlow<List<Int>>(emptyList())

    /** The heights this video has, for the picker — so it never offers 4K to a video that hasn't got it. */
    val videoQualities: StateFlow<List<Int>> = _videoQualities.asStateFlow()

    private val _videoAspect = MutableStateFlow(16f / 9f)

    /** The shape of the picture now playing, so the surface can be cut to it rather than letterbox it. */
    val videoAspect: StateFlow<Float> = _videoAspect.asStateFlow()

    private val _videoAvailable = MutableStateFlow(false)

    /** Whether YouTube has a video of what's playing — an official video, a live take — at all. */
    val videoAvailable: StateFlow<Boolean> = _videoAvailable.asStateFlow()

    @Volatile private var videoSurface: Long? = null

    init {
        scope.launch { AppSettings.state.collect { applyEngineSettings(it) } }
        // Whether there's a video of whatever is playing — the song itself, or the music video made
        // for it when the song is an album track — so it's known for a song restored from last time
        // as well as one just started.
        scope.launch {
            state.map { it.current }.distinctUntilChanged { a, b -> a?.id == b?.id }.collect { song ->
                _videoAvailable.value = false
                if (song == null || OpenFlacSongs.isFlacId(song.id)) return@collect
                val answer = runCatching { MusicVideoMatch.videoIdFor(song) != null }.getOrDefault(false)
                if (_state.value.current?.id == song.id) _videoAvailable.value = answer
            }
        }
        // Whatever lossless music is already on this PC, ready before the first song plays.
        scope.launch { runCatching { LosslessLibrary.ensureDefaultSource() } }
        restoreQueue()
        // The first song after launch pays for everything the rest are spared — YouTube's player script,
        // the client that works — so the restored song is worked out now, while the window is still coming up.
        _state.value.current?.takeUnless { OpenFlacSongs.isFlacId(it.id) }?.let { song -> scope.launch { runCatching { StreamResolver.warm(song.id) } } }
    }

    /** Settings that act on the audio engine itself. */
    private fun applyEngineSettings(settings: Settings) {
        val opened = AudioOutputStatus.state.value?.channels
        val sourceChannels = sourceAudio?.channels
        val chain = AudioEffects.filterChain(settings, opened, sourceChannels = sourceChannels)
        if (chain != appliedFilters) {
            // A libmpv without the upmix filter refuses the whole chain; keep the rest of it. And one
            // without 8D's graph either, so the equalizer at least survives — as the linear-phase FIR if
            // this build has it, as the classic band filters if not.
            val fallbacks = sequenceOf(
                chain,
                AudioEffects.filterChain(settings.copy(spatialAudio = "OFF")),
                AudioEffects.filterChain(settings.copy(spatialAudio = "OFF", eightD = false)),
                AudioEffects.filterChain(settings, opened, linearPhase = false, sourceChannels = sourceChannels),
                AudioEffects.filterChain(settings.copy(spatialAudio = "OFF", eightD = false), linearPhase = false),
            )
            fallbacks.firstOrNull { mpv.setProperty("af", it) }
            appliedFilters = chain
        }
        // Dolby passthrough: Dolby Digital, Dolby Digital Plus (and the Atmos it carries) and TrueHD go
        // to the receiver or soundbar bit for bit, which decodes them — Atmos' objects and all — rather
        // than Exhale decoding them to channels. Stereo music is unaffected; it isn't in those codecs.
        if (settings.dolbyPassthrough != appliedPassthrough) {
            mpv.setProperty("audio-spdif", if (settings.dolbyPassthrough) ImmersiveAudio.PassthroughCodecs else "")
            appliedPassthrough = settings.dolbyPassthrough
        }
        val channels = AudioEffects.outputChannels(settings)
        val resampler = AudioEffects.resamplerOptions(settings)
        if (channels != appliedChannels || settings.exclusiveAudio != appliedExclusive || resampler != appliedResampler) {
            val firstTime = appliedChannels == null
            mpv.setProperty("audio-channels", channels)
            // Should anything still fold surround down to stereo, it is scaled to fit rather than clipped.
            mpv.setProperty("audio-normalize-downmix", "yes")
            mpv.setProperty("audio-exclusive", if (settings.exclusiveAudio) "yes" else "no")
            mpv.setProperty("audio-swresample-o", resampler)
            appliedChannels = channels
            appliedExclusive = settings.exclusiveAudio
            appliedResampler = resampler
            if (!firstTime) {
                mpv.reloadAudioOutput()
                probeOutput()
            }
        }
        // A Sound preset plays the song like a record at another speed, the pitch going with it; the
        // plain speed setting keeps the pitch where it was.
        val speed = AudioEffects.effectiveSpeed(settings)
        val pitchCorrection = AudioEffects.pitchCorrection(settings)
        if (speed != appliedSpeed || pitchCorrection != appliedPitchCorrection) {
            mpv.setProperty("audio-pitch-correction", if (pitchCorrection) "yes" else "no")
            mpv.setProperty("speed", String.format(Locale.US, "%.2f", speed))
            appliedSpeed = speed
            appliedPitchCorrection = pitchCorrection
        }
        val proxy = if (settings.proxyEnabled && settings.proxyType == "HTTP" && settings.proxyUrl.isNotBlank()) {
            if ("://" in settings.proxyUrl) settings.proxyUrl.trim() else "http://${settings.proxyUrl.trim()}"
        } else {
            ""
        }
        if (proxy != appliedProxy) {
            mpv.setProperty("http-proxy", proxy)
            appliedProxy = proxy
        }
        // The keyboard's own media keys. Only worth claiming while Windows isn't already sending them
        // to Exhale as session buttons.
        MediaKeys.apply(this, settings.mediaKeys && !WindowsMediaSession.active)
        applyVolume()
    }

    /**
     * The user's level, scaled by the fade, over a pre-amp that brings every track to YouTube's
     * loudness target. Like Android's normalizeFactor, it only ever turns a loud track down.
     */
    private fun applyVolume() {
        val gain = if (AppSettings.current.normalization) normalizationDb else 0.0
        if (gain != appliedGain) {
            mpv.setProperty("volume-gain", String.format(Locale.US, "%.2f", gain))
            appliedGain = gain
        }
        mpv.setVolume(_state.value.volume * fade)
    }

    /**
     * What the music itself is: its codec, channels, and whether it's Dolby Atmos. A multichannel
     * source must never meet the stereo upmix (it would rebuild the surrounds from the front pair and
     * throw the real ones away), so the effects are laid out again once this is known.
     */
    private suspend fun probeSource() {
        val source = withContext(Dispatchers.IO) { ImmersiveAudio.fromTrackList(mpv.getProperty("track-list")) }
        sourceAudio = source
        val passthrough = AppSettings.current.dolbyPassthrough &&
            source?.codec in setOf("ac3", "eac3", "truehd") &&
            withContext(Dispatchers.IO) { mpv.getProperty("audio-out-params/format") }?.startsWith("spdif") == true
        _state.update { it.copy(immersive = source?.format, passthrough = passthrough) }
        applyEngineSettings(AppSettings.current)
    }

    /** Reads back what the audio device actually opened with — Settings and the player badge show it. */
    private fun probeOutput() {
        probeJob?.cancel()
        probeJob = scope.launch {
            delay(900)
            val output = withContext(Dispatchers.IO) {
                val channels = mpv.getProperty("audio-out-params/channel-count")?.toIntOrNull() ?: return@withContext null
                AudioOutput(
                    channels = channels,
                    sampleRate = mpv.getProperty("audio-out-params/samplerate")?.toIntOrNull(),
                    codec = mpv.getProperty("audio-codec-name"),
                    driver = mpv.getProperty("current-ao"),
                    exclusive = AppSettings.current.exclusiveAudio,
                )
            }
            if (output != null) {
                val before = AudioOutputStatus.state.value?.channels
                AudioOutputStatus.publish(output)
                // The upmix follows what the device opened, so it is laid out (or taken away) now.
                if (output.channels != before) applyEngineSettings(AppSettings.current)
            }
        }
    }

    /** "Fade between songs": ease out over the last seconds of a track and back in at the start of the next. */
    private fun updateFade(position: Double) {
        val seconds = AppSettings.current.crossfadeSeconds
        val duration = _state.value.durationSeconds
        val target = if (seconds <= 0 || duration <= 0) {
            1.0
        } else {
            val out = ((duration - position) / seconds).coerceIn(0.0, 1.0)
            val `in` = (position / (seconds / 2.0).coerceAtLeast(0.5)).coerceIn(0.0, 1.0)
            minOf(out, `in`)
        }
        if (abs(target - fade) >= 0.02 || (target == 1.0 && fade != 1.0)) {
            fade = target
            applyVolume()
        }
    }

    /** Starts [index] of [queue]. With shuffle on, everything after the chosen song is shuffled. */
    fun play(queue: List<SongItem>, index: Int) {
        val song = queue.getOrNull(index) ?: return
        if (_state.value.shuffle) {
            unshuffled = queue
            val rest = queue.filterIndexed { i, _ -> i != index }.shuffled()
            _state.update { it.copy(queue = listOf(song) + rest, index = 0) }
        } else {
            unshuffled = null
            _state.update { it.copy(queue = queue, index = index) }
        }
        load(_state.value.index)
    }

    private fun load(index: Int, startAt: Double? = null) {
        val song = _state.value.queue.getOrNull(index) ?: return
        if (song.id != retriedSongId) retriedSongId = null
        loadJob?.cancel()
        skipJob?.cancel()
        mpv.stop()
        flushListen()
        listenSong = song
        loadedSongId = null
        pendingSeek = null
        // Until the next track says what it is, nothing about this one's channels applies.
        sourceAudio = null
        fade = if (AppSettings.current.crossfadeSeconds > 0) 0.0 else 1.0
        _state.update {
            it.copy(
                index = index,
                isLoading = true,
                isPlaying = false,
                durationSeconds = song.duration?.toDouble() ?: 0.0,
                streamInfo = null,
                immersive = null,
                passthrough = false,
                error = null,
            )
        }
        _position.value = startAt ?: 0.0
        _buffered.value = 0.0
        // A new song starts on its own clock; video mode sets its offset again once it knows it.
        if (videoOffsetSec != 0.0) {
            videoOffsetSec = 0.0
            mpv.setProperty("audio-delay", "0")
        }
        // What Windows shows in its own panel and on the volume overlay.
        WindowsMediaSession.nowPlaying(song.title, song.artists.joinToString { it.name })
        loadJob = scope.launch {
            // A FLAC-page song is its own file: no video, no YouTube, nothing to look up.
            val direct = OpenFlacSongs.streamFor(song.id)
            // Watching rather than listening: the picture and its own sound track, into the surface.
            if (direct == null && _videoMode.value && videoSurface != null) {
                _videoAvailable.value = true
                // An album track has no picture of its own: the music video's picture is shown, and
                // the sound stays the song's — the video's own track can carry skits, a longer intro
                // or a radio edit.
                val videoId = runCatching { MusicVideoMatch.videoIdFor(song) }.getOrNull() ?: song.id
                // The video's own soundtrack, kept for lining the two up before it is swapped out.
                var videoSound: Pair<String, StreamHeaders>? = null
                var songSound: Pair<String, StreamHeaders>? = null
                StreamResolver.resolveVideo(videoId)
                    .map { video ->
                        if (videoId == song.id) return@map video
                        val sound = StreamResolver.resolve(song.id).getOrNull() ?: return@map video
                        videoSound = (video.audioUrl ?: video.videoUrl) to video.headers
                        songSound = sound.url to sound.headers
                        video.copy(audioUrl = sound.url, headers = video.headers.merge(sound.headers))
                    }
                    .onSuccess { video ->
                        println("Exhale video: playing ${video.quality} from ${video.clientName}")
                        ensureActive()
                        normalizationDb = 0.0
                        _state.update { it.copy(streamInfo = "Video · ${video.quality}") }
                        _videoAspect.value = video.aspect
                        _videoQualities.value = video.available
                        DesktopPrefs.recordPlayed(song)
                        loadedSongId = song.id
                        applyVolume()
                        // Started in step when this pair has been measured before; otherwise together,
                        // and lined up the moment the measurement lands.
                        val known = if (videoId == song.id) 0L else VideoAlignment.known(videoId, song.id)
                        videoOffsetSec = (known ?: 0L) / 1000.0
                        mpv.setProperty("audio-delay", videoOffsetSec.toString())
                        val start = (startAt ?: 0.0) + videoOffsetSec
                        mpv.loadVideo(video.videoUrl, video.audioUrl, video.headers, start.takeIf { it > 1.0 })
                        persistQueue()
                        val ownSound = videoSound
                        val itsSong = songSound
                        if (known == null && ownSound != null && itsSong != null) {
                            scope.launch {
                                val measured = VideoAlignment.offsetMs(videoId, song.id, ownSound.first, ownSound.second, itsSong.first, itsSong.second)
                                    ?: return@launch
                                println("Exhale video: $videoId starts its song ${measured} ms in")
                                if (_videoMode.value && loadedSongId == song.id) applyVideoOffset(measured / 1000.0)
                            }
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        // No video after all: fall back to the sound rather than stopping.
                        println("Exhale video: failed — ${error.message}")
                        _state.update { it.copy(error = error.message ?: "No video for this song") }
                        setVideoMode(false)
                    }
                return@launch
            }
            // Both are asked at once. A folder on this PC answers in milliseconds where a public
            // catalogue can take seconds, and no song should ever wait on one: YouTube's stream is
            // ready when it's ready, a lossless copy gets a moment's grace to beat it, and whatever
            // hasn't answered by then is dropped.
            val resolved = direct?.let { Result.success(it) } ?: run {
                val youtube = async { StreamResolver.resolve(song.id) }
                val lossless = if (AppSettings.current.preferLossless && LosslessLibrary.isConfigured) {
                    async { runCatching { LosslessLibrary.resolve(song) }.getOrNull() }
                } else {
                    null
                }
                val fromYouTube = youtube.await()
                val better = lossless?.let { withTimeoutOrNull(700) { it.await() } }
                lossless?.cancel()
                better?.let { Result.success(it) } ?: fromYouTube
            }
            resolved
                .onSuccess { stream ->
                    ensureActive()
                    normalizationDb = stream.loudnessDb?.let { (-it).coerceIn(-20.0, 0.0) } ?: 0.0
                    _state.update { it.copy(streamInfo = stream.describe()) }
                    DesktopPrefs.recordPlayed(song)
                    loadedSongId = song.id
                    applyVolume()
                    mpv.load(stream.url, stream.headers, startAt?.takeIf { it > 1.0 })
                    persistQueue()
                    prefetchNext(index)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(isLoading = false, error = error.message ?: error::class.simpleName) }
                    scheduleAutoSkip()
                }
        }
    }

    /**
     * Switch what's playing between sound and picture. Turning it on only sets the intent — the player
     * puts a video surface on screen and hands its handle to [attachVideoSurface], because mpv can only
     * be told which window to draw into as it starts.
     */
    fun setVideoMode(on: Boolean) {
        if (_videoMode.value == on) return
        println("Exhale video: mode -> $on")
        _videoMode.value = on
        if (!on) {
            videoSurface = null
            rebuildMpv(null)
        }
    }

    /** The video surface is on screen: rebuild mpv against it and pick the song up where it was. */
    /**
     * The surface has gone — the player is collapsing, or the picture is being taken down. mpv must
     * be told: it is drawing into a window of the toolkit's that no longer exists, and a handle that
     * has been destroyed underneath it is not something to leave it holding.
     */
    fun detachVideoSurface(handle: Long) {
        if (videoSurface != handle || handle == 0L) return
        println("Exhale video: surface $handle gone")
        videoSurface = null
        if (_videoMode.value) rebuildMpv(null)
    }

    fun attachVideoSurface(handle: Long) {
        if (!_videoMode.value || videoSurface == handle || handle == 0L) return
        println("Exhale video: attaching surface $handle")
        videoSurface = handle
        rebuildMpv(handle)
    }

    /** Starts mpv again — drawing into [windowId], or into nothing — and resumes the song where it stood. */
    /** Which window mpv is currently drawing into, so it isn't rebuilt onto the one it already has. */
    @Volatile private var mpvWindowId: Long? = null

    private fun rebuildMpv(windowId: Long?) {
        if (mpvWindowId == windowId && loadedSongId != null) return
        mpvWindowId = windowId
        val resumeAt = _position.value
        val index = _state.value.index
        runCatching { mpv.close() }
        mpv = Mpv(mpvListener, windowId)
        // The new mpv knows none of the settings the old one had.
        appliedFilters = null
        appliedPassthrough = null
        appliedSpeed = null
        appliedPitchCorrection = null
        appliedProxy = null
        appliedGain = null
        appliedChannels = null
        appliedExclusive = null
        appliedResampler = null
        applyEngineSettings(AppSettings.current)
        audioDevice?.let { mpv.setProperty("audio-device", it) }
        if (index in _state.value.queue.indices) load(index, startAt = resumeAt)
    }

    /** One way out of the PC: mpv's name for it, and the name Windows gives it. */
    data class AudioOutput(val name: String, val description: String)

    /** The output chosen in the player bar for this session; null is Windows' default. */
    @Volatile private var audioDevice: String? = null

    /** Every output mpv can play to, Windows' default first. */
    fun audioOutputs(): List<AudioOutput> = runCatching {
        val list = org.json.JSONArray(mpv.getProperty("audio-device-list") ?: "[]")
        (0 until list.length()).mapNotNull { i ->
            val entry = list.optJSONObject(i) ?: return@mapNotNull null
            val name = entry.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Only the shared-mode WASAPI devices and "auto": the rest are the same speakers again.
            if (name != "auto" && !name.startsWith("wasapi/")) return@mapNotNull null
            AudioOutput(name, if (name == "auto") "Windows default" else entry.optString("description").ifBlank { name })
        }
    }.getOrDefault(emptyList())

    fun currentAudioOutput(): String = audioDevice ?: "auto"

    fun setAudioOutput(name: String) {
        audioDevice = name.takeUnless { it == "auto" }
        mpv.setProperty("audio-device", name)
    }

    /** Works out the next song's URL while this one plays, so pressing skip starts it at once. */
    private fun prefetchNext(index: Int) {
        val next = _state.value.queue.getOrNull(index + 1) ?: return
        if (OpenFlacSongs.isFlacId(next.id)) return
        scope.launch { runCatching { StreamResolver.warm(next.id) } }
    }

    /**
     * Play anything a home shelf shows. A song starts at once and its radio fills the queue in
     * behind it; albums, playlists and artists start from the queue YouTube hands back.
     */
    fun playItem(item: YTItem) {
        val endpoint = when (item) {
            is SongItem -> item.endpoint ?: WatchEndpoint(videoId = item.id)
            is AlbumItem -> WatchEndpoint(playlistId = item.playlistId)
            is PlaylistItem -> item.playEndpoint ?: WatchEndpoint(playlistId = item.id.removePrefix("VL"))
            is ArtistItem -> item.radioEndpoint ?: item.shuffleEndpoint ?: item.playEndpoint ?: return
        }
        if (item is SongItem) play(listOf(item), 0)

        queueJob?.cancel()
        queueJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.next(endpoint) }
                .onSuccess { result ->
                    if (result.items.isEmpty()) return@onSuccess
                    if (item is SongItem) {
                        // Only if the user hasn't moved on while the radio was loading.
                        if (_state.value.current?.id != item.id) return@onSuccess
                        val others = result.items.filterNot { it.id == item.id }
                        val rest = if (_state.value.shuffle) others.shuffled() else others
                        _state.update { it.copy(queue = listOf(item) + rest, index = 0) }
                        persistQueue()
                    } else {
                        play(result.items, result.currentIndex ?: 0)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (item !is SongItem) _state.update { it.copy(error = error.message ?: "Couldn't start ${item.title}") }
                }
        }
    }

    /** An artist's station, for places that only know the artist's id. */
    fun playArtist(browseId: String) {
        queueJob?.cancel()
        queueJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.artist(browseId) }
                .onSuccess { page -> playItem(page.artist) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(error = error.message ?: "Couldn't start that artist") }
                }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        val song = current.current ?: return
        if (current.isLoading) return
        // After a restore the queue is there but nothing is loaded yet: start where it was left.
        if (loadedSongId != song.id) {
            load(current.index, startAt = _position.value)
            return
        }
        mpv.togglePause()
    }

    /** Fetch the picture again — a different quality was asked for — without losing the place. */
    fun refreshVideo() {
        val index = _state.value.index
        if (index in _state.value.queue.indices) load(index, startAt = _position.value)
    }

    fun seekTo(seconds: Double) {
        val current = _state.value
        if (current.current == null) return
        _position.value = seconds
        _state.update { it.copy(seekGeneration = it.seekGeneration + 1) }
        if (loadedSongId != current.current?.id) return
        mpv.seekTo(seconds + videoOffsetSec)
    }

    /**
     * In video mode, how far into the video its song begins (see VideoAlignment): the song's sound is
     * delayed by this in mpv, and positions are reported on the song's clock. 0 for everything else.
     */
    @Volatile private var videoOffsetSec = 0.0

    /** Lines the playing video up with its song, once measured: sound delayed, picture moved on with it. */
    private fun applyVideoOffset(seconds: Double) {
        val change = seconds - videoOffsetSec
        if (kotlin.math.abs(change) < 0.05) return
        videoOffsetSec = seconds
        mpv.setProperty("audio-delay", seconds.toString())
        // The picture jumps on by the change, so the song carries on from where it was heard.
        mpv.seekTo(_position.value + seconds)
    }

    fun seekBy(deltaSeconds: Double) {
        val current = _state.value
        val limit = current.durationSeconds.takeIf { it > 0 } ?: Double.MAX_VALUE
        seekTo((_position.value + deltaSeconds).coerceIn(0.0, limit))
    }

    fun next() {
        val current = _state.value
        when {
            current.index + 1 < current.queue.size -> load(current.index + 1)
            current.repeat == RepeatMode.ALL && current.queue.isNotEmpty() -> load(0)
            else -> {
                _state.update { it.copy(isPlaying = false) }
                _position.value = 0.0
                _buffered.value = 0.0
            }
        }
    }

    fun previous() {
        val current = _state.value
        // Like every music player: past the first few seconds, "previous" restarts the song.
        if (_position.value > 3.0 || !current.hasPrevious) {
            seekTo(0.0)
        } else {
            load(current.index - 1)
        }
    }

    /** Jump to a song already in the queue. */
    fun skipTo(index: Int) {
        if (index in _state.value.queue.indices) load(index)
    }

    private fun onTrackEnded() {
        if (_sleepAtEndOfSong.value) {
            _sleepAtEndOfSong.value = false
            _state.update { it.copy(isPlaying = false) }
            return
        }
        if (_state.value.repeat == RepeatMode.ONE) load(_state.value.index) else next()
    }

    /** Menu → Start radio: the song keeps playing and everything after it becomes its radio. */
    fun startRadio(song: SongItem) {
        queueJob?.cancel()
        queueJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.next(WatchEndpoint(videoId = song.id)) }
                .onSuccess { result ->
                    if (_state.value.current?.id != song.id) return@onSuccess
                    val rest = result.items.filterNot { it.id == song.id }
                    _state.update { it.copy(queue = listOf(song) + (if (it.shuffle) rest.shuffled() else rest), index = 0) }
                    persistQueue()
                }
        }
    }

    /** Puts [song] straight after the one playing. */
    fun playNext(song: SongItem) {
        val current = _state.value
        if (current.current == null) {
            play(listOf(song), 0)
            return
        }
        val queue = current.queue.toMutableList().apply { add(current.index + 1, song) }
        _state.update { it.copy(queue = queue) }
        persistQueue()
    }

    /** Onto the end of the queue. With nothing playing it simply starts. */
    fun addToQueue(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val current = _state.value
        if (current.current == null) {
            play(songs, 0)
            return
        }
        _state.update { it.copy(queue = it.queue + songs) }
        persistQueue()
    }

    fun addToQueue(song: SongItem) = addToQueue(listOf(song))

    /** A whole album or playlist straight after the song playing, in its own order. */
    fun playNext(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val current = _state.value
        if (current.current == null) {
            play(songs, 0)
            return
        }
        val queue = current.queue.toMutableList().apply { addAll(current.index + 1, songs) }
        _state.update { it.copy(queue = queue) }
        persistQueue()
    }

    /**
     * Takes one song out. Removing the song playing moves on to the next; removing one before it keeps
     * the same song playing, since the index it sits at has shifted.
     */
    fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        val queue = current.queue.toMutableList().apply { removeAt(index) }
        when {
            queue.isEmpty() -> {
                mpv.stop()
                loadedSongId = null
                _state.update { it.copy(queue = emptyList(), index = -1, isPlaying = false, isLoading = false) }
            }
            index < current.index -> _state.update { it.copy(queue = queue, index = it.index - 1) }
            index > current.index -> _state.update { it.copy(queue = queue) }
            else -> {
                // The song playing: hold its place and start whatever fell into it.
                _state.update { it.copy(queue = queue, index = index.coerceAtMost(queue.size - 1)) }
                load(_state.value.index)
            }
        }
        persistQueue()
    }

    /** Drags a song to another place in the queue, keeping whatever is playing playing. */
    fun moveInQueue(from: Int, to: Int) {
        val current = _state.value
        if (from !in current.queue.indices || to !in current.queue.indices || from == to) return
        val queue = current.queue.toMutableList()
        val song = queue.removeAt(from)
        queue.add(to, song)
        val index = when (current.index) {
            from -> to
            in (minOf(from, to))..(maxOf(from, to)) -> if (from < to) current.index - 1 else current.index + 1
            else -> current.index
        }
        _state.update { it.copy(queue = queue, index = index.coerceIn(0, queue.size - 1)) }
        persistQueue()
    }

    /** Everything after the song playing, gone. */
    fun clearUpNext() {
        val current = _state.value
        if (current.index < 0 || current.index >= current.queue.size - 1) return
        _state.update { it.copy(queue = it.queue.take(it.index + 1)) }
        persistQueue()
    }

    /** Menu → Sleep timer. Null turns it off; 0 means "at the end of this song". */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepAt.value = null
        _sleepAtEndOfSong.value = false
        when {
            minutes == null -> Unit
            minutes <= 0 -> _sleepAtEndOfSong.value = true
            else -> {
                _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
                sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    if (_state.value.isPlaying) mpv.togglePause()
                    _sleepAt.value = null
                }
            }
        }
    }

    private fun scheduleAutoSkip() {
        if (!AppSettings.current.autoSkipOnError || !_state.value.hasNext) return
        skipJob?.cancel()
        skipJob = scope.launch {
            delay(1_500)
            next()
        }
    }

    fun toggleShuffle() {
        val current = _state.value
        val song = current.current
        if (!current.shuffle) {
            unshuffled = current.queue
            val rest = current.queue.filterIndexed { i, _ -> i != current.index }.shuffled()
            _state.update { it.copy(shuffle = true, queue = listOfNotNull(song) + rest, index = if (song != null) 0 else -1) }
        } else {
            val original = unshuffled
            val originalIndex = original?.indexOfFirst { it.id == song?.id } ?: -1
            _state.update {
                if (original != null && originalIndex >= 0) it.copy(shuffle = false, queue = original, index = originalIndex)
                else it.copy(shuffle = false)
            }
            unshuffled = null
        }
        persistQueue()
    }

    fun cycleRepeat() {
        _state.update { it.copy(repeat = RepeatMode.entries[(it.repeat.ordinal + 1) % RepeatMode.entries.size]) }
    }

    private var volumeBeforeMute = 100

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _state.update { it.copy(volume = clamped) }
        applyVolume()
        volumeSaveJob?.cancel()
        volumeSaveJob = scope.launch {
            delay(500)
            if (AppSettings.current.rememberVolume) DesktopPrefs.volume = clamped
        }
    }

    fun toggleMute() {
        val current = _state.value.volume
        if (current > 0) {
            volumeBeforeMute = current
            setVolume(0)
        } else {
            setVolume(volumeBeforeMute.coerceAtLeast(10))
        }
    }

    private fun persistQueue() {
        if (!AppSettings.current.persistentQueue) return
        val current = _state.value
        runCatching { DesktopPrefs.saveQueue(current.queue, current.index, _position.value) }
    }

    /** "Resume where you left off": the queue comes back paused, and play picks it up at the same second. */
    private fun restoreQueue() {
        if (!AppSettings.current.persistentQueue) return
        val saved = DesktopPrefs.savedQueue() ?: return
        _state.update {
            it.copy(
                queue = saved.songs,
                index = saved.index,
                durationSeconds = saved.songs[saved.index].duration?.toDouble() ?: 0.0,
            )
        }
        _position.value = saved.positionSeconds
    }

    /** Writes down how long the song that's ending was heard, if it was heard long enough to count. */
    private fun flushListen(immediately: Boolean = false) {
        val song = listenSong ?: return
        val heard = listenedMs
        listenSong = null
        listenedMs = 0L
        lastListenTick = 0L
        if (heard < ListeningLog.MinimumListenMs) return
        if (immediately) ListeningLog.record(song, heard) else scope.launch(Dispatchers.IO) { ListeningLog.record(song, heard) }
    }

    override fun close() {
        flushListen(immediately = true)
        persistQueue()
        loadJob?.cancel()
        MediaKeys.stop()
        mpv.close()
    }
}
