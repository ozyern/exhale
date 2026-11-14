package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.utils.sharedArtworkTarget
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import kotlin.math.pow
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import com.valentinilk.shimmer.shimmer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DynamicThemeKey
import com.ozyern.exhale.constants.PureBlackHeroBackgroundKey
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.constants.SelectedThemeColorKey
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.DefaultThemeColor
import com.ozyern.exhale.ui.theme.LocalAccentColor
import com.ozyern.exhale.ui.theme.HeroPalette
import com.ozyern.exhale.ui.theme.extractHeroPalette
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ozyern.exhale.ui.theme.colorengine.textHierarchyOn
import com.ozyern.exhale.ui.component.backdrop.internal.recordLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.node.requireGraphicsContext
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.ui.utils.resize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Selected hero image, or the default when no artwork is available. */
sealed class HeroSource {
    data class Artwork(val url: String, val isAnimated: Boolean = false) : HeroSource()
    data object Default : HeroSource()
}

/** Blur radius for [HeroBackground]'s Apple-Music-style blurred artwork. */
internal val HeroBlurRadius = 48.dp

/** Blends the bottom of the artwork into the page using Haze or a color gradient fallback. */
@Composable
fun HeroMergeBand(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    /** Band height; generous, because half its run is spent arriving and half leaving. */
    bandHeight: Dp = 320.dp,
    /** Wide enough that nothing of the picture survives at full strength. */
    blurRadius: Dp = 100.dp,
    /** Aspect ratio of the hero artwork this band must line up with. */
    heroRatio: Float = 1f,
    /** Page color used by the fallback on older Android versions. */
    pageColor: Color = Color.Black,
    /** Optional Haze source; its content must be marked with `Modifier.hazeSource`. */
    hazeState: HazeState? = null,
) {
    // Use Haze only on supported Android versions and when dynamic blur is enabled.
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    val useHaze = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduceBlur

    if (!useHaze) {
        // Keep the same band size as the Haze path, with a page-color fade at the join.
        Box(
            modifier
                .fillMaxWidth()
                .height(bandHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = Array(12) { i ->
                            val t = i / 11f
                            val window = when {
                                t < 0.85f -> t / 0.85f
                                else -> 1f - (t - 0.85f) / 0.15f
                            }
                            val eased = androidx.compose.animation.core.EaseOutCubic.transform(
                                window.coerceIn(0f, 1f),
                            )
                            t to pageColor.copy(alpha = eased)
                        },
                    ),
                ),
        )
        return
    }

    // Blur content from both sides of the join and mask the band's edges to avoid a hard boundary.
    @OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalHazeApi::class)
    Box(
        modifier
            .fillMaxWidth()
            .height(bandHeight)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                // Match the material tint to the page color behind the join.
                noiseFactor = 0f
                mask = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.50f to Color.Black,
                    1.00f to Color.Transparent,
                )
            },
    )
}

/** Where [HeroMergeBand] is centred: half over the artwork, half over the page. */
val HeroMergeBandHeight: Dp = 320.dp

/** Fades a progressive blur over the hero as it scrolls under the status bar. */
@Composable
fun HeroTopBlur(
    hazeState: HazeState?,
    progress: State<Float>,
    pageColor: Color = AppTheme.colorScheme.background,
    modifier: Modifier = Modifier,
    /** Height of the blur strip + the run below it the fade needs to disappear. */
    height: Dp = 140.dp,
) {
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    // Gate Haze with a coarse state so idle frames skip its shader work.
    val useHaze =
        hazeState != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !reduceBlur
    val hazeActive by remember(progress) {
        androidx.compose.runtime.derivedStateOf { progress.value > 0.001f }
    }

    if (!useHaze) {
        // Use a page-color scrim when Haze is unavailable or disabled.
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(pageColor),
        )
        return
    }

    if (!hazeActive) {
        // Same footprint (no layout jump) but no haze node, so a motionless hero
        // does not pay progressive-blur cost for an invisible strip.
        Box(modifier.fillMaxWidth().height(height))
        return
    }

    @OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalHazeApi::class)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                blurRadius = 36.dp
                noiseFactor = 0f
                // Downscale the input to reduce work during scrolling; the blur hides the upscale.
                inputScale = HazeInputScale.Fixed(0.33f)
                // Draw-phase read: intensity follows scroll without recomposing.
                alpha = progress.value.coerceIn(0f, 1f)
                // Fade the blur to clear at the strip's lower edge.
                progressive = HazeProgressive.verticalGradient(
                    easing = androidx.compose.animation.core.EaseInCubic,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    )
}

