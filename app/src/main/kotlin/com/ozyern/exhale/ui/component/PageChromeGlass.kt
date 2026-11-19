/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.backdrop.drawBackdrop
import com.ozyern.exhale.ui.component.backdrop.effects.blur
import com.ozyern.exhale.ui.component.backdrop.effects.colorControls
import com.ozyern.exhale.ui.component.backdrop.effects.lens
import com.ozyern.exhale.ui.component.backdrop.highlight.Highlight
import com.ozyern.exhale.ui.component.backdrop.shadow.Shadow

/**
 * The glass of the controls that float over a page: the back circle and the action pill.
 *
 * A lens over the page rather than a panel on it: the colour behind lifted, a light blur, a deep
 * bend at the rim, then a dark film. So on a maroon record the controls are dark maroon glass, and
 * on a blue one blue, where a pale frost read as the same grey on every page.
 */
@Composable
fun Modifier.pageChromeGlass(shape: CornerBasedShape): Modifier {
    if (!isGlassAllowed()) {
        return background(GlassFilm.copy(alpha = 0.6f), shape).border(0.5.dp, GlassEdge, shape)
    }
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    // A small control hides a third-resolution capture completely, and it costs a ninth.
    val blurPx = with(density) { 8.dp.toPx() } * CaptureScale
    val lensPx = with(density) { 24.dp.toPx() } * CaptureScale
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.5f)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(refractionHeight = lensPx, refractionAmount = lensPx, depthEffect = true, chromaticAberration = true)
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(GlassFilm.copy(alpha = 0.4f)) },
        backdropScale = CaptureScale,
    ).border(0.5.dp, GlassEdge, shape)
}

private const val CaptureScale = 0.33f
private val GlassFilm = Color(0xFF121212)
private val GlassEdge = Color.White.copy(alpha = 0.10f)
