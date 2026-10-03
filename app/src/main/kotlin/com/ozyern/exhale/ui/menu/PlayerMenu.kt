/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.menu

import com.ozyern.exhale.extensions.toMediaItem
import androidx.compose.foundation.border
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.settingsGlassGroup
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.alpha
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.PlayerSliderColors
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ListItem
import androidx.compose.runtime.LaunchedEffect
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.ui.component.liquid.LiquidSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ozyern.exhale.ui.player.SleepTimerDialog
import com.ozyern.exhale.ui.player.sleepTimerMillisLeft
import com.ozyern.exhale.utils.makeTimeString
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import android.widget.Toast
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ArtistSeparatorsKey
import com.ozyern.exhale.constants.ExternalDownloaderEnabledKey
import com.ozyern.exhale.constants.ExternalDownloaderPackageKey
import com.ozyern.exhale.constants.EqualizerBandLevelsMbKey
import com.ozyern.exhale.constants.EqualizerBassBoostEnabledKey
import com.ozyern.exhale.constants.EqualizerBassBoostStrengthKey
import com.ozyern.exhale.constants.EqualizerCustomProfilesJsonKey
import com.ozyern.exhale.constants.EqualizerEnabledKey
import com.ozyern.exhale.constants.EqualizerOutputGainEnabledKey
import com.ozyern.exhale.constants.EqualizerOutputGainMbKey
import com.ozyern.exhale.constants.EqualizerSelectedProfileIdKey
import com.ozyern.exhale.constants.EqualizerVirtualizerEnabledKey
import com.ozyern.exhale.constants.EqualizerVirtualizerStrengthKey
import com.ozyern.exhale.constants.SpeedDialSongIdsKey
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.playback.EqProfile
import com.ozyern.exhale.playback.EqProfilesPayload
import com.ozyern.exhale.playback.EqualizerJson
import com.ozyern.exhale.playback.ExoDownloadService
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.ListDialog
import com.ozyern.exhale.ui.component.MenuSurfaceSection
import com.ozyern.exhale.ui.component.TextFieldDialog
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import java.util.UUID

