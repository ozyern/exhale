/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The filter layout, tone pad mapping, presets and state-variable processor follow BitChord's
 * equaliser (github.com/kushagrasinghx/BitChord, GPL-3.0), adapted to Exhale's audio chain: it
 * reads float as well as 16-bit PCM, and is aimed from settings through a static target the way
 * the Cavern spatialiser is.
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/** The shape of one filter: shelves at the two ends, bells between. */
enum class FilterKind { BELL, LOW_SHELF, HIGH_SHELF }

/** A slot's fixed identity: its shape and where on the spectrum it sits. */
class FilterSlot(val kind: FilterKind, val frequencyHz: Float)

/**
 * Every filter either way of tuning can ask for, in one fixed list.
 *
 * The manual bands and the tone pad drive the *same* ten slots, and a slot not in use is held at
 * 0 dB rather than removed — a section at 0 dB is skipped outright — so switching mode or preset is
 * a gain change like any other and glides, instead of rebuilding the cascade mid-signal and
 * clicking.
 */
object EqLayout {
    /** The seven centres the manual faders sit under. */
    val MANUAL_BANDS_HZ = floatArrayOf(60f, 150f, 400f, 1_000f, 2_500f, 6_000f, 14_000f)

    /** Wide enough to overlap neighbours about 1.3 octaves apart, so there are no scallops. */
    const val MANUAL_Q = 1.0f

    const val MANUAL_FIRST = 0
    const val MANUAL_COUNT = 7

    const val TONE_LOW = 7
    const val TONE_MID = 8
    const val TONE_HIGH = 9

    const val SLOTS = 10

    val slots: List<FilterSlot> = buildList {
        MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
            val kind = when (index) {
                0 -> FilterKind.LOW_SHELF
                MANUAL_BANDS_HZ.lastIndex -> FilterKind.HIGH_SHELF
                else -> FilterKind.BELL
            }
            add(FilterSlot(kind, hz))
        }
        add(FilterSlot(FilterKind.LOW_SHELF, 250f))
        add(FilterSlot(FilterKind.BELL, 1_000f))
        add(FilterSlot(FilterKind.HIGH_SHELF, 4_000f))
    }

    /** How far a manual fader travels either way. */
    const val MANUAL_RANGE_DB = 12f

    /** How far the tone pad travels on each axis, in whole steps. */
    const val TONE_STEPS = 5

    /** Decibels per step of the pad, so a corner is ±6 dB. */
    const val TONE_DB_PER_STEP = 1.2f
}

/** Dynamic is the tone pad; Manual is the seven faders. */
enum class SoundEqMode { DYNAMIC, MANUAL }

/**
 * A tuning, rendered: every slot's gain and Q, and the make-up attenuation that keeps the loudest
 * point of the curve from clipping.
 */
class EqCurve(
    val gainsDb: FloatArray,
    val qs: FloatArray,
    /** Always at or below zero: however far the curve's peak was pushed up, it is pulled back. */
    val preampDb: Float,
) {
    /** The curve's summed response at [hz], preamp included — what the screen draws. */
    fun responseDb(hz: Float): Float {
        var sum = preampDb
        for (slot in 0 until EqLayout.SLOTS) {
            sum += sectionGainDb(EqLayout.slots[slot], gainsDb[slot], qs[slot], hz)
        }
        return sum
    }

    companion object {
        val FLAT = of(FloatArray(EqLayout.SLOTS), FloatArray(EqLayout.SLOTS) { 0.707f })

        fun of(gainsDb: FloatArray, qs: FloatArray): EqCurve =
            EqCurve(gainsDb, qs, preampFor(gainsDb, qs))
    }
}

/** The seven faders, as a curve. Slots the tone pad owns stay flat. */
fun manualCurve(bandsDb: List<Float>): EqCurve {
    val gains = FloatArray(EqLayout.SLOTS)
    val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
    for (band in 0 until EqLayout.MANUAL_COUNT) {
        gains[EqLayout.MANUAL_FIRST + band] =
            bandsDb.getOrElse(band) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        qs[EqLayout.MANUAL_FIRST + band] = EqLayout.MANUAL_Q
    }
    return EqCurve.of(gains, qs)
}

/**
 * The tone pad, as a curve. [x] tilts — left warm, right bright, the two shelves moving equal and
 * opposite so the middle keeps its level — and [y] contours the mids: down scoops them, up pushes
 * them forward. [focused] narrows all three.
 */
