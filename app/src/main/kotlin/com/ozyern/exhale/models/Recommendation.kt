/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.models

import com.ozyern.exhale.innertube.models.SongItem

/** A song Home suggests, and in a few words why: "Because you like …", or the feed shelf it came from. */
data class Recommendation(
    val song: SongItem,
    val reason: String,
)
