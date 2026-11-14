/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Stages 1, 3 and 4 of the pipeline: sample the artwork down, build the spatial weight
 * map, and derive the tone statistics the scheme builder needs.
 *
 * Kotlin port of `ColorEngineNormalization.swift` and `ColorEngineWeighting.swift`.
 * CoreImage's `render` (centre-crop to the target aspect, high-quality downsample,
 * read back RGBA8) becomes a `Bitmap` crop plus [Bitmap.scale]; everything downstream
 * of the pixel read is the same arithmetic on the same values.
 */

/** A rectangle in normalised image coordinates, origin bottom-left (Vision's convention). */
data class NormalizedRegion(val x: Double, val y: Double, val width: Double, val height: Double) {
    val maxX get() = x + width
    val maxY get() = y + height
}

/**
 * Scene understanding for the weight map — saliency per sampled pixel plus text and
 * face regions. Nothing in this module produces one; it is the seam an ML Kit (or any
 * other) detector plugs into. Null means [VisionMode.DISABLED] behaviour.
 */
data class VisionData(
    val saliencyMap: FloatArray,
    val textRegions: List<NormalizedRegion>,
    val faceRegions: List<NormalizedRegion>,
    val salientRegions: List<NormalizedRegion>,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class PixelPlane(
    val width: Int,
    val height: Int,
    val points: Array<LabPoint>,
    val alpha: FloatArray,
    val fingerprint: Long,
) {
    val count: Int get() = points.size
}

class WeightMap(
    val background: FloatArray,
    val colorInfluence: FloatArray,
    val textMembership: FloatArray,
    val saliency: FloatArray,
)

data class DetailAssessment(
    val variance: Float,
    val edgeEnergy: Float,
    val chromaSpread: Float,
    val score: Float,
    val thumbnailSize: Int,
    val requestedClusterCount: Int,
)

data class ToneStatistics(
    val tone: ImageTone,
    val confidence: Float,
    val weightedMean: Float,
    val weightedMedian: Float,
    val percentile65: Float,
    val darkCoverage: Float,
    val lightCoverage: Float,
    val chromaticCoverage: Float,
    val strongChromaticCoverage: Float,
    val chromaticConfidence: Float,
)

/**
 * Centre-crops [source] to [maximumDimension]'s aspect and scales it down, then reads
 * the pixels back as ARGB. Returns null when the bitmap is unusable.
 */
internal fun sampleBitmap(source: Bitmap, maximumDimension: Int): IntArray? {
    val (width, height) = sampleDimensions(source.width, source.height, maximumDimension)
    if (width <= 0 || height <= 0) return null

    val targetAspect = width.toFloat() / height
    val sourceAspect = source.width.toFloat() / source.height
    val crop = if (sourceAspect > targetAspect) {
        val cropWidth = (source.height * targetAspect).roundToInt().coerceAtLeast(1)
        val left = (source.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, source.height)
    } else {
        val cropHeight = (source.width / targetAspect).roundToInt().coerceAtLeast(1)
        val top = (source.height - cropHeight) / 2
        Rect(0, top, source.width, top + cropHeight)
    }

    val cropped = if (crop.width() == source.width && crop.height() == source.height) {
        source
    } else {
        runCatching {
            Bitmap.createBitmap(source, crop.left, crop.top, crop.width(), crop.height())
        }.getOrNull() ?: return null
    }
    val scaled = if (cropped.width == width && cropped.height == height) {
        cropped
    } else {
        runCatching { cropped.scale(width, height) }.getOrNull() ?: return null
    }

    val pixels = IntArray(width * height)
    runCatching { scaled.getPixels(pixels, 0, width, 0, 0, width, height) }
        .getOrElse { return null }

    if (scaled !== cropped && !scaled.isRecycled) scaled.recycle()
    if (cropped !== source && !cropped.isRecycled) cropped.recycle()
    return pixels
}

internal fun sampleDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    maximumDimension: Int,
): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0) return maximumDimension to maximumDimension
    val aspect = sourceWidth.toFloat() / sourceHeight
    return if (aspect >= 1f) {
        maximumDimension to max(1, (maximumDimension / aspect).roundToInt())
    } else {
        max(1, (maximumDimension * aspect).roundToInt()) to maximumDimension
    }
}

/**
 * Stage 1: a 16px preview decides how much resolution and how many clusters the real
 * pass gets. Flat artwork does not need 8 clusters at 128px, and busy artwork looks
 * wrong with 5 at 64px.
 */
