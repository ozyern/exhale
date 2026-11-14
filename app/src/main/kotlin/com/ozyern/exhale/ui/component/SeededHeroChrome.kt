/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared instant-seed pattern for every hero-to-tint detail screen (Album,
 * Artist, Online/Local/Auto/Cache/Top playlists): the tapped tile's thumbnail
 * paints hero + mesh bg on frame one, full-res crossfades over it, and the
 * loading state is a shimmer on the seeded bg instead of a spinner on black.
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.utils.sharedArtworkTarget
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ozyern.exhale.constants.GridThumbnailHeight
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.theme.GlassTintTokens
import com.ozyern.exhale.ui.utils.LocalNavAnimatedVisibilityScope
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First non-empty of route-arg thumb, live-tile thumb, DB url. Null only when all miss. */
fun detailSeed(routeThumb: String?, tileThumb: String?, dbUrl: String?): String? =
    routeThumb?.takeIf { it.isNotEmpty() }
        ?: tileThumb?.takeIf { it.isNotEmpty() }
        ?: dbUrl?.takeIf { it.isNotEmpty() }

/**
 * Thumb-then-full hero: bottom layer repeats the grid tile's own request
 * (same URL/size/key = memory-cache hit, opaque frame one so the container
 * morph lands grown, not swapped); top layer fades full-res over it.
 */
