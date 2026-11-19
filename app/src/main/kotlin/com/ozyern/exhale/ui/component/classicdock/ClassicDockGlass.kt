/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component.classicdock

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.ozyern.exhale.ui.component.LocalAppBackdrop
import com.ozyern.exhale.ui.component.backdrop.Backdrop
import com.ozyern.exhale.ui.component.backdrop.drawBackdrop
import com.ozyern.exhale.ui.component.backdrop.highlight.Highlight
import com.ozyern.exhale.ui.component.backdrop.shadow.Shadow

/**
 * The classic dock's glass, from master's Glass.kt: almost no frost, so the page reads straight
 * through, with the colour behind it lifted, a deep bend at the rim and a light film over it. The
 * bar sits over everything, so it has to look like a lens over the page, not a panel on it.
 */
@Composable
internal fun Modifier.dockGlass(
    shape: Shape,
    backdrop: Backdrop = LocalAppBackdrop.current,
    dark: Boolean = isSystemInDarkTheme(),
): Modifier {
    val film = if (dark) ClearGlassFilmDark.copy(alpha = ClearGlassFilmDarkAlpha) else Color(0xFFFAFAFA).copy(alpha = ClearGlassFilm)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { dockGlassEffects() },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(film) },
    )
}

/** Icon colour on the dock's glass: pure white or black, the only tint that holds over any page. */
@Composable
internal fun clearGlassContentColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black

/** The light film's strength over the glass. */
internal const val ClearGlassFilm = 0.5f

/** Dark theme's film: an adaptive glass grey at a third, not a near-black slab. */
internal val ClearGlassFilmDark = Color(0xFF4A4A4E)
internal const val ClearGlassFilmDarkAlpha = 0.32f
