/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.ExploreViewModel
import com.ozyern.exhale.viewmodels.MoodAndGenresViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * The Search tab, as the iOS music app draws it: "Search" and the account disc over a grid of
 * category tiles, two across. A tile runs its search; FLAC opens the FLAC page. Typing happens in
 * the dock's own field, or in the field under the title when that setting puts it there.
 */
@Composable
fun SearchScreen(
    navController: NavController,
) {
    val (pureBlack) = rememberPreference(PureBlackKey, defaultValue = false)
    val layoutDirection = LocalLayoutDirection.current
    val basePadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val chrome = LocalSearchChrome.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BrowseCategoriesGrid(
        pureBlack = pureBlack,
        onProfileClick = { chrome?.openAccount?.invoke() ?: navController.navigate("settings") },
        onCategoryClick = { query -> navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}") },
        contentPadding = PaddingValues(
            start = basePadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = basePadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = statusTop + 8.dp,
            bottom = basePadding.calculateBottomPadding() + 16.dp,
        ),
        searchField = if (chrome?.fieldAtTop == true) {
            { SearchFieldPill(onClick = chrome.openSearch) }
        } else {
            null
        },
        onFlacClick = { navController.navigate("flac") },
    )
}

/**
 * the iOS music app's field under the title: a capsule with the glass, the prompt and a microphone at the
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
