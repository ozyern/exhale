/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.constants.AppBackgroundColorKey
import com.ozyern.exhale.constants.HomeBackgroundAnimateKey
import com.ozyern.exhale.constants.HomeBackgroundBlurKey
import com.ozyern.exhale.constants.HomeBackgroundDimKey
import com.ozyern.exhale.constants.HomeBackgroundEnabledKey
import com.ozyern.exhale.constants.HomeBackgroundIsVideoKey
import com.ozyern.exhale.constants.HomeBackgroundPathKey
import com.ozyern.exhale.constants.HomeBackgroundQualityKey
import com.ozyern.exhale.utils.rememberPreference
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import java.io.File

/** Maximum edge size for stored and decoded background images. */
private const val BackgroundAbsoluteMaxEdge = 2560

/** Source edge size for a full-quality cached blur. */
private const val BackgroundBakeMaxEdge = 768

/** Blur radius at which the small blurred bake is indistinguishable from a full-size one. */
private const val FullBlurDp = 24f

/** Calculates background image dimensions from screen size and [quality], capped at [BackgroundAbsoluteMaxEdge]. */
@Composable
fun rememberHomeBackgroundTargetSize(quality: Float): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration, density, quality) {
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val q = quality.coerceIn(0.3f, 1f)
        val width = (screenWidthPx * q).toInt().coerceIn(1, BackgroundAbsoluteMaxEdge)
        val height = (screenHeightPx * q).toInt().coerceIn(1, BackgroundAbsoluteMaxEdge)
        width to height
    }
}

/** Process-wide: the intro blur ramp plays only the first time this session. */
private var blurAnimatedThisSession = false

/** True when a custom image or background color is set. */
@Composable
fun hasCustomHomeBackground(): Boolean {
    val (enabled) = rememberPreference(HomeBackgroundEnabledKey, false)
    val (path) = rememberPreference(HomeBackgroundPathKey, "")
    val (colorInt) = rememberPreference(AppBackgroundColorKey, 0)
    return (enabled && path.isNotEmpty()) || colorInt != 0
}

/** Resolves the explicit background color, theme tint, or screen [fallback], in that order. */
@Composable
fun rememberAppBackgroundColor(fallback: Color): Color {
    val (colorInt) = rememberPreference(AppBackgroundColorKey, 0)
    return if (colorInt != 0) Color(colorInt) else rememberAppBackgroundTint(fallback)
}

/**
 * Draws the custom image or video backdrop behind Home and Library content.
 * Image blur is baked and cached to avoid full-screen blur work every frame; the bake is low-res only for strong blur.
 * Place inside a [BoxScope] so the backdrop can fill its parent.
 *
 * @param withGradient adds a primary-color wash at the bottom.
 * @param contentLoaded starts the image reveal after screen content appears.
 */
@Composable
fun BoxScope.HomeImageBackground(
    withGradient: Boolean = false,
    contentLoaded: Boolean = true,
) {
    val (enabled) = rememberPreference(HomeBackgroundEnabledKey, false)
    val (path) = rememberPreference(HomeBackgroundPathKey, "")
    val (blur) = rememberPreference(HomeBackgroundBlurKey, 48f)
    val (dim) = rememberPreference(HomeBackgroundDimKey, 0.4f)
    val (animate) = rememberPreference(HomeBackgroundAnimateKey, false)
    val (isVideo) = rememberPreference(HomeBackgroundIsVideoKey, false)
    if (!enabled || path.isEmpty()) {
        // Keep the user's solid background color visible when no image is set.
        val (backgroundColorInt) = rememberPreference(AppBackgroundColorKey, 0)
        if (backgroundColorInt != 0) {
            Box(modifier = Modifier.matchParentSize().background(Color(backgroundColorInt)))
        }
        return
    }

    if (isVideo) {
        // The video already moves, so do not animate its blur as well.
        HomeVideoBackground(path = path, blur = blur, dim = dim, withGradient = withGradient)
        return
    }

    // Play the intro reveal once per app session.
    val shouldAnimate = animate && !blurAnimatedThisSession
    var appeared by remember { mutableStateOf(!shouldAnimate) }
    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            appeared = true
            blurAnimatedThisSession = true
        }
    }
    // The blur radius is fixed in the cached bitmap, so the intro animates its opacity.
    val introAlpha by animateFloatAsState(
        targetValue = if (appeared && contentLoaded) 1f else 0f,
        // Long, gentle ease-out (easeOutExpo-like) for a fluid, unhurried settle.
        animationSpec = tween(
            durationMillis = 2200,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
        ),
        label = "homeBgIntro",
    )

    // BlurredBackdropImage bakes and caches the blur off the UI thread.
    val (quality) = rememberPreference(HomeBackgroundQualityKey, 1f)
    val (targetWidth, targetHeight) = rememberHomeBackgroundTargetSize(quality)
    // A weak blur hides nothing, so the bake grows from the small blurred size toward the full screen size as blur drops.
    val bakeSize = remember(quality, blur, targetWidth, targetHeight) {
        val blurredEdge = BackgroundBakeMaxEdge * quality.coerceIn(0.3f, 1f)
        val sharpEdge = maxOf(targetWidth, targetHeight).toFloat()
        val softness = (blur / FullBlurDp).coerceIn(0f, 1f)
        (sharpEdge + (blurredEdge - sharpEdge) * softness).toInt().coerceAtLeast(1)
    }
    val backdropFile = remember(path) { File(path) }

    BlurredBackdropImage(
        model = backdropFile,
        screenBlurRadiusDp = blur,
        decodeSize = bakeSize,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .then(if (animate) Modifier.graphicsLayer { alpha = introAlpha } else Modifier),
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = dim)),
    )
    if (withGradient) {
        val primary = AppTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to primary.copy(alpha = 0.55f),
                    ),
                ),
        )
    }
}
