/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import android.os.Build
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.HomeHeroCardHeightOverrideKey
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The "star of the day" card that opens Home.
 *
 * The lower band is the artwork itself, blurred and darkened, rather than a flat black
 * scrim — the same frosted treatment [SpotlightCard] uses, so the title sits in the
 * cover's own colour instead of under a grey wash.
 *
 * Still cheap: the blurred band is a second draw of the image Coil already decoded for
 * the sharp one, so there is no extra decode and no backdrop sampling. Home's frame cost
 * is dominated by how many rich rows compose at once, so the hero has to earn its place
 * by replacing several rows of tiles, not by adding to them.
 */
@Composable
fun HomeHeroCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meshEnabled: Boolean = false,
    meshPaused: () -> Boolean = { false },
) {
    // Apple Music's cover when the song matches (strict title + artist), YouTube's otherwise.
    val artState = com.ozyern.exhale.utils.rememberAppleArtwork(
        com.ozyern.exhale.utils.AppleArtQuery.Song(title, subtitle),
        thumbnailUrl,
        1200,
    )
    val thumbnailUrl = (artState as? com.ozyern.exhale.utils.ArtState.Ready)?.url ?: thumbnailUrl
    val (heightOverride) = rememberPreference(HomeHeroCardHeightOverrideKey, 0)
    val cardShape = rememberHomeCardShape()

    // Zoom-in entrance: the artwork starts 12 % larger and invisible, settling to
    // normal scale on Motion.morphEnter (340ms iOS curve, single clock for scale
    // and alpha). One-shot per composition: keying on thumbnailUrl re-snapped the
    // entrance when the Apple artwork resolved over YouTube's (double zoom).
    val (reduceMotion) = rememberPreference(ReduceDynamicBlurKey, false)
    val scale = remember { Animatable(1.12f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
        } else {
            scale.snapTo(1.12f)
            alpha.snapTo(0f)
            coroutineScope {
                launch { scale.animateTo(1f, animationSpec = Motion.morphEnter()) }
                launch { alpha.animateTo(1f, animationSpec = Motion.morphEnter()) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (heightOverride > 0) Modifier.height(heightOverride.dp) else Modifier.aspectRatio(4f / 3f)
            )
            .clip(cardShape)
            // Under the artwork, not decoration: a song whose thumbnailUrl is null or
            // fails to load left the card fully transparent, so the section read as
            // "the hero card never rendered" rather than as a card with no cover.
            .background(AppTheme.colorScheme.surfaceContainerHigh)
            .bounceClick(onClick = onClick)
            .homeCardRim(cardShape),
    ) {
        // Bound the decode to the card's real on-screen pixels and skip the crossfade:
        // the hero is a full-bleed artwork card, so a full-size decode + fade costs GPU
        // time for zero visual gain on every scroll frame where the card re-enters view.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val density = LocalDensity.current
            // LocalContext/LocalDensity are composable reads, so they can't live inside
            // remember's calculation lambda — read them here, capture into the key set.
            val request = remember(thumbnailUrl, maxWidth, maxHeight, context, density) {
                with(density) {
                    ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .size(maxWidth.roundToPx(), maxHeight.roundToPx())
                        .crossfade(false)
                        .build()
                }
            }
            // Read out here: inside the frost Box below, BoxScope shadows this
            // BoxWithConstraintsScope, so `maxWidth` is no longer resolvable there.
            val frostBlurDp = HeroFrostBlurRadius.value *
                (LocalConfiguration.current.screenWidthDp / maxWidth.value)
            AsyncImage(
                model = request,
                contentDescription = "$title — $subtitle",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
            )

            // Bake the static frost effect once for this card-sized artwork.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val frostAspect = maxWidth / maxHeight
                BlurredBackdropImage(
                    model = thumbnailUrl,
                    screenBlurRadiusDp = frostBlurDp,
                    contentScale = ContentScale.FillBounds,
                    finish = {
                        bakeRamp(
                            it,
                            frostAspect,
                            flip = false,
                            matrix = null,
                            rampStops = floatArrayOf(0f, 0.3f, 0.55f, 0.75f, 1f),
                            rampAlphas = floatArrayOf(0f, 0f, 0.65f, 1f, 1f),
                        )
                    },
                    finishKey = "hero-frost-${"%.3f".format(frostAspect)}",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedChromeMesh(
            enabled = meshEnabled,
            paused = meshPaused,
            fadeAtBottom = false,
            alpha = 0.25f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.48f),
        )

        // Darkening pass, kept separate from the frost so the text keeps its contrast on
        // a bright cover, and so pre-API-31 (where Modifier.blur no-ops) still gets a
        // legible title from the gradient alone.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(AppleTokens.Gutter),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap),
                modifier = Modifier.padding(top = AppleTokens.ItemGap),
            ) {
                HeroPill(
                    iconRes = R.drawable.play,
                    label = stringResource(R.string.play),
                    onClick = onPlay,
                )
                HeroPill(
                    iconRes = R.drawable.shuffle,
                    label = stringResource(R.string.shuffle),
                    onClick = onShuffle,
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = AppTheme.typography.labelLarge,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.43).sp,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Radius of the hero's frosted lower band. Matches [SpotlightCard]'s reflection. */
private val HeroFrostBlurRadius = 24.dp
