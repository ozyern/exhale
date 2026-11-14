/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.ozyern.exhale.constants.CropAlbumArtKey
import com.ozyern.exhale.constants.GridCardHeightOverrideKey
import com.ozyern.exhale.constants.GridItemSize
import com.ozyern.exhale.constants.GridItemsSizeKey
import com.ozyern.exhale.constants.SwipeToRemoveSongKey
import com.ozyern.exhale.constants.SwipeToSongKey
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

/** Preferences shared by item rows; read once to avoid blocking DataStore reads per lazy item. */
@Immutable
data class ItemPrefs(
    val cropAlbumArt: Boolean = false,
    val swipeToSong: Boolean = false,
    val swipeToRemoveSong: Boolean = false,
    val gridCardHeightOverrideDp: Int = 0,
    val gridItemSize: GridItemSize = GridItemSize.BIG,
)

val LocalItemPrefs = staticCompositionLocalOf { ItemPrefs() }

/** Reads the item preferences from DataStore. Call once, at the app root. */
@Composable
fun rememberItemPrefs(): ItemPrefs {
    val (cropAlbumArt) = rememberPreference(CropAlbumArtKey, false)
    val (swipeToSong) = rememberPreference(SwipeToSongKey, false)
    val (swipeToRemoveSong) = rememberPreference(SwipeToRemoveSongKey, false)
    val (gridCardHeightOverride) = rememberPreference(GridCardHeightOverrideKey, 0)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    return remember(
        cropAlbumArt, swipeToSong, swipeToRemoveSong, gridCardHeightOverride, gridItemSize,
    ) {
        ItemPrefs(
            cropAlbumArt = cropAlbumArt,
            swipeToSong = swipeToSong,
            swipeToRemoveSong = swipeToRemoveSong,
            gridCardHeightOverrideDp = gridCardHeightOverride,
            gridItemSize = gridItemSize,
        )
    }
}

/** Download state shared by rows; this dynamic local updates the badge when downloads change. */
val LocalDownloads = androidx.compose.runtime.compositionLocalOf<Map<String, androidx.media3.exoplayer.offline.Download>> { emptyMap() }
