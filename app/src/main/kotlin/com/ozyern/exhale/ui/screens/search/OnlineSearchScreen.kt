/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package com.ozyern.exhale.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ozyern.exhale.innertube.models.*
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.ui.menu.*
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsState()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    // Apple-Music "Browse Categories" landing: shown while the field is empty and there are no
    // history/suggestion rows to surface. Tapping a category runs it as a search query.
    val showBrowse = query.isBlank() &&
        viewState.history.isEmpty() &&
        viewState.suggestions.isEmpty() &&
        viewState.items.isEmpty()

    if (showBrowse) {
        BrowseCategoriesGrid(
            pureBlack = pureBlack,
            onProfileClick = { navController.navigate("settings") },
            onCategoryClick = { category ->
                onSearch(category)
                onDismiss()
            },
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.systemBars.only(WindowInsetsSides.Top)
                    .asPaddingValues().calculateTopPadding() + 4.dp,
                // Extra bottom room so the last cards clear the floating mini-player + bottom search pill.
                bottom = WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
                    .asPaddingValues().calculateBottomPadding() + 140.dp,
            ),
        )
        return
    }

    // Two separate inset-grouped cards, each indexed from its own zero.
    //
    // They used to be ONE card: `groupCount = history + suggestions`, so the "Search history" header
    // sat above a single plate whose top half was things you had typed before and whose bottom half
    // was live suggestions from YouTube, with nothing marking the boundary and no header naming the
    // second half. Two different kinds of thing in one container, told apart only by a 19dp icon.
    val historyCount = viewState.history.size
    val suggestionCount = viewState.suggestions.size

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (historyCount > 0) {
            item(key = "recent_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
                        .animateItem(),
                ) {
                    // "Recently Searched" set as the iOS music app sets it: Title 2, on the page's
                    // 16dp margin, with Clear as a plain accent word at the other end.
                    Text(
                        text = stringResource(R.string.search_recent),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.clear),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                database.query {
                                    clearSearchHistory()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        itemsIndexed(viewState.history, key = { _, it -> "history_${it.query}" }) { index, history ->
            // Swiped away, as a recent search is on iOS, rather than an ✕ on every row.
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        database.query { delete(history) }
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = { com.ozyern.exhale.ui.player.QueueSwipeBackground(dismissState, 16.dp) },
                modifier = Modifier.animateItem(),
            ) {
                SuggestionItem(
                    query = history.query,
                    online = false,
                    position = groupPosition(index, historyCount),
                    showDivider = index < historyCount - 1,
                    onClick = {
                        onSearch(history.query)
                        onDismiss()
                    },
                    onFillTextField = {
                        onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                    },
                    pureBlack = pureBlack
                )
            }
        }

        if (suggestionCount > 0) {
            item(key = "suggestions_header") {
                Text(
                    text = stringResource(R.string.search_suggestions),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (historyCount > 0) 20.dp else 8.dp,
                            bottom = 6.dp,
                        )
                        .animateItem(),
                )
            }
        }

        itemsIndexed(viewState.suggestions, key = { _, it -> "suggestion_$it" }) { index, query ->
            SuggestionItem(
                query = query,
                online = true,
                position = groupPosition(index, suggestionCount),
                showDivider = index < suggestionCount - 1,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(query, TextRange(query.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack
            )
        }

        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                // A plain section title, as the results page sets its own — no accent bar.
                Text(
                    text = stringResource(R.string.top_results),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Already de-duplicated in the view model. Doing it here re-allocated the entire
        // list inside the LazyColumn's content lambda on every recomposition.
        itemsIndexed(viewState.items, key = { _, it -> "item_${it.id}" }) { rowIndex, item ->
            val count = viewState.items.size
            val rowShape = when {
                count <= 1 -> RoundedCornerShape(22.dp)
                rowIndex == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                rowIndex == count - 1 -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Box(Modifier.animateItem()) {
            YouTubeListItem(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                when (item) {
                                    is SongItem -> YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is AlbumItem -> YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is ArtistItem -> YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is PlaylistItem -> YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = scope,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            // iOS's "…", not Android's vertical dots.
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata())
                                        )
                                        onDismiss()
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                    onDismiss()
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                    onDismiss()
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                    onDismiss()
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                when (item) {
                                    is SongItem -> YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )

                                    is AlbumItem -> YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )

                                    is ArtistItem -> YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )

                                    is PlaylistItem -> YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    )
            )
            }
        }
    }
}

/** Where a row sits inside its inset-grouped card — drives per-corner rounding. */
enum class GroupPosition { SINGLE, FIRST, MIDDLE, LAST }

fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.FIRST
    index == count - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

/**
 * One row of the iOS-style inset-grouped "Recent Searches" card: rows share a single
 * rounded translucent surface (corners only on the group's first/last row), separated by
 * hairline dividers inset past the leading icon — exactly iOS's Settings/Music list
 * grammar, replacing the old flat full-bleed text rows.
 */
@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    position: GroupPosition = GroupPosition.MIDDLE,
    showDivider: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean
) {
    val cardColor = if (pureBlack) {
        Color.White.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    }

    // Quiet grey glyphs: the text is the content, the icon only says what kind of row it is.
    val iconTint = if (pureBlack) Color.White.copy(alpha = 0.42f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    val corner = 22.dp
    val shape = when (position) {
        GroupPosition.SINGLE -> RoundedCornerShape(corner)
        GroupPosition.FIRST -> RoundedCornerShape(topStart = corner, topEnd = corner)
        GroupPosition.LAST -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        GroupPosition.MIDDLE -> RoundedCornerShape(0.dp)
    }

    // Rows on the page itself, as the iOS music app lists them: no card, no slab per row — a
    // magnifier, a line of text, and a hairline between rows. Nothing at the trailing edge: a
    // recent search is swiped away (see the history list), and a long press puts any row's text in
    // the field to edit, where the ✕ and the ↖ arrow used to clutter every line.
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusable()
                .combinedClickable(onClick = onClick, onLongClick = onFillTextField)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = query,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                color = if (pureBlack) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (showDivider) {
            // Hairline inset to the text and run to the screen's edge — the iOS list separator.
            HorizontalDivider(
                thickness = 0.5.dp,
                color = if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 52.dp)
            )
        }
    }
}
