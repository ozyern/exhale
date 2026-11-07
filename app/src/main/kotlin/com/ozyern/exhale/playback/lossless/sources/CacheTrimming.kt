package com.ozyern.exhale.playback.lossless.sources

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounds a [ConcurrentHashMap] that is used as a TTL cache.
 *
 * Several lossless-source caches only ever dropped an entry when that exact key was looked up
 * again after expiry, so every distinct track played in a session left an entry behind for the
 * life of the process. Calling this right after a put keeps them bounded.
 *
 * - No-op while the map is at or under [maxSize] (the common case costs one `size` read).
 * - Over the cap: expired entries go first, then the soonest-to-expire ones, down to 75% of
 *   [maxSize] so the next few puts do not each pay for a trim.
 *
 * Only use this on maps whose eviction has no side effects: a caller that must release a
 * resource when an entry leaves the map (e.g. delete a temp file) needs its own eviction.
 */
internal fun <K : Any, V : Any> ConcurrentHashMap<K, V>.trimTo(
    maxSize: Int,
    now: Long = System.currentTimeMillis(),
    expiryOf: (V) -> Long,
) {
    if (size <= maxSize) return

    val snapshot = entries.map { it.key to it.value }
    for ((key, value) in snapshot) {
        if (expiryOf(value) <= now) remove(key, value)
    }
    if (size <= maxSize) return

    val target = maxSize * 3 / 4
    snapshot
        .filter { (key, value) -> this[key] === value }
        .sortedBy { (_, value) -> expiryOf(value) }
        .take((size - target).coerceAtLeast(0))
        .forEach { (key, value) -> remove(key, value) }
}
