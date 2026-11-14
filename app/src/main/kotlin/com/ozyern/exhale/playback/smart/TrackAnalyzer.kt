/*
 * Based on Orchard's TrackAnalyzer (https://github.com/SFG5453/Orchard). Beat and vocal
 * analysis runs over the track head and tail, which transition logic reads.
 *
 * Copyright (C) 2026 Convx contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.ozyern.exhale.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.ozyern.exhale.playback.AudioCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/** Produces cached track analysis for [TransitionPlanner] without blocking playback. */
@UnstableApi
class TrackAnalyzer(context: Context, private val cache: AudioCache) {

    private val appContext = context.applicationContext

    private val power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

    /**
     * Analysis is CPU and model heavy; a warm phone or Battery Saver should not be asked for more.
     * The first measurement a transition is waiting on only yields when the phone is already hot;
     * the minutes-long refinement also yields to Battery Saver and mild throttling.
     */
    private fun deferred(refine: Boolean): Boolean {
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
        return if (refine) power.isPowerSaveMode || thermal >= android.os.PowerManager.THERMAL_STATUS_MODERATE
        else thermal >= android.os.PowerManager.THERMAL_STATUS_SEVERE
    }

    private val trackerDelegate = lazy { BeatTracker(context.applicationContext) }
    private val vocalsDelegate = lazy { VocalTracker(context.applicationContext) }
    private val tracker by trackerDelegate
    private val vocals by vocalsDelegate
    private val lifecycleLock = Any()
    private val analysisRevision = AtomicLong()
    @Volatile private var released = false
    private val activeRevision = ThreadLocal<Long>()

    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val running = ConcurrentHashMap<String, Long>()
    private val retryAfter = ConcurrentHashMap<String, Long>()

    /** Tracks whose result came from [analyzeHead] and is waiting to be superseded. */
    private val provisional = ConcurrentHashMap.newKeySet<String>()

    /** Last analyzed byte prefix per rendition, used to avoid repeating partial decodes. */
    private val headAttempts = ConcurrentHashMap<String, Long>()

    private val headSkipLogged = ConcurrentHashMap.newKeySet<String>()

    /** Short whole-track decode attempts before a rendition is treated as truncated. */
    private val shortDecodes = ConcurrentHashMap<String, Int>()

    /** Tracks already looked for on disk this session; see [restoreOnce]. */
    private val restoreAttempted = ConcurrentHashMap.newKeySet<String>()

    /** Results that survive the process, so a track is measured once and stays measured. */
    private val store = AnalysisStore(context)

    private val badRenditions = ConcurrentHashMap.newKeySet<String>()

    private val triedRenditions = ConcurrentHashMap.newKeySet<String>()

    /** Discard attempts per rendition, bounded to prevent repeated refetch loops. */
    private val discarded = ConcurrentHashMap<String, Int>()

    private fun discardsOf(key: String): Int = discarded[key] ?: 0

