/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.utils.rememberPreference
import java.net.URLEncoder

/**
 * The Search TAB — a first-class, peer-level NavHost destination (route "search"), sibling to
 * Home/Library, NOT a dialog/overlay.
 *
 * BOTTOM CHROME: the Search route uses a COMPLETELY different navigation layout from Home.
 * MainActivity swaps the morphing A/B [LiquidGlassBottomBar] for the fixed
 * [com.ozyern.exhale.ui.component.SearchBottomBar] (a standalone circular Home pill + a
 * separate search-input capsule) while this screen is on top, and disables the
 * scroll-collapse morph entirely. The mini-player floats directly above that bar. This
 * screen therefore renders ONLY the category grid — no docked pill of its own.
 */
@Composable
fun SearchScreen(
    navController: NavController,
) {
    val (pureBlack) = rememberPreference(PureBlackKey, defaultValue = false)
    val layoutDirection = LocalLayoutDirection.current
    // Player-aware insets already include the top app bar, nav bar and mini-player heights,
    // so the grid never hides behind the floating chrome (incl. the unified search bar).
    val basePadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    val chrome = LocalSearchChrome.current
    // The page draws its own header, as Apple Music's does: the app's logo bar is not shown here, so
    // the title starts just under the status bar instead of under an empty bar's worth of space.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        BrowseCategoriesGrid(
            pureBlack = pureBlack,
            // "Search", and the account disc beside it.
            showProfile = true,
            onProfileClick = { chrome?.openAccount?.invoke() ?: navController.navigate("settings") },
            onCategoryClick = { category ->
                navController.navigate("search/${URLEncoder.encode(category, "UTF-8")}")
            },
            searchField = if (chrome?.fieldAtTop == true) {
                { SearchFieldPill(onClick = chrome.openSearch) }
            } else null,
            contentPadding = PaddingValues(
                start = basePadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = basePadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = statusTop + 8.dp,
                bottom = basePadding.calculateBottomPadding() + 16.dp,
            ),
        )
    }
}

/**
 * Apple Music's field under the title: a capsule with the glass, the prompt and a microphone at the
 * end. Tapping anywhere on it starts typing in the app's search field.
 */
@Composable
private fun SearchFieldPill(onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = if (dark) Color(0xFF1C1C1E) else Color(0xFFE9E9EE)
    val ink = if (dark) Color.White else Color.Black
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Artists, Songs, Lyrics and More",
            fontSize = 17.sp,
            color = ink.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.mic),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
