/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The three-tier queue — what you queued, what you were playing from, and Autoplay — follows
 * BitChord's QueueCoordinator (github.com/kushagrasinghx/BitChord, GPL-3.0), applied to Exhale's
 * player in place rather than by rebuilding its timeline.
 */

package com.ozyern.exhale.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder

/**
 * Where a queue entry came from, which decides where new entries go and what a tap skips.
 *
 * - [USER]: added with Play Next or Add to Queue. Plays before the rest of the album or playlist,
 *   in the order it was added, and once.
 * - [CONTEXT]: the album, playlist or station the music was started from.
 * - [AUTOPLAY]: what Exhale lined up once the context ran out.
 */
enum class QueueTier { USER, CONTEXT, AUTOPLAY }

private const val TIER_KEY = "exhale.queueTier"

/** An entry's tier. Anything never tagged — a restored queue, a context — is the context. */
val MediaItem.queueTier: QueueTier
    get() = mediaMetadata.extras?.getString(TIER_KEY)
        ?.let { name -> QueueTier.entries.firstOrNull { it.name == name } }
        ?: QueueTier.CONTEXT

fun MediaItem.withQueueTier(tier: QueueTier): MediaItem {
    if (queueTier == tier && mediaMetadata.extras?.containsKey(TIER_KEY) == true) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putString(TIER_KEY, tier.name) }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}

/** Timeline indices in the order they will play, shuffle included. */
fun Player.playOrder(): List<Int> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val shuffled = shuffleModeEnabled
    val order = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(shuffled)
    while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
        order += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    }
    return order
}

/** The entries still to come, as timeline indices in play order. */
fun Player.upcomingIndices(): List<Int> {
    val order = playOrder()
    val at = order.indexOf(currentMediaItemIndex)
    return if (at < 0) emptyList() else order.drop(at + 1)
}

/**
 * Where Add to Queue puts a track in timeline order: after the current one and every queued track
 * already waiting behind it, ahead of the rest of the album and of Autoplay.
 */
internal fun Player.userQueueEnd(): Int {
    if (mediaItemCount == 0) return 0
    var index = currentMediaItemIndex + 1
    while (index < mediaItemCount && getMediaItemAt(index).queueTier == QueueTier.USER) index++
    return index
}

/**
 * With shuffle on, the timeline position of a new entry says nothing about when it plays — ExoPlayer
 * drops inserted items at random places in its shuffle order, which is why Play Next used to play
 * whenever. This puts [inserted] straight after the current track in the play order, or after the
 * queued tracks already waiting there when [afterQueued].
 */
internal fun ExoPlayer.placeInShuffle(inserted: IntRange, afterQueued: Boolean) {
    if (!shuffleModeEnabled || inserted.isEmpty()) return
    val order = playOrder().toMutableList()
    if (order.size != mediaItemCount) return
    order.removeAll { it in inserted }
    var at = order.indexOf(currentMediaItemIndex)
    if (at < 0) return
    if (afterQueued) {
        while (at + 1 < order.size && getMediaItemAt(order[at + 1]).queueTier == QueueTier.USER) at++
    }
    order.addAll(at + 1, inserted.toList())
    setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.currentTimeMillis()))
}

/** Removes every queued track still to come; the album and Autoplay are left alone. */
internal fun Player.clearUserQueue() {
    upcomingIndices()
        .filter { getMediaItemAt(it).queueTier == QueueTier.USER }
        .sortedDescending()
        .forEach(::removeMediaItem)
}

/**
 * Once playback is back in the album, the queued tracks it played on the way are spent: they come
 * out of the history, so repeat-all goes round the album and not round what was queued once.
 */
internal fun Player.consumePlayedUserQueue() {
    if (shuffleModeEnabled) return
    val current = currentMediaItemIndex
    if (current <= 0 || currentMediaItem?.queueTier != QueueTier.CONTEXT) return
    (0 until current)
        .filter { getMediaItemAt(it).queueTier == QueueTier.USER }
        .sortedDescending()
        .forEach(::removeMediaItem)
}

/**
 * Plays the entry at [target], skipping the way a person means it: tapping a song further down the
 * album skips the album songs before it but keeps everything they queued; tapping a queued song
 * uses up the queued songs before it; tapping an Autoplay song starts from there. Backwards, or with
 * shuffle on, it is a plain seek — history is never rewritten.
 */
internal fun Player.jumpToQueueItem(target: Int) {
    val current = currentMediaItemIndex
    if (target !in 0 until mediaItemCount) return
    if (shuffleModeEnabled || target <= current) {
        seekToDefaultPosition(target)
        playWhenReady = true
        return
    }
    val upcoming = (current + 1 until mediaItemCount).map(::getMediaItemAt)
    val at = target - current - 1
    val chosen = upcoming[at]
    val after = upcoming.drop(at + 1)
    val queued = upcoming.filter { it.queueTier == QueueTier.USER }
    val next = when (chosen.queueTier) {
        QueueTier.CONTEXT ->
            listOf(chosen) + queued + after.filter { it.queueTier == QueueTier.CONTEXT } +
                after.filter { it.queueTier == QueueTier.AUTOPLAY }
        QueueTier.USER ->
            listOf(chosen) + after.filter { it.queueTier == QueueTier.USER } +
                upcoming.filter { it.queueTier == QueueTier.CONTEXT } +
                upcoming.filter { it.queueTier == QueueTier.AUTOPLAY }
        QueueTier.AUTOPLAY ->
            listOf(chosen.withQueueTier(QueueTier.CONTEXT)) + queued +
                after.filter { it.queueTier == QueueTier.AUTOPLAY }
    }
    replaceMediaItems(current + 1, mediaItemCount, next)
    seekToDefaultPosition(current + 1)
    playWhenReady = true
}
