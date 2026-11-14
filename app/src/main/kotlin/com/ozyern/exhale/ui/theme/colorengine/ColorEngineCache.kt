/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Disk tier of [ColorEngine]'s cache, mirroring `ColorEngineCache.swift`'s two-tier
 * design: an in-memory [android.util.LruCache] in front (see `ColorEngine.kt`), this
 * behind it. One JSON file per key, temp-write-then-rename like [com.ozyern.exhale
 * .playback.smart.AnalysisStore], a 7-day TTL, and a prune to [MaximumFileCount] on
 * every store — the same numbers the Swift `JoiColorAnalysisCache` used.
 */
internal class ColorEngineDiskCache(context: Context, version: String) {

    private val directory = File(context.cacheDir, "$DirectoryPrefix$version")

    private val json = Json { ignoreUnknownKeys = true }

    fun load(key: String): ColorEngineResult? {
        val file = File(directory, fileNameFor(key))
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > TimeToLiveMillis) {
            file.delete()
            return null
        }
        return runCatching { json.decodeFromString(StoredResult.serializer(), file.readText()).toResult() }
            .onFailure {
                Log.w(Tag, "Discarding unreadable colour cache entry", it)
                file.delete()
            }
            .getOrNull()
    }

    fun store(key: String, result: ColorEngineResult) {
        runCatching {
            directory.mkdirs()
            val file = File(directory, fileNameFor(key))
            val temp = File(directory, "${file.name}.tmp")
            temp.writeText(json.encodeToString(StoredResult.serializer(), StoredResult.of(result)))
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { Log.w(Tag, "Could not store colour cache entry", it) }
        prune()
    }

    fun invalidate(key: String) {
        File(directory, fileNameFor(key)).delete()
    }

    fun invalidateAll() {
        directory.deleteRecursively()
    }

    /** Cheap because it only lists when a store just happened, not on every read. */
    private fun prune() {
        val files = directory.listFiles() ?: return
        if (files.size <= MaximumFileCount) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MaximumFileCount)
            .forEach { it.delete() }
    }

    /** Hashed rather than used raw: a cache key (an artwork URL) is not a legal filename. */
    private fun fileNameFor(key: String): String = "${key.hashCode().toUInt()}_${key.length}.joicolor"

    private companion object {
        const val Tag = "ColorEngineCache"
        const val DirectoryPrefix = "color_engine_"
        val TimeToLiveMillis = 7L * 24 * 60 * 60 * 1000
        const val MaximumFileCount = 300
    }
}

// Serializable mirror of ColorEngineResult.

@Serializable
private data class StoredScheme(
    val background: List<Float>,
    val primaryText: List<Float>,
    val secondaryText: List<Float>,
    val tertiaryText: List<Float>,
    val quaternaryText: List<Float>,
    val accent: List<Float>,
    val surface: List<Float>,
    val border: List<Float>,
    val shadow: List<Float>,
    val tint: List<Float>,
    val destructive: List<Float>,
    val backgroundGradientTop: List<Float>,
    val backgroundGradientBottom: List<Float>,
    val textGradientTop: List<Float>,
    val textGradientBottom: List<Float>,
    val mesh: List<List<Float>>,
) {
    fun toScheme() = ColorEngineScheme(
        background = background.toColor(),
        primaryText = primaryText.toColor(),
        secondaryText = secondaryText.toColor(),
        tertiaryText = tertiaryText.toColor(),
        quaternaryText = quaternaryText.toColor(),
        accent = accent.toColor(),
        surface = surface.toColor(),
        border = border.toColor(),
        shadow = shadow.toColor(),
        tint = tint.toColor(),
        destructive = destructive.toColor(),
        backgroundGradientTop = backgroundGradientTop.toColor(),
        backgroundGradientBottom = backgroundGradientBottom.toColor(),
        textGradientTop = textGradientTop.toColor(),
        textGradientBottom = textGradientBottom.toColor(),
        meshColors = mesh.map { it.toColor() },
    )

    companion object {
        fun of(value: ColorEngineScheme) = StoredScheme(
            background = value.background.toList(),
            primaryText = value.primaryText.toList(),
            secondaryText = value.secondaryText.toList(),
            tertiaryText = value.tertiaryText.toList(),
            quaternaryText = value.quaternaryText.toList(),
            accent = value.accent.toList(),
            surface = value.surface.toList(),
            border = value.border.toList(),
            shadow = value.shadow.toList(),
            tint = value.tint.toList(),
            destructive = value.destructive.toList(),
            backgroundGradientTop = value.backgroundGradientTop.toList(),
            backgroundGradientBottom = value.backgroundGradientBottom.toList(),
            textGradientTop = value.textGradientTop.toList(),
            textGradientBottom = value.textGradientBottom.toList(),
            mesh = value.meshColors.map { it.toList() },
        )
    }
}

