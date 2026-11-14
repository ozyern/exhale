/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalAmbientBackgroundColor
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.LocalAccentTextColor
import com.ozyern.exhale.ui.theme.isHueless
import androidx.compose.ui.graphics.isSpecified

/**
 * A screen's large title, rendered as the first item of its list or grid.
 *
 * Pairs with [CollapsedTitleBar]: the large title scrolls away with the
 * content while the compact bar fades in over it, which is the iOS/Apple Music
 * large-title behaviour. It is built this way rather than on `LargeTopAppBar`
 * because these screens put a [HeroBackground] behind the list — the artwork
 * has to scroll *with* the content, which a top-bar slot cannot do.
 *
 * Owns the status-bar clearance so screens stop repeating `Spacer(40.dp)`.
 */
@Composable
fun LargeScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Spacer(Modifier.height(40.dp))

    // Neutral per theme; hero screens keep their artwork-derived tint.
    val ambient = LocalAmbientBackgroundColor.current.value
    val titleColor = color ?: if (ambient.isSpecified && !ambient.isHueless()) {
        LocalAccentTextColor.current
    } else {
        AppTheme.colorScheme.onSurface
    }

    Text(
        text = title,
        style = AppTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        color = titleColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = AppleTokens.Gutter, vertical = 24.dp),
    )
}

/**
 * Progress from 0 to 1 as the large title scrolls out. Uses its measured height
 * so the compact title appears only after the large title is fully gone.
 */
@Composable
fun rememberTitleCollapseProgressState(state: LazyListState, titleItemKey: Any? = null): State<Float> =
    remember(state, titleItemKey) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            val titleItem = if (titleItemKey != null) {
                visible.firstOrNull { it.key == titleItemKey }
            } else {
                visible.firstOrNull { it.index == 0 }
            }
            if (titleItem == null) {
                // Not in the visible range at all — scrolled fully past.
                1f
            } else {
                (-titleItem.offset.toFloat() / titleItem.size.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

@Composable
fun rememberTitleCollapseProgress(state: LazyListState, titleItemKey: Any? = null): Float =
    rememberTitleCollapseProgressState(state, titleItemKey).value

@Composable
fun rememberTitleCollapseProgressState(state: LazyGridState, thresholdPx: Float = 220f): State<Float> =
    remember(state) {
        derivedStateOf {
            val scrolled = if (state.firstVisibleItemIndex == 0) {
                state.firstVisibleItemScrollOffset.toFloat()
            } else {
                thresholdPx
            }
            (scrolled / thresholdPx).coerceIn(0f, 1f)
        }
    }

@Composable
fun rememberTitleCollapseProgress(state: LazyGridState, thresholdPx: Float = 220f): Float {
    val progress by rememberTitleCollapseProgressState(state, thresholdPx)
    return progress
}

/**
 * The compact bar that replaces [LargeScreenTitle] once it has scrolled off.
 * Overlay it on top of the list — it draws nothing at [progress] 0, so it costs
 * only its own layout while the large title is still showing.
 */
@Composable
fun CollapsedTitleBar(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppBarHeight)
            .graphicsLayer { alpha = progress },
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color ?: LocalAccentTextColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = AppBarHeight),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap / 2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(end = AppleTokens.Gutter),
            content = actions,
        )
    }
}
