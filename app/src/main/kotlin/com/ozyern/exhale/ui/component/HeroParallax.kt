/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parallax for the big centred artwork at the top of a detail page — the artist portrait, the
 * album cover.
 *
 * Those headers used to scroll off at exactly the speed of the list under them, which is the one
 * thing that makes a hero image read as a tall list row rather than as the subject of the page.
 * Here it lags the scroll, shrinks and fades, so the list appears to slide *over* it and the page
 * gains a foreground and a background instead of being one flat plane.
 *
 * **Everything is read inside the [graphicsLayer] lambda**, which is what makes this affordable.
 * `firstVisibleItemScrollOffset` changes on every scroll frame; reading it in composition would
 * recompose the whole header — the portrait, the bio, the counts, the action row — sixty times a
 * second while the user drags. Read in the layer's block instead, a frame costs one draw pass and
 * neither recomposition nor re-layout.
 *
 * @param travel how far the header scrolls before the effect is fully applied. Past this the
 *   artwork is off screen anyway, so the ramps are clamped rather than left to run away.
 * @param drift fraction of the scroll the artwork is *held back* by. 0 scrolls with the list, 1
 *   pins it in place; a third of the way is enough to read as depth without the header visibly
 *   refusing to leave.
 */
fun Modifier.heroParallax(
    listState: LazyListState,
    travel: Dp = 220.dp,
    drift: Float = 0.34f,
    /** Recede by shrinking as it scrolls. Off for full-bleed photos, which Apple scrolls whole. */
    shrink: Boolean = true,
): Modifier = graphicsLayer {
    // Only the first item can be the header. Once the list has scrolled past it entirely the
    // index jumps and its own offset stops being meaningful, so pin the ramp at its end value.
    val scrolled = if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat()
    } else {
        Float.MAX_VALUE
    }
    val travelPx = travel.toPx().coerceAtLeast(1f)
    val progress = (scrolled / travelPx).coerceIn(0f, 1f)

    translationY = scrolled.coerceAtMost(travelPx) * drift
    // Shrinks from the top edge, so the artwork appears to recede under the app bar rather than
    // to contract toward a point in the middle of the page.
    transformOrigin = TransformOrigin(0.5f, 0f)
    val scale = if (shrink) 1f - 0.14f * progress else 1f
    scaleX = scale
    scaleY = scale
    // Faster than the shrink, and not all the way to zero: the artwork should be gone before the
    // header's last pixel leaves, but a hero that blinks out at the halfway mark reads as a bug.
    alpha = 1f - progress * 0.92f
}
