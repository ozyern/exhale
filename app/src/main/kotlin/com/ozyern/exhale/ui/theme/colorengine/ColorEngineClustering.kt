/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme.colorengine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 5: deterministic, weighted k-means over quantised LAB samples.
 *
 * Kotlin port of `ColorEngineClustering.swift`. Two things make it deterministic:
 * pixels are first collapsed into fixed LAB bins (so the input to k-means does not
 * depend on scan order), and the k-means++ seeding draws from a [SplitMix64] seeded
 * with the plane's pixel fingerprint. Every tie-break falls back to the bin's stable
 * id for the same reason.
 */

private const val Epsilon = 1e-6f

class Cluster(
    val point: LabPoint,
    val weightedShare: Float,
    val populationShare: Float,
    val textAffinity: Float,
    val meanSaliency: Float,
    val count: Int,
    val stableID: Int,
) {
    val chroma: Float get() = point.chroma
    val color: LabColor get() = point.color
}

class KMeansOutput(
    val clusters: List<Cluster>,
    val identityClusters: List<Cluster>,
    val iterations: Int,
    val quantizedSampleCount: Int,
    val fittingSampleCount: Int,
)

private class QuantizedColorSample(
    val point: LabPoint,
    val representativePoint: LabPoint,
    val centroidWeight: Float,
    val backgroundWeight: Float,
    val population: Float,
    val textPopulation: Float,
    val saliencyPopulation: Float,
    val count: Int,
    val stableID: Int,
)

private class QuantizedColorAccumulator {
    var sumL = 0f
    var sumA = 0f
    var sumB = 0f
    var centroidWeight = 0f
    var backgroundWeight = 0f
    var population = 0f
    var textPopulation = 0f
    var saliencyPopulation = 0f
    var count = 0
    var representativePoint = LabPoint(0f, 0f, 0f)
    var representativeScore = -Float.MAX_VALUE
    var representativeIndex = Int.MAX_VALUE

    fun add(
        point: LabPoint,
        alpha: Float,
        background: Float,
        textMembership: Float,
        saliency: Float,
        sampleIndex: Int,
    ) {
        val chromaSupport = smoothStep(8f, 42f, point.chroma)
        val identitySupport = 1 + chromaSupport * 0.28f + textMembership * 0.32f
        // max(background, alpha * 0.08) rather than background alone: a bin that the
        // weight map pushed to nearly zero still needs a floor, or a small but real
        // colour can be clustered out of existence entirely.
        val clusteringWeight = max(background, alpha * 0.08f) * identitySupport
        sumL += point.l * clusteringWeight
        sumA += point.a * clusteringWeight
        sumB += point.b * clusteringWeight
        centroidWeight += clusteringWeight
        backgroundWeight += background
        population += alpha
        textPopulation += alpha * textMembership
        saliencyPopulation += alpha * saliency
        count++

        val evidenceScore = alpha *
            (1 + chromaSupport * 0.24f + textMembership * 0.52f + saliency * 0.18f)
        if (evidenceScore > representativeScore + Epsilon ||
            (abs(evidenceScore - representativeScore) <= Epsilon && sampleIndex < representativeIndex)
        ) {
            representativePoint = point
            representativeScore = evidenceScore
            representativeIndex = sampleIndex
        }
    }
}