/** Crossfades three pre-blurred artwork layers as the hero scrolls; blur is not recomputed per frame. */
@Composable
fun HeroScrollBlur(
    artworkUrl: String?,
    progress: State<Float>,
    modifier: Modifier = Modifier,
    blurRadius: Dp = HeroBlurRadius,
    decodeSize: Int = 160,
) {
    val reduceBlur by rememberPreference(ReduceDynamicBlurKey, false)
    // Compose each layer only while visible; update its alpha in the draw phase.
    val softOn by remember(progress) {
        androidx.compose.runtime.derivedStateOf { heroBandOn(progress.value, 0.02f, 0.70f) }
    }
    val midOn by remember(progress) {
        androidx.compose.runtime.derivedStateOf { heroBandOn(progress.value, 0.25f, 0.90f) }
    }
    val fullOn by remember(progress) {
        androidx.compose.runtime.derivedStateOf { progress.value > 0.55f }
    }
    if (reduceBlur || artworkUrl.isNullOrBlank()) return
    if (!softOn && !midOn && !fullOn) return

    Box(modifier.fillMaxSize()) {
        if (softOn) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = (blurRadius * 0.3f).value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = heroPlateAlpha(progress.value, start = 0.02f, peak = 0.30f, end = 0.70f) },
            )
        }
        if (midOn) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = (blurRadius * 0.6f).value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = heroPlateAlpha(progress.value, start = 0.25f, peak = 0.60f, end = 0.90f) },
            )
        }
        if (fullOn) {
            BlurredBackdropImage(
                model = artworkUrl,
                screenBlurRadiusDp = blurRadius.value,
                decodeSize = decodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val eased = androidx.compose.animation.core.EaseOutCubic.transform(progress.value.coerceIn(0f, 1f))
                        alpha = ((eased - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** True when the triangular band for (start, end) can be non-zero at [p]. */
internal fun heroBandOn(p: Float, start: Float, end: Float): Boolean {
    val t = p.coerceIn(0f, 1f)
    return t > start && t < end
}

/** Draw-phase plate alpha: eased triangular band (same curve as before). */
internal fun heroPlateAlpha(p: Float, start: Float, peak: Float, end: Float): Float {
    val eased = androidx.compose.animation.core.EaseOutCubic.transform(p.coerceIn(0f, 1f))
    return heroBlurBand(eased, start = start, peak = peak, end = end)
}

/** Triangular 0→1→0 window: full at [peak], gone at [start]/[end]. */
internal fun heroBlurBand(p: Float, start: Float, peak: Float, end: Float): Float =
    when {
        p <= start || p >= end -> 0f
        p < peak -> (p - start) / (peak - start)
        else -> 1f - (p - peak) / (end - peak)
    }

/** Bounded cache of palettes keyed by artwork URL. */
internal val heroPaletteCache = android.util.LruCache<String, HeroPalette>(128)

/** Scroll progress for a hero list; uses the same pixel threshold as its chrome scrim. */
@Composable
fun rememberHeroTopBlur(
    state: androidx.compose.foundation.lazy.LazyListState,
    thresholdPx: Float = 300f,
): State<Float> = remember(state) {
    androidx.compose.runtime.derivedStateOf {
        val scrolledPx = if (state.firstVisibleItemIndex == 0) {
            state.firstVisibleItemScrollOffset.toFloat()
        } else {
            thresholdPx
        }
        (scrolledPx / thresholdPx).coerceIn(0f, 1f)
    }
}

/** [rememberHeroTopBlur], for a header that leads a grid instead of a list. */
@Composable
fun rememberHeroTopBlur(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    thresholdPx: Float = 300f,
): State<Float> = remember(state) {
    androidx.compose.runtime.derivedStateOf {
        val scrolledPx = if (state.firstVisibleItemIndex == 0) {
            state.firstVisibleItemScrollOffset.toFloat()
        } else {
            thresholdPx
        }
        (scrolledPx / thresholdPx).coerceIn(0f, 1f)
    }
}

/** Constant 0 for optional scroll-progress params that default to "no ramp". */
val ZeroScrollProgress: State<Float> = object : State<Float> {
    override val value: Float get() = 0f
}

/**
 * Resolves hero artwork from static art, animated art, then song thumbnails.
 *
 * @param staticArt Primary artwork URL (e.g. album/playlist/artist art)
 * @param animatedArt Optional animated/canvas artwork URL
 * @param songs List of songs to fall back to for artwork
 */
@Composable
fun rememberHeroSource(
    staticArt: String?,
    animatedArt: String? = null,
    songs: List<Pair<String?, Boolean>> = emptyList(), // (thumbnailUrl, isAnimated)
): HeroSource {
    return remember(staticArt, animatedArt, songs) {
        when {
            !staticArt.isNullOrBlank() -> HeroSource.Artwork(staticArt, isAnimated = false)
            !animatedArt.isNullOrBlank() -> HeroSource.Artwork(animatedArt, isAnimated = true)
            else -> {
                val firstSongArt = songs.firstOrNull { !it.first.isNullOrBlank() }
                firstSongArt?.let { (thumbnailUrl, isAnimated) ->
                    thumbnailUrl?.let { HeroSource.Artwork(it, isAnimated) }
                } ?: HeroSource.Default
            }
        }
    }
}
