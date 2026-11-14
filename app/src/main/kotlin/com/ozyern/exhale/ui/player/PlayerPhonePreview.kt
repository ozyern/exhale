/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PlayerArtworkStyle
import com.ozyern.exhale.constants.PlayerBackgroundStyle
import com.ozyern.exhale.constants.PlayerHorizontalPadding
import com.ozyern.exhale.constants.PlayerLook
import com.ozyern.exhale.constants.SliderStyle
import com.ozyern.exhale.ui.component.BlurredBackdropImage
import com.ozyern.exhale.ui.component.ScrollingWaveformSeekBar
import com.ozyern.exhale.ui.component.WavySlider
import com.ozyern.exhale.ui.component.PlayerSliderTrack
import com.ozyern.exhale.ui.component.heroFadeOutBrush
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.theme.PlayerColorExtractor
import com.ozyern.exhale.ui.theme.PlayerSliderColors
import com.ozyern.exhale.ui.theme.tiltedGradient
import com.ozyern.exhale.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The five player designs a listener picks between; each maps onto existing preferences. */
enum class PlayerDesign { CARD, VINYL, CLOVER, APPLE_MUSIC, APPLE_MUSIC_V2 }

/** The track a preview shows: the real one playing, or the last one played. */
data class PreviewTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
)

/** Everything that changes how the player looks, read from the same preferences the player reads. */
data class PlayerLookSpec(
    val design: PlayerDesign,
    val background: PlayerBackgroundStyle,
    val sliderStyle: SliderStyle,
    val look: PlayerLook,
    val staticColor: Color,
    val gradientStops: List<Color>,
    val gradientAngle: Float,
    val thumbnailShadow: Boolean,
    val showUpNext: Boolean,
    val showVolumeBar: Boolean = true,
)

/** A phone's logical screen: the preview lays out at this size and is scaled down, so type and spacing match the device. */
private val ScreenWidth = 390.dp
private val ScreenHeight = 844.dp

/**
 * A phone showing the player exactly as [spec] would draw it, at real size, scaled to [width].
 *
 * Built from the player's own parts — [BlurredBackdropImage], [NowPlayingBackground],
 * [MeshGradientBackground], the cover styles and the seek bars — so a background or seek
 * bar seen here is the one the player draws, not a picture of it.
 */
@Composable
fun PlayerPhonePreview(
    spec: PlayerLookSpec,
    track: PreviewTrack?,
    positionMs: () -> Long,
    isPlaying: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    upNext: String? = null,
    frame: Boolean = true,
) {
    val bezel = if (frame) width * 0.035f else 0.dp
    val screenWidth = width - bezel * 2
    val radius = width * 0.13f
    Box(
        modifier
            .width(width)
            .then(
                if (frame) Modifier
                    .shadow(18.dp, RoundedCornerShape(radius), clip = false)
                    .clip(RoundedCornerShape(radius))
                    .background(Color(0xFF0B0B0C))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(radius))
                    .padding(bezel)
                else Modifier
            ),
    ) {
        Box(Modifier.clip(RoundedCornerShape((radius - bezel).coerceAtLeast(4.dp)))) {
            ScaledScreen(screenWidth) {
                PlayerScreen(spec, track, positionMs, isPlaying, upNext)
            }
            if (frame) {
                // Camera cut-out, so the device reads as a phone at a glance.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = screenWidth * 0.025f)
                        .size(width = screenWidth * 0.26f, height = screenWidth * 0.07f)
                        .clip(CircleShape)
                        .background(Color.Black),
                )
            }
        }
    }
}

