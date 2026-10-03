package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.asPaddingValues
import com.ozyern.exhale.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import com.ozyern.exhale.LocalPlayerAwareWindowInsets

enum class SettingsLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun resolveLayoutMode(): SettingsLayoutMode {
    val windowInfo = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        windowInfo.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            SettingsLayoutMode.EXPANDED
        windowInfo.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            SettingsLayoutMode.MEDIUM
        else ->
            SettingsLayoutMode.COMPACT
    }
}

data class SettingsContentState(
    val quickActions: List<SettingsQuickAction>,
    val integrations: List<SettingsIntegrationAction>,
    val groups: List<SettingsGroup>,
    val internalGroup: SettingsGroup?,
    val showPermissionBanner: Boolean,
    val showUpdateBanner: Boolean,
    val latestVersion: String,
    val isSearchActive: Boolean,
    val hasSearchResults: Boolean,
    val onRequestPermission: () -> Unit,
    val onUpdateClick: () -> Unit,
    val onAboutClick: () -> Unit,
    val onSearchClick: () -> Unit,
)

@Composable
fun AdaptiveSettingsLayout(
    state: SettingsContentState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
) {
    val layoutMode = resolveLayoutMode()

    // One entrance for the whole page, not five staggered ones.
    //
    // This used to flip five booleans in sequence off a `LaunchedEffect`, each gating its own
    // `AnimatedVisibility`. Two problems, and they compound. Visually, every visit to Settings
    // played a ~300ms cascade of sections dropping in one after another, which reads as the page
    // struggling to assemble itself rather than as polish. Mechanically, the entrances used
    // `expandVertically` and `slideInVertically` — both of which animate LAYOUT, not just paint —
    // so each section forced a measure and layout pass over the LazyColumn on every frame of its
    // own entrance, five of them overlapping, at exactly the moment the screen is inflating. That
    // is the settings-page stutter, and it is not a blur or a shader: it is five relayouts a frame.
    //
    // The flags are kept (the layouts below take them as parameters, and search still toggles
    // sections independently) but they all now flip together on the first frame, and the
    // transitions they gate are pure fades. The arrival itself is one alpha ramp applied to the
    // whole list in the draw phase — no layout, no recomposition, one render-thread property.
    var sectionsVisible by remember { mutableStateOf(false) }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sectionsVisible = true
        entrance.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }
    val heroVisible = sectionsVisible
    val bannerVisible = sectionsVisible
    val quickActionsVisible = sectionsVisible
    val integrationsVisible = sectionsVisible
    val categoriesVisible = sectionsVisible

    val quickActionColumns = when (layoutMode) {
        SettingsLayoutMode.COMPACT -> SettingsDimensions.CompactColumns
        SettingsLayoutMode.MEDIUM -> SettingsDimensions.MediumColumns
        SettingsLayoutMode.EXPANDED -> SettingsDimensions.ExpandedColumns
    }

    // Read inside `graphicsLayer`, so the ramp never recomposes or relayouts anything under it.
    val entranceModifier = modifier.graphicsLayer { alpha = entrance.value }

    when (layoutMode) {
        SettingsLayoutMode.COMPACT -> {
            CompactSettingsLayout(
                state = state,
                listState = listState,
                quickActionColumns = quickActionColumns,
                heroVisible = heroVisible,
                bannerVisible = bannerVisible,
                quickActionsVisible = quickActionsVisible,
                integrationsVisible = integrationsVisible,
                categoriesVisible = categoriesVisible,
                topPadding = topPadding,
                modifier = entranceModifier,
            )
        }
        SettingsLayoutMode.MEDIUM -> {
            MediumSettingsLayout(
                state = state,
                quickActionColumns = quickActionColumns,
                heroVisible = heroVisible,
                bannerVisible = bannerVisible,
                quickActionsVisible = quickActionsVisible,
                integrationsVisible = integrationsVisible,
                categoriesVisible = categoriesVisible,
                topPadding = topPadding,
                modifier = entranceModifier,
            )
        }
        SettingsLayoutMode.EXPANDED -> {
            ExpandedSettingsLayout(
                state = state,
                quickActionColumns = quickActionColumns,
                heroVisible = heroVisible,
                bannerVisible = bannerVisible,
                quickActionsVisible = quickActionsVisible,
                integrationsVisible = integrationsVisible,
                categoriesVisible = categoriesVisible,
                topPadding = topPadding,
                modifier = entranceModifier,
            )
        }
    }
}

