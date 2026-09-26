/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalPlayerAwareWindowInsets

/**
 * Pull to refresh, with the comet ring instead of Material's expressive blob.
 *
 * The blob is the single most Material thing on any screen that has one: a filled container that
 * grows out of the top edge and squashes as it settles. What a pull is supposed to reveal is a
 * small loader hanging under the top edge and nothing else — so the ring fades and grows in with
 * the drag, spins while the refresh runs, and leaves with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val indicatorPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            // Tied to the drag, not to a spring of its own: while the finger is down the ring is
            // exactly as far along as the pull is.
            val reveal = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(indicatorPadding)
                    .padding(top = 20.dp)
                    .graphicsLayer {
                        alpha = reveal
                        val scale = 0.65f + 0.35f * reveal
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                if (reveal > 0.01f) {
                    LoadingRing(
                        modifier = Modifier.size(26.dp),
                        color = MaterialTheme.colorScheme.primary,
                        stroke = 2.5.dp,
                    )
                }
            }
        },
        content = content,
    )
}
