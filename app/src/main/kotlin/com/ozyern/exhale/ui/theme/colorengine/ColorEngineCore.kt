/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Colour models and the perceptual maths behind them.
 *
 * Kotlin port of the Swift `Color Engine` sources (`ColorEngineColorModels.swift`,
 * `ColorEngineSupport.swift`, `ColorEnginePipelineModels.swift`). The Swift original
 * splits `CGFloat` (double) presentation colours from `Float` pixel points for speed;
 * that split is kept here for the same reason — [LabPoint] is the per-pixel type and
 * there are tens of thousands of them, [LabColor]/[LChColor] are the handful of
 * presentation colours where precision matters more than throughput.
 */

/**
 * How much scene understanding feeds the weight map.
 *
 * The Swift engine drives these from Apple's Vision framework (attention saliency,
 * text and face bounding boxes). There is no equivalent on Android without pulling in
 * ML Kit, so [VisionData] is an open seam: supply it and the balanced/detailed paths
 * behave exactly like the original, leave it null and the pipeline runs on geometric
 * and spatial weighting alone, which is what [DISABLED] means.
 */
enum class VisionMode { DISABLED, BALANCED, DETAILED }

/** Presentation profiles that bend saturation and lightness without favouring a hue. */
enum class ColorStyle { BALANCED, VIBRANT, PASTEL }

/** Perceptual tone of the source image, from its luminance statistics. */
enum class ImageTone { DARK, LIGHT }

/** Colour difference formulas in CIELAB. */
enum class DeltaEAlgorithm { EUCLIDEAN, CIE94, CIEDE2000 }

/** Geometric hue relationships on the LCh wheel. */
enum class ColorHarmony { COMPLEMENTARY, SPLIT_COMPLEMENTARY, TRIADIC, TETRADIC, ANALOGOUS }

data class LabColor(val l: Double, val a: Double, val b: Double) {

    fun deltaE(other: LabColor, algorithm: DeltaEAlgorithm = DeltaEAlgorithm.CIE94): Double =
        when (algorithm) {
            DeltaEAlgorithm.EUCLIDEAN -> {
                val dL = l - other.l
                val dA = a - other.a
                val dB = b - other.b
                sqrt(dL * dL + dA * dA + dB * dB)
            }

            DeltaEAlgorithm.CIE94 -> {
                val dL = l - other.l
                val c1 = sqrt(a * a + b * b)
                val c2 = sqrt(other.a * other.a + other.b * other.b)
                val dC = c1 - c2
                val dA = a - other.a
                val dB = b - other.b
                val dHSquared = max(0.0, dA * dA + dB * dB - dC * dC)
                val sC = 1 + 0.045 * c1
                val sH = 1 + 0.015 * c1
                val cTerm = dC / sC
                val hTerm = sqrt(dHSquared) / sH
                sqrt(dL * dL + cTerm * cTerm + hTerm * hTerm)
            }

            DeltaEAlgorithm.CIEDE2000 -> deltaECiede2000(other)
        }

    fun toLCh(): LChColor {
        val chroma = sqrt(a * a + b * b)
        var hue = Math.toDegrees(atan2(b, a))
        if (hue < 0) hue += 360.0
        return LChColor(l, chroma, hue)
    }

    /** CIE Y, i.e. the luminance WCAG contrast ratios are computed from. */
    val relativeLuminance: Double
        get() {
            val fy = (l + 16) / 116
            val delta = 6.0 / 29.0
            return if (fy > delta) fy * fy * fy else 3 * delta * delta * (fy - 4.0 / 29.0)
        }

    val isInSRGBGamut: Boolean
        get() = unclampedSRGB().all { it in -GamutEpsilon..(1.0 + GamutEpsilon) }

    fun toColor(): Color {
        // Desaturate toward the gamut rather than clipping channels: clipping shifts hue,
        // stepping chroma down does not. 18 steps of 0.9 is the Swift original's budget.
        var candidate = this
        repeat(18) {
            val components = candidate.unclampedSRGB()
            if (components.all { it in -GamutEpsilon..(1.0 + GamutEpsilon) }) {
                return Color(
                    components[0].coerceIn(0.0, 1.0).toFloat(),
                    components[1].coerceIn(0.0, 1.0).toFloat(),
                    components[2].coerceIn(0.0, 1.0).toFloat(),
                    1f,
                )
            }
            candidate = LabColor(candidate.l, candidate.a * 0.9, candidate.b * 0.9)
        }
        val components = candidate.unclampedSRGB().map { it.coerceIn(0.0, 1.0) }
        return Color(components[0].toFloat(), components[1].toFloat(), components[2].toFloat(), 1f)
    }

