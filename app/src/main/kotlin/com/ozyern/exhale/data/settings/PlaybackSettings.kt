package com.ozyern.exhale.data.settings

/**
 * Playback speed multiplier exposed to the UI. MutableState so
 * CrossfadeController can read it without a settings round-trip.
 */
object PlaybackSettings {
    val playbackSpeed = androidx.compose.runtime.mutableFloatStateOf(1f)
    val crossfadeSeconds = androidx.compose.runtime.mutableFloatStateOf(0f)
    val smartFadeEnabled = androidx.compose.runtime.mutableStateOf(true)
    val smartMixInProgress = androidx.compose.runtime.mutableStateOf(false)
    val smartTransitionWindow = androidx.compose.runtime.mutableStateOf<TransitionWindow?>(null)
    val smartAnalysis = androidx.compose.runtime.mutableStateOf(SmartAnalysis())

    /** Cache budget for AudioCache in bytes. */
    val audioCacheLimitBytes = androidx.compose.runtime.mutableLongStateOf(512L * 1024 * 1024)
}
