/** GPL-3.0. Third-party portions: see NOTICE. */
package com.music.innertube.mappers

import com.music.innertube.models.YouTubeClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * YouTube Music's timed lyrics. Its Lyrics tab hands the line timings (`timedLyricsData`) only to
 * the phone apps; the web client is answered with the untimed description shelf. So the tab is
 * asked as the Android Music app, anonymously (lyrics aren't tied to an account, and a phone
 * client paired with a web session's cookie isn't a combination Google accepts), and what comes
 * back is turned into LRC. The timings are per line, so the lyrics are line-synced.
 */
internal object TimedLyrics {

    /** The Android Music client as the Lyrics tab expects it. */
    val CLIENT = YouTubeClient(
        clientName = "ANDROID_MUSIC",
        clientVersion = "7.21.50",
        clientId = "21",
        userAgent = "com.google.android.apps.youtube.music/7.21.50 (Linux; U; Android 14) gzip",
        androidSdkVersion = "34",
        osName = "Android",
        osVersion = "14",
    )

    /** [page]'s timed lyrics as LRC, or null when it carries none. */
    fun lrc(page: JsonElement): String? {
        val data = findArray(page, "timedLyricsData") ?: return null
        val lines = data.mapNotNull { element ->
            val line = element as? JsonObject ?: return@mapNotNull null
            val text = (line["lyricLine"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val cue = line["cueRange"] as? JsonObject
            val start = (cue?.get("startTimeMilliseconds") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            start to text
        }.sortedBy { it.first }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n") { (ms, text) -> "${stamp(ms)}$text" }
    }

    /** `[mm:ss.xx]` for [ms]. */
    fun stamp(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms / 1_000) % 60
        val hundredths = (ms % 1_000) / 10
        return "[" + minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0') +
            "." + hundredths.toString().padStart(2, '0') + "]"
    }

    private fun findArray(element: JsonElement, name: String): JsonArray? = when (element) {
        is JsonObject -> (element[name] as? JsonArray) ?: element.values.firstNotNullOfOrNull { findArray(it, name) }
        is JsonArray -> element.firstNotNullOfOrNull { findArray(it, name) }
        else -> null
    }
}
