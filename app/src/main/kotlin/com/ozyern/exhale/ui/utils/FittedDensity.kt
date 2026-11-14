package com.ozyern.exhale.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/** The density before [ProvideFittedDensity] shrank it, for content that must keep its size (lyrics). */
val LocalUnscaledDensity = compositionLocalOf<Density?> { null }

/**
 * Shrinks everything inside to fit [availableHeight] when its design height needs more than that,
 * so a control stack designed for a tall screen stays complete on a short, wide one at any dpi.
 */
@Composable
fun ProvideFittedDensity(
    availableHeight: Dp,
    designHeight: Dp,
    minScale: Float = 0.7f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scale = (availableHeight / designHeight).coerceIn(minScale, 1f)
    if (scale >= 0.999f) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalUnscaledDensity provides density,
        LocalDensity provides Density(density.density * scale, density.fontScale),
    ) { content() }
}

/** Restores the unscaled density inside [ProvideFittedDensity] content. */
@Composable
fun ProvideUnscaledDensity(content: @Composable () -> Unit) {
    val unscaled = LocalUnscaledDensity.current
    if (unscaled == null) content() else CompositionLocalProvider(LocalDensity provides unscaled) { content() }
}
