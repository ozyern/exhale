package com.ozyern.exhale.ui.player

internal fun elapsedPlaybackSeconds(positionMillis: Long): Long = positionMillis.coerceAtLeast(0) / 1000

internal fun remainingPlaybackSeconds(durationMillis: Long, positionMillis: Long): Long {
    if (durationMillis <= 0) return 0
    return (durationMillis - positionMillis.coerceIn(0, durationMillis)) / 1000
}