@Composable
fun ColumnScope.PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialSongs = remember(speedDialSongIds) {
        speedDialSongIds
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)
    }
    val isInSpeedDial = remember(speedDialSongs, mediaMetadata.id) { mediaMetadata.id in speedDialSongs }

    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?
    )

    val splitArtists = remember(artists, artistSeparators) {
        if (artistSeparators.isEmpty()) {
            artists.map { SplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            artists.flatMap { artist ->
                val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        SplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(SplitArtist(artist.name, artist))
                }
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction { insert(mediaMetadata) }
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message = when {
                playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(splitArtists.distinctBy { it.name }) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(text = splitArtist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
                        if (thumbUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            splitArtist.originalArtist?.let { artist ->
                                navController.navigate("artist/${artist.id}")
                                showSelectArtistDialog = false
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                            }
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    // Sleep timer.
    //
    // It lives here rather than in Player.kt because the collapsed queue bars that used to be its
    // only way in do not all have one — the Apple Music player, which is the default, has no such
    // bar at all — so on a stock install the feature was unreachable. Every player design can open
    // this menu.
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onConfirmMinutes = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(it)
            },
            onConfirmSongs = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.startAfterSongs(it)
            },
            onCancelTimer = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.clear()
            },
        )
    }

    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }
    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    var showEqualizerDialog by rememberSaveable { mutableStateOf(false) }
    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            openSystemEqualizer = {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                }
            },
        )
    }

    val nowPlayingTitle = remember(mediaMetadata.title) {
        mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
    }
    val nowPlayingSubtitle = remember(mediaMetadata.artists) {
        mediaMetadata.artists.joinToString(separator = " • ") { it.name }
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        modifier = modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        // ── The song this is all about ───────────────────────────────────────
        //
        // No card behind it. The sheet is already a card, and a card inside a card is two edges
        // saying the same thing -- which was the shape of the whole menu before this: a header
        // card, a volume card, a grid of tiles, then three more cards, six containers deep before
        // you reached a single verb.
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            ) {
                val thumb = mediaMetadata.thumbnailUrl
                if (thumb.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlayingTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    if (nowPlayingSubtitle.isNotBlank()) {
                        Text(
                            text = nowPlayingSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }

        // ── The five verbs ───────────────────────────────────────────────────
        //
        // These are what the menu is opened for, so they are the only things in it you do not have
        // to read to use: five round targets in a row, each a shape and a colour before it is a
        // word. They were previously six equal squares in a 3x2 grid that also held "Music
        // together" and "Always On Display" -- two destinations given the same weight as liking
        // the song playing, which is the sort of flattening that makes a menu feel long.
        //
        // Everything that is a *place* rather than an *action* moved down into the list.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val liked = librarySong?.song?.liked == true

                PlayerQuickAction(
                    icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                    label = stringResource(R.string.like),
                    // The only one that colours itself. Liking is the one action here with a
                    // lasting state, and the heart is the one glyph that reads at a glance.
                    active = liked,
                    activeColor = MaterialTheme.colorScheme.error,
                    onClick = { playerConnection.toggleLike() },
                    modifier = Modifier.weight(1f),
                )

                PlayerQuickAction(
                    icon = R.drawable.playlist_add,
                    label = stringResource(R.string.add_to_playlist),
                    onClick = { showChoosePlaylistDialog = true },
                    modifier = Modifier.weight(1f),
                )

                val downloadState = download?.state
                PlayerQuickAction(
                    icon = when (downloadState) {
                        Download.STATE_COMPLETED -> R.drawable.offline
                        else -> R.drawable.download
                    },
                    label = stringResource(
                        when (downloadState) {
                            Download.STATE_COMPLETED -> R.string.remove_download
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> R.string.downloading
                            else -> R.string.action_download
                        }
                    ),
                    active = downloadState == Download.STATE_COMPLETED,
                    // In flight, the ring replaces the glyph: a download that is happening should
                    // not look identical to one you could start.
                    busy = downloadState == Download.STATE_QUEUED || downloadState == Download.STATE_DOWNLOADING,
                    onClick = {
                        if (downloadState == null) {
                            database.transaction { insert(mediaMetadata) }
                            val downloadRequest = DownloadRequest
                                .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                .setCustomCacheKey(mediaMetadata.id)
                                .setData(mediaMetadata.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                downloadRequest,
                                false,
                            )
                        } else {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                mediaMetadata.id,
                                false,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )

                PlayerQuickAction(
                    icon = R.drawable.radio,
                    label = stringResource(R.string.start_radio),
                    onClick = {
                        Toast.makeText(context, context.getString(R.string.starting_radio), Toast.LENGTH_SHORT).show()
                        playerConnection.startRadioSeamlessly()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                PlayerQuickAction(
                    icon = R.drawable.link,
                    label = stringResource(R.string.copy_link),
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(
                            context.getString(R.string.copy_link),
                            "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                        )
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Volume ───────────────────────────────────────────────────────────
        if (isQueueTrigger != true) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                PlayerVolumeRow(
                    volume = playerVolume.value,
                    onVolumeChange = { playerConnection.service.playerVolume.value = it },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }

        // ── Everywhere else this song goes, and everything else you can set ──
        //
        // One card, not four. These were previously three or four separate MenuSurfaceSections
        // stacked with 12dp between them, which drew a hard break between "view artist" and
        // "equalizer" as though the two were different kinds of thing to a person scanning for a
        // row. They are not: they are all rows. Dividers inset to the label carry the grouping
        // that the gaps were doing, at a fraction of the vertical cost.
        item {
            MenuSurfaceSection {
                Column {
                    var needsDivider = false

                    @Composable
                    fun Divider() {
                        if (needsDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        needsDivider = true
                    }

                    if (splitArtists.isNotEmpty()) {
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.artist,
                            title = stringResource(R.string.view_artist),
                            onClick = {
                                if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                    onDismiss()
                                    playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                    navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                } else {
                                    showSelectArtistDialog = true
                                }
                            },
                        )
                    }

                    if (mediaMetadata.album != null) {
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.album,
                            title = stringResource(R.string.view_album),
                            onClick = {
                                onDismiss()
                                playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                navController.navigate("album/${mediaMetadata.album.id}")
                            },
                        )
                    }

                    // Sleep timer. Carries its own state in the supporting line, because a timer
                    // you have already set is the single thing on this list you are most likely to
                    // have opened the menu to check.
                    Divider()
                    val sleepTimer = playerConnection.service.sleepTimer
                    val sleepTimerActive = remember(sleepTimer.triggerTime, sleepTimer.songsRemaining) {
                        sleepTimer.isActive
                    }
                    val songsLeft = sleepTimer.songsRemaining

                    PlayerMenuRow(
                        icon = R.drawable.bedtime,
                        title = stringResource(R.string.sleep_timer),
                        subtitle = if (!sleepTimerActive) {
                            null
                        } else if (songsLeft > 0) {
                            pluralStringResource(R.plurals.n_songs_left, songsLeft, songsLeft)
                        } else {
                            makeTimeString(sleepTimerMillisLeft(playerConnection))
                        },
                        highlighted = sleepTimerActive,
                        onClick = { showSleepTimerDialog = true },
                    )

                    // The queue actions, for the song playing: hear it again straight after
                    // itself, or once more at the end of everything queued.
                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.playlist_play,
                        title = stringResource(R.string.play_next),
                        subtitle = "Hear this again right after it ends",
                        onClick = {
                            playerConnection.playNext(mediaMetadata.toMediaItem())
                            Toast.makeText(context, R.string.play_next, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )
                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.queue_music,
                        title = stringResource(R.string.add_to_queue),
                        subtitle = "Once more after everything queued",
                        onClick = {
                            playerConnection.addToQueue(mediaMetadata.toMediaItem())
                            Toast.makeText(context, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )
                    // The "Copy log": what is playing and where it came from, for a bug report.
                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.content_copy,
                        title = "Copy playback details",
                        subtitle = "For a bug report",
                        onClick = {
                            val format = playerConnection.player.audioFormat
                            val report = buildString {
                                appendLine("Exhale playback")
                                appendLine("id: ${mediaMetadata.id}")
                                appendLine("title: ${mediaMetadata.title}")
                                appendLine("artists: ${mediaMetadata.artists.joinToString { it.name }}")
                                appendLine("album: ${mediaMetadata.album?.title}")
                                appendLine("codec: ${format?.sampleMimeType} ${format?.codecs.orEmpty()}")
                                appendLine("bitrate: ${format?.bitrate}")
                                appendLine("sample rate: ${format?.sampleRate} Hz, channels: ${format?.channelCount}")
                                appendLine("position: ${playerConnection.player.currentPosition} / ${playerConnection.player.duration} ms")
                                append("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
                            }
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Exhale playback", report))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )

                    Divider()
                    PlayerMenuRow(
                        icon = if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                        title = stringResource(
                            if (isInSpeedDial) R.string.remove_from_speed_dial
                            else R.string.pin_to_speed_dial
                        ),
                        onClick = {
                            val updatedIds = if (isInSpeedDial) {
                                speedDialSongs.filterNot { it == mediaMetadata.id }
                            } else {
                                (speedDialSongs + mediaMetadata.id).distinct().take(24)
                            }
                            onSpeedDialSongIdsChange(updatedIds.joinToString(","))
                            onDismiss()
                        },
                    )

                    if (isQueueTrigger != true) {
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.equalizer,
                            title = stringResource(R.string.equalizer),
                            onClick = { showEqualizerDialog = true },
                        )

                        Divider()
                        val playbackParameters by playerConnection.playbackParameters.collectAsState()
                        PlayerMenuRow(
                            icon = R.drawable.speed,
                            title = stringResource(R.string.tempo_and_pitch),
                            subtitle = "x${formatMultiplier(playbackParameters.speed)} • x${formatMultiplier(playbackParameters.pitch)}",
                            onClick = { showPitchTempoDialog = true },
                        )
                    }

                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.dark_mode,
                        title = stringResource(R.string.always_on_display),
                        onClick = {
                            onDismiss()
                            playerBottomSheetState.collapseSoft()
                            navController.navigate("always_on_display")
                        },
                    )

                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.fire,
                        title = stringResource(R.string.music_together),
                        onClick = {
                            onDismiss()
                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                            navController.navigate("settings/music_together")
                        },
                    )

                    // A tagged .m4a in Music/Exhale. Songs already on the phone have nothing to save.
                    if (!com.ozyern.exhale.utils.LocalMediaScanner.isLocalId(mediaMetadata.id)) {
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.save_to_device,
                            title = stringResource(R.string.save_to_device),
                            onClick = {
                                onDismiss()
                                if (com.ozyern.exhale.export.SongExporter.get(context).enqueue(listOf(mediaMetadata)) > 0) {
                                    Toast.makeText(context, context.getString(R.string.save_to_device_started), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }

                    if (externalDownloaderEnabled) {
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.download,
                            title = stringResource(R.string.open_with_downloader),
                            onClick = {
                                onDismiss()
                                val url = "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                if (externalDownloaderPackage.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.external_downloader_not_configured),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@PlayerMenuRow
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setPackage(externalDownloaderPackage)
                                    data = android.net.Uri.parse(url)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.external_downloader_not_installed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }

                    // The Share, and which file is playing: the JioSaavn upgrade is invisible
                    // otherwise, and "why does this sound different" deserves an answer.
                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.share,
                        title = stringResource(R.string.share),
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
                            }
                            context.startActivity(android.content.Intent.createChooser(send, null))
                            onDismiss()
                        },
                    )

                    playerConnection.service.streamSourceLabel(mediaMetadata.id)?.let { source ->
                        Divider()
                        PlayerMenuRow(
                            icon = R.drawable.graphic_eq,
                            title = "Playing from",
                            subtitle = source,
                            onClick = {},
                        )
                    }

                    Divider()
                    PlayerMenuRow(
                        icon = R.drawable.info,
                        title = stringResource(R.string.details),
                        onClick = {
                            onShowDetailsDialog()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/**
 * One of the five round verbs at the top of the menu.
 *
 * A circle with a word under it rather than a labelled tile, because at this size the glyph is the
 * control and the label is the caption. [active] is for actions with a lasting state -- liked,
 * downloaded -- which are the only ones that should look different from the rest when you open the
 * sheet; everything else is a thing you are about to do, and a row of five differently-coloured
 * "about to do" buttons is a row with no emphasis at all.
 */
@Composable
private fun PlayerQuickAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Color.Unspecified,
    busy: Boolean = false,
) {
    val accent = if (activeColor == Color.Unspecified) MaterialTheme.colorScheme.primary else activeColor
    val container by animateColorAsState(
        if (active) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        label = "quickActionBg",
    )
    val content by animateColorAsState(
        if (active) accent else MaterialTheme.colorScheme.onSurface,
        label = "quickActionFg",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "quickActionScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(container)
                // The glass rim the circles carry.
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                LoadingRing(
                    modifier = Modifier.size(22.dp),
                    stroke = 2.dp,
                    color = content,
                )
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = content,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
        )
    }
}

/**
 * One row of the menu's single list.
 *
 * A chevron on every row that leads somewhere and none on the rows that do something in place, so
 * the list says which of its entries close the sheet before you tap them.
 */
@Composable
private fun PlayerMenuRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    highlighted: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Volume, as a line rather than a card.
 *
 * It used to be a 28dp-rounded Surface with its own title row and percentage readout, roughly the
 * same visual weight as the song header above it -- a lot of sheet spent on a control most people
 * never touch here because the hardware keys are right there. It keeps its full width and its
 * number, and gives back the box.
 */
@Composable
private fun PlayerVolumeRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeVolume = volume.coerceIn(0f, 1f)
    var previousVolume by remember { mutableFloatStateOf(0.5f) }
    val isMuted = safeVolume == 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Icon(
            painter = painterResource(
                if (isMuted) R.drawable.volume_off else R.drawable.volume_up
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clickable {
                    if (isMuted) {
                        onVolumeChange(if (previousVolume > 0f) previousVolume else 0.5f)
                    } else {
                        previousVolume = safeVolume
                        onVolumeChange(0f)
                    }
                },
        )

        LiquidSlider(
            value = safeVolume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "${(safeVolume * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeSliderL(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val safeValue = value.coerceIn(0f, 1f)
    var sliderValue by remember { mutableFloatStateOf(safeValue) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(safeValue) { if (!isDragging) sliderValue = safeValue }

    Slider(
        value = sliderValue,
        onValueChange = { updated ->
            isDragging = true
            val coerced = updated.coerceIn(0f, 1f)
            sliderValue = coerced
            onValueChange(coerced)
        },
        onValueChangeFinished = { isDragging = false },
        valueRange = 0f..1f,
        modifier = modifier.height(36.dp),
        thumb = {
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
    val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

    var tempo by remember { mutableFloatStateOf(initialSpeed.safeCoerceIn(TempoMin, TempoMax, fallback = 1f)) }
    var pitch by remember { mutableFloatStateOf(initialPitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f)) }
    var pitchMode by rememberSaveable {
        mutableStateOf(if (isPitchSemitoneAligned(pitch)) PitchMode.Semitones else PitchMode.Multiplier)
    }

    val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
        playerConnection.player.playbackParameters = PlaybackParameters(
            speed.coerceIn(TempoMin, TempoMax),
            pitchMultiplier.coerceIn(PitchMin, PitchMax),
        )
    }

    DefaultDialog(
        onDismiss = onDismiss,
        wide = true,
        title = {
            Text(
                text = stringResource(R.string.tempo_and_pitch),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TunerHeading(
                iconRes = R.drawable.speed,
                label = stringResource(R.string.tempo),
                value = "\u00d7${formatMultiplier(tempo)}",
            )

            TunerStepperRow(
                value = multiplierToSlider(tempo),
                onValueChange = { slider ->
                    val updated = sliderToMultiplier(slider).quantize(0.01f)
                    if (abs(updated - tempo) >= 0.005f) {
                        tempo = updated
                        applyPlaybackParameters(tempo, pitch)
                    }
                },
                canDecrease = tempo > TempoMin,
                canIncrease = tempo < TempoMax,
                onDecrease = {
                    tempo = (tempo - 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                    applyPlaybackParameters(tempo, pitch)
                },
                onIncrease = {
                    tempo = (tempo + 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                    applyPlaybackParameters(tempo, pitch)
                },
            )

            TunerChipRow(
                options = MultiplierPresets.map { "\u00d7${formatMultiplier(it)}" },
                selectedIndex = MultiplierPresets.indexOfFirst { abs(tempo - it) < 0.005f },
                onSelect = { index ->
                    tempo = MultiplierPresets[index]
                    applyPlaybackParameters(tempo, pitch)
                },
            )

            TunerRule()

            TunerHeading(
                iconRes = R.drawable.discover_tune,
                label = stringResource(R.string.pitch),
                value = when (pitchMode) {
                    PitchMode.Semitones -> {
                        val semitones = pitchToSemitones(pitch)
                        "${if (semitones > 0) "+" else ""}$semitones"
                    }

                    PitchMode.Multiplier -> "\u00d7${formatMultiplier(pitch)}"
                },
            )

            // A segmented control, not two chips. "st" and "\u00d7" in a pair of circles is a
            // riddle; iOS asks this kind of either/or question with one track and a sliding
            // thumb, and the words fit because there are only two of them.
            TunerSegmentedControl(
                options = listOf(
                    stringResource(R.string.pitch_mode_semitones),
                    stringResource(R.string.pitch_mode_multiplier),
                ),
                selectedIndex = if (pitchMode == PitchMode.Semitones) 0 else 1,
                onSelect = { index ->
                    pitchMode = if (index == 0) PitchMode.Semitones else PitchMode.Multiplier
                },
            )

            when (pitchMode) {
                PitchMode.Semitones -> {
                    val currentSemitones = pitchToSemitones(pitch)
                    TunerSlider(
                        value = currentSemitones.toFloat(),
                        valueRange = -12f..12f,
                        steps = 23,
                        onValueChange = { slider ->
                            val semitones = slider.roundToInt().coerceIn(-12, 12)
                            val updated = semitonesToPitch(semitones)
                            if (abs(updated - pitch) >= 0.0005f) {
                                pitch = updated
                                applyPlaybackParameters(tempo, pitch)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TunerChipRow(
                        options = SemitonePresets.map { "${if (it > 0) "+" else ""}$it" },
                        selectedIndex = SemitonePresets.indexOfFirst { it == currentSemitones },
                        onSelect = { index ->
                            pitch = semitonesToPitch(SemitonePresets[index])
                            applyPlaybackParameters(tempo, pitch)
                        },
                    )
                }

                PitchMode.Multiplier -> {
                    TunerStepperRow(
                        value = multiplierToSlider(pitch),
                        onValueChange = { slider ->
                            val updated = sliderToMultiplier(slider).quantize(0.01f)
                            if (abs(updated - pitch) >= 0.005f) {
                                pitch = updated
                                applyPlaybackParameters(tempo, pitch)
                            }
                        },
                        canDecrease = pitch > PitchMin,
                        canIncrease = pitch < PitchMax,
                        onDecrease = {
                            pitch = (pitch - 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                        onIncrease = {
                            pitch = (pitch + 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    )
                    TunerChipRow(
                        options = MultiplierPresets.map { "\u00d7${formatMultiplier(it)}" },
                        selectedIndex = MultiplierPresets.indexOfFirst { abs(pitch - it) < 0.005f },
                        onSelect = { index ->
                            pitch = MultiplierPresets[index]
                            applyPlaybackParameters(tempo, pitch)
                        },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Reset above Done, both the same size: one undoes the panel, the other closes it,
            // and neither is worth a 52dp slab of accent in a dialog this small.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TunerAction(
                    label = stringResource(R.string.reset),
                    filled = false,
                    enabled = abs(tempo - 1f) > 0.0005f || abs(pitch - 1f) > 0.0005f,
                    onClick = {
                        tempo = 1f
                        pitch = 1f
                        applyPlaybackParameters(tempo, pitch)
                    },
                    modifier = Modifier.weight(1f),
                )
                TunerAction(
                    label = stringResource(R.string.done),
                    filled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The presets both multiplier sliders offer. */
private val MultiplierPresets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Fifths, fourths and octaves - the intervals anyone actually transposes by. */
private val SemitonePresets = listOf(-12, -7, -5, 0, 5, 7, 12)

/**
 * The line that names a control and states its value.
 *
 * Label on the left in body weight, value on the right in the accent: the value is the only thing
 * on the row that changes, so it is the only thing that gets colour.
 */
@Composable
private fun TunerHeading(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The app's slider, not Material's: same track, same thumb as the one under the artwork. */
@Composable
private fun TunerSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    // The app's own slider - the one the settings pages and the volume row use.
    //
    // Not Material's, and not the seek bar's either: the seek bar is a scrubber with a tall bar
    // thumb and a gap in its track, which is the wrong object for setting a value. This is the
    // glass one, so a control inside a dialog matches the controls outside it.
    LiquidSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Minus, slider, plus - the two discs sized as tap targets rather than as bare glyphs. */
@Composable
private fun TunerStepperRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TunerStepButton(iconRes = R.drawable.remove, enabled = canDecrease, onClick = onDecrease)
        TunerSlider(
            value = value,
            valueRange = 0f..1f,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
        TunerStepButton(iconRes = R.drawable.add, enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun TunerStepButton(
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * A row of presets as capsules.
 *
 * Material's `FilterChip` brings a border, a container colour and a check mark that appears on
 * selection and shoves the label sideways. A capsule that fills with the accent when it is chosen
 * says the same thing without moving anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunerChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // Wrapped, not scrolled. A horizontally scrolling row inside a dialog is a row whose last
    // option is sliced in half by the card's own edge and gives no sign that there is more; seven
    // presets at this size fit in two lines with nothing hidden.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) accent
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Two mutually exclusive words on one track.
 *
 * The iOS segmented control: a recessed capsule with a raised thumb that slides between the
 * options, so the choice reads as a switch with positions rather than as two buttons that happen
 * to disagree.
 */
@Composable
private fun TunerSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // The menu is a sheet over the page, so the glass refracts nothing of its own rather than
    // reading a page recording it may be drawn inside.
    com.ozyern.exhale.ui.component.liquid.LiquidSegmentedTabs(
        labels = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        height = 40.dp,
        backdrop = com.ozyern.exhale.ui.component.liquid.rememberInContentBackdrop(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The hairline between the two halves of the panel, inset the way a grouped table insets one. */
@Composable
private fun TunerRule() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** The panel's buttons: same capsule, filled for the one that closes it. */
@Composable
private fun TunerAction(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(if (filled) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else accent,
        )
    }
}

private enum class PitchMode { Semitones, Multiplier }

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.safeCoerceIn(min: Float, max: Float, fallback: Float): Float {
    val safe = if (this.isFinite()) this else fallback
    return safe.coerceIn(min, max)
}

private fun Float.quantize(step: Float): Float {
    if (step <= 0f) return this
    return (round(this / step) * step).coerceAtLeast(0f)
}

private fun pitchToSemitones(pitch: Float): Int {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    return (12f * log2(safePitch)).roundToInt().coerceIn(-12, 12)
}

private fun semitonesToPitch(semitones: Int): Float =
    2f.pow(semitones.toFloat() / 12f).coerceIn(PitchMin, PitchMax)

private fun isPitchSemitoneAligned(pitch: Float): Boolean {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    val semitones = (12f * log2(safePitch)).roundToInt()
    val reconstructed = 2f.pow(semitones.toFloat() / 12f)
    return abs(reconstructed - pitch) < 0.0015f
}

private fun formatMultiplier(multiplier: Float): String = String.format("%.2f", multiplier)

private fun sliderToMultiplier(slider: Float): Float {
    val t = slider.coerceIn(0f, 1f)
    val y = (t - 0.5f) * 2f
    val curve = 2.2f
    val absY = abs(y).pow(curve)
    val shaped = when {
        y > 0f -> absY
        y < 0f -> -absY
        else -> 0f
    }
    val exponent = if (y < 0f) 2f * shaped else shaped
    return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
    val m = multiplier.coerceIn(TempoMin, TempoMax)
    val log = log2(m)
    val curve = 2.2f
    val shaped = if (m < 1f) (log / 2f) else log
    val absShaped = abs(shaped).pow(1f / curve)
    val y = when {
        shaped > 0f -> absShaped
        shaped < 0f -> -absShaped
        else -> 0f
    }
    return (0.5f + y / 2f).coerceIn(0f, 1f)
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(onDismiss: () -> Unit, openSystemEqualizer: () -> Unit) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val eqCapabilities by playerConnection.service.eqCapabilities.collectAsState()

    val (eqEnabled, setEqEnabled) = rememberPreference(EqualizerEnabledKey, defaultValue = false)
    val (selectedProfileId, setSelectedProfileId) = rememberPreference(EqualizerSelectedProfileIdKey, defaultValue = "flat")
    val (bandLevelsRaw, setBandLevelsRaw) = rememberPreference(EqualizerBandLevelsMbKey, defaultValue = "")
    val (outputGainEnabled, setOutputGainEnabled) = rememberPreference(EqualizerOutputGainEnabledKey, defaultValue = false)
    val (outputGainMb, setOutputGainMb) = rememberPreference(EqualizerOutputGainMbKey, defaultValue = 0)
    val (bassBoostEnabled, setBassBoostEnabled) = rememberPreference(EqualizerBassBoostEnabledKey, defaultValue = false)
    val (bassBoostStrength, setBassBoostStrength) = rememberPreference(EqualizerBassBoostStrengthKey, defaultValue = 0)
    val (virtualizerEnabled, setVirtualizerEnabled) = rememberPreference(EqualizerVirtualizerEnabledKey, defaultValue = false)
    val (virtualizerStrength, setVirtualizerStrength) = rememberPreference(EqualizerVirtualizerStrengthKey, defaultValue = 0)
    val (customProfilesJson, setCustomProfilesJson) = rememberPreference(EqualizerCustomProfilesJsonKey, defaultValue = "")

    val caps = eqCapabilities
    val bandCount = caps?.bandCount ?: 0
    val minMb = caps?.minBandLevelMb ?: -1500
    val maxMb = caps?.maxBandLevelMb ?: 1500

    var outputGainLocal by rememberSaveable { mutableIntStateOf(outputGainMb) }
    LaunchedEffect(outputGainMb) { outputGainLocal = outputGainMb }

    var bassBoostStrengthLocal by rememberSaveable { mutableIntStateOf(bassBoostStrength) }
    LaunchedEffect(bassBoostStrength) { bassBoostStrengthLocal = bassBoostStrength }

    var virtualizerStrengthLocal by rememberSaveable { mutableIntStateOf(virtualizerStrength) }
    LaunchedEffect(virtualizerStrength) { virtualizerStrengthLocal = virtualizerStrength }

    var bandLevelsMb by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(bandLevelsRaw, bandCount) {
        bandLevelsMb = resampleLevelsByIndex(decodeBandLevelsMb(bandLevelsRaw), bandCount)
    }

    val profiles = remember(customProfilesJson) { decodeProfilesPayload(customProfilesJson).profiles }
    val activeProfileId = selectedProfileId.removePrefix("profile:").takeIf { selectedProfileId.startsWith("profile:") }
    val activeProfile = remember(profiles, activeProfileId) { profiles.firstOrNull { it.id == activeProfileId } }

    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showManageProfilesDialog by rememberSaveable { mutableStateOf(false) }
    var showImportProfilesDialog by rememberSaveable { mutableStateOf(false) }

    if (showSaveProfileDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.eq_save_profile)) },
            placeholder = { Text(text = stringResource(R.string.eq_profile_name)) },
            onDone = { name ->
                val trimmed = name.trim()
                if (trimmed.isNotBlank()) {
                    val newProfile = EqProfile(
                        id = UUID.randomUUID().toString(), name = trimmed,
                        bandCenterFreqHz = caps?.centerFreqHz.orEmpty(), bandLevelsMb = bandLevelsMb,
                        outputGainMb = outputGainMb, bassBoostStrength = bassBoostStrength, virtualizerStrength = virtualizerStrength,
                    )
                    val updatedPayload = EqProfilesPayload(profiles = (profiles + newProfile).distinctBy { it.id }.sortedBy { it.name.lowercase() })
                    setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                    setSelectedProfileId("profile:${newProfile.id}")
                }
            },
            onDismiss = { showSaveProfileDialog = false },
        )
    }

    if (showImportProfilesDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.eq_import_profiles)) },
            placeholder = { Text(text = stringResource(R.string.eq_import_profiles_placeholder)) },
            singleLine = false, maxLines = 10, isInputValid = { it.trim().isNotBlank() },
            onDone = { raw ->
                val trimmed = raw.trim()
                val payload = decodeProfilesPayload(trimmed).takeIf { it.profiles.isNotEmpty() }
                    ?: runCatching { EqProfilesPayload(EqualizerJson.json.decodeFromString<List<EqProfile>>(trimmed)) }.getOrNull()
                    ?: EqProfilesPayload()
                if (payload.profiles.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.eq_import_failed), Toast.LENGTH_SHORT).show()
                    return@TextFieldDialog
                }
                val existingIds = profiles.map { it.id }.toMutableSet()
                val normalizedImported = payload.profiles.map { p ->
                    val baseName = p.name.trim().ifBlank { context.getString(R.string.eq_imported_profile) }
                    val incomingId = p.id.trim()
                    val finalId = if (incomingId.isBlank() || !existingIds.add(incomingId)) {
                        generateSequence { UUID.randomUUID().toString() }.first { existingIds.add(it) }
                    } else incomingId
                    p.copy(id = finalId, name = baseName)
                }
                val updatedPayload = EqProfilesPayload(profiles = (profiles + normalizedImported).distinctBy { it.id }.sortedBy { it.name.lowercase() })
                setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                normalizedImported.firstOrNull()?.id?.let { setSelectedProfileId("profile:$it") }
                Toast.makeText(context, context.getString(R.string.eq_import_success, normalizedImported.size), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showImportProfilesDialog = false },
        )
    }

    if (showManageProfilesDialog) {
        ListDialog(onDismiss = { showManageProfilesDialog = false }, modifier = Modifier.fillMaxWidth()) {
            items(items = profiles, key = { it.id }) { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            setEqEnabled(true)
                            setBandLevelsRaw(encodeBandLevelsMb(profile.bandLevelsMb))
                            setOutputGainMb(profile.outputGainMb)
                            setOutputGainEnabled(profile.outputGainMb != 0)
                            setBassBoostStrength(profile.bassBoostStrength)
                            setBassBoostEnabled(profile.bassBoostStrength != 0)
                            setVirtualizerStrength(profile.virtualizerStrength)
                            setVirtualizerEnabled(profile.virtualizerStrength != 0)
                            setSelectedProfileId("profile:${profile.id}")
                            showManageProfilesDialog = false
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = stringResource(R.string.eq_custom_profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val updatedPayload = EqProfilesPayload(profiles = profiles.filterNot { it.id == profile.id })
                        setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
                        if (selectedProfileId == "profile:${profile.id}") setSelectedProfileId("manual")
                    }) {
                        Icon(painter = painterResource(R.drawable.delete), contentDescription = null)
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The bar holds the name and the way out, and nothing else.
                //
                // The master switch used to live in the toolbar, where a switch has no label and
                // no group - it read as an action rather than as the thing that turns the whole
                // page on. It is the first row of the first card now, which is where a phone puts
                // the switch that governs a settings screen.
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.equalizer),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                    Spacer(Modifier.height(12.dp))

                    if (caps == null || bandCount <= 0) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                LoadingRing()
                                Spacer(Modifier.height(16.dp))
                                Text(text = stringResource(R.string.eq_waiting_for_audio_session), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = openSystemEqualizer) { Text(text = stringResource(R.string.eq_open_system_equalizer)) }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        return@Column
                    }

                    EqCard {
                        EqSwitchRow(
                            label = stringResource(R.string.equalizer),
                            checked = eqEnabled,
                            onCheckedChange = {
                                setEqEnabled(it)
                                if (it && selectedProfileId.isBlank()) setSelectedProfileId("manual")
                            },
                        )
                    }

                    // Everything below the master switch dims when it is off: the controls stay
                    // readable (you can see what the preset was) but stop inviting a drag that
                    // would do nothing.
                    val liveAlpha = if (eqEnabled) 1f else 0.45f

                    Spacer(Modifier.height(18.dp))

                    EqSection(
                        title = stringResource(R.string.eq_presets),
                        alpha = liveAlpha,
                        trailing = {
                            EqTextAction(
                                label = stringResource(R.string.eq_system),
                                onClick = openSystemEqualizer,
                            )
                        },
                    ) {
                        // Wrapped, so the last preset is not sliced in half by the card's edge.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            EqPresetChip(
                                label = stringResource(R.string.eq_flat),
                                selected = selectedProfileId == "flat",
                                onClick = {
                                    playerConnection.service.applyEqFlatPreset()
                                    setSelectedProfileId("flat")
                                },
                            )
                            caps.systemPresets.forEachIndexed { index, name ->
                                EqPresetChip(
                                    label = name,
                                    selected = selectedProfileId == "system:$index",
                                    onClick = {
                                        playerConnection.service.applySystemEqPreset(index)
                                        setSelectedProfileId("system:$index")
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    EqSection(title = stringResource(R.string.eq_profiles), trailing = { TextButton(onClick = { showManageProfilesDialog = true }) { Text(text = stringResource(R.string.eq_manage)) } }) {
                        val subtitle = when {
                            selectedProfileId == "flat" -> stringResource(R.string.eq_flat)
                            selectedProfileId.startsWith("system:") -> stringResource(R.string.eq_system_preset)
                            activeProfile != null -> activeProfile.name
                            else -> stringResource(R.string.eq_manual)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = subtitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = stringResource(R.string.eq_profile_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showSaveProfileDialog = true }) { Text(text = stringResource(R.string.eq_save)) }
                            TextButton(onClick = { showImportProfilesDialog = true }) { Text(text = stringResource(R.string.eq_import)) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    EqSection(title = stringResource(R.string.eq_bands), trailing = {
                        TextButton(onClick = { setSelectedProfileId("manual"); setBandLevelsRaw(encodeBandLevelsMb(List(bandCount) { 0 })) }) { Text(text = stringResource(R.string.reset)) }
                    }) {
                        caps.centerFreqHz.forEachIndexed { band, hz ->
                            val label = formatHz(hz)
                            val value = bandLevelsMb.getOrNull(band) ?: 0
                            val valueDb = (value / 100f).coerceIn(-24f, 24f)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                                Text(text = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                                // The app's slider, the same one the settings pages use. Ten of
                                // Material's in a column is ten different-looking controls doing
                                // one job; the glass one is what the rest of the app means by
                                // "drag this".
                                LiquidSlider(
                                    value = value.toFloat().coerceIn(minMb.toFloat(), maxMb.toFloat()),
                                    onValueChange = { newValue ->
                                        val coerced = newValue.toInt().coerceIn(minMb, maxMb)
                                        bandLevelsMb = bandLevelsMb.toMutableList().apply { while (size < bandCount) add(0); set(band, coerced) }
                                    },
                                    onValueChangeFinished = { setSelectedProfileId("manual"); setBandLevelsRaw(encodeBandLevelsMb(bandLevelsMb)) },
                                    valueRange = minMb.toFloat()..maxMb.toFloat(),
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = formatDb(valueDb), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // One group, three effects.
                    //
                    // Three separate cards, each with a title, a switch, a slider and a number,
                    // is three pages of furniture for three settings of the same kind. Grouped,
                    // they read as a list you can scan: name on the left, value on the right, the
                    // switch that owns the row at the end, and the slider under it.
                    EqSection(title = stringResource(R.string.eq_effects), alpha = liveAlpha) {
                        EqEffectBlock(
                            label = stringResource(R.string.eq_output_gain),
                            value = formatDb(outputGainLocal / 100f),
                            enabled = outputGainEnabled,
                            onEnabledChange = { setSelectedProfileId("manual"); setOutputGainEnabled(it) },
                            sliderValue = outputGainLocal,
                            valueRange = -1500..1500,
                            onValueChange = { outputGainLocal = it },
                            onValueChangeFinished = { setSelectedProfileId("manual"); setOutputGainMb(outputGainLocal) },
                        )
                        EqRule()
                        EqEffectBlock(
                            label = stringResource(R.string.eq_bass_boost),
                            value = "${bassBoostStrengthLocal / 10}%",
                            enabled = bassBoostEnabled,
                            onEnabledChange = { setSelectedProfileId("manual"); setBassBoostEnabled(it) },
                            sliderValue = bassBoostStrengthLocal,
                            valueRange = 0..1000,
                            onValueChange = { bassBoostStrengthLocal = it },
                            onValueChangeFinished = { setSelectedProfileId("manual"); setBassBoostStrength(bassBoostStrengthLocal) },
                        )
                        EqRule()
                        EqEffectBlock(
                            label = stringResource(R.string.eq_virtualizer),
                            value = "${virtualizerStrengthLocal / 10}%",
                            enabled = virtualizerEnabled,
                            onEnabledChange = { setSelectedProfileId("manual"); setVirtualizerEnabled(it) },
                            sliderValue = virtualizerStrengthLocal,
                            valueRange = 0..1000,
                            onValueChange = { virtualizerStrengthLocal = it },
                            onValueChangeFinished = { setSelectedProfileId("manual"); setVirtualizerStrength(virtualizerStrengthLocal) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A section: its name above the card, not inside it.
 *
 * Every settings page in this app is built this way - a quiet footnote label, then the plate the
 * rows sit on. The equalizer was the one screen that put a `titleMedium` heading *inside* a
 * Material `surfaceContainer` card, so each of its five sections read as a little titled panel
 * and none of them matched the app they are part of.
 */
@Composable
private fun EqSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    alpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        EqCard(content = content)
    }
}

/** The plate. The same one the settings pages use, so the page belongs to the app. */
@Composable
private fun EqCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
        content = content,
    )
}

/** The hairline between rows of a group: inset, never at an edge. */
@Composable
private fun EqRule() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}

/** A section's one action, as text in the accent rather than a Material button. */
@Composable
private fun EqTextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Name on the left, switch on the right: the row that governs a page. */
@Composable
private fun EqSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        LiquidToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One effect: what it is, what it is set to, whether it is on, and the slider that sets it.
 *
 * The value belongs on the label line rather than at the end of the slider - a number that moves
 * while your thumb is on the track is easier to read where the eye already is, and it stops the
 * track being squeezed into whatever is left after a toggle and a 72dp column.
 */
@Composable
private fun EqEffectBlock(
    label: String,
    value: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sliderValue: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            LiquidToggle(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(2.dp))
        LiquidSlider(
            value = sliderValue.toFloat().coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
            onValueChange = { onValueChange(it.toInt().coerceIn(valueRange.first, valueRange.last)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
private fun EqToggleSliderRow(
    enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    value: Int, onValueChange: (Int) -> Unit,
    valueRange: IntRange, formatValue: (Int) -> String,
    modifier: Modifier = Modifier, onValueChangeFinished: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LiquidToggle(
            checked = enabled, onCheckedChange = onEnabledChange,
        )
        Spacer(Modifier.width(12.dp))
        LiquidSlider(
            value = value.toFloat().coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
            onValueChange = { onValueChange(it.toInt().coerceIn(valueRange.first, valueRange.last)) },
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.4f),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = formatValue(value), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
    }
}

private fun decodeBandLevelsMb(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
}

private fun encodeBandLevelsMb(levelsMb: List<Int>): String =
    runCatching { EqualizerJson.json.encodeToString(levelsMb) }.getOrNull().orEmpty()

private fun decodeProfilesPayload(raw: String?): EqProfilesPayload {
    if (raw.isNullOrBlank()) return EqProfilesPayload()
    return runCatching { EqualizerJson.json.decodeFromString<EqProfilesPayload>(raw) }.getOrNull() ?: EqProfilesPayload()
}

private fun encodeProfilesPayload(payload: EqProfilesPayload): String =
    runCatching { EqualizerJson.json.encodeToString(payload) }.getOrNull().orEmpty()

private fun resampleLevelsByIndex(levelsMb: List<Int>, targetCount: Int): List<Int> {
    if (targetCount <= 0) return emptyList()
    if (levelsMb.isEmpty()) return List(targetCount) { 0 }
    if (levelsMb.size == targetCount) return levelsMb
    if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)
    val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
    return List(targetCount) { i ->
        val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
        val lo = kotlin.math.floor(pos).toInt().coerceIn(0, levelsMb.lastIndex)
        val hi = kotlin.math.ceil(pos).toInt().coerceIn(0, levelsMb.lastIndex)
        val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
        (levelsMb[lo] + ((levelsMb[hi] - levelsMb[lo]) * t)).toInt()
    }
}

private fun formatHz(hz: Int): String {
    if (hz <= 0) return ""
    return if (hz >= 1000) "${(hz / 1000f).let { round(it * 10f) / 10f }}k" else hz.toString()
}

private fun formatDb(db: Float): String {
    val rounded = round(db * 10f) / 10f
    return "${if (rounded > 0f) "+" else ""}$rounded dB"
}


/**
 * An equalizer preset, as a capsule.
 *
 * Same shape as the tempo panel's presets, for the same reason: a `FilterChip` brings a border,
 * a container colour of its own and a check mark that slides in on selection and pushes the label
 * sideways. Filling with the accent says "this one" without moving anything.
 */
@Composable
private fun EqPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
