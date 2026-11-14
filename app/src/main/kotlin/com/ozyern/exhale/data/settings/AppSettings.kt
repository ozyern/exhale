package com.ozyern.exhale.data.settings

/**
 * Convenience object that re-exports PlaybackSettings properties so
 * CrossfadeController's `AppSettings.crossfadeSeconds` calls resolve
 * without changing every call site.
 */
object AppSettings {
    val crossfadeSeconds get() = PlaybackSettings.crossfadeSeconds
    val playbackSpeed get() = PlaybackSettings.playbackSpeed
    val smartFadeEnabled get() = PlaybackSettings.smartFadeEnabled
    val smartMixInProgress get() = PlaybackSettings.smartMixInProgress
    val smartTransitionWindow get() = PlaybackSettings.smartTransitionWindow
    val smartAnalysis get() = PlaybackSettings.smartAnalysis
    val audioCacheLimitBytes get() = PlaybackSettings.audioCacheLimitBytes

    const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
}
