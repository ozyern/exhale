/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AudioCodec
import com.ozyern.exhale.constants.AudioQuality
import com.ozyern.exhale.constants.PlayerStreamClient
import com.ozyern.exhale.constants.PlayerStreamClientKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.di.DownloadCache
import com.ozyern.exhale.di.PlayerCache
import com.ozyern.exhale.lyrics.LyricsHelper
import com.ozyern.exhale.lyrics.LyricsUtils
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.utils.YTPlayerUtils
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves songs to the phone as real files — `Music/Exhale/Artist - Title.m4a` — that any player,
 * file manager or share sheet can use, unlike downloads, which live inside Exhale's cache.
 *
 * ### Why M4A and not MP3
 *
 * YouTube serves AAC, so an .m4a is the stream itself, unchanged: no re-encode, no quality lost,
 * seconds per song. MP3 would mean decoding and re-encoding on the phone — slower, a bundled
 * encoder, and a generation of loss — to reach a format that plays in exactly the same players.
 *
 * ### What goes in the file
 *
 * Title, artists, album artist, album, year, the cover at 1200px and the lyrics — time-synced LRC
 * when there are synced lyrics — in the atoms Poweramp, Musicolet, Samsung Music, Apple Music and
 * foobar all read. Synced lyrics are normalised to plain line-level LRC, because word-level TTML
 * shows as markup in every other player.
 *
 * Songs already downloaded in AAC are exported from those bytes, offline.
 */
