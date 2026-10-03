/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens
import com.ozyern.exhale.ui.screens.DownloadQueueScreen

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DarkModeKey
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.ui.component.BottomSheet
import com.ozyern.exhale.ui.component.BottomSheetMenu
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.WidgetSettings
import com.ozyern.exhale.ui.component.rememberBottomSheetState
import com.ozyern.exhale.ui.screens.BrowseScreen
import com.ozyern.exhale.ui.screens.artist.ArtistAlbumsScreen
import com.ozyern.exhale.ui.screens.artist.ArtistItemsScreen
import com.ozyern.exhale.ui.screens.artist.ArtistScreen
import com.ozyern.exhale.ui.screens.artist.ArtistSongsScreen
import com.ozyern.exhale.ui.screens.library.LibraryScreen
import com.ozyern.exhale.ui.screens.playlist.AutoPlaylistScreen
import com.ozyern.exhale.ui.screens.playlist.LocalPlaylistScreen
import com.ozyern.exhale.ui.screens.playlist.OnlinePlaylistScreen
import com.ozyern.exhale.ui.screens.playlist.TopPlaylistScreen
import com.ozyern.exhale.ui.screens.playlist.CachePlaylistScreen
import com.ozyern.exhale.ui.screens.search.OnlineSearchResult
import com.ozyern.exhale.ui.screens.search.SearchScreen
import com.ozyern.exhale.ui.screens.settings.AboutScreen
import com.ozyern.exhale.ui.screens.settings.AccountSettings
import com.ozyern.exhale.ui.screens.settings.AppIconScreen
import com.ozyern.exhale.ui.screens.settings.AppearanceSettings
import com.ozyern.exhale.ui.screens.settings.CustomizeBackground
import com.ozyern.exhale.ui.screens.settings.BackupAndRestore
import com.ozyern.exhale.ui.screens.settings.ChangelogScreen
import com.ozyern.exhale.ui.screens.settings.ContentSettings
import com.ozyern.exhale.ui.screens.settings.DarkMode
import com.ozyern.exhale.ui.screens.settings.DiscordLoginScreen
import com.ozyern.exhale.ui.screens.settings.DiscordSettings
import com.ozyern.exhale.ui.screens.settings.DebugSettings
import com.ozyern.exhale.ui.screens.settings.IntegrationScreen
import com.ozyern.exhale.ui.screens.settings.LastFMSettings
import com.ozyern.exhale.ui.screens.settings.MusicTogetherScreen
import com.ozyern.exhale.ui.screens.settings.PalettePickerScreen
import com.ozyern.exhale.ui.screens.settings.PlayerSettings
import com.ozyern.exhale.ui.screens.settings.PoTokenScreen
import com.ozyern.exhale.ui.screens.settings.PrivacySettings
import com.ozyern.exhale.ui.screens.settings.SettingsScreen
import com.ozyern.exhale.ui.screens.settings.StorageSettings
import com.ozyern.exhale.ui.screens.settings.ThemeCreatorScreen
import com.ozyern.exhale.ui.screens.settings.UiScaleScreen
import com.ozyern.exhale.ui.screens.settings.UpdateScreen
import com.ozyern.exhale.ui.screens.musicrecognition.MusicRecognitionRoute
import com.ozyern.exhale.ui.screens.musicrecognition.MusicRecognitionScreen
import com.ozyern.exhale.ui.screens.playlist.SpotifyPlaylistScreen
import com.ozyern.exhale.ui.screens.settings.AODSettings
import com.ozyern.exhale.ui.screens.settings.AndroidAutoSettings

import com.ozyern.exhale.ui.screens.settings.SettingsPage
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

/**
 * Registers a settings destination already wrapped in [SettingsPage].
 *
 * Every settings screen has to sit on the same solid grouped background with flat, matching
 * chrome. Doing that at the graph level means the ~20 individual settings screens stay free of
 * layout boilerplate and — more importantly — cannot drift apart from each other over time.
 */
