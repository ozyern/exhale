/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extracts palette colors and builds player gradients from artwork. */
object PlayerColorExtractor {

    /** Neutral fallback keeps grayscale covers from inheriting the theme accent. */
    val NeutralFallbackColor = Color(0xFF1C1C1E)

    /** Selects a palette color and derives darker gradient colors. */
    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int
    ): List<Color> = withContext(Dispatchers.Default) {

        // Extract all available colors with priority for dominant colors
        val colorCandidates = listOfNotNull(
            palette.dominantSwatch, // High priority for dominant color
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch
        )

        // Select best color based on weight (dominance + vibrancy)
        val bestSwatch = colorCandidates.maxByOrNull { calculateColorWeight(it) }
        val fallbackDominant = palette.dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(palette.getDominantColor(fallbackColor))

        val primaryColor = when {
            // Don't amplify JPEG color noise on grayscale art; it can turn neutral covers green or magenta.
            isArtworkAchromatic(palette) -> neutralToneOf(bestSwatch?.rgb?.let(::Color) ?: fallbackDominant)

            bestSwatch != null && isColorVibrant(Color(bestSwatch.rgb)) ->
                enhanceColorVividness(Color(bestSwatch.rgb), VIBRANT_SATURATION_FACTOR)

            else -> enhanceColorVividness(fallbackDominant, FALLBACK_SATURATION_FACTOR)
        }

        listOf(
            primaryColor,
            primaryColor.copy(
                red = (primaryColor.red * 0.6f).coerceAtLeast(0f),
                green = (primaryColor.green * 0.6f).coerceAtLeast(0f),
                blue = (primaryColor.blue * 0.6f).coerceAtLeast(0f)
            ),
            Color.Black
        )
    }

    /** Uses population-weighted saturation so a few colorful pixels do not tint a neutral cover. */
    private fun isArtworkAchromatic(palette: Palette): Boolean {
        val hsv = FloatArray(3)
        val saturations = FloatArray(palette.swatches.size)
        val values = FloatArray(palette.swatches.size)
        val populations = IntArray(palette.swatches.size)
        palette.swatches.forEachIndexed { i, swatch ->
            android.graphics.Color.colorToHSV(swatch.rgb, hsv)
            saturations[i] = hsv[1]
            values[i] = hsv[2]
            populations[i] = swatch.population
        }
        return isAchromatic(saturations, values, populations)
    }

    /** Population-weighted HSV check, kept separate from Android conversion for JVM tests. */
    internal fun isAchromatic(
        saturations: FloatArray,
        values: FloatArray,
        populations: IntArray,
    ): Boolean {
        var weighted = 0.0
        var total = 0.0
        for (i in saturations.indices) {
            // Saturation is meaningless at the extremes of value — near-black and
            // near-white pixels report erratic hue. Weight them out rather than
            // letting them decide.
            val valueWeight = (values[i] * (1f - values[i]) * 4f).coerceIn(0f, 1f)
            val weight = populations[i].toDouble() * valueWeight
            weighted += saturations[i] * weight
            total += weight
        }
        if (total <= 0.0) return true
        return weighted / total < ACHROMATIC_SATURATION_THRESHOLD
    }

    /** Removes hue while preserving and clamping the artwork's lightness. */
    private fun neutralToneOf(color: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[1] = 0f
        hsv[2] = hsv[2].coerceIn(NEUTRAL_VALUE_MIN, NEUTRAL_VALUE_MAX)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** Mean population-weighted saturation below which artwork counts as greyscale. */
    private const val ACHROMATIC_SATURATION_THRESHOLD = 0.10f

    // Greyscale artwork keeps a wider value range than a tinted one: there is no
    // hue doing the work, so the tone itself has to carry the difference between a
    // black sleeve and a white one.
    private const val NEUTRAL_VALUE_MIN = 0.14f
    private const val NEUTRAL_VALUE_MAX = 0.72f

    private const val VIBRANT_SATURATION_FACTOR = 1.12f
    private const val FALLBACK_SATURATION_FACTOR = 1.0f

    /** Returns whether a color meets the UI's saturation and brightness thresholds. */
    private fun isColorVibrant(color: Color): Boolean {
        val argb = color.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        val saturation = hsv[1] // HSV[1] is saturation
        val brightness = hsv[2] // HSV[2] is brightness
        
        return saturation > 0.25f && brightness > 0.2f && brightness < 0.9f
    }
    
    /** Adjusts saturation and brightness while preserving the cover's original color. */
    private fun enhanceColorVividness(color: Color, saturationFactor: Float): Color {
        val argb = color.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)

        // Cap saturation so album art keeps its original character.
        hsv[1] = (hsv[1] * saturationFactor).coerceAtMost(0.82f)
        // Keep dark covers dark and limit brightness behind white text.
        hsv[2] = (hsv[2] * 0.92f).coerceIn(0.12f, 0.70f)

        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** Ranks palette colors by population, saturation, and brightness. */
    private fun calculateColorWeight(swatch: Palette.Swatch): Float {
        val population = swatch.population.toFloat()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        val saturation = hsv[1]
        val brightness = hsv[2]
        
        // Give higher priority to dominance (population) while considering vibrancy
        val populationWeight = population * 2f // Double dominance weight
        val vibrancyBonus = if (saturation > 0.3f && brightness > 0.3f) 1.5f else 1f
        
        return populationWeight * vibrancyBonus * (saturation + brightness) / 2f
    }
}
