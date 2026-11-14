/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Bounded, short-lived cache of network pages keyed by string. Concurrent asks for the same key
 * share one request, which keeps running if the screen that asked leaves (so a prefetch pays off).
 * Failed loads (null) are not kept.
 */
class RequestCache<T : Any>(private val maxSize: Int, private val ttlMs: Long) {

    private class Entry<T>(val createdAt: Long, val value: Deferred<T?>)

    private val entries = object : LinkedHashMap<String, Entry<T>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>) = size > maxSize
    }

    private fun Entry<T>.isFresh() = System.currentTimeMillis() - createdAt < ttlMs

    /** The cached value if it is fresh and already loaded; never waits. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun peek(key: String): T? = synchronized(entries) {
        entries[key]?.takeIf { it.isFresh() && it.value.isCompleted }?.value?.let { runCatching { it.getCompleted() }.getOrNull() }
    }

    /** Returns the fresh cached value, joins an in-flight load, or starts [load]. */
    suspend fun get(key: String, load: suspend () -> T?): T? {
        val deferred = synchronized(entries) {
            entries[key]?.takeIf { it.isFresh() }?.value
                ?: scope.async { load() }.also { entries[key] = Entry(System.currentTimeMillis(), it) }
        }
        val value = runCatching { deferred.await() }.getOrNull()
        if (value == null) synchronized(entries) { if (entries[key]?.value === deferred) entries.remove(key) }
        return value
    }

    /** Starts loading [key] without waiting, so a screen opened right after finds it in flight. */
    fun prefetch(key: String, load: suspend () -> T?) {
        scope.async { get(key, load) }
    }

    fun put(key: String, value: T) {
        synchronized(entries) { entries[key] = Entry(System.currentTimeMillis(), kotlinx.coroutines.CompletableDeferred(value)) }
    }

    fun remove(key: String) {
        synchronized(entries) { entries.remove(key) }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Process-wide caches for pages that are slow to fetch and often revisited. */
object PageCaches {
    fun prefetchSearch(query: String) = searchSummaries.prefetch(query) {
        com.music.innertube.YouTube.searchSummary(query).getOrNull()
    }

    private const val TtlMs = 10 * 60 * 1000L
    val artistPages = RequestCache<com.music.innertube.pages.ArtistPage>(maxSize = 16, ttlMs = TtlMs)
    val searchSummaries = RequestCache<com.music.innertube.pages.SearchSummaryPage>(maxSize = 16, ttlMs = TtlMs)
    val searchResults = RequestCache<com.music.innertube.pages.SearchResult>(maxSize = 32, ttlMs = TtlMs)
}

/** Opens online search results, starting the request before the screen exists. */
fun androidx.navigation.NavController.openSearchResults(
    query: String,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {},
) {
    PageCaches.prefetchSearch(query)
    navigate("search/${java.net.URLEncoder.encode(query, "UTF-8")}", builder)
}
