/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult

/** Retries failed YouTube artwork requests with smaller thumbnail sizes. */
class ArtworkQualityInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result !is ErrorResult) return result

        val url = chain.request.data as? String ?: return result
        if (!url.contains(YouTubeThumbnailHost)) return result

        val currentQuality = QualityPattern.find(url)?.value ?: return result
        val startIndex = QualityLadder.indexOf(currentQuality)
        // Unknown or already-lowest quality: nothing left to step down to.
        if (startIndex < 0 || startIndex >= QualityLadder.lastIndex) return result

        // maxresdefault is missing for many videos, so try smaller sizes before giving up.
        var lastResult: ImageResult = result
        for (index in (startIndex + 1)..QualityLadder.lastIndex) {
            val candidate = url.replace(QualityPattern, QualityLadder[index])
            val request = chain.request.newBuilder().data(candidate).build()
            val attempt = chain.withRequest(request).proceed()
            if (attempt !is ErrorResult) return attempt
            lastResult = attempt
        }
        return lastResult
    }

    private companion object {
        const val YouTubeThumbnailHost = "i.ytimg.com"

        /** Best first. Each entry is a whole filename, which is how YouTube sizes these. */
        val QualityLadder = listOf(
            "maxresdefault.jpg",
            "sddefault.jpg",
            "hqdefault.jpg",
            "mqdefault.jpg",
            "default.jpg",
        )

        val QualityPattern = Regex("(?:maxres|sd|hq|mq)?default\\.jpg")
    }
}
