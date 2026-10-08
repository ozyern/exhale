/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.LiquidGlassIconButton
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.TopSearch
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val (searchPosition) = com.ozyern.exhale.utils.rememberEnumPreference(
        com.ozyern.exhale.constants.SettingsSearchPositionKey,
        com.ozyern.exhale.constants.SettingsSearchPosition.BOTTOM,
    )
    val searchAtTop = searchPosition == com.ozyern.exhale.constants.SettingsSearchPosition.TOP


    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isStorageGranted = result[storagePermission] == true || isStorageGranted
        if (notificationPermission != null) {
            isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
        }
    }

    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate = Updater.hasUpdate(latestVersionName, BuildConfig.VERSION_NAME)

    val resetSearch: () -> Unit = {
        isSearching = false
        query = TextFieldValue()
        focusManager.clearFocus()
    }

    val quickActions = buildQuickActions(navController, resetSearch)
    val integrationActions = buildIntegrationActions(navController, resetSearch)
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context, resetSearch)
    val internalItems = buildInternalItems(navController, resetSearch)

    val queryText = query.text.trim()
    val showSearchBar = isSearching || queryText.isNotBlank()

    val filteredQuickActions = filterQuickActions(quickActions, queryText)
    val filteredIntegrations = filterIntegrations(integrationActions, queryText)
    val filteredGroups = filterSettingsGroups(settingsGroups, queryText)
    val filteredInternalItems = filterInternalItems(internalItems, queryText)

    val hasSearchResults by remember(
        filteredQuickActions,
        filteredGroups,
        filteredIntegrations,
        filteredInternalItems,
    ) {
        derivedStateOf {
            filteredQuickActions.isNotEmpty() ||
                filteredGroups.isNotEmpty() ||
                filteredIntegrations.isNotEmpty() ||
                filteredInternalItems.isNotEmpty()
        }
    }

    val internalGroup = if (filteredInternalItems.isNotEmpty()) {
        SettingsGroup(
            title = stringResource(R.string.internal_subcategory_settings),
            items = filteredInternalItems,
        )
    } else null

    val contentState = SettingsContentState(
        quickActions = if (queryText.isBlank()) quickActions else filteredQuickActions,
        integrations = if (queryText.isBlank()) integrationActions else filteredIntegrations,
        groups = if (queryText.isBlank()) settingsGroups else filteredGroups,
        internalGroup = if (queryText.isNotBlank()) internalGroup else null,
        showPermissionBanner = shouldShowPermissionHint,
        showUpdateBanner = hasUpdate,
        latestVersion = latestVersionName,
        isSearchActive = queryText.isNotBlank(),
        hasSearchResults = hasSearchResults,
        onRequestPermission = {
            val toRequest = buildList {
                if (!isStorageGranted) add(storagePermission)
                if (!isNotificationGranted && notificationPermission != null) {
                    add(notificationPermission)
                }
            }
            if (toRequest.isNotEmpty()) {
                permissionLauncher.launch(toRequest.toTypedArray())
            }
        },
        onUpdateClick = { navController.navigate("settings/update") },
        onAboutClick = { navController.navigate("settings/about") },
        onSearchClick = { focusRequester.requestFocus() },
    )

    Scaffold(
        topBar = {
            SettingsLargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        // iOS large-title weight. The type scale supplies the geometric face
                        // and tight tracking; the header just asks for the heavy cut.
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    LiquidBackButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        icon = R.drawable.chevron_back,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // Transparent, so the album-art wash `SettingsPage` lays down is what you see behind
        // the groups.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // One list, filtered where it stands as you type — iOS Settings doesn't open a
            // separate search screen, it narrows the page you're on.
            AdaptiveSettingsLayout(
                state = contentState,
                listState = listState,
                // At the top, the list starts below the search bar as well as the title.
                topPadding = innerPadding.calculateTopPadding() + if (searchAtTop) SearchAtTopHeight else 0.dp,
                modifier = Modifier.fillMaxSize(),
            )

            // iOS's place for it by default: a capsule at the foot of the page, resting on the
            // dock. Or under the title, for those who'd rather have it there (Appearance >
            // Settings page). It rides with the title as that collapses.
            SettingsBottomSearch(
                query = query,
                onQueryChange = { query = it; isSearching = true },
                onClear = { resetSearch() },
                focusRequester = focusRequester,
                atTop = searchAtTop,
                modifier = if (searchAtTop) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding())
                } else {
                    Modifier.align(Alignment.BottomCenter)
                },
            )
        }
    }
}


/** The capsule (50dp) and its padding above and below, when it sits under the title. */
private val SearchAtTopHeight = 62.dp
