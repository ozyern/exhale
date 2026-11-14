/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.ozyern.exhale.R

/**
 * A bottom-bar / side-bar destination.
 *
 * One icon per destination per UI style — deliberately not a selected/unselected
 * pair. iOS ships outline/filled pairs because its tab bar has no selection
 * indicator; this bar has both a glass puck and an accent content colour, so a
 * filled variant would be a third signal saying the same thing. Two artworks per
 * tab also have to be optically aligned against each other or the icon visibly
 * shifts on selection, which is exactly the cheap-looking wobble the pair would
 * have bought us.
 */
@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    val route: String,
    @DrawableRes val icon: Int,
    // SF Symbols-style lookalike used when the Apple Music UI style is active.
    // Defaults to the classic icon, so a destination only names this when it
    // actually has a distinct iOS drawing.
    @DrawableRes val iosIcon: Int = icon,
) {
    /** The icon to draw for the current UI style. */
    fun icon(appleMusicUi: Boolean): Int = if (appleMusicUi) iosIcon else icon

    object Home : Screens(
        titleId = R.string.home,
        route = "home",
        icon = R.drawable.accord_home,
    )

    object Search : Screens(
        titleId = R.string.search,
        route = "search_input",
        icon = R.drawable.search,
        iosIcon = R.drawable.cosmos_search,
    )

    object Radio : Screens(
        titleId = R.string.radio,
        route = "radio",
        icon = R.drawable.radio,
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        route = "listen_together",
        icon = R.drawable.accord_groups,
    )

    /**
     * Flat, alphabetised list of every song on the device. Only ever a tab in local-only
     * mode -- with YouTube on, "all songs" has no bounded meaning.
     */
    object Songs : Screens(
        titleId = R.string.songs,
        route = "songs",
        icon = R.drawable.music_note,
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        route = "library",
        icon = R.drawable.accord_library,
    )

    /**
     * Charts, new releases and moods/genres — the discovery tab. Replaces
     * [Settings] in the floating bar; settings stays behind the top-bar pill.
     */
    object Explore : Screens(
        titleId = R.string.tab_explore,
        route = "explore",
        icon = R.drawable.sparks,
    )

    object Settings : Screens(
        titleId = R.string.settings,
        route = "settings",
        icon = R.drawable.settings,
    )

    companion object {
        // Keep Listen Together out of the tab pager to avoid showing it during tab switches.
        // Lazy initialization avoids reading singleton routes before their objects initialize.
        val MainScreens: List<Screens> by lazy { listOf(Home, Search, Radio, Library) }

        /**
         * Local-only mode promotes [Songs] and omits online destinations.
         */
        fun mainScreens(localOnly: Boolean): List<Screens> =
            if (localOnly) listOf(Home, Songs, Search, Library) else MainScreens

        /** Every route that counts as a tab root, in either mode. */
        val MainRoutes: Set<String> by lazy { (MainScreens + Songs).map { it.route }.toSet() }
    }
}
