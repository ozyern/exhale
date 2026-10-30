/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The idea is the reference design's VersionAudioAligner (github.com/kushagrasinghx/the reference design, GPL-3.0): find
 * where a song sits inside another cut of it — a music video with an intro — by lining the two
 * recordings' loudness up, rather than trusting their lengths.
 */

package com.ozyern.exhale.playback.automix

import android.media.MediaDataSource
import kotlin.math.ln
import kotlin.math.sqrt

object VersionAligner {
    private const val HOP_MS = 20
    /** How much of the video is searched for the song's opening. */
    private const val VIDEO_SECONDS = 150.0
    /** How much of the song's opening is matched. */
    private const val SONG_SECONDS = 50.0
    /** The longest intro a video is assumed to have. */
    private const val MAX_LAG_SECONDS = 100.0
    private const val MIN_CORRELATION = 0.35
    /** The longest stretch of the song's own intro a video is assumed to cut. */
    private const val MAX_LEAD_SECONDS = 20.0

    /**
     * How many milliseconds into [video] the song in [song] begins, or null when the two cannot be
     * lined up with confidence. Positive: the video plays that long before the song starts.
     * Negative: the video starts that far into the song (it cuts the song's intro).
     */
    fun offsetMs(video: MediaDataSource, song: MediaDataSource): Long? {
        if (!TrackFeatures.available) return null
        val rate = TrackFeatures.sampleRate
        val resample: (FloatArray, Double, Double) -> FloatArray? = { s, from, to -> TrackFeatures.resample(s, from, to) }
        val v = video.use { AudioDecoder.decodeRegionAt(it, 0.0, VIDEO_SECONDS, rate, resample) }?.first ?: return null
        val s = song.use { AudioDecoder.decodeRegionAt(it, 0.0, SONG_SECONDS, rate, resample) }?.first ?: return null
        val ve = onsetEnvelope(v.samples, v.sampleRate)
        val se = onsetEnvelope(s.samples, s.sampleRate)
        if (ve.size < 50 || se.size < 50) return null

        val maxLag = minOf((MAX_LAG_SECONDS * 1000 / HOP_MS).toInt(), ve.size - se.size / 2)
        // The other way too: a video that cuts in after the song's own intro starts partway into
        // the song. Only later starts were searched, so such a video was matched to the wrong place
        // (or not at all) and its picture and lyrics ran late.
        val maxLead = (MAX_LEAD_SECONDS * 1000 / HOP_MS).toInt().coerceAtMost(se.size / 2)
        var bestLag = 0
        var best = -1.0
        for (lag in -maxLead..maxLag.coerceAtLeast(0)) {
            // lag >= 0: the song's start is [lag] hops into the video. lag < 0: the video's start is
            // -[lag] hops into the song.
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

    /** Rises in loudness, every [HOP_MS]: what two cuts of one recording share, whatever their mix. */
    private fun onsetEnvelope(samples: FloatArray, rate: Double): DoubleArray {
        val hop = (rate * HOP_MS / 1000).toInt().coerceAtLeast(1)
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
}
