package com.ozyern.exhale.data.settings

/**
 * Snapshot of the two items surrounding a transition, so the UI can
 * display analysis progress without polling the analyzer.
 */
data class SmartAnalysis(
    val current: TrackAnalysisState = TrackAnalysisState.WAITING,
    val next: TrackAnalysisState = TrackAnalysisState.WAITING,
)
