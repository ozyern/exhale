/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.artist

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.component.ArtistBlendStart
import com.ozyern.exhale.ui.component.ArtistHeroImage
import com.ozyern.exhale.ui.component.ContrastCircleButton
import com.ozyern.exhale.ui.component.ListItem
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.LocalAccentTextColor
import com.ozyern.exhale.ui.theme.LocalMetadataColor
import com.ozyern.exhale.ui.utils.additiveChrome

/**
 * Static preview of the artist header treatment — reference-style hero with the
 * progressive blur foot, the play trio starting on the fade, and song rows at
 * the 20.dp gutter with additive-chrome secondary text — without running the
 * app. Artwork is a stand-in wash (no photo in previews); layout, spacing and
 * chrome are the real components.
 */
@Preview(
    name = "Artist — hero + songs",
    showBackground = true,
    backgroundColor = 0xFF121212,
    widthDp = 400,
    heightDp = 1400,
)
@Composable
private fun ArtistScreenPreview() {
    val tint = Color(0xFF4A2E4D)
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalMetadataColor provides Color.White.copy(alpha = 0.35f),
        LocalAccentTextColor provides Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tint)
                .verticalScroll(rememberScrollState()),
        ) {
            ArtistHeroImage(
                artworkUrl = null,
                pageColor = tint,
            )
            // Blend-start mirror: the real header starts at foot × ArtistBlendStart,
            // so this hangs (1 - ArtistBlendStart) of the hero height past the foot.
            // Preview hero is 400.dp wide → foot at 400 / 0.95.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = -(400.dp / 0.95f * (1f - ArtistBlendStart)))
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Artist Name",
                    style = AppTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 44.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContrastCircleButton(
                        onTint = Color.White,
                        onClick = {},
                        size = 48.dp,
                        modifier = Modifier.offset(y = 8.dp),
                    ) {
                        SymbolIcon(symbol = sf("info.circle.fill"), contentDescription = null, size = 20.dp)
                    }
                    ContrastCircleButton(
                        onTint = Color.White,
                        filled = true,
                        onClick = {},
                        size = 72.dp,
                    ) {
                        SymbolIcon(symbol = sf("play.fill"), contentDescription = null, size = 32.dp, tint = Color.Black)
                    }
                    ContrastCircleButton(
                        onTint = Color.White,
                        onClick = {},
                        size = 48.dp,
                        modifier = Modifier.offset(y = 8.dp),
                    ) {
                        SymbolIcon(symbol = sf("heart.fill"), contentDescription = null, size = 20.dp)
                    }
                }
                // Flat faded stat pill, like the real header's.
                Text(
                    text = "1.2M subscribers",
                    style = AppTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .additiveChrome()
                        .graphicsLayer { alpha = 0.45f },
                )
            }
            // Mesh wash behind the rows, as on the real screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xFF121212),
                        ),
                    ),
            ) {
                Column {
                    NavigationTitle(
                        title = "Top Songs",
                        modifier = Modifier
                            .additiveChrome()
                            .graphicsLayer { alpha = 0.85f },
                        onClick = {},
                    )
                    repeat(3) { i ->
                        ListItem(
                            title = "Song title ${i + 1}",
                            subtitle = "Artist Name • 3:4${i + 1}",
                            badges = {},
                            thumbnailContent = {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                )
                            },
                            trailingContent = {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SymbolIcon(symbol = sf("ellipsis"), contentDescription = null, size = 22.dp, modifier = Modifier
                                            
                                            .additiveChrome()
                                            .graphicsLayer { alpha = 0.45f }, tint = LocalContentColor.current)
                                }
                            },
                            flat = true,
                            secondaryAdditiveChrome = true,
                            thumbnailStartInset = 0.dp,
                            leadingDivider = i == 0,
                            trailingDividerInset = if (i == 2) null else 28.dp,
                        )
                    }
                }
            }
        }
    }
}
