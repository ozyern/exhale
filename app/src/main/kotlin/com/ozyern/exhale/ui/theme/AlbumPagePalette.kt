/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * An album page painted in its record's colour: one deep tint behind everything, the title in
 * white, and the credits, facts and notes in a pale tint of that same hue, so a purple record's
 * credits read lavender on its purple page rather than as translucent white.
 *
 * Every colour keeps the seed's hue; only saturation and lightness are set, each held to a band
 * that keeps white text legible on [background] and [onBackgroundVariant] legible on it too.
 */
data class AlbumPagePalette(
    /** The page: deep, but still recognisably the record's colour. */
    val background: Color,
    /** Cards and raised surfaces, a step lighter than the page. */
    val elevated: Color,
    /** Titles. */
    val onBackground: Color,
    /** Artist, facts, notes and row subtitles: the page's hue, lifted. */
    val onBackgroundVariant: Color,
    /** Links and highlights: the hue, vivid. */
    val accent: Color,
    val divider: Color,
)

/** The palette for an album whose artwork's main colour is [seedRgb] (0xRRGGBB, alpha ignored). */
fun albumPagePalette(seedRgb: Int): AlbumPagePalette {
    val seed = Color(seedRgb or 0xFF000000.toInt())
    return AlbumPagePalette(
        background = seed.withSaturationLightness(saturation = { it.inBandIfChromatic(0.20f, 0.62f) }, lightness = { 0.13f }),
        elevated = seed.withSaturationLightness(saturation = { it.inBandIfChromatic(0.20f, 0.62f) }, lightness = { 0.22f }),
        onBackground = Color.White,
        onBackgroundVariant = seed.withSaturationLightness(saturation = { it.inBandIfChromatic(0.22f, 0.50f) }, lightness = { 0.80f }),
        accent = seed.withSaturationLightness(saturation = { it.inBandIfChromatic(0.55f, 1f) }, lightness = { it.coerceIn(0.62f, 0.78f) }),
        divider = Color.White.copy(alpha = 0.12f),
    )
}

/**
 * Below this a colour is grey, and HSL gives grey a hue of zero: raised to a saturation floor it
 * would turn every black-and-white record's page red-brown. Greys stay grey.
 */
private const val ChromaticSaturation = 0.12f

private fun Float.inBandIfChromatic(minimum: Float, maximum: Float): Float {
    val s = coerceIn(0f, 1f)
    return if (s < ChromaticSaturation) s else s.coerceIn(minimum, maximum)
}

private fun Color.withSaturationLightness(saturation: (Float) -> Float, lightness: (Float) -> Float): Color {
    val (h, sat, light) = toHsl()
    return hslColor(h, saturation(sat).coerceIn(0f, 1f), lightness(light).coerceIn(0f, 1f))
}

/** Hue in degrees, saturation and lightness 0..1. Plain arithmetic, so it runs in unit tests too. */
internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (max) {
        red -> 60f * (((green - blue) / d) % 6f)
        green -> 60f * (((blue - red) / d) + 2f)
        else -> 60f * (((red - green) / d) + 4f)
    }
    return Triple(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
}

internal fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}