internal fun assessDetail(source: Bitmap, visionMode: VisionMode): DetailAssessment? {
    val (previewWidth, previewHeight) = sampleDimensions(source.width, source.height, 16)
    val pixels = sampleBitmap(source, 16) ?: return null
    val pixelCount = previewWidth * previewHeight
    if (pixelCount <= 0 || pixels.size < pixelCount) return null

    val luminance = FloatArray(pixelCount)
    val chroma = FloatArray(pixelCount)
    var luminanceSum = 0f
    for (index in 0 until pixelCount) {
        val pixel = pixels[index]
        val red = ((pixel shr 16) and 0xFF) / 255f
        val green = ((pixel shr 8) and 0xFF) / 255f
        val blue = (pixel and 0xFF) / 255f
        val value = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        luminance[index] = value
        luminanceSum += value
        chroma[index] = max(red, max(green, blue)) - min(red, min(green, blue))
    }
    val mean = luminanceSum / pixelCount
    var variance = 0f
    var chromaMean = 0f
    for (index in 0 until pixelCount) {
        val delta = luminance[index] - mean
        variance += delta * delta
        chromaMean += chroma[index]
    }
    variance /= pixelCount
    chromaMean /= pixelCount

    var edgeEnergy = 0f
    var edgeCount = 0
    for (y in 0 until previewHeight) {
        for (x in 0 until previewWidth) {
            val index = y * previewWidth + x
            if (x + 1 < previewWidth) {
                edgeEnergy += abs(luminance[index] - luminance[index + 1])
                edgeCount++
            }
            if (y + 1 < previewHeight) {
                edgeEnergy += abs(luminance[index] - luminance[index + previewWidth])
                edgeCount++
            }
        }
    }
    if (edgeCount > 0) edgeEnergy /= edgeCount

    val score = (variance * 3.8f + edgeEnergy * 0.9f + chromaMean * 0.22f).coerceIn(0f, 1f)
    val thumbnailSize = when (visionMode) {
        VisionMode.DISABLED -> if (score < 0.105f) 64 else 96
        VisionMode.BALANCED -> if (score < 0.105f) 80 else 112
        VisionMode.DETAILED -> if (score < 0.105f) 96 else 128
    }
    val clusterCount = when {
        score < 0.065f -> 5
        score < 0.16f -> 7
        else -> 8
    }
    return DetailAssessment(
        variance = variance,
        edgeEnergy = edgeEnergy,
        chromaSpread = chromaMean * 100,
        score = score,
        thumbnailSize = thumbnailSize,
        requestedClusterCount = clusterCount,
    )
}

/**
 * Stage 1b: ARGB pixels to a plane of LAB points plus their alphas, and an FNV
 * fingerprint of the bytes. The fingerprint seeds the k-means PRNG, which is what makes
 * a given artwork always produce the same palette.
 */
internal fun makePixelPlane(pixels: IntArray, width: Int, height: Int): PixelPlane? {
    val count = width * height
    if (count <= 0 || pixels.size < count) return null

    val alpha = FloatArray(count)
    var fingerprint = FnvOffsetBasis
    val points = Array(count) { index ->
        val pixel = pixels[index]
        val alphaByte = (pixel ushr 24) and 0xFF
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val alphaValue = alphaByte / 255f
        val point = if (alphaValue >= 0.999f) {
            rgb8ToLab(red, green, blue)
        } else {
            // getPixels hands back straight (un-premultiplied) ARGB, but a
            // premultiplied source that round-tripped through a canvas would not, so
            // undo it the same way the Swift original does before converting.
            val divisor = max(alphaValue, 1f / 255f)
            rgbToLab(
                (red / 255f / divisor).coerceIn(0f, 1f),
                (green / 255f / divisor).coerceIn(0f, 1f),
                (blue / 255f / divisor).coerceIn(0f, 1f),
            )
        }
        alpha[index] = if (alphaValue < 0.02f) 0f else alphaValue
        fingerprint = fnvMix(fingerprint, red)
        fingerprint = fnvMix(fingerprint, green)
        fingerprint = fnvMix(fingerprint, blue)
        fingerprint = fnvMix(fingerprint, alphaByte)
        point
    }
    return PixelPlane(width, height, points, alpha, fingerprint)
}

/**
 * Stage 3: how much each pixel counts toward "what colour is this artwork".
 *
 * Centre pixels count slightly more than edge ones, corners slightly less, and — when
 * [vision] is supplied — text pixels gain identity weight while skin tones lose
 * background weight (so a face does not become the app background).
 */
