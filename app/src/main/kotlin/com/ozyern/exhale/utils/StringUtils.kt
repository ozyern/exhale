/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.utils

import java.math.BigInteger
import java.security.MessageDigest

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""

    // Heuristic: if the value looks like an epoch millis (greater than ~1e12),
    // format as a human-readable date/time rather than a duration.
    // (1_000_000_000_000L ~= 2001-09-09 UTC)
    if (duration > 1_000_000_000_000L) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(duration))
    }

    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60

    // More human-friendly duration strings:
    return when {
        day > 0 -> "%dd %dh %dm %ds".format(day, hour, minute, sec)
        hour > 0 -> "%dh %dm %ds".format(hour, minute, sec)
        minute > 0 -> "%d:%02d".format(minute, sec)
        else -> "%d:%02d".format(0, sec)
    }
}

/**
 * A collection's running time the way Apple Music writes it: "1 hr 23 min", "45 min", or seconds
 * only when it is under a minute. Empty for nothing.
 */
fun totalLengthString(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600 + 30) / 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        minutes > 0 -> "$minutes min"
        else -> "$totalSeconds sec"
    }
}

fun md5(str: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(str.toByteArray())).toString(16).padStart(32, '0')
}

fun joinByBullet(vararg str: String?) =
    str
        .filterNot {
            it.isNullOrEmpty()
        }.joinToString(separator = " • ")
