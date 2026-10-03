/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens

import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.component.rememberScrollEdge
import com.ozyern.exhale.ui.component.scrollEdgeScrim
import com.ozyern.exhale.ui.component.PlayShuffleButton
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.heroParallax
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.ui.component.lightGlass
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.constants.DisableBlurKey
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.playback.ExoDownloadService
import com.ozyern.exhale.playback.queues.LocalAlbumRadio
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.SongListItem
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.ui.component.shimmer.ButtonPlaceholder
import com.ozyern.exhale.ui.component.shimmer.ListItemPlaceHolder
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.SelectionSongMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.theme.PlayerColorExtractor
import com.ozyern.exhale.ui.utils.ItemWrapper
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.AlbumUiState
import com.ozyern.exhale.viewmodels.AlbumViewModel
import com.valentinilk.shimmer.shimmer

// ============================================================================
// COVER ARTWORK DOWNLOAD
// ============================================================================

/**
 * Saves the album's cover art to the gallery at the requested resolution.
 *
 * @param quality edge length in pixels: 512 (small), 768 (medium), 1024 (large).
 */
suspend fun downloadAlbumCover(
    context: android.content.Context,
    imageUrl: String?,
    albumTitle: String,
    quality: Int = 1024,
): Boolean {
    if (imageUrl.isNullOrEmpty()) return false

    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(quality, quality))
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val bitmap = result.image?.toBitmap() ?: return false

        val qualityLabel = when (quality) {
            512 -> "small"
            768 -> "medium"
            1024 -> "large"
            else -> "custom"
        }

        val filename = "${albumTitle.replace("/", "_")}_${qualityLabel}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Exhale")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(imageUri, contentValues, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ============================================================================
// COVER QUALITY PICKER
// ============================================================================

/** One selectable resolution, so the three rows cannot drift apart. */
private data class CoverQuality(
    val pixels: Int,
    val label: String,
    val approxSize: String,
)

private val CoverQualities = listOf(
    CoverQuality(512, "Small", "~50 KB"),
    CoverQuality(768, "Medium", "~100 KB"),
    CoverQuality(1024, "Large", "~200 KB"),
)

/**
 * Resolution picker for "save cover art".
 *
 * Three near-identical `RadioButton` rows was the wrong control for this: the choice is really
 * *how big*, and a radio dot communicates none of that. Each option is now a card that leads with
 * its own pixel dimensions, so the thing being chosen is legible at a glance and the selected state
 * is the card itself lighting up rather than a 20dp dot changing colour.
 */
@Composable
private fun QualitySelectionDialog(
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    var selectedQuality by remember { mutableStateOf(1024) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Save cover art",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Pick a resolution. The image is saved to Pictures/Exhale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                CoverQualities.forEach { option ->
                    val selected = selectedQuality == option.pixels
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                }
                            )
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    Color.Transparent
                                },
                                shape = shape,
                            )
                            .selectable(
                                selected = selected,
                                onClick = { selectedQuality = option.pixels },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The dimensions ARE the choice, so they lead the row instead of
                        // hiding in a parenthetical after the adjective.
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.pixels.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${option.pixels} × ${option.pixels} · ${option.approxSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(percent = 50),
                onClick = {
                    onQualitySelected(selectedQuality)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    // The "About this album": YouTube's own editorial note, when the release has one.
    val albumBrowseId = albumWithSongs?.album?.id
    var albumAbout by remember(albumBrowseId) { mutableStateOf<String?>(null) }
    LaunchedEffect(albumBrowseId) {
        val id = albumBrowseId ?: return@LaunchedEffect
        albumAbout = withContext(Dispatchers.IO) {
            com.ozyern.exhale.innertube.YouTube.albumDescription(id).getOrNull()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val surfaceColor = MaterialTheme.colorScheme.surface

    // Cover-art download state
    var downloadingCover by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }


    val wrappedSongs = remember(albumWithSongs, hideExplicit) {
        val filteredSongs = if (hideExplicit) {
            albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
        } else {
            albumWithSongs?.songs ?: emptyList()
        }
        filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
    }

    var selection by remember { mutableStateOf(false) }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED ||
                                downloads[it]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    // State for LazyColumn to track scroll
    val lazyListState = rememberLazyListState()

    // The album's motion artwork: Apple's editorial loop for this album,
    // playing where the cover is. Looked up once per album, and only with animated covers on.
    val (animatedCovers) = rememberPreference(com.ozyern.exhale.constants.ExhaleCanvasKey, true)
    var albumMotion by remember(albumWithSongs?.album?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(albumWithSongs?.album?.id, animatedCovers) {
        val album = albumWithSongs?.album ?: return@LaunchedEffect
        if (!animatedCovers) {
            albumMotion = null
            return@LaunchedEffect
        }
        val artist = albumWithSongs?.artists?.firstOrNull()?.name ?: return@LaunchedEffect
        albumMotion = withContext(Dispatchers.IO) {
            runCatching {
                com.ozyern.exhale.canvas.providers.AppleMusicCanvasProvider
                    .getByAlbumArtist(album.title, artist)
                    ?.preferredAnimationUrl
            }.getOrNull()
        }
    }
    // Only while the header is on screen: a loop playing under a scrolled list is battery for nothing.
    val headerVisible by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 } }


    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !disableBlur && !selection && !showTopBarTitle
        }
    }

    // The release page: the sleeve's colours for the whole page, the artwork behind the
    // list, a band of blur across the join, and the track list numbered underneath.
    val palette = com.ozyern.exhale.ui.component.rememberReleasePalette(albumWithSongs?.album?.thumbnailUrl)
    val albumBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    var searching by rememberSaveable(albumBrowseId) { mutableStateOf(false) }
    var query by rememberSaveable(albumBrowseId) { mutableStateOf("") }
    val closeSearch = {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching && !selection) { closeSearch() }
    val visibleSongs = remember(wrappedSongs.toList(), query) {
        wrappedSongs.withIndex().filter { (_, w) ->
            query.isBlank() ||
                w.item.song.title.contains(query, ignoreCase = true) ||
                w.item.artists.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val artHeight = (maxWidth / com.ozyern.exhale.ui.component.ReleaseSleeveRatio)
            .coerceAtMost(maxHeight * 0.6f)
        val artworkUrl = albumWithSongs?.album?.thumbnailUrl
        if (albumWithSongs?.songs?.isNotEmpty() == true) {
            com.ozyern.exhale.ui.component.ReleaseBackground(
                artworkUrl = artworkUrl,
                palette = palette,
                artHeight = artHeight,
                listState = lazyListState,
                // Recorded for this page's own glass: the back button bends the sleeve it sits on.
                modifier = Modifier.matchParentSize().layerBackdrop(albumBackdrop),
            ) {
                albumMotion?.let { motion ->
                    com.ozyern.exhale.ui.player.CanvasArtworkPlayer(
                        primaryUrl = motion,
                        fallbackUrl = null,
                        isPlaying = headerVisible,
                        modifier = Modifier.fillMaxSize(),
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    )
                }
            }
            com.ozyern.exhale.ui.component.ReleaseMergeBand(
                artworkUrl = artworkUrl,
                palette = palette,
                artHeight = artHeight,
                listState = lazyListState,
                enabled = !disableBlur,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                    .calculateBottomPadding(),
            ),
        ) {
            val albumWithSongs = albumWithSongs
            val hasSongs = albumWithSongs?.songs?.isNotEmpty() == true
            if (hasSongs) {
                item(key = "header") {
                    val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                    val meta = buildList {
                        add("Album")
                        albumWithSongs.album.year?.let { add(it.toString()) }
                        add(pluralStringResource(R.plurals.n_song, wrappedSongs.size, wrappedSongs.size))
                        if (totalDuration > 0) add(makeTimeString(totalDuration * 1000L))
                    }.joinToString(" \u2022 ").uppercase()
                    com.ozyern.exhale.ui.component.ReleaseHeader(
                        title = albumWithSongs.album.title,
                        credit = albumWithSongs.artists.joinToString(", ") { it.name },
                        meta = meta,
                        palette = palette,
                        artHeight = artHeight,
                        onCreditClick = albumWithSongs.artists.firstOrNull()?.let { artist ->
                            { navController.navigate("artist/${artist.id}") }
                        },
                        badges = {
                            if (mediaMetadata?.album?.id == albumWithSongs.album.id) {
                                Spacer(modifier = Modifier.height(8.dp))
                                com.ozyern.exhale.ui.component.AudioFormatBadges(format = currentFormat)
                            }
                        },
                    ) {
                        val circle = 46.dp
                        // Save · Shuffle · Play · Search · More.
                        // Downloading lives in More, out of the way.
                        val saved = albumWithSongs.album.bookmarkedAt != null
                        com.ozyern.exhale.ui.component.ReleaseCircle(
                            icon = if (saved) R.drawable.check else R.drawable.add,
                            contentDescription = null,
                            palette = palette,
                            size = circle,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                database.query { update(albumWithSongs.album.toggleLike()) }
                            },
                        )
                        com.ozyern.exhale.ui.component.ReleaseCircle(
                            icon = R.drawable.shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            palette = palette,
                            size = circle,
                            onClick = {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(
                                    LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled())),
                                )
                            },
                        )
                        com.ozyern.exhale.ui.component.ReleasePlay(
                            contentDescription = stringResource(R.string.play),
                            size = circle,
                            onClick = {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                            },
                        )
                        com.ozyern.exhale.ui.component.ReleaseCircle(
                            icon = if (searching) R.drawable.close else R.drawable.search,
                            contentDescription = null,
                            palette = palette,
                            size = circle,
                            onClick = { if (searching) closeSearch() else searching = true },
                        )
                        com.ozyern.exhale.ui.component.ReleaseCircle(
                            icon = R.drawable.more_horiz,
                            contentDescription = null,
                            palette = palette,
                            size = circle,
                            onClick = {
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = Album(albumWithSongs.album, albumWithSongs.artists),
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                    }
                }

                if (searching) {
                    item(key = "search") {
                        com.ozyern.exhale.ui.component.ReleaseSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onClose = closeSearch,
                            palette = palette,
                            placeholder = "Search this album",
                        )
                    }
                }

                albumAbout?.takeIf { it.isNotBlank() && !searching }?.let { about ->
                    item(key = "about_album") {
                        com.ozyern.exhale.ui.component.ReleaseAbout(
                            title = "About the album",
                            text = about,
                            palette = palette,
                        )
                    }
                }

                if (visibleSongs.isEmpty()) {
                    item(key = "no_matches") {
                        Text(
                            text = "Nothing matches \u201c$query\u201d",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.onBackgroundVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }

                itemsIndexed(
                    items = visibleSongs,
                    key = { _, entry -> entry.value.item.id },
                ) { position, entry ->
                    val index = entry.index
                    val songWrapper = entry.value
                    val song = songWrapper.item
                    val isCurrent = song.id == mediaMetadata?.id
                    com.ozyern.exhale.ui.component.ReleaseTrackRow(
                        number = index + 1,
                        title = song.song.title,
                        subtitle = song.artists.joinToString(", ") { it.name },
                        duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                        explicit = song.song.explicit,
                        palette = palette,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        selected = songWrapper.isSelected && selection,
                        onClick = {
                            if (!selection) {
                                if (isCurrent) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.service.getAutomix(playlistId)
                                    playerConnection.playQueue(LocalAlbumRadio(albumWithSongs, startIndex = index))
                                }
                            } else {
                                songWrapper.isSelected = !songWrapper.isSelected
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!selection) selection = true
                            wrappedSongs.forEach { it.isSelected = false }
                            songWrapper.isSelected = true
                        },
                        onMore = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                    if (position < visibleSongs.lastIndex) {
                        com.ozyern.exhale.ui.component.ReleaseRowDivider(palette)
                    }
                }

                // The release signs off with its running time.
                item(key = "footer") {
                    val total = albumWithSongs.songs.sumOf { it.song.duration }
                    Text(
                        text = buildList {
                            add(pluralStringResource(R.plurals.n_song, wrappedSongs.size, wrappedSongs.size))
                            if (total > 0) add("${(total + 30) / 60} minutes")
                        }.joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onBackgroundVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
                    )
                }

                // Other Versions Section
                if (otherVersions.isNotEmpty()) {
                    item(key = "other_versions_header") {
                        com.ozyern.exhale.ui.component.ReleaseSectionHeading(
                            title = stringResource(R.string.other_versions),
                            palette = palette,
                        )
                    }
                    item(key = "other_versions_list") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            items(
                                items = otherVersions.distinctBy { it.id },
                                key = { it.id },
                            ) { item ->
                                YouTubeGridItem(
                                    item = item,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = scope,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = { navController.navigate("album/${item.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }
                        }
                    }
                }
            } else {
                when (val state = uiState) {
                    AlbumUiState.Loading,
                    AlbumUiState.Content -> {
                        item(key = "shimmer") {
                            ShimmerHost {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp, bottom = 20.dp)
                                            .size(240.dp)
                                            .shimmer()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.onSurface)
                                    )

                                    TextPlaceholder(
                                        height = 28.dp,
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .padding(horizontal = 32.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextPlaceholder(
                                        height = 20.dp,
                                        modifier = Modifier.fillMaxWidth(0.4f)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        repeat(3) {
                                            TextPlaceholder(
                                                height = 32.dp,
                                                modifier = Modifier.width(70.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                        ButtonPlaceholder(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        )
                                        ButtonPlaceholder(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                repeat(6) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    AlbumUiState.Empty -> {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight)
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_album),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_album_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is AlbumUiState.Error -> {
                        item(key = "error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight)
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (state.isNotFound) stringResource(R.string.album_not_found) else stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (state.isNotFound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (state.isNotFound) stringResource(R.string.album_not_found_desc) else stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top App Bar
        val topAppBarColors = if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        } else {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        }

        val scrollEdge = rememberScrollEdge(!transparentAppBar)
        TopAppBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .scrollEdgeScrim(palette.background) { scrollEdge.value },
            colors = topAppBarColors,
            scrollBehavior = scrollBehavior,
            title = { com.ozyern.exhale.ui.component.HeadingStyle {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = albumWithSongs?.album?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } },
            navigationIcon = {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop provides albumBackdrop,
                ) {
                LiquidBackButton(
                    onClick = {
                        if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!selection) {
                            navController.backToMain()
                        }
                    },
                    icon = if (selection) R.drawable.close else R.drawable.chevron_back,
                )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {}
                    ) {
                        Icon(
                            painter = painterResource(
                                if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all
                            ),
                            contentDescription = null
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedSongs.filter { it.isSelected }
                                        .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false }
                                )
                            }
                        },
                        onLongClick = {}
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                }
            }
        )

        // Cover-art resolution picker
        if (showQualityDialog) {
            QualitySelectionDialog(
                onDismiss = { showQualityDialog = false },
                onQualitySelected = { quality ->
                    downloadingCover = true
                    scope.launch {
                        try {
                            downloadAlbumCover(
                                context = context,
                                imageUrl = albumWithSongs?.album?.thumbnailUrl,
                                albumTitle = albumWithSongs?.album?.title ?: "album",
                                quality = quality
                            )
                        } finally {
                            downloadingCover = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