@Composable
private fun CompactSettingsLayout(
    state: SettingsContentState,
    listState: LazyListState,
    quickActionColumns: Int,
    heroVisible: Boolean,
    bannerVisible: Boolean,
    quickActionsVisible: Boolean,
    integrationsVisible: Boolean,
    categoriesVisible: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val pad = SettingsDimensions.ScreenHorizontalPadding
    val spacing = SettingsDimensions.SectionSpacing

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            ),
        contentPadding = PaddingValues(top = topPadding, bottom = settingsBottomClearance()),
    ) {
        // Search lives at the bottom of the screen now, as it does in iOS Settings.
        item(key = "hero") {
            AnimatedVisibility(
                visible = heroVisible,
                enter = fadeIn(SettingsAnimations.entranceSpring()),
            ) {
                SettingsProfileHeader(
                    onClick = state.onAboutClick,
                    modifier = Modifier
                        .padding(horizontal = pad)
                        .padding(bottom = spacing),
                )
            }
        }

        if (!state.isSearchActive) {
            item(key = "permission") {
                AnimatedVisibility(
                    visible = bannerVisible && state.showPermissionBanner,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                    exit = fadeOut(tween(300)),
                ) {
                    SettingsPermissionBanner(
                        onRequestPermission = state.onRequestPermission,
                        modifier = Modifier
                            .padding(horizontal = pad)
                            .padding(bottom = spacing),
                    )
                }
            }

            item(key = "update") {
                AnimatedVisibility(
                    visible = bannerVisible && state.showUpdateBanner,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                    exit = fadeOut(tween(300)),
                ) {
                    SettingsUpdateBanner(
                        latestVersion = state.latestVersion,
                        onClick = state.onUpdateClick,
                        modifier = Modifier
                            .padding(horizontal = pad)
                            .padding(bottom = spacing),
                    )
                }
            }
        }

        if (state.quickActions.isNotEmpty()) {
            item(key = "quickActions") {
                AnimatedVisibility(
                    visible = quickActionsVisible,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                ) {
                    // Rows in a group, as iOS Settings lists everything — never a wall of tiles.
                    SettingsGroupCard(
                        group = SettingsGroup(
                            title = stringResource(R.string.settings_group_essentials),
                            items = state.quickActions.map { SettingsItem(icon = it.icon, title = it.label, onClick = it.onClick) },
                        ),
                        modifier = Modifier
                            .padding(horizontal = pad)
                            .padding(bottom = spacing),
                    )
                }
            }
        }

        if (state.integrations.isNotEmpty()) {
            item(key = "integrations") {
                AnimatedVisibility(
                    visible = integrationsVisible,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                ) {
                    SettingsGroupCard(
                        group = SettingsGroup(
                            title = stringResource(R.string.integrations),
                            items = state.integrations.map { SettingsItem(icon = it.icon, title = it.label, onClick = it.onClick) },
                        ),
                        modifier = Modifier
                            .padding(horizontal = pad)
                            .padding(bottom = spacing),
                    )
                }
            }
        }

        if (state.isSearchActive && !state.hasSearchResults) {
            item(key = "empty") {
                Spacer(modifier = Modifier.height(24.dp))
                SettingsSearchEmpty(
                    modifier = Modifier.padding(horizontal = pad),
                )
            }
        } else {
            if (state.internalGroup != null && state.internalGroup.items.isNotEmpty()) {
                item(key = "internalSearchResults") {
                    SettingsGroupCard(
                        group = state.internalGroup,
                        modifier = Modifier
                            .padding(horizontal = pad)
                            .padding(bottom = spacing),
                    )
                }
            }

            // No per-card entrance. Each group used to slide in behind an
            // `index * StaggerDelayPerItem` delay, so on a page with eight groups the last
            // one arrived more than half a second after the first — and `slideInVertically`
            // relayouts the item every frame it runs, so the whole cascade was overlapping
            // layout passes. The page-level alpha ramp in `AdaptiveSettingsLayout` covers
            // the arrival for nothing.
            items(
                count = state.groups.size,
                key = { state.groups[it].title },
            ) { index ->
                val group = state.groups[index]
                SettingsGroupCard(
                    group = group,
                    modifier = Modifier
                        .padding(horizontal = pad)
                        .padding(bottom = spacing),
                )
            }
        }
    }
}

