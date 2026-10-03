/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * The header every collection page shares — album, playlist, Liked, Top —
 * detail pages: the artwork edge to edge from the top of the screen, darkened at the top for the
 * chrome and dissolving into the page at the foot, with the title, the credit and one quiet line of
 * facts set on that faded foot, and the round actions underneath.
 *
 * [artwork] fills the square; [PlaylistArtwork] and [GradientArtwork] are the usual choices.
 */
@Composable
fun CollectionHero(
    title: String,
    meta: String,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    credit: String? = null,
    palette: ArtworkPalette? = null,
    artwork: @Composable BoxScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    if (palette != null) {
        androidx.compose.runtime.CompositionLocalProvider(LocalReleasePalette provides palette) {
            ReleaseCollectionHero(title, meta, palette, credit, artwork, actions, modifier)
        }
        return
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .heroParallax(lazyListState, travel = 260.dp),
        ) {
            artwork()
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.38f),
                            0.2f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.78f to MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            1f to MaterialTheme.colorScheme.surface,
                        ),
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                if (!credit.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = credit,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = meta.uppercase(),
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            content = actions,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * [CollectionHero] on a page tinted from its artwork: the cover a touch
 * taller than square, its foot melted into the page through a blurred copy of itself, the title in
 * the page's ink, the credit in the sleeve's accent, a small spaced line of facts, and the circles.
 */
@Composable
private fun ReleaseCollectionHero(
    title: String,
    meta: String,
    palette: ArtworkPalette,
    credit: String?,
    artwork: @Composable BoxScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ReleaseSleeveRatio),
        ) {
            artwork()
            // The join: a heavily blurred copy of the cover, shown only across its lower part, so
            // the picture dissolves into its own colours before it reaches the page.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0.45f to Color.Transparent,
                                    0.8f to Color.Black,
                                    1f to Color.Black,
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                ) {
                    Box(Modifier.matchParentSize().blur(60.dp, BlurredEdgeTreatment.Rectangle)) { artwork() }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.18f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.82f to palette.background.copy(alpha = 0.70f),
                            1f to palette.background,
                        ),
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
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
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = meta.replace("  \u00b7  ", " \u2022 ").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            content = actions,
        )
        Spacer(Modifier.height(18.dp))
    }
}

/** A round glass action beside Play. [content] replaces the icon, for a spinner or the like. */
@Composable
fun CollectionAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    content: (@Composable () -> Unit)? = null,
) {
    val palette = LocalReleasePalette.current
    if (palette != null) {
        ReleaseCircle(
            icon = icon,
            contentDescription = contentDescription,
            palette = palette,
            onClick = onClick,
            tint = if (tint == MaterialTheme.colorScheme.onSurface) palette.onBackground else tint,
            content = content,
        )
        return
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(50.dp).lightGlass(CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                Icon(painterResource(icon), contentDescription, tint = tint, modifier = Modifier.size(23.dp))
            }
        }
    }
}

/** The one filled control on the page: Play, in the page's ink. */
@Composable
fun CollectionPlay(onClick: () -> Unit, contentDescription: String?) {
    if (LocalReleasePalette.current != null) {
        ReleasePlay(onClick = onClick, contentDescription = contentDescription)
        return
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(58.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(com.ozyern.exhale.R.drawable.play),
                contentDescription,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** A playlist's cover: its one picture, or its first four as a grid, or a glyph on the page. */
@Composable
fun BoxScope.PlaylistArtwork(thumbnails: List<String>, placeholderIcon: Int) {
    when {
        thumbnails.size >= 4 -> Column(Modifier.matchParentSize()) {
            listOf(0, 2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    listOf(row, row + 1).forEach { index ->
                        AsyncImage(
                            model = thumbnails[index],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
        thumbnails.isNotEmpty() -> AsyncImage(
            model = thumbnails.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        else -> GradientArtwork(
            colors = listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainer),
            icon = placeholderIcon,
        )
    }
}

/** The cover of a collection the app keeps — Liked, Downloaded, Top — in its own colours. */
@Composable
fun BoxScope.GradientArtwork(colors: List<Color>, icon: Int, iconSize: Dp = 96.dp) {
    Box(
        Modifier
            .matchParentSize()
            .background(Brush.linearGradient(colors))
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 1400f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(iconSize))
    }
}
