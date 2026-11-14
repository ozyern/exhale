/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.ui.component.GlassScreenChrome
import com.ozyern.exhale.ui.component.ScreenLazyPage
import com.ozyern.exhale.ui.component.ScreenPage
import com.ozyern.exhale.ui.component.ScreenScaffold
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.utils.backToMain

/**
 * The settings dialect of [ScreenPage] and friends: iOS grouped background, iOS
 * large-title type, and the back button wired to the nav stack (long-press jumps
 * straight to the root, which is how these pages have always behaved).
 *
 * Everything structural — the Haze source, the progressive blur, the title
 * handoff — lives in [com.ozyern.exhale.ui.component.ScreenChromeBox] and is shared
 * with every other pushed screen in the app. These three exist only so a settings
 * page does not repeat the same four arguments forty times.
 */
private fun settingsLargeTitle(title: String): @Composable () -> Unit = {
    Text(
        text = title,
        style = IosTheme.typography.largeTitle,
        color = IosTheme.colors.label,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 14.dp),
    )
}

/** Readable column width on tablets; phones are narrower so it never applies there. */
private val SettingsContentMaxWidth = 720.dp

/** Side gutter that centres a [maxWidth] column on wide windows and falls back to [minimum] on phones. */
@Composable
private fun settingsGutter(minimum: Dp, maxWidth: Dp): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (screenWidth - maxWidth) / 2)
}

/** A settings page whose body is a plain scrolling Column — most of them. */
@Composable
fun SettingsPage(
    navController: NavController,
    title: String,
    modifier: Modifier = Modifier,
    /** Overrides the default `navigateUp` — for a page opened outside the settings stack. */
    onBack: (() -> Unit)? = null,
    pageColor: Color = IosTheme.colors.systemGroupedBackground,
    horizontalPadding: Dp = 0.dp,
    maxContentWidth: Dp = SettingsContentMaxWidth,
    actions: @Composable () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides IosTheme.colors.label,
    ) { ScreenPage(
        title = title,
        modifier = modifier,
        onBack = onBack ?: { navController.navigateUp(); Unit },
        onBackLongClick = navController::backToMain,
        // Cards are painted on the iOS grouped fill, so the page behind them must be too.
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = settingsGutter(horizontalPadding, maxContentWidth),
        actions = actions,
        scrollState = scrollState,
        largeTitle = settingsLargeTitle(title),
        content = {
            content()
            Spacer(Modifier.height(32.dp))
        },
    ) }
}

/** The LazyColumn flavour, for settings pages whose body is a real list. */
@Composable
fun SettingsLazyPage(
    navController: NavController,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    pageColor: Color = IosTheme.colors.systemGroupedBackground,
    horizontalPadding: Dp = 0.dp,
    maxContentWidth: Dp = SettingsContentMaxWidth,
    actions: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides IosTheme.colors.label,
    ) { ScreenLazyPage(
        title = title,
        modifier = modifier,
        onBack = onBack ?: { navController.navigateUp(); Unit },
        onBackLongClick = navController::backToMain,
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = settingsGutter(horizontalPadding, maxContentWidth),
        actions = actions,
        listState = listState,
        largeTitle = settingsLargeTitle(title),
        content = content,
    ) }
}

/**
 * For a settings page whose body is neither — an icon grid, a WebView, a
 * two-pane layout. It keeps its own container and owns its own [titleAlpha].
 */
@Composable
fun SettingsScaffold(
    navController: NavController,
    title: String,
    titleAlpha: Float,
    onBack: (() -> Unit)? = null,
    pageColor: Color = IosTheme.colors.systemGroupedBackground,
    actions: @Composable () -> Unit = {},
    titleAlphaProvider: (() -> Float)? = null,
    maxContentWidth: Dp = SettingsContentMaxWidth,
    content: @Composable BoxScope.(GlassScreenChrome) -> Unit,
) {
    ScreenScaffold(
        title = title,
        titleAlpha = titleAlpha,
        titleAlphaProvider = titleAlphaProvider,
        onBack = onBack ?: { navController.navigateUp(); Unit },
        onBackLongClick = navController::backToMain,
        pageColor = pageColor,
        actions = actions,
        content = { chrome ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxSize().widthIn(max = maxContentWidth)) { content(chrome) }
            }
        },
    )
}
