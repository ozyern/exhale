/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import java.io.File
import kotlin.concurrent.thread

/**
 * The slice of the libmpv client API (mpv/client.h) the desktop player needs.
 *
 * libmpv is what ExoPlayer is on Android: it decodes both of YouTube's audio renditions
 * (Opus in WebM, AAC in MP4) straight off an HTTPS URL, so no Media3 port is needed.
 */
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
}

/**
 * [windowId] is the native handle of a window to draw video into. Null — the usual case — keeps mpv
 * audio-only with no video decoder at all. It can't be changed after mpv starts, so switching a song
 * to video means building a second Mpv against the video surface; see DesktopPlayer.
 */
/** Somewhere to keep a stream while it plays; emptied by the operating system, not by us. */
private val streamCacheDir: String by lazy {
    java.io.File(System.getProperty("java.io.tmpdir"), "exhale-stream-cache")
        .apply { mkdirs() }
        .absolutePath
}

class Mpv(private val listener: Listener, windowId: Long? = null, extraOptions: Map<String, String> = emptyMap()) : AutoCloseable {
    interface Listener {
        fun onFileLoaded() {}
        fun onEndOfFile() {}
        fun onError(message: String) {}
        fun onPositionChanged(seconds: Double) {}
        fun onDurationChanged(seconds: Double) {}
        fun onPauseChanged(paused: Boolean) {}
    }

    private val lib: LibMpv = loadLibrary()
    private val ctx: Pointer = lib.mpv_create() ?: error("mpv_create returned null")

    @Volatile private var running = true

    /** A video's separate sound track, waiting for the picture to open. */
    @Volatile private var pendingAudio: String? = null

    /**
     * loadfiles sent since mpv last began a file, and that file's playlist entry. An error from any
     * other entry, or while a newer load hasn't started yet, is stale: reporting it would mark the new
     * song failed and auto-skip past it, which is how a burst of quick song changes cascaded into
     * "nothing to play" errors. (Counting loads against starts instead never caught up when mpv
     * replaced a file before beginning it, and from then on every real error went unreported.)
     */
    @Volatile private var loadsPending = 0
    @Volatile private var startedEntry = -1L
    private val eventThread: Thread

    init {
        // Audio only: never open a video window, never shell out to yt-dlp — stream URLs
        // are resolved by innertube before they reach mpv.
        buildMap {
            if (windowId != null) {
                // Draw into the surface the player put on screen, hardware-decoded, letterboxed.
                put("wid", windowId.toString())
                put("vid", "auto")
                put("video", "auto")
                put("vo", "gpu")
                put("hwdec", "auto-safe")
                put("keepaspect", "yes")
                put("video-unscaled", "no")
            } else {
                put("vid", "no")
                put("video", "no")
            }
            putAll(audioOnlyOptions)
            // Development aid: EXHALE_MPV_LOG=<file> keeps mpv's own log, for when playback fails.
            System.getenv("EXHALE_MPV_LOG")?.let { put("log-file", it) }
            // Anything else a caller needs set before mpv starts (tests encode through it, for one).
            putAll(extraOptions)
        }.forEach { (name, value) -> lib.mpv_set_option_string(ctx, name, value) }

        check(lib.mpv_initialize(ctx) >= 0) { "mpv_initialize failed" }

        // time-pos is read, not observed: observed, mpv reports it for every decoded audio frame, each
        // report a round trip through JNA and the player's state. See eventLoop.
        lib.mpv_observe_property(ctx, 0, "duration", FORMAT_DOUBLE)
        lib.mpv_observe_property(ctx, 0, "pause", FORMAT_FLAG)

        eventThread = thread(name = "mpv-events", isDaemon = true) { eventLoop() }
    }

