/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFECircle
import com.composables.sfsymbols.monochrome.SFShuffle
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.EnableTidalStreamingKey
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.utils.readLocalAudioProperties
import com.ozyern.exhale.utils.rememberPreference

/*
 * The Apple-Music album/playlist detail header, in three pieces:
 *
 *   [DetailArtworkCard]  a bounded, shadowed square instead of a full-bleed one
 *   [DetailMetaLine]     "Album · 2026 · ⌇ Lossless"
 *   [DetailActionRow]    shuffle ○ | ▶ Play (white pill) | ○ download
 *
 * Kept here rather than inside AlbumScreen because the album screen and both
 * playlist screens draw the same three things and had three copies of the
 * button row between them.
 */

/**
 * Fraction of screen width occupied by the artwork card. This keeps the action
 * row visibly wider than the artwork, so the card reads as an object on the page.
 */
const val DetailArtworkFraction = 0.66f

/**
 * Height shared by the play pill and the two side buttons. The equal heights make
 * the three controls read as one optical group.
 */
val DetailActionHeight = 52.dp

/**
 * The detail screen's artwork: a centred, rounded, shadowed square.
 *
 * [model] is handed straight to Coil, so a caller with a tuned [coil3.request.ImageRequest]
 * (AlbumScreen keeps one, to land the shared-element morph on a cache hit) can pass it
 * instead of a URL string.
 *
 * [heroScale] is for `rememberHeroZoom()` — applied in a graphics layer so a pull-to-zoom
 * grows the card without re-laying-out the list beneath it.
 */
@Composable
fun DetailArtworkCard(
    model: Any?,
    modifier: Modifier = Modifier,
    heroScale: Float = 1f,
    widthFraction: Float = DetailArtworkFraction,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = ContinuousRoundedRectangle(AppleTokens.CardCorner)
    val hasArtwork = model != null && (model !is String || model.isNotBlank())
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
            }
            .shadow(elevation = 24.dp, shape = shape)
            .clip(shape)
            .background(AppTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (hasArtwork) {
            AsyncImage(
                model = model,
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
        overlay()
    }
}

/**
 * The one-line metadata rule under the title: dot-separated [segments], then the
 * lossless mark when [lossless].
 *
 * [color] should be the screen's own secondary text tier (`heroPalette.secondaryText`),
 * not a fixed grey — this line sits on an artwork-derived background, so a constant
 * `#8E8E93` reads as a foreign colour pasted on it. Same reasoning as
 * [AppleTokens.Metadata]'s composition-local indirection.
 */
@Composable
fun DetailMetaLine(
    segments: List<String>,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    explicit: Boolean = false,
    lossless: Boolean = false,
) {
    val visible = segments.filter { it.isNotBlank() }
    if (visible.isEmpty() && !explicit && !lossless) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        if (explicit) {
            SymbolIcon(symbol = sf("SFECircle"), contentDescription = stringResource(R.string.explicit), size = 14.dp, tint = color)
        }
        if (visible.isNotEmpty()) {
            Text(
                text = visible.joinToString(" · "),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
            )
            if (lossless) {
                Text(text = "·", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
        if (lossless) {
            Icon(
                painter = painterResource(R.drawable.stream_old_player),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(R.string.lossless),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * shuffle | Play | trailing.
 *
 * The play control is a wide pill filled with [onTint] and lettered in [tint] — the
 * inverse of the page, which is what makes it the one focal point. The two circles
 * are [ContrastCircleButton]s, so they are the page's own colour at low alpha rather
 * than a second foreground.
 *
 * [trailing] is a slot (download on the album screen, save on a playlist). Omitting
 * it still reserves the width, so the pill stays centred.
 */
@Composable
fun DetailActionRow(
    onTint: Color,
    tint: Color,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContrastCircleButton(
            onTint = onTint,
            onClick = onShuffle,
            size = DetailActionHeight,
        ) {
            SymbolIcon(symbol = sf("SFShuffle"), contentDescription = stringResource(R.string.shuffle_label), size = 22.dp)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(DetailActionHeight)
                .clip(ContinuousRoundedRectangle(percent = 50))
                .background(onTint)
                .bounceClick(onClick = onPlay),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedPlayPauseIcon(isPlaying = isPlaying, tint = tint, size = 17.dp)
                Text(
                    text = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.43).sp,
                    color = tint,
                    maxLines = 1,
                )
            }
        }

        if (trailing != null) trailing() else Spacer(Modifier.size(DetailActionHeight))
    }
}

/** True when a local file or enabled Tidal streaming can provide lossless audio. */
@Composable
fun rememberLosslessBadge(localSongId: String?): Boolean {
    val tidalLossless by rememberPreference(EnableTidalStreamingKey, defaultValue = false)
    val context = LocalContext.current
    val localIsLossless by produceState(false, localSongId) {
        value = localSongId?.let { readLocalAudioProperties(context, it)?.isLossless } == true
    }
    return tidalLossless || localIsLossless
}
