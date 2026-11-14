/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stages 6 and 7: pick semantic roles out of the clusters, then build the dark and
 * light schemes, the mesh gradient and the palette from them.
 *
 * Kotlin port of `ColorEngineRoles.swift`, `ColorEngineSchemes.swift`,
 * `ColorEngineMesh.swift` and `ColorEnginePalette.swift`.
 */

private const val Epsilon = 1e-6f

class StyleProfile(style: ColorStyle) {
    val backgroundChroma: Double
    val textChroma: Double
    val accentChroma: Double
    val meshChroma: Double
    val surfaceChroma: Double
    val textLightnessInset: Double

    init {
        when (style) {
            ColorStyle.BALANCED -> {
                backgroundChroma = 1.08; textChroma = 1.0; accentChroma = 1.0
                meshChroma = 1.07; surfaceChroma = 1.0; textLightnessInset = 2.0
            }

            ColorStyle.VIBRANT -> {
                backgroundChroma = 1.12; textChroma = 1.28; accentChroma = 1.18
                meshChroma = 1.16; surfaceChroma = 1.08; textLightnessInset = 6.0
            }

            ColorStyle.PASTEL -> {
                backgroundChroma = 0.68; textChroma = 0.72; accentChroma = 0.76
                meshChroma = 0.70; surfaceChroma = 0.78; textLightnessInset = 0.0
            }
        }
    }
}

class RoleSelection(
    val background: Cluster,
    val secondary: Cluster,
    val foreground: Cluster?,
    val accent: Cluster?,
    val identityClusters: List<Cluster>,
)

class BackgroundPair(val source: LChColor, val dark: LChColor, val light: LChColor)

private class MeshHueFamily(
    val representative: Cluster,
    val members: MutableList<Cluster>,
    var weightedSupport: Float,
    var populationSupport: Float,
)

internal fun selectRoles(clusters: List<Cluster>, identityClusters: List<Cluster>): RoleSelection {
    val largestPopulation = clusters.maxOfOrNull { it.populationShare } ?: 1f
    val supportFloor = max(0.025f, largestPopulation * 0.09f)
    val eligibleBackgrounds = clusters.filter { it.populationShare >= supportFloor }
    val backgroundCandidates = eligibleBackgrounds.ifEmpty { clusters }
    val background = backgroundCandidates.maxByOrNull { backgroundScore(it) } ?: clusters.first()

    val remaining = clusters.filter { it.stableID != background.stableID }
    val secondary = remaining.maxByOrNull { it.weightedShare } ?: background

    val semanticCandidates = artworkClusters(remaining, identityClusters).filter { candidate ->
        candidate.stableID != background.stableID &&
            candidate.color.deltaE(background.color, DeltaEAlgorithm.CIE94) >= 5.0
    }

    val textCandidates = semanticCandidates.filter { hasDetectedTextEvidence(it) }
    val foregroundCandidates = textCandidates.ifEmpty {
        remaining.filter { hasIdentityEvidence(it) }
    }
    val foreground = foregroundCandidates
        .maxByOrNull { foregroundScore(it, background) }
        ?.let { candidate ->
            val score = foregroundScore(candidate, background)
            val threshold = if (hasDetectedTextEvidence(candidate)) 0.34f else 0.29f
            if (score >= threshold) candidate else null
        }

    val accentCandidates = semanticCandidates.filter { hasIdentityEvidence(it) }
    val distinctAccentCandidates = if (foreground != null) {
        accentCandidates
            .filter { it.color.deltaE(foreground.color, DeltaEAlgorithm.CIE94) >= 10.0 }
            .ifEmpty { accentCandidates }
    } else {
        accentCandidates
    }

    val backgroundLCh = background.color.toLCh()
    // A background with no hue of its own has no hue to be distinct FROM, so this filter
    // is skipped rather than rejecting everything.
    val hueDistinctAccentCandidates = if (backgroundLCh.c < 12) {
        emptyList()
    } else {
        distinctAccentCandidates.filter {
            val source = it.color.toLCh()
            source.c >= 14 && hueDistance(source.h, backgroundLCh.h) >= 28
        }
    }
    val preferredAccentCandidates = hueDistinctAccentCandidates.ifEmpty { distinctAccentCandidates }
    val accent = preferredAccentCandidates.maxByOrNull { accentScore(it, background) }

    return RoleSelection(background, secondary, foreground, accent, identityClusters)
}

/** Identity clusters first, then any atmosphere cluster they do not already stand for. */
private fun artworkClusters(atmosphere: List<Cluster>, identities: List<Cluster>): List<Cluster> {
    val result = ArrayList<Cluster>(identities.size + atmosphere.size)
    result.addAll(identities)
    for (cluster in atmosphere) {
        val isAlreadyRepresented = result.any {
            it.stableID == cluster.stableID ||
                it.color.deltaE(cluster.color, DeltaEAlgorithm.CIE94) < 3.5
        }
        if (!isAlreadyRepresented) result.add(cluster)
    }
    return result
}