internal fun deterministicKMeans(
    plane: PixelPlane,
    weightMap: WeightMap,
    requestedCount: Int,
): KMeansOutput {
    val samples = quantizedSamples(plane, weightMap)
    if (samples.isEmpty()) {
        return KMeansOutput(emptyList(), emptyList(), 0, 0, 0)
    }
    val identityClusters = makeIdentityClusters(samples)
    val fittingSamples = clusteringRepresentatives(samples, maximumCount = 1024)
    val count = min(requestedCount, fittingSamples.size)
    val generator = SplitMix64(plane.fingerprint)
    val centroids = deterministicSeeds(fittingSamples, count, generator).toMutableList()

    var iterations = 0
    // Up to 8 passes. Below 0.75 ΔE of centroid movement nothing visible changes.
    for (iteration in 0 until 8) {
        iterations = iteration + 1
        val sumL = FloatArray(centroids.size)
        val sumA = FloatArray(centroids.size)
        val sumB = FloatArray(centroids.size)
        val sumWeight = FloatArray(centroids.size)
        for (sample in fittingSamples) {
            val bestIndex = nearestCentroid(sample.point, centroids)
            val weight = sample.centroidWeight
            sumL[bestIndex] += sample.point.l * weight
            sumA[bestIndex] += sample.point.a * weight
            sumB[bestIndex] += sample.point.b * weight
            sumWeight[bestIndex] += weight
        }
        var maximumMovementSquared = 0f
        for (centroidIndex in centroids.indices) {
            val replacement = if (sumWeight[centroidIndex] > 0f) {
                LabPoint(
                    sumL[centroidIndex] / sumWeight[centroidIndex],
                    sumA[centroidIndex] / sumWeight[centroidIndex],
                    sumB[centroidIndex] / sumWeight[centroidIndex],
                )
            } else {
                // An emptied centroid is re-seeded at the least-covered sample rather
                // than dropped, so k stays k.
                farthestSample(fittingSamples, centroids, excludedCentroid = centroidIndex)
            }
            maximumMovementSquared = max(
                maximumMovementSquared,
                centroids[centroidIndex].squaredDistance(replacement),
            )
            centroids[centroidIndex] = replacement
        }
        if (maximumMovementSquared < 0.75f * 0.75f) break
    }

    // Final assignment over ALL quantised samples (not just the fitting subset) so the
    // cluster metrics describe the finished centroids.
    val assignments = IntArray(samples.size) { nearestCentroid(samples[it].point, centroids) }
    val clusters = buildClusters(samples, centroids, assignments)

    return KMeansOutput(
        clusters = mergeAndSortClusters(clusters),
        identityClusters = identityClusters,
        iterations = iterations,
        quantizedSampleCount = samples.size,
        fittingSampleCount = fittingSamples.size,
    )
}

/** Collapse the plane into fixed LAB bins: 4 units of lightness, 8 of each opponent axis. */
private fun quantizedSamples(plane: PixelPlane, weightMap: WeightMap): List<QuantizedColorSample> {
    val lightnessStep = 4f
    val opponentStep = 8f
    val opponentBinCount = 32
    val accumulators = HashMap<Int, QuantizedColorAccumulator>(min(1024, plane.count))

    for (index in 0 until plane.count) {
        val alpha = plane.alpha[index]
        if (alpha <= 0f) continue
        val effectiveAlpha = alpha * weightMap.colorInfluence[index]
        val point = plane.points[index]
        val lightnessBin = (point.l / lightnessStep).toInt().coerceIn(0, 25)
        val aBin = ((point.a + 128) / opponentStep).toInt().coerceIn(0, opponentBinCount - 1)
        val bBin = ((point.b + 128) / opponentStep).toInt().coerceIn(0, opponentBinCount - 1)
        val stableID = (lightnessBin * opponentBinCount + aBin) * opponentBinCount + bBin
        val accumulator = accumulators.getOrPut(stableID) { QuantizedColorAccumulator() }
        accumulator.add(
            point = point,
            alpha = effectiveAlpha,
            background = weightMap.background[index],
            textMembership = weightMap.textMembership[index],
            saliency = weightMap.saliency[index],
            sampleIndex = index,
        )
    }

    return accumulators.keys.sorted().mapNotNull { stableID ->
        val accumulator = accumulators.getValue(stableID)
        if (accumulator.centroidWeight <= 0f) return@mapNotNull null
        QuantizedColorSample(
            point = LabPoint(
                accumulator.sumL / accumulator.centroidWeight,
                accumulator.sumA / accumulator.centroidWeight,
                accumulator.sumB / accumulator.centroidWeight,
            ),
            representativePoint = accumulator.representativePoint,
            centroidWeight = accumulator.centroidWeight,
            backgroundWeight = accumulator.backgroundWeight,
            population = accumulator.population,
            textPopulation = accumulator.textPopulation,
            saliencyPopulation = accumulator.saliencyPopulation,
            count = accumulator.count,
            stableID = stableID,
        )
    }
}

/**
 * Cap the k-means input at [maximumCount] bins, taking 5/8 by atmosphere (how much of
 * the picture they are) and 3/8 by identity (how much of its character they carry).
 * Purely by area, a small vivid logo never survives to be clustered.
 */