/** Lays [content] out at a real phone size and scales the drawing down to [width]. */
@Composable
private fun ScaledScreen(width: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.layout { measurable, _ ->
            val w = ScreenWidth.roundToPx()
            val h = ScreenHeight.roundToPx()
            val scale = width.toPx() / w
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout((w * scale).roundToInt(), (h * scale).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
        content = content,
    )
}

@Composable
private fun PlayerScreen(
    spec: PlayerLookSpec,
    track: PreviewTrack?,
    positionMs: () -> Long,
    isPlaying: Boolean,
    upNext: String?,
) {
    val v2 = spec.design == PlayerDesign.APPLE_MUSIC_V2
    val hero = spec.design == PlayerDesign.APPLE_MUSIC || (v2 && spec.look == PlayerLook.HERO)
    // The V2 player paints its own artwork-edge gradient for these two, exactly as Player.kt does.
    // Full bleed ignores the background choice; the square layout takes it, except the two it paints itself.
    val v2OwnBackground = v2 && (hero || spec.background in setOf(PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.GRADIENT))
    val appleChrome = v2 || spec.design == PlayerDesign.APPLE_MUSIC
    val background = if (spec.design == PlayerDesign.APPLE_MUSIC) PlayerBackgroundStyle.APPLE_MUSIC else spec.background
    val content = if (background == PlayerBackgroundStyle.DEFAULT && !v2) AppTheme.colorScheme.onSurface else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(if (background == PlayerBackgroundStyle.DEFAULT && !v2) AppTheme.colorScheme.surface else Color.Black),
    ) {
        if (v2OwnBackground) {
            NowPlayingBackground(track?.artworkUrl, hero = hero, modifier = Modifier.fillMaxSize())
        } else {
            PlayerBackdropPreview(background, track?.artworkUrl, isPlaying, spec, heroArtwork = !v2, modifier = Modifier.fillMaxSize())
        }
        if (v2 && hero) HeroArtwork(track?.artworkUrl)

        Column(Modifier.fillMaxSize()) {
            StatusBar(content)
            TopBar(content, hero)
            if (hero) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(if (v2) 36.dp else 28.dp))
                Artwork(spec, track, isPlaying, v2)
                Spacer(Modifier.height(if (v2) 36.dp else 32.dp))
            }
            TitleRow(track, content, v2)
            Spacer(Modifier.height(18.dp))
            if (appleChrome) AppleSeekBar(track, positionMs, content)
            else SeekBar(spec.sliderStyle, track, positionMs, isPlaying, content)
            Spacer(Modifier.height(6.dp))
            Times(track, positionMs, content)
            Spacer(Modifier.height(if (v2) 20.dp else 16.dp))
            Controls(isPlaying, content, v2)
            if (spec.showVolumeBar) {
                Spacer(Modifier.height(22.dp))
                VolumeRow(content)
            }
            if (spec.showUpNext && upNext != null) {
                Spacer(Modifier.height(18.dp))
                UpNextRow(upNext, content)
            }
            Spacer(Modifier.weight(if (hero) 0.25f else 1f))
            BottomRow(content)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun StatusBar(color: Color) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("9:41", color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(width = 26.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.9f)))
    }
}

@Composable
private fun TopBar(color: Color, hero: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Image(painterResource(R.drawable.expand_more), null, Modifier.size(30.dp), colorFilter = ColorFilter.tint(color))
        if (!hero) Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(color.copy(alpha = 0.45f)))
        Image(painterResource(R.drawable.more_horiz), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(color))
    }
}

/** The cover, in the shape and motion of the chosen style — the player's own [PlayerCoverStyle]. */
@Composable
private fun Artwork(spec: PlayerLookSpec, track: PreviewTrack?, isPlaying: Boolean, v2: Boolean) {
    val cover = when (spec.design) {
        PlayerDesign.VINYL -> PlayerArtworkStyle.VINYL
        PlayerDesign.CLOVER -> PlayerArtworkStyle.CLOVER
        else -> PlayerArtworkStyle.CARD
    }.coverStyle()
    val rotation by rememberCoverRotation(cover, isPlaying && !v2)
    val shape = cover.shape(if (v2) 10.dp else 16.dp)
    // V2 squares the cover down while paused, as Apple Music does.
    val pausedScale = if (v2 && !isPlaying) 0.82f else 1f
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
            .aspectRatio(1f)
            .graphicsLayer { scaleX = pausedScale; scaleY = pausedScale },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (spec.thumbnailShadow || v2) Modifier.shadow(24.dp, shape) else Modifier)
                .clip(shape)
                .graphicsLayer { rotationZ = rotation }
                .background(Color(0xFF2C2C2E)),
        ) {
            ArtworkImage(track?.artworkUrl, Modifier.fillMaxSize())
            cover.Decoration(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ArtworkImage(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(
            model = url.resize(600, 600),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.music_note), null, Modifier.size(96.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.4f)))
        }
    }
}

/** Full-bleed cover fading into the background at its foot — the hero layout. */
@Composable
private fun HeroArtwork(url: String?) {
    val fade = remember { heroFadeOutBrush() }
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = fade, blendMode = BlendMode.DstIn)
            },
    ) { ArtworkImage(url, Modifier.fillMaxSize()) }
}