/** The reverse ordering, for anything the user actually sees as a palette. */
internal fun presentationClusters(
    atmosphere: List<Cluster>,
    identities: List<Cluster>,
): List<Cluster> {
    val result = ArrayList<Cluster>(atmosphere.size + identities.size)
    result.addAll(atmosphere)
    for (cluster in identities) {
        val isAlreadyRepresented = result.any {
            it.color.deltaE(cluster.color, DeltaEAlgorithm.CIE94) < 4.5
        }
        if (!isAlreadyRepresented) result.add(cluster)
    }
    return result
}

private fun hasDetectedTextEvidence(cluster: Cluster): Boolean {
    val requiredAffinity = if (cluster.chroma < 10f) 0.48f else 0.30f
    return cluster.populationShare >= 0.0006f && cluster.textAffinity >= requiredAffinity
}

internal fun hasIdentityEvidence(cluster: Cluster): Boolean {
    if (cluster.chroma < 14f) return false
    // The more vivid the colour, the less of the picture it has to cover to count.
    val strongChroma = smoothStep(20f, 42f, cluster.chroma)
    val populationRequirement = 0.020f - strongChroma * 0.0165f
    val textRequirement = 0.10f - strongChroma * 0.065f
    return cluster.populationShare >= populationRequirement ||
        cluster.textAffinity >= textRequirement ||
        (cluster.populationShare >= populationRequirement * 0.55f && cluster.meanSaliency >= 0.58f)
}

private fun backgroundScore(cluster: Cluster): Float {
    val chromaUtility = smoothStep(10f, 42f, cluster.chroma)
    val chromaticSupport = smoothStep(0.025f, 0.18f, cluster.populationShare)
    val overlyVividPenalty = max(0f, cluster.chroma - 75f) / 100f
    val darknessTieBreak = (1 - (cluster.point.l / 100f).coerceIn(0f, 1f)) * 0.012f
    val extremeLightnessPenalty =
        if (cluster.point.l < 3f || cluster.point.l > 98f) 0.035f else 0f
    return cluster.weightedShare * 0.58f +
        cluster.populationShare * 0.34f +
        chromaUtility * chromaticSupport * 0.04f +
        cluster.meanSaliency * 0.02f +
        darknessTieBreak -
        overlyVividPenalty * 0.08f -
        extremeLightnessPenalty
}

private fun foregroundScore(cluster: Cluster, background: Cluster): Float {
    val delta = cluster.color.deltaE(background.color, DeltaEAlgorithm.CIEDE2000).toFloat()
    val difference = smoothStep(10f, 42f, delta)
    val chromaUtility = smoothStep(8f, 42f, cluster.chroma)
    val support = smoothStep(0.006f, 0.12f, cluster.populationShare)
    val textEvidence = smoothStep(0.16f, 0.72f, cluster.textAffinity)
    if (!hasDetectedTextEvidence(cluster)) {
        return cluster.textAffinity * 0.35f +
            difference * 0.25f +
            chromaUtility * 0.24f +
            support * 0.10f +
            cluster.meanSaliency * 0.06f
    }
    return textEvidence * 0.46f +
        difference * 0.24f +
        chromaUtility * 0.14f +
        support * 0.08f +
        cluster.meanSaliency * 0.08f
}

private fun accentScore(cluster: Cluster, background: Cluster): Float {
    val delta = cluster.color.deltaE(background.color, DeltaEAlgorithm.CIEDE2000).toFloat()
    val difference = smoothStep(12f, 48f, delta)
    val chromaUtility = smoothStep(10f, 48f, cluster.chroma)
    val support = smoothStep(0.006f, 0.14f, cluster.populationShare)
    return difference * 0.32f +
        chromaUtility * 0.32f +
        support * 0.20f +
        cluster.meanSaliency * 0.10f +
        cluster.textAffinity * 0.06f
}

private fun backgroundIdentity(roles: RoleSelection): Cluster? {
    val candidates = ArrayList<Cluster>(2)
    candidates.add(roles.secondary)
    roles.accent?.let { if (it.stableID != roles.secondary.stableID) candidates.add(it) }
    return candidates
        .filter { cluster ->
            val strongChroma = smoothStep(24f, 48f, cluster.chroma)
            val populationFloor = 0.008f - strongChroma * 0.0035f
            val weightedFloor = 0.012f - strongChroma * 0.005f
            val salientPopulationFloor = 0.0045f - strongChroma * 0.0015f
            hasIdentityEvidence(cluster) && (
                cluster.populationShare >= populationFloor ||
                    cluster.weightedShare >= weightedFloor ||
                    (cluster.populationShare >= salientPopulationFloor && cluster.meanSaliency >= 0.58f)
                )
        }
        .maxByOrNull { backgroundIdentityScore(it, roles.background) }
}

private fun backgroundIdentityInfluence(identity: Cluster): Double {
    val populationSupport = smoothStep(0.008f, 0.20f, identity.populationShare)
    val atmosphereSupport = smoothStep(0.012f, 0.22f, identity.weightedShare)
    val saliencySupport = smoothStep(0.58f, 0.88f, identity.meanSaliency)
    return (
        populationSupport * 0.52 + atmosphereSupport * 0.38 + saliencySupport * 0.10
        ).coerceIn(0.0, 0.72)
}

