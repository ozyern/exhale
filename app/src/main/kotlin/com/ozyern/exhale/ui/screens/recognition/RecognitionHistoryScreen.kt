/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.recognition

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.utils.openSearchResults

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.ozyern.exhale.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField as TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFClock
import com.composables.sfsymbols.monochrome.SFMagnifyingglass
import com.composables.sfsymbols.monochrome.SFTrash
import com.composables.sfsymbols.monochrome.SFXmark
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ThumbnailRoundedShape
import com.ozyern.exhale.db.entities.RecognitionHistory
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.component.ScreenScaffold
import com.ozyern.exhale.ui.component.topBarContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecognitionHistoryScreen(
    navController: NavController
) {
    val database = LocalDatabase.current.get()
    val coroutineScope = rememberCoroutineScope()

    val historyItems by database.recognitionHistory().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<RecognitionHistory?>(null) }

    // Search state
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    // Filtered list - client-side, matches title or artist
    val filteredItems = remember(historyItems, query) {
        if (query.text.isEmpty()) historyItems
        else historyItems.filter { item ->
            item.title.contains(query.text, ignoreCase = true) ||
                item.artist.contains(query.text, ignoreCase = true)
        }
    }

    // Group by date label: Today / Yesterday / This Week / Month Year
    val groupedItems = remember(filteredItems) {
        val today = LocalDate.now()
        filteredItems.groupBy { item ->
            val date = item.recognizedAt.toLocalDate()
            when {
                date == today                -> "Today"
                date == today.minusDays(1)  -> "Yesterday"
                date >= today.minusDays(7)  -> "This Week"
                else -> item.recognizedAt.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
        }
    }

    if (showClearDialog) {
        DefaultDialog(
            onDismiss = { showClearDialog = false },
            icon = {
                SymbolIcon(symbol = sf("SFTrash"), contentDescription = null)
            },
            title = { Text(stringResource(R.string.clear_recognition_history)) },
            buttons = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                clearRecognitionHistory()
                            }
                        }
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.clear_recognition_history_confirm),
                style = AppTheme.typography.bodyMedium
            )
        }
    }

    itemToDelete?.let { item ->
        DefaultDialog(
            onDismiss = { itemToDelete = null },
            icon = {
                SymbolIcon(symbol = sf("SFTrash"), contentDescription = null)
            },
            title = { Text(stringResource(R.string.delete)) },
            buttons = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                deleteRecognitionHistoryById(item.id)
                            }
                        }
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.delete_playlist_confirm, item.title),
                style = AppTheme.typography.bodyMedium
            )
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.recognition_history),
        titleAlpha = 1f,
        onBack = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        actions = {
            if (historyItems.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    SymbolIcon(symbol = sf("SFTrash"), contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarContentPadding())
        ) {
            // Static search bar
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    SymbolIcon(symbol = sf("SFMagnifyingglass"), contentDescription = null, tint = AppTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton(onClick = { query = TextFieldValue() }) {
                            SymbolIcon(symbol = sf("SFXmark"), contentDescription = null, tint = AppTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = AppTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = AppTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // List and empty state
            when {
                historyItems.isEmpty() -> {
                    // No history at all
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SymbolIcon(symbol = sf("SFClock"), contentDescription = null, size = 64.dp, tint = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No recognition history",
                                style = AppTheme.typography.bodyLarge,
                                color = AppTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                filteredItems.isEmpty() -> {
                    // History exists but query matches nothing
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SymbolIcon(symbol = sf("SFMagnifyingglass"), contentDescription = null, size = 64.dp, tint = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No results for \"${query.text}\"",
                                style = AppTheme.typography.bodyLarge,
                                color = AppTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues()
                    ) {
                        if (query.text.isEmpty()) {
                            // No active search → show grouped with sticky date headers
                            groupedItems.forEach { (label, groupItems) ->
                                stickyHeader(key = "header_$label") {
                                    NavigationTitle(
                                        title = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(AppTheme.colorScheme.surface)
                                    )
                                }
                                items(
                                    items = groupItems,
                                    key = { it.id }
                                ) { item ->
                                    RecognitionHistoryItem(
                                        item = item,
                                        onClick = {
                                            val searchQuery = "${item.title} ${item.artist}"
                                            navController.openSearchResults(searchQuery)
                                        },
                                        onDelete = {
                                            itemToDelete = item
                                        }
                                    )
                                }
                            }
                        } else {
                            // Active search → flat list, no date headers
                            items(
                                items = filteredItems,
                                key = { it.id }
                            ) { item ->
                                RecognitionHistoryItem(
                                    item = item,
                                    onClick = {
                                        val searchQuery = "${item.title} ${item.artist}"
                                        navController.openSearchResults(searchQuery)
                                    },
                                    onDelete = {
                                        itemToDelete = item
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionHistoryItem(
    item: RecognitionHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .bounceClick { onClick() },
        shape = ThumbnailRoundedShape,
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.coverArtUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(ThumbnailRoundedShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.recognizedAt.format(dateFormatter),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onDelete) {
                SymbolIcon(symbol = sf("SFTrash"), contentDescription = stringResource(R.string.delete_from_history), tint = AppTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
