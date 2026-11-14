/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.MutableState
import com.ozyern.exhale.ui.component.backdrop.backdrops.LayerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberNavTransitionFreeze
import com.ozyern.exhale.ui.component.floatingtabbar.FloatingTabBarScrollConnection
import com.ozyern.exhale.ui.ios.compat.AppTheme
import kotlinx.coroutines.delay

/** State shared by the app backdrop and its freeze controls. */
@Stable
class AppBackdropState(
    val baseBg: Color,
    val appBackdrop: LayerBackdrop,
    val frozenProvider: () -> Boolean,
    val motionFrozen: MutableState<Boolean>,
    val scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun rememberAppBackdropState(
    pureBlack: Boolean,
    currentRoute: String?,
    floatingNavBarScrollConnection: FloatingTabBarScrollConnection,
): AppBackdropState {
    val baseBg = if (pureBlack) Color.Black else AppTheme.colorScheme.surfaceContainer

    // Keep this draw lambda stable; changing it makes the backdrop record again.
    val appBackdrop = rememberLayerBackdrop(
        onDraw = remember(baseBg) {
            val bg = baseBg
            {
                drawRect(bg)
                drawContent()
            }
        }
    )

    // Keep blur capture still during scrolls and route transitions.
    val backdropFreeze = rememberBackdropFreeze()
    val navTransitionFreeze = rememberNavTransitionFreeze(currentRoute)
    val backdropFrozenProvider = remember(
        backdropFreeze,
        navTransitionFreeze,
        floatingNavBarScrollConnection,
    ) {
        {
            backdropFreeze.frozen() ||
                navTransitionFreeze.frozen() ||
                floatingNavBarScrollConnection.isScrolling()
        }
    }

    // Drawing reads the provider directly; composition needs observable state.
    val appMotionFrozen = remember { mutableStateOf(false) }
    val backdropFreezeConnection = remember(backdropFreeze, backdropFrozenProvider, appMotionFrozen) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pause motion once per gesture, before the next draw.
                if (!appMotionFrozen.value) appMotionFrozen.value = true
                return backdropFreeze.connection.onPreScroll(available, source)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val result = backdropFreeze.connection.onPostFling(consumed, available)
                appMotionFrozen.value = backdropFrozenProvider()
                return result
            }
        }
    }
    LaunchedEffect(backdropFrozenProvider) {
        while (true) {
            val isFrozen = backdropFrozenProvider()
            if (appMotionFrozen.value != isFrozen) appMotionFrozen.value = isFrozen
            delay(120L)
        }
    }

    return remember(baseBg, appBackdrop, backdropFrozenProvider, appMotionFrozen, backdropFreezeConnection) {
        AppBackdropState(baseBg, appBackdrop, backdropFrozenProvider, appMotionFrozen, backdropFreezeConnection)
    }
}
