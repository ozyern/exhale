/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.artist

import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.heroParallax
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.canvas.providers.AppleMusicArtistBackgroundProvider
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.constants.DisableBlurKey
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.db.entities.ArtistEntity
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.AlbumGridItem
import com.ozyern.exhale.ui.component.HideOnScrollFAB
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.SongListItem
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.ui.component.shimmer.ButtonPlaceholder
import com.ozyern.exhale.ui.component.shimmer.ListItemPlaceHolder
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.menu.YouTubeArtistMenu
import com.ozyern.exhale.ui.menu.YouTubePlaylistMenu
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.ui.player.CanvasArtworkPlayer
import com.ozyern.exhale.ui.theme.PlayerColorExtractor
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

// Sealed class for video background state
sealed class VideoBackgroundState {
    object Loading : VideoBackgroundState()
    object Empty : VideoBackgroundState()
    data class Success(val url: String) : VideoBackgroundState()
    data class Error(val message: String) : VideoBackgroundState()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    // The artist's story under the header: opened by its link or by the (i) beside Play.
    var bioExpanded by rememberSaveable { mutableStateOf(false) }
    // The name's face: this artist's own choice, else the default from Settings.
    val (defaultNameFont) = rememberPreference(com.ozyern.exhale.constants.ArtistNameFontKey, ArtistNameFont.CLASSIC.name)
    var artistFontOverride by remember(viewModel.artistId) { mutableStateOf<ArtistNameFont?>(null) }
    var artistFontCleared by remember(viewModel.artistId) { mutableStateOf(false) }
    val storedArtistFont = remember(viewModel.artistId) { ArtistNameFont.forArtist(context, viewModel.artistId) }
    val nameFont = artistFontOverride
        ?: storedArtistFont.takeUnless { artistFontCleared }
        ?: ArtistNameFont.of(defaultNameFont) ?: ArtistNameFont.CLASSIC
    val density = LocalDensity.current

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val surfaceColor = MaterialTheme.colorScheme.surface

    // Get thumbnail URL
    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

    val storefront = remember {
        java.util.Locale.getDefault()
            .country
            .takeIf { it.length == 2 }
            ?.lowercase(java.util.Locale.ROOT)
            ?: "us"
    }

    // Enhanced video background state with error handling and loading state
    val videoBackgroundState by produceState<VideoBackgroundState>(
        initialValue = VideoBackgroundState.Loading,
        artistName, storefront
    ) {
        value = if (!artistName.isNullOrBlank()) {
            try {
                val url = withContext(Dispatchers.IO) {
                    AppleMusicArtistBackgroundProvider.getByArtistName(
                        artistName = artistName,
                        storefront = storefront,
                    )
                }
                if (url.isNullOrBlank()) {
                    VideoBackgroundState.Empty
                } else {
                    // Small delay to ensure smooth transition
                    delay(100)
                    VideoBackgroundState.Success(url)
                }
            } catch (e: Exception) {
                VideoBackgroundState.Error(e.message ?: "Failed to load background video")
            }
        } else {
            VideoBackgroundState.Empty
        }
    }