private fun clusteringRepresentatives(
    samples: List<QuantizedColorSample>,
    maximumCount: Int,
): List<QuantizedColorSample> {
    if (samples.size <= maximumCount) return samples
    val atmosphereCount = maximumCount * 5 / 8
    val identityCount = maximumCount - atmosphereCount
    val totalBackground = max(samples.sumOf { it.backgroundWeight.toDouble() }.toFloat(), Float.MIN_VALUE)
    val totalPopulation = max(samples.sumOf { it.population.toDouble() }.toFloat(), Float.MIN_VALUE)

    fun atmosphereScore(sample: QuantizedColorSample): Float =
        sample.backgroundWeight / totalBackground * 0.68f + sample.population / totalPopulation * 0.32f

    val atmosphere = samples.sortedWith(
        compareByDescending<QuantizedColorSample> { atmosphereScore(it) }.thenBy { it.stableID },
    )
    val identity = samples.sortedWith(
        compareByDescending<QuantizedColorSample> {
            representativeIdentityScore(it, totalPopulation)
        }.thenBy { it.stableID },
    )

    val selected = LinkedHashMap<Int, QuantizedColorSample>(maximumCount)
    for (sample in atmosphere.take(atmosphereCount)) selected[sample.stableID] = sample
    for (sample in identity.take(identityCount)) selected[sample.stableID] = sample
    if (selected.size < maximumCount) {
        for (sample in identity) {
            if (selected.size >= maximumCount) break
            selected[sample.stableID] = sample
        }
    }
    return selected.keys.sorted().map { selected.getValue(it) }
}

private fun representativeIdentityScore(
    sample: QuantizedColorSample,
    totalPopulation: Float,
): Float {
    val population = max(sample.population, Float.MIN_VALUE)
    val populationShare = population / totalPopulation
    val chromaSupport = smoothStep(8f, 42f, sample.point.chroma)
    val populationSupport = smoothStep(0.0005f, 0.018f, populationShare)
    val textAffinity = sample.textPopulation / population
    val meanSaliency = sample.saliencyPopulation / population
    return chromaSupport * 0.38f +
        populationSupport * 0.28f +
        textAffinity * 0.20f +
        meanSaliency * 0.14f
}

/**
 * Colours that carry the artwork's identity — a vivid accent, a logo, coloured type —
 * kept as clusters in their own right so k-means averaging cannot dissolve them into
 * the surrounding wash.
 */
private fun makeIdentityClusters(samples: List<QuantizedColorSample>): List<Cluster> {
    val totalBackground = max(samples.sumOf { it.backgroundWeight.toDouble() }.toFloat(), Float.MIN_VALUE)
    val totalPopulation = max(samples.sumOf { it.population.toDouble() }.toFloat(), Float.MIN_VALUE)

    val candidates = samples.mapNotNull { sample ->
        val population = max(sample.population, Float.MIN_VALUE)
        val populationShare = population / totalPopulation
        val textAffinity = sample.textPopulation / population
        val meanSaliency = sample.saliencyPopulation / population
        val chroma = sample.representativePoint.chroma
        val chromaticEvidence = chroma >= 10f && (
            populationShare >= 0.0015f ||
                textAffinity >= 0.16f ||
                (populationShare >= 0.0008f && meanSaliency >= 0.55f)
            )
        val neutralTextEvidence =
            chroma < 10f && populationShare >= 0.0006f && textAffinity >= 0.42f
        if (!chromaticEvidence && !neutralTextEvidence) return@mapNotNull null
        Cluster(
            point = sample.representativePoint,
            weightedShare = sample.backgroundWeight / totalBackground,
            populationShare = populationShare,
            textAffinity = textAffinity,
            meanSaliency = meanSaliency,
            count = sample.count,
            stableID = sample.stableID,
        )
    }.sortedWith(
        compareByDescending<Cluster> { semanticEvidenceScore(it) }.thenBy { it.stableID },
    )

    return mergeIdentityClusters(candidates.take(96)).take(24)
}

private fun mergeIdentityClusters(candidates: List<Cluster>): List<Cluster> {
    val merged = ArrayList<Cluster>(min(candidates.size, 24))
    for (candidate in candidates) {
        val index = merged.indexOfFirst {
            it.color.deltaE(candidate.color, DeltaEAlgorithm.CIE94) < 5.5
        }
        if (index < 0) {
            merged.add(candidate)
            continue
        }
        val existing = merged[index]
        val totalPopulation = existing.populationShare + candidate.populationShare
        val existingRatio = existing.populationShare / max(totalPopulation, Float.MIN_VALUE)
        val candidateRatio = 1 - existingRatio
        val existingScore = semanticEvidenceScore(existing)
        val candidateScore = semanticEvidenceScore(candidate)
        val representative = when {
            candidateScore > existingScore + Epsilon -> candidate
            existingScore > candidateScore + Epsilon -> existing
            existing.stableID < candidate.stableID -> existing
            else -> candidate
        }
        merged[index] = Cluster(
            point = representative.point,
            weightedShare = existing.weightedShare + candidate.weightedShare,
            populationShare = totalPopulation,
            textAffinity = existing.textAffinity * existingRatio +
                candidate.textAffinity * candidateRatio,
            meanSaliency = existing.meanSaliency * existingRatio +
                candidate.meanSaliency * candidateRatio,
            count = existing.count + candidate.count,
            stableID = representative.stableID,
        )
    }
    return merged.sortedWith(
        compareByDescending<Cluster> { semanticEvidenceScore(it) }.thenBy { it.stableID },
    )
}

