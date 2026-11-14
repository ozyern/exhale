/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.ozyern.exhale.ui.ios.compat.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.ozyern.exhale.ui.theme.rememberArtworkTint

@Composable
fun AlbumGradient(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = AppTheme.colorScheme.surface
    // Reuse the shared artwork tint cache to avoid decoding the same image twice.
    val extractedColors = rememberArtworkTint(thumbnailUrl)

    val color1 by animateColorAsState(
        targetValue = extractedColors.getOrNull(0)?.copy(alpha = 0.5f) ?: surfaceColor,
        animationSpec = tween(durationMillis = 800),
        label = "AlbumGradientColor1"
    )
    val color2 by animateColorAsState(
        targetValue = extractedColors.getOrNull(1)?.copy(alpha = 0.3f) ?: surfaceColor,
        animationSpec = tween(durationMillis = 800),
        label = "AlbumGradientColor2"
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to color1,
                    0.5f to color2,
                    1.0f to surfaceColor
                )
            )
        )
    )
}
