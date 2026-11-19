@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Opening a tile, the way ColorOS opens a widget into its app: the tile becomes a card that grows
 * from where it sits to the whole screen. The page is laid out at its own full size the whole way
 * and only scaled to the card, top-aligned, so it reads as the tile turning into the page rather
 * than a page being drawn into a box; the tile's own picture fades out over it as it grows. Back,
 * the page shrinks into the tile the same way and the tile fades back in as it lands.
 */

/** How long the pages under a growing card take to dim, and to come back. */
const val ContainerMillis = 360

/** The card's travel: brisk off the tile, a breath of overshoot as it lands. */
private val ContainerBounds = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = Rect.VisibilityThreshold,
)

private data class ContainerKey(val id: String)

/**
 * Corners in proportion to the card: a tile's at a tile's size, opening out as it grows until they
 * meet the screen's own at full size, so the card never looks like a box on its way.
 */
private class GrowingCornerShape(private val maxRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = minOf(with(density) { maxRadius.toPx() }, size.minDimension * 0.12f)
        return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius)))
    }
}

/** One end of the card: the tile ([ContentScale.Crop], centred) or the page (full width, from the top). */
@Composable
internal fun Modifier.containerTransformElement(id: String, contentScale: ContentScale, alignment: Alignment): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val screenCorner = com.ozyern.exhale.ui.player.rememberScreenCornerRadius().coerceAtLeast(24.dp)
    return with(sharedScope) {
        this@containerTransformElement.sharedBounds(
            sharedContentState = rememberSharedContentState(ContainerKey(id)),
            animatedVisibilityScope = animatedScope,
            // The page arrives over the tile in the first stretch of the growth, and leaves the
            // same way on the trip back.
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            boundsTransform = { _, _ -> ContainerBounds },
            resizeMode = ScaleToBounds(contentScale, alignment),
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 1f,
            clipInOverlayDuringTransition = OverlayClip(GrowingCornerShape(screenCorner)),
        )
    }
}

/** The page end of the card: the whole destination, when a tile was tapped to open it. */
@Composable
fun Modifier.sharedPageTarget(): Modifier {
    val id = LocalMorphArtworkId.current ?: return this
    if (!hasMorphSource(id)) return this
    return containerTransformElement(id, ContentScale.FillWidth, Alignment.TopCenter)
}
