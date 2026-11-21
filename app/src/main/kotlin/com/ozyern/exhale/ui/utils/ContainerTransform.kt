@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
const val ContainerMillis = 300

/**
 * The card's travel, as ColorOS moves a widget: about a third of a second each way, landing
 * without overshoot. The softer spring before it took well over half a second to settle, and the
 * trip home read as the page drifting back.
 */
private val ContainerBounds = spring(
    dampingRatio = 0.92f,
    stiffness = 650f,
    visibilityThreshold = Rect.VisibilityThreshold,
)

/**
 * The card is solid the whole way. The page fills it as soon as it leaves the tile and keeps it
 * until the last stretch home, where it hands over to the tile's own picture just before landing,
 * as ColorOS hands a page back to its widget. Both used to fade together over the whole trip, so
 * the shrinking card was see-through and the page under it showed through the page in it.
 */
private val PageIn = fadeIn(tween(110))
private val PageOut = fadeOut(tween(120, delayMillis = 170))
private val TileIn = fadeIn(tween(120, delayMillis = 160))
private val TileOut = fadeOut(tween(100))

/** How far everything behind the card is blurred while it moves. */
private val BackdropBlur = 28.dp

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
internal fun Modifier.containerTransformElement(
    id: String,
    contentScale: ContentScale,
    alignment: Alignment,
    isPage: Boolean = false,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val screenCorner = com.ozyern.exhale.ui.player.rememberScreenCornerRadius().coerceAtLeast(24.dp)
    return with(sharedScope) {
        this@containerTransformElement.sharedBounds(
            sharedContentState = rememberSharedContentState(ContainerKey(id)),
            animatedVisibilityScope = animatedScope,
            enter = if (isPage) PageIn else TileIn,
            exit = if (isPage) PageOut else TileOut,
            boundsTransform = { _, _ -> ContainerBounds },
            resizeMode = scaleToBounds(contentScale, alignment),
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
    return containerTransformElement(id, ContentScale.FillWidth, Alignment.TopCenter, isPage = true)
}

/**
 * Blurs what is behind a moving card, as ColorOS blurs the home screen behind a widget opening.
 * Put it on the content of the shared-transition layout: the card is drawn in that layout's
 * overlay, above this, so only the pages behind it blur. The blur needs Android 12; before that
 * the page only dims (see PageMotion).
 */
@Composable
fun Modifier.containerBackdrop(): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val moving = sharedScope.isTransitionActive && isArtworkMorph()
    val amount = animateFloatAsState(
        targetValue = if (moving) 1f else 0f,
        // In quickly, so the trip home starts on a blurred page; out as the card lands.
        animationSpec = tween(if (moving) 140 else 220),
        label = "containerBackdrop",
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val blur = with(LocalDensity.current) { BackdropBlur.toPx() }
    return graphicsLayer {
        val radius = blur * amount.value
        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
    }
}