private fun backgroundIdentityScore(cluster: Cluster, background: Cluster): Float {
    val chromaEvidence = smoothStep(14f, 44f, cluster.chroma)
    val tonalAffinity = 1 - smoothStep(12f, 65f, abs(cluster.point.l - background.point.l))
    return cluster.weightedShare * 0.42f +
        cluster.populationShare * 0.36f +
        chromaEvidence * 0.10f +
        cluster.meanSaliency * 0.06f +
        tonalAffinity * 0.08f -
        cluster.textAffinity * 0.08f
}

private fun backgroundSource(roles: RoleSelection, chromaRetention: Double): LChColor {
    val background = roles.background.color.toLCh()
    if (background.c >= 12) {
        return background.withChroma(max(12.0, background.c * chromaRetention))
    }
    val identity = backgroundIdentity(roles) ?: return background.withChroma(0.0)
    val influence = backgroundIdentityInfluence(identity)
    val source = identity.color.toLCh()
    // A small but very vivid identity (a neon logo on grey) is worth tinting the whole
    // background from even when its influence score is low.
    val preservesSmallVividIdentity = source.c >= 32 &&
        (identity.populationShare >= 0.0045f || identity.weightedShare >= 0.007f)
    if (influence < 0.18 && !preservesSmallVividIdentity) return background.withChroma(0.0)

    val retainedChroma = max(background.c, source.c * influence) * chromaRetention
    val chroma = if (preservesSmallVividIdentity) max(12.0, retainedChroma) else retainedChroma
    if (chroma < 12) return background.withChroma(0.0)
    return LChColor(background.l, chroma, source.h)
}

/**
 * How much of the source chroma the background keeps.
 *
 * Artwork with two competing hue families, or a lot of neutral area, gets a calmer
 * background so the mesh can show the variety instead of one flat tint swallowing it.
 */
private fun backgroundChromaRetention(clusters: List<Cluster>, roles: RoleSelection): Double {
    val background = roles.background.color.toLCh()
    val source = when {
        background.c >= 12 -> background
        else -> backgroundIdentity(roles)?.color?.toLCh() ?: return 1.0
    }
    val chromaticClusters = clusters.filter {
        it.chroma >= 12f && (it.populationShare >= 0.006f || it.weightedShare >= 0.010f)
    }
    val families = makeMeshHueFamilies(chromaticClusters)
    val sourceFamily = families.minByOrNull {
        hueDistance(it.representative.color.toLCh().h, source.h)
    } ?: return 1.0
    val sourceHue = sourceFamily.representative.color.toLCh().h

    val competitor = families
        .filter { hueDistance(it.representative.color.toLCh().h, sourceHue) >= 32 }
        .maxByOrNull { meshFamilySupport(it) }

    val chromaticDiversity = if (competitor != null) {
        val supportRatio = (
            meshFamilySupport(competitor) / max(meshFamilySupport(sourceFamily), Float.MIN_VALUE)
            ).coerceIn(0f, 1f)
        val coDominance = smoothStep(0.45f, 0.90f, supportRatio)
        val hueSeparation = smoothStep(
            32f,
            88f,
            hueDistance(competitor.representative.color.toLCh().h, sourceHue).toFloat(),
        )
        coDominance * hueSeparation
    } else {
        0f
    }

    val neutralClusters = clusters.filter {
        it.chroma < 10f && (it.populationShare >= 0.006f || it.weightedShare >= 0.010f)
    }
    val neutralSupport = neutralClusters.fold(0f) { total, cluster ->
        total + cluster.weightedShare * 0.56f + cluster.populationShare * 0.44f
    }
    val neutralSupportRatio = (
        neutralSupport / max(meshFamilySupport(sourceFamily), Float.MIN_VALUE)
        ).coerceIn(0f, 1f)
    val neutralCoDominance = smoothStep(0.30f, 0.80f, neutralSupportRatio)
    val neutral = neutralClusters.maxByOrNull { meshScore(it) }
    val neutralDifference = if (neutral != null) {
        smoothStep(
            10f,
            35f,
            neutral.color.deltaE(
                sourceFamily.representative.color,
                DeltaEAlgorithm.CIEDE2000,
            ).toFloat(),
        )
    } else {
        0f
    }
    val neutralDiversity = neutralCoDominance * neutralDifference

    val attenuation = max(chromaticDiversity * 0.38f, neutralDiversity * 0.44f)
    return 1.0 - attenuation
}

