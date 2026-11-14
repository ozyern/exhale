package com.ozyern.exhale.data.settings

import com.ozyern.exhale.playback.smart.TrackAnalysis

/**
 * Smart-analysis state of a media item, surfaced in the player UI so the
 * listener can see whether a track is being measured.
 */
enum class TrackAnalysisState {
    /** No result yet; the analyzer has not been asked. */
    WAITING,
    /** Analysis is in flight. */
    ANALYSING,
    /** A provisional head result is live; the whole-track pass is running. */
    REFINING,
    /** A whole-track result is stored. */
    ANALYSED,
    /** The analyzer tried and failed; no further attempts this session. */
    FAILED,
}
