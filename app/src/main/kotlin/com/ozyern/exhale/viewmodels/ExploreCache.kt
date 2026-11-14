/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.viewmodels

import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage

/**
 * The last Explore data this process fetched, shared by Home (which loads it at startup) and the
 * Explore screen, so opening Explore shows content on the first frame instead of waiting on the
 * network again.
 */
object ExploreCache {
    private const val FRESH_MS = 5 * 60_000L

    @Volatile var page: ExplorePage? = null
        private set
    @Volatile var recommended: List<YTItem>? = null
        private set
    @Volatile private var pageAt = 0L

    val isFresh: Boolean get() = page != null && System.currentTimeMillis() - pageAt < FRESH_MS

    fun put(page: ExplorePage) {
        this.page = page
        pageAt = System.currentTimeMillis()
    }

    fun putRecommended(items: List<YTItem>) {
        recommended = items
    }
}
