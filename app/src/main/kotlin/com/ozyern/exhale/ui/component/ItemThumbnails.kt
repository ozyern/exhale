/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.GridThumbnailHeight
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import kotlinx.coroutines.flow.first

/** Real pixel size of a [dp] box on this device, for sizing an image decode to what
 *  the layout can actually show. */
@Composable
fun thumbnailPx(dp: Dp): Int = with(LocalDensity.current) { dp.roundToPx() }

/**
 * The no-artwork look wherever a thumbnail is missing: a flat dark tile (no shadow —
 * there is nothing for one to sit behind) with the app's own logo centred at low
 * alpha, same shape the reference app uses for its music-note glyph. Swapped in as an
 * `AsyncImage`'s `placeholder`/`error` painter rather than restructuring those call
 * sites into `SubcomposeAsyncImage`, which would pay a subcomposition cost on every
 * successful load too, not just the rare missing-artwork case.
 */
@Composable
fun rememberNoArtworkPainter(
    tintAlpha: Float = 0.35f,
    fillAlpha: Float = 0.18f,
): androidx.compose.ui.graphics.painter.Painter {
    val logoPainter = painterResource(R.drawable.exhale_logo)
    return remember(logoPainter, tintAlpha, fillAlpha) {
        object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize = androidx.compose.ui.geometry.Size.Unspecified
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                drawRect(Color.Black.copy(alpha = fillAlpha))
                val logoSize = minOf(size.width, size.height) * 0.4f
                translate(
                    left = (size.width - logoSize) / 2f,
                    top = (size.height - logoSize) / 2f,
                ) {
                    with(logoPainter) {
                        draw(
                            size = androidx.compose.ui.geometry.Size(logoSize, logoSize),
                            alpha = tintAlpha,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
    /** Decode size. Grid tiles keep this density-scaled default; list rows pass their
     *  real on-screen size via [thumbnailPx], because decoding a 544px bitmap for a
     *  48dp row is ~10x the pixels the row can show. Do NOT lower this default below
     *  the grid tile's own real size: the same composable draws the big tiles, and
     *  they need every pixel. A flat 544 (rather than this device-relative value) was
     *  under the tile's actual pixel size on anything above ~3.3x density, which is
     *  what read as soft/upscaled artwork on higher-density phones. */
    targetSizePx: Int = thumbnailPx(GridThumbnailHeight),
    /** Overrides the global CropAlbumArtKey preference when set — for tiles that
     *  should always fill edge-to-edge (Apple Music style) regardless of the
     *  user's general album-art setting, e.g. Home's Speed Dial. */
    forceContentScale: ContentScale? = null,
    showPausedPlayIcon: Boolean = true,
    /** Id of the album/artist/playlist/song this artwork belongs to. Supplying it makes
     *  the thumbnail the source of the artwork morph into that item's detail screen --
     *  but only for the tile actually being opened, so the same album appearing in two
     *  rails does not put two claimants on one key. See [sharedArtworkSource]. */
    sharedArtworkId: String? = null,
    appleArtQuery: com.ozyern.exhale.utils.AppleArtQuery? = null,
) {
    val cropAlbumArtPref = LocalItemPrefs.current.cropAlbumArt
    val cropAlbumArt = forceContentScale == ContentScale.Crop || (forceContentScale == null && cropAlbumArtPref)
    val context = LocalContext.current
    val actualTargetSizePx = targetSizePx
    val displayUrl = com.ozyern.exhale.utils.rememberAppleArtworkUrl(appleArtQuery, thumbnailUrl, actualTargetSizePx)
    val artworkOwner = com.ozyern.exhale.ui.utils.rememberArtworkSourceOwner()
    val imageListener = com.ozyern.exhale.ui.utils.sharedArtworkImageListener(artworkOwner, displayUrl)
    val imageRequest = remember(displayUrl, actualTargetSizePx) {
        val resolvedUrl = displayUrl?.resize(actualTargetSizePx, actualTargetSizePx)
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            // Key by the resolved URL so each quality tier caches its own image size.
            // Offline misses are possible when this exact resized URL was never fetched.
            .diskCacheKey(resolvedUrl)
            .size(CoilSize(actualTargetSizePx, actualTargetSizePx))
            .allowHardware(true)
            .bitmapConfig(android.graphics.Bitmap.Config.HARDWARE)
            .crossfade(false)
            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
            .build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            // Before the clip, so the overlay animates the rounded artwork rather than a
            // square that gets clipped at each end of the flight.
            .sharedArtworkSource(sharedArtworkId, displayUrl, artworkOwner)
            .clip(shape)
    ) {
        if (albumIndex == null) {
            AsyncImage(
                model = imageRequest,
                onSuccess = imageListener,
                contentDescription = null,
                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                placeholder = painterResource(R.drawable.thumbnail_fallback),
                error = painterResource(R.drawable.thumbnail_fallback),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = AppTheme.typography.labelLarge
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                SymbolIcon(symbol = sf("checkmark"), contentDescription = null)
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) AppTheme.colorScheme.onBackground else Color.White,
            showPausedIcon = showPausedPlayIcon,
            modifier = Modifier
                .fillMaxSize()
                // Transparent draws the same nothing as no background at all -- skip the
                // shape-fill op for numbered album-track rows (albumIndex != null), every
                // track, every album screen.
                .let {
                    if (albumIndex != null) it
                    else it.background(color = Color.Black.copy(alpha = ActiveBoxAlpha), shape = shape)
                }
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
    sharedArtworkId: String? = null,
) {
    val cropAlbumArt = LocalItemPrefs.current.cropAlbumArt
    val context = LocalContext.current
    // resize() is a no-op for local file/content URIs (only the YouTube/Google host
    // branches transform anything) — the decode size below is what actually bounds a
    // local thumbnail's resolution, so it's what needs to be density-scaled, not 544.
    val targetPx = thumbnailPx(GridThumbnailHeight)
    val artworkOwner = com.ozyern.exhale.ui.utils.rememberArtworkSourceOwner()
    val imageRequest = remember(thumbnailUrl, targetPx) {
        val resolvedUrl = thumbnailUrl?.resize(targetPx, targetPx)
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            // See ItemThumbnail above: keyed on the resolved URL so this decode
            // size can't collide on disk with a different one requested elsewhere
            // for the same song.
            .diskCacheKey(resolvedUrl)
            .size(CoilSize(targetPx, targetPx))
            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
            .build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sharedArtworkSource(sharedArtworkId, thumbnailUrl, artworkOwner)
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        AsyncImage(
            model = imageRequest,
            onSuccess = com.ozyern.exhale.ui.utils.sharedArtworkImageListener(artworkOwner, thumbnailUrl),
            contentDescription = null,
            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), shape)
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    SymbolIcon(symbol = sf("play.fill"), contentDescription = null, tint = Color.White)
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    SymbolIcon(symbol = sf("play.fill"), contentDescription = null, tint = Color.White)
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                ) {
                    SymbolIcon(symbol = sf("play.fill"), contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}


@Composable
fun BoxScope.OverlayEditButton(
    visible: Boolean,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(alignment)
            .then(if (alignment == Alignment.BottomEnd) Modifier.padding(8.dp) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .padding(0.dp)
                .clickable(onClick = onClick)
        ) {
            SymbolIcon(symbol = sf("pencil"), contentDescription = null, size = 20.dp, tint = Color.White)
        }
    }
}
