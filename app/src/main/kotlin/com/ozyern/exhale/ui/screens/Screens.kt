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
        // the reference design's Home, one solid glyph in both states, as their dock draws it: the capsule
        // behind it, not an outline/fill swap, says which tab is open.
        // The reference 2.0.4 dock's house.fill.
        iconIdInactive = R.drawable.sf_house_fill,
        iconIdActive = R.drawable.sf_house_fill,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.sf_magnifyingglass,
        iconIdActive = R.drawable.sf_magnifyingglass,
        route = "search"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.sf_music_note_square_stack_fill,
        iconIdActive = R.drawable.sf_music_note_square_stack_fill,
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
        // Convx 2.0.4's Explore glyph, square.grid.2x2.fill: one solid glyph in both states.
        iconIdInactive = R.drawable.sf_square_grid_2x2_fill,
        iconIdActive = R.drawable.sf_square_grid_2x2_fill,
        route = "mood_and_genres"
    )

    companion object {
        val MainScreens = listOf(Home, Search, MoodAndGenres, Library)
    }
}
