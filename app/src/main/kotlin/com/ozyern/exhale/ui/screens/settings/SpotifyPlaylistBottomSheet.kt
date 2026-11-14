package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.ios.compat.AppTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ozyern.exhale.ui.ios.compat.rememberIosModalSheetState
import com.ozyern.exhale.ui.ios.compat.Checkbox
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.ModalBottomSheet
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.spotify.SpotifyMapper
import com.music.spotify.models.SpotifyPlaylist
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.AnimatedActionButton
import com.ozyern.exhale.ui.component.detachedItemShape
import com.ozyern.exhale.ui.component.endItemShape
import com.ozyern.exhale.ui.component.leadingItemShape
import com.ozyern.exhale.ui.component.middleItemShape
import com.ozyern.exhale.viewmodels.SpotifyImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistBottomSheet(
    onDismiss: () -> Unit,
    viewModel: SpotifyImportViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedIds = remember { mutableStateListOf<String>() }

    val displayItems = remember(state.likedSongsCount, state.playlists) {
        val list = mutableListOf<Any>()
        if (state.likedSongsCount > 0) {
            list.add("liked_songs")
        }
        list.addAll(state.playlists)
        list
    }

    var searchQuery by remember { mutableStateOf("") }

    val filteredDisplayItems = remember(displayItems, searchQuery) {
        if (searchQuery.isBlank()) {
            displayItems
        } else {
            displayItems.filter { item ->
                if (item is String && item == "liked_songs") {
                    val likedSongsText = context.getString(R.string.spotify_liked_songs)
                    likedSongsText.contains(searchQuery, ignoreCase = true)
                } else if (item is SpotifyPlaylist) {
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.owner?.displayName.orEmpty().contains(searchQuery, ignoreCase = true)
                } else {
                    false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberIosModalSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = AppTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.playlists),
                    style = AppTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colorScheme.onSurface
                )

                val isAllSelected = filteredDisplayItems.isNotEmpty() && filteredDisplayItems.all { item ->
                    val id = if (item is String) item else (item as SpotifyPlaylist).id
                    id in selectedIds
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.spotify_select_all),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { checked ->
                            filteredDisplayItems.forEach { item ->
                                val id = if (item is String) item else (item as SpotifyPlaylist).id
                                if (checked) {
                                    if (id !in selectedIds) selectedIds.add(id)
                                } else {
                                    selectedIds.remove(id)
                                }
                            }
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    SymbolIcon(symbol = sf("magnifyingglass"), contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            SymbolIcon(symbol = sf("xmark"), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.colorScheme.primary,
                    unfocusedBorderColor = AppTheme.colorScheme.outlineVariant
                )
            )

            HorizontalDivider(
                color = AppTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(filteredDisplayItems, key = { _, item ->
                    if (item is String) item else (item as SpotifyPlaylist).id
                }) { index, item ->
                    val shape = when {
                        filteredDisplayItems.size == 1 -> detachedItemShape()
                        index == 0 -> leadingItemShape()
                        index == filteredDisplayItems.size - 1 -> endItemShape()
                        else -> middleItemShape()
                    }

                    if (item is String && item == "liked_songs") {
                        SpotifySourceRow(
                            title = stringResource(R.string.spotify_liked_songs),
                            subtitle = stringResource(R.string.spotify_liked_songs_desc),
                            thumbnailUrl = null,
                            trackCount = state.likedSongsCount,
                            selected = "liked_songs" in selectedIds,
                            shape = shape,
                            onClick = {
                                if ("liked_songs" in selectedIds) selectedIds.remove("liked_songs")
                                else selectedIds.add("liked_songs")
                            }
                        )
                    } else if (item is SpotifyPlaylist) {
                        SpotifySourceRow(
                            title = item.name,
                            subtitle = item.owner?.displayName.orEmpty(),
                            thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(item),
                            trackCount = item.tracks?.total ?: 0,
                            selected = item.id in selectedIds,
                            shape = shape,
                            onClick = {
                                if (item.id in selectedIds) selectedIds.remove(item.id)
                                else selectedIds.add(item.id)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = AppTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AnimatedActionButton(
                    text = stringResource(R.string.import_action),
                    onClick = {
                        viewModel.startImport(selectedIds.toList())
                        onDismiss()
                    },
                    modifier = Modifier.widthIn(min = 120.dp),
                    enabled = selectedIds.isNotEmpty() && !state.isLoading
                )
            }
        }
    }
}

@Composable
private fun SpotifySourceRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    trackCount: Int,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = if (selected) {
            AppTheme.colorScheme.secondaryContainer
        } else {
            AppTheme.colorScheme.surfaceContainerLow
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppTheme.shapes.medium)
                    .background(AppTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SymbolIcon(symbol = sf("heart.fill"), contentDescription = null, size = 24.dp, tint = AppTheme.colorScheme.primary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (trackCount > 0) stringResource(R.string.spotify_track_count, trackCount) else subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
            )
        }
    }
}