    private fun unclampedSRGB(): DoubleArray {
        val fy = (l + 16) / 116
        val fx = a / 500 + fy
        val fz = fy - b / 200
        val delta = 6.0 / 29.0
        fun inverseLab(value: Double): Double =
            if (value > delta) value * value * value else 3 * delta * delta * (value - 4.0 / 29.0)

        val x = inverseLab(fx) * 0.95047
        val y = inverseLab(fy)
        val z = inverseLab(fz) * 1.08883
        val linearRed = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val linearGreen = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val linearBlue = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        fun encodeSRGB(value: Double): Double =
            if (value <= 0.0031308) 12.92 * value else 1.055 * value.pow(1 / 2.4) - 0.055

        return doubleArrayOf(encodeSRGB(linearRed), encodeSRGB(linearGreen), encodeSRGB(linearBlue))
    }

    private fun deltaECiede2000(other: LabColor): Double {
        val l1 = l; val a1 = a; val b1 = b
        val l2 = other.l; val a2 = other.a; val b2 = other.b
        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val averageC = (c1 + c2) / 2
        val averageC7 = averageC.pow(7)
        val twentyFive7 = 25.0.pow(7)
        val g = 0.5 * (1 - sqrt(averageC7 / (averageC7 + twentyFive7)))
        val adjustedA1 = a1 * (1 + g)
        val adjustedA2 = a2 * (1 + g)
        val adjustedC1 = sqrt(adjustedA1 * adjustedA1 + b1 * b1)
        val adjustedC2 = sqrt(adjustedA2 * adjustedA2 + b2 * b2)

        fun hue(y: Double, x: Double): Double {
            if (x == 0.0 && y == 0.0) return 0.0
            var result = Math.toDegrees(atan2(y, x))
            if (result < 0) result += 360.0
            return result
        }

        val h1 = hue(b1, adjustedA1)
        val h2 = hue(b2, adjustedA2)
        val deltaC = adjustedC2 - adjustedC1
        val deltaHDegrees = when {
            adjustedC1 * adjustedC2 == 0.0 -> 0.0
            abs(h2 - h1) <= 180 -> h2 - h1
            h2 - h1 > 180 -> h2 - h1 - 360
            else -> h2 - h1 + 360
        }
        val deltaH = 2 * sqrt(adjustedC1 * adjustedC2) * sin(Math.toRadians(deltaHDegrees / 2))
        val averageL = (l1 + l2) / 2
        val averageAdjustedC = (adjustedC1 + adjustedC2) / 2
        val averageH = when {
            adjustedC1 * adjustedC2 == 0.0 -> h1 + h2
            abs(h1 - h2) <= 180 -> (h1 + h2) / 2
            h1 + h2 < 360 -> (h1 + h2 + 360) / 2
            else -> (h1 + h2 - 360) / 2
        }
        val hueRadians = Math.toRadians(averageH)
        val t = 1 -
            0.17 * cos(hueRadians - Math.toRadians(30.0)) +
            0.24 * cos(2 * hueRadians) +
            0.32 * cos(3 * hueRadians + Math.toRadians(6.0)) -
            0.20 * cos(4 * hueRadians - Math.toRadians(63.0))
        val lightnessOffset = averageL - 50
        val sL = 1 + 0.015 * lightnessOffset * lightnessOffset /
            sqrt(20 + lightnessOffset * lightnessOffset)
        val sC = 1 + 0.045 * averageAdjustedC
        val sH = 1 + 0.015 * averageAdjustedC * t
        val averageAdjustedC7 = averageAdjustedC.pow(7)
        val rC = 2 * sqrt(averageAdjustedC7 / (averageAdjustedC7 + twentyFive7))
        val deltaTheta = Math.toRadians(30 * exp(-((averageH - 275) / 25).pow(2)))
        val rT = -sin(2 * deltaTheta) * rC
        val lTerm = (l2 - l1) / sL
        val cTerm = deltaC / sC
        val hTerm = deltaH / sH
        return sqrt(lTerm * lTerm + cTerm * cTerm + hTerm * hTerm + rT * cTerm * hTerm)
    }

    companion object {
        val Zero = LabColor(0.0, 0.0, 0.0)

        /**
         * Half an 8-bit step. The question the gamut test actually needs to answer is
         * "does this round to a real sRGB byte", not "is it mathematically inside the
         * unit cube" -- and the LAB transform runs through an interpolated LUT, so a
         * pure primary lands a hair outside. Without this slack the desaturation loop
         * below fired on saturated source pixels and pure red came back as 245.
         */
        private const val GamutEpsilon = 1.0 / 512.0
    }
}

data class LChColor(val l: Double, val c: Double, val h: Double) {

    fun toLab(): LabColor {
        val radians = Math.toRadians(normalizedHue)
        return LabColor(l, c * cos(radians), c * sin(radians))
    }

    fun withLightness(value: Double) = LChColor(value, c, normalizedHue)
    fun withChroma(value: Double) = LChColor(l, max(0.0, value), normalizedHue)
    fun withHue(value: Double) = LChColor(l, c, normalizeHue(value))

    fun toColor(): Color = toLab().toColor()