internal fun semanticEvidenceScore(cluster: Cluster): Float {
    val chromaSupport = smoothStep(8f, 44f, cluster.chroma)
    val populationSupport = smoothStep(0.0005f, 0.045f, cluster.populationShare)
    val textSupport = smoothStep(0.12f, 0.68f, cluster.textAffinity)
    val atmosphereSupport = smoothStep(0.001f, 0.08f, cluster.weightedShare)
    return textSupport * 0.30f +
        chromaSupport * 0.25f +
        populationSupport * 0.24f +
        cluster.meanSaliency * 0.13f +
        atmosphereSupport * 0.08f
}

/** k-means++ seeding, weighted by clustering weight, drawn from the deterministic PRNG. */
private fun deterministicSeeds(
    samples: List<QuantizedColorSample>,
    count: Int,
    generator: SplitMix64,
): List<LabPoint> {
    if (samples.isEmpty() || count <= 0) return emptyList()
    val firstIndex = weightedSelection(FloatArray(samples.size) { samples[it].centroidWeight }, generator)
    val centroids = arrayListOf(samples[firstIndex].point)
    val minimumDistances = FloatArray(samples.size) { Float.MAX_VALUE }

    while (centroids.size < count) {
        val latest = centroids.last()
        val scores = FloatArray(samples.size)
        var totalScore = 0f
        for (index in samples.indices) {
            val distance = samples[index].point.squaredDistance(latest)
            minimumDistances[index] = min(minimumDistances[index], distance)
            val score = minimumDistances[index] * samples[index].centroidWeight
            scores[index] = score
            totalScore += score
        }
        if (totalScore <= Float.MIN_VALUE) break
        centroids.add(samples[weightedSelection(scores, generator)].point)
    }
    return centroids
}

private fun weightedSelection(scores: FloatArray, generator: SplitMix64): Int {
    var total = 0f
    for (score in scores) total += max(0f, score)
    if (total <= Float.MIN_VALUE) return 0
    val target = generator.nextUnitDouble().toFloat() * total
    var cumulative = 0f
    for (index in scores.indices) {
        cumulative += max(0f, scores[index])
        if (cumulative >= target) return index
    }
    return scores.size - 1
}

