package com.ozyern.exhale.playback.smart

import android.content.Context
import android.net.Uri
import kotlin.math.sqrt

/** Exact per-point vocal activity measured from a song's separated vocal stem. */
internal object StemVocalMask {
    private const val ACTIVE_FLOOR = 0.04
    private const val ACTIVE_SPAN = 0.2
    private const val MIN_HALF_WINDOW = 0.5
    private const val MAX_HALF_WINDOW = 1.5

    /** Loudness per point to 0..1 activity, relative to how loud this track's vocals get. */
    fun fromLoudness(loudness: DoubleArray): DoubleArray {
        val sorted = loudness.filter { it > 0.0 }.sorted()
        if (sorted.isEmpty()) return DoubleArray(loudness.size)
        val reference = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
        if (reference <= 1e-6) return DoubleArray(loudness.size)
        return DoubleArray(loudness.size) { ((loudness[it] / reference - ACTIVE_FLOOR) / ACTIVE_SPAN).coerceIn(0.0, 1.0) }
    }

    /** RMS of [samples] in a window centred on each of the ascending [times] (seconds). */
    fun loudnessAt(samples: FloatArray, rate: Double, times: DoubleArray, halfWindow: Double): DoubleArray =
        DoubleArray(times.size) { index ->
            val from = ((times[index] - halfWindow) * rate).toInt().coerceIn(0, samples.size)
            val to = ((times[index] + halfWindow) * rate).toInt().coerceIn(from, samples.size)
            if (to == from) 0.0 else {
                var sum = 0.0
                for (i in from until to) sum += samples[i].toDouble() * samples[i]
                sqrt(sum / (to - from))
            }
        }

    /** A mask aligned to [curve], or null when the song has no vocal stem or it can't be read. */
    fun forTrack(context: Context, trackId: String, curve: List<EnergySample>): List<Double>? {
        if (curve.size < 2) return null
        val stem = StemStore.vocals(context, trackId).takeIf { it.exists() } ?: return null
        val pcm = UriMediaDataSource.open(context, Uri.fromFile(stem))?.use { source ->
            AudioDecoder.decodeRegion(source, 0.0, curve.last().time + MAX_HALF_WINDOW)?.first
        } ?: return null
        val times = DoubleArray(curve.size) { curve[it].time }
        val spacing = (times.last() - times.first()) / (times.size - 1)
        val half = (spacing / 2).coerceIn(MIN_HALF_WINDOW, MAX_HALF_WINDOW)
        return fromLoudness(loudnessAt(pcm.samples, pcm.sampleRate, times, half)).toList()
    }
}
