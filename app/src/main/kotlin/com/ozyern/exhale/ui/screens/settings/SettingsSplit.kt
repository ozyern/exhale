/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ozyern.exhale.ui.screens.navigationBuilder

/** Windows at least this wide get the split; narrower ones the phone's single list. */
const val SettingsSplitMinWidthDp = 840

/** The sidebar's width: ColorOS's tablet Settings list, a phone's width of rows. */
private val SidebarWidth = 380.dp

/** The page the right-hand pane opens on, before a row has been chosen. */
private const val SplitStartRoute = "settings/appearance"

/**
 * Settings on a tablet or an unfolded foldable, as ColorOS lays it out there: the list as a
 * sidebar on the left, and the chosen page on the right, both on screen at once.
 *
 * The right-hand pane is a navigation host of its own, built from the app's whole graph, so every
 * settings page works in it unchanged — its own back button steps back within the pane, and a page
 * that opens somewhere outside Settings still has somewhere to go. Choosing a row in the sidebar
 * resets that pane to its first page before opening the new one, so the pane's back stack is the
 * depth of what you opened from that row, never the history of every row you tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSplit(
    navController: NavHostController,
    latestVersionName: String,
) {
    val detail = rememberNavController()
    // One bar state per pane: shared, scrolling the list collapsed the page's title beside it.
    val sidebarScroll = com.ozyern.exhale.ui.utils.appBarScrollBehavior()
    val detailScroll = com.ozyern.exhale.ui.utils.appBarScrollBehavior()
    val sidebar = remember(detail, navController) {
        SettingsSidebar(
            reset = { detail.popBackStack(SplitStartRoute, inclusive = false) },
            exit = { navController.navigateUp() },
            initialSelection = "Appearance",
        )
    }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(SidebarWidth)
                .fillMaxHeight(),
        ) {
            CompositionLocalProvider(LocalSettingsSidebar provides sidebar) {
                SettingsPage {
                    // The sidebar's rows navigate the right-hand pane, not the app.
                    SettingsScreen(detail, sidebarScroll, latestVersionName)
                }
            }
        }
        // The hairline between the panes, as ColorOS draws it.
        Box(
            Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(SettingsDimensions.dividerColor()),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            NavHost(
                navController = detail,
                startDestination = SplitStartRoute,
            ) {
                navigationBuilder(detail, detailScroll, latestVersionName)
            }
        }
    }
}
