/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.ozyern.exhale.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
    /**
     * What the dock calls this tab.
     *
     * Defaults to the page's own name and stays that way unless a name is genuinely unusable at a
     * caption size - "Mood & Genres" is not: it fits once the strip's tabs are measured on an even
     * pitch. Shortening a label to dodge a layout bug hides the bug.
     */
    @StringRes val dockTitleId: Int = titleId,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search_filled,
        route = "search"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_outlined,
        iconIdActive = R.drawable.library_filled,
        route = "library"
    )

    object DownloadQueue : Screens(
        titleId = R.string.download_queue,
        iconIdInactive = R.drawable.downloading,
        iconIdActive = R.drawable.downloading,
        route = "download_queue"
    )

    object MoodAndGenres : Screens(
        titleId = R.string.mood_and_genres,
        // Apple-Music "Radio" tab: a center node with radiating broadcast waves.
        // Thin concentric waves when inactive, bold solid node + waves when active.
        iconIdInactive = R.drawable.radio,
        iconIdActive = R.drawable.radio_filled,
        route = "mood_and_genres"
    )

    companion object {
        val MainScreens = listOf(Home, Search, MoodAndGenres, Library)
    }
}
