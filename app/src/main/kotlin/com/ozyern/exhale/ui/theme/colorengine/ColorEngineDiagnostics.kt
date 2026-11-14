/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import android.util.Log

/**
 * Runtime telemetry for one analysis pass. Kotlin port of `ColorEngineDiagnostics.swift`'s
 * `AnalysisDiagnostics` — collected only when a caller asks for it (see [ColorEngine
 * .setDebugEnabled] / `analyze(debug = true)`), so a normal run pays nothing for it.
 */
internal class AnalysisDiagnostics {
    var cacheHit: Boolean = false
    var sampleWidth: Int = 0
    var sampleHeight: Int = 0
    var pixelCount: Int = 0
    var requestedClusters: Int = 0
    var actualClusters: Int = 0
    var kMeansIterations: Int = 0
    var quantizedSampleCount: Int = 0
    var fittingSampleCount: Int = 0
    var identityClusterCount: Int = 0
    var detailAssessment: DetailAssessment? = null
    var visionMode: VisionMode = VisionMode.DISABLED
    var style: ColorStyle = ColorStyle.BALANCED
    var toneStatistics: ToneStatistics? = null
    var backgroundRole: Cluster? = null
    var foregroundRole: Cluster? = null
    var accentRole: Cluster? = null
    val warnings: MutableList<String> = mutableListOf()
}

/**
 * Formats and logs [diagnostics] the way `JoiColorAnalysisDebugReport.printLog()` does —
 * one multi-line message per analysis, tagged so it is easy to filter in Logcat.
 */
internal fun logDiagnosticsReport(
    cacheKey: String,
    diagnostics: AnalysisDiagnostics,
    result: ColorEngineResult?,
    totalMs: Double,
) {
    val lines = mutableListOf<String>()
    lines += "ColorEngine v26-kt1 | key=$cacheKey"

    if (diagnostics.cacheHit) {
        lines += "cache: memory/disk hit"
    } else {
        lines += "sample: ${diagnostics.sampleWidth}x${diagnostics.sampleHeight}  pixels: ${diagnostics.pixelCount}"
        lines += "style: ${diagnostics.style}"

        diagnostics.detailAssessment?.let { detail ->
            lines += "detail score: %.3f  variance=%.4f  edges=%.4f  chroma=%.2f".format(
                detail.score, detail.variance, detail.edgeEnergy, detail.chromaSpread,
            )
        }

        lines += "clusters: ${diagnostics.actualClusters}/${diagnostics.requestedClusters}  " +
            "iterations=${diagnostics.kMeansIterations}  " +
            "samples=${diagnostics.pixelCount}->${diagnostics.quantizedSampleCount}->${diagnostics.fittingSampleCount}  " +
            "identity=${diagnostics.identityClusterCount}"
        lines += "vision: ${diagnostics.visionMode}"

        diagnostics.toneStatistics?.let { tone ->
            lines += "tone: ${tone.tone}  confidence=%.2f  mean=%.1f  median=%.1f".format(
                tone.confidence, tone.weightedMean, tone.weightedMedian,
            )
            lines += "coverage: dark=${percentage(tone.darkCoverage)}  light=${percentage(tone.lightCoverage)}  " +
                "chromatic=${percentage(tone.chromaticCoverage)}  strong=${percentage(tone.strongChromaticCoverage)}"
        }

        lines += "roles: background=${roleSummary(diagnostics.backgroundRole)}"
        lines += "       foreground=${roleSummary(diagnostics.foregroundRole)}"
        lines += "       accent=${roleSummary(diagnostics.accentRole)}"

        if (diagnostics.warnings.isNotEmpty()) {
            lines += "notes:"
            diagnostics.warnings.forEach { lines += "  - $it" }
        }
    }

    if (result != null) {
        lines += "dark bg=${result.dark.background} accent=${result.dark.accent}"
        lines += "light bg=${result.light.background} accent=${result.light.accent}"
    }
    lines += "total: %.2f ms".format(totalMs)

    Log.d("ColorEngine", lines.joinToString("\n"))
}

private fun roleSummary(cluster: Cluster?): String {
    if (cluster == null) return "neutral synthesis"
    val lch = cluster.color.toLCh()
    return "L=%.1f C=%.1f H=%.1f weight=%s population=%s".format(
        lch.l, lch.c, lch.h, percentage(cluster.weightedShare), percentage(cluster.populationShare),
    )
}

private fun percentage(value: Float): String = "%.1f%%".format(value * 100)
