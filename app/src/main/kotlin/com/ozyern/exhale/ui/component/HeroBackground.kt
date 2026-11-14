/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PureBlackHeroBackgroundKey
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.HeroPalette
import com.ozyern.exhale.ui.utils.sharedArtworkTarget
import com.ozyern.exhale.utils.rememberPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Full-width artwork header that fades into its parent tint. */
@Composable
fun AlbumStyleHeroImage(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    // Extra pull-up beyond the status bar, to lift the square higher like AlbumScreen.
    extraPullUp: androidx.compose.ui.unit.Dp = 48.dp,
    // Driven by rememberHeroZoom() for pull-to-zoom. Applied to the drawn image
    // only, so growing it never re-lays-out the list underneath.
    heroScale: Float = 1f,
    // The screen's own Haze source. Without one the merge band below can only fall
    // back to a flat crossfade — a fade rather than a merge, which is what the
    // playlist screens were getting while Album and Artist got the real thing.
    hazeState: HazeState? = null,
    // The colour under the join, for the band's pre-13 / reduced-blur fallback.
    pageColor: Color = Color.Black,
    // 0..1 scroll progress (e.g. from rememberHeroTopBlur): the artwork blurs
    // progressively as it scrolls up, sharpening back on the way down.
    scrollBlurProgress: State<Float> = ZeroScrollProgress,
) {
    // Keep the same fade and blur used by artist headers so the join looks consistent.
    ArtistHeroImage(
        artworkUrl = artworkUrl,
        pageColor = pageColor,
        modifier = modifier,
        heroScale = { heroScale },
        loading = false,
        scrollBlur = { scrollBlurProgress.value },
    )
}

/** Hero artwork background that fades into a flat tint plane. */

