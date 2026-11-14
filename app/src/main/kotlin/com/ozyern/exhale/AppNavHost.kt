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
        // The incoming screen slides a full width while the outgoing
        // screen moves a third of that distance and dims. Both use the
        // same curve to keep the parallax in sync.
        enterTransition = {
            slideInHorizontally(if (isMorphRoute(targetState.destination.route)) Motion.artwork() else Motion.push()) { it }
        },
        exitTransition = {
            // A destination that grows out of one of this
            // screen's tiles must not slide it away underneath:
            // the tile is the thing the next screen is coming
            // out of, so this one holds still and dims.
            if (isMorphRoute(targetState.destination.route)) {
                fadeOut(Motion.artwork(), targetAlpha = 0.7f)
            } else {
                slideOutHorizontally(Motion.push()) {
                    Motion.parallaxOffset(it)
                } + fadeOut(Motion.push(), targetAlpha = Motion.PushDimAlpha)
            }
        },
        // The screen underneath eases back from its parallax offset while the leaving one slides off,
        // so back mirrors the push. The incoming screen stays opaque to avoid a transparent overlap.
        popEnterTransition = {
            if (isMorphRoute(initialState.destination.route)) {
                EnterTransition.None
            } else {
                slideInHorizontally(Motion.push()) { Motion.parallaxOffset(-it) }
            }
        },
        popExitTransition = {
            if (isMorphRoute(initialState.destination.route)) {
                fadeOut(Motion.artwork())
            } else {
                slideOutHorizontally(Motion.push()) { it } + fadeOut(Motion.push())
            }
        },
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
