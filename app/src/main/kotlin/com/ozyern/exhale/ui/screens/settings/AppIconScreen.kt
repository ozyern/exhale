/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppIconKey
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.rememberTitleCollapseProgressState
import com.ozyern.exhale.ui.component.topBarContentPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.AppIcon
import com.ozyern.exhale.utils.rememberPreference

/** Adaptive icons draw a 108dp canvas but launchers only reveal about 72dp of it. */
private const val ADAPTIVE_OVERSCAN = 108f / 72f

/**
 * Picker for the launcher icon. Each tile reproduces what the launcher will draw: the variant's
 * adaptive foreground composited over the shared tile colour, clipped to a squircle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val (selectedId, onSelectedIdChange) = rememberPreference(
        AppIconKey,
        defaultValue = AppIcon.DEFAULT.id,
    )
    val selected = remember(selectedId) { AppIcon.fromId(selectedId) }
    var pending by remember { mutableStateOf<AppIcon?>(null) }

    pending?.let { icon ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.app_icon)) },
            text = { Text(stringResource(R.string.app_icon_change_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    AppIcon.apply(context, icon)
                    onSelectedIdChange(icon.id)
                    pending = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val gridState = rememberLazyGridState()
    val appIconTitleProgress = rememberTitleCollapseProgressState(gridState)

    SettingsScaffold(
        navController = navController,
        title = stringResource(R.string.app_icon),
        titleAlpha = 0f,
        titleAlphaProvider = { appIconTitleProgress.value },
        pageColor = AppTheme.colorScheme.background,
    ) { chrome ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(96.dp),
            modifier = chrome.contentModifier()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                )
                .fillMaxSize(),
            // Top padding, not a top inset on the grid itself: the tiles have to
            // travel under the bar for the blur to have anything to frost.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarContentPadding(),
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(AppIcon.entries, key = { it.id }) { icon ->
                AppIconTile(
                    icon = icon,
                    selected = icon == selected,
                    onClick = { if (icon != selected) pending = icon },
                )
            }
        }
    }
}

@Composable
private fun AppIconTile(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(if (selected) 3.dp else 0.dp, label = "appIconBorder")
    val borderColor by animateColorAsState(
        if (selected) AppTheme.colorScheme.primary else Color.Transparent,
        label = "appIconBorderColor",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28))
                .background(colorResource(R.color.ic_launcher_tile))
                .border(borderWidth, borderColor, RoundedCornerShape(28)),
        ) {
            Image(
                painter = painterResource(icon.previewRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // The foreground is a 108dp canvas whose outer ring is mask overscan; scaling by
                // 108/72 leaves exactly the part a launcher mask actually reveals.
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = ADAPTIVE_OVERSCAN, scaleY = ADAPTIVE_OVERSCAN),
            )
        }
        Text(
            text = stringResource(icon.labelRes),
            style = AppTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
