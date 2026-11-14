package com.ozyern.exhale.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.ozyern.exhale.R
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.component.AnimatedPlayPauseIcon
import com.ozyern.exhale.ui.component.elasticStretchLayerBlock
import com.ozyern.exhale.ui.component.rememberElasticStretch
import com.ozyern.exhale.ui.component.LocalGlassEffectConfig
import com.ozyern.exhale.ui.component.GlassStyle
import com.ozyern.exhale.ui.component.liquidGlass
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.ios.compat.DropdownMenu
import com.ozyern.exhale.ui.ios.compat.DropdownMenuItem
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun FullscreenVideoOrientation() {
    val activity = LocalContext.current.videoActivity()
    val view = LocalView.current
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        // Fullscreen video follows the device in portrait and landscape.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            if (activity != null && previous != null && activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
                activity.requestedOrientation = previous
            }
        }
    }
    DisposableEffect(activity, view) {
        val window = activity?.window
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusWasVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationWasVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val keepScreenOn = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (!keepScreenOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.apply {
                if (statusWasVisible) show(WindowInsetsCompat.Type.statusBars())
                if (navigationWasVisible) show(WindowInsetsCompat.Type.navigationBars())
                if (previousBehavior != null) systemBarsBehavior = previousBehavior
            }
        }
    }
}

@Composable
internal fun FullscreenVideoControls(
    connection: PlayerConnection,
    videoController: CanvasVideoController,
    playbackReady: Boolean,
    cropVideo: Boolean,
    onCropChange: (Boolean) -> Unit,
    onInteractionChange: (Boolean) -> Unit,
    onPictureInPicture: () -> Unit,
    onClose: () -> Unit,
    onLeaveVideo: () -> Unit,
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var position by remember { mutableLongStateOf(connection.player.currentPosition) }
    var duration by remember { mutableLongStateOf(connection.player.duration.coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(connection.player.playWhenReady) }
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(menuOpen, scrubPosition != null) { onInteractionChange(menuOpen || scrubPosition != null) }
    DisposableEffect(Unit) { onDispose { onInteractionChange(false) } }
    LaunchedEffect(connection) {
        while (isActive) {
            position = connection.player.currentPosition.coerceAtLeast(0)
            duration = connection.player.duration.coerceAtLeast(0)
            playing = connection.player.playWhenReady
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(200)
        }
    }
    val seek: (Long) -> Unit = { target ->
        val clamped = target.coerceIn(0, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        position = clamped
                connection.player.seekTo(clamped)
                videoController.seekTo(clamped)
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 38.dp, vertical = 24.dp)) {
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoGlassButton(onClick = onClose) {
                SymbolIcon(sf("xmark"), stringResource(R.string.video_exit_fullscreen), tint = Color.White)
            }
            VideoGlassButton(onClick = onPictureInPicture) {
                SymbolIcon(sf("pip.enter"), stringResource(R.string.video_picture_in_picture), tint = Color.White)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).width(204.dp).height(44.dp).videoControlPill().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VideoControlSlider(
                value = volume.toFloat(),
                onValueChange = { value ->
                    volume = value.toInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                },
                valueRange = 0f..maxVolume.toFloat(),
                description = stringResource(R.string.video_volume),
                modifier = Modifier.weight(1f),
            )
            SymbolIcon(sf(if (volume == 0) "speaker.slash.fill" else "speaker.wave.2.fill"), stringResource(R.string.video_volume), size = 24.dp, tint = Color.White)
        }
        if (playbackReady) Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            VideoGlassButton(onClick = { seek((position - 10_000).coerceAtLeast(0)) }, size = 64.dp) {
                SymbolIcon(sf("gobackward.10"), stringResource(R.string.video_seek_back), size = 32.dp, tint = Color.White)
            }
            VideoGlassButton(onClick = {
                connection.player.togglePlayPause()
                playing = connection.player.playWhenReady
            }, size = 92.dp) {
                AnimatedPlayPauseIcon(isPlaying = playing, size = 40.dp, tint = Color.White)
            }
            VideoGlassButton(onClick = { seek(position.coerceAtMost(Long.MAX_VALUE - 10_000) + 10_000) }, size = 64.dp) {
                SymbolIcon(sf("goforward.10"), stringResource(R.string.video_seek_forward), size = 32.dp, tint = Color.White)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 56.dp)) {
            VideoGlassButton(onClick = { menuOpen = true }) {
                SymbolIcon(sf("ellipsis"), stringResource(R.string.video_more), tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, surfaceModifier = Modifier.videoControlSurface(RoundedCornerShape(20.dp))) {
                DropdownMenuItem(text = { Text(stringResource(if (cropVideo) R.string.video_fit else R.string.video_fill)) }, onClick = { onCropChange(!cropVideo); menuOpen = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.video_mode_song)) }, onClick = { menuOpen = false; onLeaveVideo() })
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                .videoControlPill().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val displayedPosition = scrubPosition ?: position
            val timeStyle = AppTheme.typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
            RollingPlaybackTime({ elapsedPlaybackSeconds(displayedPosition) }, "", Color.White.copy(alpha = .65f), Modifier, timeStyle)
            VideoControlSlider(
                value = displayedPosition.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
                onValueChange = { scrubPosition = it.toLong() },
                onValueChangeFinished = { scrubPosition?.let(seek); scrubPosition = null },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = duration > 0,
                description = stringResource(R.string.video_seek),
                modifier = Modifier.weight(1f),
            )
            RollingPlaybackTime({ remainingPlaybackSeconds(duration, displayedPosition) }, "−", Color.White.copy(alpha = .65f), Modifier, timeStyle)
        }
    }
}

private val VideoControlShape = RoundedCornerShape(50)

@Composable
private fun Modifier.videoControlPill(): Modifier = videoControlSurface(VideoControlShape)

@Composable
private fun Modifier.videoControlSurface(shape: RoundedCornerShape): Modifier {
    val config = LocalGlassEffectConfig.current.copy(
        globalEnabled = true,
        style = GlassStyle.LIQUID,
        surfaceTintColor = Color.Black.copy(alpha = .18f),
        surfaceOpacity = .24f,
    )
    return then(Modifier.liquidGlass(config, shape, highlightAlpha = .32f, outerShadow = false))
}

@Composable
private fun VideoGlassButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    val stretch = rememberElasticStretch()
    val reduceMotion by rememberPreference(ReduceDynamicBlurKey, false)
    val material = Modifier.videoControlSurface(VideoControlShape)
    Box(
        Modifier.then(if (reduceMotion) Modifier else Modifier.graphicsLayer(elasticStretchLayerBlock(stretch)))
            .size(size).then(material)
            .clickable(role = Role.Button, interactionSource = null, indication = null, onClick = onClick)
            .then(stretch.modifier).then(stretch.gestureModifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VideoControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        modifier = modifier.height(44.dp).semantics { contentDescription = description },
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                val radius = size.height / 2
                val start = androidx.compose.ui.geometry.Offset(radius, radius)
                val end = androidx.compose.ui.geometry.Offset((size.width - radius).coerceAtLeast(radius), radius)
                drawLine(Color.White.copy(alpha = .33f), start, end, size.height, StrokeCap.Round)
                if (fraction > 0f) drawLine(Color.White.copy(alpha = .9f), start, androidx.compose.ui.geometry.Offset(start.x + (end.x - start.x) * fraction, radius), size.height, StrokeCap.Round)
            }
        },
    )
}

internal fun Context.videoActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }
    .filterIsInstance<Activity>().firstOrNull()
