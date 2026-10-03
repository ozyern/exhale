/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * The app's mark — the transparent splash art, not the square icon — sitting in a disc of real
 * liquid glass: whatever scrolls under the bar is blurred, saturated and bent at the rim live, so
 * the disc is a lens over the page rather than a plate painted on it.
 *
 * [backdrop] must be a recording of content this is drawn *over*, never one it is drawn inside —
 * in the top bar that is the app's NavHost recording, which the bar is a sibling of.
 */
@Composable
fun LiquidGlassMark(
    @DrawableRes markRes: Int,
    backdrop: Backdrop,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    /**
     * Draws [markRes] as a silhouette in this colour, in a disc of [clearGlass] — the top
     * bar mark. Null keeps the artwork's own colours in the frosted disc (the About page).
     */
    tint: Color? = null,
) {
    if (tint != null) {
        Box(
            modifier = modifier.size(diameter).clearGlass(CircleShape, backdrop),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(markRes),
                contentDescription = contentDescription,
                // Unspecified keeps the mark's own colours: the logo, on clear glass.
                colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
                modifier = Modifier.size(if (tint == Color.Unspecified) diameter * 0.74f else diameter * 0.56f),
            )
        }
        return
    }
    val dark = isSystemInDarkTheme()
    // A breath of film so the mark keeps its edge over a flat page, where glass has nothing to show.
    val film = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.24f)
    val glint = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.16f else 0.40f),
        0.45f to Color.Transparent,
        1f to Color.Transparent,
    )
    Box(
        modifier = modifier
            .size(diameter)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    // Saturate, soften, then bend — the order that reads as glass, not plastic.
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(diameter.toPx() * 0.22f, diameter.toPx() * 0.46f, true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 10f.dp, color = Color.Black.copy(alpha = if (dark) 0.38f else 0.14f)) },
                onDrawSurface = {
                    drawRect(film)
                    drawRect(glint)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(markRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(diameter * 0.74f),
        )
    }
}