@Composable
private fun TitleRow(track: PreviewTrack?, color: Color, v2: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                track?.title ?: "Nothing playing",
                color = color,
                fontSize = if (v2) 21.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist ?: "Play a song to preview it here",
                color = color.copy(alpha = 0.7f),
                fontSize = if (v2) 19.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.favorite_border), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(color))
        }
    }
}

/** The seek bar the player would draw for [style], fed the real playhead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    style: SliderStyle,
    track: PreviewTrack?,
    positionMs: () -> Long,
    isPlaying: Boolean,
    color: Color,
    horizontalPadding: Dp = PlayerHorizontalPadding,
) {
    val duration = (track?.durationMs ?: 0L).coerceAtLeast(1L)
    val fraction = (positionMs().toFloat() / duration).coerceIn(0f, 1f).let { if (track == null) 0.35f else it }
    val colors = PlayerSliderColors.getSliderColors(activeColor = color)
    val modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
    when (style) {
        SliderStyle.DEFAULT -> Slider(value = fraction, onValueChange = {}, colors = colors, modifier = modifier)
        SliderStyle.WAVY -> WavySlider(value = fraction, onValueChange = {}, colors = colors, isPlaying = isPlaying, modifier = modifier)
        SliderStyle.SLIM -> Slider(
            value = fraction,
            onValueChange = {},
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = { PlayerSliderTrack(sliderState = it, colors = colors, showAutomix = false) },
            modifier = modifier,
        )
        SliderStyle.WAVEFORM -> ScrollingWaveformSeekBar(
            progress = { fraction },
            onSeek = {},
            playedColor = colors.activeTrackColor,
            trackColor = colors.inactiveTrackColor,
            seed = track?.id.hashCode(),
            totalBars = 160,
            visibleBars = 44,
            modifier = modifier.height(40.dp),
        )
    }
}

/** Apple Music's own thin progress capsule: both Apple designs draw this, not a seek bar style. */
@Composable
private fun AppleSeekBar(track: PreviewTrack?, positionMs: () -> Long, color: Color) {
    val duration = (track?.durationMs ?: 0L).coerceAtLeast(1L)
    val fraction = if (track == null) 0.35f else (positionMs().toFloat() / duration).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
            .height(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color.copy(alpha = 0.85f)))
    }
}

/** One seek bar on its own, for picking a style: the same component the player draws. */
@Composable
fun PlayerSeekBarSample(style: SliderStyle, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val track = remember { PreviewTrack("sample", "", "", null, 100_000L) }
    Box(modifier, contentAlignment = Alignment.Center) {
        SeekBar(style, track, { 42_000L }, isPlaying, Color.White, horizontalPadding = 12.dp)
    }
}

@Composable
private fun Times(track: PreviewTrack?, positionMs: () -> Long, color: Color) {
    val duration = track?.durationMs ?: 0L
    Row(Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(positionMs()), color = color.copy(alpha = 0.6f), fontSize = 12.sp)
        Text("-" + formatTime((duration - positionMs()).coerceAtLeast(0L)), color = color.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Controls(isPlaying: Boolean, color: Color, v2: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding + 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!v2) Icon(R.drawable.shuffle, 24.dp, color.copy(alpha = 0.7f))
        Icon(R.drawable.skip_previous, if (v2) 44.dp else 38.dp, color)
        if (v2) {
            Icon(if (isPlaying) R.drawable.pause else R.drawable.play, 56.dp, color)
        } else {
            Box(Modifier.size(72.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(if (isPlaying) R.drawable.pause else R.drawable.play, 36.dp, if (color == Color.White) Color.Black else AppTheme.colorScheme.surface)
            }
        }
        Icon(R.drawable.skip_next, if (v2) 44.dp else 38.dp, color)
        if (!v2) Icon(R.drawable.repeat, 24.dp, color.copy(alpha = 0.7f))
    }
}

@Composable
private fun Icon(res: Int, size: Dp, tint: Color) {
    Image(painterResource(res), null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
private fun VolumeRow(color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.volume_down, 18.dp, color.copy(alpha = 0.7f))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f)),
        ) { Box(Modifier.fillMaxHeight().fillMaxWidth(0.6f).background(color.copy(alpha = 0.85f))) }
        Icon(R.drawable.volume_up, 18.dp, color.copy(alpha = 0.7f))
    }
}