fun toneCurve(x: Int, y: Int, focused: Boolean): EqCurve {
    val gains = FloatArray(EqLayout.SLOTS)
    val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
    val steps = EqLayout.TONE_STEPS
    val tilt = x.coerceIn(-steps, steps) * EqLayout.TONE_DB_PER_STEP
    val contour = y.coerceIn(-steps, steps) * EqLayout.TONE_DB_PER_STEP

    gains[EqLayout.TONE_LOW] = -tilt
    gains[EqLayout.TONE_HIGH] = tilt
    gains[EqLayout.TONE_MID] = contour

    val shelfQ = if (focused) 0.9f else 0.5f
    val bellQ = if (focused) 2.2f else 0.7f
    qs[EqLayout.TONE_LOW] = shelfQ
    qs[EqLayout.TONE_HIGH] = shelfQ
    qs[EqLayout.TONE_MID] = bellQ
    return EqCurve.of(gains, qs)
}

/**
 * Starting points for the manual faders, one gain per centre in [EqLayout.MANUAL_BANDS_HZ].
 * Deliberately modest: every decibel of boost is a decibel of headroom the preamp takes back.
 */
enum class SoundPreset(val label: String, vararg val bandsDb: Float) {
    FLAT("Flat", 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    ACOUSTIC("Acoustic", 3f, 1.5f, 0f, 1.5f, 2.5f, 2f, 1f),
    BASS_BOOST("Bass Boost", 6f, 4f, 1.5f, 0f, 0f, 0f, 0f),
    BASS_CUT("Bass Cut", -6f, -4f, -1.5f, 0f, 0f, 0f, 0f),
    VOCAL("Vocal", -3f, -1.5f, 1f, 3.5f, 3f, 1f, -1f),
    TREBLE_BOOST("Treble Boost", 0f, 0f, 0f, 0f, 1.5f, 3.5f, 5f),
    TREBLE_CUT("Treble Cut", 0f, 0f, 0f, 0f, -1.5f, -3.5f, -5f),
    LOUDNESS("Loudness", 6f, 3.5f, 0f, -1.5f, -1f, 2f, 5f),
    SPOKEN_WORD("Spoken Word", -5f, -2.5f, 1.5f, 4f, 3.5f, 1.5f, -2f),
    ELECTRONIC("Electronic", 5f, 3f, -1f, 0f, 1f, 3f, 4f),
    ROCK("Rock", 4f, 2.5f, -1f, -1.5f, 1f, 3f, 3.5f),
    HIP_HOP("Hip-Hop", 6f, 4f, 0.5f, -1f, 0.5f, 2f, 2.5f),
    JAZZ("Jazz", 3f, 1.5f, 0f, 1f, 1.5f, 2f, 2.5f),
    CLASSICAL("Classical", 3f, 2f, 0f, 0f, 1f, 2.5f, 3f),
    SMALL_SPEAKERS("Small Speakers", 5f, 4f, 2f, 0.5f, 0f, -1f, -2f),
    LATE_NIGHT("Late Night", 3f, 1f, 0f, 1.5f, 1f, -1f, -3f),
    CUSTOM("Custom"),
    ;

    val bands: List<Float> get() = bandsDb.toList()

    companion object {
        /** The preset these bands are, or [CUSTOM] if they are nobody's. */
        fun matching(bandsDb: List<Float>): SoundPreset = entries.firstOrNull { preset ->
            preset != CUSTOM && preset.bandsDb.size == bandsDb.size &&
                preset.bandsDb.indices.all { abs(preset.bandsDb[it] - bandsDb[it]) < 0.05f }
        } ?: CUSTOM
    }
}

/** Seven gains as the string the preference stores them as. */
fun encodeSoundBands(bands: List<Float>): String =
    bands.joinToString(",") { ((it * 10f).roundToInt() / 10f).toString() }

fun decodeSoundBands(raw: String?): List<Float> {
    val parsed = raw?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }.orEmpty()
    return List(EqLayout.MANUAL_COUNT) { parsed.getOrElse(it) { 0f } }
}

/** How far the curve has to be pulled down to stop its summed peak clipping. */
private fun preampFor(gainsDb: FloatArray, qs: FloatArray): Float {
    var peak = 0f
    for (point in 0 until RESPONSE_POINTS) {
        val hz = responseFrequency(point)
        var sum = 0f
        for (slot in 0 until EqLayout.SLOTS) {
            sum += sectionGainDb(EqLayout.slots[slot], gainsDb[slot], qs[slot], hz)
        }
        if (sum > peak) peak = sum
    }
    return -peak
}

private fun responseFrequency(point: Int): Float {
    val fraction = point.toDouble() / (RESPONSE_POINTS - 1)
    return (20.0 * (1_000.0).pow(fraction)).toFloat()
}

