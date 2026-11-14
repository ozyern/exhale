/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ozyern.exhale.ui.ios.component.IosMenuItem
import com.ozyern.exhale.ui.ios.component.IosMenuSection
import com.ozyern.exhale.ui.screens.Screens
import com.ozyern.exhale.R

/**
 * The shortcuts behind a press-and-hold on a nav bar tab.
 *
 * iOS's own tab bar opens a context menu on hold with the destinations that tab leads to
 * but the bar has no room for. Only real routes here -- a menu entry that navigates
 * nowhere is worse than no menu, so a destination with nothing to offer returns an empty
 * list and simply has no menu (see FloatingNavBar's tabContextMenu).
 */
@Composable
internal fun navTabContextMenu(
    screen: Screens,
    navController: NavController,
): List<IosMenuSection> {
    @Composable
    fun item(@StringRes titleId: Int, route: String) = IosMenuItem(
        title = stringResource(titleId),
        onClick = { navController.navigate(route) },
    )

    val items = when (screen) {
        Screens.Home -> listOf(
            item(R.string.history, "history"),
            item(R.string.stats, "stats"),
            item(R.string.together, Screens.ListenTogether.route),
        )
        Screens.Library -> listOf(
            item(R.string.history, "history"),
            item(R.string.stats, "stats"),
            item(R.string.account, "account"),
        )
        Screens.Search -> listOf(
            item(R.string.tab_explore, Screens.Explore.route),
            item(R.string.charts, "charts_screen"),
            item(R.string.new_release_albums, "new_release"),
            item(R.string.mood_and_genres, "mood_and_genres"),
        )
        else -> emptyList()
    }
    return if (items.isEmpty()) emptyList() else listOf(IosMenuSection(items))
}