@Composable
private fun UpNextRow(title: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("UP NEXT", color = color.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(title, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BottomRow(color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding + 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(R.drawable.lyrics, 24.dp, color.copy(alpha = 0.75f))
        Icon(R.drawable.cast, 24.dp, color.copy(alpha = 0.75f))
        Icon(R.drawable.queue_music, 24.dp, color.copy(alpha = 0.75f))
    }
}

/**
 * The player's background for [style], drawn from the same parts and constants as the
 * `background` slot in Player.kt.
 *
 * ponytail: mirrors Player.kt's background `when` rather than sharing it, because that block
 * is wired to the live sheet (backdrop capture, canvas video, collapse alpha). If the two
 * drift, extract one composable from Player.kt and call it from both.
 */
@Composable
fun PlayerBackdropPreview(
    style: PlayerBackgroundStyle,
    artworkUrl: String?,
    isPlaying: Boolean,
    spec: PlayerLookSpec?,
    modifier: Modifier = Modifier,
    heroArtwork: Boolean = true,
) {
    Box(modifier) {
        when (style) {
            PlayerBackgroundStyle.DEFAULT -> Box(Modifier.fillMaxSize().background(AppTheme.colorScheme.surface))
            PlayerBackgroundStyle.BLUR -> if (artworkUrl != null) {
                BlurredBackdropImage(model = artworkUrl, screenBlurRadiusDp = 150f, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            PlayerBackgroundStyle.GRADIENT -> {
                val colors by rememberArtworkColors(artworkUrl, glow = false)
                val stops = if (colors.size >= 3) arrayOf(0f to colors[0], 0.5f to colors[1], 1f to colors[2])
                else if (colors.isNotEmpty()) arrayOf(0f to colors[0], 0.6f to colors[0].copy(alpha = 0.7f), 1f to Color.Black)
                else arrayOf(0f to Color(0xFF2C2C2E), 1f to Color.Black)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = stops)).background(Color.Black.copy(alpha = 0.2f)))
            }
            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                val colors by rememberArtworkColors(artworkUrl, glow = true)
                GlowBackground(colors, isPlaying)
            }
            PlayerBackgroundStyle.APPLE_MUSIC -> if (artworkUrl != null) {
                NowPlayingBackground(artworkUrl, hero = true, modifier = Modifier.fillMaxSize())
                if (heroArtwork) HeroArtwork(artworkUrl)
            }
            PlayerBackgroundStyle.LIVE_MESH -> if (artworkUrl != null) LiveMeshBackground(artworkUrl, isPlaying)
            PlayerBackgroundStyle.FLUID_MESH -> MeshGradientBackground(
                palette = rememberArtworkMeshPalette(artworkUrl),
                trackKey = artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
            PlayerBackgroundStyle.STATIC -> Box(Modifier.fillMaxSize().background(spec?.staticColor ?: Color(0xFF1A1A1A)))
            PlayerBackgroundStyle.CUSTOM_GRADIENT -> Box(
                Modifier.fillMaxSize().tiltedGradient(spec?.gradientStops ?: listOf(Color(0xFF3A1C71), Color(0xFFD76D77)), spec?.gradientAngle ?: 90f),
            )
            // Glass is the app showing through a frosted film; the frosted cover stands in for the app.
            PlayerBackgroundStyle.GLASS -> if (artworkUrl != null) {
                BlurredBackdropImage(model = artworkUrl, screenBlurRadiusDp = 60f, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
            }
        }
    }
}

/** The palette Player.kt extracts for GRADIENT ([glow] = false) and GLOW_ANIMATED ([glow] = true). */
@Composable
private fun rememberArtworkColors(url: String?, glow: Boolean): State<List<Color>> {
    val context = LocalContext.current
    return produceState(emptyList(), url, glow) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context).data(url).size(200, 200).allowHardware(false).build()
            val bitmap = runCatching { context.imageLoader.execute(request) }.getOrNull()?.image?.toBitmap()
                ?: return@withContext emptyList()
            val palette = Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()
            val fallback = PlayerColorExtractor.NeutralFallbackColor.toArgb()
            if (glow) listOf(
                Color(palette.getVibrantColor(fallback)),
                Color(palette.getLightVibrantColor(fallback)),
                Color(palette.getDarkVibrantColor(fallback)),
                Color(palette.getMutedColor(fallback)),
                Color(palette.getLightMutedColor(fallback)),
                Color(palette.getDarkMutedColor(fallback)),
            ).distinct()
            else PlayerColorExtractor.extractGradientColors(palette = palette, fallbackColor = fallback)
        }
    }
}