@Singleton
class SongExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
    private val lyricsHelper: LyricsHelper,
    @DownloadCache private val downloadCache: Cache,
    @PlayerCache private val playerCache: Cache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<MediaMetadata>(Channel.UNLIMITED)
    private val queued = AtomicInteger(0)
    private val done = AtomicInteger(0)
    private val failed = AtomicInteger(0)

    /** Song ids currently waiting or in flight, for menus that want to show it. */
    val pending = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch {
            for (song in queue) {
                progress(song)
                val ok = runCatching { export(song) }
                    .onFailure { Timber.tag(TAG).e(it, "Export failed for ${song.id}") }
                    .isSuccess
                if (ok) done.incrementAndGet() else failed.incrementAndGet()
                pending.value = pending.value - song.id
                if (pending.value.isEmpty()) finished()
            }
        }
    }

    /** Queues what can be saved — not songs already waiting, not files already on the phone — and says how many. */
    fun enqueue(songs: List<MediaMetadata>): Int {
        val fresh = songs.filter { it.id !in pending.value && !com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(it.id) }
        if (fresh.isEmpty()) return 0
        if (pending.value.isEmpty()) {
            queued.set(0); done.set(0); failed.set(0)
        }
        pending.value = pending.value + fresh.map { it.id }
        queued.addAndGet(fresh.size)
        fresh.forEach { queue.trySend(it) }
        return fresh.size
    }

    // ---- one song ------------------------------------------------------------------------------

    private suspend fun export(song: MediaMetadata) {
        val work = File(context.cacheDir, "export").apply { mkdirs() }
        val raw = File(work, "${song.id}.src")
        val muxed = File(work, "${song.id}.mux.m4a")
        val tagged = File(work, "${song.id}.m4a")
        try {
            val fromCache = fetchAac(song.id, raw, allowCache = true)
            try {
                remux(raw, muxed)
            } catch (error: Exception) {
                // The cache is keyed by song, not by stream: bytes recorded as AAC can belong to an
                // older resolution of the song. Fetching fresh is always the right second attempt.
                if (!fromCache) throw error
                Timber.tag(TAG).w(error, "Cached copy of ${song.id} didn't remux; fetching it")
                fetchAac(song.id, raw, allowCache = false)
                remux(raw, muxed)
            }
            raw.delete()

            val entity = database.getSongById(song.id)
            val year = entity?.song?.year ?: entity?.album?.year
            val artists = song.artists.map { it.name }.filter { it.isNotBlank() }
            Mp4Tagger.write(
                muxed, tagged,
                Mp4Tagger.Tags(
                    title = song.title,
                    artist = artists.joinToString(", ").ifBlank { null },
                    albumArtist = artists.firstOrNull(),
                    album = song.album?.title ?: entity?.song?.albumName,
                    year = year,
                    lyrics = lyricsFor(song),
                    cover = coverFor(song),
                ),
            )
            muxed.delete()

            val name = sanitize(listOfNotNull(artists.firstOrNull(), song.title).joinToString(" - "))
            SavedFiles.remember(context, song.id, publish(tagged, "$name.m4a"))
        } finally {
            raw.delete(); muxed.delete(); tagged.delete()
        }
    }

    /** The AAC stream into [into]: from a finished download or the play cache when it is AAC, else the network. */
    private suspend fun fetchAac(id: String, into: File, allowCache: Boolean): Boolean {
        val format = if (allowCache) database.format(id).first() else null
        if (format != null && format.mimeType == "audio/mp4" && format.contentLength > 0) {
            for (cache in listOf(downloadCache, playerCache)) {
                if (cache.isCached(id, 0, format.contentLength)) {
                    val source = CacheDataSource(cache, PlaceholderDataSource.INSTANCE)
                    val spec = DataSpec.Builder().setUri("exhale://$id".toUri()).setKey(id)
                        .setLength(format.contentLength).build()
                    val copied = runCatching {
                        source.open(spec)
                        into.outputStream().use { out -> pump(source::read, out) }
                    }.also { source.close() }
                    if (copied.isSuccess) return true
                }
            }
        }

        val streamClient = context.dataStore.get(PlayerStreamClientKey, PlayerStreamClient.ANDROID_VR.name)
            .let { runCatching { PlayerStreamClient.valueOf(it) }.getOrDefault(PlayerStreamClient.ANDROID_VR) }
        val playback = YTPlayerUtils.playerResponseForPlayback(
            videoId = id,
            audioQuality = AudioQuality.HIGHEST,
            connectivityManager = context.getSystemService<ConnectivityManager>()!!,
            preferredStreamClient = streamClient,
            networkMetered = false,
            avoidCodecs = setOf("opus"),
            preferredCodec = AudioCodec.AAC,
        ).getOrThrow()
        check(playback.format.mimeType.startsWith("audio/mp4")) { "No AAC stream for $id: ${playback.format.mimeType}" }
        val length = playback.format.contentLength ?: error("Unknown stream length")

        // Ranged requests: googlevideo throttles an open-ended GET to playback speed.
        into.outputStream().buffered(1 shl 16).use { out ->
            var at = 0L
            while (at < length) {
                val end = minOf(at + CHUNK - 1, length - 1)
                val request = Request.Builder().url(playback.streamUrl).header("Range", "bytes=$at-$end").build()
                downloadUtil.mediaOkHttpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val written = response.body.byteStream().use { input -> pump(input::read, out) }
                    check(written > 0) { "Empty range" }
                    at += written
                }
            }
        }
        return false
    }

    private inline fun pump(read: (ByteArray, Int, Int) -> Int, out: OutputStream): Long {
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return total
    }

    /**
     * YouTube's AAC is fragmented DASH MP4, which many players and every tag reader handle badly.
     * Android's own extractor and muxer turn it into a plain MP4, sample for sample, no decode.
     */
    private fun remux(source: File, target: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val out = muxer.addTrack(format)
                muxer.start()
                val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
                } else 256 * 1024
                val buffer = ByteBuffer.allocate(size)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val n = extractor.readSampleData(buffer, 0)
                    if (n < 0) break
                    info.set(0, n, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(out, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
    }

    private suspend fun lyricsFor(song: MediaMetadata): String? {
        val stored = database.getLyricsById(song.id)?.lyrics
        val text = stored?.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }
            ?: withTimeoutOrNull(20_000) { runCatching { lyricsHelper.getLyrics(song) }.getOrNull() }
                ?.takeUnless { it.isBlank() || it == LyricsEntity.LYRICS_NOT_FOUND }
            ?: return null
        return toLrc(text)
    }

    /** Whatever form the lyrics came in, as line-level LRC — or plain text when they were never synced. */
    private fun toLrc(lyrics: String): String {
        val entries = when {
            LyricsUtils.isTtml(lyrics) -> LyricsUtils.parseTtml(lyrics)
            lyrics.contains(LRC_TIME) -> LyricsUtils.parseLyrics(lyrics)
            else -> return lyrics.trim()
        }
        if (entries.isEmpty()) return lyrics.trim()
        return entries.joinToString("\n") { entry ->
            val ms = entry.time.coerceAtLeast(0)
            val text = entry.text.replace(WORD_TIME, "").trim()
            "[%02d:%02d.%02d]%s".format(ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10, text)
        }
    }

    /** The cover as a square 1200px JPEG, cropped to the middle when YouTube only has a 16:9 frame. */
    private fun coverFor(song: MediaMetadata): ByteArray? = runCatching {
        val url = song.thumbnailUrl?.resize(1200, 1200) ?: return null
        val bytes = downloadUtil.mediaOkHttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { if (it.isSuccessful) it.body.bytes() else null } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** Into Music/Exhale through MediaStore — no storage permission, and it shows up in every player at once. */
    private fun publish(file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Exporting the same song again replaces our earlier file instead of piling up "(1)" copies.
        existing(displayName)?.let { uri ->
            val replaced = runCatching {
                resolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            }.isSuccess
            if (replaced) return uri
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused the file")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private fun existing(displayName: String): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
            arrayOf(RELATIVE_PATH, displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trimEnd('.').take(150).ifBlank { "Untitled" }

    // ---- notification --------------------------------------------------------------------------

    private fun channel() {
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.export_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun progress(song: MediaMetadata) {
        channel()
        val total = queued.get()
        val position = done.get() + failed.get() + 1
        notify(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.exhale_monochrome)
                .setContentTitle(context.getString(R.string.export_progress_title, position, total))
                .setContentText(song.title)
                .setProgress(total, position - 1, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun finished() {
        val ok = done.get()
        val bad = failed.get()
        notify(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.exhale_monochrome)
                .setContentTitle(context.resources.getQuantityString(R.plurals.export_done_title, ok, ok))
                .setContentText(
                    if (bad > 0) context.resources.getQuantityString(R.plurals.export_failed_text, bad, bad)
                    else context.getString(R.string.export_done_text),
                )
                .setAutoCancel(true)
                .build(),
        )
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun songExporter(): SongExporter
    }

    companion object {
        private const val TAG = "SongExporter"
        private const val CHANNEL_ID = "exhale_export"
        private const val NOTIFICATION_ID = 7_401
        private const val CHUNK = 4L * 1024 * 1024
        const val RELATIVE_PATH = "Music/Exhale/"
        private val LRC_TIME = Regex("""\[\d{1,3}:\d{2}([.:]\d{1,3})?]""")
        private val WORD_TIME = Regex("""<\d{1,3}:\d{2}([.:]\d{1,3})?>""")

        fun get(context: Context): SongExporter =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).songExporter()
    }
}
