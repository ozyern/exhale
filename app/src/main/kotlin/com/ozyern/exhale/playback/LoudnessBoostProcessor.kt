/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp

/**
 * The half of loudness normalisation that makes a quiet song louder.
 *
 * Normalisation used to be one number on the player's volume, and the player clamps its volume to
 * 1: a loud master was brought down, but a quiet one was never brought up, so the songs still
 * jumped in level from one to the next. The turning-down stays on the volume; the turning-up is
 * done here, in float, with a peak limiter just under full scale so a boosted song never clips.
 *
 * At unity gain the audio is copied untouched: the limiter is only there for the boost, and a loud
 * master is never squashed for nothing. A change of gain (a new song) is ramped across the buffer,
 * so it never clicks. Inactive on anything but float PCM.
 */
@UnstableApi
class LoudnessBoostProcessor(private val boost: () -> Float) : BaseAudioProcessor() {

    private var gain = 1f
    private var reduction = 1f
    private var releaseCoefficient = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        // The limiter lets go over about 80 ms: quick enough not to pump, slow enough not to distort.
        releaseCoefficient = exp(-1.0 / (RELEASE_SECONDS * inputAudioFormat.sampleRate)).toFloat()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val bytes = inputBuffer.remaining()
        if (bytes == 0 || channels <= 0) return
        val frames = bytes / (4 * channels)
        val out = replaceOutputBuffer(bytes)
        val target = boost().coerceIn(1f, MAX_BOOST)

        if (target == 1f && gain == 1f && reduction == 1f) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val start = gain
        val frame = FloatArray(channels)
        for (f in 0 until frames) {
            val g = start + (target - start) * (f + 1) / frames
            var peak = 0f
            for (c in 0 until channels) {
                val s = inputBuffer.float * g
                frame[c] = s
                val a = abs(s)
                if (a > peak) peak = a
            }
            // Instant attack, smooth release: the gain needed to keep this frame under the ceiling.
            val needed = if (peak > CEILING) CEILING / peak else 1f
            reduction = if (needed < reduction) needed else needed + (reduction - needed) * releaseCoefficient
            for (c in 0 until channels) out.putFloat(frame[c] * reduction)
        }
        // Whatever the buffer did not divide into whole frames passes through as it was.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        gain = target
        if (target == 1f && reduction > 0.9999f) reduction = 1f
        out.flip()
    }

    override fun onReset() {
        gain = 1f
        reduction = 1f
    }

    companion object {
        /** +6 dB: as far as a quiet master is ever lifted. */
        const val MAX_BOOST = 1.995f

        /** -0.3 dBFS, under the inter-sample peaks a codec adds back. */
        private const val CEILING = 0.966f
        private const val RELEASE_SECONDS = 0.08
    }
}
