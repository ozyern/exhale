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
 * Headphone spatial audio: a stereo song widened, given the cues of two ears and a room, and
 * lifted a little, so it is heard around the head rather than between the ears. It runs in
 * [PrecisionAudioSink]'s pipeline, ahead of the sink.
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
 * The four stages, all first-order sections, short delay lines and taps, so the whole thing costs
 * a few dozen flops per sample:
 *
 *  1. **Mid/side width expansion** — pushes what is panned away from the phantom centre,
 *     above the bass only.
 *  2. **Low-passed, delayed crossfeed** (Bauer/BS2B-style, ~350 µs Haas delay
 *     at ~700 Hz) — the time and level difference a head puts between the ears,
 *     which is what makes headphone playback read as "outside the head".
 *  3. **Early reflections** — four faint, dulled echoes from a small room, two per ear at
 *     different distances. Crossfeed alone leaves the image just outside the ears; the room
 *     is what gives it distance.
 *  4. **Height/air shelf** — a gentle high-band emphasis that lifts the image, the way height
 *     channels do in a room.
 *
 * All state is pre-allocated in [onConfigure]; [queueInput] allocates nothing.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

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
        private const val CROSSFEED_CUTOFF_HZ = 700f

        /** Below this the stereo width is left as mixed: bass stays together in both ears. */
        private const val WIDTH_FLOOR_HZ = 200f

        /** Switching the effect fades over this long. */
        private const val RAMP_MS = 80f

        /** The limiter's ceiling, just under full scale. */
        private const val LIMIT = 0.97f
        private const val AIR_CUTOFF_HZ = 7_000f
        private const val HAAS_DELAY_SECONDS = 0.00035f

        /** The four reflections, in ms: an early and a late one per ear, never the same pair. */
        private val ROOM_TAPS_MS = floatArrayOf(7.9f, 19.3f, 11.1f, 24.7f)

        /** Their levels, about -21 and -26 dB: heard as distance, never as an echo. */
        private const val ROOM_EARLY = 0.09f
        private const val ROOM_LATE = 0.05f

        /** Reflections come back duller than the sound that made them. */
        private const val ROOM_DAMP_HZ = 4_500f

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
    private var sideLowAlpha = 0f
    private var sideLowState = 0f

    // The room: a short history of the mix, dulled, and where each reflection reads from it.
    private var room = FloatArray(1)
    private var roomPos = 0
    private var roomTaps = IntArray(4)
    private var roomDamp = 0f
    private var roomDampAlpha = 0f

    /** How much of the effect is in, 0..1, ramped toward on or off. */
    private var wet = 0f
    private var limiterGain = 1f
    private var limiterRelease = 0f

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
        sideLowAlpha = 1f - exp(-twoPi * WIDTH_FLOOR_HZ / sampleRate)
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f
        sideLowState = 0f
        roomTaps = IntArray(ROOM_TAPS_MS.size) { max(1, (sampleRate * ROOM_TAPS_MS[it] / 1000f).toInt()) }
        room = FloatArray(roomTaps.max() + 1)
        roomPos = 0
        roomDamp = 0f
        roomDampAlpha = 1f - exp(-twoPi * ROOM_DAMP_HZ / sampleRate)
        limiterGain = 1f
        // Lets go over about 80 ms.
        limiterRelease = 1f - exp(-1f / (0.08f * sampleRate))
        wet = if (globalEnabled) 1f else 0f

        // Same rate, same encoding: see the class doc.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val outputBuffer = replaceOutputBuffer(remaining)

        val target = if (globalEnabled) 1f else 0f
        if (target == 0f && wet == 0f) {
            // Off and faded out: bit-exact, no conversion to float and back.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameBytes = if (inputIsFloat) 8 else 4
        val rampStep = 1f / (sampleRate * RAMP_MS / 1000f).coerceAtLeast(1f)

        // Local copies of hot state for the tight loop.
        var pos = delayPos
        var lpL = lpStateL
        var lpR = lpStateR
        var airL = airStateL
        var airR = airStateR
        var sideLow = sideLowState
        var mix = wet
        var gain = limiterGain
        val dL = delayL
        val dR = delayR
        val delayLen = delaySamples
        val width = WIDTH
        val crossfeed = CROSSFEED
        val air = AIR
        val roomBuf = room
        val roomLen = roomBuf.size
        val tapL1 = roomTaps[0]
        val tapL2 = roomTaps[1]
        val tapR1 = roomTaps[2]
        val tapR2 = roomTaps[3]
        var rPos = roomPos
        var damp = roomDamp

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
            mix = when {
                mix < target -> (mix + rampStep).coerceAtMost(target)
                mix > target -> (mix - rampStep).coerceAtLeast(target)
                else -> mix
            }

            // 1. Width, above the bass only. Widening the low end too put the bass partly out of
            // phase between the ears, which reads as a hollow, smeared bottom rather than space.
            val mid = (inL + inR) * 0.5f
            val side = (inL - inR) * 0.5f
            sideLow += sideLowAlpha * (side - sideLow)
            val wideSide = sideLow + (side - sideLow) * width
            val wideL = mid + wideSide
            val wideR = mid - wideSide

            // 2. Crossfeed, Bauer/BS2B style: each ear gets the other's lows, late, and gives up
            // the same amount of its own. It used to only add, so everything below 700 Hz came out
            // about 2.5 dB heavier with the effect on; now a centred sound stays level.
            val delayedR = dR[pos]
            val delayedL = dL[pos]
            lpL += lpAlpha * (wideL - lpL)
            lpR += lpAlpha * (wideR - lpR)
            dL[pos] = lpL
            dR[pos] = lpR
            pos++
            if (pos >= delayLen) pos = 0
            var outL = wideL - crossfeed * lpL + crossfeed * delayedR
            var outR = wideR - crossfeed * lpR + crossfeed * delayedL

            // 3. The room: the middle of the mix, dulled, coming back off walls at four distances,
            // two per ear so the ears never hear the same echo.
            damp += roomDampAlpha * (mid - damp)
            roomBuf[rPos] = damp
            outL += ROOM_EARLY * roomBuf[(rPos - tapL1 + roomLen) % roomLen] +
                ROOM_LATE * roomBuf[(rPos - tapL2 + roomLen) % roomLen]
            outR += ROOM_EARLY * roomBuf[(rPos - tapR1 + roomLen) % roomLen] +
                ROOM_LATE * roomBuf[(rPos - tapR2 + roomLen) % roomLen]
            rPos++
            if (rPos >= roomLen) rPos = 0

            // 4. Height/air shelf: boost the band above the pole (out - lowpass).
            airL += airAlpha * (outL - airL)
            airR += airAlpha * (outR - airR)
            outL += air * (outL - airL)
            outR += air * (outR - airR)

            // Faded in and out rather than switched, so turning it on or off never clicks.
            outL = inL + (outL - inL) * mix
            outR = inR + (outR - inR) * mix

            // A peak limiter where a fixed -1.7 dB used to be: the effect no longer sounds quieter
            // than the song without it, and a wide loud master is turned down only when it peaks.
            val peak = max(kotlin.math.abs(outL), kotlin.math.abs(outR)) * gain
            if (peak > LIMIT) gain *= LIMIT / peak else gain += (1f - gain) * limiterRelease
            outL *= gain
            outR *= gain

            if (inputIsFloat) {
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
        sideLowState = sideLow
        roomPos = rPos
        roomDamp = damp
        wet = mix
        limiterGain = gain

        outputBuffer.flip()
    }

    override fun onFlush() {
        delayL.fill(0f)
        delayR.fill(0f)
        delayPos = 0
        lpStateL = 0f; lpStateR = 0f; airStateL = 0f; airStateR = 0f
        sideLowState = 0f
        roomDamp = 0f
        roomPos = 0
        room.fill(0f)
        limiterGain = 1f
        // A seek lands with the effect already in place: no fade-in on every seek.
        wet = if (globalEnabled) 1f else 0f
    }

    override fun onReset() {
        onFlush()
    }

    private fun clip16(sample: Float): Short =
        Math.round(min(1f, max(-1f, sample)) * INT16_SCALE).toShort()
}