/** One section's contribution at [hz], in decibels, from its analog prototype. */
internal fun sectionGainDb(slot: FilterSlot, gainDb: Float, q: Float, hz: Float): Float {
    if (abs(gainDb) < 0.01f) return 0f
    val a = 10.0.pow(gainDb / 40.0)
    val a2 = a * a
    val x = (hz / slot.frequencyHz).toDouble()
    val x2 = x * x
    val qq = (q * q).toDouble()
    val magnitude = when (slot.kind) {
        FilterKind.BELL -> {
            val flat = (1 - x2) * (1 - x2)
            sqrt((flat + x2 * a2 / qq) / (flat + x2 / (a2 * qq)))
        }
        FilterKind.LOW_SHELF -> {
            val common = x2 * a / qq
            a * sqrt(((a - x2) * (a - x2) + common) / ((1 - a * x2) * (1 - a * x2) + common))
        }
        FilterKind.HIGH_SHELF -> {
            val common = x2 * a / qq
            a * sqrt(((1 - a * x2) * (1 - a * x2) + common) / ((a - x2) * (a - x2) + common))
        }
    }
    return (20.0 * log10(magnitude)).toFloat()
}

private const val RESPONSE_POINTS = 96

/**
 * Ten state-variable sections, a make-up preamp and a left/right balance trim.
 *
 * Off, or flat and settled, it is a straight copy: the bytes out are the bytes in, so leaving it
 * in the chain costs the sound nothing. Every change is a target the coefficients glide towards a
 * few hundred times a second, so a preset switch or a drag never clicks.
 */
@UnstableApi
class ToneEqualizerProcessor : BaseAudioProcessor() {

    private class Tuning(val curve: EqCurve, val balance: Float) {
        companion object {
            val OFF = Tuning(EqCurve.FLAT, 0f)
        }
    }

    private var channelCount = 0
    private var sampleRate = 0
    private var float = false

    private val currentGainDb = FloatArray(EqLayout.SLOTS)
    private val currentQ = FloatArray(EqLayout.SLOTS) { 0.707f }
    private var currentPreampDb = 0f
    private var currentBalance = 0f

    private val coeffA1 = FloatArray(EqLayout.SLOTS)
    private val coeffA2 = FloatArray(EqLayout.SLOTS)
    private val coeffA3 = FloatArray(EqLayout.SLOTS)
    private val mixInput = FloatArray(EqLayout.SLOTS)
    private val mixBand = FloatArray(EqLayout.SLOTS)
    private val mixLow = FloatArray(EqLayout.SLOTS)

    private val activeSlots = IntArray(EqLayout.SLOTS)
    private val running = BooleanArray(EqLayout.SLOTS)

