/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle

/** How far back the presenting screen sits once a sheet is fully open. */
private const val PresentedScale = 0.91f

/** The rounding the screen picks up as it detaches — roughly a device corner radius. */
private val PresentedCornerRadius = 32.dp

/** How much the screen darkens behind the sheet. */
private const val PresentedDim = 0.22f

/**
 * The iOS sheet presentation, applied to the screen a sheet is covering: it scales back,
 * rounds its corners, slides down under the status bar and dims — so the sheet reads as a
 * card in front of another card rather than a panel pasted over a flat screen.
 *
 * Put this on the content, not on the sheet. The black is drawn *before* the layer, in
 * unscaled coordinates, so it fills the gap the shrink opens up around the screen.
 *
 * @param progress 0 = no sheet, 1 = sheet fully open. Use [rememberSheetPresentation].
 * @param topInset how far down the shrunken screen slides; pass the status bar height so
 *   its top edge clears the clock the way iOS does.
 */
fun Modifier.iosSheetPresentation(progress: Float, topInset: Dp = 12.dp): Modifier {
    if (progress <= 0.001f) return this
    val p = progress.coerceIn(0f, 1f)
    return this
        .background(Color.Black)
        .graphicsLayer {
            val scale = lerp(1f, PresentedScale, p)
            scaleX = scale
            scaleY = scale
            // Anchored at the top edge: iOS pushes the screen down and back, it does not
            // pull it in toward the middle.
            transformOrigin = TransformOrigin(0.5f, 0f)
            translationY = topInset.toPx() * p
            shape = ContinuousRoundedRectangle(PresentedCornerRadius * p)
            clip = true
        }
        .drawWithContent {
            drawContent()
            drawRect(Color.Black, alpha = PresentedDim * p)
        }
}

/**
 * Draw-phase overload of [iosSheetPresentation] driven by a live progress provider (e.g.
 * bottom sheet drag). Evaluates during the draw/graphicsLayer phase for zero-recomposition
 * 120Hz smoothness while tracking card gestures continuously.
 */
fun Modifier.iosSheetPresentation(progressProvider: () -> Float, topInset: Dp = 12.dp): Modifier {
    return this.drawWithCache {
        onDrawWithContent {
            val p = progressProvider().coerceIn(0f, 1f)
            if (p > 0.001f) {
                drawRect(Color.Black)
            }
            drawContent()
            if (p > 0.001f) {
                drawRect(Color.Black, alpha = PresentedDim * p)
            }
        }
    }
        .graphicsLayer {
            val p = progressProvider().coerceIn(0f, 1f)
            val scale = lerp(1f, PresentedScale, p)
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
            translationY = topInset.toPx() * p
            shape = ContinuousRoundedRectangle(PresentedCornerRadius * p)
            clip = p > 0.001f
        }
}

/**
 * How many sheets are on screen right now.
 *
 * A count rather than a flag: sheets stack (a menu opens a confirmation over itself), and
 * the screen behind must stay pushed back until the last one has gone, not until the first
 * one closes.
 */
@Stable
class SheetPresentationState {
    var openCount by mutableIntStateOf(0)
        private set

    fun opened() {
        openCount++
    }

    fun closed() {
        openCount = (openCount - 1).coerceAtLeast(0)
    }
}

val LocalSheetPresentation = staticCompositionLocalOf { SheetPresentationState() }

/**
 * Registers the calling sheet as on screen for as long as it stays composed.
 *
 * Called from the one place every sheet in the app already routes through
 * ([com.ozyern.exhale.ui.ios.compat.ModalBottomSheet]), so a new sheet gets the presentation
 * without its author having to know this exists — which is how the six that predate it
 * ended up without it.
 */
@Composable
fun PresentingSheetEffect() {
    val state = LocalSheetPresentation.current
    DisposableEffect(state) {
        state.opened()
        onDispose { state.closed() }
    }
}

/**
 * Animates [iosSheetPresentation]'s progress from whether any sheet is open. Springs
 * rather than tweens, so opening and closing land the way the sheet's own motion does.
 *
 * [sheetsOpen] is for sheets that do not go through the shared presenter and so cannot
 * register themselves — the menu sheet, which cupertino presents.
 */
@Composable
fun rememberSheetPresentation(vararg sheetsOpen: Boolean): Float {
    val registered = LocalSheetPresentation.current.openCount > 0
    val target = if (registered || sheetsOpen.any { it }) 1f else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "IosSheetPresentation",
    )
    return progress
}