@Composable
private fun MediumSettingsLayout(
    state: SettingsContentState,
    quickActionColumns: Int,
    heroVisible: Boolean,
    bannerVisible: Boolean,
    quickActionsVisible: Boolean,
    integrationsVisible: Boolean,
    categoriesVisible: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val pad = SettingsDimensions.ScreenHorizontalPadding
    val spacing = SettingsDimensions.SectionSpacing

    Row(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(pad),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(SettingsDimensions.MediumPaneLeftWeight)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = topPadding, bottom = settingsBottomClearance()),
        ) {
            item(key = "hero") {
                AnimatedVisibility(
                    visible = heroVisible,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                ) {
                    SettingsProfileHeader(
                        onClick = state.onAboutClick,
                        modifier = Modifier.padding(top = 4.dp, bottom = spacing),
                    )
                }
            }

            if (!state.isSearchActive) {
                item(key = "permission") {
                    AnimatedVisibility(
                        visible = bannerVisible && state.showPermissionBanner,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                        exit = fadeOut(tween(300)),
                    ) {
                        SettingsPermissionBanner(
                            onRequestPermission = state.onRequestPermission,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }

                item(key = "update") {
                    AnimatedVisibility(
                        visible = bannerVisible && state.showUpdateBanner,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                        exit = fadeOut(tween(300)),
                    ) {
                        SettingsUpdateBanner(
                            latestVersion = state.latestVersion,
                            onClick = state.onUpdateClick,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }

            if (state.quickActions.isNotEmpty()) {
                item(key = "quickActions") {
                    AnimatedVisibility(
                        visible = quickActionsVisible,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                    ) {
                        SettingsQuickActionsSection(
                            actions = state.quickActions,
                            columns = 2,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }

            if (state.integrations.isNotEmpty()) {
                item(key = "integrations") {
                    AnimatedVisibility(
                        visible = integrationsVisible,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                    ) {
                        SettingsIntegrationsSection(
                            integrations = state.integrations,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(SettingsDimensions.MediumPaneRightWeight)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = topPadding, bottom = settingsBottomClearance()),
        ) {
            if (state.isSearchActive && !state.hasSearchResults) {
                item(key = "empty") {
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsSearchEmpty()
                }
            } else {
                if (state.internalGroup != null && state.internalGroup.items.isNotEmpty()) {
                    item(key = "internalSearchResults") {
                        SettingsGroupCard(
                            group = state.internalGroup,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }

                items(
                    count = state.groups.size,
                    key = { state.groups[it].title },
                ) { index ->
                    SettingsGroupCard(
                        group = state.groups[index],
                        modifier = Modifier.padding(bottom = spacing),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedSettingsLayout(
    state: SettingsContentState,
    quickActionColumns: Int,
    heroVisible: Boolean,
    bannerVisible: Boolean,
    quickActionsVisible: Boolean,
    integrationsVisible: Boolean,
    categoriesVisible: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val pad = SettingsDimensions.ScreenHorizontalPadding
    val spacing = SettingsDimensions.SectionSpacing

    Row(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(pad),
    ) {
        LazyColumn(
            modifier = Modifier
                .width(SettingsDimensions.ExpandedListPaneWidth)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = topPadding, bottom = settingsBottomClearance()),
        ) {
            item(key = "hero") {
                AnimatedVisibility(
                    visible = heroVisible,
                    enter = fadeIn(SettingsAnimations.entranceSpring()),
                ) {
                    SettingsProfileHeader(
                        onClick = state.onAboutClick,
                        modifier = Modifier.padding(top = 4.dp, bottom = spacing),
                    )
                }
            }

            if (!state.isSearchActive) {
                item(key = "permission") {
                    AnimatedVisibility(
                        visible = bannerVisible && state.showPermissionBanner,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                        exit = fadeOut(tween(300)),
                    ) {
                        SettingsPermissionBanner(
                            onRequestPermission = state.onRequestPermission,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }

                item(key = "update") {
                    AnimatedVisibility(
                        visible = bannerVisible && state.showUpdateBanner,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                        exit = fadeOut(tween(300)),
                    ) {
                        SettingsUpdateBanner(
                            latestVersion = state.latestVersion,
                            onClick = state.onUpdateClick,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }

            if (state.quickActions.isNotEmpty()) {
                item(key = "quickActions") {
                    AnimatedVisibility(
                        visible = quickActionsVisible,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                    ) {
                        SettingsQuickActionsSection(
                            actions = state.quickActions,
                            columns = 2,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }

            if (state.integrations.isNotEmpty()) {
                item(key = "integrations") {
                    AnimatedVisibility(
                        visible = integrationsVisible,
                        enter = fadeIn(SettingsAnimations.entranceSpring()),
                    ) {
                        SettingsIntegrationsSection(
                            integrations = state.integrations,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = topPadding, bottom = settingsBottomClearance()),
        ) {
            if (state.isSearchActive && !state.hasSearchResults) {
                item(key = "empty") {
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsSearchEmpty()
                }
            } else {
                if (state.internalGroup != null && state.internalGroup.items.isNotEmpty()) {
                    item(key = "internalSearchResults") {
                        SettingsGroupCard(
                            group = state.internalGroup,
                            modifier = Modifier.padding(bottom = spacing),
                        )
                    }
                }

                items(
                    count = state.groups.size,
                    key = { state.groups[it].title },
                ) { index ->
                    SettingsGroupCard(
                        group = state.groups[index],
                        modifier = Modifier.padding(bottom = spacing),
                    )
                }
            }
        }
    }
}

/** Space under the last group so the floating search field never covers it. */
internal val SettingsBottomSearchClearance = 104.dp

/**
 * How much room the list leaves at its foot.
 *
 * The flat 104dp assumed the search capsule was the only thing floating over the bottom of the
 * page. With a song playing there is a 64dp mini player above it as well, so the last group in
 * every settings page slid under the two of them - a row half-hidden behind a translucent capsule,
 * which is what "the search bar is sitting on Storage" was. The player-aware insets already know
 * the height of that chrome, so the clearance follows it instead of guessing.
 */
@Composable
internal fun settingsBottomClearance(): Dp =
    SettingsBottomSearchClearance +
        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
