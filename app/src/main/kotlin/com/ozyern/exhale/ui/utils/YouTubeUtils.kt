/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.utils

private val youtubeThumbnailFilename =
    Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg")
private val googleusercontentArtworkUrl =
    Regex("https://lh\\d\\.googleusercontent\\.com/.*")
private val appleArtworkFilename = Regex("""/\d+x\d+[a-z]*\.(jpg|jpeg|png|webp)$""")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    // YouTube thumbnails use fixed quality filenames. Request the smallest rung that fits;
    // the quality interceptor falls back when maxres or sd thumbnails return 404.
    if (this.contains("i.ytimg.com")) {
        val target = maxOf(width ?: 0, height ?: 0)
        val targetQuality = when {
            target >= 1000 -> "maxresdefault.jpg" // 1280x720
            target >= 400 -> "sddefault.jpg"      // 640x480
            target >= 250 -> "hqdefault.jpg"      // 480x360
            else -> "mqdefault.jpg"               // 320x180
        }
        return this.replace(
            youtubeThumbnailFilename,
            targetQuality
        )
    }

    // googleusercontent.com handling (includes lh3-lh6, yt3, etc.)
    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = substringBefore("=w")
        val w = width ?: 0
        val h = height ?: width ?: 0
        // Keep the full requested size and use the JPEG format these thumbnails support.
        return "$baseUrl=w$w-h$h-p-l90-rj"
    }

    if (this.contains("yt3.ggpht.com")) {
        val baseUrl = substringBefore("=").substringBefore("-s")
        return "$baseUrl=s${width ?: height}"
    }

    // Fallback for other lh3-style URLs that might not have =w yet
    googleusercontentArtworkUrl.matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${substringBefore("=")}=w$w-h$h-p-l90-rj"
    }

    // Apple Music (mzstatic) artwork: the size is the filename, "bb" = fit inside the box
    // keeping aspect, so a 16:9 editorial image asked for as a square is not cropped.
    if (this.contains("mzstatic.com")) {
        val px = maxOf(width ?: 0, height ?: 0)
        return replace(appleArtworkFilename, "/${px}x${px}bb.jpg")
    }

    return this
}