    private fun lane(name: String, threadPriority: Int) = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
    ) { runnable ->
        Thread({
            Process.setThreadPriority(threadPriority)
            runnable.run()
        }, name).apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    /** First measurement of a track; a transition is waiting on this one. */
    private val headExecutor = lane("exhale-smart-analysis", Process.THREAD_PRIORITY_BACKGROUND)

    /** Whole-track refinement of a track that already has a result; minutes long and never urgent. */
    private val refineExecutor = lane("exhale-smart-refine", Process.THREAD_PRIORITY_LOWEST)

    fun analysisFor(trackId: String): TrackAnalysis = results[trackId] ?: TrackAnalysis(trackId = trackId)

    private fun restoreOnce(trackId: String, uri: Uri? = null) {
        if (results.containsKey(trackId) || !restoreAttempted.add(trackId)) return
        val fingerprint = uri?.takeIf(::isDeviceFile)?.let { { SongFingerprint.of(appContext, it) } }
        store.load(trackId, fingerprint)?.let { results.putIfAbsent(trackId, it) }
    }

    fun isAnalysed(trackId: String): Boolean = results.containsKey(trackId)

    fun isAnalysing(trackId: String): Boolean = running.containsKey(trackId)

    fun request(trackId: String, uri: Uri, durationSeconds: Double) {
        if (trackId.isBlank()) return
        if (results[trackId]?.isUsable == true && trackId !in provisional) return
        if (deferred(refine = results.containsKey(trackId))) return
        synchronized(lifecycleLock) {
            if (released) return
            val now = SystemClock.elapsedRealtime()
            if (now < (retryAfter[trackId] ?: 0L)) return
            val revision = analysisRevision.get()
            if (running.putIfAbsent(trackId, revision) != null) return
            retryAfter[trackId] = now + REQUEST_RETRY_MS
            val executor = if (results.containsKey(trackId)) refineExecutor else headExecutor
            executor.execute {
                activeRevision.set(revision)
                try {
                    checkWorkCurrent()
                    analyzeRequest(trackId, uri, durationSeconds)
                } catch (_: CancellationException) {
                    // Cancellation does not turn a pending analysis into a failed result.
                } finally {
                    synchronized(lifecycleLock) {
                        if (running.remove(trackId, revision) && !released &&
                            revision == analysisRevision.get()
                        ) retryAfter[trackId] = SystemClock.elapsedRealtime() + REQUEST_RETRY_MS
                    }
                    if (running.isEmpty()) releaseModels()
                }
            }
        }
    }

    private fun analyzeRequest(trackId: String, uri: Uri, durationSeconds: Double) {
        var wholeAttempt = false
        try {
            restoreOnce(trackId, uri)
            checkWorkCurrent()
            val recorded = results[trackId]
            if (recorded?.isUsable == true && trackId !in provisional) return
            val local = isDeviceFile(uri)
            val complete = if (local) listOf(deviceRendition(trackId)) else cache.renditionsOf(uri).filter { it.isComplete && it.key !in badRenditions }
            val usableComplete = complete.isNotEmpty()
            val untried = complete.any { it.key !in triedRenditions }
            val supersedable = when {
                recorded == null -> false
                trackId in provisional -> usableComplete
                else -> !recorded.isUsable && untried
            }
            if (recorded != null && !supersedable) {
                if (trackId in provisional && !usableComplete) cache.requestAnalysisHead(uri)
                return
            }
            if (recorded != null && !recorded.isUsable) shortDecodes.remove(trackId)
            val headRendition = if (usableComplete) null else headWorthTrying(trackId, uri, durationSeconds)
            if (!usableComplete && headRendition == null) {
                cache.requestAnalysisHead(uri)
                return
            }
            checkWorkCurrent()
            wholeAttempt = usableComplete
            if (usableComplete) {
                val outcome = analyze(trackId, uri, durationSeconds)
                checkWorkCurrent()
                val whole = outcome.analysis
                if (whole != null) {
                    results[trackId] = whole
                    provisional.remove(trackId)
                    shortDecodes.remove(trackId)
                    store.save(trackId, whole, uri.takeIf(::isDeviceFile)?.let { SongFingerprint.of(appContext, it) })
                    restoreAttempted.add(trackId)
                } else if (outcome.decodedShort) {
                    val attempts = (shortDecodes[trackId] ?: 0) + 1
                    shortDecodes[trackId] = attempts
                    if (attempts >= MAX_SHORT_DECODE_ATTEMPTS && trackId !in provisional) {
                        results[trackId] = empty(trackId, durationSeconds)
                    }
                }
            } else if (headRendition != null) {
                val head = analyzeHead(trackId, uri, durationSeconds, headRendition)
                checkWorkCurrent()
                if (head != null) {
                    provisional.add(trackId)
                    results[trackId] = head
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!isWorkCurrent()) return
            Log.w(TAG, "Analysis of $trackId failed", error)
            if (wholeAttempt) {
                results[trackId] = empty(trackId, durationSeconds)
                provisional.remove(trackId)
            }
        }
    }

    /** Replaces a track's vocal mask with the one measured from its stem, and stores it. */
    fun applyStemMask(trackId: String) {
        restoreOnce(trackId)
        val current = results[trackId] ?: return
        val mask = StemVocalMask.forTrack(appContext, trackId, current.energyCurve) ?: return
        val updated = current.copy(vocalActivityMask = mask)
        results[trackId] = updated
        store.save(trackId, updated)
    }

    fun cancelPending() {
        synchronized(lifecycleLock) {
            if (released) return
            analysisRevision.incrementAndGet()
            headExecutor.queue.clear()
            refineExecutor.queue.clear()
            running.clear()
            retryAfter.clear()
        }
    }

    private fun isWorkCurrent(): Boolean = !released && activeRevision.get() == analysisRevision.get()

    private fun checkWorkCurrent() {
        if (!isWorkCurrent()) throw CancellationException("Track analysis cancelled")
    }

    private fun releaseModels() {
        if (trackerDelegate.isInitialized()) tracker.release()
        if (vocalsDelegate.isInitialized()) vocals.release()
    }

    /**
     * Finds a cached rendition with enough new audio to justify another head analysis.
     * The required bytes are estimated from duration and bitrate when available;
     * otherwise retries wait for the cached prefix to grow by [HEAD_RETRY_GROWTH].
     */
    private fun headWorthTrying(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
    ): AudioCache.Rendition? {
        // The cached audio may use a sibling key instead of the active URI's key.
        val candidate = cache.renditionsOf(uri)
            .filter { it.cachedPrefix > 0L && it.key !in badRenditions }
            // Filter each copy so a stalled candidate does not block the others.
            .filter { rendition ->
                val previous = headAttempts[rendition.key] ?: return@filter true
                rendition.cachedPrefix >= previous * HEAD_RETRY_GROWTH
            }
            // Compare playable time because renditions can have different bitrates.
            .maxByOrNull { headSecondsOf(it, durationSeconds) }
            ?: return null

        val prefix = candidate.cachedPrefix
        val total = candidate.contentLength
        val needed = if (durationSeconds.isFinite() && durationSeconds > MIN_HEAD_SECONDS && total > 0) {
            val bytesPerSecond = total / durationSeconds
            // Require the minimum decodable audio, not the model's full window.
            // Retries can use a longer prefix as more audio arrives.
            (MIN_HEAD_SECONDS * bytesPerSecond * HEAD_BYTES_MARGIN).toLong()
                .coerceAtLeast(MIN_HEAD_BYTES)
                .coerceAtMost(total)
        } else {
            MIN_HEAD_BYTES
        }
        if (prefix < needed) {
            // Avoid repeating the same skip message on every analysis tick.
            if (headSkipLogged.add("$trackId@${candidate.key}")) {
                Log.d(
                    TAG,
                    "Head pass for $trackId waiting: ${prefix / 1024}kB cached of " +
                        "${needed / 1024}kB needed (rendition ${candidate.key})",
                )
            }
            return null
        }

        headAttempts[candidate.key] = prefix
        return candidate
    }

    /**
     * Estimates how many seconds of audio the cached prefix contains.
     * Uses the rendition's duration when known, otherwise a typical byte rate.
     */
    private fun headSecondsOf(rendition: AudioCache.Rendition, durationSeconds: Double): Double =
        if (rendition.contentLength > 0 && durationSeconds.isFinite() && durationSeconds > 0) {
            rendition.cachedPrefix * durationSeconds / rendition.contentLength
        } else {
            rendition.cachedPrefix / ASSUMED_BYTES_PER_SECOND
        }

    /**
     * Analyzes the cached opening and keeps only entry features that fit within it.
     * Whole-track fields and vocal masks stay unset because a partial curve could
     * incorrectly describe the missing tail.
     */
    private fun analyzeHead(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
        rendition: AudioCache.Rendition,
    ): TrackAnalysis? {
        fun openSource(): MediaDataSource? = cache.renditionDataSource(uri, rendition)

        // A sibling copy must match the playing track's duration. If its partial
        // header is unreadable, let it retry after more bytes arrive.
        val expected = durationSeconds.takeIf { it.isFinite() && it > 0 }
        if (expected != null && rendition.key != cache.cacheKeyOf(uri)) {
            val length = openSource()?.use(AudioDecoder::containerDurationSeconds)
            if (length == null || length <= 0) {
                // A partial container may expose its duration after more bytes arrive.
                Log.d(TAG, "Head rendition ${rendition.key} for $trackId has no readable duration yet")
                return null
            }
            if (abs(length - expected) > RENDITION_DURATION_TOLERANCE) {
                Log.d(
                    TAG,
                    "Head rendition ${rendition.key} rejected for $trackId: " +
                        "${"%.1f".format(length)}s against ${"%.1f".format(expected)}s expected",
                )
                badRenditions.add(rendition.key)
                return null
            }
        }

        val window = BeatTracker.WINDOW_SECONDS
        val head = region(::openSource, 0.0, window, features = null, deriveFeatures = true)
            ?: run {
                Log.d(TAG, "Head pass for $trackId could not decode rendition ${rendition.key}")
                return null
            }
        // Reject short reads because partial audio can produce unreliable tempo estimates.
        if (head.seconds < MIN_HEAD_SECONDS) {
            Log.d(TAG, "Head pass for $trackId decoded only ${"%.1f".format(head.seconds)}s; too short")
            return null
        }
        val grid = head.grid
        val entry = head.features
        if (grid == null && entry == null) {
            Log.d(TAG, "Head pass for $trackId produced nothing usable")
            return null
        }

        Log.d(
            TAG,
            "Analysed head of $trackId: bpm=${grid?.bpm ?: entry?.bpm} " +
                "conf=${grid?.beatConfidence ?: entry?.beatConfidence} " +
                "audibleStart=${entry?.audibleStartTime} pickup=${entry?.pickupTime} " +
                "introEnd=${entry?.introEndTime} mixInCandidates=${entry?.mixInCandidates?.size ?: 0} " +
                "over ${"%.1f".format(head.seconds)}s",
        )

        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = durationSeconds,
            bpm = grid?.bpm ?: entry?.bpm ?: 0.0,
            beatInterval = grid?.beatInterval ?: entry?.beatInterval ?: 0.0,
            beatConfidence = grid?.beatConfidence ?: entry?.beatConfidence ?: 0.0,
            downbeats = grid?.downbeats ?: entry?.downbeats.orEmpty(),
            firstBeat = grid?.firstBeat ?: entry?.firstBeat ?: 0.0,
            key = entry?.key.orEmpty(),
            keyConfidence = entry?.keyConfidence ?: 0.0,
            audibleStartTime = entry?.audibleStartTime,
            pickupTime = entry?.pickupTime,
            introEndTime = entry?.introEndTime ?: 0.0,
            mixInTime = entry?.mixInTime ?: 0.0,
            mixInCandidates = entry?.mixInCandidates.orEmpty(),
        )
    }

    /**
     * Chooses a complete cached copy that matches the playing track's duration.
     * Reusing a sibling copy lets analysis start before a quality upgrade finishes.
     */
    private fun chooseRendition(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
    ): AudioCache.Rendition? {
        val complete = cache.renditionsOf(uri)
            .filter { it.isComplete && it.key !in badRenditions }
        if (complete.isEmpty()) return null

        val expected = durationSeconds.takeIf { it.isFinite() && it > 0 }
        // Without a length to check a sibling against, sharing would be a guess,
        // so only the rendition actually being played can be trusted.
        if (expected == null) {
            val own = cache.cacheKeyOf(uri)
            complete.firstOrNull { it.key == own }?.let { return it }
            // With no duration to compare, a single cached copy is still usable.
            // Multiple copies remain ambiguous and are rejected.
            return complete.singleOrNull()?.also {
                Log.d(
                    TAG,
                    "Analysing $trackId from its only cached rendition ${it.key}; " +
                        "no duration to check it against",
                )
            }
        }

        for (candidate in complete) {
            val length = cache.renditionDataSource(uri, candidate)
                .use(AudioDecoder::containerDurationSeconds) ?: continue
            if (length <= 0) continue
            if (abs(length - expected) > RENDITION_DURATION_TOLERANCE) {
                Log.d(
                    TAG,
                    "Rendition ${candidate.key} rejected for $trackId: " +
                        "${"%.1f".format(length)}s against ${"%.1f".format(expected)}s expected",
                )
                continue
            }
            if (candidate.key != cache.cacheKeyOf(uri)) {
                Log.d(
                    TAG,
                    "Analysing $trackId from lighter rendition ${candidate.key} " +
                        "(${candidate.contentLength / 1024}kB)",
                )
            }
            return candidate
        }
        return null
    }

    /** Separates a short decode (a failed rendition) from a missing rendition. */
    private class WholeTrack(val analysis: TrackAnalysis?, val decodedShort: Boolean = false)

    /** Keeps short-decode failures distinct from missing audio during Pass 1. */
    private class Structural(
        val features: TrackFeatures.Features?,
        val decodedShort: Boolean = false,
    )

    private fun structure(
        trackId: String,
        uri: Uri,
        rendition: AudioCache.Rendition,
        openSource: () -> MediaDataSource?,
        effectiveDuration: Double,
    ): Structural {
        checkWorkCurrent()
        val structRate = TrackFeatures.sampleRate
        val decoded = openSource()?.use { AudioDecoder.decodeRegion(it, 0.0, effectiveDuration, ::isWorkCurrent) }
            ?: return Structural(null)
        checkWorkCurrent()
        val (pcm, _) = decoded

        // A short decode can look like trailing silence and place the mix-out point too early.
        val decodedSeconds = if (pcm.sampleRate > 0) pcm.samples.size / pcm.sampleRate else 0.0
        if (decodedSeconds < effectiveDuration * MIN_DECODED_FRACTION) {
            Log.w(
                TAG,
                "Analysis of $trackId refused: rendition ${rendition.key} decoded " +
                    "${"%.1f".format(decodedSeconds)}s of a ${"%.1f".format(effectiveDuration)}s " +
                    "container — cached with holes?",
            )
            // Skip this rendition on retry so another cached copy can be analyzed.
            badRenditions.add(rendition.key)
            // Remove a corrupt completed cache entry so it can be downloaded again.
            if (!isDeviceFile(uri) && rendition.isComplete && discardsOf(rendition.key) < MAX_RENDITION_DISCARDS &&
                cache.discardBadRendition(uri, rendition.key)
            ) {
                // Clear state tied to the deleted bytes so the replacement can be tried.
                discarded.merge(rendition.key, 1, Int::plus)
                badRenditions.remove(rendition.key)
                triedRenditions.remove(rendition.key)
                shortDecodes.remove(trackId)
            }
            return Structural(null, decodedShort = true)
        }

        val samples = if (abs(pcm.sampleRate - structRate) > 1.0) {
            TrackFeatures.resample(pcm.samples, pcm.sampleRate, structRate) ?: return Structural(null)
        } else {
            pcm.samples
        }

        return Structural(TrackFeatures.analyze(samples, effectiveDuration))
    }

    /** A song file on the device is read in place; there is no cache copy to wait for. */
    private fun isDeviceFile(uri: Uri): Boolean = uri.scheme == "content" || uri.scheme == "file"

    private fun deviceRendition(trackId: String) = AudioCache.Rendition(key = "device:$trackId", isComplete = true, cachedPrefix = 0L)

    private fun analyze(trackId: String, uri: Uri, durationSeconds: Double): WholeTrack {
        checkWorkCurrent()
        val local = isDeviceFile(uri)
        val rendition = (if (local) deviceRendition(trackId) else chooseRendition(trackId, uri, durationSeconds))
            ?: return WholeTrack(null)
        // Record before decoding so a failed rendition is not retried on every tick.
        triedRenditions.add(rendition.key)
        fun openSource(): MediaDataSource? =
            if (local) UriMediaDataSource.open(appContext, uri) else cache.renditionDataSource(uri, rendition)

        var effectiveDuration = durationSeconds
        if (!effectiveDuration.isFinite() || effectiveDuration <= 0) {
            effectiveDuration = openSource()?.use(AudioDecoder::containerDurationSeconds) ?: 0.0
        }
        if (effectiveDuration <= 0) {
            Log.d(TAG, "Skipping $trackId: cached media has no duration")
            return WholeTrack(empty(trackId, 0.0))
        }

        // Release structural decode buffers before allocating the model buffers.
        val structural = structure(trackId, uri, rendition, ::openSource, effectiveDuration)
        checkWorkCurrent()
        if (structural.decodedShort) return WholeTrack(null, decodedShort = true)
        val features = structural.features ?: return WholeTrack(empty(trackId, effectiveDuration))

        // Transitions use only the outgoing tail and incoming head, so analyze those windows only.
        // Each window is decoded once and shared by beat and vocal analysis.
        val window = BeatTracker.WINDOW_SECONDS
        val tailStart = max(0.0, effectiveDuration - window)
        val head = region(::openSource, 0.0, minOf(window, effectiveDuration), features)
        val tail = if (tailStart > window / 2) region(::openSource, tailStart, effectiveDuration, features) else null

        val headGrid = head?.grid
        val tailGrid = tail?.grid

        // The tail governs where the outgoing track is mixed out, so it takes precedence; the
        // head is what a track uses when it is the *incoming* side of a different transition.
        val leading = tailGrid ?: headGrid

        Log.d(
            TAG,
            "Analysed $trackId: bpm=${leading?.bpm ?: features.bpm} " +
                "conf=${leading?.beatConfidence ?: features.beatConfidence} " +
                "key=${features.key} contentEnd=${features.contentEndTime} " +
                "mixOutCandidates=${features.mixOutCandidates.size} " +
                "vocalMask=${if (head?.vocalMask != null || tail?.vocalMask != null) "model" else "dsp"}",
        )

        return WholeTrack(
                TrackAnalysis(
                    status = TrackAnalysis.STATUS_READY,
                    trackId = trackId,
                    duration = effectiveDuration,
                    contentEndTime = features.contentEndTime.takeIf { it > 0 } ?: effectiveDuration,
                    bpm = leading?.bpm ?: features.bpm,
                beatInterval = leading?.beatInterval ?: features.beatInterval,
                beatConfidence = leading?.beatConfidence ?: features.beatConfidence,
                downbeats = (headGrid?.downbeats.orEmpty() + tailGrid?.downbeats.orEmpty())
                    .ifEmpty { features.downbeats }
                    .sorted(),
                firstBeat = headGrid?.firstBeat ?: features.firstBeat,
                phraseBoundaries = features.phraseBoundaries,
                key = features.key,
                keyConfidence = features.keyConfidence,
                audibleStartTime = features.audibleStartTime,
                pickupTime = features.pickupTime,
                introEndTime = features.introEndTime,
                outroStartTime = features.outroStartTime,
                mixInTime = features.mixInTime,
                mixOutTime = features.mixOutTime,
                mixInCandidates = features.mixInCandidates,
                mixOutCandidates = features.mixOutCandidates,
                energyCurve = features.energyCurve,
                lowEnergyCurve = features.lowEnergyCurve,
                // Keep DSP estimates wherever the model has no measurements.
                vocalActivityMask = StemVocalMask.forTrack(appContext, trackId, features.energyCurve)
                    ?: mergeMasks(features.energyCurve.size, head?.vocalMask, tail?.vocalMask)
                    ?: features.vocalActivityMask,
                vocalProbability = features.vocalProbability,
            ),
        )
    }

    /**
     * Everything a decoded region contributes, once its audio has been let go
     * of. [seconds] is what was actually decoded, which for a partially cached
     * file is not what was asked for.
     */
    private class Region(
        val grid: BeatTracker.Grid?,
        val vocalMask: DoubleArray?,
        val seconds: Double,
        /** Only populated when the caller asked for it; see [region]'s `deriveFeatures`. */
        val features: TrackFeatures.Features? = null,
    )

    /** Decodes one window; null means decoding failed or returned under one second. */
    private fun region(
        openSource: () -> MediaDataSource?,
        startSeconds: Double,
        endSeconds: Double,
        features: TrackFeatures.Features?,
        deriveFeatures: Boolean = false,
    ): Region? {
        checkWorkCurrent()
        val decoded = openSource()?.use { AudioDecoder.decodeRegionStereo(it, startSeconds, endSeconds, ::isWorkCurrent) }
            ?: run {
                Log.d(TAG, "Region [$startSeconds, $endSeconds) would not open")
                return null
            }
        checkWorkCurrent()
        val (stereo, actualStart) = decoded
        if (stereo.left.size < stereo.sampleRate) {
            Log.d(TAG, "Region [$startSeconds, $endSeconds) decoded ${stereo.left.size} frames; too few")
            return null
        }

        val seconds = stereo.left.size / stereo.sampleRate
        // Keep the large mono downmix inside this helper so it can be collected before inference.
        val inputs = regionInputs(stereo, seconds, deriveFeatures)
        checkWorkCurrent()

        return Region(
            // The decoder may seek back to a sync sample; beat times use the actual start.
            grid = inputs.modelInput?.let { tracker.track(it, offsetSeconds = actualStart) },
            vocalMask = features?.let { vocalMask(stereo, it, actualStart) },
            seconds = seconds,
            features = inputs.features,
        )
    }

    private class RegionInputs(val modelInput: FloatArray?, val features: TrackFeatures.Features?)

    /** Builds the model input and optional DSP features from one mono downmix. */
    private fun regionInputs(
        stereo: AudioDecoder.StereoPcm,
        seconds: Double,
        deriveFeatures: Boolean,
    ): RegionInputs {
        val mono = FloatArray(stereo.left.size) { index -> (stereo.left[index] + stereo.right[index]) * 0.5f }
        val modelInput = if (abs(stereo.sampleRate - MelSpectrogram.sampleRate) > 1.0) {
            MelSpectrogram.resample(mono, stereo.sampleRate, MelSpectrogram.sampleRate)
        } else {
            mono
        }

        val features = if (deriveFeatures) {
            val forFeatures = if (abs(stereo.sampleRate - TrackFeatures.sampleRate) > 1.0) {
                TrackFeatures.resample(mono, stereo.sampleRate, TrackFeatures.sampleRate)
            } else {
                mono
            }
            forFeatures?.let { TrackFeatures.analyze(it, seconds) }
        } else {
            null
        }

        return RegionInputs(modelInput, features)
    }

    /** Builds an energy-curve-sized mask; unmeasured points stay below the vocal activity threshold. */
    private fun vocalMask(
        stereo: AudioDecoder.StereoPcm,
        features: TrackFeatures.Features,
        actualStart: Double,
    ): DoubleArray? {
        val curve = features.energyCurve
        if (curve.isEmpty() || !VocalSpectrogram.available) return null

        // The vocal model has a fixed input size; leave two frames for resampling rounding.
        val maxSeconds = (VocalTracker.FIXED_FRAMES - 2) * VocalSpectrogram.hop / VocalSpectrogram.sampleRate
        val maxSamples = (maxSeconds * stereo.sampleRate).toInt().coerceAtMost(stereo.left.size)
        if (maxSamples <= 0) return null
        val left = if (maxSamples < stereo.left.size) stereo.left.copyOf(maxSamples) else stereo.left
        val right = if (maxSamples < stereo.right.size) stereo.right.copyOf(maxSamples) else stereo.right

        checkWorkCurrent()
        val values = vocals.track(left, right, stereo.sampleRate) ?: return null

        val mask = DoubleArray(curve.size) { NEUTRAL_VOCAL }
        for (index in curve.indices) {
            val frame = ((curve[index].time - actualStart) * VocalSpectrogram.frameRate).toInt()
            if (frame in values.indices) mask[index] = values[frame].toDouble()
        }
        return mask
    }

    /**
     * Overlays the head and tail masks onto one full-length curve, or null when neither ran —
     * which the caller answers by keeping the DSP heuristic rather than reporting a mask of
     * nothing but [NEUTRAL_VOCAL].
     */
    private fun mergeMasks(size: Int, head: DoubleArray?, tail: DoubleArray?): List<Double>? {
        if (size <= 0 || (head == null && tail == null)) return null
        val merged = DoubleArray(size) { NEUTRAL_VOCAL }
        for (source in listOfNotNull(head, tail)) {
            for (index in merged.indices) {
                if (index < source.size && source[index] != NEUTRAL_VOCAL) merged[index] = source[index]
            }
        }
        return merged.toList()
    }

    /** Recorded ready-but-empty so a track that cannot be decoded is not retried every tick. */
    private fun empty(trackId: String, durationSeconds: Double) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        trackId = trackId,
        duration = durationSeconds,
    )

    fun release() {
        synchronized(lifecycleLock) {
            if (released) return
            released = true
            analysisRevision.incrementAndGet()
            headExecutor.queue.clear()
            refineExecutor.queue.clear()
            running.clear()
            retryAfter.clear()
            headExecutor.execute {
                releaseModels()
                results.clear()
                headAttempts.clear()
                headSkipLogged.clear()
                provisional.clear()
                restoreAttempted.clear()
                shortDecodes.clear()
                badRenditions.clear()
                triedRenditions.clear()
                discarded.clear()
            }
            headExecutor.shutdown()
            refineExecutor.shutdown()
        }
    }

    private companion object {
        const val TAG = "ExhaleTrackAnalyzer"
        const val REQUEST_RETRY_MS = 1000L

        /**
         * What an unmeasured instant reads as. Below the policy's VOCAL_ACTIVE_THRESHOLD by
         * design, so absence of measurement is never mistaken for absence of a vocal, or for the
         * presence of one.
         */
        const val NEUTRAL_VOCAL = 0.5

        /** Extra bytes beyond average bitrate to cover container headers and higher opening bitrates. */
        const val HEAD_BYTES_MARGIN = 1.35

        /** Minimum prefix required for a decodable opening; also used when duration is unknown. */
        const val MIN_HEAD_BYTES = 256L * 1024L

        /**
         * How much of the container's stated duration must actually decode
         * before the whole-track pass is trusted. Not 1.0: a decoder legitimately
         * comes up a frame or two short of the container's rounding, and
         * refusing over that would refuse everything.
         */
        const val MIN_DECODED_FRACTION = 0.95

        /** Refusals before a track is written off as truncated rather than still filling in. */
        const val MAX_SHORT_DECODE_ATTEMPTS = 3

        /** A bad cached rendition is discarded once before being rejected as a source issue. */
        const val MAX_RENDITION_DISCARDS = 1

        /**
         * How far two renditions' container durations may differ and still count
         * as the same cut. Generous enough for codec padding and the player's own
         * rounding, tight enough that a different edit of the same song — where a
         * borrowed beat grid would be useless — is rejected.
         */
        const val RENDITION_DURATION_TOLERANCE = 1.0

        /**
         * Stand-in bitrate for a rendition whose real length isn't recorded yet,
         * used only to rank copies against each other in [headSecondsOf]. About
         * 160kbps, the middle of what the streams in play here run at.
         */
        const val ASSUMED_BYTES_PER_SECOND = 20_000.0

        /**
         * The least decoded audio a head-only tempo estimate is allowed to rest
         * on. Twelve seconds is around 24 beats at 120 bpm — enough for the
         * grid's own confidence measure to mean something.
         */
        const val MIN_HEAD_SECONDS = 12.0

        /**
         * How much more of the file has to be cached before the head is worth
         * decoding again. Doubling bounds the attempts to a handful over a whole
         * download while still catching up quickly on a high-bitrate rendition
         * whose first attempt covered only a few seconds.
         */
        const val HEAD_RETRY_GROWTH = 2
    }
}