private fun NavGraphBuilder.settingsComposable(
    route: String,
    content: @Composable () -> Unit,
) = composable(route) {
    SettingsPage { content() }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    composable(Screens.Home.route) {
        HomeScreen(navController)
    }
    // Search is a standard, peer-level bottom-tab destination (sibling of Home/Library),
    // NOT a dialog/overlay: the top bar, mini-player and back stack behave normally on it.
    composable(Screens.Search.route) {
        SearchScreen(navController)
    }
    composable(
        Screens.Library.route,
    ) {
        LibraryScreen(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("year_in_music") {
        YearInMusicScreen(navController)
    }
    composable(MusicRecognitionRoute) {
        MusicRecognitionScreen(navController)
    }
    composable(Screens.MoodAndGenres.route) {
        MoodAndGenresScreen(navController)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("charts_screen") {
        ChartsScreen(navController)
    }
    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }
    composable(
        route = "song_preferences",
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 3 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally { it / 3 } },
    ) {
        com.ozyern.exhale.ui.screens.onboarding.SongPreferencesScreen(navController)
    }
    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }
    settingsComposable("settings") {
        SettingsScreen(navController, scrollBehavior, latestVersionName)
    }
    settingsComposable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/appearance/ui_scale") {
        UiScaleScreen(navController)
    }
    settingsComposable("settings/haptics") {
        com.ozyern.exhale.ui.screens.settings.HapticsScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/appearance/player_style") {
        com.ozyern.exhale.ui.screens.settings.PlayerStyleScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/appearance/app_icon") {
        AppIconScreen(navController)
    }
    settingsComposable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    settingsComposable("settings/appearance/theme_creator") {
        ThemeCreatorScreen(navController)
    }
    composable(
        route = "settings/appearance/always_on_display",
        enterTransition = {
            fadeIn(tween(300)) + slideInHorizontally { it / 3 }
        },
        exitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally { -it / 3 }
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideInHorizontally { -it / 3 }
        },
        popExitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally { it / 3 }
        },
    ) {
        SettingsPage { AODSettings(navController, scrollBehavior) }
    }


    composable(
        route = "spotify_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        SpotifyPlaylistScreen(navController, scrollBehavior)
    }

    settingsComposable("settings/widget") {
        WidgetSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/content/lyrics_sources") {
        com.ozyern.exhale.ui.screens.settings.LyricsSourcesScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/player/equalizer") {
        com.ozyern.exhale.ui.screens.settings.SoundEqualizerScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }
    settingsComposable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }
    settingsComposable("settings/discord") {
        DiscordSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/integration") {
        IntegrationScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/music_together") {
        MusicTogetherScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/lastfm") {
        LastFMSettings(navController, scrollBehavior)
    }
    settingsComposable("settings/discord/advanced") {
        com.ozyern.exhale.ui.screens.settings.DiscordAdvanced(navController)
    }
    settingsComposable("settings/discord/experimental") {
        com.ozyern.exhale.ui.screens.settings.DiscordExperimental(navController)
    }
    settingsComposable("settings/misc") {
        DebugSettings(navController)
    }
    settingsComposable("settings/update") {
        UpdateScreen(navController, scrollBehavior)
    }
    // Same screen, arriving with the transfer already starting. A separate route rather than a
    // query argument because `settingsComposable` takes a plain route string, and one extra line
    // here is cheaper than threading NavBackStackEntry through all thirty settings destinations.
    settingsComposable("settings/update/download") {
        UpdateScreen(navController, scrollBehavior, autoStart = true)
    }
    settingsComposable("settings/update/notes") {
        com.ozyern.exhale.ui.screens.settings.UpdateNotesScreen(navController)
    }
    settingsComposable("settings/changelog") {
        ChangelogScreen(navController, scrollBehavior)
    }
    settingsComposable("settings/discord/login") {
        DiscordLoginScreen(navController)
    }
    settingsComposable("settings/android_auto") {
        AndroidAutoSettings(
            navController,
            scrollBehavior,
            context = androidx.compose.ui.platform.LocalContext.current
        )
    }
    settingsComposable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }
    composable(Screens.DownloadQueue.route) {
        DownloadQueueScreen(navController)
    }
    settingsComposable("settings/po_token") {
        PoTokenScreen(navController, scrollBehavior)
    }
    settingsComposable("customize_background") {
        CustomizeBackground(navController)
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments = listOf(
            navArgument(LOGIN_URL_ARGUMENT) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode)
        )
    }


// ─────────────────────────────────────────────────────────────────────────
// Always On Display — como diálogo que cubre completamente
// ─────────────────────────────────────────────────────────────────────────
    composable(
        route = "always_on_display",
        // Esto la hace un diálogo que se superpone
    ) { backStackEntry ->
        Dialog(
            onDismissRequest = { navController.navigateUp() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AlwaysOnDisplayScreen(navController)
        }
    }

}