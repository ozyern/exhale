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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Spatial-audio upscaler inspired by the open-source Cavern engine
 * (github.com/VoidXH/Cavern). Cavern itself is a C#/.NET renderer, so its core
 * headphone-virtualizer signal path is ported here as a native Media3 [BaseAudioProcessor].
 * It runs in [PrecisionAudioSink]'s pipeline, ahead of the sink.
 *
 * ## The signal stays what the decoder made
 *
 *  - **Rate.** The song keeps its own sample rate. This stage used to lift 44.1 kHz to 48 kHz with
 *    a linear interpolator, on every song and with the effect off too, and a linear interpolator
 *    rolls off the top octave and folds images back into the audible band. That was most of
 *    YouTube's AAC and every JioSaavn copy. The Android mixer's resampler, where one is needed at
 *    all, is far better than anything that cheap.
 *  - **Encoding.** Float in is float out, 16-bit in is 16-bit out. It used to emit 16-bit always,
 *    because Media3's own chain once ran Sonic after it and Sonic takes nothing else. Nothing runs
 *    after it but Exhale's own float-capable processors now, so headphones get float all the way.
 *  - **Off is off.** With [globalEnabled] off the bytes are copied through untouched.
 *
 * The three virtualization stages, reduced to first-order sections so the whole thing costs
 * ~20 flops per sample:
 *
 *  1. **Mid/side width expansion** — widens the stereo image the way Cavern's
 *     object spreader pushes sources away from the phantom center.
 *  2. **Low-passed, delayed crossfeed** (Bauer/BS2B-style, ~350 µs Haas delay
 *     at ~700 Hz) — the interaural time/level cues Cavern's HRTF path produces,
 *     which is what makes headphone playback read as "outside the head".
 *  3. **Height/air shelf** — a gentle high-band emphasis standing in for
 *     Cavern's height-channel virtualization, lifting the image vertically the
 *     way Atmos height beds do.
 *
 * All state is pre-allocated in [onConfigure]; [queueInput] allocates nothing.
 */
@UnstableApi
class CavernSpatialAudioProcessor : BaseAudioProcessor() {

    companion object {
        /**
         * Runtime toggle, driven from DataStore ([com.ozyern.exhale.constants.SpatialAudioKey])
         * by MusicService. @Volatile so the audio thread always sees the latest
         * value; checked once per buffer, so toggling costs nothing.
         * Defaults to true — spatial audio ships enabled out of the box.
         */
        @Volatile
        var globalEnabled: Boolean = true

        // Tuning, live. These were constants; they are the three numbers the stage is made of,
        // so the profile setting writes them here and the next buffer picks them up.
        @Volatile private var WIDTH = 1.35f      // mid/side expansion factor
        @Volatile private var CROSSFEED = 0.38f  // opposite-ear feed level
        @Volatile private var AIR = 0.12f        // height/air shelf gain

        /** Applies a stage size. Called from MusicService when the setting changes. */
        fun applyProfile(width: Float, crossfeed: Float, air: Float) {
            WIDTH = width
            CROSSFEED = crossfeed
            AIR = air
        }
        private const val MAKEUP = 0.82f         // headroom so widening never clips
        private const val CROSSFEED_CUTOFF_HZ = 700f
        private const val AIR_CUTOFF_HZ = 7_000f
        private const val HAAS_DELAY_SECONDS = 0.00035f

        /** Full-scale for 16-bit samples. */
        private const val INT16_SCALE = 32767f
    }

    // Format bookkeeping resolved in onConfigure.
    private var inputIsFloat = false
    private var sampleRate = 48_000

    // Haas/crossfeed delay lines — sized for the *processing* rate at configure time.
    private var delaySamples = 1
    private var delayL = FloatArray(0)
    private var delayR = FloatArray(0)
    private var delayPos = 0

    // One-pole filter states.
    private var lpStateL = 0f
    private var lpStateR = 0f
    private var airStateL = 0f
    private var airStateR = 0f
    private var lpAlpha = 0f
    private var airAlpha = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputIsFloat = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> false
            C.ENCODING_PCM_FLOAT -> true
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        // Only stereo content is spatialized; mono/multichannel passes through
        // untouched (processor stays inactive → zero overhead).
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate

        delaySamples = max(1, (sampleRate * HAAS_DELAY_SECONDS).toInt())
        delayL = FloatArray(delaySamples)
        delayR = FloatArray(delaySamples)
        delayPos = 0

        val twoPi = 2f * Math.PI.toFloat()
        lpAlpha = 1f - exp(-twoPi * CROSSFEED_CUTOFF_HZ / sampleRate)
        airAlpha = 1f - exp(-twoPi * AIR_CUTOFF_HZ / sampleRate)
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f

        // Same rate, same encoding: see the class doc.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val outputBuffer = replaceOutputBuffer(remaining)

        if (!globalEnabled) {
            // Bit-exact: no conversion to float and back.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameBytes = if (inputIsFloat) 8 else 4

        // Local copies of hot state for the tight loop.
        var pos = delayPos
        var lpL = lpStateL
        var lpR = lpStateR
        var airL = airStateL
        var airR = airStateR
        val dL = delayL
        val dR = delayR
        val delayLen = delaySamples

        while (inputBuffer.remaining() >= frameBytes) {
            val inL: Float
            val inR: Float
            if (inputIsFloat) {
                inL = inputBuffer.float
                inR = inputBuffer.float
            } else {
                inL = inputBuffer.short / INT16_SCALE
                inR = inputBuffer.short / INT16_SCALE
            }

            // 2. Crossfeed: low-pass each channel, feed it (delayed) to the
            // opposite ear — Cavern's interaural time+level difference cues.
            val delayedR = dR[pos]
            val delayedL = dL[pos]
            lpL += lpAlpha * (inL - lpL)
            lpR += lpAlpha * (inR - lpR)
            dL[pos] = lpL
            dR[pos] = lpR
            pos++
            if (pos >= delayLen) pos = 0

            // 1. Mid/side width expansion.
            val mid = (inL + inR) * 0.5f
            val side = (inL - inR) * 0.5f * WIDTH
            var outL = mid + side + CROSSFEED * delayedR
            var outR = mid - side + CROSSFEED * delayedL

            // 3. Height/air shelf: boost the band above the pole (out - lowpass).
            airL += airAlpha * (outL - airL)
            airR += airAlpha * (outR - airR)
            outL += AIR * (outL - airL)
            outR += AIR * (outR - airR)

            outL *= MAKEUP
            outR *= MAKEUP

            if (inputIsFloat) {
                // Float has the headroom; the mixer limits at the very end.
                outputBuffer.putFloat(outL)
                outputBuffer.putFloat(outR)
            } else {
                outputBuffer.putShort(clip16(outL))
                outputBuffer.putShort(clip16(outR))
            }
        }

        delayPos = pos
        lpStateL = lpL
        lpStateR = lpR
        airStateL = airL
        airStateR = airR

        outputBuffer.flip()
    }

    override fun onFlush() {
        delayL.fill(0f)
        delayR.fill(0f)
        delayPos = 0
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f
    }

    override fun onReset() {
        onFlush()
    }

    private fun clip16(sample: Float): Short =
        Math.round(min(1f, max(-1f, sample)) * INT16_SCALE).toShort()
}
