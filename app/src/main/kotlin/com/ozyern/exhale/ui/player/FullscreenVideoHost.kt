package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.zIndex
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.delay

internal data class FullscreenVideoSession(
    val stream: YTPlayerUtils.VideoStream,
    val connection: PlayerConnection,
    val videoController: CanvasVideoController,
    val onLeaveVideo: () -> Unit,
)

@Stable
internal class FullscreenVideoState {
    var selectedVideoId by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<FullscreenVideoSession?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    private var afterClose by mutableStateOf<(() -> Unit)?>(null)

    fun open(
        stream: YTPlayerUtils.VideoStream,
        connection: PlayerConnection,
        videoController: CanvasVideoController,
        onLeaveVideo: () -> Unit,
    ) {
        afterClose = null
        selectedVideoId = stream.mediaId
        session = FullscreenVideoSession(stream, connection, videoController, onLeaveVideo)
        visible = true
    }

    fun close(after: (() -> Unit)? = null) {
        afterClose = after
        visible = false
    }

    fun finishClose(closedSession: FullscreenVideoSession) {
        if (session !== closedSession || visible) return
        session = null
        val action = afterClose
        afterClose = null
        action?.invoke()
    }

    fun selectVideo(id: String?, selected: Boolean) { selectedVideoId = id.takeIf { selected } }
}

internal val LocalFullscreenVideo = staticCompositionLocalOf<FullscreenVideoState?> { null }

@Composable
internal fun FullscreenVideoOverlay(state: FullscreenVideoState) {
    val session = state.session ?: return
    val reduceMotion by rememberPreference(ReduceDynamicBlurKey, false)
    val reduced = reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(session) {
        session.connection.mediaMetadata.collect { metadata ->
            if (metadata != null && metadata.id != session.stream.mediaId) state.close()
        }
    }
    LaunchedEffect(session, state.visible) {
        if (!state.visible) {
            delay(if (reduced) 1 else FULLSCREEN_VIDEO_FADE_MS.toLong())
            state.finishClose(session)
        }
    }
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(Motion.gate(reduced, Motion.swapEnter())),
        exit = fadeOut(Motion.gate(reduced, Motion.swapExit())),
        modifier = Modifier.fillMaxSize().zIndex(100f),
    ) {
        YouTubeVideoPane(
            stream = session.stream,
            connection = session.connection,
            videoController = session.videoController,
            visible = true,
            fullscreenOnly = true,
            modifier = Modifier.fillMaxSize(),
            onClose = { state.close() },
            onLeaveVideo = { state.close(session.onLeaveVideo) },
        )
    }
}

private const val FULLSCREEN_VIDEO_FADE_MS = 260
