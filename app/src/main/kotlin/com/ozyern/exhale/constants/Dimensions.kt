/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val NavigationBarHeight = 80.dp
val SlimNavBarHeight = 64.dp
val MiniPlayerHeight = 64.dp
// The docked floating accessory (iOS-style row: thumbnail + 2 lines + progress
// bar, or the multi-icon tab-style row) runs taller than the classic mini
// player MiniPlayerHeight was sized for — under-reserving this clips content
// behind it.
val DockedAccessoryHeight = 84.dp
val MinMiniPlayerHeight = 16.dp
val MiniPlayerBottomSpacing = 8.dp // Space between MiniPlayer and NavigationBar
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

/** iOS list row: 48 artwork + 5 above and below. */
val ListItemHeight = 58.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
val SmallGridThumbnailHeight = 128.dp
val GridThumbnailHeight = 164.dp

// Minimum width for adaptive grid columns; keeps phone library grids at three columns.
val GridColumnMinWidth = 108.dp
val AlbumThumbnailSize = 144.dp

// Mirrors AppleTokens.Artwork without making :constants depend on the theme package.
val ThumbnailCornerRadius = 8.dp

// Rounded corners avoid a generated path clip for every list thumbnail.
val ThumbnailRoundedShape = RoundedCornerShape(ThumbnailCornerRadius)

/** Artwork shape for list rows. */
val ListRowArtShape = RoundedCornerShape(5.dp)

/** Artwork shape for grid cards. */
val GridCardArtShape = RoundedCornerShape(10.dp)

val PlayerHorizontalPadding = 32.dp

/** Expanded player's width cap in tab view when "Compact player in tab view" is on —
 *  roughly a large phone's width, so the player reads the same as it does on mobile
 *  instead of stretching across the wide screen. */
val CompactPlayerMaxWidth = 480.dp

val NavigationBarAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val BottomSheetAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val BottomSheetSoftAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
