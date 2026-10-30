/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.ozyern.exhale.utils.ComposeToImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.liquidGlass
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.UseSystemFontKey
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.rememberPreference

// ─────────────────────────────────────────────────────────────────────────────
// Shared by every layout
// ─────────────────────────────────────────────────────────────────────────────

internal val LyricsCardSize       = 340.dp
internal val LyricsCardCorner     = 24.dp

// ─────────────────────────────────────────────────────────────────────────────
// Helpers every layout uses
// ─────────────────────────────────────────────────────────────────────────────

/** The lyrics font, following the listener's font setting */
@Composable
internal fun rememberLyricsFontFamily(): FontFamily? {
    val (useSystemFont) = rememberPreference(UseSystemFontKey, defaultValue = false)
    return remember(useSystemFont) {
        if (useSystemFont) null else FontFamily(Font(R.font.linotte))
    }
}

/** The artwork, crossfaded in */
@Composable
internal fun rememberArtworkPainter(thumbnailUrl: String?): Painter =
    rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current)
            .data(thumbnailUrl)
            .crossfade(true)
            .build()
    )

/** The Exhale signature at the foot of every layout, when showBranding is on */
@Composable
internal fun LyricsBrandingRow(
    secondaryColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AppIcon(size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text  = context.getString(R.string.app_name),
            color = secondaryColor,
            fontSize    = 13.sp,
            fontWeight  = FontWeight.SemiBold,
            letterSpacing = 0.02.em,
        )
    }
}

/**
 * The song row: artwork, title and artist.
 * Follows the config's showCoverArt / showTitle / showArtist.
 */
