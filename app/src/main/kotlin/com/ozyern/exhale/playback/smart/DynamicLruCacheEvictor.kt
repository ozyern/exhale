package com.ozyern.exhale.playback.smart

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * LRU cache evictor with an adjustable size limit. It protects a bounded amount
 * of each track's opening bytes because analysis must decode from the beginning.
 * A mutable limit lets settings change without reopening [SimpleCache].
 */
@UnstableApi
class DynamicLruCacheEvictor(
    @Volatile var maxBytes: Long,
    /**
     * How far into a track counts as its "head" — the region the analyzer needs
     * contiguously from zero. Sized for lossless, where twelve seconds of audio
     * is a few megabytes; a compressed rendition's head fits many times over.
     */
    private val headBytes: Long = DEFAULT_HEAD_BYTES,
    /** Ceiling on the total held back. See the class doc. */
    private val headBudgetBytes: Long = DEFAULT_HEAD_BUDGET_BYTES,
) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)

    /**
     * Head spans exempt from eviction, oldest first. Ordered by the same clock
     * as [leastRecentlyUsed] so that releasing one back to ordinary LRU picks
     * the least useful.
     */
    private val protectedHeads = TreeSet<CacheSpan>(::compare)
    private var protectedSize = 0L
    private var currentSize = 0L

    /** A span is a head if it starts at the very beginning, or within [headBytes] of it. */
    private fun isHead(span: CacheSpan): Boolean = span.position < headBytes

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (isHead(span) && protectedSize + span.length <= headBudgetBytes) {
            protectedHeads.add(span)
            protectedSize += span.length
        } else {
            leastRecentlyUsed.add(span)
        }
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (protectedHeads.remove(span)) {
            protectedSize -= span.length
        } else {
            leastRecentlyUsed.remove(span)
        }
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Reclaims space right away when [maxBytes] drops, rather than waiting for
     * the next write to notice — otherwise a lowered limit only takes effect
     * whenever the listener next happens to play something.
     */
    fun applyNow(cache: Cache) = evictCache(cache, 0)

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes) {
            if (leastRecentlyUsed.isNotEmpty()) {
                cache.removeSpan(leastRecentlyUsed.first())
                continue
            }
            // Nothing left but protected heads. Releasing the oldest back to
            // ordinary LRU is what stops the exemption from becoming a way to
            // wedge the cache above its own ceiling: the loop then evicts it on
            // the next pass like any other span.
            val oldest = protectedHeads.pollFirst() ?: return
            protectedSize -= oldest.length
            leastRecentlyUsed.add(oldest)
        }
    }

    private companion object {
        /** Twelve seconds of lossless, comfortably: what the head-only analysis pass reads. */
        const val DEFAULT_HEAD_BYTES = 4L * 1024 * 1024

        /**
         * Total held back from eviction. Roughly a hundred compressed openings,
         * or a couple of dozen lossless ones — enough that a session's worth of
         * tracks stay analysable without meaningfully denting the cache.
         */
        const val DEFAULT_HEAD_BUDGET_BYTES = 96L * 1024 * 1024

        fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
            val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
            return when {
                delta == 0L -> lhs.compareTo(rhs)
                delta < 0L -> -1
                else -> 1
            }
        }
    }
}
