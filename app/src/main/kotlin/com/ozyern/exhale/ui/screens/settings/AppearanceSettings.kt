/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.settings

import androidx.compose.ui.unit.sp
import com.ozyern.exhale.utils.AppLanguage
import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroup
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.liquid.LiquidSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.ozyern.exhale.utils.rememberAppIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.CanvasSource
import com.ozyern.exhale.constants.CanvasSourceKey
import com.ozyern.exhale.constants.ChipSortTypeKey
import com.ozyern.exhale.constants.DarkModeKey
import com.ozyern.exhale.constants.DefaultOpenTabKey
import com.ozyern.exhale.constants.DynamicThemeKey
import com.ozyern.exhale.constants.GridItemSize
import com.ozyern.exhale.constants.GridItemsSizeKey
import com.ozyern.exhale.constants.LibraryFilter
import com.ozyern.exhale.constants.LyricsClickKey
import com.ozyern.exhale.constants.LyricsScrollKey
import com.ozyern.exhale.constants.LyricsTextPositionKey
import com.ozyern.exhale.constants.PlayerDesignStyle
import com.ozyern.exhale.constants.PlayerDesignStyleKey
import com.ozyern.exhale.constants.UseNewMiniPlayerDesignKey
import com.ozyern.exhale.constants.PlayerBackgroundStyle
import com.ozyern.exhale.constants.PlayerBackgroundStyleKey
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.constants.RandomThemeOnStartupKey
import com.ozyern.exhale.constants.SabrinaThemeKey
import com.ozyern.exhale.constants.UiScaleKey
import com.ozyern.exhale.utils.UiScaleDefault
import com.ozyern.exhale.constants.UseSystemFontKey
import com.ozyern.exhale.constants.PlayerButtonsStyle
import com.ozyern.exhale.constants.PlayerButtonsStyleKey
import com.ozyern.exhale.constants.LyricsAnimationStyleKey
import com.ozyern.exhale.constants.LyricsAnimationStyle
import com.ozyern.exhale.constants.LyricsLayout
import com.ozyern.exhale.constants.LyricsLayoutKey
import com.ozyern.exhale.constants.ArtistNameFontKey
import com.ozyern.exhale.ui.screens.artist.ArtistNameFont
import com.ozyern.exhale.constants.LyricsTextSizeKey
import com.ozyern.exhale.constants.LyricsLineSpacingKey
import com.ozyern.exhale.constants.SliderStyle
import com.ozyern.exhale.constants.SliderStyleKey
import com.ozyern.exhale.constants.SlimNavBarKey
import com.ozyern.exhale.constants.ShowLikedPlaylistKey
import com.ozyern.exhale.constants.ShowDownloadedPlaylistKey
import com.ozyern.exhale.constants.ShowHomeCategoryChipsKey
import com.ozyern.exhale.constants.ShowTopPlaylistKey
import com.ozyern.exhale.constants.ShowCachedPlaylistKey
import com.ozyern.exhale.constants.ShowTagsInLibraryKey
import com.ozyern.exhale.constants.SwipeThumbnailKey
import com.ozyern.exhale.constants.SwipeSensitivityKey
import com.ozyern.exhale.constants.SwipeToSongKey
import com.ozyern.exhale.constants.HidePlayerThumbnailKey
import com.ozyern.exhale.constants.ClassicPlayerKey
import com.ozyern.exhale.constants.ReactiveBackdropKey
import com.ozyern.exhale.constants.ExhaleCanvasKey
import com.ozyern.exhale.constants.ExhaleCanvasKey
import com.ozyern.exhale.constants.ThumbnailCornerRadiusKey
import com.ozyern.exhale.constants.CropThumbnailToSquareKey
import com.ozyern.exhale.constants.DisableBlurKey
import com.ozyern.exhale.constants.EnableHapticFeedbackKey
import com.ozyern.exhale.constants.LiquidGlassNavBarKey
import com.ozyern.exhale.constants.PlayerFullscreenKey
import com.ozyern.exhale.constants.UseLyricsV2Key
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.EnumListPreference
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.ListPreference
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.component.ThumbnailCornerRadiusSelectorButton
import com.ozyern.exhale.ui.player.StyledPlaybackSlider
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.roundToInt
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (sabrinaTheme, onSabrinaThemeChange) = rememberPreference(
        SabrinaThemeKey,
        defaultValue = false
    )
    val (randomThemeOnStartup, onRandomThemeOnStartupChange) = rememberPreference(
        RandomThemeOnStartupKey,
        defaultValue = false
    )
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val (dockStyle, onDockStyleChange) = rememberEnumPreference(
        com.ozyern.exhale.constants.DockStyleKey,
        defaultValue = com.ozyern.exhale.constants.DockStyle.LIQUID
    )
    val (dockLabels, onDockLabelsChange) = rememberPreference(
        com.ozyern.exhale.constants.DockLabelsKey,
        defaultValue = true
    )
    val (dockCompact, onDockCompactChange) = rememberPreference(
        com.ozyern.exhale.constants.DockCompactKey,
        defaultValue = false
    )
    val (dockGlow, onDockGlowChange) = rememberPreference(
        com.ozyern.exhale.constants.DockGlowKey,
        defaultValue = true
    )
    val (playerDesignStyle, onPlayerDesignStyleChange) = rememberEnumPreference(
        PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V8
    )
    val (useNewMiniPlayerDesign, onUseNewMiniPlayerDesignChange) = rememberPreference(
        UseNewMiniPlayerDesignKey,
        defaultValue = true
    )
    val (useNewLibraryDesign, onUseNewLibraryDesignChange) = rememberPreference(
        key = com.ozyern.exhale.constants.UseNewLibraryDesignKey,
        defaultValue = true
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(
        HidePlayerThumbnailKey,
        defaultValue = false
    )
    val (liquidGlassPlayer) = rememberPreference(com.ozyern.exhale.constants.LiquidGlassPlayerKey, false)
    val (classicPlayer, onClassicPlayerChange) = rememberPreference(
        ClassicPlayerKey,
        defaultValue = false
    )
    val (animatedCovers, onAnimatedCoversChange) = rememberPreference(
        ExhaleCanvasKey,
        defaultValue = true
    )
    val (reactiveBackdrop, onReactiveBackdropChange) = rememberPreference(
        ReactiveBackdropKey,
        defaultValue = true
    )
    val (canvasSource, setCanvasSource) = rememberEnumPreference(
        key = CanvasSourceKey,
        defaultValue = CanvasSource.AUTO,
    )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) = rememberPreference(
        key = ThumbnailCornerRadiusKey,
        defaultValue = 16f // default dp
    )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) = rememberPreference(
        CropThumbnailToSquareKey,
        defaultValue = false
    )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (liquidGlass, onLiquidGlassChange) = rememberPreference(LiquidGlassNavBarKey, defaultValue = true)
    val (useSystemFont, onUseSystemFontChange) = rememberPreference(UseSystemFontKey, defaultValue = false)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) = rememberEnumPreference(
        PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.LEFT
    )
    val (lyricsAnimation, onLyricsAnimationChange) = rememberEnumPreference<LyricsAnimationStyle>(
    key = LyricsAnimationStyleKey,
    defaultValue = LyricsAnimationStyle.APPLE
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsLayout, onLyricsLayoutChange) = rememberEnumPreference(LyricsLayoutKey, defaultValue = LyricsLayout.GLASS)
    val (artistNameFontName, onArtistNameFontChange) = rememberPreference(ArtistNameFontKey, defaultValue = ArtistNameFont.CLASSIC.name)
    val artistNameFont = ArtistNameFont.of(artistNameFontName) ?: ArtistNameFont.CLASSIC
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (uiScale) = rememberPreference(UiScaleKey, defaultValue = UiScaleDefault)

    // Named on the row so the setting reads as answered rather than as a door. Comes from the
    // system rather than the preference — see AppIconPack.current for why the two can disagree.
    val activeIconPack = rememberAppIconPack()
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (useLyricsV2, onUseLyricsV2Change) = rememberPreference(UseLyricsV2Key, defaultValue = true)

    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.Simple
    )
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    val (slimNav, onSlimNavChange) = rememberPreference(
        SlimNavBarKey,
        defaultValue = false
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )
    val (showTagsInLibrary, onShowTagsInLibraryChange) = rememberPreference(
        ShowTagsInLibraryKey,
        defaultValue = true
    )
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) = rememberPreference(
        ShowHomeCategoryChipsKey,
        defaultValue = true
    )

    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    val (playerFullscreen, onPlayerFullscreenChange) = rememberPreference(
        PlayerFullscreenKey,
        defaultValue = false
    )

    val (hapticEnabled, onHapticEnabledChange) = rememberPreference(
        EnableHapticFeedbackKey,
        defaultValue = true
    )


    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSliderOptionDialog) {
        val sliderStyles = remember {
            listOf(
                SliderStyle.Standard,
                SliderStyle.Wavy,
                SliderStyle.Thick,
                SliderStyle.Circular,
                SliderStyle.Simple
            )
        }
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sliderStyles.chunked(3).forEach { styleRow ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        styleRow.forEach { style ->
                            SliderStyleOptionCard(
                                sliderStyle = style,
                                selected = sliderStyle == style,
                                onClick = {
                                    onSliderStyleChange(style)
                                    showSliderOptionDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - styleRow.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.theme),
        )
        PreferenceGroup {
            // Sabrina sits first because it beats everything under it, and the things it beats are
            // hidden while it is on rather than left switchable and inert. A toggle that visibly moves
            // and changes nothing is worse than a toggle that is not there.
            SwitchPreference(
                title = { Text(stringResource(R.string.sabrina_theme)) },
                description = stringResource(R.string.sabrina_theme_desc),
                icon = { Icon(painterResource(R.drawable.palette), null) },
                checked = sabrinaTheme,
                onCheckedChange = onSabrinaThemeChange,
            )

            AnimatedVisibility(visible = !sabrinaTheme) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                    icon = { Icon(painterResource(R.drawable.palette), null) },
                    checked = dynamicTheme,
                    onCheckedChange = onDynamicThemeChange,
                )
            }

            SwitchPreference(
                title = { Text(stringResource(R.string.player_fullscreen)) },
                icon = { Icon(painterResource(R.drawable.fullscreen), null) },
                checked = playerFullscreen,
                onCheckedChange = onPlayerFullscreenChange,
            )


            AnimatedVisibility(visible = !sabrinaTheme && (!dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.random_theme_on_startup)) },
                    description = stringResource(R.string.random_theme_on_startup_desc),
                    icon = { Icon(painterResource(R.drawable.shuffle), null) },
                    checked = randomThemeOnStartup,
                    onCheckedChange = onRandomThemeOnStartupChange,
                )
            }

            AnimatedVisibility(visible = !sabrinaTheme && (!dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.color_palette)) },
                    description = stringResource(R.string.customize_theme_colors),
                    icon = { Icon(painterResource(R.drawable.format_paint), null) },
                    onClick = { navController.navigate("settings/appearance/palette_picker") }
                )
            }

            EnumListPreference(
                title = { Text(stringResource(R.string.dark_theme)) },
                icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                selectedValue = darkMode,
                onValueSelected = onDarkModeChange,
                valueText = {
                    when (it) {
                        DarkMode.ON -> stringResource(R.string.dark_theme_on)
                        DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                        DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                    }
                },
            )

            // AMOLED black: only meaningful when the app can be dark, and Sabrina keeps its own
            // warm neutrals, so the switch is hidden rather than left there doing nothing.
            AnimatedVisibility(visible = darkMode != DarkMode.OFF && !sabrinaTheme) {
                SwitchPreference(
                    title = { Text("AMOLED black") },
                    description = "Pure black backgrounds in dark theme. Easier on OLED screens and on the battery",
                    icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                    checked = pureBlack,
                    onCheckedChange = onPureBlackChange,
                )
            }

            // True Blacks (AMOLED) is enforced ON by default — toggle intentionally removed.

            SwitchPreference(
                title = { Text(stringResource(R.string.use_system_font)) },
                description = stringResource(R.string.use_system_font_desc),
                icon = { Icon(painterResource(R.drawable.text_fields), null) },
                checked = useSystemFont,
                onCheckedChange = onUseSystemFontChange,
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.display),
        )
        PreferenceGroup {
            // Interface scale.
            //
            // Android already ships two size dials, and this is neither of them: Font Size grows type
            // inside a layout that stays put, and Display Size is buried three levels into system
            // settings and moves every app at once. Plenty of people want this app bigger — reading a
            // track list at arm's length, or on a tablet where a phone-tuned layout leaves half the
            // screen empty — and plenty want it smaller to fit more on screen. That is an app setting.
            //
            // It gets a page rather than a dialog because what it changes is the whole app, and a
            // dialog can only show you a corner of it. See UiScaleScreen.
            PreferenceEntry(
                title = { Text(stringResource(R.string.ui_scale)) },
                description = stringResource(R.string.ui_scale_desc) + " • " + uiScaleLabel(uiScale),
                icon = { Icon(painterResource(R.drawable.format_size), null) },
                onClick = { navController.navigate("settings/appearance/ui_scale") }
            )

            // Language.
            //
            // The system owns the setting - `LocaleManager`, which the phone also exposes in its
            // own app-info screen - so this row only chooses; nothing here has to survive a
            // restart or be re-applied at launch. Marked Beta because the translations behind it
            // are partial: choosing one gets you a translated app with English where the strings
            // have not landed yet, and saying so is better than letting someone find out.
            val context = LocalContext.current
            var language by remember { mutableStateOf(AppLanguage.current(context)) }
            ListPreference(
                title = { BetaTitle(stringResource(R.string.app_language)) },
                icon = { Icon(painterResource(R.drawable.language), null) },
                selectedValue = language,
                values = listOf(AppLanguage.SYSTEM) + AppLanguage.SUPPORTED.map { it.first },
                valueText = { tag ->
                    AppLanguage.labelFor(tag) ?: stringResource(R.string.app_language_system)
                },
                onValueSelected = { tag ->
                    language = tag
                    AppLanguage.apply(context, tag)
                    // Redraw with the new strings now.
                    //
                    // The platform does recreate the activity for a locale change, but it does it
                    // on its own schedule and a Compose tree that is already composed keeps the
                    // resources it resolved at composition. Asking directly is what makes picking
                    // a language *do* something instead of appearing to do nothing until the next
                    // cold start.
                    (context as? android.app.Activity)?.recreate()
                },
            )

            // App icon.
            //
            // A page, not a dialog: the thing being chosen is a picture, and two thumbnails squeezed
            // side by side under a warning is not enough surface to choose from. See AppIconScreen.
            PreferenceEntry(
                title = { Text(stringResource(R.string.app_icon)) },
                description = stringResource(R.string.app_icon_desc) + " • " + stringResource(activeIconPack.labelRes),
                icon = { Icon(painterResource(R.drawable.palette), null) },
                onClick = { navController.navigate("settings/appearance/app_icon") }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.player),
        )
        PreferenceGroup {

            // When custom background is selected, show a direct link to customize it
            if (playerBackground == PlayerBackgroundStyle.CUSTOM) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.customized_background)) },
                    icon = { Icon(painterResource(R.drawable.image), null) },
                    onClick = { navController.navigate("customize_background") }
                )
            }

            SwitchPreference(
                title = { Text("Music-reactive player") },
                description = "The player's colours breathe with the song and light up on the beat",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = reactiveBackdrop,
                onCheckedChange = onReactiveBackdropChange
            )

            SwitchPreference(
                title = { Text("Animated covers") },
                description = "Play a song's motion artwork where it has one",
                icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                checked = animatedCovers,
                onCheckedChange = onAnimatedCoversChange
            )

            SwitchPreference(
                title = { Text(stringResource(R.string.hide_player_thumbnail)) },
                description = stringResource(R.string.hide_player_thumbnail_desc),
                icon = { Icon(painterResource(R.drawable.hide_image), null) },
                checked = hidePlayerThumbnail,
                onCheckedChange = onHidePlayerThumbnailChange
            )

            PreferenceGroupDivider()
            ListPreference(
                title = { Text("Canvas source") },
                icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                selectedValue = canvasSource,
                values = CanvasSource.entries,
                valueText = { source ->
                    when (source) {
                        CanvasSource.AUTO -> "Automatic"
                        CanvasSource.APPLE_MUSIC -> "Apple Music"
                        CanvasSource.TIDAL -> "Tidal"
                    }
                },
                onValueSelected = setCanvasSource,
            )

            PreferenceGroupDivider()
            ListPreference(
                title = { Text("Lyrics layout") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                selectedValue = lyricsLayout,
                values = LyricsLayout.entries,
                valueText = {
                    when (it) {
                        LyricsLayout.GLASS -> "Liquid glass"
                        LyricsLayout.CLASSIC -> "Classic"
                    }
                },
                onValueSelected = onLyricsLayoutChange,
            )

            PreferenceGroupDivider()
            com.ozyern.exhale.ui.screens.artist.ArtistNameFontPicker(
                selected = artistNameFont,
                onSelect = { onArtistNameFontChange(it.name) },
            )


            ThumbnailCornerRadiusSelectorButton(
                modifier = Modifier.padding(16.dp),
                onRadiusSelected = { selectedRadius ->
                    Timber.tag("Thumbnail").d("Radius Selector: $selectedRadius")
                }
            )

            SwitchPreference(
                title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                description = stringResource(R.string.crop_thumbnail_to_square_desc),
                icon = { Icon(painterResource(R.drawable.image), null) },
                checked = cropThumbnailToSquare,
                onCheckedChange = onCropThumbnailToSquareChange
            )


            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text(stringResource(R.string.player_buttons_style)) },
                icon = { Icon(painterResource(R.drawable.palette), null) },
                selectedValue = playerButtonsStyle,
                onValueSelected = onPlayerButtonsStyleChange,
                valueText = {
                    when (it) {
                        PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                        PlayerButtonsStyle.SECONDARY -> stringResource(R.string.secondary_color_style)
                    }
                },
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                icon = { Icon(painterResource(R.drawable.swipe), null) },
                checked = swipeThumbnail,
                onCheckedChange = onSwipeThumbnailChange,
            )

            AnimatedVisibility(swipeThumbnail) {
                var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }
            
                if (showSensitivityDialog) {
                    var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }
                
                    DefaultDialog(
                        onDismiss = { 
                            tempSensitivity = swipeSensitivity
                            showSensitivityDialog = false 
                        },
                        buttons = {
                            TextButton(
                                onClick = { 
                                    tempSensitivity = 0.73f
                                }
                            ) {
                                Text(stringResource(R.string.reset))
                            }
                        
                            Spacer(modifier = Modifier.weight(1f))
                        
                            TextButton(
                                onClick = { 
                                    tempSensitivity = swipeSensitivity
                                    showSensitivityDialog = false 
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = { 
                                    onSwipeSensitivityChange(tempSensitivity)
                                    showSensitivityDialog = false 
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.swipe_sensitivity),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
    
                            Text(
                                text = stringResource(R.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
    
                            LiquidSlider(
                                value = tempSensitivity,
                                onValueChange = { tempSensitivity = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            
                PreferenceEntry(
                    title = { Text(stringResource(R.string.swipe_sensitivity)) },
                    description = stringResource(R.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                    icon = { Icon(painterResource(R.drawable.tune), null) },
                    onClick = { showSensitivityDialog = true }
                )
            }
        }


        // The open dock's tabs. The folded dock (a song playing, page scrolled) is the same in
        // every style, so these only describe the bar you see at the top of a page.
        PreferenceGroupTitle(
            title = "Dock",
        )
        PreferenceGroup {
            EnumListPreference(
                title = { Text("Dock style") },
                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                selectedValue = dockStyle,
                onValueSelected = onDockStyleChange,
                valueText = {
                    when (it) {
                        com.ozyern.exhale.constants.DockStyle.LIQUID -> "Liquid glass"
                        com.ozyern.exhale.constants.DockStyle.JELLY -> "Jelly"
                    }
                },
            )

            // The rest belong to the jelly dock, and are hidden rather than left inert under the
            // liquid one.
            AnimatedVisibility(dockStyle == com.ozyern.exhale.constants.DockStyle.JELLY) {
                Column {
                    PreferenceGroupDivider()
                    SwitchPreference(
                        title = { Text("Tab labels") },
                        description = "Names under the icons",
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        checked = dockLabels,
                        onCheckedChange = onDockLabelsChange,
                    )

                    PreferenceGroupDivider()
                    SwitchPreference(
                        title = { Text("Compact dock") },
                        description = "A shorter dock with smaller icons",
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        checked = dockCompact,
                        onCheckedChange = onDockCompactChange,
                    )

                    PreferenceGroupDivider()
                    SwitchPreference(
                        title = { Text("Touch glow") },
                        description = "A light under your finger while you drag between tabs",
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        checked = dockGlow,
                        onCheckedChange = onDockGlowChange,
                    )
                }
            }
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.misc),
        )
        PreferenceGroup {
            EnumListPreference(
                title = { Text(stringResource(R.string.default_open_tab)) },
                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                selectedValue = defaultOpenTab,
                onValueSelected = onDefaultOpenTabChange,
                valueText = {
                    when (it) {
                        NavigationTab.HOME -> stringResource(R.string.home)
                        NavigationTab.SEARCH -> stringResource(R.string.search)
                        NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                    }
                },
            )

            PreferenceGroupDivider()
            ListPreference(
                title = { Text(stringResource(R.string.default_lib_chips)) },
                icon = { Icon(painterResource(R.drawable.tab), null) },
                selectedValue = defaultChip,
                values = listOf(
                    LibraryFilter.LIBRARY, LibraryFilter.PLAYLISTS, LibraryFilter.SONGS,
                    LibraryFilter.ALBUMS, LibraryFilter.ARTISTS
                ),
                valueText = {
                    when (it) {
                        LibraryFilter.SONGS -> stringResource(R.string.songs)
                        LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                        LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                        LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                        LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                        LibraryFilter.SPOTIFY -> stringResource(R.string.spotify)
                    }
                },
                onValueSelected = onDefaultChipChange,
            )


            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Always On Display") },
                description = "Styles, shapes and customization options",
                icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                onClick = { navController.navigate("settings/appearance/always_on_display") }
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Widget Settings") },
                description = "Customize the widget appearance",
                icon = { Icon(painterResource(R.drawable.buttons), null) },
                onClick = { navController.navigate("settings/widget") }
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.show_home_category_chips)) },
                description = stringResource(R.string.show_home_category_chips_desc),
                icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                checked = showHomeCategoryChips,
                onCheckedChange = onShowHomeCategoryChipsChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.show_tags_in_library)) },
                description = stringResource(R.string.show_tags_in_library_desc),
                icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                checked = showTagsInLibrary,
                onCheckedChange = onShowTagsInLibraryChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.swipe_song_to_add)) },
                icon = { Icon(painterResource(R.drawable.swipe), null) },
                checked = swipeToSong,
                onCheckedChange = onSwipeToSongChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.slim_navbar)) },
                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                checked = slimNav,
                onCheckedChange = onSlimNavChange
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text(stringResource(R.string.grid_cell_size)) },
                icon = { Icon(painterResource(R.drawable.grid_view), null) },
                selectedValue = gridItemSize,
                onValueSelected = onGridItemSizeChange,
                valueText = {
                    when (it) {
                        GridItemSize.BIG -> stringResource(R.string.big)
                        GridItemSize.SMALL -> stringResource(R.string.small)
                    }
                },
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.auto_playlists)
        )
        PreferenceGroup {
            SwitchPreference(
                title = { Text(stringResource(R.string.show_liked_playlist)) },
                icon = { Icon(painterResource(R.drawable.favorite), null) },
                checked = showLikedPlaylist,
                onCheckedChange = onShowLikedPlaylistChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.show_downloaded_playlist)) },
                icon = { Icon(painterResource(R.drawable.offline), null) },
                checked = showDownloadedPlaylist,
                onCheckedChange = onShowDownloadedPlaylistChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.show_top_playlist)) },
                icon = { Icon(painterResource(R.drawable.trending_up), null) },
                checked = showTopPlaylist,
                onCheckedChange = onShowTopPlaylistChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.show_cached_playlist)) },
                icon = { Icon(painterResource(R.drawable.cached), null) },
                checked = showCachedPlaylist,
                onCheckedChange = onShowCachedPlaylistChange
            )
        }

    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        }
    )
}

@Composable
private fun SliderStyleOptionCard(
    sliderStyle: SliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember {
        mutableFloatStateOf(0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {},
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Text(
            text = sliderStyleLabel(sliderStyle),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun sliderStyleLabel(sliderStyle: SliderStyle): String {
    return when (sliderStyle) {
        SliderStyle.Standard -> stringResource(R.string.slider_style_standard)
        SliderStyle.Wavy -> stringResource(R.string.slider_style_wavy)
        SliderStyle.Thick -> stringResource(R.string.slider_style_thick)
        SliderStyle.Circular -> stringResource(R.string.slider_style_circular)
        SliderStyle.Simple -> stringResource(R.string.slider_style_simple)
    }
}


enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}


/**
 * A row title with a Beta badge after it.
 *
 * Small, quiet and in the accent - the row is usable, the badge is a note about the state of what
 * is behind it, not a warning. Kept as a composable rather than a string suffix so it cannot end
 * up inside a translation, which is the one place a word like "Beta" must not go.
 */
@Composable
private fun BetaTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.beta_label),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}
