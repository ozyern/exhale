/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.menu

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.layout.Column
import com.ozyern.exhale.ui.component.Next
import com.ozyern.exhale.ui.component.MenuDividers
import com.ozyern.exhale.ui.component.MenuSurfaceSection
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ArtistSongSortType
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.ArtistListItem
import com.ozyern.exhale.ui.component.NewAction
import com.ozyern.exhale.ui.component.NewActionGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist

    ArtistListItem(
        artist = artist,
        badges = {},
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        userScrollEnabled = !isPortrait,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            // Enhanced Action Grid using NewMenuComponents
            NewActionGrid(
                actions = buildList {
                    if (artist.songCount > 0) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )

                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                                .shuffled()
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/channel/${artist.id}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }
        item {
            Spacer(Modifier.height(14.dp))
            MenuSurfaceSection {
                Column {
                    val dividers = MenuDividers()


        // Subscribe/Subscribed button
        run { dividers.Next()

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(text = if (artist.artist.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.subscribed else R.drawable.subscribe),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    database.transaction {
                        update(artist.artist.toggleLike())
                    }
                }
            )
        }
    
                }
            }
        }
    }
}
