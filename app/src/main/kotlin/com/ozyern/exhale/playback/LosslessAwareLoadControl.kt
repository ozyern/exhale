package com.ozyern.exhale.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * The player's buffering rules, with a deeper start for lossless streams.
 *
 * Two seconds in hand is plenty to start AAC or Opus on: those arrive many times faster than they
 * play. A FLAC from a lossless source is ten times the bytes and often comes from a slower server,
 * so two seconds of it ran dry a moment after the song began — the stutter heard in the first
 * seconds of a lossless song and almost never later, once the buffer had pulled ahead. A lossless
 * stream now starts on [losslessStartUs] of audio; everything else starts exactly as before.
 *
 * A song that is already fully buffered starts at once regardless: the player does not ask the
 * load control when it holds the whole file.
 *
 * Every method is forwarded by hand. `LoadControl` is a Java interface made almost entirely of
 * default methods, and Kotlin's `by` delegation does not forward those: the player then called the
 * interface's own `getBackBufferDurationUs`, which throws, and the service failed to start.
 */
@OptIn(UnstableApi::class)
class LosslessAwareLoadControl(
    private val delegate: LoadControl,
    private val losslessStartUs: Long = 4_000_000L,
) : LoadControl {

    @Volatile
    private var heavyStream = false

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        heavyStream = trackSelections.any { selection ->
            val format = selection?.selectedFormat ?: return@any false
            val mime = format.sampleMimeType.orEmpty()
            mime == MimeTypes.AUDIO_FLAC || mime == MimeTypes.AUDIO_ALAC || mime == MimeTypes.AUDIO_RAW ||
                (format.bitrate != Format.NO_VALUE && format.bitrate > 600_000)
        }
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!delegate.shouldStartPlayback(parameters)) return false
        // After a rebuffer the delegate already asks for more; only the first start is deepened.
        if (!heavyStream || parameters.rebuffering) return true
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) return true
        val needed = (losslessStartUs / parameters.playbackSpeed.coerceAtLeast(0.25f)).toLong()
        return parameters.bufferedDurationUs >= needed
    }
}
