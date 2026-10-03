/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.player

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.ozyern.exhale.ui.component.PlayingIndicatorBox
import androidx.activity.compose.BackHandler
import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.constants.PlayerDesignStyle
import com.ozyern.exhale.constants.PlayerDesignStyleKey
import com.ozyern.exhale.constants.QueueEditLockKey
import com.ozyern.exhale.constants.AutoLoadMoreKey
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.extensions.move
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toggleRepeatMode
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.BottomSheet
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.MediaMetadataListItem
import com.ozyern.exhale.ui.menu.PlayerMenu
import com.ozyern.exhale.ui.menu.SelectionMediaMetadataMenu
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.datastore.preferences.core.booleanPreferencesKey


@SuppressLint("UnrememberedMutableState", "LocalContextGetResourceValueCall", "StringFormatInvalid")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onBackgroundColor: Color,
    /** The artwork's colours, as the player draws its background from. Empty for a plain surface. */
    artworkColors: List<Color> = emptyList(),
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onShowLyrics: () -> Unit = {},
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }
    var selection by remember { mutableStateOf(false) }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)
    var infiniteQueueEnabled by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsState()
    val togetherForcesLock =
        togetherSessionState is com.ozyern.exhale.together.TogetherSessionState.Joined &&
                (togetherSessionState as com.ozyern.exhale.together.TogetherSessionState.Joined).role is com.ozyern.exhale.together.TogetherRole.Guest
    val effectiveLocked = locked || togetherForcesLock

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V8
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.songsRemaining
    ) {
        playerConnection.service.sleepTimer.isActive
    }
    var sleepTimerTimeLeft by remember { mutableStateOf(0L) }

    val (showCodecOnPlayer) = rememberPreference(
        key = booleanPreferencesKey("show_codec_on_player"),
        defaultValue = false
    )

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft = sleepTimerMillisLeft(playerConnection)
                delay(1000L)
            }
        }
    }

    BottomSheet(
        state = state,
        backgroundColor = Color.Unspecified,
        modifier = modifier,
        collapsedContent = {
            when (playerDesignStyle) {
                PlayerDesignStyle.V2 -> {
                    QueueCollapsedContentV2(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        repeatMode = repeatMode,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
                }

                PlayerDesignStyle.V3 -> {
                    QueueCollapsedContentV3(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
                }

                PlayerDesignStyle.V5 -> {
                    QueueCollapsedContentV3(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
                }

                PlayerDesignStyle.V4 -> {
                    QueueCollapsedContentV4(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics
                    )
                }

                PlayerDesignStyle.V1 -> {
                    QueueCollapsedContentV1(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics
                    )
                }

                PlayerDesignStyle.V6 -> {
                    QueueCollapsedContentV4(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = { state.expandSoft() },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics
                    )
                }

                PlayerDesignStyle.V7 -> {
                    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
                    val activeDevice = remember(audioManager) {
                        audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET }
                            ?.productName?.toString() ?: "Speaker"
                    }
                    QueueCollapsedContentV7(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        onExpandQueue = { state.expandSoft() },
                        onShowLyrics = onShowLyrics,
                        onDeviceClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        deviceName = activeDevice
                    )
                }

                // ============================================================
                // V8 - Apple Music Style Collapsed Content
                // ============================================================
                PlayerDesignStyle.V8 -> {
                    QueueCollapsedContentV8(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        onShowLyrics = onShowLyrics,
                        onExpandQueue = { state.expandSoft() },
                    )
                }
            }

            if (showSleepTimerDialog) {
                SleepTimerDialog(
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirmMinutes = { minutes ->
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(minutes)
                    },
                    onConfirmSongs = { songs ->
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.startAfterSongs(songs)
                    },
                    onCancelTimer = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.clear()
                    },
                )
            }
        },
    ) {
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength by remember {
            derivedStateOf {
                queueWindows.sumOf { it.mediaItem.metadata?.duration ?: 0 }
            }
        }

        val coroutineScope = rememberCoroutineScope()



        val headerItems = 1
        val lazyListState = rememberLazyListState()

        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        var shouldScrollToCurrent by remember { mutableStateOf(false) }
        var lastScrolledUid by remember { mutableStateOf<Long?>(null) }

        val currentPlayingUid = remember(currentWindowIndex, queueWindows) {
            if (currentWindowIndex in queueWindows.indices) {
                queueWindows[currentWindowIndex].uid
            } else null
        }

        // Where the playing song sits in the list as it is drawn (shuffle reorders it), so the songs
        // already heard can step back and the ones to come can be named.
        val activeListIndex by remember {
            derivedStateOf { mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid } }
        }

        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars.add(
                WindowInsets(
                    top = ListItemHeight,
                    bottom = ListItemHeight
                )
            ).asPaddingValues()
        ) { from, to ->
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                from.index to to.index
            } else {
                currentDragInfo.first to to.index
            }

            val safeFrom = (from.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)
            val safeTo = (to.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)

            mutableQueueWindows.move(safeFrom, safeTo)

            if (selection && currentWindowIndex in mutableQueueWindows.indices) {
                val draggedItemUid = mutableQueueWindows[if (to.index > from.index) safeTo else safeFrom].uid
                val currentItem = queueWindows.getOrNull(currentWindowIndex)

                if (currentItem?.uid == draggedItemUid) {
                    val newIndex = mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                    if (newIndex != -1) {
                        selectedSongs.clear()
                        selectedItems.clear()
                        mutableQueueWindows.getOrNull(newIndex)?.let { window ->
                            window.mediaItem.metadata?.let { metadata ->
                                selectedSongs.add(metadata)
                                selectedItems.add(window)
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(mutableQueueWindows) {
            if (mutableQueueWindows.isNotEmpty() && !shouldScrollToCurrent) {
                shouldScrollToCurrent = true
            }
        }

        LaunchedEffect(currentPlayingUid, shouldScrollToCurrent) {
            if (currentPlayingUid != null && shouldScrollToCurrent) {
                val indexInMutableList = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                if (indexInMutableList != -1) {
                    lazyListState.scrollToItem(indexInMutableList + 1)
                }
            }
        }

        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (from, to) ->
                    val safeFrom = (from - headerItems).coerceIn(0, queueWindows.lastIndex)
                    val safeTo = (to - headerItems).coerceIn(0, queueWindows.lastIndex)

                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.player.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows.map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(safeFrom, safeTo)
                                    .toIntArray(),
                                System.currentTimeMillis()
                            )
                        )
                    }
                    dragInfo = null
                }
            }
        }

        LaunchedEffect(queueWindows) {
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(state.isCollapsed) {
            if (!state.isCollapsed && currentPlayingUid != null) {
                val indexInMutableList = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                if (indexInMutableList != -1) {
                    // Scroll to the item + headerItems (Spacer)
                    // The Spacer is at index 0, so the first song is at index 1.
                    // If indexInMutableList is 0 (first song), we want to scroll to index 1.
                    lazyListState.scrollToItem(indexInMutableList + 1)
                }
            }
        }

        val tinted = artworkColors.isNotEmpty()
        val ink = if (tinted) Color.White else onBackgroundColor
        val queueBrush = remember(artworkColors, backgroundColor) {
            if (tinted) {
                val top = artworkColors.first()
                val bottom = artworkColors.getOrNull(1) ?: top
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(top, Color.Black, 0.40f),
                        androidx.compose.ui.graphics.lerp(bottom, Color.Black, 0.62f),
                    ),
                )
            } else {
                androidx.compose.ui.graphics.SolidColor(backgroundColor)
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(queueBrush),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CurrentSongHeader(
                    sheetState = state,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = playerConnection.player.shuffleModeEnabled,
                    locked = effectiveLocked,
                    songCount = queueWindows.size,
                    queueDuration = queueLength,
                    infiniteQueueEnabled = infiniteQueueEnabled,
                    backgroundColor = Color.Transparent,
                    onBackgroundColor = ink,
                    onToggleLike = {
                        playerConnection.service.toggleLike()
                    },
                    onMenuClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                onShowDetailsDialog = {
                                    mediaMetadata?.id?.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss
                            )
                        }
                    },
                    onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                    onShuffleClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                        }
                    },
                    onLockClick = {
                        if (togetherForcesLock) {
                            Toast.makeText(context, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                        } else {
                            locked = !locked
                        }
                    },
                    onInfiniteQueueClick = {
                        val nextInfiniteQueueEnabled = !infiniteQueueEnabled
                        infiniteQueueEnabled = nextInfiniteQueueEnabled
                        if (nextInfiniteQueueEnabled) {
                            playerConnection.service.onInfiniteQueueEnabled()
                        } else {
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    }
                )

                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(
                                WindowInsets(
                                    bottom = ListItemHeight + 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {

                    item {
                        Spacer(
                            modifier =
                                Modifier
                                    .animateContentSize()
                                    .height(if (selection) 48.dp else 0.dp),
                        )
                    }

                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, item -> item.uid.hashCode() },
                    ) { index, window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.uid.hashCode(),
                        ) {
                            val currentItem by rememberUpdatedState(window)
                            val isActive = window.uid == currentPlayingUid
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance }
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dv = dismissBoxState.currentValue
                                if (!processedDismiss && (
                                            dv == SwipeToDismissBoxValue.StartToEnd ||
                                                    dv == SwipeToDismissBoxValue.EndToStart
                                            )
                                ) {
                                    processedDismiss = true
                                    playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                    dismissJob?.cancel()
                                    dismissJob = coroutineScope.launch {
                                        val snackbarResult = snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.removed_song_from_playlist,
                                                currentItem.mediaItem.metadata?.title,
                                            ),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                                            playerConnection.player.addMediaItem(currentItem.mediaItem)
                                            playerConnection.player.moveMediaItem(
                                                mutableQueueWindows.size,
                                                currentItem.firstPeriodIndex,
                                            )
                                        }
                                    }
                                }
                                if (dv == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val heard = activeListIndex >= 0 && index < activeListIndex
                            val openMenu: (MediaMetadata) -> Unit = { trackMetadata ->
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = trackMetadata,
                                        navController = navController,
                                        playerBottomSheetState = playerBottomSheetState,
                                        isQueueTrigger = true,
                                        onShowDetailsDialog = {
                                            window.mediaItem.mediaId.let {
                                                bottomSheetPageState.show { ShowMediaInfo(it) }
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                            val onRowClick: () -> Unit = onRowClick@{
                                if (index == currentWindowIndex) {
                                    playerConnection.player.togglePlayPause()
                                    return@onRowClick
                                }
                                val joined = togetherSessionState as? com.ozyern.exhale.together.TogetherSessionState.Joined
                                if (joined?.role is com.ozyern.exhale.together.TogetherRole.Guest) {
                                    if (joined.roomState.settings.allowGuestsToControlPlayback != true) {
                                        Toast.makeText(context, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                                        return@onRowClick
                                    }
                                    val trackId = window.mediaItem.metadata?.id?.trim().orEmpty().ifBlank { window.mediaItem.mediaId.trim() }
                                    if (trackId.isBlank()) return@onRowClick
                                    Toast.makeText(context, R.string.together_requesting_song_change, Toast.LENGTH_SHORT).show()
                                    playerConnection.service.requestTogetherControl(
                                        com.ozyern.exhale.together.ControlAction.SeekToTrack(trackId = trackId, positionMs = 0L),
                                    )
                                } else {
                                    playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                    playerConnection.player.playWhenReady = true
                                }
                                shouldScrollToCurrent = false
                            }
                            val content: @Composable () -> Unit = {
                                val trackMetadata = window.mediaItem.metadata
                                if (trackMetadata != null) {
                                    QueueRow(
                                        metadata = trackMetadata,
                                        active = isActive,
                                        playing = isPlaying && isActive,
                                        heard = heard && !isActive,
                                        ink = ink,
                                        editable = !effectiveLocked,
                                        handle = { Modifier.draggableHandle() },
                                        onClick = onRowClick,
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            openMenu(trackMetadata)
                                        },
                                    )
                                }
                            }

                            Column {
                                if (index == activeListIndex + 1 && activeListIndex >= 0 && !reorderableState.isAnyItemDragging) {
                                    UpNextHeader(
                                        remaining = mutableQueueWindows.size - index,
                                        seconds = mutableQueueWindows.drop(index).sumOf { it.mediaItem.metadata?.duration?.coerceAtLeast(0) ?: 0 },
                                        shuffled = playerConnection.player.shuffleModeEnabled,
                                        source = queueTitle,
                                        color = ink,
                                    )
                                }
                                if (effectiveLocked) {
                                    content()
                                } else {
                                    SwipeToDismissBox(
                                        state = dismissBoxState,
                                        // What a swipe is about to do, under the card as it slides: it used
                                        // to be nothing at all, so a row could be sent away without any sign
                                        // that sending it away was what was happening.
                                        backgroundContent = { QueueSwipeBackground(dismissBoxState, 22.dp) },
                                    ) {
                                        content()
                                    }
                                }
                            }
                        }
                    }


                }
            }

            // Old header hidden - now using sticky header at top of queue
            Column(
                modifier =
                    Modifier
                        .height(0.dp)
            ) {

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val count = selectedSongs.size
                        IconButton(
                            onClick = {
                                selection = false
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                        Text(
                            text = stringResource(R.string.elements_selected, count),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (count == mutableQueueWindows.size) {
                                    selectedSongs.clear()
                                    selectedItems.clear()
                                } else {
                                    queueWindows
                                        .filter { it.mediaItem.metadata!! !in selectedSongs }
                                        .forEach {
                                            selectedSongs.add(it.mediaItem.metadata!!)
                                            selectedItems.add(it)
                                        }
                                }
                            },
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (count == mutableQueueWindows.size) {
                                            R.drawable.deselect
                                        } else {
                                            R.drawable.select_all
                                        },
                                    ),
                                contentDescription = null,
                            )
                        }

                        IconButton(
                            onClick = {
                                menuState.show {
                                    SelectionMediaMetadataMenu(
                                        songSelection = selectedSongs,
                                        onDismiss = menuState::dismiss,
                                        clearAction = {
                                            selectedSongs.clear()
                                            selectedItems.clear()
                                        },
                                        currentItems = selectedItems,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                                tint = LocalContentColor.current,
                            )
                        }
                    }
                }
                if (pureBlack) {
                    HorizontalDivider()
                }
            }

            val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

            // Bottom bar hidden - controls now in sticky header
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .height(0.dp)
                            .align(Alignment.BottomCenter)
                ) {
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = {
                            coroutineScope
                                .launch {
                                    lazyListState.animateScrollToItem(
                                        if (playerConnection.player.shuffleModeEnabled) playerConnection.player.currentMediaItemIndex else 0,
                                    )
                                }.invokeOnCompletion {
                                    playerConnection.player.shuffleModeEnabled =
                                        !playerConnection.player.shuffleModeEnabled
                                }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            modifier = Modifier.alpha(if (shuffleModeEnabled) 1f else 0.5f),
                        )
                    }

                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    IconButton(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = playerConnection.player::toggleRepeatMode,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                        else -> throw IllegalStateException()
                                    },
                                ),
                            contentDescription = null,
                            modifier = Modifier.alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .padding(
                                bottom =
                                    ListItemHeight +
                                            WindowInsets.systemBars
                                                .asPaddingValues()
                                                .calculateBottomPadding(),
                            )
                            .align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Apple Music's heading over what is still to come: "Continue Playing", and under it where the
 * songs come from — the album, playlist or radio that started the queue — or that they are shuffled.
 */
