/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.theme.rememberArtworkTint
import com.ozyern.exhale.ui.utils.combinedBounceClick

/** Square artwork edge. The card is this wide; one page of the spotlight row. */
val SpotlightCardArtworkSize = 240.dp

/** Strip below the artwork that the title and subtitle sit in. */
val SpotlightCardInfoHeight = 70.dp

/** Total card height, artwork plus the info strip. */
val SpotlightCardHeight = SpotlightCardArtworkSize + SpotlightCardInfoHeight

/** Gap between cards in the spotlight row. */
val SpotlightCardSpacing = 12.dp

/**
 * Reference art darkens the reflection with two black washes at 0.302; one pass at the
 * composited value (1 - 0.698^2) is the same result with one fewer draw.
 */
private const val ReflectionDarken = 0.51f

/** The reflection is a mirror of the art, so it wants more colour, not less. */
private const val ReflectionSaturation = 1.5f

private val ReflectionBlurRadius = 24.dp

/** Width/height above which artwork counts as wide (a video frame) and is not cropped. */
private const val WideArtRatio = 1.15f

/**
 * Artwork card with a mirrored, blurred reflection behind its title. The image
 * is reused from Coil; below API 31 the reflection is omitted because blur is
 * unavailable and would leave a sharp inverted image.
 */
@Composable
fun SpotlightCard(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artworkSize: Dp = SpotlightCardArtworkSize,
    infoHeight: Dp = SpotlightCardInfoHeight,
    // When 2+ URLs are given, the artwork is a 2x2 collage of them instead of one
    // image — real chart branding (its top songs) rather than a single arbitrary
    // thumbnail standing in for the whole chart. No reflection in that case: there
    // is no single source image left to mirror.
    collageUrls: List<String> = emptyList(),
) {
    val isCollage = collageUrls.size >= 2
    val tints = rememberArtworkTint(collageUrls.firstOrNull() ?: thumbnailUrl)
    // Well short of black: the card colour still has to read as the artwork colour
    // where the reflection fades out over it.
    val cardColor = remember(tints) {
        lerp(tints.firstOrNull() ?: Color.Black, Color.Black, 0.48f)
    }
    val reflectionsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isCollage

    val cardShape = rememberHomeCardShape()
    Box(
        modifier = modifier
            .width(artworkSize)
            .height(artworkSize + infoHeight)
            .clip(cardShape)
            .background(cardColor)
            .combinedBounceClick(onLongClick = onLongClick, onClick = onClick)
            .homeCardRim(cardShape),
    ) {
        if (isCollage) {
            ChartCollageArt(
                urls = collageUrls,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(artworkSize),
            )
        } else {
            // A 16:9 video frame centre-cropped into this square loses over 40% of the image
            // (titles, faces). Wide art is shown whole instead, over a blurred copy of
            // itself so the square still reads as filled. Square album art is unchanged.
            var wideArt by remember(thumbnailUrl) { mutableStateOf(false) }
            if (wideArt) {
                BlurredBackdropImage(
                    model = thumbnailUrl,
                    screenBlurRadiusDp = ReflectionBlurRadius.value *
                        (LocalConfiguration.current.screenWidthDp / artworkSize.value),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(artworkSize),
                )
            }
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = if (wideArt) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { state ->
                    val image = state.result.image
                    wideArt = image.width > image.height * WideArtRatio
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(artworkSize),
            )
        }

        if (reflectionsSupported) {
            SpotlightReflection(
                thumbnailUrl = thumbnailUrl,
                artworkSize = artworkSize,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A 2x2 grid of up to 4 thumbnails filling one square — the "top global" chart's own
 * branding (a glance at what's actually on it) rather than one song's cover standing
 * in for the whole chart. Fewer than 4 URLs still tile evenly across the 2x2 cells,
 * repeating the last one rather than leaving a cell blank.
 */
@Composable
private fun ChartCollageArt(urls: List<String>, modifier: Modifier = Modifier) {
    val cells = remember(urls) { List(4) { i -> urls.getOrNull(i) ?: urls.last() } }
    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            AsyncImage(cells[0], null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
            AsyncImage(cells[1], null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Row(modifier = Modifier.weight(1f)) {
            AsyncImage(cells[2], null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
            AsyncImage(cells[3], null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * The mirrored lower portion, baked once per artwork on the CPU
 * (see BlurredBackdropImage's finish pass): no layer, no per-frame cost.
 */
@Composable
private fun BoxScope.SpotlightReflection(
    thumbnailUrl: String?,
    artworkSize: Dp,
) {
    // BlurredBackdropImage projects its radius assuming the result is stretched to the
    // full screen width; this one is stretched to the card's width instead, so scale the
    // radius up by however much narrower that is or the reflection comes out sharp.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val projectedBlur = ReflectionBlurRadius.value * (screenWidthDp / artworkSize.value)
    // Build the static reflection color treatment once instead of drawing extra layers each frame.
    val reflectionMatrix = remember {
        android.graphics.ColorMatrix().apply {
            setSaturation(ReflectionSaturation)
            postConcat(
                android.graphics.ColorMatrix().apply {
                    setScale(1f - ReflectionDarken, 1f - ReflectionDarken, 1f - ReflectionDarken, 1f)
                },
            )
        }
    }
    BlurredBackdropImage(
        model = thumbnailUrl,
        screenBlurRadiusDp = projectedBlur,
        contentScale = ContentScale.FillBounds,
        finish = {
            bakeRamp(
                it,
                1f,
                flip = true,
                matrix = reflectionMatrix,
                rampStops = floatArrayOf(0f, 1f / 3f, 1f / 3f + 0.3f / 3f, 1f / 3f + 0.75f / 3f, 2f / 3f, 1f),
                rampAlphas = floatArrayOf(0f, 0f, 0.3f, 0.8f, 1f, 1f),
            )
        },
        finishKey = "spot-refl",
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(artworkSize),
    )
}
