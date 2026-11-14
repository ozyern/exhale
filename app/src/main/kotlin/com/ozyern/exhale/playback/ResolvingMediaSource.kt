package com.ozyern.exhale.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.upstream.Allocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException

/** Selects the container after URL resolution, on the playback thread, without blocking it. */
@UnstableApi
internal class ResolvingMediaSource(
    private val item: MediaItem,
    private val resolve: suspend () -> MediaSource,
) : CompositeMediaSource<Unit>() {
    private var child: MediaSource? = null
    private var job: Job? = null
    private var handler: Handler? = null
    @Volatile private var error: IOException? = null
    private var generation = 0

    override fun getMediaItem(): MediaItem = item

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean = false

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val playbackHandler = Handler(checkNotNull(Looper.myLooper()))
        handler = playbackHandler
        val currentGeneration = ++generation
        error = null
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val source = resolve()
                currentCoroutineContext().ensureActive()
                playbackHandler.post {
                    if (currentGeneration == generation) {
                        child = source
                        prepareChildSource(Unit, source)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                playbackHandler.post {
                    if (currentGeneration == generation) {
                        error = if (e is IOException) e else IOException("Could not resolve playback source", e)
                    }
                }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(newTimeline)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        error?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        checkNotNull(child).createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) { child?.releasePeriod(mediaPeriod) }

    override fun releaseSourceInternal() {
        generation++
        job?.cancel()
        job = null
        handler?.removeCallbacksAndMessages(null)
        handler = null
        super.releaseSourceInternal()
        child = null
        error = null
    }
}