    /** Two integrator states per section, per channel. */
    private var state = FloatArray(0)
    private var channelGain = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val readable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!readable || inputAudioFormat.channelCount < 1 || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        state = FloatArray(channelCount * EqLayout.SLOTS * 2)
        channelGain = FloatArray(channelCount) { 1f }
        running.fill(false)
        snapToTarget()
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        state.fill(0f)
        running.fill(false)
        snapToTarget()
    }

    override fun onReset() {
        state = FloatArray(0)
        channelGain = FloatArray(0)
        running.fill(false)
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (float) 4 else 2
        val bytesPerFrame = bytesPerSample * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val tuning = target
        if (isFlat(tuning) && isSettled(tuning)) {
            // Nothing to do: a byte-for-byte copy.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            glideTowards(tuning)
            val active = prepareSections()
            prepareChannelGains()
            repeat(block) {
                for (channel in 0 until channelCount) {
                    var sample = if (float) inputBuffer.float else inputBuffer.short * INV_SHORT
                    for (index in 0 until active) {
                        sample = section(activeSlots[index], channel, sample)
                    }
                    sample *= channelGain[channel]
                    if (float) {
                        outputBuffer.putFloat(sample)
                    } else {
                        outputBuffer.putShort((sample * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
                    }
                }
            }
            flushDenormals(active)
            remaining -= block
        }
        outputBuffer.flip()
    }

    private fun snapToTarget() {
        val tuning = target
        tuning.curve.gainsDb.copyInto(currentGainDb)
        tuning.curve.qs.copyInto(currentQ)
        currentPreampDb = tuning.curve.preampDb
        currentBalance = tuning.balance
    }

    private fun glideTowards(tuning: Tuning) {
        for (slot in 0 until EqLayout.SLOTS) {
            currentGainDb[slot] += (tuning.curve.gainsDb[slot] - currentGainDb[slot]) * GLIDE_RATE
            val from = ln(currentQ[slot].coerceAtLeast(MIN_Q))
            val to = ln(tuning.curve.qs[slot].coerceAtLeast(MIN_Q))
            currentQ[slot] = exp(from + (to - from) * GLIDE_RATE)
        }
        currentPreampDb += (tuning.curve.preampDb - currentPreampDb) * GLIDE_RATE
        currentBalance += (tuning.balance - currentBalance) * GLIDE_RATE
    }

    private fun isFlat(tuning: Tuning): Boolean =
        abs(tuning.balance) < SETTLED_BALANCE &&
            abs(tuning.curve.preampDb) < SETTLED_DB &&
            tuning.curve.gainsDb.all { abs(it) < SETTLED_DB }

    private fun isSettled(tuning: Tuning): Boolean {
        if (abs(currentBalance - tuning.balance) >= SETTLED_BALANCE) return false
        if (abs(currentPreampDb - tuning.curve.preampDb) >= SETTLED_DB) return false
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot] - tuning.curve.gainsDb[slot]) >= SETTLED_DB) return false
        }
        return true
    }

    private fun prepareSections(): Int {
        var active = 0
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot]) >= SETTLED_DB) {
                updateCoefficients(slot)
                activeSlots[active++] = slot
                running[slot] = true
            } else if (running[slot]) {
                clearState(slot)
                running[slot] = false
            }
        }
        return active
    }

    private fun updateCoefficients(slot: Int) {
        val spec = EqLayout.slots[slot]
        val a = 10f.pow(currentGainDb[slot] / 40f)
        val q = currentQ[slot].coerceAtLeast(MIN_Q)
        val hz = spec.frequencyHz.coerceIn(MIN_HZ, sampleRate * MAX_FREQUENCY_FRACTION)
        val base = tan(Math.PI * hz / sampleRate).toFloat()
        val g: Float
        val k: Float
        when (spec.kind) {
            FilterKind.BELL -> {
                g = base
                k = 1f / (q * a)
                mixInput[slot] = 1f
                mixBand[slot] = k * (a * a - 1f)
                mixLow[slot] = 0f
            }
            FilterKind.LOW_SHELF -> {
                g = base / sqrt(a)
                k = 1f / q
                mixInput[slot] = 1f
                mixBand[slot] = k * (a - 1f)
                mixLow[slot] = a * a - 1f
            }
            FilterKind.HIGH_SHELF -> {
                g = base * sqrt(a)
                k = 1f / q
                mixInput[slot] = a * a
                mixBand[slot] = k * (1f - a) * a
                mixLow[slot] = 1f - a * a
            }
        }
        val d = 1f / (1f + g * (g + k))
        coeffA1[slot] = d
        coeffA2[slot] = g * d
        coeffA3[slot] = g * (g * d)
    }

    private fun prepareChannelGains() {
        val preamp = 10f.pow(currentPreampDb / 20f)
        if (channelCount == 2) {
            channelGain[0] = preamp * min(1f, 1f - currentBalance)
            channelGain[1] = preamp * min(1f, 1f + currentBalance)
        } else {
            channelGain.fill(preamp)
        }
    }

    private fun section(slot: Int, channel: Int, input: Float): Float {
        val i = (channel * EqLayout.SLOTS + slot) * 2
        val ic1 = state[i]
        val ic2 = state[i + 1]
        val v3 = input - ic2
        val v1 = coeffA1[slot] * ic1 + coeffA2[slot] * v3
        val v2 = ic2 + coeffA2[slot] * ic1 + coeffA3[slot] * v3
        state[i] = 2f * v1 - ic1
        state[i + 1] = 2f * v2 - ic2
        return mixInput[slot] * input + mixBand[slot] * v1 + mixLow[slot] * v2
    }

    private fun clearState(slot: Int) {
        for (channel in 0 until channelCount) {
            val i = (channel * EqLayout.SLOTS + slot) * 2
            state[i] = 0f
            state[i + 1] = 0f
        }
    }

    private fun flushDenormals(active: Int) {
        for (index in 0 until active) {
            val slot = activeSlots[index]
            for (channel in 0 until channelCount) {
                val i = (channel * EqLayout.SLOTS + slot) * 2
                if (abs(state[i]) < DENORMAL_FLOOR) state[i] = 0f
                if (abs(state[i + 1]) < DENORMAL_FLOOR) state[i + 1] = 0f
            }
        }
    }

    companion object {
        /** What every instance aims at. Swapped whole from settings; read once per sub-block. */
        @Volatile
        private var target: Tuning = Tuning.OFF

        /** Aims the equaliser. Off is a flat curve, so switching off glides down to nothing. */
        fun setTuning(enabled: Boolean, curve: EqCurve, balance: Float) {
            target = if (enabled) Tuning(curve, balance.coerceIn(-1f, 1f)) else Tuning.OFF
        }

        private const val INV_SHORT = 1f / 32768f
        private const val GLIDE_FRAMES = 64
        private const val GLIDE_RATE = 0.08f
        private const val SETTLED_DB = 0.01f
        private const val SETTLED_BALANCE = 0.0005f
        private const val MIN_Q = 0.05f
        private const val MIN_HZ = 10f
        private const val MAX_FREQUENCY_FRACTION = 0.45f
        private const val DENORMAL_FLOOR = 1e-12f
    }
}
