/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.playback

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import com.ozyern.exhale.constants.AudioQuality
import com.ozyern.exhale.constants.AudioCodec
import com.ozyern.exhale.constants.AudioCodecKey
import com.ozyern.exhale.constants.AudioQualityKey
import com.ozyern.exhale.constants.PlayerStreamClient
import com.ozyern.exhale.constants.PlayerStreamClientKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.db.entities.SongEntity
import com.ozyern.exhale.di.DownloadCache
import com.ozyern.exhale.di.PlayerCache
import com.ozyern.exhale.utils.YTPlayerUtils
import com.ozyern.exhale.utils.StreamClientUtils
import com.ozyern.exhale.utils.enumPreference
import com.ozyern.exhale.constants.NetworkMeteredKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: Cache,
    @PlayerCache val playerCache: Cache,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.HIGHEST)
    private val preferredAudioCodec by enumPreference(context, AudioCodecKey, AudioCodec.AAC)
    private val preferredStreamClient by enumPreference(context, PlayerStreamClientKey, PlayerStreamClient.ANDROID_VR)
    /** mediaId → (stream URL, expiry, that stream's own length or null when YouTube didn't say). */
    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Triple<String, Long, Long?>>()
    private val avoidStreamCodecs: Set<String> by lazy {
        if (deviceSupportsMimeType("audio/opus")) emptySet() else setOf("opus")
    }
    internal val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamProxy)
            .followRedirects(true)
            .followSslRedirects(true)
            // Every download in the queue goes to the same host, and OkHttp's default ceiling
            // there is five concurrent calls. That is not a limit anyone chose for this — it is
            // the library's default for a browser-shaped workload — and it sat right on top of
            // however many downloads the manager was willing to run.
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 24
                    maxRequestsPerHost = 12
                },
            )
            // OkHttp keeps five idle connections by default. Five parallel downloads plus the
            // player-response calls that resolve them is already more than that, so the sixth
            // song in a queue paid for a fresh TLS handshake to googlevideo that the fifth had
            // just thrown away.
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /**
     * What a download reads through.
     *
     * The player cache is in the chain so that a song you have already streamed downloads out of
     * local storage instead of off the network — but **read-only**, which is the
     * `setCacheWriteDataSinkFactory(null)`. It used to write as well, so every downloaded byte was
     * committed to disk twice, once into the download cache by the download manager and once into
     * the player cache on the way past. That is double the write time on a phone's storage for a
     * copy nothing reads, and it evicted genuinely useful playback cache to hold it.
     */
    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            // Straight to the network. Reading through the player cache saved re-fetching a song
            // you had streamed, but the cache holds whatever *playback* chose — another bitrate,
            // another codec — under the same key, and a download that resolved a different stream
            // stitched the two together, or failed when the lengths disagreed.
            OkHttpDataSource.Factory(
                mediaOkHttpClient,
            ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                // The length of *this* stream, never the shared format row, which playback rewrites.
                return@Factory bounded(dataSpec.withUri(it.first.toUri()), mediaId, it.third, useStoredLength = false)
            }
            val playbackData = runBlocking(Dispatchers.IO) {
                val networkMeteredPref = context.dataStore.get(NetworkMeteredKey, false)
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    preferredStreamClient = preferredStreamClient,
                    connectivityManager = connectivityManager,
                    networkMetered = networkMeteredPref,
                    avoidCodecs = avoidStreamCodecs,
                    preferredCodec = preferredAudioCodec,
                )
            }.getOrThrow()
            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=").getOrNull(1)?.removeSurrounding("\"").orEmpty(),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength ?: 0L,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )

                val now = LocalDateTime.now()
                val existing = getSongByIdBlocking(mediaId)?.song

                val updatedSong = if (existing != null) {
                    if (existing.dateDownload == null) existing.copy(dateDownload = now) else existing
                } else {
                    SongEntity(
                        id = mediaId,
                        title = playbackData.videoDetails?.title ?: "Unknown",
                        duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                        thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                        dateDownload = now
                    )
                }

                upsert(updatedSong)
            }

            val streamUrl = playbackData.streamUrl

            songUrlCache[mediaId] = Triple(
                streamUrl,
                System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
                format.contentLength?.takeIf { it > 0 },
            )
            bounded(dataSpec.withUri(streamUrl.toUri()), mediaId, format.contentLength?.takeIf { it > 0 }, useStoredLength = false)
        }

    /**
     * Ask for a byte range rather than for "the file".
     *
     * This is the single biggest thing standing between a download and the connection it is on.
     * `OkHttpDataSource` only sends a `Range` header when the DataSpec says where it ends, and a
     * download's spec does not — the download manager opens the whole resource and reads until
     * EOF. googlevideo serves an open-ended GET at roughly the rate the audio plays back; the same
     * bytes asked for as an explicit range come down as fast as the link allows. It is why
     * *playback* has never felt slow while downloading a handful of songs took minutes: the
     * playback path has always bounded its requests (see MusicService.CHUNK_LENGTH) and this one
     * never did.
     *
     * The bound has to be the real end of the file and nothing shorter. `CacheDataSource` records
     * whatever length the upstream reports as the resource's content length, so a spec that stops
     * early is not a slow download — it is a truncated one that reports itself finished. The
     * length comes from the player response that produced the URL, or from the format row written
     * the last time it did, so the two always describe the same stream.
     */
    private fun bounded(
        dataSpec: DataSpec,
        mediaId: String,
        knownLength: Long? = null,
        useStoredLength: Boolean = true,
    ): DataSpec {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec

        // Unknown length: an open-ended request is slower but whole; a guessed one truncates.
        val total = knownLength
            ?: (if (useStoredLength) runCatching {
                runBlocking(Dispatchers.IO) { database.format(mediaId).first()?.contentLength }
            }.getOrNull()?.takeIf { it > 0 } else null)
            ?: return dataSpec

        val remaining = total - dataSpec.position
        if (remaining <= 0L) return dataSpec
        return dataSpec.buildUpon().setLength(remaining).build()
    }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    /**
     * Threads for the download manager to work on.
     *
     * `Runnable::run` — what this used to pass — is legal, and means each download's cache writer
     * runs on the download task's own thread. What it does not do is give the manager anywhere to
     * put the work that is not a download: the content-length probe each new task opens with, and
     * the removals when something is deleted, all queue up behind whatever is currently
     * transferring. A small pool is what media3's own guidance asks for and costs six idle threads.
     */
    private val downloadExecutor = Executors.newFixedThreadPool(6)

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            downloadExecutor,
        ).apply {
            // Five at a time rather than three.
            //
            // Even with ranged requests a single stream is the slowest part of this, so queue
            // throughput is mostly a question of how many are in flight — downloading an album is
            // the case that matters and it is entirely parallel. Five is where the returns flatten
            // on a phone: past that the connections start competing for the same link and the
            // notification becomes a wall of half-finished rows.
            maxParallelDownloads = 5
            // A dropped connection mid-song should be retried, not surfaced as a failed download
            // the user has to notice and re-queue.
            minRetryCount = 5
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }
                    }
                }
            )
        }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val result = mutableMapOf<String, Download>()
            val cursor = downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                result[cursor.download.request.id] = cursor.download
            }
            downloads.value = result
        }

        // Progress, while it is happening.
        //
        // `DownloadManager.Listener` only fires when a download *changes state* — queued, started,
        // finished — and never as bytes arrive. Everything that draws a progress bar was therefore
        // reading the percentage recorded at the moment the download started, which is zero, so a
        // song downloading for two minutes showed an empty bar and then a tick. media3 keeps the
        // live figures on the Download objects themselves, so the only thing missing was somebody
        // to look at them.
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val active = downloadManager.currentDownloads
                if (active.isEmpty()) {
                    delay(2000)
                    continue
                }
                downloads.update { current -> current + active.associateBy { it.request.id } }
                delay(500)
            }
        }
    }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    private fun deviceSupportsMimeType(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }
}
