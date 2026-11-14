package com.ozyern.artwork

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

data class MeshColor(val red: Float, val green: Float, val blue: Float) {
    fun saturated(amount: Float): MeshColor {
        val r = (red * 255).roundToInt() / 255f
        val g = (green * 255).roundToInt() / 255f
        val b = (blue * 255).roundToInt() / 255f
        val maximum = maxOf(r, g, b)
        val minimum = minOf(r, g, b)
        val lightness = (maximum + minimum) / 2f
        val range = maximum - minimum
        val saturation = if (range > 0f) range / (1f - abs(2f * lightness - 1f)) else 0f
        if (saturation < .08f) return this
        val factor = minOf(amount, 1f / saturation)
        return MeshColor(
            lightness + (r - lightness) * factor,
            lightness + (g - lightness) * factor,
            lightness + (b - lightness) * factor,
        )
    }
}

data class ArtworkMeshPalette(val cells: List<MeshColor>, val average: MeshColor, val sourceAverage: MeshColor = average) {
    init { require(cells.size == 9) }

    companion object {
        val Fallback = ArtworkMeshPalette(
            listOf(0x101E3D, 0x2E1A57, 0x0F3B4D, 0x38143D, 0x24476B, 0x4D1F4D, 0x0D2E42, 0x291A4D, 0x121C33)
                .map(::color),
            color(0x171D32),
        )

        fun fromPixels(pixels: IntArray, width: Int, height: Int): ArtworkMeshPalette {
            require(width > 0 && height > 0 && width.toLong() * height <= pixels.size)
            fun average(left: Int, top: Int, right: Int, bottom: Int): MeshColor {
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0
                for (y in top until bottom step 2) for (x in left until right step 2) {
                    val pixel = pixels[y * width + x]
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                    count++
                }
                if (count == 0) return color(pixels[(top.coerceAtMost(height - 1)) * width + left.coerceAtMost(width - 1)])
                val scale = count * 255f
                return MeshColor(red / scale, green / scale, blue / scale)
            }
            val cells = List(9) { index ->
                val column = index % 3
                val row = index / 3
                average(column * width / 3, row * height / 3, (column + 1) * width / 3, (row + 1) * height / 3)
                    .saturated(1.2f)
            }
            val sourceAverage = average(0, 0, width, height)
            return ArtworkMeshPalette(cells, sourceAverage.saturated(1.1f), sourceAverage)
        }

        /**
         * A palette spun from one accent colour, for pages with no artwork to sample: nine neighbours of the
         * seed's hue at different lightness, so the mesh still drifts through colour instead of sitting flat.
         */
        fun fromSeed(argb: Int): ArtworkMeshPalette {
            val seed = color(argb)
            val maximum = maxOf(seed.red, seed.green, seed.blue)
            val minimum = minOf(seed.red, seed.green, seed.blue)
            val delta = maximum - minimum
            val hue = when {
                delta == 0f -> 0f
                maximum == seed.red -> 60f * (((seed.green - seed.blue) / delta) % 6f)
                maximum == seed.green -> 60f * ((seed.blue - seed.red) / delta + 2f)
                else -> 60f * ((seed.red - seed.green) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val saturation = if (maximum == 0f) 0f else (delta / maximum).coerceAtLeast(.55f)
            val hueShifts = floatArrayOf(-28f, 0f, 24f, 14f, 0f, -18f, 30f, -10f, 6f)
            val values = floatArrayOf(.42f, .62f, .38f, .55f, .72f, .36f, .5f, .66f, .4f)
            val cells = List(9) { hsv(hue + hueShifts[it], saturation, values[it]) }
            val average = hsv(hue, saturation, .5f)
            return ArtworkMeshPalette(cells, average, average)
        }

        private fun hsv(hue: Float, saturation: Float, value: Float): MeshColor {
            val h = ((hue % 360f) + 360f) % 360f / 60f
            val c = value * saturation
            val x = c * (1f - abs(h % 2f - 1f))
            val m = value - c
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return MeshColor(r + m, g + m, b + m)
        }

        private fun color(argb: Int) = MeshColor(
            ((argb ushr 16) and 255) / 255f,
            ((argb ushr 8) and 255) / 255f,
            (argb and 255) / 255f,
        )
    }
}

class ArtworkMesh(val width: Int = 32, val height: Int = width * 23 / 10) {
    init { require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE) }

    private val centersX = FloatArray(9)
    private val centersY = FloatArray(9)
    private val frequencyX = floatArrayOf(.50f, .25f, .75f, .50f, .25f, .75f, .50f, .25f, .75f)
    private val frequencyY = floatArrayOf(.25f, .75f, .50f, .75f, .50f, .25f, .50f, .25f, .75f)

    fun render(palette: ArtworkMeshPalette, phase: Float, pixels: IntArray) {
        require(pixels.size >= width * height)
        val time = if (phase.isFinite()) phase % PhasePeriod else 0f
        for (index in 0..8) {
            centersX[index] = .5f + .3f * sin(time * frequencyX[index] + index * 1.7f)
            centersY[index] = .5f + .3f * cos(time * frequencyY[index] + index * .9f)
        }
        val dimension = maxOf(width, height).toFloat()
        val widthScale = width / dimension
        val heightScale = height / dimension
        var output = 0
        for (y in 0 until height) for (x in 0 until width) {
            val u = x.toFloat() / (width - 1).coerceAtLeast(1)
            val v = y.toFloat() / (height - 1).coerceAtLeast(1)
            val warpX = sin(v * 3.1f + time) * cos(u * 2.3f - time * .75f) +
                .5f * sin((u + v) * 4.9f - time * 1.25f)
            val warpY = cos(u * 2.9f - time) * sin(v * 2.6f + time * .5f) +
                .5f * cos((u - v) * 5.4f + time)
            var sampleU = u + .15f * warpX
            var sampleV = v + .15f * warpY
            sampleU += .15f * .28f * sin(sampleV * 6.7f - time * 1.5f)
            sampleV += .15f * .28f * cos(sampleU * 7.1f + time * 1.5f)
            var weightSum = .16f
            var red = palette.average.red * .16f
            var green = palette.average.green * .16f
            var blue = palette.average.blue * .16f
            for (index in 0..8) {
                val dx = (sampleU - centersX[index]) * widthScale / .45f
                val dy = (sampleV - centersY[index]) * heightScale / .45f
                val falloff = 1f / (1f + (dx * dx + dy * dy) * 4.5f)
                val weight = falloff * falloff
                weightSum += weight
                red += palette.cells[index].red * weight
                green += palette.cells[index].green * weight
                blue += palette.cells[index].blue * weight
            }
            pixels[output++] = (255 shl 24) or
                (((red / weightSum).coerceIn(0f, 1f) * 255).toInt() shl 16) or
                (((green / weightSum).coerceIn(0f, 1f) * 255).toInt() shl 8) or
                ((blue / weightSum).coerceIn(0f, 1f) * 255).toInt()
        }
    }

    companion object {
        const val PhasePeriod = 25.132742f
        const val RadiansPerSecond = 1.92f
    }
}