internal fun makeBackgroundPair(
    clusters: List<Cluster>,
    roles: RoleSelection,
    statistics: ToneStatistics,
    style: StyleProfile,
): BackgroundPair {
    val chromaRetention = backgroundChromaRetention(clusters, roles)
    val source = backgroundSource(roles, chromaRetention)

    val counterpartSource = when {
        source.c >= 12 -> source
        else -> backgroundIdentity(roles)?.let { identity ->
            val identityColor = identity.color.toLCh()
            val identityInfluence = max(0.34, backgroundIdentityInfluence(identity))
            LChColor(
                source.l,
                max(12.0, identityColor.c * identityInfluence * chromaRetention),
                identityColor.h,
            )
        } ?: source
    }

    val sourceLightness = source.l * 0.78 + statistics.weightedMedian * 0.22
    val nativeLightness: Double
    val counterpartLightness: Double
    if (statistics.tone == ImageTone.DARK) {
        val nativeCandidate = sourceLightness.coerceIn(7.0, 36.0)
        nativeLightness = refinedDarkLightness(nativeCandidate)
        counterpartLightness = (50 + (50 - nativeCandidate) * 0.64).coerceIn(64.0, 92.0)
    } else {
        nativeLightness = sourceLightness.coerceIn(64.0, 92.0)
        counterpartLightness =
            refinedDarkLightness((50 - (nativeLightness - 50) * 0.64).coerceIn(8.0, 36.0))
    }

    val nativeSource =
        if (statistics.tone == ImageTone.LIGHT && source.c < 12 && counterpartSource.c >= 12) {
            counterpartSource
        } else {
            source
        }
    val native = backgroundColor(nativeSource, nativeLightness, style.backgroundChroma)
    val counterpart =
        backgroundColor(counterpartSource, counterpartLightness, style.backgroundChroma * 0.94)

    return if (statistics.tone == ImageTone.DARK) {
        BackgroundPair(source, native, refinedLightBackgroundChroma(counterpart))
    } else {
        BackgroundPair(source, counterpart, refinedLightBackgroundChroma(native))
    }
}

/** Compress lightness above 22 so a "dark" background never drifts into mid grey. */
private fun refinedDarkLightness(source: Double): Double {
    val compressionStart = 22.0
    if (source <= compressionStart) return source
    return compressionStart + (source - compressionStart) * 0.50
}

private fun backgroundColor(source: LChColor, lightness: Double, chromaScale: Double): LChColor {
    val sourceChroma = if (source.c < 12) 0.0 else source.c
    return fitToSRGBGamut(LChColor(lightness, min(82.0, sourceChroma * chromaScale), source.h))
}

/** Near-white light backgrounds lose their tint to the eye; give it back a little. */
private fun refinedLightBackgroundChroma(color: LChColor): LChColor {
    if (color.c < 8) return color
    val nearWhite = smoothStep(82f, 92f, color.l.toFloat())
    val restrainedChroma = 1 - smoothStep(16f, 36f, color.c.toFloat())
    val chromaScale = 1 + nearWhite * restrainedChroma * 0.12
    return fitToSRGBGamut(color.withChroma(min(82.0, color.c * chromaScale)))
}

private fun foregroundSource(roles: RoleSelection, background: LChColor): LChColor {
    val foreground = roles.foreground?.color?.toLCh()
    val accentCluster = roles.accent
    if (accentCluster != null) {
        val accent = accentCluster.color.toLCh()
        val backgroundDifference =
            accentCluster.color.deltaE(roles.background.color, DeltaEAlgorithm.CIEDE2000)
        val backgroundRole = roles.background.color.toLCh()
        val accentIsExpressive = accent.c >= 16 && backgroundDifference >= 12
        val foregroundIsNeutral = (foreground?.c ?: 0.0) < 12
        val accentHasStrongerIdentity = foreground?.let { accent.c >= it.c + 12 } ?: true
        val accentIsHueDistinct =
            hueDistance(accent.h, backgroundRole.h) >= 28 && backgroundRole.c >= 12
        val foregroundRepeatsBackgroundHue = foreground?.let {
            backgroundRole.c >= 12 && it.c >= 12 && hueDistance(it.h, backgroundRole.h) < 24
        } ?: false
        // Type takes the accent hue when the artwork's own text colour is neutral or
        // simply repeats the background.
        if (accentIsExpressive &&
            (
                foregroundIsNeutral ||
                    accentHasStrongerIdentity ||
                    (accentIsHueDistinct && foregroundRepeatsBackgroundHue)
                )
        ) {
            return accent
        }
    }
    if (foreground != null) return foreground
    if (accentCluster != null) return accentCluster.color.toLCh()
    if (background.c >= 8) return background
    return LChColor(50.0, 0.0, 0.0)
}

