/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ChipSortTypeKey
import com.ozyern.exhale.constants.DisableBlurKey
import com.ozyern.exhale.constants.LibraryFilter
import com.ozyern.exhale.constants.PlaylistTagsFilterKey
import com.ozyern.exhale.constants.ShowTagsInLibraryKey
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.AmbientArtworkGlow
import com.ozyern.exhale.ui.component.LiquidGlassIconButton
import com.ozyern.exhale.ui.component.TagsFilterChips
import com.ozyern.exhale.ui.component.rememberArtworkAmbientColors
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

/**
 * The Library tab: one of six views, opening on the one [ChipSortTypeKey] names.
 *
 * The chip row that used to sit at the top of every one of them is gone. It was doing two
 * incompatible jobs — naming where you were, and offering the five places you could go — and it
 * did the second one badly, because a selected chip and an unselected chip differ by a fill
 * colour. [LibraryMixScreen] now lists the destinations as rows, and each destination gets a real
 * sub-page header instead: a back button and its own title, which is what every other pushed
 * screen in the app looks like.
 *
 * ### Where you are is not what you prefer
 *
 * Which sub-view is open is *screen state*. It used to be the preference itself: tapping "Songs"
 * wrote [ChipSortTypeKey], which is the "Default library page" setting in Appearance. Three things
 * followed from that, and all three of them read as the tab being broken.
 *
 * Browsing silently rewrote a setting the user had chosen. The tab stopped being able to return to
 * its own front page — leaving and coming back reopened the sub-view, and the dock's Library button
 * counts a tap on the tab you are already on as a re-tap, so it did nothing. And, worst of the
 * three, the sub-view arms a [BackHandler]; once that state survived across sessions, the back
 * gesture from what looked like the Library tab silently went "up one level inside Library"
 * instead of back to Home, on a screen that gave no sign there was a level to go up from.
 *
 * So the preference is read, never written. It says where the tab *opens*; the tab remembers where
 * you *are* for as long as it is on the back stack, and a re-tap on the dock's Library button
 * returns to the front page the way re-tapping a tab is supposed to.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    // The setting, read-only: which view the tab opens on. Account settings also writes it
    // immediately before navigating here, which is how "you have 42 songs" opens the songs list —
    // so this is observed rather than merely sampled once.
    val (defaultFilter) = rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var filterType by rememberSaveable { mutableStateOf(defaultFilter) }

    // Follow the setting when it *changes*, not whenever this composable happens to run again.
    // `seenDefault` is what makes that distinction survive a rotation: without it the effect
    // fires on every re-entry and throws away where the user was every time the screen recreates.
    var seenDefault by rememberSaveable { mutableStateOf(defaultFilter) }
    LaunchedEffect(defaultFilter) {
        if (defaultFilter != seenDefault) {
            seenDefault = defaultFilter
            filterType = defaultFilter
        }
    }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val database = LocalDatabase.current
    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, true)
    val (selectedTagsFilter, onSelectedTagsFilterChange) = rememberPreference(PlaylistTagsFilterKey, "")
    val selectedTagIds = remember(selectedTagsFilter) {
        selectedTagsFilter.split(",").filter { it.isNotBlank() }.toSet()
    }

    val backToLibrary = { filterType = LibraryFilter.LIBRARY }

    // A sub-view is a pushed page, so the system back gesture should pop it rather than leaving
    // the tab. Previously the only way back was to find and re-tap the lit chip.
    //
    // Armed only when there is somewhere to go up *to*. If the user has set a sub-view as their
    // default library page then that view is the tab as far as they are concerned, and swallowing
    // their first back press to reveal a page they told us not to open is a level of the app they
    // never asked for and cannot see.
    BackHandler(enabled = filterType != LibraryFilter.LIBRARY && defaultFilter != filterType) {
        backToLibrary()
    }

    // Re-tapping the Library tab returns to the front page, which is what re-tapping a tab means
    // everywhere else. The dock sends this on a tap on the already-selected tab; Home reads the
    // same signal to scroll itself back to the top.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val reselected = backStackEntry
        ?.savedStateHandle
        ?.getStateFlow("scrollToTop", false)
        ?.collectAsState()
    LaunchedEffect(reselected?.value) {
        if (reselected?.value == true) {
            filterType = defaultFilter
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val subPageTitle = when (filterType) {
        LibraryFilter.PLAYLISTS -> R.string.filter_playlists
        LibraryFilter.SPOTIFY -> R.string.spotify
        else -> R.string.filter_library
    }

    // Passed to the sub-screens that take a header slot (Playlists, Spotify). Songs, Albums and
    // Artists render their own.
    val filterContent = @Composable {
        Column {
            LibrarySubPageHeader(
                title = stringResource(subPageTitle),
                onBack = backToLibrary,
            )

            if (showTagsInLibrary && filterType == LibraryFilter.PLAYLISTS) {
                TagsFilterChips(
                    database = database,
                    selectedTags = selectedTagIds,
                    onTagToggle = { tag ->
                        val newTags = if (tag.id in selectedTagIds) {
                            selectedTagIds - tag.id
                        } else {
                            selectedTagIds + tag.id
                        }
                        onSelectedTagsFilterChange(newTags.joinToString(","))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Library takes the same ambient wash as Home and the player: the colour of whatever is
    // playing, bleeding down from the top.
    //
    // Not `?: return` as Home does: Library is reachable before the playback service has bound,
    // and a blank tab is a worse failure than a wash that has not arrived yet.
    val mediaMetadata by LocalPlayerConnection.current?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val ambientColors by rememberArtworkAmbientColors(
        songId = mediaMetadata?.id,
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!disableBlur) {
            AmbientArtworkGlow(
                colors = ambientColors,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                onTabSelected = { filterType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
            LibraryFilter.SONGS -> LibrarySongsScreen(navController, backToLibrary)
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(navController, backToLibrary)
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(navController, backToLibrary)
            LibraryFilter.SPOTIFY -> LibrarySpotifyPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
            )
        }
    }
}

/**
 * The header a library sub-view opens with: a round glass back button, then the view's name.
 *
 * The title is set one step below the Library page's own — a pushed page is subordinate to the
 * page that pushed it, and matching them at display size makes the stack read flat.
 */
@Composable
private fun LibrarySubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
    ) {
        LiquidGlassIconButton(
            onClick = onBack,
            icon = R.drawable.chevron_back,
            contentDescription = stringResource(R.string.back),
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.025).em),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
