/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The panning and interaural-delay core follows BeatWave's EightDAudioProcessor
 * (github.com/vortexapps67/BeatWave, GPL-3.0); the rear shadow, float path and continuous orbit
 * are Exhale's.
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "8D audio": the song circles your head instead of sitting between the two ears.
 *
 * Three cues, because the ear needs all three to hear a position going *round* rather than a
 * volume swinging side to side:
 *
 *  - **Equal-power panning.** Gains follow the cosine and sine of the pan angle, so the total
 *    power stays flat as the image sweeps. A linear pan dips through the middle, which is what
 *    makes a cheap autopan sound like the volume pumping.
 *  - **Interaural time difference.** The far ear hears it up to [MAX_ITD_US] later — the delay a
 *    head actually puts between the ears, and the cue the brain trusts most below 1.5 kHz.
 *    Without it the sound stays inside your head and is merely louder on one side.
 *  - **The rear shadow.** On the half of the orbit behind you the highs are softened and the level
 *    drops a little, the way the outer ear shades a sound from behind. Without it the sound only
 *    swings left and right through the front; with it, it goes *behind* you on the way round.
 *
 * Headphones only, really: on speakers both ears hear both channels and the delay cue collapses.
 *
 * Stereo, 16-bit or float, at any rate. Always active for stereo and a straight copy while off, so
 * switching it never reconfigures the sink mid-song; the orbit keeps going across seeks and songs
 * rather than snapping back to the middle each time. Driven from DataStore by MusicService
 * through the volatile fields below, read once per buffer.
 */
@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {

    companion object {
        @Volatile var enabled: Boolean = false

        /** Full orbits a second. 0.125 is one lap every eight seconds. */
        @Volatile var rotationHz: Float = DEFAULT_ROTATION_HZ

        /** How far toward each ear the image travels, 0..1. */
        @Volatile var depth: Float = DEFAULT_DEPTH

        const val MIN_ROTATION_HZ = 0.05f
        const val MAX_ROTATION_HZ = 0.40f
        const val DEFAULT_ROTATION_HZ = 0.125f

        /**
         * Not 1: at full depth the far ear's gain reaches zero and the song drops out of it
         * entirely, which is more dramatic than musical.
         */
        const val DEFAULT_DEPTH = 0.85f

        /** About 23 cm of head at the speed of sound. Wider only smears transients into an echo. */
        private const val MAX_ITD_US = 660.0

        /** Toggling ramps over this long, so neither the gains nor the delay step and click. */
        private const val RAMP_MS = 120f

        /** Where the rear shadow starts rolling the highs off. */
        private const val REAR_CUTOFF_HZ = 3_200f

        /** How much of the shadowed signal replaces the direct one, fully behind. */
        private const val REAR_SHADOW = 0.55f

        /** And how much quieter fully behind: about 1.5 dB. */
        private const val REAR_DIP = 0.16f

        private const val TWO_PI = 2.0 * PI

        /** Where the orbit is, shared so a new sink (a new song, a seek) picks up where it was. */
        @Volatile private var phase = 0.0
    }

    private var isFloat = false
    private var sampleRate = 0
    private var maxItdSamples = 1
    private var capacity = 2
    private var ringL = FloatArray(0)
    private var ringR = FloatArray(0)
    private var write = 0
    private var applied = 0f
    private var shadowAlpha = 0f
    private var shadowL = 0f
    private var shadowR = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        isFloat = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> false
            C.ENCODING_PCM_FLOAT -> true
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        maxItdSamples = ((sampleRate * MAX_ITD_US) / 1_000_000.0).roundToInt().coerceAtLeast(1)
        capacity = maxItdSamples + 1
        ringL = FloatArray(capacity)
        ringR = FloatArray(capacity)
        write = 0
        applied = 0f
        shadowAlpha = 1f - exp(-2f * PI.toFloat() * REAR_CUTOFF_HZ / sampleRate)
        shadowL = 0f
        shadowR = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val target = if (enabled) depth.coerceIn(0f, 1f) else 0f

        // Off and already ramped out: the bytes go through untouched.
        if (target == 0f && applied == 0f) {
            out.put(inputBuffer).flip()
            return
        }

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (isFloat) 4 else 2
        val frames = size / (bytesPerSample * 2)
        var position = input.position()
        val phaseStep = TWO_PI * rotationHz.coerceIn(MIN_ROTATION_HZ, MAX_ROTATION_HZ) / sampleRate
        val rampStep = 1f / (sampleRate * RAMP_MS / 1000f).coerceAtLeast(1f)
        var orbit = phase

        repeat(frames) {
            applied = when {
                applied < target -> (applied + rampStep).coerceAtMost(target)
                applied > target -> (applied - rampStep).coerceAtLeast(target)
                else -> applied
            }
            val left: Float
            val right: Float
            if (isFloat) {
                left = input.getFloat(position)
                right = input.getFloat(position + 4)
            } else {
                left = input.getShort(position) / 32768f
                right = input.getShort(position + 2) / 32768f
            }
            position += bytesPerSample * 2

            ringL[write] = left
            ringR[write] = right

            // Round the head: sine is left/right, cosine is front/back.
            val pan = (sin(orbit) * applied).toFloat().coerceIn(-1f, 1f)
            val behind = (-cos(orbit)).toFloat().coerceAtLeast(0f) * applied

            val farDelay = (abs(pan) * maxItdSamples).roundToInt()
            var l = ringL[(write - (if (pan > 0f) farDelay else 0) + capacity) % capacity]
            var r = ringR[(write - (if (pan < 0f) farDelay else 0) + capacity) % capacity]

            // The rear shadow: a gentle low-pass, blended in as the image goes behind.
            shadowL += shadowAlpha * (l - shadowL)
            shadowR += shadowAlpha * (r - shadowR)
            val shade = behind * REAR_SHADOW
            l += (shadowL - l) * shade
            r += (shadowR - r) * shade
            val level = 1f - behind * REAR_DIP

            val angle = (pan + 1f) * (PI / 4.0)
            l *= cos(angle).toFloat() * 1.4142135f * level
            r *= sin(angle).toFloat() * 1.4142135f * level
            // Equal power is +3 dB in the middle relative to a hard pan; the √2 above puts the
            // centre back at unity, and a soft limit keeps the ear that is turned toward from
            // clipping on a loud master.
            l = softClip(l)
            r = softClip(r)

            if (isFloat) {
                out.putFloat(l)
                out.putFloat(r)
            } else {
                out.putShort((l * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
                out.putShort((r * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
            }

            write = (write + 1) % capacity
            orbit += phaseStep
            if (orbit >= TWO_PI) orbit -= TWO_PI
        }
        phase = orbit
        // Consumed: the reads above are absolute, and the pipeline judges acceptance by what is left.
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Transparent below 0.9, then rounding off toward 1 rather than clipping hard. */
    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= 0.9f) return x
        val over = (a - 0.9f) / 0.1f
        val shaped = 0.9f + 0.1f * (over / (1f + over))
        return if (x < 0f) -shaped else shaped
    }

    override fun onFlush() {
        ringL.fill(0f)
        ringR.fill(0f)
        write = 0
        shadowL = 0f
        shadowR = 0f
        // The ramp restarts from the middle so a seek never jumps the image; the orbit itself
        // carries on from where it was.
        applied = 0f
    }

    override fun onReset() {
        ringL = FloatArray(0)
        ringR = FloatArray(0)
        sampleRate = 0
    }
}
