package com.ozyern.exhale.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.VideoStreamLookup
import com.ozyern.exhale.utils.YTPlayerUtils

/** The song/video choice for the classic cover, sharing V2's selection so both skins agree. */
@Stable
internal class ClassicVideo(
    val watch: Boolean,
    val canWatch: Boolean,
    val stream: YTPlayerUtils.VideoStream?,
    val unavailable: Boolean,
    val controller: CanvasVideoController,
    val select: (Boolean) -> Unit,
    val requestStream: () -> Unit,
) {
    val active: Boolean get() = watch && stream != null
}

@Composable
internal fun rememberClassicVideo(
    connection: PlayerConnection,
    metadata: MediaMetadata?,
    isPlaying: Boolean,
    sheetCollapsed: Boolean,
    isPreviewOnly: Boolean,
): ClassicVideo {
    val id = metadata?.id
    val lookup by connection.videoStreamLookup.collectAsStateWithLifecycle()
    val available by connection.videoAvailability.collectAsStateWithLifecycle()
    val presentation = LocalFullscreenVideo.current
    var previewWatch by remember(id) { mutableStateOf(false) }
    val standalone = isPreviewOnly || presentation == null
    val watch = if (standalone) previewWatch else id != null && presentation?.selectedVideoId == id
    val select: (Boolean) -> Unit = { selected ->
        if (standalone) {
            previewWatch = selected
        } else {
            presentation?.selectVideo(id, selected)
            id?.let { connection.service.setVideoPlaybackMode(it, selected) }
        }
    }
    LaunchedEffect(id, presentation?.selectedVideoId) {
        val selectedId = presentation?.selectedVideoId
        if (selectedId != null && selectedId != id) presentation.selectVideo(selectedId, false)
    }

    LaunchedEffect(id, sheetCollapsed, isPreviewOnly) {
        connection.requestVideoAvailability(id.takeIf { !sheetCollapsed && !isPreviewOnly })
    }
    DisposableEffect(connection) { onDispose { connection.requestVideoAvailability(null) } }
    LaunchedEffect(watch, id) {
        if (watch) id?.let(connection::requestVideoStream) else connection.clearVideoStreamRequest()
    }

    val stream = (lookup as? VideoStreamLookup.Ready)?.stream?.takeIf { it.mediaId == id }
    // Without a watched video the controller gets no metadata, so it never fetches canvas art.
    val controller = rememberCanvasVideoController(
        mediaMetadata = metadata.takeIf { watch },
        isPlaying = isPlaying && !sheetCollapsed && watch,
        trackFadeMillis = 300,
        videoStream = stream.takeIf { watch },
        videoStartPositionMs = connection.player.currentPosition,
    )
    return ClassicVideo(
        watch = watch,
        canWatch = available != null && available?.songId == id,
        stream = stream,
        unavailable = lookup is VideoStreamLookup.Unavailable,
        controller = controller,
        select = select,
        requestStream = { id?.let(connection::requestVideoStream) },
    )
}