/** Four tiers of type, each pushed until it clears its WCAG ratio against the background. */
internal fun makeTextHierarchy(
    source: LChColor,
    background: LabColor,
    isDark: Boolean,
    style: StyleProfile,
): List<LChColor> {
    val baseLightness = if (isDark) {
        val boost = ((20 - background.l) / 20).coerceIn(0.0, 1.0) * 7
        doubleArrayOf(
            82 + boost * 0.45 - style.textLightnessInset,
            72 + boost * 0.58 - style.textLightnessInset * 0.55,
            62 + boost * 0.42 - style.textLightnessInset * 0.25,
            52 + boost * 0.24,
        )
    } else {
        val pull = ((background.l - 88) / 10).coerceIn(0.0, 1.0) * 6
        doubleArrayOf(
            18 - pull * 0.45 + style.textLightnessInset,
            29 - pull * 0.58 + style.textLightnessInset * 0.55,
            40 - pull * 0.42 + style.textLightnessInset * 0.25,
            51 - pull * 0.24,
        )
    }
    val chromaRatios = doubleArrayOf(1.05, 0.82, 0.60, 0.40)
    val chromaCaps = doubleArrayOf(68.0, 50.0, 36.0, 24.0)
    val contrastTargets = doubleArrayOf(4.5, 3.5, 3.0, 2.5)

    return (0 until 4).map { index ->
        var candidate = LChColor(
            baseLightness[index].coerceIn(3.0, 97.0),
            min(source.c * chromaRatios[index] * style.textChroma, chromaCaps[index]),
            source.h,
        )
        candidate = enforceContrast(background, candidate, contrastTargets[index], isDark)
        fitToSRGBGamut(candidate)
    }
}

/** Binary-search chroma down until the colour is representable in sRGB. */
internal fun fitToSRGBGamut(color: LChColor): LChColor {
    if (color.toLab().isInSRGBGamut) return color
    var lower = 0.0
    var upper = color.c
    repeat(14) {
        val midpoint = (lower + upper) / 2
        if (color.withChroma(midpoint).toLab().isInSRGBGamut) lower = midpoint else upper = midpoint
    }
    return color.withChroma(lower)
}

/** Move lightness (never hue or chroma) until the WCAG ratio is met. */
private fun enforceContrast(
    background: LabColor,
    candidate: LChColor,
    minimumRatio: Double,
    isDark: Boolean,
): LChColor {
    fun contrast(color: LChColor): Double {
        val first = background.relativeLuminance
        val second = color.toLab().relativeLuminance
        val brighter = max(first, second)
        val darker = min(first, second)
        return (brighter + 0.05) / (darker + 0.05)
    }
    if (contrast(candidate) >= minimumRatio) return candidate

    var lower = if (isDark) candidate.l else 0.0
    var upper = if (isDark) 100.0 else candidate.l
    repeat(18) {
        val midpoint = (lower + upper) / 2
        if (contrast(candidate.withLightness(midpoint)) >= minimumRatio) {
            if (isDark) upper = midpoint else lower = midpoint
        } else {
            if (isDark) lower = midpoint else upper = midpoint
        }
    }
    return candidate.withLightness(if (isDark) upper else lower)
}

private fun accentColor(
    roles: RoleSelection,
    background: LChColor,
    isDark: Boolean,
    style: StyleProfile,
): LChColor {
    val source = roles.accent?.color?.toLCh() ?: background
    val hasIndependentAccent = roles.accent != null
    val minimumLightness = lightnessToward(
        background.l,
        target = if (isDark) 100.0 else 0.0,
        fraction = if (hasIndependentAccent) 0.34 else 0.42,
    )
    // If the artwork's own accent already stands off the background far enough, keep the
    // observed lightness rather than forcing it to the floor.
    val preservesObservedSeparation =
        if (isDark) source.l >= minimumLightness else source.l <= minimumLightness
    var candidate = LChColor(
        if (preservesObservedSeparation) source.l else minimumLightness,
        min(78.0, source.c * style.accentChroma),
        source.h,
    )
    candidate = enforceContrast(background.toLab(), candidate, 2.25, isDark)
    return fitToSRGBGamut(candidate)
}

internal fun lightnessToward(source: Double, target: Double, fraction: Double): Double =
    (source + (target - source) * fraction).coerceIn(0.0, 100.0)

internal fun makeScheme(
    roles: RoleSelection,
    background: LChColor,
    meshColors: List<LChColor>,
    isDark: Boolean,
    style: StyleProfile,
): ColorEngineScheme {
    val backgroundLab = background.toLab()
    val text = makeTextHierarchy(foregroundSource(roles, background), backgroundLab, isDark, style)
    val accent = accentColor(roles, background, isDark, style)
    val contrastingPole = if (isDark) 100.0 else 0.0

    val surface = fitToSRGBGamut(
        background
            .withLightness(
                lightnessToward(background.l, contrastingPole, if (isDark) 0.09 else 0.07),
            )
            .withChroma(background.c * 0.82 * style.surfaceChroma),
    )
    val border = fitToSRGBGamut(
        background
            .withLightness(
                lightnessToward(background.l, contrastingPole, if (isDark) 0.20 else 0.16),
            )
            .withChroma(background.c * 0.62),
    )
    val shadow = fitToSRGBGamut(
        background
            .withLightness(lightnessToward(background.l, 0.0, if (isDark) 0.58 else 0.72))
            .withChroma(background.c * 0.28),
    )
    val tint = fitToSRGBGamut(
        accent
            .withLightness(lightnessToward(accent.l, contrastingPole, 0.08))
            .withChroma(accent.c * 0.86),
    )
    val destructive = LChColor(if (isDark) 58.0 else 44.0, 58.0, 28.0)
    val gradientTop = fitToSRGBGamut(
        background
            .withLightness(
                lightnessToward(background.l, if (isDark) 0.0 else 100.0, 0.055),
            )
            .withChroma(background.c * 1.02),
    )
    val gradientBottom = fitToSRGBGamut(
        background
            .withLightness(lightnessToward(background.l, contrastingPole, 0.085))
            .withChroma(background.c * 0.94),
    )

    return ColorEngineScheme(
        background = background.toColor(),
        primaryText = text[0].toColor(),
        secondaryText = text[1].toColor(),
        tertiaryText = text[2].toColor(),
        quaternaryText = text[3].toColor(),
        accent = accent.toColor(),
        surface = surface.toColor(),
        border = border.toColor(),
        shadow = shadow.toColor(),
        tint = tint.toColor(),
        destructive = destructive.toColor(),
        backgroundGradientTop = gradientTop.toColor(),
        backgroundGradientBottom = gradientBottom.toColor(),
        textGradientTop = text[0].toColor(),
        textGradientBottom = text[1].toColor(),
        meshColors = meshColors.map { it.toColor() },
    )
}

