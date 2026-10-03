/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.ozyern.exhale.R
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The release page, piece by piece: the artwork drawn behind the list rather than in it,
 * running edge to edge under the status bar; the page under it tinted from the sleeve; one wide
 * band of blur laid across the join so picture and page become one surface; and over that, the
 * title, the credit in the sleeve's accent, a quiet line of facts, a row of circles with a white
 * Play in the middle, and the numbered tracks.
 */

/** Artwork height for a full-width sleeve: a touch taller than square. */
const val ReleaseSleeveRatio = 0.92f

/** How far below the artwork the header's text block reaches. */
val ReleaseHeaderDrop = 44.dp

private val ReleaseGutter = 24.dp
private val MergeBandHeight = 320.dp

/** Where the top of the artwork is: following item zero while it is on screen, parked far above once it isn't. */
fun LazyListState.releaseHeaderTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

/**
 * Everything on the page that is colour rather than words: the wash, and the artwork on top of it,
 * offset to follow the list. [overlay] draws over the still (an animated cover, say), under the
 * gradients that settle the picture onto the page.
 */
@Composable
fun ReleaseBackground(
    artworkUrl: String?,
    palette: ArtworkPalette,
    artHeight: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onArtworkLongPress: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clipToBounds().background(palette.background)) {
        ArtworkWash(palette = palette, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset { IntOffset(0, listState.releaseHeaderTop(artHeight.toPx()).roundToInt()) },
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.elevated),
            )
            overlay()
            // A light darkening under the status bar and the back button, whatever the sleeve does there.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.18f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to palette.wash.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }
    }
}

/**
 * One pane of blur across the join, centred on the artwork's bottom edge so half of it is over the
 * picture and half over the page — the colours of each are carried into the other and the line
 * between them has nothing left to be. Its own edges fade in and out over its whole height.
 * Drawn between the background and the list, so the text over it stays sharp.
 */
@Composable
fun ReleaseMergeBand(
    artworkUrl: String?,
    palette: ArtworkPalette,
    artHeight: Dp,
    listState: LazyListState,
    enabled: Boolean = true,
) {
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    val painter = rememberAsyncImagePainter(artworkUrl)
    Box(
        Modifier
            .fillMaxWidth()
            .height(MergeBandHeight)
            .offset {
                IntOffset(
                    0,
                    (listState.releaseHeaderTop(artHeight.toPx()) + artHeight.toPx() - MergeBandHeight.toPx() / 2f)
                        .roundToInt(),
                )
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .blur(100.dp, BlurredEdgeTreatment.Rectangle)
                .drawWithContent {
                    drawRect(palette.wash)
                    // The same picture the background shows, at the same place: cropped to fill the
                    // artwork's box, with the box's top half a band above this one's top.
                    val artH = artHeight.toPx()
                    val intrinsic = painter.intrinsicSize
                    if (intrinsic.width > 0f && intrinsic.height > 0f) {
                        val scale = max(size.width / intrinsic.width, artH / intrinsic.height)
                        val drawW = intrinsic.width * scale
                        val drawH = intrinsic.height * scale
                        val top = -(artH - size.height / 2f) + (artH - drawH) / 2f
                        translate(left = (size.width - drawW) / 2f, top = top) {
                            with(painter) { draw(Size(drawW, drawH)) }
                        }
                    }
                    translate(top = -(artH - size.height / 2f)) {
                        drawRect(
                            Brush.verticalGradient(
                                0.55f * artH to Color.Transparent,
                                artH to palette.wash.copy(alpha = 0.88f),
                                startY = 0f,
                                endY = artH,
                            ),
                            size = Size(size.width, artH),
                        )
                    }
                },
        )
    }
}

/** A circle on the release header: the sleeve's elevated tint behind a hairline rim. */
@Composable
fun ReleaseCircle(
    icon: Int,
    contentDescription: String?,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    size: Dp = 50.dp,
    tint: Color = palette.onBackground,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.72f))
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(painterResource(icon), contentDescription, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
    }
}

/** Play: white with a black glyph, the one control that survives every sleeve. */
@Composable
fun ReleasePlay(onClick: () -> Unit, contentDescription: String?, size: Dp = 50.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.play),
            contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * The text and the buttons over the foot of the artwork. The artwork itself is the background's;
 * this holds a spacer of its height so the two stay in step.
 */
@Composable
fun ReleaseHeader(
    title: String,
    credit: String?,
    meta: String,
    palette: ArtworkPalette,
    artHeight: Dp,
    onCreditClick: (() -> Unit)?,
    badges: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + ReleaseHeaderDrop))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ReleaseGutter),
            )
            if (!credit.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = credit,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = ReleaseGutter)
                        .clip(RoundedCornerShape(6.dp))
                        .let { if (onCreditClick != null) it.clickable(onClick = onCreditClick) else it },
                )
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = ReleaseGutter),
                )
            }
            badges()
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReleaseGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** The release's editorial note, three lines with a "More" in the accent when it runs longer. */
@Composable
fun ReleaseAbout(title: String, text: String, palette: ArtworkPalette) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.onBackground,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 6.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 22.dp)
                .let { if (clipped || expanded) it.clickable { expanded = !expanded } else it },
        )
        if (clipped || expanded) {
            Text(
                text = if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

/** A section title on a release page, with an optional "Show all" in the accent. */
@Composable
fun ReleaseSectionHeading(title: String, palette: ArtworkPalette, onShowAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = palette.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onShowAll != null) {
            Text(
                text = "Show all",
                style = MaterialTheme.typography.titleSmall,
                color = palette.accent,
                modifier = Modifier.clickable(onClick = onShowAll).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * One numbered track: the number (or a playing mark) where artwork would be, the title with its
 * explicit mark, the artist under it, the running time and a "more" at the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReleaseTrackRow(
    number: Int,
    title: String,
    subtitle: String,
    duration: String?,
    explicit: Boolean,
    palette: ArtworkPalette,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor by animateColorAsState(
        if (isCurrent) palette.accent else palette.onBackground,
        label = "releaseRowTitle",
    )
    val rowTint by animateColorAsState(
        if (isCurrent || selected) palette.accent.copy(alpha = 0.14f) else Color.Transparent,
        label = "releaseRowTint",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowTint)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.graphic_eq else R.drawable.play),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (explicit) {
                    Icon(
                        painterResource(R.drawable.explicit),
                        contentDescription = null,
                        tint = palette.onBackgroundVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (duration != null) {
            Spacer(Modifier.width(8.dp))
            Text(duration, style = MaterialTheme.typography.labelMedium, color = palette.onBackgroundVariant)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.more_vert),
                contentDescription = null,
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The inset hairline between tracks, starting under the title rather than the number. */
@Composable
fun ReleaseRowDivider(palette: ArtworkPalette) {
    Box(
        Modifier
            .padding(start = 78.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(palette.divider),
    )
}

/** The filter box under the header while the search circle is lit. Live: the list is already here. */
@Composable
fun ReleaseSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    palette: ArtworkPalette,
    placeholder: String,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReleaseGutter)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(shape)
            .background(palette.elevated.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.search),
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.close),
                contentDescription = null,
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
