/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquidGlassSurface

/** One of the library's own collections: Liked, Downloaded, Cached, Top. */
internal data class LibraryCollection(
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    val colors: List<Color>,
    val onClick: () -> Unit,
)

/** A place in the library: Playlists, Artists, Albums, Songs. */
internal data class LibraryDestination(
    val title: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val onClick: () -> Unit,
)

/**
 * The top of the library: the first collection — Liked, normally — as a wide card of its own
 * colour with the count and a play button, the way a music app puts the thing you open most at
 * the top; the rest beside each other under it as small glass tiles.
 */
@Composable
internal fun LibraryCollectionsHero(
    collections: List<LibraryCollection>,
    gutter: Dp,
    onPlayFirst: (() -> Unit)?,
) {
    if (collections.isEmpty()) return
    val lead = collections.first()
    val rest = collections.drop(1)
    val likedCount by LocalDatabase.current.likedSongsCount().collectAsState(initial = 0)

    // A tablet or an unfolded foldable: the lead card and the rest in one row of cards the same
    // height, rather than a banner across the whole screen over three stretched tiles.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    val stack: @Composable (@Composable () -> Unit, @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) -> Unit =
        { leadCard, tiles ->
            if (wide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                ) {
                    Box(Modifier.weight(2f).fillMaxHeight()) { leadCard() }
                    tiles()
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    leadCard()
                    if (rest.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { tiles() }
                    }
                }
            }
        }

    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        stack({
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(lead.colors))
                .clickable(onClick = lead.onClick),
        ) {
            // The glyph, large and faint, off the card's right edge: the card is the collection.
            Icon(
                painter = painterResource(lead.icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 26.dp, y = 10.dp)
                    .size(150.dp)
                    .graphicsLayer { rotationZ = -12f },
            )
            // A light from the top left, so the card reads as a lit object rather than a fill.
            Box(
                Modifier
                    .matchParentSizeCompat()
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                            radius = 520f,
                            center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp, end = 90.dp),
            ) {
                Text(
                    text = lead.title,
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.02).em),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (lead.icon == R.drawable.favorite && likedCount > 0) {
                        "$likedCount song${if (likedCount == 1) "" else "s"}"
                    } else {
                        lead.subtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
            if (onPlayFirst != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onPlayFirst),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = "Play",
                        tint = lead.colors.last(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        }, {
                rest.forEach { c ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (wide) Modifier.fillMaxHeight() else Modifier)
                            .liquidGlassSurface(RoundedCornerShape(20.dp), tint = c.colors.first())
                            .clickable(onClick = c.onClick)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(c.colors)),
                        ) {
                            Icon(painterResource(c.icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 13.sp,
                            letterSpacing = (-0.2).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = c.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        })
    }
}

/**
 * The places in the library, as Apple Music lists them: a plain list on the page, each row an
 * accent-coloured glyph, the name at Title 3, a grey chevron, and hairlines inset to the text and
 * running to the screen's edge. No card around them, and no coloured tiles behind the glyphs;
 * those are Settings' idiom, not Music's.
 */
@Composable
internal fun LibraryDestinationList(destinations: List<LibraryDestination>, gutter: Dp) {
    // Two grouped cards side by side on a tablet, rather than one list of rows a screen wide.
    if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840 && destinations.size > 1) {
        val half = (destinations.size + 1) / 2
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 22.dp),
        ) {
            listOf(destinations.take(half), destinations.drop(half)).forEach { column ->
                Box(Modifier.weight(1f)) { DestinationGroup(column) }
            }
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter)
            .padding(top = 22.dp),
    ) { DestinationGroup(destinations) }
}

@Composable
private fun DestinationGroup(destinations: List<LibraryDestination>) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        destinations.forEachIndexed { i, d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(onClick = d.onClick)
                    .padding(vertical = 12.dp),
            ) {
                Icon(painterResource(d.icon), null, tint = accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    text = d.title,
                    fontSize = 20.sp,
                    letterSpacing = (-0.01).em,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (i < destinations.lastIndex) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    modifier = Modifier.padding(start = 42.dp),
                )
            }
        }
    }
}

/** `matchParentSize` outside a BoxScope receiver, for the light laid over the hero card. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(132.dp))
