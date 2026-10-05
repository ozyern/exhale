/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



@file:Suppress("DEPRECATION")

package com.ozyern.exhale.playback

import com.ozyern.exhale.constants.SpatialAudioProfileKey
import com.ozyern.exhale.constants.SpatialAudioProfile
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import android.app.PendingIntent
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import android.content.pm.PackageManager
import android.database.SQLException
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.MediaCodecList
import android.media.audiofx.Virtualizer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.lyrics.LyricsPreloadManager
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.MainActivity
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AudioNormalizationKey
import com.ozyern.exhale.constants.AudioOffload
import com.ozyern.exhale.constants.AudioCrossfadeDurationKey
import com.ozyern.exhale.constants.AutomixEnabledKey
import com.ozyern.exhale.constants.AutomixPerformanceKey
import com.ozyern.exhale.playback.automix.AutomixPerformance
import com.ozyern.exhale.playback.automix.MixAnalyzer
import com.ozyern.exhale.playback.automix.StreamAudioSource
import com.ozyern.exhale.playback.automix.TransitionFilterProcessor
import com.ozyern.exhale.playback.automix.TransitionFilters
import com.ozyern.exhale.constants.AudioCodecKey
import com.ozyern.exhale.constants.AudioQualityKey
import com.ozyern.exhale.constants.AutoLoadMoreKey
import com.ozyern.exhale.constants.AutoDownloadOnLikeKey
import com.ozyern.exhale.constants.AutoSkipNextOnErrorKey
import com.ozyern.exhale.constants.AutoStartOnBluetoothKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.DiscordTokenKey
import com.ozyern.exhale.constants.EqualizerBandLevelsMbKey
import com.ozyern.exhale.constants.EqualizerBassBoostEnabledKey
import com.ozyern.exhale.constants.EqualizerBassBoostStrengthKey
import com.ozyern.exhale.constants.EqualizerEnabledKey
import com.ozyern.exhale.constants.EqualizerOutputGainEnabledKey
import com.ozyern.exhale.constants.EqualizerOutputGainMbKey
import com.ozyern.exhale.constants.EqualizerSelectedProfileIdKey
import com.ozyern.exhale.constants.EqualizerVirtualizerEnabledKey
import com.ozyern.exhale.constants.EqualizerVirtualizerStrengthKey
import com.ozyern.exhale.constants.EnableDiscordRPCKey
import com.ozyern.exhale.constants.EnableLockScreenLyricsKey
import com.ozyern.exhale.constants.LyricsOnMediaCardKey
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoKey
import com.ozyern.exhale.constants.HistoryDuration
import com.ozyern.exhale.constants.MediaSessionConstants.CommandOutputSwitcher
import com.ozyern.exhale.constants.MediaSessionConstants.CommandToggleLike
import com.ozyern.exhale.constants.MediaSessionConstants.CommandToggleStartRadio
import com.ozyern.exhale.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.ozyern.exhale.constants.MediaSessionConstants.CommandToggleShuffle
import com.ozyern.exhale.constants.PauseListenHistoryKey
import com.ozyern.exhale.constants.PauseOnDeviceMuteKey
import com.ozyern.exhale.constants.PermanentShuffleKey
import com.ozyern.exhale.constants.PersistentQueueKey
import com.ozyern.exhale.constants.PlayerStreamClient
import com.ozyern.exhale.constants.PlayerStreamClientKey
import com.ozyern.exhale.constants.PlayerVolumeKey
import com.ozyern.exhale.constants.RepeatModeKey
import com.ozyern.exhale.constants.SkipSilenceKey
import com.ozyern.exhale.constants.PreferLocalLosslessKey
import com.ozyern.exhale.constants.SpatialAudioKey
import com.ozyern.exhale.constants.SoundBalanceKey
import com.ozyern.exhale.constants.OutputFloatKey
import com.ozyern.exhale.constants.PreferMusicOnlyKey
import com.ozyern.exhale.constants.PreferUsbDacKey
import com.ozyern.exhale.constants.SoundEqBandsKey
import com.ozyern.exhale.constants.SoundEqEnabledKey
import com.ozyern.exhale.constants.SoundEqFocusedKey
import com.ozyern.exhale.constants.SoundEqModeKey
import com.ozyern.exhale.constants.SoundEqToneXKey
import com.ozyern.exhale.constants.SoundEqToneYKey
import com.ozyern.exhale.constants.MaxSongCacheSizeKey
import com.ozyern.exhale.constants.SmartTrimmerKey
import com.ozyern.exhale.constants.StopMusicOnTaskClearKey
import com.ozyern.exhale.constants.WakelockKey
import com.ozyern.exhale.constants.YtmSyncKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Event
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.db.entities.RelatedSongMap
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.db.entities.SongEntity
import com.ozyern.exhale.db.entities.ArtistEntity
import com.ozyern.exhale.db.entities.AlbumEntity
import com.ozyern.exhale.di.DownloadCache
import com.ozyern.exhale.di.PlayerCache
import com.ozyern.exhale.extensions.SilentHandler
import com.ozyern.exhale.extensions.collect
import com.ozyern.exhale.extensions.collectLatest
import com.ozyern.exhale.extensions.currentMetadata
import com.ozyern.exhale.extensions.directorySizeBytes
import com.ozyern.exhale.extensions.findNextMediaItemById
import com.ozyern.exhale.extensions.mediaItems
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.extensions.setOffloadEnabled
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.extensions.toPersistQueue
import com.ozyern.exhale.extensions.toQueue
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils
import com.ozyern.exhale.lyrics.LyricsHelper
import com.ozyern.exhale.models.PersistQueue
import com.ozyern.exhale.models.PersistPlayerState
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.EmptyQueue
import com.ozyern.exhale.playback.queues.Queue
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.playback.queues.filterExplicit
import com.ozyern.exhale.playback.queues.filterVideo
import com.ozyern.exhale.utils.CoilBitmapLoader
import com.ozyern.exhale.utils.DiscordRPC
import com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.YTPlayerUtils
import com.ozyern.exhale.utils.StreamClientUtils
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.enumPreference
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.getAsync
import com.ozyern.exhale.utils.getPresenceIntervalMillis
import com.ozyern.exhale.utils.reportException
import com.ozyern.exhale.utils.NetworkConnectivityObserver
import dagger.hilt.android.AndroidEntryPoint
import com.ozyern.exhale.ui.screens.settings.ListenBrainzManager
import com.ozyern.exhale.constants.ListenBrainzEnabledKey
import com.ozyern.exhale.constants.ListenBrainzTokenKey
import com.ozyern.exhale.lastfm.LastFM
import com.ozyern.exhale.constants.EnableLastFMScrobblingKey
import com.ozyern.exhale.constants.LastFMUseNowPlaying
import com.ozyern.exhale.constants.ScrobbleDelayPercentKey
import com.ozyern.exhale.constants.ScrobbleMinSongDurationKey
import com.ozyern.exhale.constants.ScrobbleDelaySecondsKey
import com.ozyern.exhale.constants.TogetherClientIdKey
import com.ozyern.exhale.widget.PlayerWidgetActions
import com.ozyern.exhale.widget.PlayerWidgetState
import com.ozyern.exhale.widget.PlayerWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var pauseOnDeviceMuteEnabled = false
    private var wasAutoPausedByDeviceMute = false
    private var hasAudioFocus = false
    private var autoStartOnBluetoothEnabled = false
    private var bluetoothReceiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakelockEnabled = false

    private var scopeJob = Job()
    private var scope = CoroutineScope(Dispatchers.Main + scopeJob)
    private var ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val binder = MusicBinder()
    private var hasBoundClients = false
    private var idleStopJob: Job? = null

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        com.ozyern.exhale.constants.AudioQuality.HIGHEST
    )
    private val preferredAudioCodec by enumPreference(
        this,
        AudioCodecKey,
        com.ozyern.exhale.constants.AudioCodec.AAC
    )
    private val preferredStreamClient by enumPreference(
        this,
        PlayerStreamClientKey,
        PlayerStreamClient.ANDROID_VR
    )
    private val playbackUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    /** Read on the loader thread for every stream, so kept here rather than asked of the datastore. */
    @Volatile
    private var preferLocalLossless = true

    /** See [PreferMusicOnlyKey]. */
    @Volatile
    private var preferMusicOnly = false

    /** A music video's id to the catalogue song played in its place (or itself, where there is none). */
    private val musicOnlyIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** See [com.ozyern.exhale.constants.JioSaavnUpgradeKey]. Read on the loader thread. */
    @Volatile
    private var jioSaavnUpgrade = true

    /** Queue songs' details, captured on the main thread so the loader can match them. */
    private val queueMetadata = ConcurrentHashMap<String, com.ozyern.exhale.models.MediaMetadata>()

    /** One JioSaavn lookup per song, shared by prefetch and playback. */
    private val saavnLookups = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<JioSaavn.Stream?>>()

    /**
     * Which file a song is playing from this session: a JioSaavn URL, or [YOUTUBE_SOURCE].
     * Decided at the song's first open and then kept, so a later range request can never splice
     * one file's bytes onto another's.
     */
    private val streamSource = ConcurrentHashMap<String, String>()

    /** What the audio track was actually opened with, for the output readout in settings. */
    val outputStatus = MutableStateFlow<OutputStatus?>(null)

    private var preferUsbDac = false
    private val usbDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) = applyUsbPreference()
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) = applyUsbPreference()
    }
    private var streamPrefetchJob: Job? = null
    @Volatile
    private var streamPrefetchMediaId: String? = null
    private val streamRecoveryState = ConcurrentHashMap<String, Pair<Int, Long>>()
    @Volatile
    private var pendingStreamRefreshValidationMediaId: String? = null
    @Volatile
    private var refreshValidatedPlayingMediaId: String? = null
    private val avoidStreamCodecs: Set<String> by lazy {
        if (deviceSupportsMimeType("audio/opus")) emptySet() else setOf("opus")
    }
    private val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamProxy)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                val clientParam = request.url.queryParameter("c")?.trim().orEmpty()

                val userAgent = StreamClientUtils.resolveUserAgent(clientParam)
                val originReferer = StreamClientUtils.resolveOriginReferer(clientParam)

                val builder = request.newBuilder().header("User-Agent", userAgent)
                originReferer.origin?.let { builder.header("Origin", it) }
                originReferer.referer?.let { builder.header("Referer", it) }

                chain.proceed(builder.build())
            }.build()
    }

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    private val persistentStateLock = Any()
    @Volatile
    private var suppressAutoPlayback = false
    private var lastPresenceToken: String? = null
    @Volatile
    private var lastLoginRecoveryPrompt: Pair<String, Long>? = null

    val currentMediaMetadata = MutableStateFlow<com.ozyern.exhale.models.MediaMetadata?>(null)
    val queueRestoreCompleted = MutableStateFlow(false)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }.flowOn(Dispatchers.IO)

    private val normalizeFactor = MutableStateFlow(1f)
    var playerVolume = MutableStateFlow(1f)
    private val audioFocusVolumeFactor = MutableStateFlow(1f)
    private val playbackFadeFactor = MutableStateFlow(1f)
    private val crossfadeDurationMs = MutableStateFlow(0)
    private val audioNormalizationEnabled = MutableStateFlow(true)
    private var crossfadeAudio: CrossfadeAudio? = null

    // Automix. Each player's audio chain carries its own transition filter: the main player's is
    // the song leaving, the overlap player's the song arriving.
    private val automixEnabled = MutableStateFlow(false)
    private val mainTransitionFilter = TransitionFilterProcessor()
    private val overlapTransitionFilter = TransitionFilterProcessor()
    private var mixAnalyzer: MixAnalyzer? = null
    private var lyricsPreloadManager: LyricsPreloadManager? = null

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == packageName
        }
    }

    private fun promptLoginRecovery(mediaId: String, targetUrl: String) {
        if (!isAppInForeground()) return

        val now = System.currentTimeMillis()
        val lastPrompt = lastLoginRecoveryPrompt
        if (lastPrompt?.first == mediaId && now - lastPrompt.second < 10000L) return
        lastLoginRecoveryPrompt = mediaId to now

        val deepLink = Uri.parse("Exhale://login?url=${Uri.encode(targetUrl)}")
        val intent = Intent(Intent.ACTION_VIEW, deepLink, this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            Timber.e(it, "Failed to open login recovery for %s", mediaId)
        }
    }

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false
    private var openedAudioSessionId: Int? = null
    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
    private val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
            ),
        )

    private var audioEffectsSessionId: Int? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var discordRpc: DiscordRPC? = null
    private var lastDiscordUpdateTime = 0L

    private var scrobbleManager: com.ozyern.exhale.utils.ScrobbleManager? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val automixLoading = MutableStateFlow(false)
    val automixError = MutableStateFlow<String?>(null)
    private var automixJob: Job? = null
    private var automixSeedMediaId: String? = null

    val autoAddedMediaIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    private var consecutivePlaybackErr = 0

    /** Makes each forced Live Space publication distinguishable. See [publishLiveLyrics]. */
    private var liveLyricsRevision = 0

    /** Timed lines for the current track, driving [startLiveLyricLineTicker]. */
    private var liveLyricLines: List<LyricsEntry> = emptyList()

    /** The line index last pushed to the session, so the ticker only writes on a change. */
    private var liveLyricLineIndex = -1

    private var lockLyricsReceiver: android.content.BroadcastReceiver? = null

    /** The ticker itself; one at a time, cancelled on every track change. */
    private var liveLyricTickerJob: Job? = null

    /**
     * Whether the current line is also written into the session's displayed subtitle. Cached off
     * the datastore so the ticker never has to suspend on a preference read per line.
     */
    private var liveLyricsOnMediaCard = true

    /** Cached [EnableLockScreenLyricsKey]; see [cardLineWouldFightDocument]. */
    private var liveLyricsDocument = true

    /** The subtitle currently published, so a repeat line is not republished. */
    private var liveLyricCardLine: String? = null

    val maxSafeGainFactor = 1.414f // +3 dB
    @Volatile
    private var hasCalledStartForeground = false

    val togetherSessionState = MutableStateFlow<com.ozyern.exhale.together.TogetherSessionState>(
        com.ozyern.exhale.together.TogetherSessionState.Idle,
    )
    private var togetherServer: com.ozyern.exhale.together.TogetherServer? = null
    private var togetherOnlineHost: com.ozyern.exhale.together.TogetherOnlineHost? = null
    private var togetherClient: com.ozyern.exhale.together.TogetherClient? = null
    private var togetherBroadcastJob: Job? = null
    private var togetherOnlineConnectJob: Job? = null
    private var togetherClientEventsJob: Job? = null
    private var togetherHeartbeatJob: Job? = null
    private var togetherClock: com.ozyern.exhale.together.TogetherClock? = null
    private var togetherSelfParticipantId: String? = null
    private var togetherLastAppliedQueueHash: String? = null
    private var togetherIsOnlineSession: Boolean = false
    @Volatile
    private var togetherApplyingRemote: Boolean = false
    @Volatile
    private var togetherSuppressEchoUntilElapsedMs: Long = 0L
    @Volatile
    private var togetherLastAppliedRoomStateSentAtElapsedMs: Long = 0L
    @Volatile
    private var togetherLastRemoteAppliedPlayWhenReady: Boolean? = null
    @Volatile
    private var togetherLastRemoteAppliedIndex: Int = -1
    @Volatile
    private var togetherLastSentControlAtElapsedMs: Long = 0L
    @Volatile
    private var togetherLastSentControlAction: com.ozyern.exhale.together.ControlAction? = null
    @Volatile
    private var togetherPendingGuestControl: TogetherPendingGuestControl? = null

    private fun isTogetherApplyingRemote(): Boolean = togetherApplyingRemote
    private val togetherHostId: String = "host"
    private var lastTogetherNoticeAtElapsedMs: Long = 0L
    private var lastTogetherNoticeKey: String? = null

    private data class TogetherPendingGuestControl(
        val desiredIsPlaying: Boolean? = null,
        val desiredIndex: Int? = null,
        val desiredTrackId: String? = null,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
    )

    private fun showTogetherNotice(message: String, key: String? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val normalizedKey = key ?: message
        if (normalizedKey == lastTogetherNoticeKey && now - lastTogetherNoticeAtElapsedMs < 1200L) return
        lastTogetherNoticeKey = normalizedKey
        lastTogetherNoticeAtElapsedMs = now
        scope.launch(SilentHandler) {
            Toast.makeText(this@MusicService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun getOrCreateTogetherClientId(): String {
        val existing = dataStore.getAsync(TogetherClientIdKey)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[TogetherClientIdKey] = generated }
        return generated
    }

    private fun ensureStartedAsForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (hasCalledStartForeground) return

        val notification =
            try {
                val contentIntent =
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.exhale_monochrome)
                    .setContentTitle(getString(R.string.music_player))
                    .setContentText(getString(R.string.app_name))
                    .setContentIntent(contentIntent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            } catch (e: Exception) {
                reportException(e)
                return
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasCalledStartForeground = true
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun promoteToStartedService() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    private fun stopForegroundAndSelf() {
        cancelIdleStop()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
        hasCalledStartForeground = false
        stopSelf()
    }

    private fun scheduleStopIfIdle() {
        if (hasBoundClients) return
        val state = player.playbackState
        val keepAlive =
            player.isPlaying ||
                (player.playWhenReady && (state == Player.STATE_BUFFERING || state == Player.STATE_READY))
        if (keepAlive) {
            cancelIdleStop()
            return
        }
        val togetherIdle = togetherSessionState.value is com.ozyern.exhale.together.TogetherSessionState.Idle
        if (!togetherIdle) {
            cancelIdleStop()
            return
        }

        val delayMs =
            when (state) {
                Player.STATE_READY -> 5 * 60_000L
                Player.STATE_ENDED, Player.STATE_IDLE -> 30_000L
                else -> 60_000L
            }

        cancelIdleStop()
        idleStopJob =
            scope.launch {
                delay(delayMs)
                if (hasBoundClients) return@launch
                val currentState = player.playbackState
                val shouldKeep =
                    player.isPlaying ||
                        (player.playWhenReady && (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY))
                if (shouldKeep) return@launch
                if (togetherSessionState.value !is com.ozyern.exhale.together.TogetherSessionState.Idle) return@launch
                stopForegroundAndSelf()
            }
    }

    override fun onCreate() {
        super.onCreate()
        ensureScopesActive()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.music_player),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        } catch (e: Exception) {
            reportException(e)
        }

        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setTrackSelector(createTrackSelector())
                .setLoadControl(createLoadControl())
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
                .apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this)
                    addListener(sleepTimer)
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                    setOffloadEnabled(false)
                }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(usbDeviceCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioTrackInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig,
            ) {
                outputStatus.value = OutputStatus(
                    sampleRateHz = audioTrackConfig.sampleRate,
                    encoding = audioTrackConfig.encoding,
                    offload = audioTrackConfig.offload,
                )
            }
        })
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Exhale:Playback")
            .also { it.setReferenceCounted(false) }
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
            openOutputSwitcher = ::openOutputSwitcher
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, sessionPlayer(), mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            ).apply {
                setSmallIcon(R.drawable.exhale)
            }
        )
        
        updateNotification()
        player.repeatMode = REPEAT_MODE_OFF

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())
        scope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val repeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF
            val volume = (prefs[PlayerVolumeKey] ?: 1f).coerceIn(0f, 1f)
            val offload = prefs[AudioOffload] ?: false
            withContext(Dispatchers.Main) {
                player.repeatMode = repeatMode
                playerVolume.value = volume
                updateAudioOffload(offload)
            }
        }

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    waitingForNetworkConnection.value = false
                    if (player.currentMediaItem != null && player.playWhenReady &&
                        player.playbackState == Player.STATE_IDLE
                    ) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }

        combine(playerVolume, normalizeFactor, audioFocusVolumeFactor, playbackFadeFactor) { playerVolume, normalizeFactor, audioFocusVolumeFactor, playbackFadeFactor ->
            playerVolume * normalizeFactor * audioFocusVolumeFactor * playbackFadeFactor
        }.collectLatest(scope) { finalVolume ->
            player.volume = finalVolume
        }

        playerVolume.debounce(1000).collect(ioScope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(300).collect(scope) { song ->
            updateNotification()
            if (song != null && player.playWhenReady && player.playbackState == Player.STATE_READY) {
                ensurePresenceManager()
            } else {
                discordRpc?.closeRPC()
            }
        }

        // Lyrics pre-fetch: fire the moment the current song changes — NOT gated on the
        // lyrics panel being open. By the time the user taps the lyrics tab the text is
        // already in Room + the in-memory LRU, so the panel renders instantly instead of
        // showing the shimmer through a full multi-provider network round-trip.
        currentMediaMetadata
            .distinctUntilChangedBy { it?.id }
            .collectLatest(ioScope) { mediaMetadata ->
                if (mediaMetadata == null) return@collectLatest

                // Saved lyrics a source censored beyond repair are looked up again.
                val cached = database.lyrics(mediaMetadata.id).first()?.takeUnless {
                    com.ozyern.exhale.lyrics.Uncensor.isCensored(com.ozyern.exhale.lyrics.Uncensor.restore(it.lyrics))
                }
                val lyrics = if (cached == null) {
                    lyricsHelper.getLyrics(mediaMetadata).also { fetched ->
                        database.query {
                            upsert(
                                LyricsEntity(
                                    id = mediaMetadata.id,
                                    lyrics = fetched,
                                ),
                            )
                        }
                    }
                } else {
                    cached.lyrics
                }

                publishLiveLyrics(mediaMetadata, lyrics)
            }

        // Cached so the per-line ticker never suspends on a datastore read. Turning it off
        // restores the real artist immediately rather than at the next track change.
        dataStore.data
            .map { it[LyricsOnMediaCardKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                liveLyricsOnMediaCard = enabled
                if (!enabled) publishMediaCardLine(null)
            }

        // Exhale's own lyrics over the lock screen (see LockScreenLyrics).
        lockLyricsReceiver = LockScreenLyrics.register(this, player)
        dataStore.data
            .map { it[com.ozyern.exhale.constants.LockScreenLyricsOverlayKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) { LockScreenLyrics.enabled = it }

        dataStore.data
            .map { it[EnableLockScreenLyricsKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                liveLyricsDocument = enabled
                // Turning the document channel on hands the lock screen back to it, so the
                // subtitle goes back to being the artist.
                if (enabled) publishMediaCardLine(null)
            }

        dataStore.data
            .map { it[com.ozyern.exhale.constants.JioSaavnUpgradeKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                jioSaavnUpgrade = enabled
                if (!enabled) {
                    saavnLookups.clear()
                    streamSource.clear()
                }
            }

        dataStore.data
            .map { it[PreferMusicOnlyKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                preferMusicOnly = it
                musicOnlyIds.clear()
            }

        dataStore.data
            .map { it[PreferUsbDacKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                preferUsbDac = it
                applyUsbPreference()
            }

        dataStore.data
            .map { it[PreferLocalLosslessKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) {
                preferLocalLossless = it
                com.ozyern.exhale.utils.LocalLossless.invalidate()
            }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        // Cavern spatial-audio upscaler toggle. The flag is a @Volatile static
        // read once per audio buffer, so flipping it is instant and never
        // requires rebuilding the player/sink.
        dataStore.data
            // Off by default where the phone already spatialises (OnePlus/OPPO's OReality);
            // see DeviceAudio.
            .map { it[SpatialAudioKey] ?: com.ozyern.exhale.utils.DeviceAudio.defaultSpatialAudio }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                CavernSpatialAudioProcessor.globalEnabled = enabled
            }

        dataStore.data
            .map { settings ->
                settings[SpatialAudioProfileKey]?.let {
                    runCatching { SpatialAudioProfile.valueOf(it) }.getOrNull()
                } ?: SpatialAudioProfile.CINEMA
            }
            .distinctUntilChanged()
            .collectLatest(scope) { profile ->
                when (profile) {
                    SpatialAudioProfile.NATURAL -> CavernSpatialAudioProcessor.applyProfile(1.15f, 0.30f, 0.06f)
                    SpatialAudioProfile.WIDE -> CavernSpatialAudioProcessor.applyProfile(1.35f, 0.38f, 0.12f)
                    SpatialAudioProfile.CINEMA -> CavernSpatialAudioProcessor.applyProfile(1.60f, 0.46f, 0.20f)
                }
            }

        // Exhale's equaliser: the processor reads a static target, so a change is heard at the
        // next audio buffer, gliding there without a click, and never rebuilds the player.
        dataStore.data
            .map { prefs ->
                val enabled = prefs[SoundEqEnabledKey] ?: false
                val mode = prefs[SoundEqModeKey]?.let { runCatching { SoundEqMode.valueOf(it) }.getOrNull() }
                    ?: SoundEqMode.DYNAMIC
                val curve = when (mode) {
                    SoundEqMode.DYNAMIC -> toneCurve(
                        prefs[SoundEqToneXKey] ?: 0,
                        prefs[SoundEqToneYKey] ?: 0,
                        prefs[SoundEqFocusedKey] ?: false,
                    )
                    SoundEqMode.MANUAL -> manualCurve(decodeSoundBands(prefs[SoundEqBandsKey]))
                }
                Triple(enabled, curve, prefs[SoundBalanceKey] ?: 0f)
            }
            .distinctUntilChanged { a, b ->
                a.first == b.first && a.third == b.third &&
                    a.second.gainsDb.contentEquals(b.second.gainsDb) && a.second.qs.contentEquals(b.second.qs)
            }
            .collectLatest(scope) { (enabled, curve, balance) ->
                ToneEqualizerProcessor.setTuning(enabled, curve, balance)
            }

        dataStore.data
            .map { it[PauseOnDeviceMuteKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                pauseOnDeviceMuteEnabled = enabled
                if (!enabled) {
                    wasAutoPausedByDeviceMute = false
                } else {
                    handleDeviceMuteStateChanged()
                }
            }

        dataStore.data
            .map { it[AutoStartOnBluetoothKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                autoStartOnBluetoothEnabled = enabled
                if (enabled) {
                    registerBluetoothReceiver()
                } else {
                    unregisterBluetoothReceiver()
                }
            }

        dataStore.data
            .map { it[AudioOffload] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                updateAudioOffload(enabled)
                if (enabled) {
                    val skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
                    if (skipSilenceEnabled) {
                        dataStore.edit { it[SkipSilenceKey] = false }
                        player.skipSilenceEnabled = false
                    }
                    val crossfadeSeconds = dataStore.get(AudioCrossfadeDurationKey, 0)
                    if (crossfadeSeconds != 0) {
                        dataStore.edit { it[AudioCrossfadeDurationKey] = 0 }
                    }
                    // Offloaded audio skips the processors Automix blends through.
                    if (dataStore.get(AutomixEnabledKey, false)) {
                        dataStore.edit { it[AutomixEnabledKey] = false }
                    }
                }
            }
        
        dataStore.data
            .map { (it[AudioCrossfadeDurationKey] ?: 0) * 1000 }
            .distinctUntilChanged()
            .collectLatest(scope) {
                crossfadeDurationMs.value = it
            }

        mixAnalyzer = MixAnalyzer(this) { id, abort -> automixSource(id, abort) }
        // Music-video lyrics: how far into the video its song starts, measured from the two
        // recordings. Nothing to move when music-only is already playing the song's own audio.
        com.ozyern.exhale.lyrics.VideoLyricsAlignment.aligner = { videoId, songId ->
            if (preferMusicOnly && musicOnlyIds[videoId] == songId) {
                0L
            } else {
                withContext(Dispatchers.IO) {
                    val video = automixSource(videoId, { false }, followMusicOnly = false)
                    val song = automixSource(songId, { false }, followMusicOnly = false)
                    if (video == null || song == null) {
                        runCatching { video?.close() }
                        runCatching { song?.close() }
                        null
                    } else {
                        runCatching {
                            com.ozyern.exhale.playback.automix.VersionAligner.offsetMs(video, song)
                        }.getOrNull()
                    }
                }
            }
        }
        dataStore.data
            .map { it[AutomixEnabledKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                automixEnabled.value = it
            }
        dataStore.data
            .map { prefs ->
                runCatching { AutomixPerformance.valueOf(prefs[AutomixPerformanceKey] ?: "") }
                    .getOrDefault(AutomixPerformance.BALANCED)
            }
            .distinctUntilChanged()
            .collectLatest(scope) {
                mixAnalyzer?.performance = it
            }

        dataStore.data
            .map { it[WakelockKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                wakelockEnabled = enabled
                updateWakeLock()
            }

        crossfadeAudio =
            CrossfadeAudio(
                player = player,
                database = database,
                crossfadeDurationMs = crossfadeDurationMs,
                playbackFadeFactor = playbackFadeFactor,
                playerVolume = playerVolume,
                audioFocusVolumeFactor = audioFocusVolumeFactor,
                audioNormalizationEnabled = audioNormalizationEnabled,
                maxSafeGainFactor = maxSafeGainFactor,
                overlapPlayerFactory = {
                    ExoPlayer
                        .Builder(this)
                        .setMediaSourceFactory(createMediaSourceFactory())
                        .setRenderersFactory(createRenderersFactory(overlapTransitionFilter))
                        .setTrackSelector(createTrackSelector())
                        .setHandleAudioBecomingNoisy(false)
                        .setWakeMode(C.WAKE_MODE_NETWORK)
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                            false,
                        ).setSeekBackIncrementMs(5000)
                        .setSeekForwardIncrementMs(5000)
                        .build()
                },
                automixEnabled = automixEnabled,
                analyzer = mixAnalyzer,
                filters = object : TransitionFilters {
                    override fun incoming(lowPassHz: Float, highPassHz: Float) =
                        overlapTransitionFilter.setCutoffs(lowPassHz, highPassHz)

                    override fun outgoing(lowPassHz: Float, highPassHz: Float) =
                        mainTransitionFilter.setCutoffs(lowPassHz, highPassHz)
                },
                // Every listener in a session starts the next song at their own moment, so a
                // transition planned on one phone would land differently on the others.
                automixAllowed = {
                    togetherSessionState.value is com.ozyern.exhale.together.TogetherSessionState.Idle
                },
                onCrossfadeStart = { mediaItem ->
                    val metadata = mediaItem.metadata
                    currentMediaMetadata.value = metadata
                    // immediate update when media item transitions to avoid stale presence
                    scope.launch {
                        try {
                            val token = dataStore.get(DiscordTokenKey, "")
                            if (token.isNotBlank() && DiscordPresenceManager.isRunning()) {
                                val mediaId = mediaItem.mediaId
                                val song = if (mediaId != null) withContext(Dispatchers.IO) { database.song(mediaId).first() } else null
                                val finalSong = song ?: metadata?.let { createTransientSongFromMedia(it) }

                                run {
                                    DiscordPresenceManager.updateNow(
                                        context = this@MusicService,
                                        token = token,
                                        song = finalSong,
                                        positionMs = 0L,
                                        isPaused = false
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            ).also { it.start(scope) }

        // Initialize lyrics pre-load manager — shares the service's LyricsHelper so
        // prefetched lyrics land in the same in-memory cache the UI reads from.
        lyricsPreloadManager = LyricsPreloadManager(
            context = this,
            database = database,
            networkConnectivity = connectivityObserver,
            lyricsHelper = lyricsHelper,
        )

        dataStore.data
            .map(::readEqSettingsFromPrefs)
            .distinctUntilChanged()
            .collectLatest(scope) { settings ->
                desiredEqSettings.value = settings
                applyEqSettingsToEffects(settings)
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            audioNormalizationEnabled.value = normalizeAudio
            Timber.tag("AudioNormalization").d("Audio normalization enabled: $normalizeAudio")
            Timber.tag("AudioNormalization").d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")
            
            normalizeFactor.value =
                if (normalizeAudio) {
                    // Use loudnessDb if available, otherwise fall back to perceptualLoudnessDb
                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb
                    
                    if (loudness != null) {
                        val loudnessDb = loudness.toFloat()
                        var factor = 10f.pow(-loudnessDb / 20)
                        
                        Timber.tag("AudioNormalization").d("Calculated raw normalization factor: $factor (from loudness: $loudnessDb)")
                        
                        if (factor > 1f) {
                            factor = min(factor, maxSafeGainFactor)
                            Timber.tag("AudioNormalization").d("Factor capped at maxSafeGainFactor: $factor")
                        }
                        
                        Timber.tag("AudioNormalization").i("Applying normalization factor: $factor")
                        factor
                    } else {
                        Timber.tag("AudioNormalization").w("Normalization enabled but no loudness data available - no normalization applied")
                        1f
                    }
                } else {
                    Timber.tag("AudioNormalization").d("Normalization disabled - using factor 1.0")
                    1f
                }
        }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest(scope) { (key, enabled) ->
                val newRpc =
                    withContext(Dispatchers.IO) {
                        if (!key.isNullOrBlank() && enabled) {
                            runCatching { DiscordRPC(this@MusicService, key) }
                                .onFailure { Timber.tag("MusicService").e(it, "failed to create DiscordRPC client") }
                                .getOrNull()
                        } else {
                            null
                        }
                    }

                try {
                    if (discordRpc?.isRpcRunning() == true) {
                        withContext(Dispatchers.IO) { discordRpc?.closeRPC() }
                    }
                } catch (_: Exception) {}
                discordRpc = newRpc

                if (discordRpc != null) {
                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                        currentSong.value?.let {
                            ensurePresenceManager()
                        }
                    }
                } else {
                    try { DiscordPresenceManager.stop() } catch (_: Exception) {}
                }
            }

        dataStore.data
            .map { prefs ->
                (prefs[SmartTrimmerKey] ?: false) to (prefs[MaxSongCacheSizeKey] ?: 1024)
            }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest(ioScope) { (enabled, maxSongCacheSizeMb) ->
                if (!enabled) return@collectLatest
                if (maxSongCacheSizeMb <= 0 || maxSongCacheSizeMb == -1) return@collectLatest
                val bytesPerMb = 1024L * 1024L
                val safeSizeMb = maxSongCacheSizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
                val limitBytes = safeSizeMb * bytesPerMb
                trimPlayerCacheToBytes(limitBytes)
            }

        // Last.fm ScrobbleManager setup
        dataStore.data
            .map { it[EnableLastFMScrobblingKey] ?: false }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                if (enabled && scrobbleManager == null) {
                    val delayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration = dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)
                    
                    scrobbleManager = com.ozyern.exhale.utils.ScrobbleManager(
                        ioScope,
                        minSongDuration = minSongDuration,
                        scrobbleDelayPercent = delayPercent,
                        scrobbleDelaySeconds = delaySeconds
                    )
                    scrobbleManager?.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                } else if (!enabled && scrobbleManager != null) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                scrobbleManager?.useNowPlaying = it
            }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS
                )
            }
            .distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }

        scope.launch(Dispatchers.IO) {
            if (dataStore.get(PersistentQueueKey, true)) {
                readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
                    ?.let { persistedQueue ->
                    restorePersistentQueue(persistedQueue)
                }
                readPersistentObject<PersistQueue>(PERSISTENT_AUTOMIX_FILE)
                    ?.let { persistedAutomix ->
                    val items = persistedAutomix.items.map { it.toMediaItem() }
                    withContext(Dispatchers.Main) {
                        automixItems.value = items
                        automixSeedMediaId = player.currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                    }
                }
                
                readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)
                    ?.let { playerState ->
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        player.repeatMode = playerState.repeatMode
                        player.shuffleModeEnabled = playerState.shuffleModeEnabled
                        playerVolume.value = playerState.volume
                        
                        if (player.mediaItemCount > 0) {
                            val index =
                                if (playerState.currentMediaItemIndex in 0 until player.mediaItemCount) {
                                    playerState.currentMediaItemIndex
                                } else {
                                    player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                                }
                            player.seekTo(index, playerState.currentPosition)
                        }
                        
                        currentMediaMetadata.value = player.currentMetadata
                        updateNotification()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                queueRestoreCompleted.value = true
            }
            // Last, once the restored queue is on the song it will resume: that is the one warmed.
            // Runs with or without a persisted queue — the player-code half helps any first song.
            warmUpPlayback()
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                val interval = if (player.isPlaying) 10.seconds else 30.seconds
                delay(interval)
                val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
                if (shouldSave) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun ensureScopesActive() {
        if (!scopeJob.isActive) {
            scopeJob = Job()
        }
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
        if (!ioScope.isActive) {
            ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
    }

    private suspend fun restorePersistentQueue(persistedQueue: PersistQueue) {
        val restoredQueue = persistedQueue.toQueue()
        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideo = dataStore.get(HideVideoKey, false)
        val initialStatus =
            restoredQueue
                .getInitialStatus()
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)

        withContext(Dispatchers.Main) {
            currentQueue = restoredQueue
            queueTitle = initialStatus.title

            val items = initialStatus.items
            if (items.isEmpty()) {
                return@withContext
            }

            val fullIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
            val windowStart = (fullIndex - 20).coerceAtLeast(0)
            val windowEnd = (fullIndex + 50).coerceAtMost(items.size)

            val initialChunk = items.subList(windowStart, windowEnd)
            val relativeIndex = (fullIndex - windowStart).coerceIn(0, initialChunk.lastIndex)

            player.setMediaItems(
                initialChunk,
                relativeIndex,
                initialStatus.position,
            )
            player.prepare()
            player.playWhenReady = false
            currentMediaMetadata.value = player.currentMetadata
            updateNotification()

            if (items.size > initialChunk.size) {
                scope.launch(SilentHandler) {
                    delay(2000)
                    if (!isActive || player.mediaItemCount == 0) return@launch
                    if (windowStart > 0) {
                        player.addMediaItems(0, items.subList(0, windowStart))
                    }
                    if (windowEnd < items.size) {
                        player.addMediaItems(items.subList(windowEnd, items.size))
                    }
                }
            }
        }
    }

    private fun ensurePresenceManager() {
        if (DiscordPresenceManager.isRunning() && lastPresenceToken != null) return

        // Launch in scope to avoid blocking
        scope.launch {
            // Don't start if Discord RPC is disabled in settings
            if (!dataStore.get(EnableDiscordRPCKey, true)) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("Discord RPC disabled → stopping presence manager")
                    try { DiscordPresenceManager.stop() } catch (_: Exception) {}
                    lastPresenceToken = null
                }
                return@launch
            }

            val key: String = dataStore.get(DiscordTokenKey, "")
            if (key.isNullOrBlank()) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("No Discord token → stopping presence manager")
                    try { DiscordPresenceManager.stop() } catch (_: Exception) {}
                    lastPresenceToken = null
                }
                return@launch
            }

            if (DiscordPresenceManager.isRunning() && lastPresenceToken == key) {
                // try {
                //     if (DiscordPresenceManager.restart()) {
                //         Timber.tag("MusicService").d("Presence manager restarted with same token")
                //     }
                // } catch (ex: Exception) {
                //     Timber.tag("MusicService").e(ex, "Failed to restart presence manager")
                // }
                return@launch
            }

            try {
                DiscordPresenceManager.stop()
                DiscordPresenceManager.start(
                    context = this@MusicService,
                    token = key,
                    songProvider = { player.currentMetadata?.let { createTransientSongFromMedia(it) } ?: currentSong.value },
                    positionProvider = { player.currentPosition },
                    isPausedProvider = { !player.isPlaying },
                    intervalProvider = { getPresenceIntervalMillis(this@MusicService) }
                )
                Timber.tag("MusicService").d("Presence manager started with token=$key")
                lastPresenceToken = key
            } catch (ex: Exception) {
                Timber.tag("MusicService").e(ex, "Failed to start presence manager")
            }
        }
    }

    private var lastPlayingNowId: String? = null
    private var lastPlayingNowAt = 0L

    /**
     * ListenBrainz's "playing now", at most once a song per quarter minute. Every play, pause, seek and
     * skip lands here, and the old gate that spread these out also sat in front of Discord — where it
     * silently dropped a skip made inside the window, leaving the wrong song showing.
     */
    private fun canSubmitPlayingNow(song: Song?): Boolean {
        val id = song?.song?.id
        val now = System.currentTimeMillis()
        synchronized(this) {
            val fresh = id != lastPlayingNowId || now - lastPlayingNowAt > 15_000L
            if (fresh) {
                lastPlayingNowId = id
                lastPlayingNowAt = now
            }
            return fresh
        }
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                audioFocusVolumeFactor.value = 1f
                wasPlayingBeforeAudioFocusLoss = false

                if (player.isPlaying) {
                    player.pause()
                }

                abandonAudioFocus()

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                audioFocusVolumeFactor.value = 1f
                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                if (player.isPlaying) {
                    player.pause()
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {

                hasAudioFocus = false

                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                audioFocusVolumeFactor.value = 0.2f

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {

                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }
        
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
    
        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun isDeviceMutedNow(): Boolean {
        return player.isDeviceMuted || player.deviceVolume <= 0
    }

    private fun isTogetherGuestSession(): Boolean {
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        return joined?.role is com.ozyern.exhale.together.TogetherRole.Guest
    }

    private fun handleDeviceMuteStateChanged() {
        if (!pauseOnDeviceMuteEnabled || isTogetherGuestSession()) {
            wasAutoPausedByDeviceMute = false
            return
        }

        if (isDeviceMutedNow()) {
            val canPauseNow =
                player.currentMediaItem != null &&
                    player.playWhenReady &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED

            if (canPauseNow) {
                player.pause()
                wasAutoPausedByDeviceMute = true
            }
            return
        }

        if (!wasAutoPausedByDeviceMute) return

        wasAutoPausedByDeviceMute = false
        val canResumeNow =
            player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
        if (canResumeNow) {
            player.play()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            if (!autoStartOnBluetoothEnabled) return

            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            val isAudioDevice = try {
                val majorClass = device.bluetoothClass?.majorDeviceClass
                majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    majorClass == BluetoothClass.Device.Major.WEARABLE
            } catch (_: SecurityException) {
                true
            }

            if (!isAudioDevice) return

            scope.launch {
                delay(1500)
                handleBluetoothAutoStart()
            }
        }
    }

    private fun handleBluetoothAutoStart() {
        if (isTogetherGuestSession()) return

        if (player.currentMediaItem != null &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        ) {
            if (!player.playWhenReady) {
                player.play()
            }
            return
        }

        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    @Suppress("DEPRECATION")
    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        bluetoothReceiverRegistered = true
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        bluetoothReceiverRegistered = false
    }

    /** Moves to the next queue item that plays offline. False when nothing ahead of here does. */
    private fun skipToNextPlayableOffline(): Boolean {
        val count = player.mediaItemCount
        val from = player.currentMediaItemIndex
        if (count <= 0 || from < 0) return false
        val shuffled = player.shuffleModeEnabled
        var index = from
        repeat(count - 1) {
            index = if (shuffled) {
                player.currentTimeline.getNextWindowIndex(index, Player.REPEAT_MODE_ALL, true)
            } else {
                (index + 1) % count
            }
            if (index == C.INDEX_UNSET || index == from) return false
            val id = runCatching { player.getMediaItemAt(index).mediaId }.getOrNull() ?: return@repeat
            if (isPlayableOffline(id)) {
                player.seekTo(index, 0L)
                player.prepare()
                player.playWhenReady = true
                return true
            }
        }
        return false
    }

    private fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    /**
     * Hands the current track's timed lyrics to OxygenOS / ColorOS Live Space.
     *
     * The ROM's reader hooks `MediaSession#setMetadata`, so it is not enough to hang the payload
     * on our [MediaItem] — a metadata push carrying it has to actually reach the platform session.
     * Under Media3 that is the whole difficulty, because two separate layers drop a change that
     * exists only in `mediaMetadata.extras`:
     *
     *  - `MediaMetadata.equals` compares `extras` by null-ness alone — a `Bundle` has no useful
     *    equality — so two metadata objects differing only in a lyric payload are "equal".
     *    `ExoPlayerImpl` gates `onMediaMetadataChanged` on exactly that comparison. Every one of
     *    our items already carries extras (`ExtraIsMusicVideo`), so the event never fired and the
     *    first version of this code published into a void.
     *  - `MediaSessionLegacyStub` gates its `setMetadata` call on the same comparison, plus the
     *    media id, the request URI and the duration.
     *
     * `requestMetadata.mediaUri` is the one field both layers compare that means nothing to this
     * app — we route playback off `mediaId` and never read it — so [OplusLiveLyrics.signalUri]
     * puts a per-publication value there. The item's timeline entry then genuinely differs, the
     * timeline change reaches the stub, the stub's own guard sees a new URI, and it pushes the
     * live metadata, extras and all.
     *
     * Two rules from the OPlus protocol shape the rest:
     *
     *  - **Attach before publishing where possible.** The spec is explicit that patching extras
     *    onto a track that has already been announced risks the ROM debouncing the second update,
     *    so [preAttachQueueLyrics] fills in the payload for upcoming items while they are still
     *    off screen. When the transition happens the very first push already carries the lyrics
     *    and nothing has to be forced.
     *  - **At most two publications per track.** Forcing is the fallback for lyrics that resolve
     *    after playback started. Position updates, pause/resume and notification refreshes must
     *    never rewrite the payload, so this hangs off the collector that resolves lyrics — which
     *    fires once per media id — and never off ticking state.
     */
    private suspend fun publishLiveLyrics(
        metadata: com.ozyern.exhale.models.MediaMetadata,
        lyrics: String?,
    ) {
        // Read both switches here, not one. The line ticker below feeds the media card as well
        // as the Live Space extras, so returning early on the Live Space switch alone used to
        // take the media card down with it - a user who turned off a feature that does nothing on
        // their phone silently lost the one that works on it.
        val preferences = dataStore.data.first()
        val liveSpaceEnabled = preferences[EnableLockScreenLyricsKey] ?: true
        val mediaCardEnabled = preferences[LyricsOnMediaCardKey] ?: true
        val lockScreenEnabled = preferences[com.ozyern.exhale.constants.LockScreenLyricsOverlayKey] ?: true
        if (!liveSpaceEnabled && !mediaCardEnabled && !lockScreenEnabled) return

        val usable = lyrics?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND }
        // Normalise once. `toLrc` is the TTML flattener for word-synced providers, and both the
        // document below and the line ticker need its output — running it twice to get the same
        // string back would be a full re-parse of the lyrics on every track change.
        val lrc = OplusLiveLyrics.toLrc(usable)
        val payload = lrc?.let {
            OplusLiveLyrics.buildPayloadFromLrc(
                songId = metadata.id,
                songName = metadata.title,
                artist = metadata.artists.joinToString { it.name },
                lrc = it,
                rawLyrics = usable,
                album = metadata.album?.title,
                durationMs = metadata.duration.takeIf { d -> d > 0 }?.times(1000L) ?: 0L,
            )
        }

        startLiveLyricLineTicker(lrc)

        // Everything past here is the OPlus document channel.
        if (!liveSpaceEnabled) return

        // Second delivery channel, independent of the media items.
        //
        // Track metadata reaches SystemUI through `MediaSession#setMetadata`, and getting a
        // lyrics-only change past Media3 to that call is the elaborate dance documented above.
        // Session extras are a separate broadcast that Media3 forwards verbatim and immediately,
        // with none of the equality gating, so a consumer reading from there sees the payload the
        // moment it resolves. The Live Alert capsule is already served by the metadata path; this
        // exists for the lock-screen surface, which on ColorOS 16.1 is not picking that path up.
        withContext(Dispatchers.Main) { publishSessionLyricExtras(payload) }

        if (payload == null) {
            Log.i(
                LiveLyricsTag,
                "${metadata.id}: no timed lyrics, leaving the lock screen on its own fallback",
            )
        } else if (withContext(Dispatchers.Main) { currentItemCarries(metadata.id, payload) }) {
            Log.i(LiveLyricsTag, "${metadata.id}: published with the track, nothing to force")
        } else {
            withContext(Dispatchers.Main) { forceLyricInfo(metadata.id, payload) }
            // One retry, and only if the first one did not stick.
            //
            // The integration spec is emphatic that this must be at most one republication, and
            // conditional: OPlus debounces metadata updates that arrive close together, so a
            // second unconditional write is not insurance, it is the thing most likely to get the
            // first one discarded. Re-reading the item is the whole point - if `lyricInfo` is
            // already on it, the publication crossed and there is nothing to do.
            delay(LiveLyricsRepublishDelayMs)
            val stuck = withContext(Dispatchers.Main) { currentItemCarries(metadata.id, payload) }
            if (!stuck) {
                withContext(Dispatchers.Main) { forceLyricInfo(metadata.id, payload) }
            }
        }

        withContext(Dispatchers.Main) { logLiveLyricsDelivery(metadata.id) }

        preAttachQueueLyrics()
    }

    /**
     * Feeds the **current line** to the session extras as playback advances.
     *
     * The document channel hands a consumer a whole LRC and leaves the timing to it. A consumer
     * built the other way round — one that renders whichever line it was last handed — gets
     * nothing from a document, which is a plausible shape for the split we are seeing, where the
     * Live Alert capsule scrolls correctly and the lock screen shows its own "no lyrics" state.
     * This is the second shape, published alongside the first rather than instead of it.
     *
     * Deliberately cheap. It polls rather than driving off a position flow because the session
     * only needs line granularity: a 300ms tick is four decimal places finer than the shortest
     * line anyone sings, and a tick that finds no change writes nothing at all. It also only runs
     * while something is actually playing, so a paused or idle service is not holding a loop open.
     */
    private suspend fun startLiveLyricLineTicker(lrc: String?) {
        // Parse on whatever background thread got us here — this is a full pass over the lyric
        // text — then hand the result over on main. Swapping the fields from off-main while the
        // previous ticker is still winding down would let it read the new track's lines against
        // the old track's index for a tick.
        val parsed = lrc?.let {
            runCatching { LyricsUtils.parseLyrics(it) }.getOrNull().orEmpty()
        }.orEmpty()

        withContext(Dispatchers.Main) {
            liveLyricTickerJob?.cancel()
            liveLyricLineIndex = -1
            liveLyricLines = parsed
            LockScreenLyrics.publishLines(parsed)

            if (parsed.isEmpty()) {
                // Clear, so a previous track's words can never sit under a new one.
                publishSessionCurrentLine(null, 0L)
                publishMediaCardLine(null)
                return@withContext
            }

            liveLyricTickerJob = launchLiveLyricTicker()
        }
    }

    /**
     * The ticker. Main thread: it reads the player directly.
     *
     * ### It sleeps until the next line, not for a fixed interval
     *
     * The old loop woke every 300ms and asked `findCurrentLineIndex` for the line at
     * `position + 300ms`. Both numbers were wrong in the same direction and neither cancelled the
     * other: the 300ms lead published each line up to a third of a second *early*, and the 300ms
     * poll could then discover the change up to a third of a second *late*. Net error anywhere in
     * ±300ms, varying per line, on a surface whose entire job is to agree with what you can hear.
     * It also woke 200 times a minute to conclude nothing had changed.
     *
     * Now the delay is derived from the gap to the next line: far from a boundary it sleeps the
     * full [LiveLyricsTickMs], and as the boundary approaches it shortens until it is landing
     * within [LiveLyricsMinTickMs] of the real transition. Fewer wakeups on average *and* an order
     * of magnitude better placement, because those were never a trade-off — they were both
     * symptoms of a loop that did not know what it was waiting for.
     *
     * Re-derived from `player.currentPosition` every pass rather than accumulated, so a seek, a
     * pause or a track change is absorbed on the next tick instead of leaving a stale timer
     * pointed at a position that no longer exists. Playback speed divides the wait for the same
     * reason: at 1.5x the next line arrives in two thirds of the wall-clock time.
     *
     * [LiveLyricsPublishLeadMs] is what remains of the old lead, and it is now doing an honest
     * job — it covers the real cost of getting a string onto the lock screen (a `replaceMediaItem`
     * plus the system's own metadata debounce), not the loop's own imprecision.
     */
    private fun launchLiveLyricTicker(): Job =
        scope.launch {
            while (isActive) {
                if (!player.isPlaying) {
                    delay(LiveLyricsIdleTickMs)
                    continue
                }

                val lines = liveLyricLines
                if (lines.isEmpty()) {
                    delay(LiveLyricsIdleTickMs)
                    continue
                }

                val position = player.currentPosition
                val index = LyricsUtils.findCurrentLineIndex(
                    lines,
                    position,
                    leadMs = LiveLyricsPublishLeadMs,
                )
                if (index >= 0 && index != liveLyricLineIndex) {
                    liveLyricLineIndex = index
                    val entry = lines[index]
                    // A blank entry is the gap between verses, and an LRC has plenty of them.
                    // Publishing "" leaves the lock screen showing an empty subtitle under the
                    // title, which looks like the feature broke; null restores the artist, which
                    // is what an instrumental passage should say.
                    val text = entry.text.takeIf { it.isNotBlank() }
                    publishSessionCurrentLine(text, entry.time)
                    publishMediaCardLine(text)
                }

                // Sleep until just before the next line rather than for a fixed interval.
                val nextTime = lines.getOrNull(index + 1)?.time
                val speed = player.playbackParameters.speed.takeIf { it > 0.05f } ?: 1f
                val untilNext =
                    if (nextTime == null) LiveLyricsTickMs
                    else ((nextTime - LiveLyricsPublishLeadMs - position) / speed).toLong()
                delay(untilNext.coerceIn(LiveLyricsMinTickMs, LiveLyricsTickMs))
            }
        }

    /**
     * Writes one lyric line onto the session extras under every key in
     * [OplusLiveLyrics.CURRENT_LINE_KEY_ALIASES], or clears them when [line] is null.
     */
    private fun publishSessionCurrentLine(line: String?, timeMs: Long) {
        // The same rule as the subtitle: no per-line churn where the native page is reading.
        if (line != null && cardLineWouldFightDocument) return
        val extras = Bundle(mediaSession.sessionExtras)
        OplusLiveLyrics.CURRENT_LINE_KEY_ALIASES.forEach { key ->
            if (line == null) extras.remove(key) else extras.putString(key, line)
        }
        OplusLiveLyrics.CURRENT_LINE_TIME_KEY_ALIASES.forEach { key ->
            if (line == null) extras.remove(key) else extras.putLong(key, timeMs)
        }
        mediaSession.sessionExtras = extras
    }

    /**
     * Writes [line] into the session's displayed subtitle, which is what the lock screen's media
     * card renders under the song title. Null restores the real artist.
     *
     * This is the path that works without a ROM module, and the only one that does, which is why
     * it is now on by default. Everything else here talks to OPlus SystemUI's private lyric
     * surface, and stock ColorOS gates that on a package whitelist a third-party player is never
     * added to - which is why the Live Alert capsule lights up and the lock screen stays on "no
     * lyrics" no matter how many keys we publish under. The media card is the platform control
     * every Android lock screen has drawn since 11. It has no whitelist. It renders whatever the
     * session's subtitle says.
     *
     * It does interfere with the `lyricInfo` document on phones where that document is actually
     * being read, because OPlus treats the subtitle as track identity - see
     * [LyricsOnMediaCardKey] for why that is a switch to flip rather than a gate to enforce.
     *
     * `replaceMediaItem` on the current item is the same mechanism [forceLyricInfo] already uses
     * and does not interrupt playback: the item keeps its URI and its `tag`, so the player sees a
     * metadata edit rather than a new track. At one write per line — a few seconds apart, and only
     * while something is playing — the cost is far below what a position ticker already does.
     *
     * Main thread.
     */
    private fun publishMediaCardLine(line: String?) {
        // A null is a *restore*, so it is allowed through even when the feature is off - that is
        // how switching the preference puts the real artist back straight away.
        if (line != null && !liveLyricsOnMediaCard) return
        if (line != null && cardLineWouldFightDocument) return
        if (line == liveLyricCardLine) return

        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        // The real artist comes from our own tag, which `withDisplayLine` preserves — so this
        // restores correctly however many times the subtitle has been overwritten.
        val artist = item.metadata?.artists?.joinToString { it.name }

        liveLyricCardLine = line
        with(OplusLiveLyrics) {
            player.replaceMediaItem(index, item.withDisplayLine(line, artist))
        }
    }

    /**
     * Whether writing a lyric line into the subtitle right now would cost more than it buys.
     *
     * Only one thing can own the lock screen. The document channel needs the session's metadata to
     * sit still - OPlus reads track identity out of the title/artist pair, and the integration
     * contract forbids rewriting metadata for lyric progress - while the card ticker rewrites the
     * subtitle every line. So when the document is actually being *rendered*, the ticker stands
     * down.
     *
     * "Actually being rendered" is the whole point, and it is narrower than "OPlus device". Stock
     * ColorOS does not draw a third-party player's lyrics at all: SystemUI takes the enable map for
     * that surface from OPlus's own remote config (`app_systemui_oplus_media_controller_config`,
     * read by `MediaActionPrioritySelectorImpl.getLyricEnable(pkg)`), and the packages in it are
     * OPlus's partner players. Ours is not one of them and cannot be added from the device side.
     * Measured on ColorOS 16.1: our payload lands on the platform session under all four keys and
     * the lock screen still shows none of it.
     *
     * The Bridge module is what changes that, by patching those SystemUI checks. So the rule is
     * simply: if the bridge is installed, the document wins and the ticker gets out of its way;
     * otherwise the subtitle is the only lyric anyone is going to see on that lock screen, and
     * standing down would mean showing nothing at all.
     */
    // ColorOS 16.1+ draws `lyricInfo` natively, Bridge or not (Live Lyrics Bridge 4.0 contract,
    // docs/PLAYER_INTEGRATION.md): SystemUI takes the timeline from the document and the progress
    // from PlaybackState, and metadata rewritten for lyric progress is what it names as the thing
    // that breaks it. The per-line subtitle was exactly such a rewrite, on every line, which is
    // why the native lyric page stayed empty. On ColorOS the document now owns the lock screen.
    // Only where something will actually draw the document: stock ColorOS admits lyricInfo from
    // its partner players alone, and the Live Lyrics Bridge is what admits the rest. Without the
    // Bridge the per-line subtitle is the only lyric the lock screen shows, so it stays on.
    private val cardLineWouldFightDocument: Boolean
        get() = liveLyricsDocument && com.ozyern.exhale.utils.DeviceAudio.isOplusDevice &&
            lockScreenLyricBridgeInstalled

    /**
     * Whether the ColorOS Live Lyrics Bridge is on this phone.
     *
     * Read once: installing an LSPosed module requires a reboot, so the answer cannot change
     * inside a playback session, and this is consulted on every lyric line.
     */
    private val lockScreenLyricBridgeInstalled: Boolean by lazy {
        runCatching {
            packageManager.getPackageInfo(OplusLiveLyrics.BRIDGE_PACKAGE, 0)
        }.isSuccess
    }

    /**
     * Logs what actually landed on the **platform** session, as opposed to what we asked Media3
     * to put there.
     *
     * The whole difficulty with this feature is that a publication can be dropped by three
     * separate layers before SystemUI ever sees it, and until now the only evidence either way
     * was that the lock screen looked empty. This reads the framework session back through its
     * own token and reports, per key, whether the payload survived — so the next iteration starts
     * from a measurement instead of a guess. Wrapped in `runCatching` and gated on the log tag:
     * nothing here may ever be able to interrupt playback.
     *
     * `adb logcat -s LiveLyrics` to read it.
     */
    private fun logLiveLyricsDelivery(songId: String) {
        runCatching {
            val token = mediaSession.platformToken
            val controller = android.media.session.MediaController(this, token)
            val metadata = controller.metadata
            if (metadata == null) {
                Log.w(LiveLyricsTag, "$songId: platform session has NO metadata yet")
                return@runCatching
            }
            val landed = OplusLiveLyrics.METADATA_KEY_ALIASES.filter { key ->
                metadata.getString(key) != null
            }
            val sessionExtraKeys = OplusLiveLyrics.METADATA_KEY_ALIASES.filter { key ->
                controller.extras?.getString(key) != null
            }
            Log.i(
                LiveLyricsTag,
                "$songId: metadata keys carrying the document = $landed; " +
                    "session-extra keys = $sessionExtraKeys; " +
                    "lines parsed for the ticker = ${liveLyricLines.size}",
            )
            if (landed.isEmpty() && sessionExtraKeys.isEmpty()) {
                Log.w(
                    LiveLyricsTag,
                    "$songId: nothing reached the platform session — the drop is on our side, " +
                        "not the ROM's",
                )
            }
        }.onFailure {
            Log.w(LiveLyricsTag, "delivery read-back unavailable: ${it.message}")
        }
    }

    /**
     * Mirrors [payload] onto the session extras under every key in
     * [OplusLiveLyrics.METADATA_KEY_ALIASES], clearing them when there are no timed lyrics so a
     * previous track's words can never linger on the lock screen. Main thread.
     */
    private fun publishSessionLyricExtras(@Suppress("UNUSED_PARAMETER") payload: String?) {
        // Clears only. The document belongs in the track's metadata, once, under `lyricInfo`: the
        // contract rules out extras-only copies, and a second copy here is a duplicate publication
        // to a Bridge-patched SystemUI. What earlier builds left on the session is removed.
        val extras = Bundle(mediaSession.sessionExtras)
        val keys = OplusLiveLyrics.METADATA_KEY_ALIASES + OplusLiveLyrics.RETIRED_KEYS
        if (keys.none { extras.containsKey(it) }) return
        keys.forEach { extras.remove(it) }
        mediaSession.sessionExtras = extras
    }

    /** Whether the current item is [songId] and already carries [payload]. Main thread. */
    private fun currentItemCarries(songId: String, payload: String): Boolean =
        with(OplusLiveLyrics) {
            val item = player.currentMediaItem ?: return false
            item.mediaId == songId && item.lyricInfo() == payload
        }

    /**
     * Republishes the current item with [payload] and a fresh signal URI. No-ops unless the track
     * is still the current one. Main thread.
     */
    private fun forceLyricInfo(songId: String, payload: String) {
        with(OplusLiveLyrics) {
            val item = player.currentMediaItem ?: return
            if (item.mediaId != songId) return
            liveLyricsRevision++
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.withLyricInfo(payload, signalUri(songId, liveLyricsRevision)),
            )
            Log.i(LiveLyricsTag, "$songId: forced publication #$liveLyricsRevision")
        }
    }

    /**
     * Attaches already-cached lyrics to the next few queue items, so their first metadata push
     * carries the payload instead of needing one forced after the fact.
     *
     * Only the database is consulted — [com.ozyern.exhale.lyrics.LyricsPreloadManager] is what
     * puts lyrics there ahead of time, and this must not turn into a second fetcher racing it.
     * No signal URI is needed: these items are not current, so nothing is being deduplicated yet.
     */
    private suspend fun preAttachQueueLyrics() {
        val pending = withContext(Dispatchers.Main) {
            val start = player.currentMediaItemIndex + 1
            val end = minOf(start + LiveLyricsPreAttachCount, player.mediaItemCount)
            (start until end).mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                val song = item.metadata ?: return@mapNotNull null
                if (with(OplusLiveLyrics) { item.lyricInfo() } != null) null else index to song
            }
        }

        for ((index, song) in pending) {
            val cached = database.lyrics(song.id).first()?.lyrics ?: continue
            val payload = OplusLiveLyrics.buildPayload(
                songId = song.id,
                songName = song.title,
                artist = song.artists.joinToString { it.name },
                lyrics = cached.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND },
                album = song.album?.title,
                durationMs = song.duration.takeIf { d -> d > 0 }?.times(1000L) ?: 0L,
            ) ?: continue

            withContext(Dispatchers.Main) {
                // The queue can be reordered while we are off the main thread.
                if (index >= player.mediaItemCount) return@withContext
                val item = player.getMediaItemAt(index)
                if (item.mediaId != song.id) return@withContext
                player.replaceMediaItem(index, with(OplusLiveLyrics) { item.withLyricInfo(payload) })
                Log.i(LiveLyricsTag, "${song.id}: attached ahead of playback at $index")
            }
        }
    }

    private fun updateNotification() {
        try {
            val customLayout = listOf(
                // First, so the system media card puts it in the left-hand slot beside previous —
                // where every other player keeps the choice of where the sound goes.
                CommandButton
                    .Builder()
                    .setDisplayName(getString(R.string.output_device))
                    .setIconResId(R.drawable.output_devices)
                    .setSessionCommand(CommandOutputSwitcher)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked == true) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> R.drawable.repeat
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            )
            mediaSession.setCustomLayout(customLayout)
        } catch (e: Exception) {
            reportException(e)
        }
    }

    /**
     * The system's own "play on" picker — phone speaker, Bluetooth, wired, cast — opened from the
     * media card's device button. Android 14 has an API for it; before that, the same dialog is
     * reached by the broadcast SystemUI listens for, and failing both, Bluetooth settings.
     */
    private fun openOutputSwitcher() {
        if (Build.VERSION.SDK_INT >= 34) {
            val shown = runCatching {
                android.media.MediaRouter2.getInstance(this).showSystemOutputSwitcher()
            }.getOrDefault(false)
            if (shown) return
        }
        val dialog = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", packageName)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val sent = runCatching { sendBroadcast(dialog) }.isSuccess
        if (sent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun refreshPlaybackNotification() {
        updateNotification()
        runCatching { super.onUpdateNotification(mediaSession, player.isPlaying) }
            .onFailure { reportException(it) }
    }

    /**
     * Swaps the metadata a queued song carries for [metadata], wherever it is in the queue, keeping
     * everything else about the item. If it is the one playing, what the app shows follows at once.
     */
    private fun replaceQueuedMetadata(mediaId: String, metadata: com.ozyern.exhale.models.MediaMetadata) {
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId != mediaId) continue
            val names = metadata.artists.joinToString { it.name }
            player.replaceMediaItem(
                index,
                item.buildUpon()
                    .setTag(metadata)
                    .setMediaMetadata(item.mediaMetadata.buildUpon().setArtist(names).setSubtitle(names).build())
                    .build(),
            )
        }
        if (currentMediaMetadata.value?.id == mediaId) currentMediaMetadata.value = metadata
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val queued = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        // Some rows (an upload in the search suggestions, a remix channel's video) carry no artist at
        // all, and the song would play with a blank where the artist belongs, everywhere in the app,
        // until someone happened to open the artist's page. The player's own answer always names who
        // published it, so a song that arrives without an artist gets that name before anything else.
        val mediaMetadata = if (queued.artists.isNotEmpty()) {
            queued
        } else {
            val details = playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId).getOrNull()?.videoDetails
            val author = details?.author?.removeSuffix(" - Topic")?.trim()?.takeIf { it.isNotBlank() }
            if (author == null) {
                queued
            } else {
                val filled = queued.copy(
                    artists = listOf(com.ozyern.exhale.models.MediaMetadata.Artist(id = details.channelId.takeIf { it.isNotBlank() }, name = author)),
                )
                withContext(Dispatchers.Main) { replaceQueuedMetadata(mediaId, filled) }
                filled
            }
        }
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (!isTogetherApplyingRemote() && joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_DISABLED")
                return
            }
            ensureScopesActive()
            scope.launch(SilentHandler) {
                val initialStatus =
                    withContext(Dispatchers.IO) {
                        queue.getInitialStatus()
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideo(dataStore.get(HideVideoKey, false))
                    }

                val targetItem =
                    initialStatus.items.getOrNull(initialStatus.mediaItemIndex)
                        ?: queue.preloadItem?.toMediaItem()

                val meta = targetItem?.metadata
                val trackId =
                    meta?.id?.trim().orEmpty().ifBlank {
                        targetItem?.mediaId?.trim().orEmpty()
                    }
                if (trackId.isBlank()) {
                    showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_NO_TRACK")
                    return@launch
                }

                val track =
                    com.ozyern.exhale.together.TogetherTrack(
                        id = trackId,
                        title = meta?.title ?: trackId,
                        artists = meta?.artists?.map { it.name }.orEmpty(),
                        durationSec = meta?.duration ?: -1,
                        thumbnailUrl = meta?.thumbnailUrl,
                    )

                val ops =
                    com.ozyern.exhale.together.TogetherGuestPlaybackPlanner.planPlayTrackNow(
                        roomState = joined.roomState,
                        track = track,
                        positionMs = initialStatus.position,
                        playWhenReady = playWhenReady,
                    )

                if (ops.isEmpty()) {
                    showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_BLOCKED")
                    return@launch
                }

                showTogetherNotice(getString(R.string.together_requesting_song_change), key = "GUEST_PLAYQUEUE_REQUEST")
                ops.forEach { op ->
                    when (op) {
                        is com.ozyern.exhale.together.TogetherGuestOp.Control -> requestTogetherControl(op.action)
                        is com.ozyern.exhale.together.TogetherGuestOp.AddTrack -> requestTogetherAddTrack(op.track, op.mode)
                    }
                }
            }
            return
        }
        if (playWhenReady) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
        }
        ensureScopesActive()
        suppressAutoPlayback = false
        currentQueue = queue
        queueTitle = null
        val permanentShuffle = dataStore.get(PermanentShuffleKey, false)
        if (!permanentShuffle) {
            player.shuffleModeEnabled = false
        }
        
        clearAutomix()
        automixSeedMediaId = null
        autoAddedMediaIds.clear()
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false)).filterVideo(dataStore.get(HideVideoKey, false))
                }
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                // A source can report a start index past its own list; clamp rather than crash.
                val startAt = initialStatus.mediaItemIndex.coerceIn(0, initialStatus.items.lastIndex)
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, startAt)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        startAt + 1,
                        initialStatus.items.size
                    )
                )
                if (player.shuffleModeEnabled) {
                    applyCurrentFirstShuffleOrder()
                }
            } else {
                val items = initialStatus.items
                val index = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
                
                // Chunk Loading: Only load a window around the current item initially
                // to prevent blocking the Main Thread for seconds with large queues.
                val windowStart = (index - 20).coerceAtLeast(0)
                val windowEnd = (index + 50).coerceAtMost(items.size)
                
                val initialChunk = items.subList(windowStart, windowEnd)
                val relativeIndex = index - windowStart
                
                player.setMediaItems(
                    initialChunk,
                    if (relativeIndex > 0) relativeIndex else 0,
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
                if (player.shuffleModeEnabled) {
                    applyCurrentFirstShuffleOrder()
                }
                
                // Defer loading the rest of the queue
                if (items.size > initialChunk.size) {
                    scope.launch(SilentHandler) {
                        try {
                            delay(2000) // Allow UI to settle
                            if (!isActive) return@launch
                            
                            // Add preceding items
                            if (windowStart > 0) {
                                val startChunk = items.subList(0, windowStart)
                                player.addMediaItems(0, startChunk)
                            }
                            
                            // Add succeeding items
                            if (windowEnd < items.size) {
                                val endChunk = items.subList(windowEnd, items.size)
                                player.addMediaItems(endChunk)
                            }

                            if (player.shuffleModeEnabled) {
                                applyCurrentFirstShuffleOrder()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load deferred queue items")
                        }
                    }
                }
            }
        }
    }

    private fun applyCurrentFirstShuffleOrder() {
        val count = player.mediaItemCount
        if (count <= 1) return
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val shuffledIndices = IntArray(count) { it }
        shuffledIndices.shuffle()
        val currentPos = shuffledIndices.indexOf(currentIndex)
        if (currentPos >= 0) {
            shuffledIndices[currentPos] = shuffledIndices[0]
        }
        shuffledIndices[0] = currentIndex
        player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
    }

    fun startRadioSeamlessly() {
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (!isTogetherApplyingRemote() && joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_DISABLED")
                return
            }
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_UNSUPPORTED")
            return
        }
        suppressAutoPlayback = false
        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(videoId = currentMediaId)
            )
            val initialStatus = withContext(Dispatchers.IO) {
                radioQueue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false)).filterVideo(dataStore.get(HideVideoKey, false))
            }

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            val radioItems = initialStatus.items.filter { item ->
                item.mediaId != currentMediaId
            }
            
            if (radioItems.isNotEmpty()) {
                val itemCount = player.mediaItemCount
                
                if (itemCount > currentIndex + 1) {
                    player.removeMediaItems(currentIndex + 1, itemCount)
                }
                
                player.addMediaItems(currentIndex + 1, radioItems)
            }

            currentQueue = radioQueue
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(Dispatchers.IO + SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(AutoLoadMoreKey, true) && 
            player.repeatMode == REPEAT_MODE_OFF) {
            scope.launch(Dispatchers.IO + SilentHandler) {
                val seedAtRequest =
                    withContext(Dispatchers.Main) {
                        player.currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                    }
                YouTube
                    .next(WatchEndpoint(playlistId = playlistId))
                    .onSuccess {
                        YouTube
                            .next(WatchEndpoint(playlistId = it.endpoint.playlistId))
                            .onSuccess {
                                val mediaItems = it.items.map { song -> song.toMediaItem() }
                                withContext(Dispatchers.Main) {
                                    val currentSeed =
                                        player.currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                                    if (seedAtRequest != null && currentSeed != seedAtRequest) return@withContext
                                    automixItems.value = mediaItems
                                    automixSeedMediaId = currentSeed
                                }
                            }
                    }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixJob?.cancel()
        automixJob = null
        automixItems.value = emptyList()
        automixLoading.value = false
        automixError.value = null
        automixSeedMediaId = null
    }

    private fun refreshAutomixForCurrentMedia(force: Boolean) {
        if (!dataStore.get(AutoLoadMoreKey, true)) return
        if (player.repeatMode != REPEAT_MODE_OFF) return
        if (suppressAutoPlayback) return
        if (player.mediaItemCount == 0) return

        val currentMeta = player.currentMetadata ?: return
        val seedMediaId = currentMeta.id.trim().ifBlank { return }

        if (!force && automixSeedMediaId == seedMediaId && automixItems.value.isNotEmpty() && automixJob?.isActive == true) return

        automixJob?.cancel()
        automixJob = null
        automixItems.value = emptyList()
        automixLoading.value = true
        automixError.value = null
        automixSeedMediaId = seedMediaId

        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideo = dataStore.get(HideVideoKey, false)

        automixJob = scope.launch {
            try {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = seedMediaId))
                }

                nextResult
                    .onSuccess { result ->
                        if (automixSeedMediaId != seedMediaId) {
                            automixLoading.value = false
                            return@onSuccess
                        }

                        val queueIds =
                            (0 until player.mediaItemCount)
                                .map { player.getMediaItemAt(it).mediaId }
                                .toSet()

                        val fromNext =
                            result.items
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIds }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)

                        val relatedCandidates =
                            result.relatedEndpoint
                                ?.let { endpoint ->
                                    withContext(Dispatchers.IO) { YouTube.related(endpoint) }
                                        .getOrNull()
                                        ?.songs
                                        .orEmpty()
                                }
                                .orEmpty()

                        val related =
                            relatedCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIds }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)

                        val poolBase =
                            (fromNext + related)
                                .asSequence()
                                .distinctBy { it.mediaId }
                                .take(50)
                                .toList()

                        val pool =
                            if (poolBase.size >= 25 || result.endpoint.playlistId.isNullOrBlank()) {
                                poolBase
                            } else {
                                val playlistId = result.endpoint.playlistId
                                val extra =
                                    withContext(Dispatchers.IO) {
                                        YouTube.next(WatchEndpoint(playlistId = playlistId))
                                    }.getOrNull()
                                        ?.items
                                        .orEmpty()
                                        .map { it.toMediaItem() }
                                        .filter { it.mediaId !in queueIds }
                                        .filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)

                                (poolBase + extra)
                                    .asSequence()
                                    .distinctBy { it.mediaId }
                                    .take(75)
                                    .toList()
                            }

                        if (automixSeedMediaId != seedMediaId) {
                            automixLoading.value = false
                            return@onSuccess
                        }

                        automixItems.value = pool
                        if (pool.isEmpty()) {
                            automixError.value = getString(R.string.error_no_similar_songs)
                        }
                        automixLoading.value = false
                    }
                    .onFailure { throwable ->
                        if (automixSeedMediaId == seedMediaId) {
                            automixLoading.value = false
                            automixError.value =
                                throwable.localizedMessage ?: getString(R.string.error_automix_failed)
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (automixSeedMediaId == seedMediaId) {
                    automixLoading.value = false
                    automixError.value = e.localizedMessage ?: getString(R.string.error_automix_failed)
                }
            }
        }
    }

    fun onInfiniteQueueDisabled() {
        automixJob?.cancel()
        automixJob = null
        automixLoading.value = false
        automixError.value = null
        val currentIndex = player.currentMediaItemIndex
        val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
        if (idsToRemove.isEmpty()) {
            clearAutomix()
            return
        }
        for (i in player.mediaItemCount - 1 downTo 0) {
            if (i == currentIndex) continue
            val item = player.getMediaItemAt(i)
            if (item.mediaId in idsToRemove) {
                player.removeMediaItem(i)
            }
        }
        autoAddedMediaIds.clear()
        clearAutomix()
    }

    fun onInfiniteQueueEnabled() {
        val currentMeta = player.currentMetadata
        if (currentMeta == null) {
            automixError.value = getString(R.string.error_no_song_playing)
            return
        }

        automixJob?.cancel()
        automixLoading.value = true
        automixError.value = null
        automixItems.value = emptyList()
        automixSeedMediaId = currentMeta.id.trim().ifBlank { null }

        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideo = dataStore.get(HideVideoKey, false)

        automixJob = scope.launch {
            try {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = currentMeta.id))
                }

                nextResult
                    .onSuccess { result ->
                        if (suppressAutoPlayback || player.playbackState == STATE_IDLE || player.mediaItemCount == 0) {
                            automixLoading.value = false
                            return@onSuccess
                        }
                        val initialQueueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                        val filteredFromNext =
                            result.items
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in initialQueueIds }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)

                        val addedNow = ArrayList<MediaItem>(32)

                        if (filteredFromNext.isNotEmpty()) {
                            val toAdd = filteredFromNext.take(25)
                            player.addMediaItems(toAdd)
                            toAdd.forEach { autoAddedMediaIds.add(it.mediaId) }
                            addedNow.addAll(toAdd)
                        }

                        val queueIdsAfterNext = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                        val relatedCandidates =
                            result.relatedEndpoint?.let { relatedEndpoint ->
                                withContext(Dispatchers.IO) {
                                    YouTube.related(relatedEndpoint)
                                }.getOrNull()?.songs.orEmpty()
                            }.orEmpty()

                        val filteredRelated =
                            relatedCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIdsAfterNext }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)

                        if (addedNow.isEmpty() && filteredRelated.isNotEmpty()) {
                            val toAdd = filteredRelated.take(25)
                            player.addMediaItems(toAdd)
                            toAdd.forEach { autoAddedMediaIds.add(it.mediaId) }
                            addedNow.addAll(toAdd)
                        }

                        val queueIdsAfterAdds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                        val playlistId = result.endpoint.playlistId
                        val automixCandidates =
                            if (playlistId.isNullOrBlank()) {
                                emptyList()
                            } else {
                                withContext(Dispatchers.IO) {
                                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                                }.getOrNull()?.items.orEmpty()
                            }

                        val filteredAutomix =
                            automixCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIdsAfterAdds }
                                .filterExplicit(hideExplicit)
                                .filterVideo(hideVideo)

                        val addedIds = addedNow.map { it.mediaId }.toSet()
                        val pool =
                            (filteredFromNext + filteredRelated + filteredAutomix)
                                .asSequence()
                                .distinctBy { it.mediaId }
                                .filter { it.mediaId !in addedIds }
                                .take(75)
                                .toList()

                        automixItems.value = pool

                        if (addedNow.isEmpty() && pool.isEmpty()) {
                            automixError.value = getString(R.string.error_no_similar_songs)
                        }
                        automixLoading.value = false
                    }
                    .onFailure { throwable ->
                        automixLoading.value = false
                        automixError.value = throwable.localizedMessage ?: getString(R.string.error_automix_failed)
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                automixLoading.value = false
                automixError.value = e.localizedMessage ?: getString(R.string.error_automix_failed)
            }
        }
    }

    fun stopAndClearPlayback() {
        suppressAutoPlayback = true
        clearAutomix()
        currentQueue = EmptyQueue
        queueTitle = null
        clearStreamRefreshGuards()
        waitingForNetworkConnection.value = false
        currentMediaMetadata.value = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        abandonAudioFocus()
        closeAudioEffectSession()
        consecutivePlaybackErr = 0
    }

    fun playNext(items: List<MediaItem>) {
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    com.ozyern.exhale.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.asReversed().forEach { track ->
                requestTogetherAddTrack(track, com.ozyern.exhale.together.AddTrackMode.PLAY_NEXT)
            }
            return
        }
        suppressAutoPlayback = false
        // Queued, and straight after the current track — in the play order too, with shuffle on.
        val queued = items.map { it.withQueueTier(QueueTier.USER) }
        val insertAt = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
        player.addMediaItems(insertAt, queued)
        player.placeInShuffle(insertAt until insertAt + queued.size, afterQueued = false)
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    com.ozyern.exhale.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.forEach { track ->
                requestTogetherAddTrack(track, com.ozyern.exhale.together.AddTrackMode.ADD_TO_QUEUE)
            }
            return
        }
        suppressAutoPlayback = false
        // After what is already queued, ahead of the rest of the album and of Autoplay — not at
        // the very end of everything, where a queued song used to wait out a whole playlist.
        val queued = items.map { it.withQueueTier(QueueTier.USER) }
        val insertAt = player.userQueueEnd()
        player.addMediaItems(insertAt, queued)
        player.placeInShuffle(insertAt until insertAt + queued.size, afterQueued = true)
        player.prepare()
    }

    /** Empties Next in Queue; the album and Autoplay stay. */
    fun clearQueuedTracks() {
        player.clearUserQueue()
    }

    /** Plays a queue entry the way the queue means it; see [jumpToQueueItem]. */
    fun playQueueEntry(timelineIndex: Int) {
        player.jumpToQueueItem(timelineIndex)
    }

    fun startTogetherHost(
        port: Int,
        displayName: String,
        settings: com.ozyern.exhale.together.TogetherRoomSettings,
    ) {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.Idle
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = false

            val localIps = getLocalIpv4Candidates()
            val localIp = localIps.firstOrNull()
            val sessionId = java.util.UUID.randomUUID().toString()
            val sessionKey = java.util.UUID.randomUUID().toString()
            val joinInfo =
                com.ozyern.exhale.together.TogetherJoinInfo(
                    host = localIp ?: "127.0.0.1",
                    port = port,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    altHosts = localIps.drop(1),
                )
            val joinLink = com.ozyern.exhale.together.TogetherLink.encode(joinInfo)

            val server =
                com.ozyern.exhale.together.TogetherServer(
                    scope = ioScope,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    hostDisplayName = displayName.trim().ifBlank { getString(R.string.app_name) },
                    initialSettings = settings,
                )

            server.onEvent = { event ->
                ioScope.launch(SilentHandler) {
                    handleTogetherHostEvent(event) { server.currentSettings() }
                }
            }

            server.start(port)
            togetherServer = server

            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.Hosting(
                        sessionId = sessionId,
                        joinLink = joinLink,
                        localAddressHint = localIp,
                        port = port,
                        settings = settings,
                        roomState = null,
                    )
            }

            togetherBroadcastJob =
                ioScope.launch(SilentHandler) {
                    while (togetherServer === server) {
                        val state = buildTogetherRoomState(sessionId = sessionId, hostId = togetherHostId)
                        server.broadcastRoomState(state)
                        scope.launch(SilentHandler) {
                            val hosting = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Hosting
                            if (hosting?.sessionId == sessionId) {
                                togetherSessionState.value =
                                    hosting.copy(
                                        settings = server.currentSettings(),
                                        roomState = state.copy(
                                            participants = server.currentParticipants(),
                                            settings = server.currentSettings(),
                                        ),
                                    )
                            }
                        }
                        kotlinx.coroutines.delay(750)
                    }
                }
        }
    }

    private fun togetherOnlineErrorMessage(t: Throwable): String {
        if (t is com.ozyern.exhale.together.TogetherOnlineApiException) {
            val code = t.statusCode
            return when {
                code == 404 -> getString(R.string.together_session_not_found)
                code != null && code in 500..599 -> getString(R.string.together_server_error)
                else -> t.message ?: getString(R.string.network_unavailable)
            }
        }
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        return when (root) {
            is UnknownHostException -> getString(R.string.together_server_unreachable)
            is ConnectException -> getString(R.string.together_server_unreachable)
            is SocketTimeoutException -> getString(R.string.together_connection_timed_out)
            is javax.net.ssl.SSLHandshakeException -> getString(R.string.together_server_unreachable)
            else -> getString(R.string.network_unavailable)
        }
    }

    fun startTogetherOnlineHost(
        displayName: String,
        settings: com.ozyern.exhale.together.TogetherRoomSettings,
    ) {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.Idle
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = true

            val baseUrl = com.ozyern.exhale.together.TogetherOnlineEndpoint.baseUrlOrNull(dataStore)
            if (baseUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        com.ozyern.exhale.together.TogetherSessionState.Error(
                            message = getString(R.string.together_online_not_configured),
                            recoverable = true,
                        )
                }
                return@launch
            }

            // A relay that asks for a token gets the one this build was given; one that doesn't is used as it is.
            val togetherToken = com.ozyern.exhale.BuildConfig.TOGETHER_BEARER_TOKEN.trim().takeIf { it.isNotBlank() }

            val api = com.ozyern.exhale.together.TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
            val hostName = displayName.trim().ifBlank { getString(R.string.app_name) }

            val created =
                runCatching {
                    api.createSession(
                        hostDisplayName = hostName,
                        settings = settings,
                    )
                }.getOrElse { t ->
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            com.ozyern.exhale.together.TogetherSessionState.Error(
                                message = togetherOnlineErrorMessage(t),
                                recoverable = true,
                            )
                    }
                    reportException(t)
                    return@launch
                }

            val onlineHost =
                com.ozyern.exhale.together.TogetherOnlineHost(
                    externalScope = ioScope,
                    sessionId = created.sessionId,
                    sessionKey = created.hostKey,
                    hostId = togetherHostId,
                    hostDisplayName = hostName,
                    initialSettings = created.settings,
                    clientId = getOrCreateTogetherClientId(),
                    bearerToken = togetherToken,
                )

            onlineHost.onEvent = { event ->
                ioScope.launch(SilentHandler) {
                    handleTogetherHostEvent(event) { onlineHost.currentSettings() }
                }
            }

            togetherOnlineHost = onlineHost

            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.HostingOnline(
                        sessionId = created.sessionId,
                        code = created.code,
                        settings = created.settings,
                        roomState = null,
                    )
            }

            val wsUrl =
                com.ozyern.exhale.together.TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                    rawWsUrl = created.wsUrl,
                    baseUrl = baseUrl,
                )
            if (wsUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        com.ozyern.exhale.together.TogetherSessionState.Error(
                            message = "Connection failed: Invalid server websocket URL",
                            recoverable = true,
                        )
                }
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                return@launch
            }

            togetherOnlineConnectJob?.cancel()
            togetherOnlineConnectJob =
                ioScope.launch(SilentHandler) {
                    onlineHost.connect(wsUrl)
                }

            togetherBroadcastJob =
                ioScope.launch(SilentHandler) {
                    while (togetherOnlineHost === onlineHost) {
                        val state =
                            buildTogetherRoomState(
                                sessionId = created.sessionId,
                                hostId = togetherHostId,
                            )
                        onlineHost.broadcastRoomState(state)
                        scope.launch(SilentHandler) {
                            val hosting =
                                togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.HostingOnline
                            if (hosting?.sessionId == created.sessionId) {
                                val currentSettings = onlineHost.currentSettings()
                                togetherSessionState.value =
                                    hosting.copy(
                                        settings = currentSettings,
                                        roomState =
                                            state.copy(
                                                participants = onlineHost.currentParticipants(),
                                                settings = currentSettings,
                                            ),
                                    )
                            }
                        }
                        kotlinx.coroutines.delay(750)
                    }
                }
        }
    }

    fun joinTogether(
        rawLink: String,
        displayName: String,
    ) {
        ensureScopesActive()
        val joinInfo = com.ozyern.exhale.together.TogetherLink.decode(rawLink)
        if (joinInfo == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.Error(
                        message = getString(R.string.invalid_link),
                        recoverable = true,
                    )
            }
            return
        }

        scope.launch(SilentHandler) {
            togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.Joining(joinInfo.toDeepLink())
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = false
            val client =
                com.ozyern.exhale.together.TogetherClient(
                    ioScope,
                    clientId = getOrCreateTogetherClientId(),
                    socketFactory = wifiSocketFactoryOrNull(),
                )
            togetherClient = client
            togetherClock = com.ozyern.exhale.together.TogetherClock()
            togetherSelfParticipantId = null
            togetherLastAppliedQueueHash = null

            togetherClientEventsJob?.cancel()
            togetherClientEventsJob =
                ioScope.launch(SilentHandler) {
                client.events.collect { event ->
                    when (event) {
                        is com.ozyern.exhale.together.TogetherClientEvent.Welcome -> {
                            togetherSelfParticipantId = event.welcome.participantId
                            scope.launch(SilentHandler) {
                                val state = togetherSessionState.value
                                if (state is com.ozyern.exhale.together.TogetherSessionState.Joining) {
                                    val selfName = displayName.trim().ifBlank { getString(R.string.together_role_guest) }
                                    val initial =
                                        com.ozyern.exhale.together.TogetherRoomState(
                                            sessionId = joinInfo.sessionId,
                                            hostId = togetherHostId,
                                            participants =
                                                listOf(
                                                    com.ozyern.exhale.together.TogetherParticipant(
                                                        id = event.welcome.participantId,
                                                        name = selfName,
                                                        isHost = false,
                                                        isPending = event.welcome.isPending,
                                                        isConnected = true,
                                                    ),
                                                ),
                                            settings = event.welcome.settings,
                                            queue = emptyList(),
                                            queueHash = "",
                                            currentIndex = 0,
                                            isPlaying = false,
                                            positionMs = 0L,
                                            repeatMode = 0,
                                            shuffleEnabled = false,
                                            sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                        )
                                    togetherSessionState.value =
                                        com.ozyern.exhale.together.TogetherSessionState.Joined(
                                            role = com.ozyern.exhale.together.TogetherRole.Guest,
                                            sessionId = joinInfo.sessionId,
                                            selfParticipantId = event.welcome.participantId,
                                            roomState = initial,
                                        )
                                }
                            }
                            startTogetherHeartbeat(joinInfo.sessionId, client)
                        }

                        is com.ozyern.exhale.together.TogetherClientEvent.RoomState -> {
                            applyRemoteRoomState(event.state)
                        }

                        is com.ozyern.exhale.together.TogetherClientEvent.JoinDecision -> {
                            if (!event.decision.approved) {
                                scope.launch(SilentHandler) {
                                    togetherSessionState.value =
                                        com.ozyern.exhale.together.TogetherSessionState.Error(
                                            message = getString(R.string.not_allowed),
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }

                        is com.ozyern.exhale.together.TogetherClientEvent.ServerIssue -> {
                            Timber.tag("Together").w("server issue (lan) code=${event.code.orEmpty()} message=${event.message}")
                            when (event.code) {
                                "GUEST_CONTROL_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                    val joined =
                                        togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
                                    if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
                                        togetherPendingGuestControl = null
                                        togetherLastSentControlAction = null
                                        scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
                                    }
                                }

                                "GUEST_ADD_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                }

                                "HOST_OFFLINE" -> {
                                    showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                }

                                else -> {
                                    scope.launch(SilentHandler) {
                                        togetherSessionState.value =
                                            com.ozyern.exhale.together.TogetherSessionState.Error(
                                                message = event.message,
                                                recoverable = true,
                                            )
                                    }
                                    ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }
                        }

                        is com.ozyern.exhale.together.TogetherClientEvent.HeartbeatPong -> {
                            val clock = togetherClock ?: return@collect
                            clock.onPong(
                                sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                                receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                                serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                            )
                        }

                        is com.ozyern.exhale.together.TogetherClientEvent.Error -> {
                            scope.launch(SilentHandler) {
                                togetherSessionState.value =
                                    com.ozyern.exhale.together.TogetherSessionState.Error(
                                        message = event.message,
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }

                        com.ozyern.exhale.together.TogetherClientEvent.Disconnected -> {
                            val current = togetherSessionState.value
                            if (current is com.ozyern.exhale.together.TogetherSessionState.Idle) return@collect
                            scope.launch(SilentHandler) {
                                val currentState = togetherSessionState.value
                                togetherSessionState.value =
                                    com.ozyern.exhale.together.TogetherSessionState.Error(
                                        message =
                                            if (currentState is com.ozyern.exhale.together.TogetherSessionState.Joined &&
                                                currentState.role is com.ozyern.exhale.together.TogetherRole.Guest
                                            ) {
                                                getString(R.string.together_host_left_session)
                                            } else {
                                                getString(R.string.network_unavailable)
                                            },
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }
                    }
                }
            }

            client.connect(joinInfo, displayName.trim().ifBlank { getString(R.string.together_role_guest) })
        }
    }

    fun joinTogetherOnline(
        code: String,
        displayName: String,
    ) {
        ensureScopesActive()
        val trimmedCode = code.trim()
        if (trimmedCode.isBlank()) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.Error(
                        message = getString(R.string.invalid_code),
                        recoverable = true,
                    )
            }
            return
        }

        scope.launch(SilentHandler) {
            togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.JoiningOnline(trimmedCode)
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = true

            val baseUrl = com.ozyern.exhale.together.TogetherOnlineEndpoint.baseUrlOrNull(dataStore)
            if (baseUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        com.ozyern.exhale.together.TogetherSessionState.Error(
                            message = getString(R.string.together_online_not_configured),
                            recoverable = true,
                        )
                }
                return@launch
            }

            // A relay that asks for a token gets the one this build was given; one that doesn't is used as it is.
            val togetherToken = com.ozyern.exhale.BuildConfig.TOGETHER_BEARER_TOKEN.trim().takeIf { it.isNotBlank() }

            val api = com.ozyern.exhale.together.TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
            val resolved =
                runCatching { api.resolveCode(trimmedCode) }
                    .getOrElse { t ->
                        scope.launch(SilentHandler) {
                            togetherSessionState.value =
                                com.ozyern.exhale.together.TogetherSessionState.Error(
                                    message = togetherOnlineErrorMessage(t),
                                    recoverable = true,
                                )
                        }
                        reportException(t)
                        return@launch
                    }

            val client =
                com.ozyern.exhale.together.TogetherClient(
                    ioScope,
                    clientId = getOrCreateTogetherClientId(),
                    bearerToken = togetherToken,
                )
            togetherClient = client
            togetherClock = com.ozyern.exhale.together.TogetherClock()
            togetherSelfParticipantId = null
            togetherLastAppliedQueueHash = null

            togetherClientEventsJob?.cancel()
            togetherClientEventsJob =
                ioScope.launch(SilentHandler) {
                    client.events.collect { event ->
                        when (event) {
                            is com.ozyern.exhale.together.TogetherClientEvent.Welcome -> {
                                togetherSelfParticipantId = event.welcome.participantId
                                scope.launch(SilentHandler) {
                                    val state = togetherSessionState.value
                                    if (state is com.ozyern.exhale.together.TogetherSessionState.JoiningOnline) {
                                        val selfName = displayName.trim().ifBlank { getString(R.string.together_role_guest) }
                                        val initial =
                                            com.ozyern.exhale.together.TogetherRoomState(
                                                sessionId = resolved.sessionId,
                                                hostId = togetherHostId,
                                                participants =
                                                    listOf(
                                                        com.ozyern.exhale.together.TogetherParticipant(
                                                            id = event.welcome.participantId,
                                                            name = selfName,
                                                            isHost = false,
                                                            isPending = event.welcome.isPending,
                                                            isConnected = true,
                                                        ),
                                                    ),
                                                settings = event.welcome.settings,
                                                queue = emptyList(),
                                                queueHash = "",
                                                currentIndex = 0,
                                                isPlaying = false,
                                                positionMs = 0L,
                                                repeatMode = 0,
                                                shuffleEnabled = false,
                                                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                            )
                                        togetherSessionState.value =
                                            com.ozyern.exhale.together.TogetherSessionState.Joined(
                                                role = com.ozyern.exhale.together.TogetherRole.Guest,
                                                sessionId = resolved.sessionId,
                                                selfParticipantId = event.welcome.participantId,
                                                roomState = initial,
                                            )
                                    }
                                }
                                startTogetherHeartbeat(resolved.sessionId, client)
                            }

                            is com.ozyern.exhale.together.TogetherClientEvent.RoomState -> {
                                applyRemoteRoomState(event.state)
                            }

                            is com.ozyern.exhale.together.TogetherClientEvent.JoinDecision -> {
                                if (!event.decision.approved) {
                                    scope.launch(SilentHandler) {
                                        togetherSessionState.value =
                                            com.ozyern.exhale.together.TogetherSessionState.Error(
                                                message = getString(R.string.not_allowed),
                                                recoverable = true,
                                            )
                                    }
                                    ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }

                            is com.ozyern.exhale.together.TogetherClientEvent.ServerIssue -> {
                                Timber.tag("Together").w("server issue (online) code=${event.code.orEmpty()} message=${event.message}")
                                when (event.code) {
                                    "GUEST_CONTROL_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                        val joined =
                                            togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
                                        if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
                                            togetherPendingGuestControl = null
                                            togetherLastSentControlAction = null
                                            scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
                                        }
                                    }

                                    "GUEST_ADD_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                    }

                                    "HOST_OFFLINE" -> {
                                        showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                    }

                                    else -> {
                                        scope.launch(SilentHandler) {
                                            togetherSessionState.value =
                                                com.ozyern.exhale.together.TogetherSessionState.Error(
                                                    message = event.message,
                                                    recoverable = true,
                                                )
                                        }
                                        ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                    }
                                }
                            }

                            is com.ozyern.exhale.together.TogetherClientEvent.HeartbeatPong -> {
                                val clock = togetherClock ?: return@collect
                                clock.onPong(
                                    sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                                    receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                                    serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                                )
                            }

                            is com.ozyern.exhale.together.TogetherClientEvent.Error -> {
                                scope.launch(SilentHandler) {
                                    togetherSessionState.value =
                                        com.ozyern.exhale.together.TogetherSessionState.Error(
                                            message = event.message,
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }

                            com.ozyern.exhale.together.TogetherClientEvent.Disconnected -> {
                                val current = togetherSessionState.value
                                if (current is com.ozyern.exhale.together.TogetherSessionState.Idle) return@collect
                                scope.launch(SilentHandler) {
                                    val currentState = togetherSessionState.value
                                    togetherSessionState.value =
                                        com.ozyern.exhale.together.TogetherSessionState.Error(
                                            message =
                                                if (currentState is com.ozyern.exhale.together.TogetherSessionState.Joined &&
                                                    currentState.role is com.ozyern.exhale.together.TogetherRole.Guest
                                                ) {
                                                    getString(R.string.together_host_left_session)
                                                } else {
                                                    getString(R.string.network_unavailable)
                                                },
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }
                    }
                }

            val wsUrl =
                com.ozyern.exhale.together.TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                    rawWsUrl = resolved.wsUrl,
                    baseUrl = baseUrl,
                )
            if (wsUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        com.ozyern.exhale.together.TogetherSessionState.Error(
                            message = "Connection failed: Invalid server websocket URL",
                            recoverable = true,
                        )
                }
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                return@launch
            }

            client.connect(
                wsUrl = wsUrl,
                sessionId = resolved.sessionId,
                sessionKey = resolved.guestKey,
                displayName = displayName.trim().ifBlank { getString(R.string.together_role_guest) },
            )
        }
    }

    fun leaveTogether() {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.Idle
        }
        ioScope.launch(SilentHandler) { stopTogetherInternal() }
    }

    fun updateTogetherSettings(settings: com.ozyern.exhale.together.TogetherRoomSettings) {
        val server = togetherServer
        val onlineHost = togetherOnlineHost
        if (server == null && onlineHost == null) return
        ioScope.launch(SilentHandler) {
            server?.updateSettings(settings)
            onlineHost?.updateSettings(settings)
        }
    }

    fun approveTogetherParticipant(participantId: String, approved: Boolean) {
        val server = togetherServer
        val onlineHost = togetherOnlineHost
        if (server == null && onlineHost == null) return
        ioScope.launch(SilentHandler) {
            server?.approveParticipant(participantId, approved)
            onlineHost?.approveParticipant(participantId, approved)
        }
    }

    fun kickTogetherParticipant(participantId: String, reason: String? = null) {
        val server = togetherServer
        val onlineHost = togetherOnlineHost
        if (server == null && onlineHost == null) return
        ioScope.launch(SilentHandler) {
            // Whichever kind of room this is; the other is null. It used to reach only the online host,
            // so on a same-Wi-Fi session the button did nothing.
            server?.removeParticipant(participantId, reason, ban = false)
            onlineHost?.kickParticipant(participantId, reason)
        }
    }

    fun banTogetherParticipant(participantId: String, reason: String? = null) {
        val server = togetherServer
        val onlineHost = togetherOnlineHost
        if (server == null && onlineHost == null) return
        ioScope.launch(SilentHandler) {
            server?.removeParticipant(participantId, reason, ban = true)
            onlineHost?.banParticipant(participantId, reason)
        }
    }

    fun requestTogetherControl(action: com.ozyern.exhale.together.ControlAction) {
        val client =
            togetherClient ?: run {
                showTogetherNotice(getString(R.string.network_unavailable), key = "TOGETHER_CLIENT_MISSING")
                return
            }
        val state = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined ?: return
        if (state.role !is com.ozyern.exhale.together.TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToControlPlayback) {
            Timber.tag("Together").i("control blocked locally (disabled) action=${action::class.java.simpleName}")
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_CONTROL_DISABLED_LOCAL")
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val lastAction = togetherLastSentControlAction
        val lastAt = togetherLastSentControlAtElapsedMs
        if (lastAction == action && now - lastAt < 350L) return
        togetherLastSentControlAction = action
        togetherLastSentControlAtElapsedMs = now

        val timeout = if (togetherIsOnlineSession) 5000L else 2000L
        togetherPendingGuestControl =
            when (action) {
                com.ozyern.exhale.together.ControlAction.Play ->
                    TogetherPendingGuestControl(desiredIsPlaying = true, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                com.ozyern.exhale.together.ControlAction.Pause ->
                    TogetherPendingGuestControl(desiredIsPlaying = false, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                is com.ozyern.exhale.together.ControlAction.SeekToIndex ->
                    TogetherPendingGuestControl(desiredIndex = action.index.coerceAtLeast(0), requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                is com.ozyern.exhale.together.ControlAction.SeekToTrack ->
                    TogetherPendingGuestControl(
                        desiredTrackId = action.trackId.trim().ifBlank { null },
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs = now + timeout,
                    )
                else -> togetherPendingGuestControl
            }
        client.requestControl(state.sessionId, action)
    }

    fun requestTogetherAddTrack(
        track: com.ozyern.exhale.together.TogetherTrack,
        mode: com.ozyern.exhale.together.AddTrackMode,
    ) {
        val client = togetherClient ?: return
        val state = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined ?: return
        if (state.role !is com.ozyern.exhale.together.TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToAddTracks) {
            Timber.tag("Together").i("add blocked locally (disabled) mode=$mode trackId=${track.id}")
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_ADD_DISABLED_LOCAL")
            return
        }
        client.requestAddTrack(state.sessionId, track, mode)
    }

    private suspend fun handleTogetherHostEvent(
        event: com.ozyern.exhale.together.TogetherServerEvent,
        currentSettings: suspend () -> com.ozyern.exhale.together.TogetherRoomSettings,
    ) {
        when (event) {
            is com.ozyern.exhale.together.TogetherServerEvent.ControlRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToControlPlayback) return
                applyHostControl(event.request.action)
            }

            is com.ozyern.exhale.together.TogetherServerEvent.AddTrackRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToAddTracks) return
                applyHostAddTrack(event.request.track, event.request.mode)
            }

            is com.ozyern.exhale.together.TogetherServerEvent.Error -> {
                val current = togetherSessionState.value
                if (current is com.ozyern.exhale.together.TogetherSessionState.Idle) return
                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.Error(
                        message = event.message,
                        recoverable = true,
                    )
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
            }

            else -> Unit
        }
    }

    private suspend fun applyHostControl(action: com.ozyern.exhale.together.ControlAction) {
        withContext(Dispatchers.Main) {
            when (action) {
                com.ozyern.exhale.together.ControlAction.Play -> {
                    if (!player.playWhenReady) {
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                com.ozyern.exhale.together.ControlAction.Pause -> {
                    if (player.playWhenReady) {
                        player.playWhenReady = false
                    }
                }

                is com.ozyern.exhale.together.ControlAction.SeekTo -> {
                    player.seekTo(action.positionMs.coerceAtLeast(0L))
                    player.prepare()
                }

                com.ozyern.exhale.together.ControlAction.SkipNext -> {
                    if (player.hasNextMediaItem()) {
                        if (!skipFromAudible(next = true)) player.seekToNext()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                com.ozyern.exhale.together.ControlAction.SkipPrevious -> {
                    if (player.hasPreviousMediaItem()) {
                        if (!skipFromAudible(next = false)) player.seekToPrevious()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                is com.ozyern.exhale.together.ControlAction.SeekToTrack -> {
                    val trackId = action.trackId.trim()
                    if (trackId.isNotBlank()) {
                        val idx =
                            player.mediaItems.indexOfFirst {
                                val metaId = it.metadata?.id
                                it.mediaId == trackId || metaId == trackId
                            }
                        if (idx >= 0 && idx < player.mediaItemCount) {
                            player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                            player.prepare()
                        }
                    }
                }

                is com.ozyern.exhale.together.ControlAction.SeekToIndex -> {
                    val idx = action.index.coerceAtLeast(0)
                    if (idx < player.mediaItemCount) {
                        player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        player.prepare()
                    }
                }

                is com.ozyern.exhale.together.ControlAction.SetRepeatMode -> {
                    if (player.repeatMode != action.repeatMode) {
                        player.repeatMode = action.repeatMode
                    }
                }

                is com.ozyern.exhale.together.ControlAction.SetShuffleEnabled -> {
                    if (player.shuffleModeEnabled != action.shuffleEnabled) {
                        player.shuffleModeEnabled = action.shuffleEnabled
                    }
                }
            }
        }
    }

    private suspend fun applyHostAddTrack(
        track: com.ozyern.exhale.together.TogetherTrack,
        mode: com.ozyern.exhale.together.AddTrackMode,
    ) {
        val mediaItem = track.toMediaMetadata().toMediaItem()
        withContext(Dispatchers.Main) {
            when (mode) {
                com.ozyern.exhale.together.AddTrackMode.PLAY_NEXT -> playNext(listOf(mediaItem))
                com.ozyern.exhale.together.AddTrackMode.ADD_TO_QUEUE -> addToQueue(listOf(mediaItem))
            }
        }
    }

    private suspend fun buildTogetherRoomState(
        sessionId: String,
        hostId: String,
    ): com.ozyern.exhale.together.TogetherRoomState {
        return withContext(Dispatchers.Main) {
            val tracks =
                player.mediaItems.mapNotNull { it.metadata }.map { meta ->
                    com.ozyern.exhale.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }

            val queueHash = com.ozyern.exhale.utils.md5(tracks.joinToString(separator = "|") { it.id })

            com.ozyern.exhale.together.TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                settings = com.ozyern.exhale.together.TogetherRoomSettings(),
                participants = emptyList(),
                queue = tracks,
                queueHash = queueHash,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            )
        }
    }

    private suspend fun applyRemoteRoomState(state: com.ozyern.exhale.together.TogetherRoomState) {
        val pid = togetherSelfParticipantId ?: return
        val now = android.os.SystemClock.elapsedRealtime()

        val pending = togetherPendingGuestControl
        if (pending != null) {
            val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
            val mismatch =
                (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                    (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                    (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
            if (now >= pending.expiresAtElapsedMs) {
                if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                    now - pending.requestedAtElapsedMs >= 1200L &&
                    mismatch
                ) {
                    showTogetherNotice(getString(R.string.together_song_change_failed), key = "GUEST_SEEK_TIMEOUT")
                }
                togetherPendingGuestControl = null
            } else {
                if (mismatch) return
                togetherPendingGuestControl = null
            }
        }

        val lastSentAt = togetherLastAppliedRoomStateSentAtElapsedMs
        val sentAt = state.sentAtElapsedRealtimeMs
        if (sentAt > 0L && lastSentAt > 0L && sentAt <= lastSentAt) return

        val offset = if (togetherIsOnlineSession) 0L else (togetherClock?.snapshot()?.estimatedOffsetMs ?: 0L)
        val correctedSentAt = sentAt + offset
        val estimatedOnlineLatency = if (togetherIsOnlineSession) 1200L else 0L
        val delta = if (togetherIsOnlineSession) estimatedOnlineLatency else (now - correctedSentAt).coerceAtLeast(0L)
        val targetPos =
            if (state.isPlaying) (state.positionMs + delta).coerceAtLeast(0L) else state.positionMs.coerceAtLeast(0L)

        withContext(Dispatchers.Main) {
            togetherApplyingRemote = true
            togetherSuppressEchoUntilElapsedMs = android.os.SystemClock.elapsedRealtime() + 450L
            try {
                val desiredItems = state.queue.map { it.toMediaMetadata().toMediaItem() }
                val desiredIds = state.queue.map { it.id }
                val desiredHash = state.queueHash
                val localIds = player.mediaItems.mapNotNull { it.metadata?.id ?: it.mediaId }.filter { it.isNotBlank() }
                val localHash = if (localIds.isEmpty()) "" else com.ozyern.exhale.utils.md5(localIds.joinToString(separator = "|"))
                val needsRebuild =
                    desiredItems.isNotEmpty() &&
                        (
                            (desiredHash.isNotBlank() && desiredHash != localHash) ||
                                (desiredHash.isBlank() && desiredIds != localIds)
                        )

                if (desiredItems.isNotEmpty() && needsRebuild) {
                    togetherLastAppliedQueueHash = desiredHash.ifBlank { localHash }
                    val startIndex = state.currentIndex.coerceIn(0, desiredItems.lastIndex)
                    suppressAutoPlayback = false
                    currentQueue =
                        com.ozyern.exhale.playback.queues.ListQueue(
                            title = getString(R.string.music_player),
                            items = desiredItems,
                            startIndex = startIndex,
                            position = targetPos,
                        )
                    queueTitle = null
                    player.setMediaItems(desiredItems, startIndex, targetPos)
                    player.prepare()
                    player.repeatMode = state.repeatMode
                    player.shuffleModeEnabled = state.shuffleEnabled
                    player.playWhenReady = state.isPlaying
                    togetherLastRemoteAppliedIndex = startIndex
                } else {
                    val index = state.currentIndex.coerceAtLeast(0)
                    val indexChanged = player.mediaItemCount > 0 && index != player.currentMediaItemIndex
                    val stateChanged =
                        player.repeatMode != state.repeatMode ||
                            player.shuffleModeEnabled != state.shuffleEnabled ||
                            player.playWhenReady != state.isPlaying

                    if (indexChanged) {
                        player.seekTo(index.coerceAtMost(player.mediaItemCount - 1), targetPos)
                        player.prepare()
                        player.playWhenReady = state.isPlaying
                    } else if (stateChanged) {
                        if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                        if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                        if (player.playWhenReady != state.isPlaying) {
                            player.playWhenReady = state.isPlaying
                            val drift = kotlin.math.abs(player.currentPosition - targetPos)
                            if (drift > 100) {
                                player.seekTo(targetPos)
                                player.prepare()
                            }
                        }
                    } else {
                        val drift = kotlin.math.abs(player.currentPosition - targetPos)
                        val seekThreshold = if (togetherIsOnlineSession) 4000L else 2000L
                        val threshold = if (state.isPlaying) seekThreshold else 200L
                        
                        if (drift > threshold) {
                            player.seekTo(targetPos)
                            player.prepare()
                        }
                    }
                    togetherLastRemoteAppliedIndex = index
                }
                togetherLastRemoteAppliedPlayWhenReady = state.isPlaying
                togetherLastAppliedRoomStateSentAtElapsedMs = sentAt

                togetherSessionState.value =
                    com.ozyern.exhale.together.TogetherSessionState.Joined(
                        role = com.ozyern.exhale.together.TogetherRole.Guest,
                        sessionId = state.sessionId,
                        selfParticipantId = pid,
                        roomState = state,
                    )
            } finally {
                togetherApplyingRemote = false
            }
        }
    }

    private fun startTogetherHeartbeat(sessionId: String, client: com.ozyern.exhale.together.TogetherClient) {
        togetherHeartbeatJob?.cancel()
        togetherHeartbeatJob =
            ioScope.launch(SilentHandler) {
                var pingId = 0L
                while (togetherClient === client) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    client.sendHeartbeat(sessionId = sessionId, pingId = pingId++, clientElapsedRealtimeMs = now)
                    kotlinx.coroutines.delay(2000)
                }
            }
    }

    private suspend fun stopTogetherInternal() {
        togetherBroadcastJob?.cancel()
        togetherBroadcastJob = null

        togetherOnlineConnectJob?.cancel()
        togetherOnlineConnectJob = null

        togetherClientEventsJob?.cancel()
        togetherClientEventsJob = null

        togetherHeartbeatJob?.cancel()
        togetherHeartbeatJob = null

        togetherClock = null
        togetherSelfParticipantId = null
        togetherLastAppliedQueueHash = null
        togetherIsOnlineSession = false
        togetherApplyingRemote = false
        togetherSuppressEchoUntilElapsedMs = 0L
        togetherLastAppliedRoomStateSentAtElapsedMs = 0L
        togetherLastRemoteAppliedPlayWhenReady = null
        togetherLastRemoteAppliedIndex = -1
        togetherLastSentControlAtElapsedMs = 0L
        togetherLastSentControlAction = null
        togetherPendingGuestControl = null

        try {
            togetherClient?.disconnect()
        } catch (_: Exception) {}
        togetherClient = null

        try {
            togetherOnlineHost?.disconnect()
        } catch (_: Exception) {}
        togetherOnlineHost = null

        try {
            togetherServer?.stop()
        } catch (_: Exception) {}
        togetherServer = null
    }

    private fun com.ozyern.exhale.together.TogetherTrack.toMediaMetadata(): com.ozyern.exhale.models.MediaMetadata {
        return com.ozyern.exhale.models.MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { name -> com.ozyern.exhale.models.MediaMetadata.Artist(id = null, name = name) },
            duration = durationSec,
            thumbnailUrl = thumbnailUrl,
        )
    }

    /**
     * This phone's LAN addresses, best first, for a guest to dial.
     *
     * "The first IPv4 address", which this used to return, is the carrier's on most phones: with
     * the hotspot on and mobile data up, `rmnet_data0` enumerates before `ap0`, and the join link
     * sent the guest to an address on the operator's network. Now the hotspot interface ranks
     * first, a Wi-Fi the phone joined next, and cellular, VPN and IMS interfaces are never offered.
     */
    private fun getLocalIpv4Candidates(): List<String> =
        runCatching {
            val cellularOrTunnel = Regex("^(rmnet|r_rmnet|ccmni|pdp|radio|clat|v4-|tun|ppp|ipsec|ims|dummy|seth|umts|lo)")
            val secondaryWlan = Regex("^wlan[1-9]")
            fun rank(name: String): Int = when {
                name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") ||
                    name.startsWith("wigig") || secondaryWlan.containsMatchIn(name) -> 0
                name.startsWith("wlan") -> 1
                name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("eth") || name.startsWith("bt-pan") -> 2
                name.startsWith("p2p") -> 3
                else -> 4
            }
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !cellularOrTunnel.containsMatchIn(it.name.lowercase()) }
                .sortedBy { rank(it.name.lowercase()) }
                .flatMap { nic ->
                    nic.inetAddresses.toList().filterIsInstance<java.net.Inet4Address>()
                        .filter { it.isSiteLocalAddress }
                        .mapNotNull { it.hostAddress }
                }
                .distinct()
        }.getOrDefault(emptyList())

    /** A socket factory on the Wi-Fi network, when there is one; see TogetherClient.socketFactory. */
    @Suppress("DEPRECATION")
    private fun wifiSocketFactoryOrNull(): javax.net.SocketFactory? =
        runCatching {
            val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return null
            cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            }?.socketFactory
        }.getOrNull()

    private fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
         database.query {
             currentSong.value?.let {
                 val song = it.song.toggleLike()
                 update(song)
                 syncUtils.likeSong(song)

                 // Check if auto-download on like is enabled and the song is now liked
                 if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                     // Trigger download for the liked song
                     val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest
                         .Builder(song.id, song.id.toUri())
                         .setCustomCacheKey(song.id)
                         .setData(song.title.toByteArray())
                         .build()
                     androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                         this@MusicService,
                         ExoDownloadService::class.java,
                         downloadRequest,
                         false
                     )
                 }
             }
         }
     }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    private fun encodeBandLevelsMb(levelsMb: List<Int>): String {
        return runCatching { EqualizerJson.json.encodeToString(levelsMb) }.getOrNull().orEmpty()
    }

    private fun readEqSettingsFromPrefs(prefs: Preferences): EqSettings {
        val levels = decodeBandLevelsMb(prefs[EqualizerBandLevelsMbKey])
        return EqSettings(
            enabled = prefs[EqualizerEnabledKey] ?: false,
            bandLevelsMb = levels,
            outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
            outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
            bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
            bassBoostStrength = (prefs[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
            virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
            virtualizerStrength = (prefs[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
        )
    }

    fun applyEqFlatPreset() {
        ioScope.launch {
            val caps = eqCapabilities.value
            val bandCount = caps?.bandCount ?: runCatching { equalizer?.numberOfBands?.toInt() }.getOrNull() ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int) {
        scope.launch {
            ensureAudioEffects(player.audioSessionId)
            val eq = equalizer ?: return@launch
            val maxPreset = runCatching { eq.numberOfPresets.toInt() }.getOrNull() ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = runCatching { eq.numberOfBands.toInt() }.getOrNull() ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    runCatching { eq.getBandLevel(band.toShort()).toInt() }.getOrNull() ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope.launch {
                dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }

    private fun resampleLevelsByIndex(levelsMb: List<Int>, targetCount: Int): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (levelsMb.isEmpty()) return List(targetCount) { 0 }
        if (levelsMb.size == targetCount) return levelsMb
        if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)

        val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
        return List(targetCount) { i ->
            val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
            val lo = kotlin.math.floor(pos).toInt().coerceIn(0, levelsMb.lastIndex)
            val hi = kotlin.math.ceil(pos).toInt().coerceIn(0, levelsMb.lastIndex)
            val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
            val a = levelsMb[lo]
            val b = levelsMb[hi]
            (a + ((b - a) * t)).toInt()
        }
    }

    private fun updateEqCapabilitiesFromEffect(eq: Equalizer) {
        val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
        val range = runCatching { eq.bandLevelRange }.getOrNull()
        val minMb = range?.getOrNull(0)?.toInt() ?: -1500
        val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
        val center =
            (0 until bandCount).map { band ->
                (runCatching { eq.getCenterFreq(band.toShort()) }.getOrNull() ?: 0) / 1000
            }
        val presets =
            (0 until eq.numberOfPresets.toInt()).map { idx ->
                runCatching { eq.getPresetName(idx.toShort()).toString() }.getOrNull() ?: "Preset ${idx + 1}"
            }
        eqCapabilities.value =
            EqCapabilities(
                bandCount = bandCount,
                minBandLevelMb = minMb,
                maxBandLevelMb = maxMb,
                centerFreqHz = center,
                systemPresets = presets,
            )
    }

    private fun releaseAudioEffects() {
        audioEffectsSessionId = null
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        try {
            bassBoost?.release()
        } catch (_: Exception) {
        }
        try {
            virtualizer?.release()
        } catch (_: Exception) {
        }
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        eqCapabilities.value = null
    }

    private fun ensureAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        if (audioEffectsSessionId == sessionId && equalizer != null) return

        releaseAudioEffects()
        audioEffectsSessionId = sessionId

        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        equalizer?.let(::updateEqCapabilitiesFromEffect)
        applyEqSettingsToEffects(desiredEqSettings.value)
    }

    private fun applyEqSettingsToEffects(settings: EqSettings) {
        val eq = equalizer ?: return
        val caps = eqCapabilities.value
        val bandCount = caps?.bandCount ?: eq.numberOfBands.toInt()
        val minMb = caps?.minBandLevelMb ?: runCatching { eq.bandLevelRange.getOrNull(0)?.toInt() }.getOrNull() ?: -1500
        val maxMb = caps?.maxBandLevelMb ?: runCatching { eq.bandLevelRange.getOrNull(1)?.toInt() }.getOrNull() ?: 1500

        val levels = resampleLevelsByIndex(settings.bandLevelsMb, bandCount)
        runCatching { eq.enabled = settings.enabled }

        for (band in 0 until bandCount) {
            val levelMb = levels.getOrNull(band)?.coerceIn(minMb, maxMb) ?: 0
            runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
        }

        bassBoost?.let { bb ->
            runCatching { bb.enabled = settings.bassBoostEnabled }
            runCatching { bb.setStrength(settings.bassBoostStrength.toShort()) }
        }

        virtualizer?.let { v ->
            // OReality is a virtualizer, and on OnePlus/OPPO it is already in this chain.
            //
            // Running ours as well is two head-related transfer functions applied in series to the
            // same stereo pair: the stage collapses toward the middle, the centre image goes
            // hollow and cymbals smear - the "weirdish" sound those devices get. The device's own
            // one wins, because it is the one the speakers and the tuning were designed around and
            // the one the user can control from the system panel. This is the same rule the Cavern
            // spatial stage follows in DeviceAudio.defaultSpatialAudio.
            val ours = settings.virtualizerEnabled && !com.ozyern.exhale.utils.DeviceAudio.isOplusDevice
            runCatching { v.enabled = ours }
            runCatching { v.setStrength(settings.virtualizerStrength.toShort()) }
        }

        loudnessEnhancer?.let { le ->
            val gainMb = if (settings.outputGainEnabled) settings.outputGainMb.coerceIn(-1500, 1500) else 0
            runCatching { le.setTargetGain(gainMb) }
            runCatching { le.enabled = settings.outputGainEnabled }
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        val sessionId = player.audioSessionId
        if (sessionId <= 0) return
        isAudioEffectSessionOpened = true
        openedAudioSessionId = sessionId
        ensureAudioEffects(sessionId)
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        val sessionId = openedAudioSessionId ?: player.audioSessionId
        openedAudioSessionId = null
        releaseAudioEffects()
        if (sessionId <= 0) return
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, rawReason: Int) {
    // The crossfade catching up with the song already fading in is that song playing on, not a
    // skip, whatever the player calls it — so the queue, the radio and Together all see it as one.
    val reason =
        if (rawReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && crossfadeAudio?.adoptsTransitionTo(mediaItem) == true) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        } else {
            rawReason
        }
    super.onMediaItemTransition(mediaItem, reason)
    rememberQueueMetadata()

    // Back in the album after a run of queued tracks: those are spent. Posted, so everything
    // below still sees the timeline this transition happened in.
    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem?.queueTier == QueueTier.CONTEXT) {
        scope.launch { player.consumePlayedUserQueue() }
    }

    clearStreamRefreshGuards(
        mediaItem?.mediaId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: player.currentMediaItem?.mediaId
    )

    crossfadeAudio?.onMediaItemTransition(mediaItem, reason)

    // Resolve the next track's stream URL now, so skipping to it does not block on the network.
    prefetchNextStreamUrl()

    // Pre-load lyrics for upcoming songs in queue
    val currentIndex = player.currentMediaItemIndex
    // Convert media items to MediaMetadata for lyrics pre-loading
    val queue = player.mediaItems.mapNotNull { it.metadata }
    if (queue.isNotEmpty()) {
        lyricsPreloadManager?.onSongChanged(currentIndex, queue)
    }

    val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
    if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest &&
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    ) {
        if (!joined.roomState.settings.allowGuestsToControlPlayback) {
            scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val isEcho =
            isTogetherApplyingRemote() ||
                (now < togetherSuppressEchoUntilElapsedMs && togetherLastRemoteAppliedIndex == index)
        if (!isEcho) {
            val trackId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
            requestTogetherControl(
                if (trackId.isBlank()) {
                    com.ozyern.exhale.together.ControlAction.SeekToIndex(
                        index = index,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                } else {
                    com.ozyern.exhale.together.ControlAction.SeekToTrack(
                        trackId = trackId,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        }
    }

    val timelineEmpty = player.currentTimeline.isEmpty || player.mediaItemCount == 0 || player.currentMediaItem == null
    currentMediaMetadata.value = if (timelineEmpty) null else (mediaItem?.metadata ?: player.currentMetadata)

    scrobbleManager?.onSongStop()

    if (!timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.repeatMode == REPEAT_MODE_OFF
    ) {
        val isNearEndWithoutPaging =
            player.mediaItemCount - player.currentMediaItemIndex <= 3 && !currentQueue.hasNextPage()

        if (!isNearEndWithoutPaging) {
            val force =
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED

            val currentId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
            if (force || (currentId.isNotBlank() && automixSeedMediaId != currentId)) {
                refreshAutomixForCurrentMedia(force = force)
            }
        }
    }

    // Auto-load more from queue if available
    if (!suppressAutoPlayback &&
        !timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
        currentQueue.hasNextPage() &&
        player.repeatMode == REPEAT_MODE_OFF
    ) {
        scope.launch(SilentHandler) {
            val mediaItems =
                currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false)).filterVideo(dataStore.get(HideVideoKey, false))
            if (player.playbackState != STATE_IDLE) {
                player.addMediaItems(mediaItems.drop(1))
            } else {
                scope.launch { discordRpc?.stopActivity() }
            }
        }
    }
    
    if (!suppressAutoPlayback &&
        !timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.repeatMode == REPEAT_MODE_OFF &&
        player.mediaItemCount - player.currentMediaItemIndex <= 3 &&
        !currentQueue.hasNextPage()
    ) {
        scope.launch(SilentHandler) {
            if (suppressAutoPlayback || player.mediaItemCount == 0) return@launch
            val queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val currentMediaMetadata = player.currentMetadata
            val currentMediaId = currentMediaMetadata?.id?.trim().orEmpty()
            val existingSeed = automixSeedMediaId?.trim().orEmpty()
            val existingAutomix =
                if (currentMediaId.isNotBlank() && existingSeed == currentMediaId) {
                    automixItems.value
                } else {
                    if (automixItems.value.isNotEmpty()) {
                        clearAutomix()
                    }
                    emptyList()
                }
            if (existingAutomix.isNotEmpty()) {
                if (player.playbackState == STATE_IDLE) return@launch
                val filteredAutomix = existingAutomix.filter { it.mediaId !in queueIds }
                    .map { it.withQueueTier(QueueTier.AUTOPLAY) }
                if (filteredAutomix.isNotEmpty()) {
                    player.addMediaItems(filteredAutomix)
                    filteredAutomix.forEach { autoAddedMediaIds.add(it.mediaId) }
                }
                clearAutomix()
            } else {
                if (currentMediaMetadata != null) {
                    refreshAutomixForCurrentMedia(force = true)
                }
            }
        }
    }

    if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
        scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
    }

    scope.launch {
        val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
        if (shouldSave) {
            saveQueueToDisk()
        }
    }
    ensurePresenceManager()
        updatePlayerWidgets()
}

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
    super.onPlaybackStateChanged(playbackState)
        updatePlayerWidgets()

    val activeMediaId = player.currentMediaItem?.mediaId
    clearStreamRefreshGuards(activeMediaId)
    if (
        playbackState == Player.STATE_READY &&
        player.playWhenReady &&
        player.isPlaying &&
        activeMediaId != null &&
        pendingStreamRefreshValidationMediaId == activeMediaId
    ) {
        refreshValidatedPlayingMediaId = activeMediaId
        pendingStreamRefreshValidationMediaId = null
        streamRecoveryState.remove(activeMediaId)
        Timber.tag("MusicService").i("Stream refresh validated and playback resumed for $activeMediaId")
    }

    scope.launch {
        val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
        if (shouldSave) {
            saveQueueToDisk()
        }
    }

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        crossfadeAudio?.stop(resetMainFade = true)
        scrobbleManager?.onSongStop()
    }
    
    // Auto-start recommendations when playback ends
    if (!suppressAutoPlayback &&
        playbackState == Player.STATE_ENDED &&
        dataStore.get(AutoLoadMoreKey, true) &&
        player.repeatMode == REPEAT_MODE_OFF &&
        player.currentMediaItem != null
    ) {
        scope.launch(SilentHandler) {
            if (suppressAutoPlayback || player.playbackState == STATE_IDLE || player.mediaItemCount == 0) return@launch
            val lastMediaMetadata = player.currentMetadata
            val existingAutomix = automixItems.value
            if (existingAutomix.isNotEmpty()) {
                val filteredAutomix = existingAutomix.filter { it.mediaId != lastMediaMetadata?.id }
                if (filteredAutomix.isNotEmpty()) {
                    autoAddedMediaIds.clear()
                    player.setMediaItems(filteredAutomix, 0, 0)
                    player.prepare()
                    player.play()
                    filteredAutomix.forEach { autoAddedMediaIds.add(it.mediaId) }
                }
                clearAutomix()
            } else {
                if (lastMediaMetadata != null) {
                    withContext(Dispatchers.IO) {
                        YouTube.next(WatchEndpoint(videoId = lastMediaMetadata.id))
                    }.onSuccess { nextResult ->
                        if (suppressAutoPlayback || player.playbackState == STATE_IDLE || player.mediaItemCount == 0) return@onSuccess
                        val hideExplicit = dataStore.get(HideExplicitKey, false)
                        val hideVideo = dataStore.get(HideVideoKey, false)
                        val radioItems = nextResult.items
                            .map { it.toMediaItem() }
                            .filter { it.mediaId != lastMediaMetadata.id }
                            .filterExplicit(hideExplicit)
                            .filterVideo(hideVideo)

                        if (radioItems.isNotEmpty()) {
                            autoAddedMediaIds.clear()
                            player.setMediaItems(radioItems, 0, 0)
                            player.prepare()
                            player.play()
                            radioItems.forEach { autoAddedMediaIds.add(it.mediaId) }

                            withContext(Dispatchers.IO) {
                                YouTube.next(WatchEndpoint(playlistId = nextResult.endpoint.playlistId))
                            }.onSuccess { automixResult ->
                                if (suppressAutoPlayback || player.playbackState == STATE_IDLE) return@onSuccess
                                automixItems.value = automixResult.items
                                    .map { it.toMediaItem() }
                                    .filter { it.mediaId != lastMediaMetadata.id }
                                    .filterExplicit(hideExplicit)
                                    .filterVideo(hideVideo)
                            }
                        }
                    }
                }
            }
        }
    }

    ensurePresenceManager()
    scope.launch {
        try {
            val token = withContext(Dispatchers.IO) { dataStore.get(DiscordTokenKey, "") }
            if (token.isNotBlank() && DiscordPresenceManager.isRunning()) {
                // Obtain the freshest Song from DB using current media item id to avoid stale currentSong.value
                val mediaId = player.currentMediaItem?.mediaId
                val song = if (mediaId != null) withContext(Dispatchers.IO) { database.song(mediaId).first() } else null
                val finalSong = song ?: player.currentMetadata?.let { createTransientSongFromMedia(it) }

                run {
                    val success = withContext(Dispatchers.IO) {
                        DiscordPresenceManager.updateNow(
                            context = this@MusicService,
                            token = token,
                            song = finalSong,
                            positionMs = player.currentPosition,
                            isPaused = !player.playWhenReady,
                        )
                    }
                    if (!success) {
                        Timber.tag("MusicService").w("immediate presence update returned false — attempting restart")
                        if (DiscordPresenceManager.isRunning()) {
                            try {
                                if (DiscordPresenceManager.restart()) {
                                    Timber.tag("MusicService").d("presence manager restarted after failed update")
                                }
                            } catch (ex: Exception) {
                                Timber.tag("MusicService").e(ex, "restart after failed presence update threw")
                            }
                        }
                    }

                    try {
                        val lbEnabled = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzEnabledKey, false) }
                        val lbToken = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzTokenKey, "") }
                        if (lbEnabled && !lbToken.isNullOrBlank() && canSubmitPlayingNow(finalSong)) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    ListenBrainzManager.submitPlayingNow(this@MusicService, lbToken, finalSong, player.currentPosition)
                                } catch (ie: Exception) {
                                    Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed")
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Timber.tag("MusicService").v(e, "immediate presence update failed")
        }
    }
}


    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
            if (crossfadeAudio?.isCrossfading() != true) {
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
    if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest &&
        events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
    ) {
        if (!joined.roomState.settings.allowGuestsToControlPlayback) {
            scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
        } else {
            val now = android.os.SystemClock.elapsedRealtime()
            val playWhenReady = this.player.playWhenReady
            val isEcho =
                isTogetherApplyingRemote() ||
                    (now < togetherSuppressEchoUntilElapsedMs &&
                        togetherLastRemoteAppliedPlayWhenReady != null &&
                        togetherLastRemoteAppliedPlayWhenReady == playWhenReady)
            if (!isEcho) {
                val action =
                    if (playWhenReady) {
                        com.ozyern.exhale.together.ControlAction.Play
                    } else {
                        com.ozyern.exhale.together.ControlAction.Pause
                    }
                requestTogetherControl(action)
            }
        }
    }
    if (events.contains(Player.EVENT_DEVICE_VOLUME_CHANGED)) {
        handleDeviceMuteStateChanged()
    }
    if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && isDeviceMutedNow() && this.player.playWhenReady) {
        handleDeviceMuteStateChanged()
    }
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
        (this.player.playbackState == Player.STATE_IDLE || this.player.playbackState == Player.STATE_ENDED)
    ) {
        wasAutoPausedByDeviceMute = false
    }
    if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
        val newSessionId = this.player.audioSessionId
        val oldSessionId = openedAudioSessionId
        if (isAudioEffectSessionOpened && newSessionId > 0 && oldSessionId != null && oldSessionId > 0 && oldSessionId != newSessionId) {
            sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                },
            )
            openedAudioSessionId = newSessionId
            ensureAudioEffects(newSessionId)
            sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, newSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }
    }
    if (events.containsAny(
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED
        )
    ) {
        val playbackState = player.playbackState
        val keepAudioEffectSessionOpen =
            playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
        if (player.playWhenReady && keepAudioEffectSessionOpen) {
            requestAudioFocus()
        }
        if (keepAudioEffectSessionOpen) {
            openAudioEffectSession()
        } else {
            closeAudioEffectSession()
        }
        updateWakeLock()
        if (player.playWhenReady && keepAudioEffectSessionOpen) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
        } else {
            scheduleStopIfIdle()
        }
    }

       if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            if (crossfadeAudio?.isCrossfading() != true) {
                currentMediaMetadata.value = player.currentMetadata
            }
            // immediate update when media item transitions to avoid stale presence
            scope.launch {
                try {
                    val token = dataStore.get(DiscordTokenKey, "")
                    if (token.isNotBlank() && DiscordPresenceManager.isRunning()) {
                        val mediaId = player.currentMediaItem?.mediaId
                        val song = if (mediaId != null) withContext(Dispatchers.IO) { database.song(mediaId).first() } else null
                        val finalSong = song ?: player.currentMetadata?.let { createTransientSongFromMedia(it) }

                        run {
                            val success = DiscordPresenceManager.updateNow(
                                context = this@MusicService,
                                token = token,
                                song = finalSong,
                                positionMs = player.currentPosition,
                                isPaused = !player.isPlaying,
                            )
                            if (!success) {
                                Timber.tag("MusicService").w("transition immediate presence update failed — attempting restart")
                                try { DiscordPresenceManager.stop(); DiscordPresenceManager.start(this@MusicService, dataStore.get(DiscordTokenKey, ""), { song }, { player.currentPosition }, { !player.isPlaying }, { getPresenceIntervalMillis(this@MusicService) }) } catch (_: Exception) {}
                            }
                            try {
                                val lbEnabled = dataStore.get(ListenBrainzEnabledKey, false)
                                val lbToken = dataStore.get(ListenBrainzTokenKey, "")
                                if (lbEnabled && !lbToken.isNullOrBlank() && canSubmitPlayingNow(finalSong)) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            ListenBrainzManager.submitPlayingNow(this@MusicService, lbToken, finalSong, player.currentPosition)
                                        } catch (ie: Exception) {
                                            Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed on transition")
                                        }
                                    }
                                }
                                
                                // Last.fm now playing - handled by ScrobbleManager
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("MusicService").v(e, "immediate presence update failed on transition")
                }
            }
        }

        // Also handle immediate update for play state and media item transition events explicitly
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                currentMediaMetadata.value = player.currentMetadata
            }
            // Capture player state on Main thread
            val currentMediaId = player.currentMediaItem?.mediaId
            val currentMetadata = player.currentMetadata
            val currentPosition = player.currentPosition
            val isPlaying = player.isPlaying

            scope.launch {
                try {
                    val token = withContext(Dispatchers.IO) { dataStore.get(DiscordTokenKey, "") }
                    if (token.isNotBlank() && DiscordPresenceManager.isRunning()) {
                        val song = if (currentMediaId != null) withContext(Dispatchers.IO) { database.song(currentMediaId).first() } else null
                        val finalSong = song ?: currentMetadata?.let { createTransientSongFromMedia(it) }

                        run {
                            // Run update on IO if possible, assuming updateNow is thread-safe or handles its own threading correctly
                            // If updateNow touches Views, this might break. Assuming it's network/logic.
                            val success = withContext(Dispatchers.IO) {
                                DiscordPresenceManager.updateNow(
                                    context = this@MusicService,
                                    token = token,
                                    song = finalSong,
                                    positionMs = currentPosition,
                                    isPaused = !isPlaying,
                                )
                            }
                            if (!success) {
                                Timber.tag("MusicService").w("isPlaying/mediaTransition immediate presence update failed — restarting manager")
                                if (DiscordPresenceManager.isRunning()) {
                                    try { DiscordPresenceManager.stop(); DiscordPresenceManager.restart() } catch (_: Exception) {}
                                }
                            }
                            try {
                                val lbEnabled = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzEnabledKey, false) }
                                val lbToken = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzTokenKey, "") }
                                if (lbEnabled && !lbToken.isNullOrBlank() && canSubmitPlayingNow(finalSong)) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            ListenBrainzManager.submitPlayingNow(this@MusicService, lbToken, finalSong, currentPosition)
                                        } catch (ie: Exception) {
                                            Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed for isPlaying/mediaTransition")
                                        }
                                    }
                                }
                                
                                // Last.fm now playing - handled by ScrobbleManager
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("MusicService").v(e, "immediate presence update failed for isPlaying/mediaTransition")
                }
            }
        }

   if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
        ensurePresenceManager()
        // Scrobble: Track play/pause state
        scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
       updatePlayerWidgets()
    } else if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
        ensurePresenceManager()
       updatePlayerWidgets()
    } else {
        ensurePresenceManager()
    }
  }


    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
                    return
                }
                requestTogetherControl(
                    com.ozyern.exhale.together.ControlAction.SetShuffleEnabled(
                        shuffleEnabled = shuffleModeEnabled,
                    ),
                )
            }
            return
        }
        if (shuffleModeEnabled) {
            applyCurrentFirstShuffleOrder()
        }
        
        // Save state when shuffle mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        val joined = togetherSessionState.value as? com.ozyern.exhale.together.TogetherSessionState.Joined
        if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
            if (!isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
                    return
                }
                requestTogetherControl(
                    com.ozyern.exhale.together.ControlAction.SetRepeatMode(
                        repeatMode = repeatMode,
                    ),
                )
            }
            return
        }
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
        
        // Save state when repeat mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        val isConnectionError = (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        if (!isNetworkConnected.value || isConnectionError) {
            // A local music player doesn't stop at a song it can't reach: offline, move on to the
            // next one in the queue that is on the phone, and only wait when there is none.
            if (!isNetworkConnected.value && skipToNextPlayableOffline()) return
            waitOnNetworkError()
            return
        }

        val currentMediaId = player.currentMediaItem?.mediaId
        val httpStatusCode = error.httpStatusCodeOrNull()

        if (currentMediaId != null && YTPlayerUtils.isBotDetectionException(error)) {
            if (markAndCheckRecoveryAllowance(currentMediaId)) {
                Timber.tag("MusicService").w(
                    "Bot detection error for $currentMediaId — clearing caches and retrying with fresh stream"
                )
                YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
                playbackUrlCache.remove(currentMediaId)
                pendingStreamRefreshValidationMediaId = currentMediaId
                player.prepare()
                player.playWhenReady = true
                return
            }
        }

        val shouldAttemptStreamRefresh =
            currentMediaId != null && (
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    httpStatusCode in setOf(403, 404, 410, 416, 429, 500, 502, 503)
                )

        if (currentMediaId != null && error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            scope.launch(Dispatchers.IO) {
                runCatching { downloadCache.removeResource(currentMediaId) }
                runCatching { playerCache.removeResource(currentMediaId) }
            }
        }

        if (shouldAttemptStreamRefresh && currentMediaId != null && shouldSkipRedundantStreamRefresh(currentMediaId)) {
            Timber.tag("MusicService").w(
                "Skipping redundant stream refresh for $currentMediaId after validated recovery; resuming playback without URL refresh"
            )
            refreshValidatedPlayingMediaId = null
            player.prepare()
            player.playWhenReady = true
            return
        }

        if (shouldAttemptStreamRefresh && currentMediaId != null && markAndCheckRecoveryAllowance(currentMediaId)) {
            val failingStreamClientKey =
                playbackUrlCache[currentMediaId]
                    ?.first
                    ?.toHttpUrlOrNull()
                    ?.queryParameter("c")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            Timber.tag("MusicService").w(
                "Attempting stream refresh for $currentMediaId (http=$httpStatusCode, code=${error.errorCode}, client=${failingStreamClientKey ?: "unknown"})"
            )
            YTPlayerUtils.markStreamClientFailed(currentMediaId, failingStreamClientKey, httpStatusCode)
            YTPlayerUtils.markPreferredClientFailed(currentMediaId, preferredStreamClient, httpStatusCode)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            playbackUrlCache.remove(currentMediaId)
            pendingStreamRefreshValidationMediaId = currentMediaId
            player.prepare()
            player.playWhenReady = true
            return
        }

        // Any other network-side failure — a read timing out, a connection reset part-way through a
        // range — is transient: pick the song up again from where it was rather than stopping.
        // The same per-song allowance as the recoveries above keeps a truly broken stream from
        // looping.
        val ioError = error.errorCode in 2000..2999
        if (ioError && currentMediaId != null && markAndCheckRecoveryAllowance(currentMediaId)) {
            Timber.tag("MusicService").w(
                "Transient IO error for $currentMediaId (code=${error.errorCode}) — resuming at ${player.currentPosition}ms",
            )
            player.prepare()
            player.playWhenReady = true
            return
        }

        val skipSilenceCurrentlyEnabled = dataStore.get(SkipSilenceKey, false)
        val causeText = (error.cause?.stackTraceToString() ?: error.stackTraceToString()).lowercase()
        val looksLikeSilenceProcessor = skipSilenceCurrentlyEnabled && (
            "silenceskippingaudioprocessor" in causeText || "silence" in causeText
        )

        if (looksLikeSilenceProcessor) {
            scope.launch {
                try {
                    dataStore.edit { settings ->
                        settings[SkipSilenceKey] = false
                    }
                    player.skipSilenceEnabled = false
                    val currentPos = player.currentPosition
                    val targetPos = min(currentPos + 1500L, if (player.duration > 0) player.duration - 1000L else currentPos + 1500L)
                    player.seekTo(targetPos)
                    player.prepare()
                    player.play()
                    return@launch
                } catch (t: Throwable) {
                    Timber.tag("MusicService").e(t, "failed to recover from silence-skipper error")
                }
                if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
                    skipOnError()
                } else {
                    stopOnError()
                }
            }

            return
        }

        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }
    }

    private suspend fun trimPlayerCacheToBytes(limitBytes: Long) {
        if (limitBytes <= 0L) return

        withContext(Dispatchers.IO) {
            val cacheDir = filesDir.resolve("exoplayer")
            val currentSpace = runCatching { playerCache.cacheSpace }.getOrNull() ?: 0L
            var totalBytes = if (currentSpace > 0L) currentSpace else cacheDir.directorySizeBytes()
            if (totalBytes <= limitBytes) return@withContext

            data class Candidate(
                val key: String,
                val lastTouchTimestamp: Long,
                val sizeBytes: Long,
            )

            val candidates =
                runCatching {
                    playerCache.keys.mapNotNull { key ->
                        runCatching {
                            val spans = playerCache.getCachedSpans(key)
                            if (spans.isEmpty()) return@runCatching null
                            val oldestTouch = spans.minOf { it.lastTouchTimestamp }
                            val sizeBytes = spans.sumOf { it.length }
                            Candidate(key = key, lastTouchTimestamp = oldestTouch, sizeBytes = sizeBytes)
                        }.getOrNull()
                    }.sortedBy { it.lastTouchTimestamp }
                }.getOrNull().orEmpty()

            for (candidate in candidates) {
                if (totalBytes <= limitBytes) break
                val removedSize = candidate.sizeBytes.coerceAtLeast(0L)
                runCatching { playerCache.removeResource(candidate.key) }
                totalBytes -= removedSize
            }
        }
    }

    /**
     * Read as far ahead as the track will allow.
     *
     * On the defaults the player kept about twenty seconds in hand, and twenty seconds is not a
     * buffer on a phone — it is the length of one lift ride or one dead spot on a train. Music is
     * small: two minutes of AAC is under two megabytes, so there is no reason not to hold minutes
     * of it. Playback still starts on the first couple of seconds; it just doesn't stop again.
     */
    private fun createLoadControl(): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60_000,
                /* maxBufferMs = */ 300_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setTargetBufferBytes(32 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(/* backBufferDurationMs = */ 30_000, /* retainBackBufferFromKeyframe = */ true)
            .build()

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                mediaOkHttpClient,
                            ),
                        ),
                    )
                    .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * The FLAC/WAV/AIFF on this phone that is this song, or null. The song's title, artists and length
     * come from the database, or from the queue when it has only just been added — songs are written
     * to the database after their stream resolves, not before. A match is also written as this song's
     * format, so the player's badge says Lossless for what is actually playing.
     */
    private fun losslessCopyOf(mediaId: String): com.ozyern.exhale.utils.LocalLossless.Track? {
        // Every chunk of a stream comes through here; only the first one does any work.
        if (com.ozyern.exhale.utils.LocalLossless.known(mediaId)) return com.ozyern.exhale.utils.LocalLossless.cached(mediaId)
        if (!com.ozyern.exhale.utils.LocalMediaScanner.hasPermission(this)) return null
        val metadata = runCatching {
            runBlocking(Dispatchers.IO) { database.song(mediaId).first() }?.toMediaMetadata()
                ?: runBlocking(Dispatchers.Main) { player.findNextMediaItemById(mediaId)?.metadata }
        }.getOrNull() ?: return null
        val track = com.ozyern.exhale.utils.LocalLossless.find(
            this,
            songId = mediaId,
            title = metadata.title,
            artists = metadata.artists.map { it.name },
            durationSeconds = metadata.duration.takeIf { it > 0 },
        ) ?: return null
        val seconds = metadata.duration.takeIf { it > 0 } ?: track.durationSeconds?.toInt() ?: 0
        database.query {
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = -1,
                    mimeType = track.mimeType,
                    codecs = track.codec,
                    bitrate = if (seconds > 0) (track.size * 8 / seconds).toInt() else 0,
                    sampleRate = track.sampleRate,
                    contentLength = track.size,
                    loudnessDb = null,
                    playbackUrl = null,
                ),
            )
        }
        return track
    }

    /**
     * The catalogue song a music video is a video of: searched by its title and artist, and
     * accepted only when the title and artist agree and the length is close — a video's intro and
     * outro allow some drift, a different song does not get through.
     */
    private suspend fun findCatalogueAudio(video: com.ozyern.exhale.innertube.models.response.PlayerResponse.VideoDetails): String? {
        val title = com.ozyern.exhale.lyrics.SongQuery.cleanTitle(video.title)
        val artist = video.author.removeSuffix(" - Topic").removeSuffix("VEVO").trim()
        val seconds = video.lengthSeconds.toIntOrNull() ?: 0
        val results = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
            ?.filterIsInstance<com.ozyern.exhale.innertube.models.SongItem>()
            .orEmpty()
        fun norm(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val wantedTitle = norm(title)
        val wantedArtist = norm(artist)
        return results.mapNotNull { song ->
            val name = norm(com.ozyern.exhale.lyrics.SongQuery.cleanTitle(song.title))
            val titleScore = when {
                name == wantedTitle -> 50
                name.isNotEmpty() && (name.contains(wantedTitle) || wantedTitle.contains(name)) -> 30
                else -> return@mapNotNull null
            }
            val artists = song.artists.joinToString(" ") { norm(it.name) }
            val artistScore = if (wantedArtist.isNotEmpty() && (artists.contains(wantedArtist) || wantedArtist.contains(artists))) 30 else 0
            val drift = song.duration?.let { kotlin.math.abs(it - seconds) } ?: 999
            val lengthScore = when {
                seconds <= 0 -> 0
                drift <= 3 -> 25
                drift <= 15 -> 15
                drift <= 45 -> 5
                else -> return@mapNotNull null
            }
            song.id to titleScore + artistScore + lengthScore
        }.filter { it.second >= 60 }.maxByOrNull { it.second }?.first
    }

    /** "This song: ready · Next: measuring", for the settings page; empty while Automix is off. */
    fun automixStatus(): String {
        if (!automixEnabled.value || !::player.isInitialized) return ""
        val analyzer = mixAnalyzer ?: return ""
        if (togetherSessionState.value !is com.ozyern.exhale.together.TogetherSessionState.Idle) {
            return "Paused while listening together"
        }
        fun stateOf(id: String?): String = when {
            id == null -> "—"
            analyzer.isMeasured(id) -> "ready"
            analyzer.isAnalysing(id) -> "measuring…"
            analyzer.analysisFor(id).status.isNotEmpty() -> "plain fade"
            else -> "waiting"
        }
        val current = player.currentMediaItem?.mediaId ?: return ""
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
            ?.let { runCatching { player.getMediaItemAt(it).mediaId }.getOrNull() }
        return "This song: ${stateOf(current)} · Next: ${stateOf(next)}"
    }

    /**
     * [mediaId]'s audio, for Automix to measure: the file itself for music on the phone or saved
     * from Exhale, otherwise the stream, read through the player's own data source so every byte
     * is cached on the way past and the player finds it there. Null when its length isn't known
     * even after asking — the analyzer tries again later.
     */
    private fun automixSource(
        mediaId: String,
        abort: () -> Boolean,
        followMusicOnly: Boolean = true,
    ): android.media.MediaDataSource? {
        val id = if (followMusicOnly && preferMusicOnly) musicOnlyIds[mediaId] ?: mediaId else mediaId
        val file = com.ozyern.exhale.utils.LocalMediaScanner.uriFor(id)
            ?: com.ozyern.exhale.export.SavedFiles.uriFor(this, id)
            ?: if (preferLocalLossless) losslessCopyOf(id)?.uri else null
        if (file != null) return com.ozyern.exhale.playback.automix.LocalAudioSource.open(contentResolver, file)

        fun knownLength(): Long? =
            runCatching { downloadCache.getContentMetadata(id).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) }
                .getOrNull()?.takeIf { it > 0L }
                ?: runCatching { playerCache.getContentMetadata(id).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) }
                    .getOrNull()?.takeIf { it > 0L }
                ?: runBlocking(Dispatchers.IO) { database.format(id).first()?.contentLength }?.takeIf { it > 0L }

        val factory = createDataSourceFactory()
        val length = knownLength() ?: run {
            // Resolving the stream records its length; one small read is enough to make that happen.
            runCatching {
                val source = factory.createDataSource()
                try {
                    source.open(androidx.media3.datasource.DataSpec.Builder().setUri(id).setKey(id).setLength(1).build())
                    source.read(ByteArray(1), 0, 1)
                } finally {
                    source.close()
                }
            }
            knownLength()
        } ?: return null
        return StreamAudioSource(factory, id, length, abort)
    }

    /** Routes to a USB DAC while one is connected and the setting is on; otherwise, the phone's choice. */
    private fun applyUsbPreference() {
        if (!::player.isInitialized || !::audioManager.isInitialized) return
        val usb = if (preferUsbDac) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
        } else {
            null
        }
        runCatching { player.setPreferredAudioDevice(usb) }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { requestSpec ->
            val requestedId = requestSpec.key ?: error("No media id")
            // A music video already matched to its catalogue song plays that song's audio, under
            // the song's own id so its bytes are never cached as the video's.
            val mediaId = if (preferMusicOnly) musicOnlyIds[requestedId] ?: requestedId else requestedId
            val dataSpec = if (mediaId != requestedId) requestSpec.buildUpon().setKey(mediaId).build() else requestSpec

            // Music from this phone is the file itself: nothing to look up, cache or fetch.
            com.ozyern.exhale.utils.LocalMediaScanner.uriFor(mediaId)?.let { return@Factory dataSpec.withUri(it) }

            // A lossless copy of this song on the phone beats YouTube's stream. Decided once per song,
            // so every later chunk of the same stream is a map lookup. Played under its own cache key:
            // the file's bytes must never be mixed into YouTube's cached copy of the same song.
            if (preferLocalLossless) {
                losslessCopyOf(mediaId)?.let { track ->
                    return@Factory dataSpec.buildUpon()
                        .setUri(track.uri)
                        .setKey("lossless:$mediaId")
                        .build()
                }
            }

            // A finished download plays from disk, whatever has been streamed since.
            //
            // The check below sized "fully cached" from the song's format row, and that row is
            // rewritten every time the song is streamed: stream it again at another bitrate and the
            // row describes a file the download is not, the check fails, and a downloaded song asks
            // the network for itself — which offline is an error. The download cache knows its own
            // file's length; ask it first.
            if (dataSpec.length < 0 && isDownloadedLocally(mediaId)) {
                return@Factory dataSpec
            }

            // Saved to the phone (Music/Exhale): play the file, the way a local music player would.
            // Its own cache key, so the file's bytes are never mixed into a streamed copy.
            com.ozyern.exhale.export.SavedFiles.uriFor(this, mediaId)?.let { saved ->
                if (!playerCacheHasWhole(mediaId)) {
                    return@Factory dataSpec.buildUpon().setUri(saved).setKey("saved:$mediaId").build()
                }
            }

            val requiredCachedLength =
                if (dataSpec.length >= 0) {
                    dataSpec.length
                } else {
                    val contentLength =
                        runBlocking(Dispatchers.IO) {
                            database.format(mediaId).first()?.contentLength
                        } ?: runCatching {
                            downloadCache
                                .getContentMetadata(mediaId)
                                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                        }.getOrNull()?.takeIf { it > 0L } ?: runCatching {
                            playerCache
                                .getContentMetadata(mediaId)
                                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                        }.getOrNull()?.takeIf { it > 0L }

                    contentLength?.let { nonNullContentLength ->
                        (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
                    }
                }

            if (requiredCachedLength != null) {
                val isFullyCached =
                    downloadCache.isCached(mediaId, dataSpec.position, requiredCachedLength) ||
                        playerCache.isCached(mediaId, dataSpec.position, requiredCachedLength)
                if (isFullyCached) {
                    streamSource.putIfAbsent(mediaId, YOUTUBE_SOURCE)
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }
            }

            // JioSaavn, when this song is already playing from there, or when an exact match is in
            // hand before YouTube's answer would be.
            streamSource[mediaId]?.takeIf { it != YOUTUBE_SOURCE }?.let { url ->
                return@Factory dataSpec.buildUpon().setUri(url.toUri()).setKey("saavn:$mediaId").build()
            }
            if (streamSource[mediaId] == null) {
                val lookup = saavnLookup(mediaId)
                if (lookup != null) {
                    // A cached YouTube URL is a head start, not a reason to settle for less: a
                    // lookup already running gets a moment to land.
                    val picked = runBlocking(Dispatchers.IO) {
                        val cachedUrl = playbackUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }
                        if (cachedUrl != null) {
                            withTimeoutOrNull(SAAVN_HEAD_START_GRACE_MS) { lookup.await() }
                        } else {
                            val youtube = ioScope.async {
                                YTPlayerUtils.playerResponseForPlayback(
                                    mediaId,
                                    audioQuality = audioQuality,
                                    connectivityManager = connectivityManager,
                                    preferredStreamClient = preferredStreamClient,
                                    avoidCodecs = avoidStreamCodecs,
                                    preferredCodec = preferredAudioCodec,
                                )
                            }
                            val first = kotlinx.coroutines.selects.select<JioSaavn.Stream?> {
                                lookup.onAwait { it }
                                youtube.onAwait { withTimeoutOrNull(SAAVN_GRACE_MS) { lookup.await() } }
                            }
                            // YouTube's answer still carries the loudness figure and the format row,
                            // and is the fallback: stored either way.
                            if (first != null) {
                                ioScope.launch {
                                    youtube.await().getOrNull()?.let { storeResolvedStream(mediaId, it) }
                                }
                            } else {
                                // Stored where the lines below look first, so it is not asked twice.
                                youtube.await().getOrNull()?.let { storeResolvedStream(mediaId, it) }
                            }
                            first
                        }
                    }
                    if (picked != null) {
                        streamSource[mediaId] = picked.url
                        Timber.tag("JioSaavn").i("%s playing from JioSaavn at %d kbps", mediaId, picked.kbps)
                        return@Factory dataSpec.buildUpon().setUri(picked.url.toUri()).setKey("saavn:$mediaId").build()
                    }
                }
                streamSource[mediaId] = YOUTUBE_SOURCE
            }

            playbackUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                val chunk = if (dataSpec.position > 0L) LATER_CHUNK_LENGTH else CHUNK_LENGTH
                val length = if (dataSpec.length >= 0) minOf(dataSpec.length, chunk) else chunk
                return@Factory dataSpec.withUri(it.first.toUri()).subrange(dataSpec.uriPositionOffset, length)
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    preferredStreamClient = preferredStreamClient,
                    avoidCodecs = avoidStreamCodecs,
                    preferredCodec = preferredAudioCodec,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is YTPlayerUtils.LoginRequiredForPlaybackException -> {
                        promptLoginRecovery(mediaId, throwable.targetUrl)
                        throw PlaybackException(
                            getString(R.string.playback_requires_youtube_music_confirmation),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }

                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            // Prefer music-only: the first time a music video comes through, find the catalogue song
            // it is a video of and play that instead — the video's details stay on screen.
            if (preferMusicOnly && mediaId == requestedId && !musicOnlyIds.containsKey(requestedId)) {
                val type = nonNullPlayback.videoDetails?.musicVideoType
                val isMusicVideo = type == "MUSIC_VIDEO_TYPE_OMV" || type == "MUSIC_VIDEO_TYPE_UGC"
                val counterpart = if (isMusicVideo) {
                    runBlocking(Dispatchers.IO) {
                        withTimeoutOrNull(4_000) { findCatalogueAudio(nonNullPlayback.videoDetails!!) }
                    }
                } else {
                    null
                }
                musicOnlyIds[requestedId] = counterpart ?: requestedId
                if (counterpart != null) {
                    val audio = runBlocking(Dispatchers.IO) {
                        YTPlayerUtils.playerResponseForPlayback(
                            counterpart,
                            audioQuality = audioQuality,
                            connectivityManager = connectivityManager,
                            preferredStreamClient = preferredStreamClient,
                            avoidCodecs = avoidStreamCodecs,
                            preferredCodec = preferredAudioCodec,
                        )
                    }.getOrNull()
                    if (audio != null) {
                        val streamUrl = storeResolvedStream(counterpart, audio)
                        val chunk = if (dataSpec.position > 0L) LATER_CHUNK_LENGTH else CHUNK_LENGTH
                        val length = if (dataSpec.length >= 0) minOf(dataSpec.length, chunk) else chunk
                        return@Factory dataSpec.buildUpon().setKey(counterpart).build()
                            .withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, length)
                    }
                    musicOnlyIds[requestedId] = requestedId
                }
            }
            run {
                val streamUrl = storeResolvedStream(mediaId, nonNullPlayback)
                val chunk = if (dataSpec.position > 0L) LATER_CHUNK_LENGTH else CHUNK_LENGTH
                val length = if (dataSpec.length >= 0) minOf(dataSpec.length, chunk) else chunk
                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, length)
            }
        }
    }

    /**
     * Records a freshly resolved player response and returns the stream URL to play.
     *
     * Shared by the resolving data source and by [prefetchNextStreamUrl], so a prefetched track
     * arrives at playback indistinguishable from one resolved on demand: same format row, same
     * loudness data, same cache entry with the same expiry. Only the timing differs.
     */
    private fun storeResolvedStream(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData,
    ): String {
        val format = playbackData.format
        val loudnessDb = playbackData.audioConfig?.loudnessDb
        val perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb

        Timber.tag("AudioNormalization").d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
        if (loudnessDb == null && perceptualLoudnessDb == null) {
            Timber.tag("AudioNormalization").w("No loudness data available from YouTube for video: $mediaId")
        }

        dropCachedBytesOfOtherFile(mediaId, format)

        database.query {
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = format.itag,
                    mimeType = format.mimeType.split(";")[0],
                    codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                    bitrate = format.bitrate,
                    sampleRate = format.audioSampleRate,
                    contentLength = format.contentLength!!,
                    loudnessDb = loudnessDb,
                    perceptualLoudnessDb = perceptualLoudnessDb,
                    playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                )
            )
        }
        scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }

        val streamUrl = playbackData.streamUrl
        playbackUrlCache[mediaId] =
            streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
        return streamUrl
    }

    /**
     * Empties the play cache for [mediaId] when what is in it came from a different file than [format].
     *
     * The play cache is keyed by song, not by file, and a song is more than one file: YouTube serves
     * it in several renditions, and which one wins depends on the client that answered, the codec
     * preference and the quality setting. Cached bytes of one rendition followed by network bytes of
     * another, at the same offset, is not a stream — the extractor reads a header from the first file
     * and frames from the second, and the song loads and then stops. An update that changes how the
     * rendition is picked turns every partly cached song into exactly that.
     *
     * The song's format row says which file the cached bytes belong to: it is written each time the
     * song resolves, and the bytes cached since came from that resolution. So a new rendition that
     * differs from the row, while the cache holds anything for the song, means the cache is the other
     * file's. It is dropped before any byte of it is read; the song then streams clean.
     */
    private fun dropCachedBytesOfOtherFile(
        mediaId: String,
        format: com.ozyern.exhale.innertube.models.response.PlayerResponse.StreamingData.Format,
    ) {
        val previous = runCatching {
            runBlocking(Dispatchers.IO) { database.format(mediaId).first() }
        }.getOrNull() ?: return
        val sameFile = previous.itag == format.itag && previous.contentLength == format.contentLength
        if (sameFile) return
        runCatching {
            if (playerCache.getCachedSpans(mediaId).isNotEmpty()) {
                Timber.tag("PlayerCache").i(
                    "Dropping cached $mediaId: itag ${previous.itag} (${previous.contentLength}B) " +
                        "is now itag ${format.itag} (${format.contentLength}B)",
                )
                playerCache.removeResource(mediaId)
            }
        }
    }

    /**
     * Resolves the NEXT track's stream URL while the current one is still playing.
     *
     * Without this, skipping to the next song pays for a full YouTube player-response round trip
     * before a single byte of audio is requested - and it is paid on ExoPlayer's loading thread,
     * inside runBlocking, in the resolving data source. Nothing can start until it returns, so the
     * gap between hitting next and hearing anything is however long that network call takes:
     * a second or two on a good connection, ten or more on a bad one.
     *
     * The resolver already prefers [playbackUrlCache] over the network, so the whole fix is to
     * make sure the entry is there before it is asked for. By the time the transition happens the
     * URL is a map lookup, and playback starts at the speed of the audio fetch alone.
     *
     * Deliberately quiet: a failed prefetch changes nothing, because the resolver will make the
     * same call for real and raise a proper error then. It must never throw, never retry, and
     * never surface anything to the user.
     */
    /** Where [mediaId] is playing from this session, for the menu: "JioSaavn · 320 kbps", "YouTube". */
    fun streamSourceLabel(mediaId: String): String? {
        if (com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(mediaId)) return "This phone"
        val source = streamSource[mediaId] ?: return null
        if (source == YOUTUBE_SOURCE) {
            return "YouTube"
        }
        val kbps = Regex("_(\\d+)\\.(mp4|aac|mp3)").find(source)?.groupValues?.get(1)
        return "JioSaavn" + (kbps?.let { " · $it kbps AAC" } ?: "")
    }

    /** Captures the current and next few songs' details for [saavnLookup]. Main thread. */
    private fun rememberQueueMetadata() {
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val end = minOf(player.mediaItemCount, index + 4)
        for (i in index until end) {
            val item = runCatching { player.getMediaItemAt(i) }.getOrNull() ?: continue
            item.metadata?.let { queueMetadata[item.mediaId] = it }
        }
        if (queueMetadata.size > 400) queueMetadata.clear()
    }

    /**
     * The JioSaavn lookup for [mediaId], started if it has not been, or null where it does not
     * apply: switched off, the low-data setting, a phone file, or a song whose details are unknown.
     */
    private fun saavnLookup(mediaId: String): kotlinx.coroutines.Deferred<JioSaavn.Stream?>? {
        if (!jioSaavnUpgrade || audioQuality == com.ozyern.exhale.constants.AudioQuality.LOW) return null
        if (com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(mediaId)) return null
        saavnLookups[mediaId]?.let { return it }
        val song = queueMetadata[mediaId] ?: return null
        return saavnLookups.getOrPut(mediaId) {
            ioScope.async { withTimeoutOrNull(SAAVN_LOOKUP_TIMEOUT_MS) { JioSaavn.streamFor(song) } }
        }
    }

    private fun prefetchNextStreamUrl() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val mediaId = runCatching { player.getMediaItemAt(nextIndex).mediaId }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return

        rememberQueueMetadata()
        saavnLookup(mediaId)
        if (mediaId == streamPrefetchMediaId && streamPrefetchJob?.isActive == true) return
        // Already covered: a URL that has not expired yet.
        if (playbackUrlCache[mediaId]?.second?.let { it > System.currentTimeMillis() } == true) return

        streamPrefetchJob?.cancel()
        streamPrefetchMediaId = mediaId
        streamPrefetchJob = ioScope.launch {
            runCatching {
                // A completed download never touches the network, so resolving it would burn a
                // request to fill a cache entry nothing will read.
                if (isDownloadedLocally(mediaId)) return@runCatching
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    preferredStreamClient = preferredStreamClient,
                    avoidCodecs = avoidStreamCodecs,
                    preferredCodec = preferredAudioCodec,
                ).getOrNull() ?: return@runCatching
                storeResolvedStream(mediaId, playbackData)
                Timber.tag("StreamPrefetch").d("Prefetched stream for %s", mediaId)
            }
        }
    }

    /**
     * Moves the first songs' one-time costs from the first presses of play to app start.
     *
     * After every cold start the first two or three songs were slow and everything after was
     * quick: each launch pays once to fetch and compile YouTube's player code, and once to build
     * the local-lossless index, and whatever is played first pays it. So all of it runs here, in
     * the background, as soon as the queue is back:
     *
     *  1. the player code and its two deciphering functions ([NewPipeUtils.warmUp]);
     *  2. the stream of the song the restored queue is sitting on, resolved exactly as playback
     *     would resolve it — same function, same quality, codec and client settings — and stored
     *     where the resolver looks first, so pressing play starts on a URL already in hand;
     *  3. the lossless index, when that setting is on.
     *
     * Nothing here changes which stream is chosen; it only chooses it earlier. Every step is
     * best-effort: a failure costs nothing, because playback will make the same call itself.
     */
    private fun warmUpPlayback() {
        ioScope.launch {
            val currentId = withContext(Dispatchers.Main) {
                player.currentMediaItem?.mediaId?.trim()?.takeIf { it.isNotBlank() }
            }
            val streamable = currentId?.takeUnless { com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(it) }

            runCatching {
                com.ozyern.exhale.innertube.NewPipeUtils.warmUp(streamable ?: WARM_UP_VIDEO_ID)
            }

            if (preferLocalLossless && com.ozyern.exhale.utils.LocalMediaScanner.hasPermission(this@MusicService)) {
                com.ozyern.exhale.utils.LocalLossless.warm(this@MusicService)
            }

            val mediaId = streamable ?: return@launch
            if (playbackUrlCache[mediaId]?.second?.let { it > System.currentTimeMillis() } == true) return@launch
            runCatching {
                // Nothing to resolve for a song that plays from disk.
                if (isDownloadedLocally(mediaId) || playerCacheHasWhole(mediaId)) return@runCatching
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    preferredStreamClient = preferredStreamClient,
                    avoidCodecs = avoidStreamCodecs,
                    preferredCodec = preferredAudioCodec,
                ).getOrNull() ?: return@runCatching
                storeResolvedStream(mediaId, playbackData)
                Timber.tag("StreamPrefetch").d("Warmed stream for %s at launch", mediaId)
            }
        }
    }

    /** True when the play cache holds the whole of what it last recorded for [mediaId]. */
    private fun playerCacheHasWhole(mediaId: String): Boolean {
        val contentLength = runCatching {
            playerCache.getContentMetadata(mediaId).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrNull()?.takeIf { it > 0L } ?: return false
        return playerCache.isCached(mediaId, 0, contentLength)
    }

    /** Whether [mediaId] can play with no network at all: on the phone, saved, downloaded or fully cached. */
    private fun isPlayableOffline(mediaId: String): Boolean =
        com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(mediaId) ||
            com.ozyern.exhale.export.SavedFiles.has(this, mediaId) ||
            isDownloadedLocally(mediaId) ||
            playerCacheHasWhole(mediaId)

    /** True when [mediaId] is complete in the download cache, so playback never asks the network. */
    private fun isDownloadedLocally(mediaId: String): Boolean {
        val contentLength = runCatching {
            downloadCache.getContentMetadata(mediaId)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrNull()?.takeIf { it > 0L } ?: return false
        return downloadCache.isCached(mediaId, 0, contentLength)
    }

    fun retryCurrentFromFreshStream() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        clearStreamRefreshGuards(mediaId)
        YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
        playbackUrlCache.remove(mediaId)
        pendingStreamRefreshValidationMediaId = mediaId
        player.prepare()
        player.playWhenReady = true
    }

    private fun PlaybackException.httpStatusCodeOrNull(): Int? {
        var t: Throwable? = cause
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
        }
        return null
    }

    private fun markAndCheckRecoveryAllowance(mediaId: String): Boolean {
        val now = System.currentTimeMillis()
        val (count, lastAt) = streamRecoveryState[mediaId] ?: (0 to 0L)
        val nextCount = if (now - lastAt > 45_000L) 1 else count + 1
        if (nextCount > 2) return false
        streamRecoveryState[mediaId] = nextCount to now
        return true
    }

    private fun shouldSkipRedundantStreamRefresh(mediaId: String): Boolean {
        if (refreshValidatedPlayingMediaId != mediaId) return false
        val expiresAt = playbackUrlCache[mediaId]?.second ?: return false
        if (expiresAt <= System.currentTimeMillis()) {
            refreshValidatedPlayingMediaId = null
            return false
        }
        return true
    }

    private fun clearStreamRefreshGuards(activeMediaId: String? = null) {
        val normalizedActiveMediaId = activeMediaId?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedActiveMediaId == null || refreshValidatedPlayingMediaId != normalizedActiveMediaId) {
            refreshValidatedPlayingMediaId = null
        }
        if (normalizedActiveMediaId == null || pendingStreamRefreshValidationMediaId != normalizedActiveMediaId) {
            pendingStreamRefreshValidationMediaId = null
        }
    }

    private fun deviceSupportsMimeType(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            ExtractorsFactory {
                arrayOf(Mp4Extractor(), FragmentedMp4Extractor(), MatroskaExtractor())
            },
        )

    private fun updateAudioOffload(enabled: Boolean) {
        // Offload and OReality are mutually exclusive, and only one of them is why anyone bought
        // the phone.
        //
        // An offloaded stream is handed to the DSP as compressed data and decoded past the point
        // where the effect chain lives, so every system effect - OReality, the OEM equaliser, our
        // own session effects - is bypassed silently. On an OPlus device that trade is never worth
        // taking: the user gets a little battery back in exchange for the phone's entire audio
        // character, with nothing on screen to say why the sound changed. So it stays off there,
        // and the setting says so.
        val offload = enabled && !com.ozyern.exhale.utils.DeviceAudio.isOplusDevice
        runCatching {
            val builder = player.trackSelectionParameters.buildUpon()
            val audioOffloadPrefsClass = Class.forName("androidx.media3.common.AudioOffloadPreferences")
            val audioOffloadPrefsBuilderClass = Class.forName("androidx.media3.common.AudioOffloadPreferences\$Builder")

            val modeFieldName = if (offload) "AUDIO_OFFLOAD_MODE_ENABLED" else "AUDIO_OFFLOAD_MODE_DISABLED"
            val mode = audioOffloadPrefsClass.getField(modeFieldName).getInt(null)

            val prefsBuilder = audioOffloadPrefsBuilderClass.getDeclaredConstructor().newInstance()
            audioOffloadPrefsBuilderClass.getMethod("setAudioOffloadMode", Int::class.javaPrimitiveType).invoke(prefsBuilder, mode)
            val prefs = audioOffloadPrefsBuilderClass.getMethod("build").invoke(prefsBuilder)

            val setMethod =
                builder.javaClass.methods.firstOrNull { method ->
                    method.name == "setAudioOffloadPreferences" && method.parameterTypes.size == 1
                }
            if (setMethod != null) {
                setMethod.invoke(builder, prefs)
                player.trackSelectionParameters = builder.build()
            }
        }
        player.setOffloadEnabled(offload)
    }

    private fun updateWakeLock() {
        val wl = wakeLock ?: return
        val shouldHold = wakelockEnabled && player.isPlaying
        if (shouldHold && !wl.isHeld) {
            wl.acquire()
        } else if (!shouldHold && wl.isHeld) {
            wl.release()
        }
    }

    /**
     * Track selector tuned for "always the best audio we can get".
     *
     * - [setMaxVideoSize] `(0, 0)` hard-disables every video renderer. This service never renders
     *   video, so any muxed video track was pure wasted bandwidth — and on a metered connection
     *   that bandwidth is exactly what we want to spend on the audio track instead.
     * - [setForceHighestSupportedBitrate] flips the adaptive selection logic from "pick the
     *   highest track the *current* estimated bandwidth supports" to "pick the highest track the
     *   *device* supports", so a momentary bandwidth dip can never pin us to a lossy rendition.
     * - [setExceedAudioConstraintsIfNecessary] guarantees a track is still chosen if every
     *   candidate exceeds the (now effectively absent) constraints, rather than failing selection.
     * - Preferred MIME order is lossless-first, then the two codecs YouTube actually serves,
     *   highest-fidelity-per-bit first.
     */
    private fun createTrackSelector() =
        DefaultTrackSelector(this).apply {
            parameters =
                buildUponParameters()
                    .setMaxVideoSize(0, 0)
                    .setForceHighestSupportedBitrate(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_FLAC,
                        MimeTypes.AUDIO_OPUS,
                        MimeTypes.AUDIO_AAC,
                    ).build()
        }

    private fun createRenderersFactory(transitionFilter: TransitionFilterProcessor = mainTransitionFilter) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                val sink = DefaultAudioSink
                    .Builder(this@MusicService)
                    // Always able to carry float; whether a track *gets* float is decided per track
                    // by `floatAllowed` below, so the switch takes effect from the next song on.
                    .setEnableFloatOutput(true)
                    // On the float path the sink runs no processors of its own, Sonic included,
                    // so speed has to be the AudioTrack's job there.
                    .setEnableAudioTrackPlaybackParams(true)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            SilenceSkippingAudioProcessor(
                                1_500_000L,
                                0.35f,
                                500_000L,
                                10,
                                150.toShort(),
                            ),
                            SonicAudioProcessor(),
                        ),
                    ).build()
                // Exhale's own processors run in front of the sink, where float cannot skip them.
                // See PrecisionAudioSink for the whole story.
                return PrecisionAudioSink(
                    delegate = sink,
                    processors = listOf(
                        // The equaliser first, so it shapes the mix the way it was made.
                        ToneEqualizerProcessor(),
                        // Automix's filter sweeps and bass swaps; a straight copy between transitions.
                        transitionFilter,
                        CavernSpatialAudioProcessor(),
                        // Last: it only measures what everything before it made.
                        LevelMeterAudioProcessor(),
                    ),
                    floatAllowed = { dataStore.get(OutputFloatKey, true) && externalOutputActive() },
                )
            }
        }

    /**
     * Whether sound is leaving by something other than the phone's own speaker: headphones,
     * Bluetooth, USB or HDMI. Float stays off on the speaker, where OEM mixers have distorted it.
     */
    private fun externalOutputActive(): Boolean = runCatching {
        val external = setOf(
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY,
            android.media.AudioDeviceInfo.TYPE_HDMI,
            android.media.AudioDeviceInfo.TYPE_LINE_ANALOG,
            android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in external }
    }.getOrDefault(false)

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        if (playbackStats.totalPlayTimeMs >= (
                dataStore[HistoryDuration]?.times(1000f)
                    ?: 30000f
            ) &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val song = database.song(mediaItem.mediaId).first()
                        ?: return@launch

                    val lbEnabled = dataStore.get(ListenBrainzEnabledKey, false)
                    val lbToken = dataStore.get(ListenBrainzTokenKey, "")
                    if (lbEnabled && !lbToken.isNullOrBlank()) {
                        val endMs = System.currentTimeMillis()
                        val startMs = endMs - playbackStats.totalPlayTimeMs
                        try {
                            ListenBrainzManager.submitFinished(this@MusicService, lbToken, song, startMs, endMs)
                        } catch (ie: Exception) {
                            Timber.tag("MusicService").v(ie, "ListenBrainz finished submit failed")
                        }
                    }
                } catch (_: Exception) {
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                runCatching { registerRemoteListeningHistory(mediaItem.mediaId) }
            }
        }
    }

    private suspend fun registerRemoteListeningHistory(mediaId: String) {
        if (!isRemoteHistorySyncAllowed()) return

        val attemptedUrls = LinkedHashSet<String>()

        suspend fun registerTrackingUrl(url: String): Boolean {
            attemptedUrls += url
            return YouTube.registerPlayback(playbackTracking = url)
                .onFailure {
                    reportException(it)
                }.isSuccess
        }

        val cachedPlaybackUrl = database.format(mediaId).first()?.playbackUrl
        val cachedSuccess = cachedPlaybackUrl?.let { registerTrackingUrl(it) } == true
        if (cachedSuccess) return

        val playbackTracking =
            YTPlayerUtils.playerResponseForMetadata(mediaId, null)
                .getOrNull()
                ?.playbackTracking
                ?: return

        for (
            trackingUrl in listOfNotNull(
                playbackTracking.videostatsPlaybackUrl?.baseUrl,
                playbackTracking.videostatsWatchtimeUrl?.baseUrl,
            )
        ) {
            if (trackingUrl.isBlank() || trackingUrl in attemptedUrls) continue
            registerTrackingUrl(trackingUrl)
        }
    }

    private suspend fun isRemoteHistorySyncAllowed(): Boolean {
        if (!dataStore.getAsync(YtmSyncKey, true)) return false
        val cookie = dataStore.getAsync(InnerTubeCookieKey, "")
        return cookie.isNotBlank() && cookie.contains("SAPISID")
    }

    // Create a transient Song object from current Player MediaMetadata when the DB doesn't have it.
    private fun createTransientSongFromMedia(media: com.ozyern.exhale.models.MediaMetadata): Song {
        val songEntity = SongEntity(
            id = media.id,
            title = media.title,
            duration = media.duration,
            thumbnailUrl = media.thumbnailUrl,
            albumId = media.album?.id,
            albumName = media.album?.title,
            explicit = media.explicit,
        )

        val artists = media.artists.map { artist ->
            ArtistEntity(
                id = artist.id ?: "LA_unknown_${artist.name}",
                name = artist.name,
                thumbnailUrl = if (!artist.thumbnailUrl.isNullOrBlank()) artist.thumbnailUrl else media.thumbnailUrl,
            )
        }

        val album = media.album?.let { alb ->
            AlbumEntity(
                id = alb.id,
                playlistId = null,
                title = alb.title,
                year = null,
                thumbnailUrl = media.thumbnailUrl,
                themeColor = null,
                songCount = 1,
                duration = media.duration,
            )
        }

        return Song(
            song = songEntity,
            artists = artists,
            album = album,
            format = null,
        )
    }

    private inline fun <reified T> readPersistentObject(fileName: String): T? {
        val persistentFile = filesDir.resolve(fileName)
        if (!persistentFile.exists() || !persistentFile.isFile) return null

        return synchronized(persistentStateLock) {
            runCatching {
                persistentFile.inputStream().use { fis ->
                    ObjectInputStream(fis).use { input ->
                        input.readObject() as? T
                    }
                }
            }.onFailure {
                Timber.tag("MusicService").w(it, "Failed to read persistent file: $fileName")
                runCatching { persistentFile.delete() }
            }.getOrNull()
        }
    }

    private fun writePersistentObject(fileName: String, payload: Serializable) {
        val persistentFile = filesDir.resolve(fileName)
        val tempFile = filesDir.resolve("$fileName.tmp")

        synchronized(persistentStateLock) {
            runCatching {
                FileOutputStream(tempFile).use { fos ->
                    ObjectOutputStream(fos).use { output ->
                        output.writeObject(payload)
                        output.flush()
                    }
                    fos.fd.sync()
                }

                if (persistentFile.exists() && !persistentFile.delete()) {
                    error("Could not replace $fileName")
                }
                if (!tempFile.renameTo(persistentFile)) {
                    error("Could not atomically move $fileName")
                }
            }.onFailure {
                runCatching { tempFile.delete() }
                reportException(it)
            }
        }
    }

    private fun MediaItem.toPersistableMetadata(): com.ozyern.exhale.models.MediaMetadata? {
        val tagged = metadata
        if (tagged != null) return tagged

        val id =
            mediaId.trim().ifBlank {
                localConfiguration?.uri?.toString()?.trim().orEmpty()
            }.takeIf { it.isNotBlank() } ?: return null

        val title =
            mediaMetadata.title?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                ?: id

        val artistText =
            mediaMetadata.artist?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                ?: mediaMetadata.subtitle?.toString()?.trim().takeIf { !it.isNullOrBlank() }

        val artists =
            artistText
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                ?.map { name -> com.ozyern.exhale.models.MediaMetadata.Artist(id = null, name = name) }
                .orEmpty()

        val thumbnailUrl = mediaMetadata.artworkUri?.toString()
        val albumTitle = mediaMetadata.albumTitle?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val album =
            albumTitle?.let { titleValue ->
                com.ozyern.exhale.models.MediaMetadata.Album(id = titleValue, title = titleValue)
            }

        return com.ozyern.exhale.models.MediaMetadata(
            id = id,
            title = title,
            artists = artists,
            duration = -1,
            thumbnailUrl = thumbnailUrl,
            album = album,
        )
    }

    private suspend fun saveQueueToDisk() {
        val mediaItemsSnapshot = player.mediaItems.mapNotNull { it.toPersistableMetadata() }
        if (mediaItemsSnapshot.isEmpty()) return

        val currentMediaItemIndex = player.currentMediaItemIndex
        val currentPosition = player.currentPosition
        val automixSnapshot = automixItems.value.mapNotNull { it.metadata }
        val playWhenReady = player.playWhenReady
        val repeatMode = player.repeatMode
        val shuffleModeEnabled = player.shuffleModeEnabled
        val volume = playerVolume.value
        val playbackState = player.playbackState

        withContext(Dispatchers.IO) {
            // Save current queue with proper type information
            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = mediaItemsSnapshot,
                mediaItemIndex = currentMediaItemIndex,
                position = currentPosition
            )
            
            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixSnapshot,
                    mediaItemIndex = 0,
                    position = 0,
                )
                
            // Save player state
            val persistPlayerState = PersistPlayerState(
                playWhenReady = playWhenReady,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                volume = volume,
                currentPosition = currentPosition,
                currentMediaItemIndex = currentMediaItemIndex, // Redundant but part of data class
                playbackState = playbackState
            )
            
            writePersistentObject(PERSISTENT_QUEUE_FILE, persistQueue)
            writePersistentObject(PERSISTENT_AUTOMIX_FILE, persistAutomix)
            writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, persistPlayerState)
        }
    }


    override fun onDestroy() {
        LockScreenLyrics.unregister(this, lockLyricsReceiver)
        lockLyricsReceiver = null
        if (::audioManager.isInitialized) runCatching { audioManager.unregisterAudioDeviceCallback(usbDeviceCallback) }
        super.onDestroy()
        unregisterBluetoothReceiver()
        try {
            scope.launch { stopTogetherInternal() }
        } catch (_: Exception) {}
        try {
            DiscordPresenceManager.stop()
        } catch (_: Exception) {}
        try {
            discordRpc?.closeRPC()
        } catch (_: Exception) {}
        discordRpc = null
        try {
            connectivityObserver.unregister()
        } catch (_: Exception) {}
        abandonAudioFocus()
        try {
            releaseAudioEffects()
        } catch (_: Exception) {}
        try {
            if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                val mediaItemsSnapshot = player.mediaItems.mapNotNull { it.metadata }
                val currentMediaItemIndex = player.currentMediaItemIndex
                val currentPosition = player.currentPosition
                val automixSnapshot = automixItems.value.mapNotNull { it.metadata }
                val repeatMode = player.repeatMode
                val shuffleModeEnabled = player.shuffleModeEnabled
                val volume = playerVolume.value
                val playbackState = player.playbackState
                val playWhenReady = player.playWhenReady
                runBlocking(Dispatchers.IO) {
                    val persistQueue = currentQueue.toPersistQueue(
                        title = queueTitle,
                        items = mediaItemsSnapshot,
                        mediaItemIndex = currentMediaItemIndex,
                        position = currentPosition
                    )
                    val persistAutomix = PersistQueue(
                        title = "automix",
                        items = automixSnapshot,
                        mediaItemIndex = 0,
                        position = 0,
                    )
                    val persistPlayerState = PersistPlayerState(
                        playWhenReady = playWhenReady,
                        repeatMode = repeatMode,
                        shuffleModeEnabled = shuffleModeEnabled,
                        volume = volume,
                        currentPosition = currentPosition,
                        currentMediaItemIndex = currentMediaItemIndex,
                        playbackState = playbackState
                    )

                    writePersistentObject(PERSISTENT_QUEUE_FILE, persistQueue)
                    writePersistentObject(PERSISTENT_AUTOMIX_FILE, persistAutomix)
                    writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, persistPlayerState)
                }
            }
        } catch (_: Exception) {}
        try {
            mediaSession.release()
        } catch (_: Exception) {}
        try {
            crossfadeAudio?.release()
            crossfadeAudio = null
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        try {
            player.removeListener(this)
            player.removeListener(sleepTimer)
            player.release()
        } catch (_: Exception) {}
        scopeJob.cancel()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        hasBoundClients = true
        cancelIdleStop()
        val result = super.onBind(intent) ?: binder
        if (player.mediaItemCount > 0 && player.currentMediaItem != null) {
            currentMediaMetadata.value = player.currentMetadata
            scope.launch {
                delay(50)
                updateNotification()
            }
        }
        return result
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hasBoundClients = false
        scheduleStopIfIdle()
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        hasBoundClients = true
        cancelIdleStop()
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // When the user clears the app from Recents, ensure we clear Discord rich presence
        try {
            scope.launch {
                try { discordRpc?.stopActivity() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            if (discordRpc?.isRpcRunning() == true) {
                try { discordRpc?.closeRPC() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        discordRpc = null
        try { DiscordPresenceManager.stop() } catch (_: Exception) {}
        lastPresenceToken = null

        val stopMusicOnTaskClearEnabled = dataStore.get(StopMusicOnTaskClearKey, false)

        try {
            val state = togetherSessionState.value
            val isHostSessionActive =
                state is com.ozyern.exhale.together.TogetherSessionState.Hosting ||
                    state is com.ozyern.exhale.together.TogetherSessionState.HostingOnline ||
                    (state is com.ozyern.exhale.together.TogetherSessionState.Joined &&
                        state.role is com.ozyern.exhale.together.TogetherRole.Host)

            val isPlaybackInactive = player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0

            if (shouldStopServiceOnTaskRemoved(stopMusicOnTaskClearEnabled, isHostSessionActive, isPlaybackInactive)) {
                if (isHostSessionActive && isPlaybackInactive) {
                    runCatching { scope.launch { stopTogetherInternal() } }
                    runCatching { togetherSessionState.value = com.ozyern.exhale.together.TogetherSessionState.Idle }
                    stopSelf()
                    return
                }

                if (stopMusicOnTaskClearEnabled) {
                    if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                        runBlocking { saveQueueToDisk() }
                    }
                    runCatching { stopAndClearPlayback() }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            stopForeground(true)
                        }
                    }
                    stopSelf()
                    return
                }
            }
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    /**
     * Next / previous from the app's own controls. During a transition these skip relative to the
     * song being heard (see [CrossfadeAudio.skipFromAudible]); otherwise as the player always has.
     * Returns true when the transition took the skip.
     */
    fun skipFromAudible(next: Boolean): Boolean = crossfadeAudio?.skipFromAudible(next) == true

    /**
     * The player the notification, lock screen, headset and car see: the real one, with next and
     * previous routed through [skipFromAudible] so they behave the same as the in-app buttons in
     * the middle of a transition.
     */
    private fun sessionPlayer(): Player =
        object : androidx.media3.common.ForwardingPlayer(player) {
            override fun seekToNext() {
                if (!skipFromAudible(next = true)) super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                if (!skipFromAudible(next = true)) super.seekToNextMediaItem()
            }

            override fun seekToPrevious() {
                if (!skipFromAudible(next = false)) super.seekToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                if (!skipFromAudible(next = false)) super.seekToPreviousMediaItem()
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handlePlayerWidgetAction(intent?.action)
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun handlePlayerWidgetAction(action: String?) {
        when (action) {
            PlayerWidgetActions.ACTION_PLAY_PAUSE -> {
                if (!player.playWhenReady) {
                    cancelIdleStop()
                    promoteToStartedService()
                    ensureStartedAsForeground()
                }
                player.togglePlayPause()
                updatePlayerWidgets()
            }

            PlayerWidgetActions.ACTION_NEXT -> {
                if (player.hasNextMediaItem()) {
                    cancelIdleStop()
                    promoteToStartedService()
                    ensureStartedAsForeground()
                    if (!skipFromAudible(next = true)) player.seekToNext()
                    player.prepare()
                    player.playWhenReady = true
                }
                updatePlayerWidgets()
            }

            PlayerWidgetActions.ACTION_PREVIOUS -> {
                if (player.hasPreviousMediaItem()) {
                    cancelIdleStop()
                    promoteToStartedService()
                    ensureStartedAsForeground()
                    if (!skipFromAudible(next = false)) player.seekToPrevious()
                    player.prepare()
                    player.playWhenReady = true
                }
                updatePlayerWidgets()
            }

            PlayerWidgetActions.ACTION_REFRESH -> updatePlayerWidgets()
        }
    }

    private fun updatePlayerWidgets() {
        if (!::player.isInitialized) return

        scope.launch(SilentHandler) {
            val state = PlayerWidgetState.fromPlayer(player, this@MusicService)
            PlayerWidgetUpdater.update(this@MusicService, state)
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (startInForegroundRequired) ensureStartedAsForeground()
        runCatching { super.onUpdateNotification(session, startInForegroundRequired) }
            .onFailure { reportException(it) }
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        /** [streamSource] marker for a song playing from YouTube. */
        private const val YOUTUBE_SOURCE = "youtube"
        /** How long a JioSaavn lookup may take before the song simply plays from YouTube. */
        private const val SAAVN_LOOKUP_TIMEOUT_MS = 4_000L
        /** How long YouTube's ready answer waits for a lookup still in flight. */
        private const val SAAVN_GRACE_MS = 350L
        /** The same, when a prefetched YouTube URL is already in hand. */
        private const val SAAVN_HEAD_START_GRACE_MS = 600L
        internal fun shouldStopServiceOnTaskRemoved(
            stopMusicOnTaskClearEnabled: Boolean,
            isHostSessionActive: Boolean,
            isPlaybackInactive: Boolean,
        ): Boolean = (isHostSessionActive && isPlaybackInactive) || stopMusicOnTaskClearEnabled

        /** `adb logcat -s LiveLyrics` tells you which branch each track took. */
        private const val LiveLyricsTag = "LiveLyrics"

        /** The retry window the OPlus spec allows when a first publication is swallowed. */
        private const val LiveLyricsRepublishDelayMs = 800L

        /** Longest the ticker will sleep while playing — the cadence far from any boundary. */
        private const val LiveLyricsTickMs = 400L

        /** Shortest, used as a line boundary comes up. This is the placement error, near enough. */
        private const val LiveLyricsMinTickMs = 32L

        /**
         * How far ahead of a line's own timestamp it is published.
         *
         * Not a fudge factor for a sloppy loop any more — see [launchLiveLyricTicker]. It buys
         * back the time it actually takes for a string written here to be drawn on the lock
         * screen: a `replaceMediaItem`, the platform session update, and whatever debounce the
         * system UI applies before it redraws the card.
         */
        private const val LiveLyricsPublishLeadMs = 120L

        /** Cadence while paused — just often enough to notice playback resuming. */
        private const val LiveLyricsIdleTickMs = 1_000L

        /** How far ahead to attach cached lyrics, so transitions publish them from the start. */
        private const val LiveLyricsPreAttachCount = 3

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
    /**
     * How much of a stream one request asks for.
     *
     * googlevideo serves an open-ended GET at about the speed the audio plays, so playback asks in
     * ranges. Every range boundary costs a fresh connection, and a stream fetched 512KB at a time —
     * roughly half a minute of audio — spends its life one chunk ahead: any boundary where the
     * handshake is slow lands as a stall a minute or two into the song, at the same place every
     * time, because the boundaries are at fixed byte offsets.
     *
     * So: the first request stays small, because nothing plays until it arrives, and every request
     * after it is larger — four minutes of a 128kbps track, usually the whole rest of the song in
     * one connection.
     *
     * The first request is no longer smaller, and that is the fix for the stall at around half a
     * minute. A range's *size* does not gate playback - the response streams, and the player starts
     * on the first frames that arrive - so a small first chunk bought nothing and cost a boundary
     * early in the song. 512KB is about thirty seconds of a 128kbps stream, so the first boundary
     * landed exactly where the audio ran out; 1.5MB moved it to roughly a minute and a half, which
     * is still inside the song at higher bitrates, which is why some tracks went on stalling. At
     * 4MB the first boundary is past the end of most songs entirely.
     */
        const val CHUNK_LENGTH = 4 * 1024 * 1024L
        const val LATER_CHUNK_LENGTH = 4 * 1024 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"

        /**
         * Any public video: the player code is the same for all of them, so warming it needs only
         * a valid id when there is no restored song to use.
         */
        const val WARM_UP_VIDEO_ID = "jNQXAC9IVRw"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
    }
}