internal fun makeWeightMap(
    plane: PixelPlane,
    vision: VisionData?,
    visionMode: VisionMode,
): WeightMap {
    val count = plane.count
    val background = FloatArray(count)
    val colorInfluence = FloatArray(count) { 1f }
    val textMembership = FloatArray(count)

    val saliencyStrength = when (visionMode) {
        VisionMode.DISABLED -> 0f
        VisionMode.BALANCED -> 0.20f
        VisionMode.DETAILED -> 0.28f
    }
    val rawSaliency = vision?.saliencyMap?.takeIf { it.size == count }
    // Centred on 0.5 so non-salient but authentic artwork colours are attenuated, never
    // eliminated.
    val saliency = rawSaliency?.let { source ->
        FloatArray(count) { 0.5f + (source[it] - 0.5f) * saliencyStrength }
    }

    val centerX = (plane.width - 1) / 2f
    val centerY = (plane.height - 1) / 2f
    val maximumDistance = max(1f, sqrt(centerX * centerX + centerY * centerY))

    for (y in 0 until plane.height) {
        for (x in 0 until plane.width) {
            val index = y * plane.width + x
            val alpha = plane.alpha[index]
            if (alpha <= 0f) continue
            val xDistance = x - centerX
            val yDistance = y - centerY
            val distance = sqrt(xDistance * xDistance + yDistance * yDistance)
            val centerWeight = 1 + 0.22f * (1 - distance / maximumDistance)
            val saliencyValue = saliency?.get(index) ?: 0.5f
            val saliencyWeight = 0.82f + 0.43f * saliencyValue
            val cornerWeight = if (isCorner(x, y, plane.width, plane.height)) 0.72f else 1f
            background[index] = alpha * centerWeight * saliencyWeight * cornerWeight
        }
    }

    if (vision != null) {
        val textEvidenceFloor = if (visionMode == VisionMode.DETAILED) 0.16f else 0.12f
        val textEdgeScale = if (visionMode == VisionMode.DETAILED) 0.74f else 0.68f
        for (region in vision.textRegions) {
            applyRegion(region, plane.width, plane.height) { index ->
                val edgeSupport = textEdgeSupport(index, plane)
                textMembership[index] = max(
                    textMembership[index],
                    textEvidenceFloor + edgeSupport * textEdgeScale,
                )
            }
        }
        val skinBackgroundPenalty = if (visionMode == VisionMode.DETAILED) 0.08f else 0.06f
        for (region in vision.faceRegions) {
            applyRegion(region, plane.width, plane.height) { index ->
                val skinInfluence = skinToneInfluence(plane.points[index])
                background[index] *= 1 - skinInfluence * skinBackgroundPenalty
            }
        }
    }

    var total = 0f
    for (value in background) total += value
    val resolvedBackground = if (total <= 0f) plane.alpha.copyOf() else background

    return WeightMap(
        background = resolvedBackground,
        colorInfluence = colorInfluence,
        textMembership = textMembership,
        saliency = saliency ?: FloatArray(count) { 0.5f },
    )
}

private inline fun applyRegion(
    region: NormalizedRegion,
    width: Int,
    height: Int,
    action: (Int) -> Unit,
) {
    val minimumX = max(0, (region.x * width).toInt())
    val maximumX = min(width, ceil(region.maxX * width).toInt())
    // Vision regions are bottom-left origin; the pixel plane is top-left.
    val minimumY = max(0, ((1 - region.maxY) * height).toInt())
    val maximumY = min(height, ceil((1 - region.y) * height).toInt())
    if (minimumX >= maximumX || minimumY >= maximumY) return
    for (y in minimumY until maximumY) {
        for (x in minimumX until maximumX) {
            action(y * width + x)
        }
    }
}

private fun isCorner(x: Int, y: Int, width: Int, height: Int): Boolean {
    val horizontalMargin = max(1, (width * 0.08f).toInt())
    val verticalMargin = max(1, (height * 0.08f).toInt())
    val isHorizontalEdge = x < horizontalMargin || x >= width - horizontalMargin
    val isVerticalEdge = y < verticalMargin || y >= height - verticalMargin
    return isHorizontalEdge && isVerticalEdge
}

/** How much this point looks like skin: the intersection of four soft bands in LAB. */
internal fun skinToneInfluence(point: LabPoint): Float {
    val chroma = point.chroma
    val lightnessSupport = smoothStep(8f, 20f, point.l) * (1 - smoothStep(94f, 100f, point.l))
    val redSupport = smoothStep(1f, 8f, point.a) * (1 - smoothStep(34f, 48f, point.a))
    val yellowSupport = smoothStep(2f, 9f, point.b) * (1 - smoothStep(42f, 56f, point.b))
    val chromaSupport = smoothStep(6f, 12f, chroma) * (1 - smoothStep(54f, 70f, chroma))
    return min(min(lightnessSupport, redSupport), min(yellowSupport, chromaSupport))
}

/** Mean colour distance to the four neighbours — high inside glyph edges, low in fills. */
private fun textEdgeSupport(index: Int, plane: PixelPlane): Float {
    val x = index % plane.width
    val y = index / plane.width
    val point = plane.points[index]
    var distance = 0f
    var count = 0f
    if (x > 0) {
        distance += sqrt(point.squaredDistance(plane.points[index - 1])); count++
    }
    if (x + 1 < plane.width) {
        distance += sqrt(point.squaredDistance(plane.points[index + 1])); count++
    }
    if (y > 0) {
        distance += sqrt(point.squaredDistance(plane.points[index - plane.width])); count++
    }
    if (y + 1 < plane.height) {
        distance += sqrt(point.squaredDistance(plane.points[index + plane.width])); count++
    }
    if (count == 0f) return 0f
    return smoothStep(4f, 28f, distance / count)
}