@Composable
fun ThumbThenFullHero(
    thumbUrl: String?,
    fullUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbPx: Int = thumbnailPx(GridThumbnailHeight),
    fullPx: Int = 1024,
    fullCrossfadeMillis: Int = Motion.MorphEnterMillis,
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        val thumbRequest = remember(thumbUrl, thumbPx) {
            val resolvedUrl = thumbUrl?.resize(thumbPx, thumbPx)
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .diskCacheKey(resolvedUrl)
                .size(thumbPx, thumbPx)
                .crossfade(false)
                .build()
        }
        val reused = com.ozyern.exhale.ui.utils.SharedArtworkSeedImage(Modifier.fillMaxSize(), contentScale)
        if (!reused) AsyncImage(
            model = thumbRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        val fullRequest = remember(fullUrl, fullPx) {
            val resolvedUrl = fullUrl?.resize(fullPx, fullPx)
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .diskCacheKey(resolvedUrl)
                .size(fullPx, fullPx)
                .crossfade(fullCrossfadeMillis)
                .build()
        }
        if (!reused || !com.ozyern.exhale.ui.utils.artworkTransitionRunning()) AsyncImage(
            model = fullRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}

/** Seeded hero for the loading state: real thumb art, rows shimmer below it. */
@Composable
fun SeededShimmerHero(
    thumbUrl: String?,
    modifier: Modifier = Modifier,
    ratio: Float = 1f,
) {
    val context = LocalContext.current
    Box(modifier = modifier.sharedArtworkTarget().fillMaxWidth().aspectRatio(ratio)) {
        val thumbPx = thumbnailPx(GridThumbnailHeight)
        val request = remember(thumbUrl, thumbPx) {
            val resolvedUrl = thumbUrl?.resize(thumbPx, thumbPx)
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .diskCacheKey(resolvedUrl)
                .size(thumbPx, thumbPx)
                .crossfade(false)
                .build()
        }
        if (!com.ozyern.exhale.ui.utils.SharedArtworkSeedImage(Modifier.fillMaxSize(), ContentScale.Crop)) AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Liquid-glass background for the top-bar icon pills (share/menu, search/
 * share/menu) — same fixed-style glass as [GlassCircleButton], ignoring the
 * NAV_BAR toggle like the back button does. Flat translucent fallback only
 * where glass can't render at all.
 */
@Composable
fun pillGlassBackground(shape: androidx.compose.foundation.shape.CornerBasedShape): Modifier {
    // Fixed style like GlassCircleButton: pill glass stays even when the
    // NAV_BAR bar toggle is off. Only old/unsupported devices fall back.
    val glassConfig = LocalGlassEffectConfig.current.withFixedStyle()
    if (isGlassAllowed()) {
        return Modifier
            .chromeShadow(shape)
            .liquidGlass(config = glassConfig, shape = shape)
    }
    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    return Modifier
        .chromeShadow(shape)
        .background(
            GlassTintTokens.baseSurfaceColor(isDark).copy(
                alpha = if (isDark) GlassTintTokens.NavPillFillOpacityDark
                else GlassTintTokens.NavPillFillOpacityLight,
            ),
            shape,
        )
}

/**
 * Icon pill with the same elastic press/drag stretch as [GlassCircleButton]
 * (2x magnitude so it reads at pill size) plus the mirrored exit: on pop it
 * shrinks back toward the right edge and fades over 180ms, while the enter
 * grow (right-to-left, via [DetailChromeEntrance]) runs on a post-morph
 * delay. Exit rides the Nav transition, so gesture scrub still tracks it.
 */
@Composable
fun GlassPillRow(
    modifier: Modifier = Modifier,
    magnitude: Float = 2f,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val stretch = rememberElasticStretch()
    val scope = LocalNavAnimatedVisibilityScope.current
    val exitModifier = if (scope != null) {
        with(scope) {
            Modifier.animateEnterExit(
                enter = EnterTransition.None,
                exit = fadeOut(tween(180)) +
                    scaleOut(tween(180), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0.5f)),
            )
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .graphicsLayer(elasticStretchLayerBlock(stretch, magnitude))
            .then(modifier)
            .then(exitModifier)
            .then(stretch.modifier)
            .then(stretch.gestureModifier),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** Same mirrored exit for the round back button (shrinks toward its left edge). */
@Composable
fun backExitModifier(): Modifier {
    val scope = LocalNavAnimatedVisibilityScope.current ?: return Modifier
    return with(scope) {
        Modifier.animateEnterExit(
            enter = EnterTransition.None,
            exit = fadeOut(tween(180)) +
                scaleOut(tween(180), targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 0.5f)),
        )
    }
}

/**
 * Post-morph chrome entrance, shared by every detail top bar: after the
 * container lands, the pill elastic-expands once and icons deblur staggered.
 * Draw-phase only (graphicsLayer), no recomposition per frame. Once per open.
 */
@androidx.compose.runtime.Stable
class DetailChromeEntrance internal constructor(
    val container: Animatable<Float, *>,
    val backIcon: Animatable<Float, *>,
    private val icons: List<Animatable<Float, *>>,
) {
    fun icon(index: Int): Animatable<Float, *> = icons.getOrElse(index) { icons.last() }

    /** Elastic expand for the pill itself. Anchor right edge so it grows leftwards. */
    fun Modifier.containerEnter(): Modifier = graphicsLayer {
        val p = container.value
        scaleX = 0.7f + 0.3f * p
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(1f, 0.5f)
    }

    /** Grow-in for the round back button (starts 60ms before the pill icons). */
    fun Modifier.backEnter(): Modifier = graphicsLayer {
        val a = backIcon.value.coerceIn(0f, 1f)
        alpha = a
        scaleX = 0.7f + 0.3f * a
        scaleY = 0.7f + 0.3f * a
        transformOrigin = TransformOrigin(0f, 0.5f)
    }

    /** Deblur + rise for pill icon [index] (0 = leftmost). */
    fun Modifier.iconEnter(index: Int): Modifier = graphicsLayer {
        val a = icon(index).value.coerceIn(0f, 1f)
        alpha = a
        translationY = (1f - a) * 6.dp.toPx()
        renderEffect = if (a < 1f) {
            androidx.compose.ui.graphics.BlurEffect(
                (1f - a) * 10f, (1f - a) * 10f,
                androidx.compose.ui.graphics.TileMode.Clamp,
            )
        } else {
            null
        }
    }
}

@Composable
fun rememberDetailChromeEntrance(
    iconCount: Int = 3,
    startDelayMillis: Int = Motion.MorphEnterMillis,
): DetailChromeEntrance {
    val container = remember { Animatable(0f) }
    val backIcon = remember { Animatable(0f) }
    val icons = remember(iconCount) { List(iconCount) { Animatable(0f) } }
    LaunchedEffect(startDelayMillis, icons) {
        delay(startDelayMillis.toLong())
        launch {
            container.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch { backIcon.animateTo(1f, tween(200)) }
        icons.forEachIndexed { index, state ->
            launch {
                delay(40L * (index + 1))
                state.animateTo(1f, tween(200))
            }
        }
    }
    return remember(container, backIcon, icons) {
        DetailChromeEntrance(container, backIcon, icons)
    }
}

/** Small icon inside a stretched pill row — keeps call sites terse. */
@Composable
fun RowScope.StretchPillIcon(
    entrance: DetailChromeEntrance,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = with(entrance) { Modifier.iconEnter(index).size(40.dp).then(modifier) },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
