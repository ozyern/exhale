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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** After the blur's [ContainerMillis], how long the card's spring may still be landing. */
private const val CardSettleMillis = 400L

/**
 * Where each open page's tile was when it was tapped, so the way back can shrink the page into it
 * without waiting for that tile to be laid out again. On the way back the page under the card is
 * rebuilt, and its list restores its scroll a frame or two late: a shared element aimed at the tile
 * then aimed at nothing, and the card flew to the top-left corner or simply vanished.
 */
object ContainerReturn {
    data class Target(val tile: Rect, val window: androidx.compose.ui.unit.IntSize)

    /** Each open page's tile, by its back-stack entry id. */
    private val targets = object : LinkedHashMap<String, Target>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Target>?) = size > 32
    }

    /**
     * The entry closing now. Set by navigation's pop transition ([PageMotion.popExit]), which is
     * chosen when the pop starts: for the back gesture that is before the page is popped and before
     * the destination listener runs, so anything keyed on the listener missed it and the page
     * vanished in a plain fade.
     */
    var closingEntry by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** When the latest card began to grow: the backdrop blurs for its length. */
    var openedAt by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    /**
     * True only while a card is growing out of its tile. The shared bounds between tile and page
     * exist only then, so no way back (button or gesture) can start a second, competing card.
     */
    var opening by androidx.compose.runtime.mutableStateOf(false)

    fun opened(entryId: String, target: Target?) {
        if (target == null) return
        targets[entryId] = target
        openedAt = android.os.SystemClock.uptimeMillis()
        opening = true
    }

    fun targetFor(entryId: String?): Target? = entryId?.let { targets[it] }
}

/**
 * The page shrinking into its tile on the way back, as ColorOS returns a page to its widget.
 *
 * Drawn by the page itself, in the shared-transition overlay. As an exit transition it ran, but
 * underneath the page coming back, which navigation draws on top, so it was never seen: the page
 * just vanished. Its exit only holds it on screen for this long (see PageMotion).
 */
@Composable
fun Modifier.containerReturnPage(entryId: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val transition = animatedScope.transition
    val target = ContainerReturn.targetFor(entryId) ?: return this
    val popping = ContainerReturn.closingEntry == entryId
    // Kept for the rest of a running transition, so a back gesture let go of early springs the
    // page back to full size instead of jumping there.
    val held = androidx.compose.runtime.remember { booleanArrayOf(false) }
    val closingNow = popping && transition.targetState == androidx.compose.animation.EnterExitState.PostExit
    if (closingNow) held[0] = true
    else if (!transition.isRunning || !popping) held[0] = false
    if (!held[0]) return this
    val progress = transition.animateFloat(
        transitionSpec = { tween(ContainerMillis, easing = androidx.compose.animation.core.FastOutSlowInEasing) },
        label = "containerReturn",
    ) { if (it == androidx.compose.animation.EnterExitState.PostExit) 1f else 0f }
    val screenCorner = com.ozyern.exhale.ui.player.rememberScreenCornerRadius().coerceAtLeast(24.dp)
    return with(sharedScope) {
        this@containerReturnPage
            .renderInSharedTransitionScopeOverlay(renderInOverlay = { true }, zIndexInOverlay = 1f)
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@graphicsLayer
                // The page's width comes down to the tile's, top-left onto the tile's, its height
                // clipped to the tile's own so it lands as the tile, not as a tall strip.
                val endScale = (target.tile.width / w).coerceIn(0.05f, 1f)
                val scale = 1f + (endScale - 1f) * p
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                translationX = target.tile.left * p
                translationY = target.tile.top * p
                val clipH = h + (target.tile.height / endScale - h) * p
                shape = GrowingCornerShape(screenCorner, clipHeightFraction = (clipH / h).coerceIn(0f, 1f))
                clip = true
                alpha = 1f - ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
            }
    }
}

private data class ContainerKey(val id: String)

/**
 * Corners in proportion to the card: a tile's at a tile's size, opening out as it grows until they
 * meet the screen's own at full size, so the card never looks like a box on its way.
 */
private class GrowingCornerShape(private val maxRadius: Dp, private val clipHeightFraction: Float = 1f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val height = size.height * clipHeightFraction
        val radius = minOf(with(density) { maxRadius.toPx() }, minOf(size.width, height) * 0.12f)
        return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, height), CornerRadius(radius)))
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
    // Only while a card grows. The way back is the page's own exit (containerReturnPage); a shared
    // bounds left in place there ran a second card on a back gesture, which the listener sees late.
    if (!ContainerReturn.opening || lastNavWasPop()) return this
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
    if (LocalSharedTransitionScope.current == null) return this
    // For the length of a card's growth, timed from the tap rather than read off "a shared
    // transition is running": anything else that ran one (a page's own header) flashed the landed
    // page blurred. Only opening: closing, the page shrinks in the layer this blurs, so it would
    // blur the page away; Home brightening back under it is the close's backdrop.
    var moving by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val openedAt = ContainerReturn.openedAt
    androidx.compose.runtime.LaunchedEffect(openedAt) {
        if (openedAt == 0L) return@LaunchedEffect
        moving = true
        kotlinx.coroutines.delay(ContainerMillis.toLong())
        moving = false
        // The card's spring has settled by now: tile and page let go of each other.
        kotlinx.coroutines.delay(CardSettleMillis)
        ContainerReturn.opening = false
    }
    val amount = animateFloatAsState(
        targetValue = if (moving) 1f else 0f,
        // In quickly; off at once when the card lands, or the landed page was drawn blurred.
        animationSpec = if (moving) tween(140) else androidx.compose.animation.core.snap(),
        label = "containerBackdrop",
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val blur = with(LocalDensity.current) { BackdropBlur.toPx() }
    return graphicsLayer {
        val radius = blur * amount.value
        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
    }
}
