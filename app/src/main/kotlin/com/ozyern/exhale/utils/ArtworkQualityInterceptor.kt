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
        // A size this thumbnail is already known to lack: go straight to the one that worked.
        // The stepped-down image is cached under its own URL, never the one asked for, so without
        // this every later load of the same cover paid the missing size's 404 round trip again.
        val asked = chain.request.data as? String
        val known = asked?.let { resolved[it] }
        if (known != null) {
            val direct = chain.withRequest(chain.request.newBuilder().data(known).build()).proceed()
            if (direct !is ErrorResult) return direct
            resolved.remove(asked)
        }

        val result = chain.proceed()
        if (result !is ErrorResult) return result

        val url = asked ?: return result
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
            if (attempt !is ErrorResult) {
                remember(url, candidate)
                return attempt
            }
            lastResult = attempt
        }
        return lastResult
    }

    private companion object {
        const val YouTubeThumbnailHost = "i.ytimg.com"

        /** Asked-for URL -> the smaller one that loaded. Bounded; oldest entries go first. */
        private const val MaxRemembered = 2048
        val resolved: MutableMap<String, String> = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, String>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > MaxRemembered
            },
        )

        fun remember(asked: String, loaded: String) {
            resolved[asked] = loaded
        }

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