@Composable
internal fun LyricsMetadataRow(
    mediaMetadata: MediaMetadata,
    artworkPainter: Painter,
    config: LyricsCardConfig,
    mainTextColor: Color,
    secondaryColor: Color,
    coverArtSize: Dp = 56.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        if (config.showCoverArt) {
            Image(
                painter      = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier
                    .size(coverArtSize)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            if (config.showTitle) {
                Text(
                    text       = mediaMetadata.title,
                    color      = mainTextColor,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.padding(bottom = 2.dp),
                    style      = TextStyle(letterSpacing = (-0.02).em),
                )
            }
            if (config.showArtist) {
                Text(
                    text       = mediaMetadata.artists.joinToString { it.name },
                    color      = secondaryColor,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The default launcher icon, masked as the launcher masks it; signs every card. */
@Composable
internal fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val icon = remember(px) {
        runCatching { context.getDrawable(R.mipmap.ic_launcher)?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()
    } ?: return
    Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 0 — Vivid (Apple Music's lyric card)
// Mirrors ComposeToImage.renderVivid, so the preview is the PNG.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VividLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val context        = LocalContext.current
    val density        = LocalDensity.current
    val fontFamily     = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val url            = mediaMetadata.thumbnailUrl

    var colors by remember(url) { mutableStateOf(Color(0xFF3A3A3C) to Color(0xFF1C1C1E)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
        val bitmap = (runCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult)
            ?.image?.toBitmap() ?: return@LaunchedEffect
        val (top, bottom) = withContext(Dispatchers.Default) { ComposeToImage.lyricCardColors(bitmap) }
        colors = Color(top) to Color(bottom)
    }

    val padding = config.cardPadding * 1.15f
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner))
                .background(
                    Brush.linearGradient(
                        colors = listOf(colors.first, colors.second),
                        start = Offset.Zero,
                        end = with(density) { Offset(LyricsCardSize.toPx() * 0.35f, LyricsCardSize.toPx()) },
                    )
                )
                .padding(padding),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                val textStyle = TextStyle(
                    color         = Color.White,
                    fontFamily    = fontFamily,
                    fontWeight    = FontWeight.Bold,
                    textAlign     = config.textAlign,
                    letterSpacing = (-0.01).em,
                )
                val fontSize = rememberAdjustedFontSize(
                    text            = lyricText,
                    maxWidth        = maxWidth,
                    maxHeight       = maxHeight - padding * 0.8f,
                    density         = density,
                    initialFontSize = (34f * config.textSizeMultiplier).sp,
                    minFontSize     = 11.sp,
                    style           = textStyle,
                    textMeasurer    = rememberTextMeasurer(),
                )
                Text(
                    text     = lyricText,
                    style    = textStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.15f),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(padding * 0.8f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LyricsCardSize * 0.12f),
            ) {
                if (config.showCoverArt) {
                    Image(
                        painter            = artworkPainter,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (config.showTitle) {
                        Text(
                            text       = mediaMetadata.title,
                            color      = Color.White,
                            fontFamily = fontFamily,
                            fontSize   = 12.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                    }
                    if (config.showArtist) {
                        Text(
                            text       = mediaMetadata.artists.joinToString { it.name },
                            color      = Color.White.copy(alpha = 0.62f),
                            fontFamily = fontFamily,
                            fontSize   = 10.5.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (config.showBranding) {
                    Spacer(Modifier.width(12.dp))
                    AppIcon(size = LyricsCardSize * 0.085f)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The one entry point for every caller.
// A new layout is a branch in the when plus its composable below.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LyricsCardByLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    when (config.layoutStyle) {
        LyricsLayoutStyle.Vivid          -> VividLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.GlassCard      -> GlassCardLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.Minimal        -> MinimalLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.CoverFocused   -> CoverFocusedLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.Centered       -> CenteredLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.BlurWash       -> BlurWashLayout(lyricText, mediaMetadata, config, modifier)
        LyricsLayoutStyle.StreamingModern -> StreamingModernLayout(lyricText, mediaMetadata, config, modifier)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 1 — Glass Card
// The original LyricsImageCard's design; LyricsImageCard hands over to it.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GlassCardLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density       = LocalDensity.current
    val fontFamily    = rememberLyricsFontFamily()
    val glassStyle    = config.glassStyle
    val mainTextColor = glassStyle.textColor
    val secondaryColor = glassStyle.secondaryTextColor
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)

    var glassComponentSize by remember { mutableStateOf(Size.Zero) }
    val lensCenter = remember(glassComponentSize) {
        Offset(glassComponentSize.width / 2f, glassComponentSize.height / 2f)
    }
    val lensSize = remember(glassComponentSize) {
        Size(glassComponentSize.width, glassComponentSize.height)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner)),
            contentAlignment = Alignment.Center,
        ) {
            // Blurred artwork behind
            Image(
                painter      = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize().cloudy(radius = glassStyle.cloudyRadius),
            )
            // Gradiente oscurecedor
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = glassStyle.backgroundDimAlpha * 0.8f),
                            Color.Black.copy(alpha = glassStyle.backgroundDimAlpha),
                            Color.Black.copy(alpha = glassStyle.backgroundDimAlpha * 1.2f),
                        )
                    )
                )
            )

            // The liquid glass panel
            Box(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxSize()
                    .onSizeChanged { glassComponentSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .cloudy(radius = glassStyle.cloudyRadius)
                        .then(
                            if (glassComponentSize.width > 0f && glassComponentSize.height > 0f) {
                                Modifier.liquidGlass(
                                    lensCenter  = lensCenter,
                                    lensSize    = lensSize,
                                    cornerRadius = glassStyle.glassCornerRadius,
                                    refraction  = glassStyle.refraction,
                                    curve       = glassStyle.curve,
                                    dispersion  = glassStyle.dispersion,
                                    saturation  = glassStyle.glassSaturation,
                                    contrast    = glassStyle.glassContrast,
                                    tint        = glassStyle.glassTint,
                                    edge        = glassStyle.glassEdge,
                                )
                            } else Modifier
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(glassStyle.surfaceTint.copy(alpha = glassStyle.surfaceAlpha))
                            drawRect(glassStyle.overlayColor.copy(alpha = glassStyle.overlayAlpha))
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(config.cardPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LyricsMetadataRow(mediaMetadata, artworkPainter, config, mainTextColor, secondaryColor)

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val textStyle = TextStyle(
                            color         = mainTextColor,
                            fontWeight    = FontWeight.SemiBold,
                            textAlign     = config.textAlign,
                            letterSpacing = (-0.01).em,
                            fontFamily    = fontFamily,
                        )
                        val initialSize = when {
                            lyricText.length < 50  -> 22.sp
                            lyricText.length < 100 -> 19.sp
                            lyricText.length < 200 -> 16.sp
                            lyricText.length < 300 -> 14.sp
                            else                   -> 12.sp
                        }
                        val dynamicFontSize = rememberAdjustedFontSize(
                            text            = lyricText,
                            maxWidth        = maxWidth - 6.dp,
                            maxHeight       = maxHeight - 6.dp,
                            density         = density,
                            initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                            minFontSize     = 11.sp,
                            style           = textStyle,
                            textMeasurer    = rememberTextMeasurer(),
                        )
                        Text(
                            text      = lyricText,
                            style     = textStyle.copy(
                                fontSize   = dynamicFontSize,
                                lineHeight = dynamicFontSize.value.sp * 1.35f,
                            ),
                            overflow  = TextOverflow.Ellipsis,
                            textAlign = config.textAlign,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    }

                    if (config.showBranding) {
                        LyricsBrandingRow(secondaryColor, glassStyle.isDark)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 2 — Minimal
// A solid ground, large type and a quotation mark; no glass.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MinimalLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density    = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val isDark     = config.glassStyle.isDark

    val bgColor      = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF5F5F5)
    val mainText     = if (isDark) Color.White else Color(0xFF1A1A1A)
    val secondaryTxt = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF1A1A1A).copy(alpha = 0.5f)
    // Accent: the style's tint, unless it is pure black or white
    val accent = config.glassStyle.surfaceTint.let {
        if (it == Color.Black || it == Color.White) Color(0xFF6366F1) else it.copy(alpha = 1f)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner))
                .background(bgColor),
        ) {
            // Accent line along the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0f))))
                    .align(Alignment.TopCenter),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Quotation mark
                Text(
                    text       = "\u201C",
                    color      = accent.copy(alpha = 0.25f),
                    fontSize   = 80.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 64.sp,
                )

                // Letra
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val textStyle = TextStyle(
                        color         = mainText,
                        fontWeight    = FontWeight.Bold,
                        textAlign     = config.textAlign,
                        fontFamily    = fontFamily,
                        letterSpacing = (-0.02).em,
                    )
                    val initialSize = when {
                        lyricText.length < 50  -> 26.sp
                        lyricText.length < 100 -> 22.sp
                        lyricText.length < 200 -> 18.sp
                        else                   -> 14.sp
                    }
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text            = lyricText,
                        maxWidth        = maxWidth - 6.dp,
                        maxHeight       = maxHeight - 6.dp,
                        density         = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize     = 11.sp,
                        style           = textStyle,
                        textMeasurer    = rememberTextMeasurer(),
                    )
                    Text(
                        text      = lyricText,
                        style     = textStyle.copy(
                            fontSize   = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.4f,
                        ),
                        overflow  = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }

                // Pie: separador + metadatos + branding
                Column {
                    if (config.showTitle || config.showArtist) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(mainText.copy(alpha = 0.1f))
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (config.showTitle) {
                        Text(
                            text       = mediaMetadata.title,
                            color      = mainText,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                    }
                    if (config.showArtist) {
                        Text(
                            text       = mediaMetadata.artists.joinToString { it.name },
                            color      = secondaryTxt,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                    }
                    if (config.showBranding) {
                        Spacer(Modifier.height(8.dp))
                        LyricsBrandingRow(secondaryTxt, isDark)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 3 — Cover Focused
// The artwork across the top; the lyrics below it, over blur.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CoverFocusedLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density        = LocalDensity.current
    val fontFamily     = rememberLyricsFontFamily()
    val glassStyle     = config.glassStyle
    val mainTextColor  = glassStyle.textColor
    val secondaryColor = glassStyle.secondaryTextColor
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner)),
        ) {
            // Blurred artwork behind everything
            Image(
                painter      = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize().cloudy(radius = 22),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large artwork
                if (config.showCoverArt) {
                    Image(
                        painter      = artworkPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier     = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (config.showTitle) {
                    Text(
                        text       = mediaMetadata.title,
                        color      = mainTextColor,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        textAlign  = TextAlign.Center,
                        style      = TextStyle(letterSpacing = (-0.02).em),
                    )
                }
                if (config.showArtist) {
                    Text(
                        text       = mediaMetadata.artists.joinToString { it.name },
                        color      = secondaryColor,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        textAlign  = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
                Spacer(Modifier.height(10.dp))

                // Letra
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val textStyle = TextStyle(
                        color         = mainTextColor,
                        fontWeight    = FontWeight.SemiBold,
                        textAlign     = config.textAlign,
                        fontFamily    = fontFamily,
                        letterSpacing = (-0.01).em,
                    )
                    val initialSize = when {
                        lyricText.length < 50  -> 20.sp
                        lyricText.length < 100 -> 17.sp
                        lyricText.length < 200 -> 14.sp
                        else                   -> 12.sp
                    }
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text            = lyricText,
                        maxWidth        = maxWidth - 6.dp,
                        maxHeight       = maxHeight - 6.dp,
                        density         = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize     = 10.sp,
                        style           = textStyle,
                        textMeasurer    = rememberTextMeasurer(),
                    )
                    Text(
                        text      = lyricText,
                        style     = textStyle.copy(
                            fontSize   = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.4f,
                        ),
                        overflow  = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }

                if (config.showBranding) {
                    Spacer(Modifier.height(8.dp))
                    LyricsBrandingRow(secondaryColor, glassStyle.isDark, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 4 — Centered
// The lyrics dead centre; the song above and the signature below.
// No glass panel, only a scrim over the blurred artwork.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CenteredLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density        = LocalDensity.current
    val fontFamily     = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val mainTextColor  = config.glassStyle.textColor
    val secondaryColor = config.glassStyle.secondaryTextColor

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner)),
        ) {
            // Blurred artwork
            Image(
                painter      = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize().cloudy(radius = config.glassStyle.cloudyRadius),
            )
            // Vertical scrim, heavier at the edges
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.72f),
                        )
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement    = Arrangement.SpaceBetween,
                horizontalAlignment    = Alignment.CenterHorizontally,
            ) {
                // Metadatos arriba (centrados)
                if (config.showTitle || config.showArtist) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (config.showTitle) {
                            Text(
                                text          = mediaMetadata.title,
                                color         = mainTextColor,
                                fontSize      = 15.sp,
                                fontWeight    = FontWeight.Bold,
                                maxLines      = 1,
                                overflow      = TextOverflow.Ellipsis,
                                textAlign     = TextAlign.Center,
                                letterSpacing = 0.01.em,
                            )
                        }
                        if (config.showArtist) {
                            Text(
                                text          = mediaMetadata.artists.joinToString { it.name },
                                color         = secondaryColor,
                                fontSize      = 12.sp,
                                maxLines      = 1,
                                overflow      = TextOverflow.Ellipsis,
                                textAlign     = TextAlign.Center,
                                letterSpacing = 0.04.em,
                            )
                        }
                    }
                }

                // The lyrics, centred, taking all the room there is
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val textStyle = TextStyle(
                        color         = mainTextColor,
                        fontWeight    = FontWeight.Bold,
                        textAlign     = TextAlign.Center,
                        fontFamily    = fontFamily,
                        letterSpacing = (-0.02).em,
                    )
                    val initialSize = when {
                        lyricText.length < 40  -> 28.sp
                        lyricText.length < 80  -> 24.sp
                        lyricText.length < 150 -> 20.sp
                        lyricText.length < 250 -> 16.sp
                        else                   -> 13.sp
                    }
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text            = lyricText,
                        maxWidth        = maxWidth - 6.dp,
                        maxHeight       = maxHeight - 6.dp,
                        density         = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize     = 11.sp,
                        style           = textStyle,
                        textMeasurer    = rememberTextMeasurer(),
                    )
                    Text(
                        text      = lyricText,
                        style     = textStyle.copy(
                            fontSize   = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.4f,
                        ),
                        overflow  = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }

                if (config.showBranding) {
                    LyricsBrandingRow(secondaryColor, config.glassStyle.isDark, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 5 — Blur Wash
// A very heavy blur behind; the lyrics on a frosted panel.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BlurWashLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density        = LocalDensity.current
    val fontFamily     = rememberLyricsFontFamily()
    val glassStyle     = config.glassStyle
    val mainTextColor  = glassStyle.textColor
    val secondaryColor = glassStyle.secondaryTextColor
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)

    // The inner panel's size, for liquidGlass
    var panelSize by remember { mutableStateOf(Size.Zero) }
    val lensCenter = remember(panelSize) { Offset(panelSize.width / 2f, panelSize.height / 2f) }
    val lensSize   = remember(panelSize) { Size(panelSize.width, panelSize.height) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner)),
        ) {
            // Artwork at full blur
            Image(
                painter      = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize().cloudy(radius = 30),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header compacto
                LyricsMetadataRow(mediaMetadata, artworkPainter, config, mainTextColor, secondaryColor, 46.dp)

                // The lyrics on the frosted panel
                val textMeasurer = rememberTextMeasurer()

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val constraintsScope = this

                    val availableWidth = constraintsScope.maxWidth - 32.dp
                    val availableHeight = constraintsScope.maxHeight - 32.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged {
                                panelSize = Size(
                                    it.width.toFloat(),
                                    it.height.toFloat()
                                )
                            }
                            .clip(RoundedCornerShape(18.dp)),
                    ) {

                        // Superficie frosted
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .cloudy(radius = 14)
                                .then(
                                    if (panelSize.width > 0f && panelSize.height > 0f) {
                                        Modifier.liquidGlass(
                                            lensCenter = lensCenter,
                                            lensSize = lensSize,
                                            cornerRadius = 40f,
                                            refraction = 0.18f,
                                            curve = 0.18f,
                                            dispersion = 0.0f,
                                            saturation = 1.08f,
                                            contrast = 1.0f,
                                            tint = Color.White.copy(alpha = 0.08f),
                                            edge = 0.18f,
                                        )
                                    } else Modifier
                                )
                                .drawWithContent {
                                    drawContent()
                                    drawRect(Color.White.copy(alpha = 0.07f))
                                }
                        )

                        val textStyle = TextStyle(
                            color = mainTextColor,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = config.textAlign,
                            fontFamily = fontFamily,
                            letterSpacing = (-0.01).em,
                        )

                        val initialSize = when {
                            lyricText.length < 50 -> 21.sp
                            lyricText.length < 100 -> 18.sp
                            lyricText.length < 200 -> 15.sp
                            else -> 12.sp
                        }

                        // Scoped explicitly so the receiver resolves
                        val dynamicFontSize = rememberAdjustedFontSize(
                            text = lyricText,
                            maxWidth = availableWidth,
                            maxHeight = availableHeight,
                            density = density,
                            initialFontSize =
                                (initialSize.value * config.textSizeMultiplier).sp,
                            minFontSize = 11.sp,
                            style = textStyle,
                            textMeasurer = textMeasurer,
                        )

                        Text(
                            text = lyricText,
                            style = textStyle.copy(
                                fontSize = dynamicFontSize,
                                lineHeight = dynamicFontSize.value.sp * 1.35f,
                            ),
                            overflow = TextOverflow.Ellipsis,
                            textAlign = config.textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }

                if (config.showBranding) {
                    Spacer(Modifier.height(4.dp))
                    LyricsBrandingRow(secondaryColor, glassStyle.isDark)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout 6 — Streaming Modern
// A dark gradient, a colour accent and a quotation mark, in a streaming app's style.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun StreamingModernLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density        = LocalDensity.current
    val fontFamily     = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)

    // Accent: the glass style's surfaceTint, or indigo when that is black or white
    val accent = config.glassStyle.surfaceTint.let {
        if (it == Color.Black || it == Color.White) Color(0xFF6366F1) else it.copy(alpha = 1f)
    }
    val mainTextColor  = Color.White
    val secondaryColor = Color.White.copy(alpha = 0.55f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LyricsCardSize)
                .clip(RoundedCornerShape(LyricsCardCorner))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0D0D1A), Color(0xFF191928)))
                ),
        ) {
            // Accent glow in the top right corner
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .offset(x = 120.dp, y = (-50).dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header: small artwork, title, artist and a play mark
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (config.showCoverArt) {
                        Image(
                            painter      = artworkPainter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (config.showTitle) {
                            Text(
                                text          = mediaMetadata.title,
                                color         = mainTextColor,
                                fontSize      = 15.sp,
                                fontWeight    = FontWeight.Bold,
                                maxLines      = 1,
                                overflow      = TextOverflow.Ellipsis,
                                letterSpacing = (-0.02).em,
                            )
                        }
                        if (config.showArtist) {
                            Text(
                                text          = mediaMetadata.artists.joinToString { it.name },
                                color         = secondaryColor,
                                fontSize      = 12.sp,
                                maxLines      = 1,
                                overflow      = TextOverflow.Ellipsis,
                                letterSpacing = 0.01.em,
                            )
                        }
                    }
                    // Decorative play mark
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("\u25B6", color = accent, fontSize = 9.sp)
                    }
                }

                // Accent divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent))
                        )
                )

                // The lyrics, under an opening quotation mark
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val textStyle = TextStyle(
                        color         = mainTextColor,
                        fontWeight    = FontWeight.Bold,
                        textAlign     = config.textAlign,
                        fontFamily    = fontFamily,
                        letterSpacing = (-0.02).em,
                    )
                    val initialSize = when {
                        lyricText.length < 50  -> 23.sp
                        lyricText.length < 100 -> 19.sp
                        lyricText.length < 200 -> 16.sp
                        else                   -> 13.sp
                    }
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text            = lyricText,
                        maxWidth        = maxWidth - 6.dp,
                        maxHeight       = maxHeight - 36.dp,  // room for the quotation mark
                        density         = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize     = 11.sp,
                        style           = textStyle,
                        textMeasurer    = rememberTextMeasurer(),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text       = "\u201C",
                            color      = accent.copy(alpha = 0.55f),
                            fontSize   = 38.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 30.sp,
                        )
                        Text(
                            text      = lyricText,
                            style     = textStyle.copy(
                                fontSize   = dynamicFontSize,
                                lineHeight = dynamicFontSize.value.sp * 1.35f,
                            ),
                            overflow  = TextOverflow.Ellipsis,
                            textAlign = config.textAlign,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (config.showBranding) {
                    LyricsBrandingRow(secondaryColor, isDark = true)
                }
            }
        }
    }
}
