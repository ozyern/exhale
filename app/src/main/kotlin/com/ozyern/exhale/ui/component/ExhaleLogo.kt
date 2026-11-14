/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.AppTheme

/**
 * The Exhale monogram, black on a light theme and white on a dark one. Neutral on
 * purpose: it never takes the artwork-derived accent, so the brand mark reads the
 * same on every screen.
 */
@Composable
fun ExhaleLogo(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    Image(
        painter = painterResource(R.drawable.exhale_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (isDark) Color.White else Color.Black),
        modifier = modifier.size(size),
    )
}