@Composable
private fun UpNextHeader(remaining: Int, seconds: Int, shuffled: Boolean, source: String?, color: Color) {
    val minutes = seconds / 60
    val length = buildString {
        append(if (remaining == 1) "1 song" else "$remaining songs")
        if (minutes > 0) append(if (minutes >= 60) " · ${minutes / 60} hr ${minutes % 60} min" else " · $minutes min")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    ) {
        Text(
            text = "Continue Playing",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = when {
                shuffled -> "Shuffled · $length"
                !source.isNullOrBlank() -> "From $source · $length"
                else -> length
            },
            fontSize = 13.sp,
            color = color.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One song in the queue as Apple lays it out: flat on the song's colour, artwork, title and artist,
 * and a grab handle at the end. The song playing wears the equaliser over its artwork; songs already
 * heard step back. Tap plays it, a long press opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    metadata: MediaMetadata,
    active: Boolean,
    playing: Boolean,
    heard: Boolean,
    ink: Color,
    editable: Boolean,
    handle: @Composable () -> Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ink.copy(alpha = if (pressed) 0.08f else 0f))
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { alpha = if (heard) 0.45f else 1f }
            .padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(ink.copy(alpha = 0.08f)),
        ) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
                PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = playing,
                    color = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                metadata.title,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = metadata.artists.joinToString(", ") { it.name }
            if (artists.isNotBlank()) {
                Text(
                    artists,
                    fontSize = 13.sp,
                    color = ink.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (editable) {
            Box(
                modifier = handle().size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.drag_handle),
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
