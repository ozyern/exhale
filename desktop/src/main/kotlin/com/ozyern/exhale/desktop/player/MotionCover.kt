/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Moving covers, as the Android app shows them (the :canvas module's Apple Music and Tidal lookups).
 * A bundled FFmpeg is the usual way to decode motion artwork; here the libmpv the player
 * already ships renders it, into memory, through mpv's software render API.
 */

package com.ozyern.exhale.desktop.player

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.ozyern.exhale.canvas.providers.AppleMusicCanvasProvider
import com.ozyern.exhale.canvas.providers.TidalCanvasProvider
import com.ozyern.exhale.desktop.lyrics.SongQuery
import com.ozyern.exhale.innertube.models.SongItem
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Where a song's moving cover is, if it has one. Asked once a song, remembered for the session. */
object MotionCovers {
    private val known = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun find(song: SongItem): String? {
        known[song.id]?.let { return it }
        if (song.id in missing) return null
        val title = SongQuery.cleanTitle(song.title)
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        if (title.isBlank() || artist.isBlank()) return null
        val url = withContext(Dispatchers.IO) {
            withTimeoutOrNull(15_000) {
                // Apple first, where most moving covers are; Tidal has some Apple doesn't.
                runCatching { AppleMusicCanvasProvider.getBySongArtist(title, artist, song.album?.name) }.getOrNull()
                    ?.preferredAnimationUrl?.takeIf { it.isNotBlank() }
                    ?: runCatching { TidalCanvasProvider.getBySongArtist(title, artist, song.album?.name) }.getOrNull()
                        ?.preferredAnimationUrl?.takeIf { it.isNotBlank() }
            }
        }
        val playable = url?.let { rendition(it) }
        if (playable == null) missing += song.id else known[song.id] = playable
        return playable
    }

    /**
     * The one rendition worth playing out of Apple's master playlist: H.264 (decoded in hardware
     * everywhere), the smallest that is still at least [MinimumSide] across. Handed the master
     * itself, FFmpeg opens every one of its dozens of playlists — 4K, HEVC, I-frame — one after
     * another before the first frame, which took twelve seconds.
     */
    private suspend fun rendition(master: String): String = withContext(Dispatchers.IO) {
        if (!master.contains(".m3u8")) return@withContext master
        val text = runCatching {
            val connection = java.net.URI(master).toURL().openConnection().apply {
                connectTimeout = 6_000
                readTimeout = 6_000
            }
            connection.getInputStream().use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return@withContext master
        val lines = text.lines()
        data class Variant(val uri: String, val side: Int, val bandwidth: Long)
        val variants = lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
            val codecs = Regex("""CODECS="([^"]+)"""").find(line)?.groupValues?.get(1).orEmpty()
            if (!codecs.contains("avc1")) return@mapIndexedNotNull null
            val (w, h) = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)?.destructured ?: return@mapIndexedNotNull null
            val bandwidth = Regex("""[^-]BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val uri = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@mapIndexedNotNull null
            Variant(java.net.URI(master).resolve(uri.trim()).toString(), minOf(w.toInt(), h.toInt()), bandwidth)
        }
        (variants.filter { it.side >= MinimumSide }.minByOrNull { it.side * 1_000_000_000L + it.bandwidth }
            ?: variants.maxByOrNull { it.side })?.uri ?: master
    }

    private const val MinimumSide = 600
}

@Suppress("FunctionName")
private interface LibMpvRender : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)
}

private fun interface UpdateCallback : Callback {
    fun invoke(data: Pointer?)
}

/**
 * One moving cover, looping silently, drawn into [frames] for Compose to show. A second, video-only
 * libmpv: it decodes in software and renders through mpv's `sw` render API straight into the pixels
 * of a Skia bitmap — no window of its own, so the glass and the rounded corners go over it like they
 * go over anything else. Three bitmaps take turns, so the one being drawn is never the one being
 * written. Close it when the cover leaves the screen.
 */
class MotionCoverRenderer(private val size: Int = 720, logFile: String? = null) : AutoCloseable {
    private val lib: LibMpvRender = Native.load("libmpv-2", LibMpvRender::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    private val mpv: Pointer = lib.mpv_create() ?: error("mpv_create returned null")
    private val render: Pointer

    private val info = ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    private val bitmaps = List(3) { Bitmap().apply { allocPixels(info) } }
    private val images: List<ImageBitmap> = bitmaps.map { it.asComposeImageBitmap() }

    /** Bumped for every new frame; read it while drawing [current] so each frame redraws. */
    val frames: MutableIntState = mutableIntStateOf(0)

    @Volatile private var front = 0

    /** The frame on show. Null until the first one is drawn. */
    val current: ImageBitmap? get() = if (frames.intValue == 0) null else images[front]

    @Volatile private var running = true
    private val wake = Semaphore(0)
    // Kept referenced for as long as mpv may call it; a collected callback crashes the process.
    private val onUpdate = UpdateCallback { wake.release() }
    private val renderThread: Thread

    init {
        mapOf(
            "vo" to "libmpv",
            "aid" to "no",
            "audio" to "no",
            "mute" to "yes",
            "loop-file" to "inf",
            "hwdec" to "auto-copy",
            "ytdl" to "no",
            "idle" to "yes",
            "terminal" to "no",
            "input-default-bindings" to "no",
            // Fill the square, cropping rather than letterboxing, as the still cover does.
            "keepaspect" to "yes",
            "panscan" to "1.0",
            // Apple's playlists go up to 4K HEVC; a cover a few hundred pixels across needs nothing like it,
            // and the top rendition is what made the first second play and the rest stall.
            "hls-bitrate" to "3000000",
            "cache" to "yes",
            "demuxer-max-bytes" to (24L * 1024 * 1024).toString(),
        ).forEach { (name, value) -> lib.mpv_set_option_string(mpv, name, value) }
        if (logFile != null) lib.mpv_set_option_string(mpv, "log-file", logFile)
        check(lib.mpv_initialize(mpv) >= 0) { "mpv_initialize failed" }

        val apiType = Memory(3).apply { setString(0, "sw", "US-ASCII") }
        val params = Memory(32).apply {
            clear()
            setInt(0, PARAM_API_TYPE)
            setPointer(8, apiType)
            setInt(16, PARAM_INVALID)
        }
        val out = PointerByReference()
        check(lib.mpv_render_context_create(out, mpv, params) >= 0) { "mpv_render_context_create failed" }
        render = out.value
        lib.mpv_render_context_set_update_callback(render, onUpdate, null)
        renderThread = thread(name = "motion-cover", isDaemon = true) { renderLoop() }
    }

    fun play(url: String) {
        lib.mpv_command(mpv, arrayOf("loadfile", url, "replace", null))
    }

    fun setPaused(paused: Boolean) {
        lib.mpv_set_property_string(mpv, "pause", if (paused) "yes" else "no")
    }

    private fun renderLoop() {
        val sizeParam = Memory(8).apply { setInt(0, size); setInt(4, size) }
        val format = Memory(5).apply { setString(0, "bgr0", "US-ASCII") }
        val stride = Memory(8)
        val params = Memory(16L * 5).apply { clear() }
        var back = 1
        while (running) {
            if (!wake.tryAcquire(250, TimeUnit.MILLISECONDS)) continue
            if (!running) break
            wake.drainPermits()
            val flags = lib.mpv_render_context_update(render)
            if (flags and UPDATE_FRAME == 0L) continue
            val bitmap = bitmaps[back]
            val pixels = bitmap.peekPixels() ?: continue
            stride.setLong(0, bitmap.rowBytes.toLong())
            params.setInt(0, PARAM_SW_SIZE); params.setPointer(8, sizeParam)
            params.setInt(16, PARAM_SW_FORMAT); params.setPointer(24, format)
            params.setInt(32, PARAM_SW_STRIDE); params.setPointer(40, stride)
            params.setInt(48, PARAM_SW_POINTER); params.setPointer(56, Pointer(pixels.addr))
            params.setInt(64, PARAM_INVALID); params.setPointer(72, null)
            if (lib.mpv_render_context_render(render, params) < 0) continue
            bitmap.notifyPixelsChanged()
            front = back
            back = (back + 1) % bitmaps.size
            if (back == front) back = (back + 1) % bitmaps.size
            frames.intValue++
        }
    }

    override fun close() {
        running = false
        wake.release()
        renderThread.join(1_000)
        // The render context goes before the core, as the API requires.
        lib.mpv_render_context_set_update_callback(render, null, null)
        lib.mpv_render_context_free(render)
        lib.mpv_terminate_destroy(mpv)
        bitmaps.forEach { it.close() }
    }

    private companion object {
        const val PARAM_INVALID = 0
        const val PARAM_API_TYPE = 1
        const val PARAM_SW_SIZE = 17
        const val PARAM_SW_FORMAT = 18
        const val PARAM_SW_STRIDE = 19
        const val PARAM_SW_POINTER = 20
        const val UPDATE_FRAME = 1L

        init {
            // The same search path the player's own libmpv is loaded from.
            if (System.getProperty("jna.library.path").isNullOrBlank()) {
                System.setProperty(
                    "jna.library.path",
                    listOfNotNull(
                        System.getProperty("compose.application.resources.dir"),
                        File("desktop/resources/windows").absolutePath,
                        File("resources/windows").absolutePath,
                    ).joinToString(File.pathSeparator),
                )
            }
        }
    }
}