@Composable
fun HeroBackground(
    tint: Color,
    heroSource: HeroSource,
    modifier: Modifier = Modifier,
    // Use a full-screen blur instead of a sharp image fading into the tint.
    blurArtwork: Boolean = false,
    // Blur the whole background when blurArtwork is enabled.
    fullBlur: Boolean = false,
    // Hide the placeholder icon on screens that need a plain tint.
    showDefaultIcon: Boolean = true,
    // Add a primary-color wash at the bottom unless meshWash is provided.
    bottomGradient: Boolean = false,
    // Use colors derived from the artwork for the bottom wash.
    meshWash: HeroPalette? = null,
    // Scroll progress for the blur over the sharp artwork.
    topBlurProgress: State<Float> = ZeroScrollProgress,
    // Scale used while pulling past the top of the list.
    heroScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val primaryColor = AppTheme.colorScheme.primary
    val (pureBlack) = rememberPreference(PureBlackHeroBackgroundKey, false)
    // Reuses the existing Default branch (flat tint, no image) to suppress the
    // artwork backdrop entirely rather than adding a second code path.
    val effectiveTint = if (pureBlack) Color.Black else tint
    val effectiveHeroSource = if (pureBlack) HeroSource.Default else heroSource
    // Per-screen Haze source (Album/Artist pattern): this background becomes the
    // backdrop the top blur strip samples, so the artwork dissolves as it scrolls
    // under the status bar instead of sliding under a hard edge.
    val hazeState = remember { HazeState() }
    Box(modifier = modifier.background(effectiveTint).hazeSource(hazeState)) {
        when (val heroSource = effectiveHeroSource) {
            is HeroSource.Artwork -> {
                // Fade in the artwork once it loads.
                var visible by remember { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 600),
                    label = "heroFadeIn",
                )

                if (blurArtwork) {
                    // Avoid composing blur layers when they are outside their scroll range.
                    val midActive by remember(topBlurProgress) {
                        androidx.compose.runtime.derivedStateOf {
                            topBlurProgress.value in 0.05f..0.95f
                        }
                    }
                    val heavyActive by remember(topBlurProgress) {
                        androidx.compose.runtime.derivedStateOf { topBlurProgress.value > 0.60f }
                    }
                    // A low-resolution blur is cached per artwork. At this radius,
                    // the smaller source looks the same and avoids per-frame GPU blur work.
                    BlurredBackdropImage(
                        model = heroSource.url,
                        screenBlurRadiusDp = HeroBlurRadius.value,
                        decodeSize = 160,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = heroScale
                                scaleY = heroScale
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.5f,
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                        onReady = { visible = true },
                    )
                    // Blend between the sharp layer and the heavier blur as the list scrolls.
                    if (midActive) {
                        BlurredBackdropImage(
                            model = heroSource.url,
                            screenBlurRadiusDp = (HeroBlurRadius * 0.45f).value,
                            decodeSize = 160,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val midAlpha = alpha * heroPlateAlpha(
                                        topBlurProgress.value,
                                        start = 0.05f,
                                        peak = 0.50f,
                                        end = 0.95f,
                                    )
                                    this.alpha = midAlpha
                                    scaleX = heroScale
                                    scaleY = heroScale
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height * 0.5f,
                                            endY = size.height,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                    // Increase blur further near the end of the scroll.
                    if (heavyActive) {
                        BlurredBackdropImage(
                            model = heroSource.url,
                            screenBlurRadiusDp = (HeroBlurRadius * 1.5f).value,
                            decodeSize = 160,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = topBlurProgress.value.coerceIn(0f, 1f)
                                    this.alpha = alpha * ((p - 0.60f) / 0.40f).coerceIn(0f, 1f)
                                    scaleX = heroScale
                                    scaleY = heroScale
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height * 0.5f,
                                            endY = size.height,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                    // Keep the top sharp until the blur takes over during scroll.
                    if (!fullBlur) {
                        AsyncImage(
                            model = heroSource.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onSuccess = { visible = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = topBlurProgress.value.coerceIn(0f, 1f)
                                    this.alpha = alpha * (1f - ((p - 0.05f) / 0.45f).coerceIn(0f, 1f))
                                    scaleX = heroScale
                                    scaleY = heroScale
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height * 0.35f,
                                            endY = size.height * 0.6f,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                } else {
                    AsyncImage(
                        model = heroSource.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = alpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.55f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        onSuccess = { visible = true },
                    )
                    // Apply progressive blur over the artwork as the list scrolls.
                    HeroScrollBlur(
                        artworkUrl = heroSource.url,
                        progress = topBlurProgress,
                        modifier = Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height * 0.55f,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                    )
                }
            }
            is HeroSource.Default -> {
            }
        }
        if (meshWash != null) {
            HeroMeshWash(
                palette = meshWash,
                screenBackground = AppTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (bottomGradient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to primaryColor.copy(alpha = 0.55f),
                        ),
                    ),
            )
        }
        content()

        // Blur the artwork under the status bar as the list scrolls.
        HeroTopBlur(
            hazeState = hazeState,
            progress = topBlurProgress,
            pageColor = AppTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Artwork size used by the wide hero card. */
val HeroCardSize: Dp = 200.dp

/** Space reserved above the hero card for the floating Back button. */
val HeroBackButtonClearance: Dp = 56.dp

/** Wide hero card with artwork, title, and actions. */
@Composable
fun HeroCardHeader(
    artworkUrl: String?,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = HeroCardSize,
    // Artist pages are circular in the reference; albums and playlists are square.
    circular: Boolean = false,
    subtitle: (@Composable () -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = if (circular) {
        ContinuousRoundedRectangle(percent = 50)
    } else {
        ContinuousRoundedRectangle(16.dp)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = HeroBackButtonClearance, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(cardSize)
                .shadow(elevation = 12.dp, shape = shape)
                .sharedArtworkTarget()
                .clip(shape)
                .background(AppTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.4f)
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = 0.3f },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title()
            subtitle?.invoke()
            actions?.invoke(this)
        }
    }
}

/** Hero card overload that creates the title and subtitle text styles. */
@Composable
fun HeroCardHeader(
    artworkUrl: String?,
    titleText: String,
    modifier: Modifier = Modifier,
    subtitleText: String? = null,
    cardSize: Dp = HeroCardSize,
    circular: Boolean = false,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    HeroCardHeader(
        artworkUrl = artworkUrl,
        modifier = modifier,
        cardSize = cardSize,
        circular = circular,
        title = {
            Text(
                text = titleText,
                style = AppTheme.typography.displaySmall,
                color = AppTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = subtitleText?.let {
            {
                Text(
                    text = it,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = actions,
    )
}
