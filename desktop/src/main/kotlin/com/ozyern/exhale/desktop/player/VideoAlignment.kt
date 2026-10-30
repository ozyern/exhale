/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The matching is the Android app's VersionAligner (itself after the reference design's
 * VersionAudioAligner, GPL-3.0); the decoding goes through libmpv instead of Android's codecs.
 */

package com.ozyern.exhale.desktop.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * How far into a music video its song begins: the length of the video's intro.
 *
 * The video is shown with the song's own sound, and the two used to start together, so a video with
 * a cold open, a spoken scene or a longer lead-in ran ahead of the music by all of it, and the lyrics,
 * which follow the same clock, ran wrong by the same amount. This finds where the song sits inside
 * the video by lining the two recordings' rises in loudness up (what any two cuts of one recording
 * share, whatever their mix), as the Android app does.
 *
 * Each recording's opening is decoded by a separate, silent libmpv, written as low-rate mono PCM as
 * fast as it decodes. Measured once per pair and kept.
 */
object VideoAlignment {
    private const val HOP_MS = 20
    private const val RATE = 8_000
    /** How much of the video is searched for the song's opening. */
    private const val VIDEO_SECONDS = 150
    /** How much of the song's opening is matched. */
    private const val SONG_SECONDS = 50
    /** The longest intro a video is assumed to have. */
    private const val MAX_LAG_SECONDS = 100
    /** The longest stretch of the song's own intro a video is assumed to cut. */
    private const val MAX_LEAD_SECONDS = 20
    private const val MIN_CORRELATION = 0.35
    /** The most a decode may take before it is given up. */
    private const val DECODE_TIMEOUT_SECONDS = 45.0

    private val known = ConcurrentHashMap<String, Long>()

    /** The offset already measured for this pair, in milliseconds, if it has been. */
    fun known(videoId: String, songId: String): Long? = known["$videoId|$songId"]

    /**
     * Milliseconds into the video the song begins (negative: the video starts that far into the song),
     * or null when the two cannot be lined up with confidence.
     */
    suspend fun offsetMs(
        videoId: String,
        songId: String,
        videoSound: String,
        videoHeaders: StreamHeaders,
        songSound: String,
        songHeaders: StreamHeaders,
    ): Long? = withContext(Dispatchers.IO) {
        if (videoId == songId) return@withContext 0L
        known(videoId, songId)?.let { return@withContext it }
        val video = decode(videoSound, videoHeaders, VIDEO_SECONDS) ?: return@withContext null
        val song = decode(songSound, songHeaders, SONG_SECONDS) ?: return@withContext null
        match(video, song)?.also { known["$videoId|$songId"] = it }
    }

    /** Lines the two openings up; see the Android app's VersionAligner. */
    internal fun match(videoPcm: FloatArray, songPcm: FloatArray): Long? {
        val ve = onsetEnvelope(videoPcm)
        val se = onsetEnvelope(songPcm)
        if (ve.size < 50 || se.size < 50) return null
        val maxLag = minOf(MAX_LAG_SECONDS * 1000 / HOP_MS, ve.size - se.size / 2)
        val maxLead = (MAX_LEAD_SECONDS * 1000 / HOP_MS).coerceAtMost(se.size / 2)
        var bestLag = 0
        var best = -1.0
        for (lag in -maxLead..maxLag.coerceAtLeast(0)) {
            val videoStart = maxOf(lag, 0)
            val songStart = maxOf(-lag, 0)
            val n = minOf(se.size - songStart, ve.size - videoStart)
            if (n < se.size / 2) {
                if (lag >= 0) break else continue
            }
            var dot = 0.0
            var a = 0.0
            var b = 0.0
            for (i in 0 until n) {
                val x = ve[videoStart + i]
                val y = se[songStart + i]
                dot += x * y
                a += x * x
                b += y * y
            }
            val c = if (a > 0 && b > 0) dot / sqrt(a * b) else 0.0
            if (c > best) {
                best = c
                bestLag = lag
            }
        }
        return if (best >= MIN_CORRELATION) bestLag.toLong() * HOP_MS else null
    }

    /** Rises in loudness, every [HOP_MS]. */
    private fun onsetEnvelope(samples: FloatArray): DoubleArray {
        val hop = RATE * HOP_MS / 1000
        val frames = samples.size / hop
        val energy = DoubleArray(frames) { f ->
            var sum = 0.0
            val start = f * hop
            for (i in start until start + hop) sum += samples[i] * samples[i]
            ln(1e-6 + sum / hop)
        }
        val onset = DoubleArray(frames) { f -> if (f == 0) 0.0 else (energy[f] - energy[f - 1]).coerceAtLeast(0.0) }
        val mean = onset.average()
        return DoubleArray(frames) { onset[it] - mean }
    }

    /**
     * The first [seconds] of [url]'s sound as mono samples at [RATE]: a libmpv of its own with no
     * picture and no speakers, writing PCM to a temporary file as fast as it can decode.
     */
    private fun decode(url: String, headers: StreamHeaders, seconds: Int): FloatArray? {
        val out = File.createTempFile("exhale-align-", ".pcm")
        // Every way out deletes it: a failed start used to leave one behind in the temp folder.
        return try {
            decodeInto(out, url, headers, seconds)
        } finally {
            out.delete()
        }
    }

    private fun decodeInto(out: File, url: String, headers: StreamHeaders, seconds: Int): FloatArray? {
        val lib = runCatching { Mpv.loadLibrary() }.getOrNull() ?: return null
        val ctx = lib.mpv_create() ?: return null
        try {
            fun opt(name: String, value: String) = lib.mpv_set_option_string(ctx, name, value)
            opt("vid", "no")
            opt("sid", "no")
            opt("ao", "pcm")
            opt("ao-pcm-file", out.absolutePath)
            opt("ao-pcm-waveheader", "no")
            opt("untimed", "yes")
            opt("audio-samplerate", RATE.toString())
            opt("audio-channels", "mono")
            opt("audio-format", "s16")
            opt("length", seconds.toString())
            opt("terminal", "no")
            opt("msg-level", "all=no")
            opt("user-agent", headers.userAgent)
            opt("referrer", headers.referer.orEmpty())
            opt("http-header-fields", headers.origin?.let { "Origin: $it" }.orEmpty())
            if (lib.mpv_initialize(ctx) < 0) return null
            if (lib.mpv_command(ctx, arrayOf("loadfile", url, "replace")) < 0) return null
            val deadline = System.nanoTime() + (DECODE_TIMEOUT_SECONDS * 1e9).toLong()
            while (System.nanoTime() < deadline) {
                val event = lib.mpv_wait_event(ctx, 1.0)
                when (event.getInt(0)) {
                    Mpv.EVENT_END_FILE, Mpv.EVENT_SHUTDOWN -> break
                }
            }
        } finally {
            lib.mpv_terminate_destroy(ctx)
        }
        val bytes = out.readBytes()
        if (bytes.size < RATE * 2 * 5) return null
        return FloatArray(bytes.size / 2) { i ->
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            ((hi shl 8) or lo).toShort() / 32768f
        }
    }
}
