/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * A fine, still grain over a colour field — what makes iOS's artwork grounds read as a surface
 * rather than as a gradient on glass, and what stops a slow dark gradient from banding into
 * visible steps on an 8-bit panel.
 *
 * The grain is one 128px tile of blue-ish noise, made once per process and repeated by the GPU as
 * a shader: no allocation, no animation and no extra layer, so it costs a single textured rect
 * whenever the surface under it redraws anyway. Overlay blending pushes lights lighter and darks
 * darker around mid-grey, so it adds texture without washing a colour out or dimming it.
 */
fun Modifier.filmGrain(strength: Float = 0.06f): Modifier = drawWithContent {
    drawContent()
    if (strength <= 0f) return@drawWithContent
    drawRect(brush = GrainBrush, alpha = strength.coerceIn(0f, 1f), blendMode = BlendMode.Overlay)
}

private const val GrainTile = 128

private val GrainBitmap: ImageBitmap by lazy {
    // Fixed seed: the same grain every launch, so nothing crawls between screens.
    val random = Random(0x5EED)
    val pixels = IntArray(GrainTile * GrainTile)
    for (y in 0 until GrainTile) {
        for (x in 0 until GrainTile) {
            // Two octaves averaged: pure white noise reads as digital static, this as film.
            val fine = random.nextInt(256)
            val coarse = random.nextInt(256)
            val v = ((fine * 3 + coarse) / 4).coerceIn(0, 255)
            pixels[y * GrainTile + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    Bitmap.createBitmap(pixels, GrainTile, GrainTile, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val GrainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(GrainBitmap, TileMode.Repeated, TileMode.Repeated))
}
