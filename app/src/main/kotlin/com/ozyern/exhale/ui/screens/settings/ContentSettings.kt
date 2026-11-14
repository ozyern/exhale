/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFGlobeAsiaAustraliaFill
import com.composables.sfsymbols.monochrome.SFLeaf
import com.composables.sfsymbols.monochrome.SFGlobe
import com.composables.sfsymbols.monochrome.SFLine3Horizontal
import com.composables.sfsymbols.monochrome.SFMappinAndEllipse
import com.composables.sfsymbols.monochrome.SFPlayRectangle
import com.composables.sfsymbols.monochrome.SFEyeSlashFill
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSliderHorizontal3
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.ozyern.exhale.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ozyern.exhale.ui.ios.compat.Button
import com.ozyern.exhale.ui.ios.compat.AppTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import com.ozyern.exhale.ui.ios.compat.Slider
import com.ozyern.exhale.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import com.ozyern.exhale.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ContentCountryKey
import com.ozyern.exhale.constants.ContentLanguageKey
import com.ozyern.exhale.constants.SuggestionRegionKey
import com.ozyern.exhale.constants.SuggestionRegionSlugToName
import com.ozyern.exhale.ui.screens.search.suggestions.SuggestionRegionSheet
import com.ozyern.exhale.constants.CountryCodeToName
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.DataSaverEnabledKey
import com.ozyern.exhale.constants.HideVideoSongsKey
import com.ozyern.exhale.constants.HideYoutubeShortsKey
import com.ozyern.exhale.constants.LanguageCodeToName
import com.ozyern.exhale.constants.RandomizeHomeOrderKey
import com.ozyern.exhale.constants.ShowHomeMoodFiltersKey
import com.ozyern.exhale.constants.SYSTEM_DEFAULT
import com.ozyern.exhale.constants.ShowWrappedCardKey
import com.ozyern.exhale.ui.component.EnumDialog
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

import androidx.compose.runtime.mutableStateListOf
import com.music.innertube.YouTube
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scope = rememberCoroutineScope()

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (suggestionRegion, onSuggestionRegionChange) = rememberPreference(key = SuggestionRegionKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (dataSaverEnabled, onDataSaverEnabledChange) = rememberPreference(key = DataSaverEnabledKey, defaultValue = false)
    val (hideVideoSongs, onHideVideoSongsChange) = rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val (hideYoutubeShorts, onHideYoutubeShortsChange) = rememberPreference(key = HideYoutubeShortsKey, defaultValue = false)
    val (showWrappedCard, onShowWrappedCardChange) = rememberPreference(key = ShowWrappedCardKey, defaultValue = false)
    val (showMoodFilters, onShowMoodFiltersChange) = rememberPreference(
        key = ShowHomeMoodFiltersKey,
        defaultValue = true,
    )
    val (randomizeHomeOrder, onRandomizeHomeOrderChange) = rememberPreference(
        RandomizeHomeOrderKey,
        defaultValue = true
    )
    var showSuggestionSheet by rememberSaveable { mutableStateOf(false) }

    var showContentLanguageDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentLanguageDialog) {
        EnumDialog(
            onDismiss = { showContentLanguageDialog = false },
            onSelect = {
                onContentLanguageChange(it)
                showContentLanguageDialog = false
            },
            title = stringResource(R.string.content_language),
            current = contentLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    var showContentCountryDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showContentCountryDialog) {
        EnumDialog(
            onDismiss = { showContentCountryDialog = false },
            onSelect = {
                onContentCountryChange(it)
                showContentCountryDialog = false
            },
            title = stringResource(R.string.content_country),
            current = contentCountry,
            values = (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    if (showSuggestionSheet) {
        SuggestionRegionSheet(
            currentRegionSlug = suggestionRegion,
            onRegionSelected = { onSuggestionRegionChange(it) },
            onDismiss = { showSuggestionSheet = false }
        )
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.content),
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = 16.dp,
    ) {
        IosSettingsGroup(
            title = "Region & language",
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFGlobe),
                    title = { Text(stringResource(R.string.content_language)) },
                    description = {
                        Text(
                            LanguageCodeToName.getOrElse(contentLanguage) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentLanguageDialog = true }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFMappinAndEllipse),
                    title = { Text(stringResource(R.string.content_country)) },
                    description = {
                        Text(
                            CountryCodeToName.getOrElse(contentCountry) { stringResource(R.string.system_default) }
                        )
                    },
                    onClick = { showContentCountryDialog = true }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFGlobeAsiaAustraliaFill),
                    title = { Text("Suggestions Region") },
                    description = {
                        Text(
                            SuggestionRegionSlugToName.getOrElse(suggestionRegion) { "Global Charts" }
                        )
                    },
                    onClick = { showSuggestionSheet = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = "Filters & data",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.explicit),
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    trailingContent = {
                        Switch(
                            checked = hideExplicit,
                            onCheckedChange = onHideExplicitChange
                        )
                    },
                    onClick = { onHideExplicitChange(!hideExplicit) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFPlayRectangle),
                    title = { Text(stringResource(R.string.hide_video_songs)) },
                    trailingContent = {
                        Switch(
                            checked = hideVideoSongs,
                            onCheckedChange = onHideVideoSongsChange
                        )
                    },
                    onClick = { onHideVideoSongsChange(!hideVideoSongs) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFLeaf),
                    title = { Text(stringResource(R.string.data_saver_mode)) },
                    description = { Text(stringResource(R.string.data_saver_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = dataSaverEnabled,
                            onCheckedChange = onDataSaverEnabledChange
                        )
                    },
                    onClick = { onDataSaverEnabledChange(!dataSaverEnabled) }
                ),

                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFEyeSlashFill),
                    title = { Text(stringResource(R.string.hide_youtube_shorts)) },
                    trailingContent = {
                        Switch(
                            checked = hideYoutubeShorts,
                            onCheckedChange = onHideYoutubeShortsChange
                        )
                    },
                    onClick = { onHideYoutubeShortsChange(!hideYoutubeShorts) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = "Home feed",
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFShuffle),
                    title = { Text(stringResource(R.string.randomize_home_order)) },
                    description = { Text(stringResource(R.string.randomize_home_order_desc)) },
                    trailingContent = {
                        Switch(
                            checked = randomizeHomeOrder,
                            onCheckedChange = onRandomizeHomeOrderChange
                        )
                    },
                    onClick = { onRandomizeHomeOrderChange(!randomizeHomeOrder) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFLine3Horizontal),
                    title = { Text(stringResource(R.string.home_feed_order)) },
                    description = { Text(stringResource(R.string.home_feed_order_desc)) },
                    onClick = { navController.navigate("settings/home_feed_order") }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFSliderHorizontal3),
                    title = { Text(stringResource(R.string.show_mood_filters)) },
                    description = { Text(stringResource(R.string.show_mood_filters_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showMoodFilters,
                            onCheckedChange = onShowMoodFiltersChange
                        )
                    },
                    onClick = { onShowMoodFiltersChange(!showMoodFilters) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.advanced),
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFSliderHorizontal3),
                    title = { Text(stringResource(R.string.advanced)) },
                    description = { Text(stringResource(R.string.advanced_settings_desc)) },
                    onClick = { navController.navigate("settings/content/advanced") }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    
    }
}
