/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.GridItemSize
import com.ozyern.exhale.constants.GridThumbnailHeight
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.constants.ListRowArtShape
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.constants.SmallGridThumbnailHeight
import com.ozyern.exhale.constants.ThumbnailCornerRadius
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.ui.utils.marqueeWhenVisible
import com.ozyern.exhale.ui.utils.rememberGridSpacing
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import com.ozyern.exhale.ui.utils.ArtworkSourceHost
import com.ozyern.exhale.ui.utils.sharedTitleSource
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val ActiveBoxAlpha = 0.6f

/**
 * Strength of secondary content drawn through [additiveChrome]: below full so
 * the blend doesn't clip to plain white — the same 0.45 the player uses for
 * its own chrome, so rows match the player.
 */
const val SecondaryChromeAlpha = 0.45f

/**
 * Song titles only get a hint of the same treatment: near-full alpha keeps
 * them the brightest line while still picking up the backdrop's glow.
 */
const val TitleChromeAlpha = 0.85f

/**
 * Extra dim on the subtitle text when chromed: player v2's own artist-line
 * logic (white 0.35, then × 0.7) so row subtitles land at the same weight as
 * the player's artist read. Badges keep the undimmed tier.
 */
internal const val ChromedSubtitleDim = 0.7f

@Composable
fun currentGridThumbnailHeight(): Dp {
    val prefs = LocalItemPrefs.current
    if (prefs.gridCardHeightOverrideDp > 0) return prefs.gridCardHeightOverrideDp.dp
    return if (prefs.gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
}

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    crossinline thumbnailContent: @Composable () -> Unit,
    crossinline trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    flat: Boolean = false,
    /**
     * Draws the subtitle tier and the flat divider through additiveChrome
     * (white content added onto the backdrop, player-style) instead of plain
     * alpha white — for artwork-tinted screens. Trailing chrome stays with the
     * caller's trailingContent. The title only gets a hint ([TitleChromeAlpha]).
     * Costs 2 offscreen saveLayer passes per row (see [Modifier.additiveChrome]) —
     * opt in per screen, don't default it on for plain scrolling lists.
     */
    secondaryAdditiveChrome: Boolean = false,
    /** Lets metadata keep contrast-safe ink while dividers retain additive chrome. */
    subtitleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    /** False keeps the title plain white while the subtitle tier is chromed. */
    titleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    /** Row height; callers can adjust it for denser or larger lists. */
    rowHeight: Dp = ListItemHeight,
    gutter: Dp = AppleTokens.Gutter,
    divider: Boolean = true,
    /**
     * Full-width rule above the row (same guttered inset as the trailing one):
     * for the first row of a section, so the section opens with a rule and the
     * rows that follow divide with [trailingDividerInset].
     */
    leadingDivider: Boolean = false,
    /**
     * Start inset of the trailing rule. Null keeps the full guttered width;
     * pass the thumbnail leading edge so inter-row rules stop just before the
     * thumbnails while the section's outer rules stay full.
     */
    trailingDividerInset: Dp? = null,
    /**
     * Gap between the row gutter and the thumbnail. Lets a screen sit its
     * thumbnails flush on the page gutter (0.dp, aligned with section titles)
     * instead of the default nested step.
     */
    thumbnailStartInset: Dp = 12.dp,
    /**
     * Title weight and letter spacing for row styles that need stronger emphasis.
     */
    titleFontWeight: FontWeight = FontWeight.Medium,
    titleLetterSpacing: TextUnit = TextUnit.Unspecified,
    /**
     * Title size for system-style rows; defaults to the standard song-row size.
     */
    titleFontSize: TextUnit = AppleTokens.ItemTitle,
    titleLineHeight: TextUnit = AppleTokens.ItemTitleLineHeight,
) {
    val onSurface = LocalContentColor.current
    // Draw modifier only: no layout node, so row measurement is unchanged.
    // Alpha after additiveChrome, never before (see additiveChrome's own KDoc).
    val secondaryChrome =
        if (secondaryAdditiveChrome) Modifier.additiveChrome().graphicsLayer { alpha = SecondaryChromeAlpha }
        else Modifier
    val subtitleChrome =
        if (subtitleAdditiveChrome) Modifier.additiveChrome().graphicsLayer { alpha = SecondaryChromeAlpha }
        else Modifier
    val titleChrome =
        if (titleAdditiveChrome) Modifier.additiveChrome().graphicsLayer { alpha = TitleChromeAlpha }
        else Modifier
    // Top-songs shell when flat: thumbs flush on the gutter (text-leading dividers,
    // full-bleed highlight). Non-flat rows (menus, cards) keep their nested step + pill.
    val effectiveThumbStart = if (flat) 0.dp else thumbnailStartInset
    val highlight = when {
        isActive -> onSurface.copy(alpha = 0.15f)
        isSelected == true && drawHighlight -> onSurface.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    // Skip the background draw for inactive, unselected rows.
    val hasHighlight = highlight != Color.Transparent
    ArtworkSourceHost(modifier) { sourceModifier ->
    Column(sourceModifier) {
        if (flat && leadingDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppleTokens.Gutter).then(secondaryChrome),
                color = AppleTokens.divider,
                thickness = 0.75.dp,
            )
        }
        // Full-bleed highlight when flat: bg on the outer full-width box, content
        // keeps the gutter. Non-flat keeps the inset pill with its shape.
        androidx.compose.foundation.layout.Box(
            modifier = if (flat && hasHighlight) Modifier.background(highlight) else Modifier,
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(rowHeight)
                .padding(horizontal = gutter)
                .then(
                    when {
                        flat -> Modifier
                        hasHighlight -> Modifier.clip(shape).background(highlight)
                        else -> Modifier.clip(shape)
                    }
                )
        ) {
            Box(
                modifier = Modifier.padding(
                    start = effectiveThumbStart,
                    top = 5.dp,
                    // Keep a 10 dp visual gap: 4 dp after the art and 6 dp before the text.
                    end = 4.dp,
                    bottom = 5.dp
                ),
                contentAlignment = Alignment.Center
            ) {
                thumbnailContent()
                if (!isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .align(Alignment.Center)
                            .background(
                                Color.Black.copy(alpha = 0.25f),
                                ListRowArtShape
                            )
                    ) {
                        SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null, modifier = Modifier
                                .size(ListThumbnailSize / 2)
                                .align(Alignment.Center)
                                .graphicsLayer { alpha = 1f }, tint = Color.White)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    color = onSurface,
                    fontWeight = titleFontWeight,
                    letterSpacing = titleLetterSpacing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleChrome.sharedTitleSource(title),
                )

                if (subtitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = subtitleChrome,
                    ) {
                        // Metadata is a fixed grey, not an alpha of the title colour:
                        // over artwork-tinted screens an alpha step reads as a faded
                        // version of the tint rather than as a second tier.
                        CompositionLocalProvider(LocalContentColor provides AppleTokens.Metadata) {
                            subtitle()
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalContentColor provides onSurface.copy(alpha = 0.7f)) {
                trailingContent()
            }
        }
        }
        if (flat && divider) {
            // Internal divider starts under the artist-name text (thumbnail + gaps),
            // ends at the same gutter; null stays the full outer rule. Top/bottom
            // outer rules keep their full width.
            val textLeading = AppleTokens.Gutter + effectiveThumbStart + ListThumbnailSize + 12.dp
            HorizontalDivider(
                modifier = Modifier
                    .padding(
                        start = if (trailingDividerInset == null) AppleTokens.Gutter else textLeading,
                        end = AppleTokens.Gutter,
                    )
                    .then(secondaryChrome),
                color = AppleTokens.divider,
                thickness = 0.75.dp,
            )
        }
    }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: AnnotatedString?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    flat: Boolean = false,
    /** See the base overload: subtitle tier + flat divider via additiveChrome. */
    secondaryAdditiveChrome: Boolean = false,
    subtitleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    /** False keeps the title plain white while the subtitle is chromed. */
    titleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    /** Row height; callers can adjust it for denser or larger lists. */
    rowHeight: Dp = ListItemHeight,
    gutter: Dp = AppleTokens.Gutter,
    divider: Boolean = true,
    /** See the base overload: full rule above the first row of a section. */
    leadingDivider: Boolean = false,
    /** See the base overload: start inset of the trailing rule. */
    trailingDividerInset: Dp? = null,
    /** See the base overload: gap between the row gutter and the thumbnail. */
    thumbnailStartInset: Dp = 12.dp,
    /** See the base overload. */
    titleFontWeight: FontWeight = FontWeight.Medium,
    /** See the base overload. */
    titleLetterSpacing: TextUnit = TextUnit.Unspecified,
    /** See the base overload. */
    titleFontSize: TextUnit = AppleTokens.ItemTitle,
    /** See the base overload. */
    titleLineHeight: TextUnit = AppleTokens.ItemTitleLineHeight,
    /** Defaults to the compact 13/18 subtitle used by song rows. */
    subtitleFontSize: TextUnit = AppleTokens.ItemSubtitle,
    /** See [subtitleFontSize]. */
    subtitleLineHeight: TextUnit = AppleTokens.ItemSubtitleLineHeight,
) = ListItem(
    title = title,
    subtitle = {
        badges()
        if (subtitle != null) {
            // Colour comes from the 0.6-alpha LocalContentColor the base
            // ListItem already provides around its subtitle slot.
            Text(
                text = subtitle,
                fontSize = subtitleFontSize,
                lineHeight = subtitleLineHeight,
                // Player v2 artist-line logic when chromed; identical otherwise.
                color = LocalContentColor.current.copy(
                    alpha = if (subtitleAdditiveChrome) ChromedSubtitleDim else 1f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    flat = flat,
    secondaryAdditiveChrome = secondaryAdditiveChrome,
    subtitleAdditiveChrome = subtitleAdditiveChrome,
    titleAdditiveChrome = titleAdditiveChrome,
    rowHeight = rowHeight,
    gutter = gutter,
    divider = divider,
    leadingDivider = leadingDivider,
    trailingDividerInset = trailingDividerInset,
    thumbnailStartInset = thumbnailStartInset,
    titleFontWeight = titleFontWeight,
    titleLetterSpacing = titleLetterSpacing,
    titleFontSize = titleFontSize,
    titleLineHeight = titleLineHeight,
)

// Combine badges and subtitle in the base row's subtitle slot.
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    flat: Boolean = false,
    /** See the base overload: subtitle tier + flat divider via additiveChrome. */
    secondaryAdditiveChrome: Boolean = false,
    subtitleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    /** See the base overload: full rule above the first row of a section. */
    leadingDivider: Boolean = false,
    /** See the base overload: start inset of the trailing rule. */
    trailingDividerInset: Dp? = null,
    /** See the base overload: gap between the row gutter and the thumbnail. */
    thumbnailStartInset: Dp = 12.dp,
    /** See the base overload. */
    titleFontWeight: FontWeight = FontWeight.Medium,
    /** See the base overload. */
    titleLetterSpacing: TextUnit = TextUnit.Unspecified,
    /** See the base overload. */
    titleFontSize: TextUnit = AppleTokens.ItemTitle,
    /** See the base overload. */
    titleLineHeight: TextUnit = AppleTokens.ItemTitleLineHeight,
    /** System-row subtitle — see the AnnotatedString overload. */
    subtitleFontSize: TextUnit = AppleTokens.ItemSubtitle,
    /** See [subtitleFontSize]. */
    subtitleLineHeight: TextUnit = AppleTokens.ItemSubtitleLineHeight,
    subtitleColor: Color? = null,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            // Colour inherited — see the AnnotatedString overload above.
            Text(
                text = subtitle,
                fontSize = subtitleFontSize,
                lineHeight = subtitleLineHeight,
                // Player v2 artist-line logic when chromed; identical otherwise.
                color = subtitleColor ?: LocalContentColor.current.copy(
                    alpha = if (subtitleAdditiveChrome) ChromedSubtitleDim else 1f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    flat = flat,
    secondaryAdditiveChrome = secondaryAdditiveChrome,
    subtitleAdditiveChrome = subtitleAdditiveChrome,
    leadingDivider = leadingDivider,
    trailingDividerInset = trailingDividerInset,
    thumbnailStartInset = thumbnailStartInset,
    titleFontWeight = titleFontWeight,
    titleLetterSpacing = titleLetterSpacing,
    titleFontSize = titleFontSize,
    titleLineHeight = titleLineHeight,
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    // Artists are round and centred; everything else is square and left-aligned.
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val gridHeight = currentGridThumbnailHeight()
    val gridSpacing = rememberGridSpacing()

    val sizeModifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.width(gridHeight * thumbnailRatio)

    ArtworkSourceHost(modifier) { sourceModifier ->
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = sourceModifier
            .then(sizeModifier)
            // The tile owns half the gap; the grid's contentPadding owns the
            // other half at the screen edge. Together: AppleTokens.Gutter at the
            // edge, gridSpacing between neighbours.
            .padding(gridSpacing / 2)
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }
                .aspectRatio(thumbnailRatio)
                .clip(GridCardArtShape)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Keep artist cards stacked; a title-row badge would break their centering.
        if (horizontalAlignment == Alignment.CenterHorizontally) {
            title()

            Row(verticalAlignment = Alignment.CenterVertically) {
                badges()

                subtitle()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    title()
                }
                badges()
            }

            subtitle()
        }
    }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            fontSize = AppleTokens.ItemTitle,
            lineHeight = AppleTokens.ItemTitleLineHeight,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.23).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) {
                TextAlign.Center
            } else {
                TextAlign.Start
            },
            modifier = Modifier.sharedTitleSource(title).fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = AppleTokens.Metadata,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    horizontalAlignment = horizontalAlignment,
)

/**
 * Queue details use a 13px name and 15px separator and duration on one baseline.
 * The adaptive metadata color keeps them readable across themes.
 */
@Composable
internal fun  queueDetailsLine(
    name: String,
    duration: String,
    metadataColor: Color = AppleTokens.Metadata,
): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = metadataColor)) { append(name) }
        withStyle(SpanStyle(color = metadataColor, fontSize = 15.sp, fontWeight = FontWeight.Thin)) {
            append(" • ")
        }
        withStyle(SpanStyle(color = metadataColor, fontSize = 15.sp)) {
            append(duration)
        }
    }