@Serializable
private data class StoredResult(
    val dark: StoredScheme,
    val light: StoredScheme,
    val style: ColorStyle,
    val sourceImageTone: ImageTone,
    val sourceToneConfidence: Float,
    val processingTimeMs: Double,
    val clusters: List<List<Double>>,
    val dominant: List<Float>,
    val secondary: List<Float>,
    val accent: List<Float>,
    val averageLab: List<Double>,
    val weightedAverageLab: List<Double>,
) {
    fun toResult(): ColorEngineResult {
        val paletteClusters = clusters.mapNotNull { values ->
            if (values.size < 3) null else LabColor(values[0], values[1], values[2])
        }
        val dominantColor = dominant.toColor()
        val secondaryColor = secondary.toColor()
        val accentColor = accent.toColor()
        val average = averageLab.toLabColor()
        val weightedAverage = weightedAverageLab.toLabColor()
        val harmonyBase = paletteClusters.firstOrNull()?.toLCh() ?: LChColor(50.0, 0.0, 0.0)
        return ColorEngineResult(
            dark = dark.toScheme(),
            light = light.toScheme(),
            palette = ColorEnginePalette(
                dominant = dominantColor,
                secondary = secondaryColor,
                accent = accentColor,
                dominantHex = dominantColor.toHex(),
                secondaryHex = secondaryColor.toHex(),
                accentHex = accentColor.toHex(),
                averageColor = average,
                weightedAverageColor = weightedAverage,
                harmonies = harmonyBase.allHarmonies(),
                clusters = paletteClusters,
            ),
            style = style,
            sourceImageTone = sourceImageTone,
            sourceToneConfidence = sourceToneConfidence,
            processingTimeMs = processingTimeMs,
        )
    }

    companion object {
        fun of(result: ColorEngineResult) = StoredResult(
            dark = StoredScheme.of(result.dark),
            light = StoredScheme.of(result.light),
            style = result.style,
            sourceImageTone = result.sourceImageTone,
            sourceToneConfidence = result.sourceToneConfidence,
            processingTimeMs = result.processingTimeMs,
            clusters = result.palette.clusters.map { listOf(it.l, it.a, it.b) },
            dominant = result.palette.dominant.toList(),
            secondary = result.palette.secondary.toList(),
            accent = result.palette.accent.toList(),
            averageLab = listOf(result.palette.averageColor.l, result.palette.averageColor.a, result.palette.averageColor.b),
            weightedAverageLab = listOf(
                result.palette.weightedAverageColor.l,
                result.palette.weightedAverageColor.a,
                result.palette.weightedAverageColor.b,
            ),
        )
    }
}

private fun Color.toList(): List<Float> = listOf(red, green, blue, alpha)
private fun List<Float>.toColor(): Color =
    if (size >= 4) Color(this[0], this[1], this[2], this[3]) else Color.Transparent
private fun List<Double>.toLabColor(): LabColor =
    if (size >= 3) LabColor(this[0], this[1], this[2]) else LabColor.Zero
