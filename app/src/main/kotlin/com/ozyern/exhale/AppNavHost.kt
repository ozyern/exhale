/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.ozyern.exhale.ui.screens.navigationBuilder
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.ui.utils.isMorphRoute
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.utils.PageMotion

/** The app's NavHost: push/pop motion plus the destination graph. */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    activity: android.app.Activity,
    snackbarHostState: SnackbarHostState,
    accountImageUrl: String?,
    accountName: String,
    onAccountClick: () -> Unit,
) {
    // NavHost with animations (Material 3 Expressive style)
    NavHost(
        navController = navController,
        // Which tab this opens on (launch shortcut / the
        // default-open-tab preference) -- passed in by the caller.
        startDestination = startDestination,
        // Tabs swap outright; every other page crossfades in 180ms (see PageMotion).
        enterTransition = { PageMotion.enter(initialState.destination.route, targetState.destination.route) },
        exitTransition = { PageMotion.exit(initialState.destination.route, targetState.destination.route) },
        popEnterTransition = { PageMotion.enter(initialState.destination.route, targetState.destination.route) },
        popExitTransition = { PageMotion.exit(initialState.destination.route, targetState.destination.route) },
        modifier = Modifier.fillMaxSize(),
    ) {
        navigationBuilder(
            navController = navController,
            scrollBehavior = scrollBehavior,
            activity = activity,
            snackbarHostState = snackbarHostState,
            accountImageUrl = accountImageUrl,
            accountName = accountName,
            onAccountClick = onAccountClick,
        )
    }
}