private fun nearestCentroid(point: LabPoint, centroids: List<LabPoint>): Int {
    var bestIndex = 0
    var bestDistance = point.squaredDistance(centroids[0])
    for (index in 1 until centroids.size) {
        val distance = point.squaredDistance(centroids[index])
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private fun farthestSample(
    samples: List<QuantizedColorSample>,
    centroids: List<LabPoint>,
    excludedCentroid: Int,
): LabPoint {
    var bestIndex = 0
    var bestDistance = -1f
    for (index in samples.indices) {
        val point = samples[index].point
        var minimumDistance = Float.MAX_VALUE
        for (centroidIndex in centroids.indices) {
            if (centroidIndex == excludedCentroid) continue
            minimumDistance = min(minimumDistance, point.squaredDistance(centroids[centroidIndex]))
        }
        val supportedDistance = minimumDistance * samples[index].centroidWeight
        if (supportedDistance > bestDistance) {
            bestDistance = supportedDistance
            bestIndex = index
        }
    }
    return samples[bestIndex].point
}

/**
 * Turn assignments into clusters. Each cluster's colour is a real observed bin (its
 * medoid), never the arithmetic centroid — averaging LAB across a cluster is what
 * produces the muddy browns naive extractors are known for.
 */
private fun buildClusters(
    samples: List<QuantizedColorSample>,
    centroids: List<LabPoint>,
    assignments: IntArray,
): List<Cluster> {
    val weightedPopulation = FloatArray(centroids.size)
    val rawPopulation = FloatArray(centroids.size)
    val textPopulation = FloatArray(centroids.size)
    val saliencyPopulation = FloatArray(centroids.size)
    val counts = IntArray(centroids.size)

    for (index in samples.indices) {
        val clusterIndex = assignments[index]
        val sample = samples[index]
        weightedPopulation[clusterIndex] += sample.backgroundWeight
        rawPopulation[clusterIndex] += sample.population
        textPopulation[clusterIndex] += sample.textPopulation
        saliencyPopulation[clusterIndex] += sample.saliencyPopulation
        counts[clusterIndex] += sample.count
    }

    val totalWeight = max(weightedPopulation.sum(), Float.MIN_VALUE)
    val totalPopulation = max(rawPopulation.sum(), Float.MIN_VALUE)

    val representativePoints = centroids.toMutableList()
    val representativeIDs = IntArray(centroids.size) { Int.MAX_VALUE }
    val representativeScores = FloatArray(centroids.size) { -Float.MAX_VALUE }

    for (sampleIndex in samples.indices) {
        val clusterIndex = assignments[sampleIndex]
        val sample = samples[sampleIndex]
        val clusterWeight = max(weightedPopulation[clusterIndex], Float.MIN_VALUE)
        val supportedPopulation = max(sample.backgroundWeight, sample.population * 0.25f)
        val support = supportedPopulation / clusterWeight
        val centrality = 1 / (1 + sample.point.squaredDistance(centroids[clusterIndex]) / 576f)
        val supportScore = smoothStep(0.0005f, 0.08f, support)
        val score = centrality * 0.72f + supportScore * 0.28f
        val currentScore = representativeScores[clusterIndex]
        val currentID = representativeIDs[clusterIndex]
        if (score > currentScore + Epsilon ||
            (abs(score - currentScore) <= Epsilon && sample.stableID < currentID)
        ) {
            representativeScores[clusterIndex] = score
            representativePoints[clusterIndex] = sample.point
            representativeIDs[clusterIndex] = sample.stableID
        }
    }

    return centroids.indices.mapNotNull { index ->
        if (counts[index] <= 0) return@mapNotNull null
        val raw = max(rawPopulation[index], Float.MIN_VALUE)
        Cluster(
            point = representativePoints[index],
            weightedShare = weightedPopulation[index] / totalWeight,
            populationShare = rawPopulation[index] / totalPopulation,
            textAffinity = textPopulation[index] / raw,
            meanSaliency = saliencyPopulation[index] / raw,
            count = counts[index],
            stableID = representativeIDs[index],
        )
    }
}

private fun mergeAndSortClusters(input: List<Cluster>): List<Cluster> {
    val sorted = stableClusterSort(input)
    val merged = ArrayList<Cluster>(sorted.size)
    for (cluster in sorted) {
        val index = merged.indexOfFirst {
            it.color.deltaE(cluster.color, DeltaEAlgorithm.CIE94) < 7.5
        }
        if (index < 0) {
            merged.add(cluster)
            continue
        }
        val existing = merged[index]
        val total = existing.weightedShare + cluster.weightedShare
        val firstRatio = existing.weightedShare / max(total, Float.MIN_VALUE)
        val secondRatio = 1 - firstRatio
        val representative = when {
            existing.weightedShare > cluster.weightedShare -> existing
            cluster.weightedShare > existing.weightedShare -> cluster
            existing.stableID < cluster.stableID -> existing
            else -> cluster
        }
        merged[index] = Cluster(
            point = representative.point,
            weightedShare = total,
            populationShare = existing.populationShare + cluster.populationShare,
            textAffinity = existing.textAffinity * firstRatio + cluster.textAffinity * secondRatio,
            meanSaliency = existing.meanSaliency * firstRatio + cluster.meanSaliency * secondRatio,
            count = existing.count + cluster.count,
            stableID = representative.stableID,
        )
    }
    return stableClusterSort(merged)
}

private fun stableClusterSort(clusters: List<Cluster>): List<Cluster> =
    clusters.sortedWith { first, second ->
        when {
            abs(first.weightedShare - second.weightedShare) > Epsilon ->
                second.weightedShare.compareTo(first.weightedShare)

            abs(first.populationShare - second.populationShare) > Epsilon ->
                second.populationShare.compareTo(first.populationShare)

            abs(first.point.l - second.point.l) > Epsilon -> first.point.l.compareTo(second.point.l)
            abs(first.point.a - second.point.a) > Epsilon -> first.point.a.compareTo(second.point.a)
            abs(first.point.b - second.point.b) > Epsilon -> first.point.b.compareTo(second.point.b)
            else -> first.stableID.compareTo(second.stableID)
        }
    }
