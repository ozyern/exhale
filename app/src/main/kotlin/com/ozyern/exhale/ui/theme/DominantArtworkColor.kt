/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.theme

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dominantCache = LruCache<String, Int>(64)

/**
 * The colour a cover is mostly made of (0xRRGGBB), or null until it has been read.
 *
 * The most populous swatch with Palette's filters off: the default filters throw away near-black
 * and near-white, which is right when hunting for an accent and wrong for "what colour is this
 * page", where a dark photograph would come down to whatever small coloured detail survived.
 */
@Composable
fun rememberDominantArtworkColor(url: String?): Int? {
    val context = LocalContext.current
    val color by produceState(url?.let(dominantCache::get), url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(160)
                .allowHardware(false)
                .build()
            runCatching {
                val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
                Palette.from(bitmap)
                    .maximumColorCount(24)
                    .clearFilters()
                    .generate()
                    .swatches
                    .maxByOrNull { it.population }
                    ?.rgb
                    ?.and(0xFFFFFF)
            }.getOrNull()?.also { dominantCache.put(url, it) }
        }
    }
    return color
}