    LaunchedEffect(libraryArtist) {
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    val palette = com.ozyern.exhale.ui.component.rememberReleasePalette(thumbnail?.resize(1200, 1200))
    val artistBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val artistConfiguration = LocalConfiguration.current
    val artistArtHeight = remember(artistConfiguration.screenWidthDp, artistConfiguration.screenHeightDp) {
        minOf(artistConfiguration.screenWidthDp / 0.95f, artistConfiguration.screenHeightDp * 0.6f).dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {

        // The artist page: the photo drawn behind the list, the page tinted from it, and
        // one wide band of blur across the join so photo and page are one surface.
        if (artistPage != null || showLocal) {
            com.ozyern.exhale.ui.component.ReleaseBackground(
                artworkUrl = thumbnail?.resize(1200, 1200),
                palette = palette,
                artHeight = artistArtHeight,
                listState = lazyListState,
                // Recorded for this page's own glass: the back button bends the photo it sits on.
                modifier = Modifier.matchParentSize().layerBackdrop(artistBackdrop),
            ) {
                (videoBackgroundState as? VideoBackgroundState.Success)?.let { motion ->
                    val heroOnScreen by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 } }
                    CanvasArtworkPlayer(
                        primaryUrl = motion.url,
                        fallbackUrl = null,
                        isPlaying = heroOnScreen,
                        modifier = Modifier.matchParentSize(),
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    )
                }
            }
            com.ozyern.exhale.ui.component.ReleaseMergeBand(
                artworkUrl = thumbnail?.resize(1200, 1200),
                palette = palette,
                artHeight = artistArtHeight,
                listState = lazyListState,
                enabled = !disableBlur,
            )
        }

        LazyColumn(
            state = lazyListState,
            // No top inset: the photo runs to the top edge, under the see-through bar, as Apple's does.
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                // Shimmer loading state
                item(key = "shimmer") {
                    ShimmerHost {
                        // Hero section placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = systemBarsTopPadding + AppBarHeight)
                        ) {
                            // Artist image placeholder - circular
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(210.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .shimmer()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Artist name placeholder
                            TextPlaceholder(
                                height = 32.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stats placeholder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(3) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        TextPlaceholder(
                                            height = 20.dp,
                                            modifier = Modifier.width(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextPlaceholder(
                                            height = 14.dp,
                                            modifier = Modifier.width(50.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Buttons placeholder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                            ) {
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
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        // Songs list placeholder
                        repeat(5) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                // Hero Header
                item(key = "header") {
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                    // ── Hero ──
                    //
                    // A profile header, not a poster.
                    //
                    // It used to be a 172dp portrait centred on the page with the name in
                    // `displaySmall` underneath it and the counts centred under that — three
                    // stacked centred blocks that between them ate the entire first screen before
                    // a single song appeared. Centring is also the wrong axis for this page: the
                    // description below it is a paragraph, the sections below that are lists, and
                    // both of those read left-to-right from a fixed margin, so the top of the page
                    // was the only part of it that did not.
                    //
                    // Now the portrait, the name and the counts are one row against the same
                    // margin as everything under them, over a blurred wash of the artist's own
                    // image. That is roughly two thirds of the vertical space for the same
                    // information, and the first section header now lands above the fold.
                    val countsLine = run {
                        val songSections = artistPage?.sections?.filter { section ->
                            section.items.any { it is SongItem }
                        }
                        val songCount = if (showLocal) {
                            librarySongs.size
                        } else {
                            songSections
                                ?.flatMap { it.items }
                                ?.filterIsInstance<SongItem>()
                                ?.distinctBy { it.id }
                                ?.size ?: librarySongs.size
                        }
                        val hasMoreSongs =
                            !showLocal && songSections?.any { it.moreEndpoint != null } == true

                        val albumSections = artistPage?.sections?.filter { section ->
                            section.items.any { it is AlbumItem }
                        }
                        val albumCount = if (showLocal) {
                            libraryAlbums.size
                        } else {
                            albumSections
                                ?.flatMap { it.items }
                                ?.filterIsInstance<AlbumItem>()
                                ?.distinctBy { it.id }
                                ?.size ?: libraryAlbums.size
                        }
                        val hasMoreAlbums =
                            !showLocal && albumSections?.any { it.moreEndpoint != null } == true

                        buildList {
                            if (songCount > 0) {
                                val n = if (hasMoreSongs) "$songCount+" else songCount.toString()
                                add("$n ${stringResource(R.string.songs)}")
                            }
                            if (albumCount > 0) {
                                val n = if (hasMoreAlbums) "$albumCount+" else albumCount.toString()
                                add("$n ${stringResource(R.string.albums)}")
                            }
                        }.joinToString("  ·  ").takeIf { it.isNotEmpty() }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // The hero: the artist, full-bleed, sharp, with the page dissolving out
                        // of the bottom of them.
                        //
                        // This replaces a 104dp circular avatar sitting in a row beside the name,
                        // over a blurred copy of that same picture. That layout spent the most
                        // valuable space on the screen — the first thing you see when you open a
                        // person — on a thumbnail, and then used a destroyed version of the very
                        // image it was shrinking as wallpaper behind it. The photo was doing two
                        // jobs badly instead of one job well.
                        //
                        // Now it is the ground itself. The scrim is what makes that legible: the
                        // image is untouched down to about a third of its height and then loses
                        // itself into `surfaceColor`, so the name has solid contrast to sit on and
                        // there is no seam where the header stops and the list begins. The old
                        // blur is gone entirely — nothing is blurred here any more, which also
                        // means one less full-screen RenderEffect on a scrolling surface.
                        // The proportions: the photo is a touch taller than the page is wide,
                        // and never more than six tenths of the window.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(artistArtHeight + com.ozyern.exhale.ui.component.ReleaseHeaderDrop),
                        ) {
                            // The name across the foot of the photo — in the face
                            // chosen for this artist, or the default. Hold it to choose another.
                            val artistId = artistPage?.artist?.id ?: libraryArtist?.artist?.id
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 7.dp)
                                    .combinedClickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                        onLongClick = {
                                            if (artistId != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistFontMenu(
                                                        name = artistName ?: "",
                                                        current = nameFont,
                                                        onPick = { font ->
                                                            ArtistNameFont.setForArtist(context, artistId, font)
                                                            artistFontOverride = font
                                                            artistFontCleared = font == null
                                                            menuState.dismiss()
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                ArtistDisplayName(name = artistName ?: stringResource(R.string.unknown_artist), font = nameFont, color = palette.onBackground)
                            }
                        }

                        val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
                        // YouTube's own reach, when it gives it; the counts of
                        // what is on this page when it doesn't.
                        val subscribers = artistPage?.artist?.subscriberCountText?.takeIf { !showLocal && it.isNotBlank() }
                        val listeners = artistPage?.artist?.monthlyListenerCountText?.takeIf { !showLocal && it.isNotBlank() }
                        if (subscribers != null || listeners != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                            ) {
                                subscribers?.let { ArtistStatChip(R.drawable.person, "${it.substringBefore(' ')} subscribers", palette) }
                                listeners?.let { ArtistStatChip(R.drawable.graphic_eq, "${it.substringBefore(' ')} monthly listeners", palette) }
                            }
                        } else if (countsLine != null) {
                            Text(
                                text = countsLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onBackgroundVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            )
                        }

                        // The action row: subscribe beside Play — the circle about this
                        // artist — then the white Play pill, then shuffle.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            ArtistActionButton(
                                iconRes = if (isSubscribed) R.drawable.check else R.drawable.add,
                                contentDescription = stringResource(
                                    if (isSubscribed) R.string.subscribed else R.string.subscribe
                                ),
                                palette = palette,
                                onClick = {
                                    database.transaction {
                                        val artist = libraryArtist?.artist
                                        if (artist != null) {
                                            update(artist.toggleLike())
                                        } else {
                                            artistPage?.artist?.let {
                                                insert(
                                                    ArtistEntity(
                                                        id = it.id,
                                                        name = it.title,
                                                        channelId = it.channelId,
                                                        thumbnailUrl = it.thumbnail,
                                                    ).toggleLike()
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                            ArtistPlayPill(
                                palette = palette,
                                onClick = {
                                    if (!showLocal) {
                                        val endpoint = artistPage?.artist?.playEndpoint
                                            ?: artistPage?.artist?.shuffleEndpoint
                                            ?: artistPage?.artist?.radioEndpoint
                                        endpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                                    } else if (librarySongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                items = librarySongs.map { it.toMediaItem() },
                                            )
                                        )
                                    }
                                },
                            )
                            ArtistActionButton(
                                iconRes = R.drawable.shuffle,
                                contentDescription = stringResource(R.string.shuffle),
                                palette = palette,
                                onClick = {
                                    if (!showLocal) {
                                        artistPage?.artist?.shuffleEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                                    } else if (librarySongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                items = librarySongs.shuffled().map { it.toMediaItem() },
                                            )
                                        )
                                    }
                                },
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Artist Description (expandable)
                        //
                        // Left-aligned, and truncated by LINE COUNT rather than by character count.
                        // Centring a paragraph is fine for one line and hostile past that — every
                        // line starts at a different x, so the eye has to hunt for the start of the
                        // next one, and this block routinely runs to eight or ten lines expanded.
                        // The old `description.take(100)` also cut mid-word and then appended its
                        // own ellipsis on top of an `overflow = Ellipsis` that was already doing the
                        // job, so a clipped bio could end in two ellipses.
                        val description = artistPage?.description
                        if (!description.isNullOrBlank()) {
                            com.ozyern.exhale.ui.component.ReleaseAbout(
                                title = "About ${artistName.orEmpty()}".trim(),
                                text = description,
                                palette = palette,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Content sections
                if (showLocal) {
                    // Local Songs Section
                    if (librarySongs.isNotEmpty()) {
                        item {
                            com.ozyern.exhale.ui.component.ReleaseSectionHeading(
                                title = stringResource(R.string.songs),
                                palette = palette,
                                onShowAll = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                }
                            )
                        }

                        val filteredLibrarySongs = if (hideExplicit) {
                            librarySongs.filter { !it.song.explicit }
                        } else {
                            librarySongs
                        }

                        itemsIndexed(
                            items = filteredLibrarySongs.take(5),
                            key = { index, item -> "local_song_${item.id}_$index" }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = libraryArtist?.artist?.name
                                                            ?: "Unknown Artist",
                                                        items = librarySongs.map { it.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }

                        // Show "View All" if more songs available
                        if (filteredLibrarySongs.size > 5) {
                            item {
                                Surface(
                                    onClick = {
                                        navController.navigate("artist/${viewModel.artistId}/songs")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.view_all),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Local Albums Section
                    if (libraryAlbums.isNotEmpty()) {
                        item {
                            com.ozyern.exhale.ui.component.ReleaseSectionHeading(
                                title = stringResource(R.string.albums),
                                palette = palette,
                                onShowAll = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                }
                            )
                        }

                        item {
                            val filteredLibraryAlbums = if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { album -> "local_album_${album.id}_${filteredLibraryAlbums.indexOf(album)}" }
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("album/${album.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // YouTube/Remote content sections
                    artistPage?.sections?.fastForEach { section ->
                        if (section.items.isNotEmpty()) {
                            item {
                                com.ozyern.exhale.ui.component.ReleaseSectionHeading(
                                    title = section.title,
                                    palette = palette,
                                    onShowAll = section.moreEndpoint?.let {
                                        {
                                            navController.navigate(
                                                "artist/${viewModel.artistId}/items?browseId=${it.browseId}&params=${it.params}",
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            // Top songs page sideways, four to a column, rather than burying the
                            // album shelves under a long list. The next column peeks in from the
                            // edge so it is plain there is more, and a fling lands on a column.
                            item(key = "youtube_song_columns_${section.title}") {
                                val columns = remember(section.items) {
                                    section.items.distinctBy { it.id }.filterIsInstance<SongItem>().take(20).chunked(4)
                                }
                                val rowState = rememberLazyListState()
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val columnWidth = minOf(maxWidth * 0.86f, 420.dp)
                                    LazyRow(
                                        state = rowState,
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
                                    ) {
                                        items(columns) { column ->
                                            Column(Modifier.width(columnWidth)) {
                                                column.forEach { song ->
                                                    ArtistTopSongRow(
                                                        song = song,
                                                        palette = palette,
                                                        isCurrent = mediaMetadata?.id == song.id,
                                                        isPlaying = isPlaying,
                                                        onClick = {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.player.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        WatchEndpoint(videoId = song.id),
                                                                        song.toMediaMetadata()
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                        onMore = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                YouTubeSongMenu(
                                                                    song = song,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Grid items (albums, playlists, etc.)
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(
                                        items = section.items.distinctBy { it.id },
                                        key = {
                                            val type = when (it) {
                                                is SongItem -> "song"
                                                is AlbumItem -> "album"
                                                is ArtistItem -> "artist"
                                                is PlaylistItem -> "playlist"
                                                else -> "item"
                                            }
                                            "youtube_${type}_${it.id}"
                                        },
                                    ) { item ->
                                        ArtistShelfCard(
                                            item = item,
                                            palette = palette,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        when (item) {
                                                            is SongItem ->
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        WatchEndpoint(videoId = item.id),
                                                                        item.toMediaMetadata()
                                                                    ),
                                                                )

                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate(
                                                                "artist/${item.id}"
                                                            )

                                                            is PlaylistItem -> navController.navigate(
                                                                "online_playlist/${item.id}"
                                                            )
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            when (item) {
                                                                is SongItem ->
                                                                    YouTubeSongMenu(
                                                                        song = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is AlbumItem ->
                                                                    YouTubeAlbumMenu(
                                                                        albumItem = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is ArtistItem ->
                                                                    YouTubeArtistMenu(
                                                                        artist = item,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is PlaylistItem ->
                                                                    YouTubePlaylistMenu(
                                                                        playlist = item,
                                                                        coroutineScope = coroutineScope,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                            }
                                                        }
                                                    },
                                                )
                                                .animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // FAB for switching between local/remote view
        HideOnScrollFAB(
            visible = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }


    // No bar: the photo runs to the top of the screen with only the back button floating on it,
    // in glass made from this page's own pixels rather than the app's.
    androidx.compose.runtime.CompositionLocalProvider(
        com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop provides artistBackdrop,
    ) {
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 6.dp),
        ) {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        }
    }
}

/**
 * A secondary action beside the artist page's Play pill: a 52dp glyph disc in the page's tint,
 * sized to the pill's height so the row reads as one cluster.
 */
@Composable
private fun ArtistActionButton(
    iconRes: Int,
    contentDescription: String?,
    palette: com.ozyern.exhale.ui.component.ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = palette.elevated.copy(alpha = 0.72f)
    val content = palette.onBackground

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(container)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A small glass chip under the artist's name: one of YouTube's reach figures, with its glyph. */
@Composable
private fun ArtistStatChip(iconRes: Int, text: String, palette: com.ozyern.exhale.ui.component.ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.elevated.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
        )
    }
}

/** The artist's name, large, in [font]: the displayLarge credit, in the face of your choosing. */
@Composable
private fun ArtistDisplayName(name: String, font: ArtistNameFont, color: Color = Color.White) {
    val size = font.size(name)
    Text(
        text = if (font.uppercase) name.uppercase() else name,
        fontFamily = font.family,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.02f,
        letterSpacing = (-0.5).sp,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.artistNameTexture(font),
    )
}

/** Choose the face for this one artist, each option set in itself; or go back to the default. */
@Composable
private fun ArtistFontMenu(name: String, current: ArtistNameFont, onPick: (ArtistNameFont?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Name style",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ArtistNameFont.entries.forEach { font ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (font == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onPick(font) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (font.uppercase) name.uppercase() else name,
                    fontFamily = font.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp * font.scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).artistNameTexture(font),
                )
                Text(
                    text = font.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Use the default",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onPick(null) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * The Play on an artist page: a white capsule with a dark glyph and the word, the one
 * filled control on the page, so it survives whatever colour the photo gives it.
 */
@Composable
private fun ArtistPlayPill(palette: com.ozyern.exhale.ui.component.ArtworkPalette, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.94f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "artistPlayPress",
    )
    Row(
        modifier = Modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.play),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
    }
}

/** One of the artist's top songs: cover, title with its mark, artist, more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTopSongRow(
    song: SongItem,
    palette: com.ozyern.exhale.ui.component.ArtworkPalette,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) palette.accent.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
                    .background(palette.elevated),
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.graphic_eq else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    Icon(
                        painterResource(R.drawable.explicit),
                        contentDescription = null,
                        tint = palette.onBackgroundVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) palette.accent else palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOfNotNull(song.album?.name, song.artists.firstOrNull()?.name).firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.more_vert),
                contentDescription = null,
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A release on an artist's shelf: square cover, title, quiet subtitle. */
@Composable
private fun ArtistShelfCard(
    item: com.ozyern.exhale.innertube.models.YTItem,
    palette: com.ozyern.exhale.ui.component.ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val round = item is ArtistItem
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    val subtitle = when (item) {
        is AlbumItem -> listOfNotNull(item.year?.toString(), if (item.explicit) "Explicit" else null).joinToString(" \u00b7 ")
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is PlaylistItem -> item.author?.name.orEmpty()
        else -> ""
    }
    Column(modifier.width(150.dp)) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(150.dp)
                .clip(shape)
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
                .background(palette.elevated),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
