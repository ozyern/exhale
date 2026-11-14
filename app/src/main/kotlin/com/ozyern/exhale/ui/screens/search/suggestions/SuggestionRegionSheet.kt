package com.ozyern.exhale.ui.screens.search.suggestions

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.ios.compat.AppTheme

import androidx.compose.animation.core.animateDpAsState
import com.ozyern.exhale.ui.utils.bounceClick
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.ListItem
import com.ozyern.exhale.ui.ios.compat.IosSearchField
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import androidx.compose.foundation.background
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.ozyern.exhale.ui.ios.compat.ModalBottomSheet
import com.ozyern.exhale.ui.ios.compat.rememberIosModalSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.constants.SuggestionRegionSlugToName
import kotlinx.coroutines.launch

@Composable
fun SuggestionRegionSheet(
    currentRegionSlug: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredRegions = remember(searchQuery) {
        SuggestionRegionSlugToName.toList()
            .filter { it.first != "system" }
            .filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberIosModalSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Choose Suggestions Region",
                style = AppTheme.typography.labelLarge
            )
            
            IosSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search regions...",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                item {
                    Text(
                        text = "System",
                        style = AppTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
                item {
                    val isSelected = currentRegionSlug == "system"
                    RegionListItem(
                        headlineContent = { Text("System Default") },
                        selected = isSelected,
                        items = 1,
                        index = 0,
                        onClick = {
                            onRegionSelected("system")
                            scope.launch { bottomSheetState.hide() }
                        }
                    )
                }
                
                item {
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    Text(
                        text = "Countries & Regions",
                        style = AppTheme.typography.titleSmall,
                        modifier = Modifier.padding(
                            top = 14.dp,
                            bottom = 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                    )
                }

                itemsIndexed(filteredRegions, key = { _, pair -> pair.first }) { index, (slug, name) ->
                    val isSelected = slug == currentRegionSlug
                    RegionListItem(
                        headlineContent = { Text(name) },
                        selected = isSelected,
                        items = filteredRegions.size,
                        index = index,
                        onClick = {
                            onRegionSelected(slug)
                            scope.launch { bottomSheetState.hide() }
                        }
                    )
                    Spacer(Modifier.height(2.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
    
    LaunchedEffect(searchQuery) {
        if (filteredRegions.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
}

@Composable
fun RegionListItem(
    headlineContent: @Composable (() -> Unit),
    selected: Boolean,
    items: Int,
    index: Int,
    supportingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val top by animateDpAsState(
        if (isPressed) 36.dp
        else {
            if (items == 1 || index == 0) 20.dp
            else 4.dp
        },
        label = "top"
    )
    val bottom by animateDpAsState(
        if (isPressed) 36.dp
        else {
            if (items == 1 || index == items - 1) 20.dp
            else 4.dp
        },
        label = "bottom"
    )

    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = if (selected) {
            {
                SymbolIcon(symbol = sf("SFCheckmark"), contentDescription = "Selected", size = 17.dp, tint = IosTheme.tint)
            }
        } else null,
        modifier = Modifier
            .clip(
                RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomStart = bottom,
                    bottomEnd = bottom,
                )
            )
            .background(
                if (isPressed) IosTheme.colors.tertiarySystemFill
                else IosTheme.colors.secondarySystemGroupedBackground
            )
            .bounceClick(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
    )
}