    fun harmony(type: ColorHarmony): List<LChColor> = when (type) {
        ColorHarmony.COMPLEMENTARY -> listOf(withHue(h + 180))
        ColorHarmony.SPLIT_COMPLEMENTARY -> listOf(withHue(h + 150), withHue(h + 210))
        ColorHarmony.TRIADIC -> listOf(withHue(h + 120), withHue(h + 240))
        ColorHarmony.TETRADIC -> listOf(withHue(h + 90), withHue(h + 180), withHue(h + 270))
        ColorHarmony.ANALOGOUS -> listOf(withHue(h - 30), withHue(h + 30))
    }

    fun allHarmonies(): Map<ColorHarmony, List<LChColor>> =
        ColorHarmony.entries.associateWith { harmony(it) }

    private val normalizedHue: Double get() = normalizeHue(h)

    private companion object {
        fun normalizeHue(hue: Double): Double {
            val value = hue % 360
            return if (value < 0) value + 360 else value
        }
    }
}

/** The per-pixel colour type. Float, because there is one per sampled pixel. */
data class LabPoint(val l: Float, val a: Float, val b: Float) {
    val chroma: Float get() = sqrt(a * a + b * b)
    val color: LabColor get() = LabColor(l.toDouble(), a.toDouble(), b.toDouble())

    fun squaredDistance(other: LabPoint): Float {
        val dL = l - other.l
        val dA = a - other.a
        val dB = b - other.b
        return dL * dL + dA * dA + dB * dB
    }
}

/** sRGB byte -> linear light. 256 entries, so the per-pixel path never calls pow(). */
private val sRGBLinear8BitLUT: FloatArray = FloatArray(256) { byte ->
    val value = byte / 255f
    if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}

private const val LabTransformResolution = 4096

/** The f(t) leg of XYZ->Lab, sampled and linearly interpolated. */
private val labTransformLUT: FloatArray = FloatArray(LabTransformResolution + 1) { index ->
    val value = index.toFloat() / LabTransformResolution
    val delta = 6f / 29f
    if (value > delta * delta * delta) {
        value.toDouble().pow(1.0 / 3.0).toFloat()
    } else {
        value / (3 * delta * delta) + 4f / 29f
    }
}

fun rgb8ToLab(red: Int, green: Int, blue: Int): LabPoint =
    linearRgbToLab(
        sRGBLinear8BitLUT[red and 0xFF],
        sRGBLinear8BitLUT[green and 0xFF],
        sRGBLinear8BitLUT[blue and 0xFF],
    )

fun rgbToLab(red: Float, green: Float, blue: Float): LabPoint {
    fun linearize(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f
        else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return linearRgbToLab(linearize(red), linearize(green), linearize(blue))
}

fun linearRgbToLab(red: Float, green: Float, blue: Float): LabPoint {
    val x = (0.4124564f * red + 0.3575761f * green + 0.1804375f * blue) / 0.95047f
    val y = 0.2126729f * red + 0.7151522f * green + 0.0721750f * blue
    val z = (0.0193339f * red + 0.1191920f * green + 0.9503041f * blue) / 1.08883f

    fun labTransform(value: Float): Float {
        val position = value.coerceIn(0f, 1f) * LabTransformResolution
        val lowerIndex = position.toInt()
        val upperIndex = min(LabTransformResolution, lowerIndex + 1)
        val fraction = position - lowerIndex
        val lower = labTransformLUT[lowerIndex]
        return lower + (labTransformLUT[upperIndex] - lower) * fraction
    }

    val fx = labTransform(x)
    val fy = labTransform(y)
    val fz = labTransform(z)
    return LabPoint(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

/** Hermite interpolation between two edges — the engine's only easing primitive. */
fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** Shortest angular distance between two hues, in degrees. */
fun hueDistance(first: Double, second: Double): Double {
    val direct = abs(first - second) % 360
    return min(direct, 360 - direct)
}

/**
 * The deterministic PRNG the k-means++ seeding draws from.
 *
 * Determinism is the whole point: the seed is the pixel fingerprint, so the same
 * artwork always produces the same palette. A shared [kotlin.random.Random] would make
 * the result depend on call order.
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed + -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    fun next(): Long {
        state += -0x61c8864680b583ebL
        var value = state
        value = (value xor (value ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        value = (value xor (value ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return value xor (value ushr 31)
    }

    fun nextUnitDouble(): Double = (next() ushr 11).toDouble() / (1L shl 53).toDouble()
}

const val FnvOffsetBasis: Long = -3750763034362895579L // 14695981039346656037 unsigned
private const val FnvPrime: Long = 1099511628211L

fun fnvMix(hash: Long, byte: Int): Long = (hash xor (byte.toLong() and 0xFF)) * FnvPrime

/** A Compose colour as a LAB colour, through the same 8-bit LUT the pixel path uses. */
fun Color.toLabColor(): LabColor = rgb8ToLab(
    (red.coerceIn(0f, 1f) * 255).roundToInt(),
    (green.coerceIn(0f, 1f) * 255).roundToInt(),
    (blue.coerceIn(0f, 1f) * 255).roundToInt(),
).color

fun Color.toHex(): String {
    fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255).roundToInt()
    return "#%02X%02X%02X".format(channel(red), channel(green), channel(blue))
}