private fun makeMeshHueFamilies(clusters: List<Cluster>): List<MeshHueFamily> {
    val families = ArrayList<MeshHueFamily>()
    for (cluster in clusters) {
        val source = cluster.color.toLCh()
        val index = families.indexOfFirst {
            hueDistance(source.h, it.representative.color.toLCh().h) <= 24
        }
        if (index >= 0) {
            families[index].members.add(cluster)
            families[index].weightedSupport += cluster.weightedShare
            families[index].populationSupport += cluster.populationShare
        } else {
            families.add(
                MeshHueFamily(cluster, mutableListOf(cluster), cluster.weightedShare, cluster.populationShare),
            )
        }
    }
    return families.sortedWith { first, second ->
        val firstSupport = meshFamilySupport(first)
        val secondSupport = meshFamilySupport(second)
        if (abs(firstSupport - secondSupport) > Epsilon) {
            secondSupport.compareTo(firstSupport)
        } else {
            first.representative.stableID.compareTo(second.representative.stableID)
        }
    }
}

private fun meshFamilySupport(family: MeshHueFamily): Float =
    family.weightedSupport * 0.56f + family.populationSupport * 0.44f

private fun meshScore(cluster: Cluster): Float {
    val chromaUtility = smoothStep(8f, 42f, cluster.chroma)
    val populationSupport = smoothStep(0.008f, 0.18f, cluster.populationShare)
    return cluster.weightedShare * 0.42f +
        populationSupport * 0.30f +
        chromaUtility * populationSupport * 0.18f +
        cluster.meanSaliency * 0.10f
}

private fun meshColor(source: LChColor, style: StyleProfile): LChColor {
    val chroma = if (source.c < 8) 0.0 else min(84.0, source.c * style.meshChroma)
    return fitToSRGBGamut(LChColor(source.l.coerceIn(2.0, 98.0), chroma, source.h))
}