    private val audioOnlyOptions: Map<String, String>
        get() = mapOf(
            "audio-display" to "no",
            "ytdl" to "no",
            "idle" to "yes",
            "terminal" to "no",
            "input-default-bindings" to "no",
            // Keep what has been read, on disk, and seek within it.
            //
            // YouTube's best renditions come from a client whose URLs are served strictly in order:
            // ask for a piece from the middle and the answer is 403, however it's asked. Every seek
            // would otherwise be a request from somewhere new, and every one of those is refused —
            // which is why the picture stopped the moment the slider moved. With the stream kept
            // here as it arrives, a seek reads from what's already down rather than asking again.
            "cache" to "yes",
            "cache-on-disk" to "yes",
            "cache-dir" to streamCacheDir,
            // Read ahead by the clock, not to the end of the file. Given room for the whole video mpv
            // takes it at once, several megabytes a second — and pulled that hard these URLs stop
            // answering partway through, for minutes, which ends the song mid-play. A minute and a
            // half in hand covers any slow moment on the network and keeps the asking near the pace
            // the picture is actually watched at.
            "demuxer-readahead-secs" to "30",
            "cache-secs" to "90",
            "demuxer-max-bytes" to (96L * 1024 * 1024).toString(),
            "demuxer-max-back-bytes" to (128L * 1024 * 1024).toString(),
            // Opus through libopus, Xiph's reference decoder, ahead of ffmpeg's own; anything else as before.
            "ad" to "libopus",
            // The last buffer of one song runs straight into the next, with no reopened device between.
            "gapless-audio" to "weak",
            // Volume is only ever turned down (normalization, fades), so the path stays clean at 100%.
            "volume-max" to "100",
            // WASAPI, named: shared mode goes through Windows' own processing (Dolby Atmos, Sonic),
            // exclusive mode can bypass it, and the volume mixer shows "Exhale" rather than "mpv".
            "ao" to "wasapi",
            "audio-client-name" to "Exhale",
        )

    /**
     * mpv's `start` applies to whatever file opens next, so it is set on every load — a number to begin
     * there, "none" to begin at the top. Opening at the place, rather than at the start and seeking once
     * loaded, is what keeps a picture reloaded at a new quality from flashing its first scene.
     */
    private fun setStart(startSeconds: Double?) {
        val value = startSeconds?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "none"
        lib.mpv_set_property_string(ctx, "start", value)
    }

    fun load(url: String, headers: StreamHeaders, startSeconds: Double? = null) {
        setStart(startSeconds)
        lib.mpv_set_property_string(ctx, "user-agent", headers.userAgent)
        lib.mpv_set_property_string(ctx, "referrer", headers.referer.orEmpty())
        // http-header-fields is a comma-separated list, so only comma-free values go here.
        lib.mpv_set_property_string(ctx, "http-header-fields", headers.origin?.let { "Origin: $it" }.orEmpty())
        lib.mpv_set_property_string(ctx, "pause", "no")
        loadsPending++
        if (!command("loadfile", url, "replace")) loadsPending--
    }

    /**
     * A video, whose sound is usually a stream of its own: YouTube's only rendition carrying both is
     * 360p. The sound is added once the picture has loaded — audio-add needs a file already open, and
     * the URL can't ride along in loadfile's options, which are comma-separated like the URLs are.
     */
    fun loadVideo(videoUrl: String, audioUrl: String?, headers: StreamHeaders, startSeconds: Double? = null) {
        setStart(startSeconds)
        lib.mpv_set_property_string(ctx, "user-agent", headers.userAgent)
        lib.mpv_set_property_string(ctx, "referrer", headers.referer.orEmpty())
        lib.mpv_set_property_string(ctx, "http-header-fields", headers.origin?.let { "Origin: $it" }.orEmpty())
        lib.mpv_set_property_string(ctx, "pause", "no")
        pendingAudio = audioUrl
        loadsPending++
        if (!command("loadfile", videoUrl, "replace")) loadsPending--
    }

    /** The separate sound track for the picture that just loaded, if there is one. */
    private fun attachPendingAudio() {
        val url = pendingAudio ?: return
        pendingAudio = null
        command("audio-add", url, "select")
    }

    /** Flips mpv's own pause flag, whatever the UI last believed it was. */
    fun togglePause() {
        command("cycle", "pause")
    }

    fun setPaused(paused: Boolean) {
        lib.mpv_set_property_string(ctx, "pause", if (paused) "yes" else "no")
    }

    fun seekTo(seconds: Double) {
        command("seek", seconds.toString(), "absolute")
    }

    fun setVolume(percent: Int) {
        lib.mpv_set_property_string(ctx, "volume", percent.coerceIn(0, 100).toString())
    }

    fun setVolume(percent: Double) {
        lib.mpv_set_property_string(ctx, "volume", String.format(java.util.Locale.US, "%.2f", percent.coerceIn(0.0, 100.0)))
    }

    /** Any mpv property by name (`af`, `speed`, `volume-gain`, `http-proxy`…). False if mpv refused it. */
    fun setProperty(name: String, value: String): Boolean = lib.mpv_set_property_string(ctx, name, value) >= 0

    fun stop() {
        command("stop")
    }

    /** Any property as mpv formats it (`audio-out-params/channel-count`…), or null if it has none right now. */
    fun getProperty(name: String): String? {
        val data = lib.mpv_get_property_string(ctx, name) ?: return null
        return try {
            data.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(data)
        }
    }