/**
 * Stage 4: is this artwork dark or light, and how sure are we?
 *
 * Chroma is deliberately excluded from the classification — a vivid dark cover is dark,
 * however saturated it is.
 */
internal fun analyzeTone(plane: PixelPlane): ToneStatistics {
    var totalWeight = 0f
    var weightedMean = 0f
    var darkWeight = 0f
    var lightWeight = 0f
    var chromaticWeight = 0f
    var strongChromaticWeight = 0f
    val histogramResolution = 0.5f
    val histogramBinCount = 201
    val lightnessHistogram = FloatArray(histogramBinCount)

    for (index in 0 until plane.count) {
        val weight = plane.alpha[index]
        if (weight <= 0f) continue
        val point = plane.points[index]
        totalWeight += weight
        weightedMean += point.l * weight
        val bin = (point.l / histogramResolution).roundToInt().coerceIn(0, histogramBinCount - 1)
        lightnessHistogram[bin] += weight
        if (point.l < 43f) darkWeight += weight
        if (point.l > 61f) lightWeight += weight
        if (point.chroma >= 12f) chromaticWeight += weight
        if (point.chroma >= 24f) strongChromaticWeight += weight
    }

    totalWeight = max(totalWeight, Float.MIN_VALUE)
    weightedMean /= totalWeight
    val median = histogramPercentile(lightnessHistogram, totalWeight, 0.5f, histogramResolution)
    val percentile65 = histogramPercentile(lightnessHistogram, totalWeight, 0.65f, histogramResolution)
    val darkCoverage = darkWeight / totalWeight
    val lightCoverage = lightWeight / totalWeight
    val chromaticCoverage = chromaticWeight / totalWeight
    val strongChromaticCoverage = strongChromaticWeight / totalWeight

    val coverageBias = (lightCoverage - darkCoverage) * 9
    val perceptualLightness =
        0.42f * weightedMean + 0.38f * median + 0.20f * percentile65 + coverageBias
    val threshold = 50f
    val tone = if (perceptualLightness < threshold) ImageTone.DARK else ImageTone.LIGHT
    val confidence = (
        abs(perceptualLightness - threshold) / 26 +
            abs(lightCoverage - darkCoverage) * 0.28f
        ).coerceIn(0f, 1f)
    val chromaticConfidence =
        (chromaticCoverage * 0.68f + strongChromaticCoverage * 0.72f).coerceIn(0f, 1f)

    return ToneStatistics(
        tone = tone,
        confidence = confidence,
        weightedMean = weightedMean,
        weightedMedian = median,
        percentile65 = percentile65,
        darkCoverage = darkCoverage,
        lightCoverage = lightCoverage,
        chromaticCoverage = chromaticCoverage,
        strongChromaticCoverage = strongChromaticCoverage,
        chromaticConfidence = chromaticConfidence,
    )
}

private fun histogramPercentile(
    histogram: FloatArray,
    totalWeight: Float,
    percentile: Float,
    resolution: Float,
): Float {
    val target = totalWeight * percentile
    var accumulated = 0f
    for (index in histogram.indices) {
        accumulated += histogram[index]
        if (accumulated >= target) return index * resolution
    }
    return (histogram.size - 1) * resolution
}

/** Plain and weight-map-weighted mean colours, for the palette's metadata. */
internal fun calculateAverages(plane: PixelPlane, weights: FloatArray): Pair<LabColor, LabColor> {
    var totalAlpha = 0f
    var totalWeight = 0f
    var rawL = 0f; var rawA = 0f; var rawB = 0f
    var weightedL = 0f; var weightedA = 0f; var weightedB = 0f

    for (index in 0 until plane.count) {
        val alpha = plane.alpha[index]
        val weight = weights[index]
        val point = plane.points[index]
        rawL += point.l * alpha; rawA += point.a * alpha; rawB += point.b * alpha
        totalAlpha += alpha
        weightedL += point.l * weight; weightedA += point.a * weight; weightedB += point.b * weight
        totalWeight += weight
    }
    if (totalAlpha > 0f) { rawL /= totalAlpha; rawA /= totalAlpha; rawB /= totalAlpha }
    val raw = LabPoint(rawL, rawA, rawB)
    val weighted = if (totalWeight > 0f) {
        LabPoint(weightedL / totalWeight, weightedA / totalWeight, weightedB / totalWeight)
    } else {
        raw
    }
    return raw.color to weighted.color
}