internal fun makeMeshSourceColors(
    clusters: List<Cluster>,
    roles: RoleSelection,
    statistics: ToneStatistics,
    style: StyleProfile,
): List<LChColor> {
    val allClusters = presentationClusters(clusters, roles.identityClusters)
    val accentID = roles.accent?.stableID
    val atmosphereClusters = allClusters
        .filter {
            it.populationShare >= 0.006f ||
                it.weightedShare >= 0.010f ||
                (
                    hasIdentityEvidence(it) &&
                        (it.populationShare >= 0.003f || it.meanSaliency >= 0.55f || it.stableID == accentID)
                    )
        }
        .sortedWith(compareByDescending<Cluster> { meshScore(it) }.thenBy { it.stableID })

    val chromaticClusters = atmosphereClusters.filter { it.chroma >= 12f }
    val base = roles.background.color.toLCh()
    if (chromaticClusters.none { hasIdentityEvidence(it) }) {
        return neutralMesh(atmosphereClusters, base, statistics.tone)
    }
    val families = makeMeshHueFamilies(chromaticClusters)
    val strongestFamily = families.firstOrNull()
        ?: return neutralMesh(atmosphereClusters, base, statistics.tone)

    val populationFloor = max(0.008f, strongestFamily.populationSupport * 0.07f)
    val weightedFloor = max(0.010f, strongestFamily.weightedSupport * 0.07f)
    val selectedFamilies = families.filter {
        it.populationSupport >= populationFloor ||
            it.weightedSupport >= weightedFloor ||
            it.members.any { member -> member.stableID == accentID }
    }.toMutableList()
    if (selectedFamilies.isEmpty()) selectedFamilies.add(strongestFamily)

    fun contrastFamilyScore(family: MeshHueFamily): Float {
        val difference = family.representative.color
            .deltaE(strongestFamily.representative.color, DeltaEAlgorithm.CIEDE2000).toFloat()
        return smoothStep(16f, 48f, difference) * 0.56f +
            smoothStep(14f, 48f, family.representative.chroma) * 0.28f +
            smoothStep(0.003f, 0.05f, family.populationSupport) * 0.12f +
            family.representative.meanSaliency * 0.04f
    }

    // Keep one genuinely contrasting identity hue in slot 2, so a two-colour cover reads
    // as two colours instead of six shades of the dominant one.
    val contrastFamily = families.drop(1)
        .filter { family ->
            family.members.any { hasIdentityEvidence(it) } &&
                family.representative.color.deltaE(
                    strongestFamily.representative.color,
                    DeltaEAlgorithm.CIEDE2000,
                ) >= 16.0
        }
        .maxByOrNull { contrastFamilyScore(it) }
    if (contrastFamily != null) {
        selectedFamilies.removeAll {
            it.representative.stableID == contrastFamily.representative.stableID
        }
        selectedFamilies.add(min(1, selectedFamilies.size), contrastFamily)
    }

    // A broad black/white/grey region is a real artwork element; reserve it a slot.
    val supportedNeutral = atmosphereClusters
        .filter { it.chroma < 10f && (it.populationShare >= 0.10f || it.weightedShare >= 0.12f) }
        .maxByOrNull { meshScore(it) }
    val chromaticColorLimit = if (supportedNeutral == null) 6 else 5

    val colors = ArrayList<LChColor>(6)
    fun appendIfDistinct(source: LChColor, minimumDelta: Double, minimumChromaRetention: Double) {
        val candidate = meshColor(source, style)
        val intendedChroma = if (source.c < 8) 0.0 else min(84.0, source.c * style.meshChroma)
        // Reject a colour the gamut fit desaturated past recognition — it would read as
        // a duplicate grey rather than the hue it came from.
        if (intendedChroma >= 18 && candidate.c / intendedChroma < minimumChromaRetention) return
        val isDistinct = colors.all {
            it.toLab().deltaE(candidate.toLab(), DeltaEAlgorithm.CIE94) >= minimumDelta
        }
        if (isDistinct) colors.add(candidate)
    }

    for (family in selectedFamilies) {
        appendIfDistinct(family.representative.color.toLCh(), 8.0, 0.0)
        if (colors.size == chromaticColorLimit) break
    }
    supportedNeutral?.let { appendIfDistinct(it.color.toLCh().withChroma(0.0), 5.0, 0.0) }

    if (colors.size < 6) {
        outer@ for (family in selectedFamilies) {
            val members = family.members.sortedWith(
                compareByDescending<Cluster> { meshScore(it) }.thenBy { it.stableID },
            )
            for (member in members) {
                if (member.stableID == family.representative.stableID) continue
                appendIfDistinct(member.color.toLCh(), 8.0, 0.0)
                if (colors.size == 6) break@outer
            }
        }
    }

    // Mesh gradients need at least 3 stops. A single-family cover gets lightness
    // variations along its own observed range rather than invented hues.
    val identityFamily = selectedFamilies[0]
    val identity = identityFamily.representative.color.toLCh()
    val observedLightness = identityFamily.members.map { it.point.l.toDouble() }
    val lowerObserved = observedLightness.minOrNull() ?: identity.l
    val upperObserved = observedLightness.maxOrNull() ?: identity.l
    val observedSpan = upperObserved - lowerObserved
    val lowerLightness =
        if (observedSpan >= 8) lowerObserved else lightnessToward(identity.l, 0.0, 0.10)
    val upperLightness =
        if (observedSpan >= 8) upperObserved else lightnessToward(identity.l, 100.0, 0.12)
    val variants = listOf(
        LChColor(lowerLightness, identity.c * 0.94, identity.h),
        LChColor(upperLightness, identity.c * 1.04, identity.h),
        LChColor((lowerLightness + upperLightness) / 2, identity.c, identity.h),
    )
    for (variant in variants) {
        if (colors.size >= 3) break
        appendIfDistinct(variant, 6.0, 0.72)
    }
    val fallbackChromaScales = doubleArrayOf(0.82, 1.0, 1.12)
    while (colors.size < 3) {
        val scale = fallbackChromaScales[colors.size]
        colors.add(meshColor(LChColor(identity.l, identity.c * scale, identity.h), style))
    }
    return colors.take(6)
}