@Composable
private fun GlowBackground(colors: List<Color>, isPlaying: Boolean) {
    val progress = if (isPlaying) rememberInfiniteTransition(label = "glowPreview").animateFloat(
        0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "glowProgress",
    ) else remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF050505))
            if (colors.isEmpty()) return@drawBehind
            val p = progress.value
            fun colorAt(i: Int): Color {
                val idx = i + p * colors.size
                val a = kotlin.math.floor(idx).toInt() % colors.size
                return androidx.compose.ui.graphics.lerp(colors[a], colors[(a + 1) % colors.size], idx - kotlin.math.floor(idx))
            }
            fun osc(min: Float, max: Float, phase: Float): Float =
                min + (max - min) * ((kotlin.math.sin(2f * Math.PI.toFloat() * (p + phase)) + 1f) / 2f)
            // Same six blobs, phases and strengths as the player's GLOW_ANIMATED.
            val blobs = listOf(
                floatArrayOf(osc(0f, 1f, 0f), osc(0f, .5f, .07f), osc(.8f, 1.6f, .12f), .85f),
                floatArrayOf(osc(1f, 0f, .2f), osc(.5f, 1f, .25f), osc(.7f, 1.5f, .18f), .8f),
                floatArrayOf(osc(.2f, .8f, .33f), osc(.8f, .2f, .36f), osc(.6f, 1.4f, .29f), .75f),
                floatArrayOf(osc(.3f, .7f, .44f), osc(.2f, .8f, .41f), osc(.9f, 1.7f, .47f), .7f),
                floatArrayOf(osc(.4f, .6f, .55f), osc(0f, 1f, .51f), osc(.7f, 1.5f, .58f), .65f),
                floatArrayOf(osc(0f, 1f, .66f), osc(.5f, .7f, .62f), osc(.8f, 1.8f, .69f), .6f),
            )
            blobs.forEachIndexed { i, (x, y, r, a) ->
                val c = colorAt(i)
                drawRect(
                    Brush.radialGradient(
                        listOf(c.copy(alpha = a), c.copy(alpha = a * 0.6f), Color.Transparent),
                        center = Offset(size.width * x, size.height * y),
                        radius = size.width * r,
                    ),
                )
            }
        },
    )
}

@Composable
private fun LiveMeshBackground(url: String, isPlaying: Boolean) {
    val zero = remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "liveMeshPreview")
    fun spin(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing), RepeatMode.Restart)
    val anchor = if (isPlaying) transition.animateFloat(0f, -360f, spin(80000), label = "anchor") else zero
    val fast = if (isPlaying) transition.animateFloat(0f, 360f, spin(40000), label = "fast") else zero
    val slow = if (isPlaying) transition.animateFloat(0f, 360f, spin(60000), label = "slow") else zero
    val filter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.8f) }) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = 1.7f; scaleY = 1.7f }
            .blur(32.dp),
    ) {
        BlurredBackdropImage(url, 100f, Modifier.fillMaxSize().graphicsLayer { rotationZ = anchor.value }, decodeSize = 160, colorFilter = filter)
        BlurredBackdropImage(url, 120f, Modifier.fillMaxSize().graphicsLayer { rotationZ = fast.value; alpha = 0.6f }, decodeSize = 160, alignment = Alignment.TopStart, colorFilter = filter)
        BlurredBackdropImage(url, 120f, Modifier.fillMaxSize().graphicsLayer { rotationZ = slow.value; alpha = 0.5f }, decodeSize = 160, alignment = Alignment.BottomEnd, colorFilter = filter)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))))
    }
}

/** The playhead, ticking while playing so the preview's seek bar and clock move with the song. */
@Composable
fun rememberPreviewPosition(read: () -> Long, isPlaying: Boolean): () -> Long {
    var position by remember { mutableLongStateOf(read()) }
    LaunchedEffect(isPlaying) {
        position = read()
        while (isPlaying) {
            delay(250)
            position = read()
        }
    }
    return { position }
}
