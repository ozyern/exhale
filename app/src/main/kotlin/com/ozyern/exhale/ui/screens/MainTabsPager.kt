/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.ozyern.exhale.ui.screens.library.LibraryScreen
import com.ozyern.exhale.ui.screens.settings.SettingsScreen

/** Main pager pages in tab-bar order. Search is an overlay, not a pager page. */
val MainTabsScreens = listOf(
    Screens.Home,
    Screens.Library,
    Screens.Settings,
)

const val MainTabsRoute = "main_tabs"

/**
 * Animate to [page] without ever animating *across* an intervening page.
 *
 * [PagerState.animateScrollToPage] scrolls through everything between here and there,
 * and with `beyondViewportPageCount = 0` each page it passes over is composed from
 * scratch inside the animation's own frames. Home -> Settings therefore paid for
 * building the entire Library screen mid-slide, so how badly a tab switch stuttered
 * depended on which tab it happened to travel over rather than on where it was going.
 *
 * Landing one page short first makes the animated part always exactly one page: the
 * jump is invisible (it happens in a single frame, before any motion starts) and the
 * slide the user actually sees is unchanged.
 */
suspend fun PagerState.slideToPage(page: Int) {
    val from = currentPage
    if (page - from > 1 || from - page > 1) {
        scrollToPage(page + if (page > from) -1 else 1)
    }
    animateScrollToPage(page)
}

/**
 * Hosts top-level screens in one pager; detail routes still use [navController].
 * Keeps beyond-viewport composition at its default to avoid holding every tab open.
 * Swiping between tabs is disabled because screens use horizontal gestures; tab
 * taps still animate between pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsPager(
    pagerState: PagerState,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (MainTabsScreens.getOrNull(page)) {
            Screens.Home -> HomeRoute(navController = navController, snackbarHostState = snackbarHostState)

            Screens.Library -> LibraryScreen(navController)

            Screens.ListenTogether -> JamScreen(navController, asTab = true)

            Screens.Settings -> SettingsScreen(navController, scrollBehavior)

            else -> Unit
        }
    }
}