/** Re-lights the mesh sources against a specific scheme's background. */
internal fun makeSchemeMeshColors(
    sources: List<LChColor>,
    background: LChColor,
    isDark: Boolean,
): List<LChColor> {
    if (sources.isEmpty()) return listOf(background)
    val minimumSourceLightness = sources.minOf { it.l }
    val maximumSourceLightness = sources.maxOf { it.l }
    val sourceSpan = maximumSourceLightness - minimumSourceLightness
    val lowerLightness = lightnessToward(background.l, 0.0, if (isDark) 0.28 else 0.23)
    val upperLightness = lightnessToward(background.l, 100.0, if (isDark) 0.25 else 0.18)
    val maximumSourceChroma = sources.maxOf { it.c }

    val lightNearWhite = if (isDark) 0f else smoothStep(82f, 92f, background.l.toFloat())
    val restrainedPalette = 1 - smoothStep(22f, 42f, maximumSourceChroma.toFloat())
    val lightChromaCompensation = 1 + lightNearWhite * restrainedPalette * 0.12
    val themeChromaScale = if (isDark) 0.94 else 0.85 * lightChromaCompensation
    val themeChromaLimit = max(if (isDark) 44.0 else 40.0, background.c * 1.20 + 18)
    val sourceCount = sources.size

    return sources.mapIndexed { index, source ->
        val tonalPosition = when {
            sourceSpan >= 6 -> ((source.l - minimumSourceLightness) / sourceSpan).coerceIn(0.0, 1.0)
            sourceCount > 1 -> index.toDouble() / (sourceCount - 1)
            else -> 0.5
        }
        val lightness = lowerLightness + (upperLightness - lowerLightness) * tonalPosition
        val tonalDistance = abs(tonalPosition - 0.5) * 2
        val chromaVariation = 0.90 + tonalDistance * 0.14
        val hueSeparation = if (background.c < 8) {
            if (source.c >= 12) 1f else 0f
        } else {
            smoothStep(18f, 72f, hueDistance(source.h, background.h).toFloat())
        }
        val identityChromaScale = 1 + hueSeparation * 0.18
        val identityChromaLimit = themeChromaLimit + hueSeparation * 6
        val chroma = if (source.c < 8) {
            0.0
        } else {
            min(source.c * themeChromaScale * chromaVariation * identityChromaScale, identityChromaLimit)
        }
        fitToSRGBGamut(LChColor(lightness, chroma, source.h))
    }
}

private fun neutralMesh(clusters: List<Cluster>, base: LChColor, tone: ImageTone): List<LChColor> {
    val neutralClusters = clusters
        .filter { it.chroma < 10f }
        .sortedWith(compareByDescending<Cluster> { meshScore(it) }.thenBy { it.stableID })

    val colors = ArrayList<LChColor>(3)
    for (cluster in neutralClusters) {
        val candidate = LChColor(cluster.point.l.toDouble(), 0.0, 0.0)
        if (colors.all { abs(it.l - candidate.l) >= 7 }) colors.add(candidate)
        if (colors.size == 3) break
    }
    val tonalBase = colors.firstOrNull()?.l ?: base.l
    val firstTarget =
        lightnessToward(tonalBase, if (tone == ImageTone.DARK) 100.0 else 0.0, 0.16)
    val secondTarget =
        lightnessToward(tonalBase, if (tone == ImageTone.DARK) 0.0 else 100.0, 0.16)
    for (lightness in listOf(firstTarget, secondTarget)) {
        if (colors.size >= 3) break
        val candidate = LChColor(lightness, 0.0, 0.0)
        if (colors.all { abs(it.l - candidate.l) >= 5 }) colors.add(candidate)
    }
    while (colors.size < 3) {
        val fraction = (colors.size + 1) / 4.0
        colors.add(
            LChColor(
                lightnessToward(tonalBase, if (tone == ImageTone.DARK) 100.0 else 0.0, fraction * 0.24),
                0.0,
                0.0,
            ),
        )
    }
    return colors
}

internal fun makePalette(
    roles: RoleSelection,
    clusters: List<Cluster>,
    backgroundSource: LChColor,
    averages: Pair<LabColor, LabColor>,
): ColorEnginePalette {
    val dominant = fitToSRGBGamut(backgroundSource).toColor()
    val secondary = roles.secondary.color.toColor()
    val accentSource = roles.accent?.color?.toLCh() ?: backgroundSource
    val accent = fitToSRGBGamut(accentSource).toColor()
    return ColorEnginePalette(
        dominant = dominant,
        secondary = secondary,
        accent = accent,
        dominantHex = dominant.toHex(),
        secondaryHex = secondary.toHex(),
        accentHex = accent.toHex(),
        averageColor = averages.first,
        weightedAverageColor = averages.second,
        harmonies = backgroundSource.allHarmonies(),
        clusters = clusters.map { it.color },
    )
}

/**
 * The four text tiers — primary, secondary, tertiary, quaternary — for a background the
 * caller already chose, hue-tinted from [tint].
 *
 * The full [ColorEngine.analyze] pipeline picks the background itself; the hero screens
 * do not, because their backgrounds go through their own tint clamps first. This runs
 * the same lightness/chroma ladder and the same WCAG binary search against whatever
 * background it is handed, so type on a hero screen is derived, not guessed at with a
 * fixed white-at-70%-alpha.
 */
fun textHierarchyOn(
    background: Color,
    tint: Color = background,
    style: ColorStyle = ColorStyle.BALANCED,
): List<Color> {
    val backgroundLab = background.toLabColor()
    // L* 50 is the perceptual midpoint, which is the question "is this a dark surface"
    // actually asks -- not the sRGB average, which calls mid greens light and mid blues
    // dark.
    val isDark = backgroundLab.l < 50.0
    return makeTextHierarchy(
        source = tint.toLabColor().toLCh(),
        background = backgroundLab,
        isDark = isDark,
        style = StyleProfile(style),
    ).map { it.toColor() }
}
