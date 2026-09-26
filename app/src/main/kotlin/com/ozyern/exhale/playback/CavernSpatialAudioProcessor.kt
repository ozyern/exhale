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
 * headphone-virtualizer signal path is ported here as a native Media3
 * [BaseAudioProcessor] and wired into the [androidx.media3.exoplayer.audio.DefaultAudioSink]
 * processor chain — every decoded stereo buffer flows through it before output.
 *
 * ## High-resolution processing pipeline
 *
 * The decoded stream is lifted to a high-resolution internal representation *before* any
 * virtualization runs, and the whole DSP chain executes there:
 *
 *  - **32-bit float sample format.** Every stage below works in normalized `[-1, 1]` floats.
 *    Integer PCM input is converted on the way in and re-quantized only once, at the very end.
 *    The three stages are cascaded gain changes (width expansion, crossfeed summing, air shelf),
 *    and in 16-bit fixed point each cascade re-quantizes and compounds its error into the noise
 *    floor. In float there is exactly one quantization, at output.
 *  - **[hiResSampleRate] Hz processing rate** (48 kHz default, 96 kHz available). 44.1 kHz
 *    material is resampled *up* first, so the filters and the Haas delay line are all built at
 *    the higher rate. That matters most for stage 2: the interaural delay is ~350 µs, which is
 *    15 samples at 44.1 kHz but 34 at 96 kHz — finer time resolution on the exact cue that makes
 *    the image read as external, plus the shelf poles land further from Nyquist where a one-pole
 *    section's phase response is better behaved.
 *
 * The output is emitted as [C.ENCODING_PCM_16BIT] at the high-resolution rate. That is not a
 * detail we get to choose: [androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain]
 * always appends its own [androidx.media3.common.audio.SonicAudioProcessor] after ours, and Sonic
 * throws [AudioProcessor.UnhandledAudioFormatException] for any encoding but 16-bit. Emitting
 * float here would hard-fail playback. The *rate* increase passes through untouched, so the
 * AudioTrack is opened at 48/96 kHz and the sink stops resampling on our behalf.
 *
 * The three virtualization stages, reduced to first-order sections so the whole thing costs
 * ~20 flops per sample (no measurable impact on the playback thread, hence no stuttering):
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
 * Performance notes: all state (delay lines, filter poles) is pre-allocated in
 * [onConfigure]; [queueInput] allocates nothing and runs entirely on
 * ExoPlayer's dedicated audio playback thread — never the main thread. When
 * [globalEnabled] is off the virtualization stages are skipped and only the
 * rate/format conversion runs.
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

        /** Studio rate. The safe default: every Android audio HAL handles 48 kHz natively. */
        const val HI_RES_48K = 48_000

        /** Hi-res rate. Genuinely better for the Haas stage, but not every device HAL takes it. */
        const val HI_RES_96K = 96_000

        /**
         * Internal processing rate. Read once per [onConfigure], so a change takes effect on the
         * next track (or the next seek) rather than mid-buffer — which is exactly what we want,
         * since the output format is part of the sink's configuration contract.
         */
        @Volatile
        var hiResSampleRate: Int = HI_RES_48K

        /**
         * Escape hatch: when false the processor keeps the source rate and only does the
         * float-domain virtualization. Worth having because resampling is the one part of this
         * chain that runs even with [globalEnabled] off.
         */
        @Volatile
        var hiResUpsamplingEnabled: Boolean = true

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

        /** Full-scale for the final 16-bit quantization. */
        private const val INT16_SCALE = 32767f
    }

    // Format bookkeeping resolved in onConfigure.
    private var inputIsFloat = false
    private var inputBytesPerFrame = 4
    private var sourceSampleRate = HI_RES_48K
    private var processingSampleRate = HI_RES_48K

    // Linear-interpolation resampler state. `resampleStep` is how far the read head advances
    // through the *input* per emitted output frame, so < 1 means upsampling.
    private var resampleStep = 1f
    private var resamplePhase = 0f
    private var prevL = 0f
    private var prevR = 0f
    private var primed = false

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
        // Both integer and float PCM are accepted on the way in; the DSP is float either way.
        inputIsFloat = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> false
            C.ENCODING_PCM_FLOAT -> true
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Only stereo content is spatialized; mono/multichannel passes through
        // untouched (processor stays inactive → zero overhead).
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        inputBytesPerFrame = if (inputIsFloat) 8 else 4
        sourceSampleRate = inputAudioFormat.sampleRate

        // Never *down*sample: if the source is already above the hi-res target (a 96 kHz FLAC
        // with the 48 kHz default selected) we keep its rate rather than throwing detail away.
        processingSampleRate =
            if (hiResUpsamplingEnabled) max(sourceSampleRate, hiResSampleRate) else sourceSampleRate

        resampleStep = sourceSampleRate.toFloat() / processingSampleRate.toFloat()
        resamplePhase = 0f
        prevL = 0f
        prevR = 0f
        primed = false

        delaySamples = max(1, (processingSampleRate * HAAS_DELAY_SECONDS).toInt())
        delayL = FloatArray(delaySamples)
        delayR = FloatArray(delaySamples)
        delayPos = 0

        val twoPi = 2f * Math.PI.toFloat()
        lpAlpha = 1f - exp(-twoPi * CROSSFEED_CUTOFF_HZ / processingSampleRate)
        airAlpha = 1f - exp(-twoPi * AIR_CUTOFF_HZ / processingSampleRate)
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f

        // 16-bit out, at the processing rate — see the class doc for why the encoding is forced.
        return AudioProcessor.AudioFormat(
            processingSampleRate,
            2,
            C.ENCODING_PCM_16BIT,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inputFrames = remaining / inputBytesPerFrame
        // Upper bound on emitted frames: at most ceil(outRate / inRate) outputs per input frame,
        // plus one for a phase that was already mid-interval when the buffer started.
        val expansion = (processingSampleRate + sourceSampleRate - 1) / sourceSampleRate
        val outputBuffer = replaceOutputBuffer((inputFrames * expansion + 1) * 4)

        val spatialize = globalEnabled

        // Local copies of hot state for the tight loop.
        var pos = delayPos
        var lpL = lpStateL
        var lpR = lpStateR
        var airL = airStateL
        var airR = airStateR
        var pL = prevL
        var pR = prevR
        var phase = resamplePhase
        val step = resampleStep
        val dL = delayL
        val dR = delayR
        val delayLen = delaySamples

        while (inputBuffer.remaining() >= inputBytesPerFrame) {
            val curL: Float
            val curR: Float
            if (inputIsFloat) {
                curL = inputBuffer.float
                curR = inputBuffer.float
            } else {
                curL = inputBuffer.short / INT16_SCALE
                curR = inputBuffer.short / INT16_SCALE
            }

            if (!primed) {
                // Seed the interpolator so the first interval doesn't ramp up from silence.
                pL = curL
                pR = curR
                primed = true
            }

            // Emit every output frame whose read head falls inside [prev, cur).
            while (phase < 1f && outputBuffer.remaining() >= 4) {
                var outL = pL + (curL - pL) * phase
                var outR = pR + (curR - pR) * phase
                phase += step

                if (spatialize) {
                    // 2. Crossfeed: low-pass each channel, feed it (delayed) to the
                    // opposite ear — Cavern's interaural time+level difference cues.
                    val delayedR = dR[pos]
                    val delayedL = dL[pos]
                    lpL += lpAlpha * (outL - lpL)
                    lpR += lpAlpha * (outR - lpR)
                    dL[pos] = lpL
                    dR[pos] = lpR
                    pos++
                    if (pos >= delayLen) pos = 0

                    // 1. Mid/side width expansion.
                    val mid = (outL + outR) * 0.5f
                    val side = (outL - outR) * 0.5f * WIDTH
                    outL = mid + side + CROSSFEED * delayedR
                    outR = mid - side + CROSSFEED * delayedL

                    // 3. Height/air shelf: boost the band above the pole (out - lowpass).
                    airL += airAlpha * (outL - airL)
                    airR += airAlpha * (outR - airR)
                    outL += AIR * (outL - airL)
                    outR += AIR * (outR - airR)

                    outL *= MAKEUP
                    outR *= MAKEUP
                }

                // The single quantization in the whole chain.
                outputBuffer.putShort(clip16(outL))
                outputBuffer.putShort(clip16(outR))
            }
            phase -= 1f

            pL = curL
            pR = curR
        }

        delayPos = pos
        lpStateL = lpL
        lpStateR = lpR
        airStateL = airL
        airStateR = airR
        prevL = pL
        prevR = pR
        resamplePhase = phase

        outputBuffer.flip()
    }

    override fun onFlush() {
        delayL.fill(0f)
        delayR.fill(0f)
        delayPos = 0
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f
        resamplePhase = 0f
        prevL = 0f
        prevR = 0f
        primed = false
    }

    override fun onReset() {
        onFlush()
    }

    private fun clip16(sample: Float): Short =
        (min(1f, max(-1f, sample)) * INT16_SCALE).toInt().toShort()
}