    /** Reopens the audio device, so channel layout and exclusive mode changes apply mid-song. */
    fun reloadAudioOutput() {
        lib.mpv_command(ctx, arrayOf("ao-reload"))
    }

    /** Runs an mpv command; a refusal is reported to the listener and returned as false. */
    private fun command(vararg args: String): Boolean {
        val result = lib.mpv_command(ctx, arrayOf(*args))
        if (result < 0) listener.onError("mpv ${args.first()}: ${lib.mpv_error_string(result)}")
        return result >= 0
    }

    /**
     * mpv's events, and the playback position. While a song plays the loop wakes five times a second
     * to read where it is; paused or idle, it sleeps until mpv has something to say.
     */
    private fun eventLoop() {
        var loaded = false
        var paused = false
        var lastPosition = Double.NaN
        while (running) {
            val event = lib.mpv_wait_event(ctx, if (loaded && !paused) PositionPollSeconds else -1.0)
            when (event.getInt(0)) {
                EVENT_SHUTDOWN -> return
                EVENT_START_FILE -> {
                    // mpv_event_start_file: the playlist entry's id.
                    startedEntry = event.getPointer(16)?.getLong(0) ?: -1L
                    loadsPending = 0
                }
                EVENT_FILE_LOADED -> {
                    loaded = true
                    attachPendingAudio()
                    listener.onFileLoaded()
                }
                EVENT_END_FILE -> {
                    loaded = false
                    // mpv_event_end_file { int reason; int error; ... }
                    val data = event.getPointer(16)
                    when (data?.getInt(0)) {
                        END_REASON_EOF -> listener.onEndOfFile()
                        END_REASON_ERROR -> if (loadsPending == 0 && data!!.getLong(8) == startedEntry) listener.onError("Playback failed: ${lib.mpv_error_string(data!!.getInt(4))}")
                    }
                }
                EVENT_PROPERTY_CHANGE -> {
                    // mpv_event_property { const char* name; mpv_format format; void* data; }
                    val property = event.getPointer(16)
                    val format = property?.getInt(8)
                    val value = property?.getPointer(16)
                    if (property != null && value != null) {
                        when (property.getPointer(0).getString(0)) {
                            "duration" -> if (format == FORMAT_DOUBLE) listener.onDurationChanged(value.getDouble(0))
                            "pause" -> if (format == FORMAT_FLAG) {
                                paused = value.getInt(0) != 0
                                listener.onPauseChanged(paused)
                            }
                        }
                    }
                }
            }
            if (loaded && !paused) {
                val position = position()
                if (position != null && position != lastPosition) {
                    lastPosition = position
                    listener.onPositionChanged(position)
                }
            }
        }
    }

    fun position(): Double? {
        val out = DoubleByReference()
        return if (lib.mpv_get_property(ctx, "time-pos", FORMAT_DOUBLE, out.pointer) >= 0) out.value else null
    }

    /**
     * How far into the stream has been fetched, as a timestamp. A seek beyond this has to be waited
     * for, and on a stream that is only served in order it cannot be jumped to at all — so the player
     * shows the listener where the picture actually reaches.
     */
    fun bufferedTo(): Double? {
        val out = DoubleByReference()
        return if (lib.mpv_get_property(ctx, "demuxer-cache-time", FORMAT_DOUBLE, out.pointer) >= 0) out.value else null
    }

    override fun close() {
        running = false
        lib.mpv_wakeup(ctx)
        eventThread.join(1_000)
        lib.mpv_terminate_destroy(ctx)
    }

    internal companion object {
        /** How often a playing song's position is read: often enough for progress, fades and the lyrics clock to steer by. */
        const val PositionPollSeconds = 0.2

        const val FORMAT_FLAG = 3
        const val FORMAT_DOUBLE = 5

        const val EVENT_SHUTDOWN = 1
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_REASON_EOF = 0
        const val END_REASON_ERROR = 4

        fun loadLibrary(): LibMpv {
            // Packaged app: Compose copies resources/windows into the install and points
            // compose.application.resources.dir at it. `gradlew :desktop:run` does the same
            // with a staging copy. The fallback covers running Main from the IDE.
            val searchPath = listOfNotNull(
                System.getProperty("compose.application.resources.dir"),
                File("desktop/resources/windows").absolutePath,
                File("resources/windows").absolutePath,
            ).joinToString(File.pathSeparator)
            System.setProperty("jna.library.path", searchPath)
            return Native.load("libmpv-2", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        }
    }
}
