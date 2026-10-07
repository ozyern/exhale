/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.player

import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.SliderStyle
import com.ozyern.exhale.constants.SliderStyleKey
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toggleRepeatMode
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.Lyrics
import com.ozyern.exhale.ui.component.LyricsV2
import com.ozyern.exhale.constants.UseLyricsV2Key
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.BigSeekBar
import androidx.navigation.NavController
import com.ozyern.exhale.ui.menu.LyricsMenu
import com.ozyern.exhale.ui.utils.highRes
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.di.LyricsHelperEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.runCatching
import com.ozyern.exhale.utils.makeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    onBackClick: () -> Unit,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier
) {

    var showLyricsMenu by remember { mutableStateOf(false) }

    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    
    // slider style preference
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Wavy)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val (useLyricsV2) = rememberPreference(UseLyricsV2Key, defaultValue = true)

    // Auto-fetch lyrics when no lyrics found (same logic as refetch)
    LaunchedEffect(mediaMetadata.id, currentLyrics) {
        if (currentLyrics == null) {
            // Small delay to ensure database state is stable
            delay(500)
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Get LyricsHelper from Hilt
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    
                    // The service looks the song up too, and may have saved it in the meantime.
                    if (database.lyrics(mediaMetadata.id).first() != null) return@launch

                    // Fetch lyrics automatically
                    val lyrics = lyricsHelper.getLyrics(mediaMetadata)

                    // A miss must not overwrite lyrics the service found while this was asking.
                    if (lyrics == LyricsEntity.LYRICS_NOT_FOUND && database.lyrics(mediaMetadata.id).first() != null) {
                        return@launch
                    }
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, lyrics))
                    }
                } catch (e: Exception) {
                    // Handle error silently - user can manually refetch if needed
                }
            }
        }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    
    // Track loading state: when buffering or when user is seeking
    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null

    // The lyrics screen ALWAYS sits on the dark blurred-album-art backdrop, so its
    // chrome is always white-on-dark regardless of the player-background preference.
    val textBackgroundColor = Color.White

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                com.ozyern.exhale.utils.awaitAppVisible()
                delay(100)
                position = player.currentPosition
                duration = player.duration
            }
        }
    }

    BackHandler(onBack = onBackClick)

    // What the buttons' liquid glass refracts: the artwork wash, recorded beneath them.
    val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        // Apple-Music lyrics backdrop: ALWAYS the current track's album art, blown up and
        // blurred into a glowing liquid wash — regardless of the player-background
        // preference (that preference still drives the main player screen).
        Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
            AppleLyricsBackground(mediaMetadata = mediaMetadata)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalPlayerBackdrop provides glassBackdrop) {

        // Check orientation and layout accordingly
        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Landscape layout - split screen horizontally
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    // Unified header across full width
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .zIndex(1f),  // Ensure header is above content
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Down arrow button (left)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .playerGlass(CircleShape)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = stringResource(R.string.close),
                                tint = textBackgroundColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Now Playing info in center
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.titleMedium,
                                color = textBackgroundColor
                            )
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textBackgroundColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // More button (right)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .playerGlass(CircleShape)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) {
                                    showLyricsMenu = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more_options),
                                tint = textBackgroundColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Main content row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Right side - Lyrics only
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            // Lyrics content - centered in landscape
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center  // Center lyrics in landscape
                            ) {
                                if (useLyricsV2) {
                                    LyricsV2(
                                        sliderPositionProvider = { sliderPosition }
                                    )
                                } else {
                                    Lyrics(
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                        
                        // Left side - Controls only (from slider to volume)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(horizontal = 48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Slider
                            StyledPlaybackSlider(
                                sliderStyle = sliderStyle,
                                value = (sliderPosition ?: position).toFloat(),
                                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                                onValueChange = {
                                    sliderPosition = it.toLong()
                                },
                                onValueChangeFinished = {
                                    sliderPosition?.let {
                                        player.seekTo(it)
                                        position = it
                                    }
                                    sliderPosition = null
                                },
                                activeColor = textBackgroundColor,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Time display below slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = makeTimeString(sliderPosition ?: position),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textBackgroundColor.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textBackgroundColor.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Control buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Repeat button
                                IconButton(
                                    onClick = { playerConnection.player.toggleRepeatMode() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when (repeatMode) {
                                                Player.REPEAT_MODE_OFF, 
                                                Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                                else -> R.drawable.repeat
                                            }
                                        ),
                                        contentDescription = when (repeatMode) {
                                            Player.REPEAT_MODE_OFF -> "Repeat Off"
                                            Player.REPEAT_MODE_ALL -> "Repeat All"
                                            Player.REPEAT_MODE_ONE -> "Repeat One"
                                            else -> "Repeat"
                                        },
                                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                            textBackgroundColor.copy(alpha = 0.4f)
                                        } else {
                                            textBackgroundColor
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Previous button
                                IconButton(
                                    onClick = { playerConnection.seekToPrevious() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.skip_previous),
                                        contentDescription = null,
                                        tint = textBackgroundColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Play/Pause button
                                IconButton(
                                    onClick = { player.togglePlayPause() },
                                    modifier = Modifier.size(64.dp).playerGlass(CircleShape, lit = true)
                                ) {
                                    if (isLoading) {
                                        LoadingRing(
                                            modifier = Modifier.size(36.dp),
                                            color = textBackgroundColor,
                                            stroke = 3.dp
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                if (isPlaying) R.drawable.pause else R.drawable.play
                                            ),
                                            contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                                            tint = textBackgroundColor,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                // Next button
                                IconButton(
                                    onClick = { playerConnection.seekToNext() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.skip_next),
                                        contentDescription = null,
                                        tint = textBackgroundColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Shuffle button
                                IconButton(
                                    onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = if (shuffleModeEnabled) stringResource(R.string.shuffle) else stringResource(R.string.shuffle),
                                        tint = if (shuffleModeEnabled) {
                                            textBackgroundColor
                                        } else {
                                            textBackgroundColor.copy(alpha = 0.4f)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Volume Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.volume_off),
                                    contentDescription = stringResource(R.string.minimum_volume),
                                    modifier = Modifier.size(20.dp),
                                    tint = textBackgroundColor
                                )

                                BigSeekBar(
                                    progressProvider = playerVolume::value,
                                    onProgressChange = { playerConnection.service.playerVolume.value = it },
                                    color = textBackgroundColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                        .padding(horizontal = 16.dp)
                                )

                                Icon(
                                    painter = painterResource(R.drawable.volume_up),
                                    contentDescription = stringResource(R.string.maximum_volume),
                                    modifier = Modifier.size(20.dp),
                                    tint = textBackgroundColor
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // Portrait layout - original layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                ) {
                    // Header with More button and Down arrow on opposite sides
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Down arrow button (left)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .playerGlass(CircleShape)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = stringResource(R.string.close),
                                tint = textBackgroundColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Centered content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.titleMedium,
                                color = textBackgroundColor
                            )
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textBackgroundColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // More button (right)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .playerGlass(CircleShape)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) {
                                    showLyricsMenu = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more_options),
                                tint = textBackgroundColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (useLyricsV2) {
                            LyricsV2(
                                sliderPositionProvider = { sliderPosition }
                            )
                        } else {
                            Lyrics(
                                sliderPositionProvider = { sliderPosition },
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = modifier,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 16.dp)
                    ) {
                        StyledPlaybackSlider(
                            sliderStyle = sliderStyle,
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            activeColor = textBackgroundColor,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time display below slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = makeTimeString(sliderPosition ?: position),
                                style = MaterialTheme.typography.bodySmall,
                                color = textBackgroundColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = textBackgroundColor.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Optimized control buttons for better fit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp), // Reduced padding
                            horizontalArrangement = Arrangement.SpaceEvenly, // Even distribution
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repeat button with clear state indication
                            IconButton(
                                onClick = { playerConnection.player.toggleRepeatMode() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (repeatMode) {
                                            Player.REPEAT_MODE_OFF, 
                                            Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                            else -> R.drawable.repeat
                                        }
                                    ),
                                    contentDescription = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> "Repeat Off"
                                        Player.REPEAT_MODE_ALL -> "Repeat All"
                                        Player.REPEAT_MODE_ONE -> "Repeat One"
                                        else -> "Repeat"
                                    },
                                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                        // Inactive state - low opacity
                                        textBackgroundColor.copy(alpha = 0.4f)
                                    } else {
                                        // Active state - full brightness
                                        textBackgroundColor
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Previous button
                            IconButton(
                                onClick = { playerConnection.seekToPrevious() },
                                modifier = Modifier.size(40.dp) // Slightly smaller
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    tint = textBackgroundColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Play/Pause button (largest)
                            IconButton(
                                onClick = { player.togglePlayPause() },
                                modifier = Modifier.size(64.dp).playerGlass(CircleShape, lit = true)
                            ) {
                                if (isLoading) {
                                    LoadingRing(
                                        modifier = Modifier.size(36.dp),
                                        color = textBackgroundColor,
                                        stroke = 3.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            if (isPlaying) R.drawable.pause else R.drawable.play
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                                        tint = textBackgroundColor,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Next button
                            IconButton(
                                onClick = { playerConnection.seekToNext() },
                                modifier = Modifier.size(40.dp) // Slightly smaller
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = null,
                                    tint = textBackgroundColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Shuffle button with clear state indication
                            IconButton(
                                onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = if (shuffleModeEnabled) stringResource(R.string.shuffle) else stringResource(R.string.shuffle),
                                    tint = if (shuffleModeEnabled) {
                                        // Active state - full brightness
                                        textBackgroundColor
                                    } else {
                                        // Inactive state - low opacity
                                        textBackgroundColor.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp)) // Proper spacing

                        // Volume Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.volume_off),
                                contentDescription = stringResource(R.string.minimum_volume),
                                modifier = Modifier.size(20.dp),
                                tint = textBackgroundColor
                            )

                            BigSeekBar(
                                progressProvider = playerVolume::value,
                                onProgressChange = { playerConnection.service.playerVolume.value = it },
                                color = textBackgroundColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp)
                                    .padding(horizontal = 16.dp)
                            )

                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = stringResource(R.string.maximum_volume),
                                modifier = Modifier.size(20.dp),
                                tint = textBackgroundColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        }
    }
    if (showLyricsMenu) {
        Dialog(
            onDismissRequest = { showLyricsMenu = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                onDismiss = { showLyricsMenu = false }
            )
        }
    }
}

/**
 * Apple-Music lyrics backdrop: the current track's high-res cover art scaled past the screen
 * edges and blurred so hard it reads as a glowing liquid mesh gradient, crossfaded on track
 * change, with a dark scrim so white lyrics stay readable on bright covers.
 */
@Composable
private fun AppleLyricsBackground(mediaMetadata: MediaMetadata) {
    AnimatedContent(
        targetState = mediaMetadata.thumbnailUrl,
        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
        label = "lyricsBackground",
        modifier = Modifier.fillMaxSize()
    ) { thumbnailUrl ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailUrl != null) {
                // The desktop build's lyrics sit on the cover as a slowly moving field of its own
                // colours, and that is the thing worth having: a still, hard-blurred bitmap is a
                // photograph of a background, while this one breathes with the song. It goes
                // underneath the cover rather than instead of it, so a dark album still gives the
                // screen some light to work with.
                com.ozyern.exhale.ui.component.FluidArtworkBackground(
                    url = thumbnailUrl,
                    brightness = 0.62f,
                    modifier = Modifier.fillMaxSize(),
                )
                AsyncImage(
                    model = thumbnailUrl.highRes(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // Overscale so the blur never reveals hard screen-edge vignetting.
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                        }
                        // 100dp was not a blur, it was an erasure: at this screen's density that
                        // kernel is ~350px across a 1440px cover, which averages the whole image
                        // down to a single colour. What was left was a brown gradient that
                        // happened to have been computed from artwork. The desktop build keeps the
                        // cover legible behind the words, and that is the part worth copying - you
                        // can still see which record you are listening to.
                        .blur(radius = 42.dp)
                        .graphicsLayer { alpha = 0.55f },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101018))
                )
            }
            // Dark translucent scrim: keeps full-alpha white lyrics legible on bright art.
            //
            // Graded rather than flat, now that what is underneath has structure again: heaviest
            // at the top and the foot, where the header and the transport sit, and lightest across
            // the middle, which is where the lyrics are and where the cover should show through.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.58f),
                            0.28f to Color.Black.copy(alpha = 0.40f),
                            0.72f to Color.Black.copy(alpha = 0.42f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        )
                    )
            )
        }
    }
}
