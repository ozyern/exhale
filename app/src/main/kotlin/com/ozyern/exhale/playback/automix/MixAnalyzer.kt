/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Automix's analysis, after BitChord's TrackAnalyzer (github.com/kushagrasinghx/BitChord,
 * GPL-3.0), which is itself modeled on Orchard's (github.com/SFG5453/Orchard): the same
 * measurements, fed from Exhale's own player caches.
 */

package com.ozyern.exhale.playback.automix

import android.content.Context
import android.media.MediaDataSource
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

/** How hard Automix works in the background — the three settings, meant for Exhale. */
enum class AutomixPerformance(
    val label: String,
    val detail: String,
    /** Seconds a song has to play before it is measured, so a skipped song costs nothing. */
    val settleSeconds: Int,
    /** How many songs past the playing one are measured ahead of time. */
    val lookAhead: Int,
    val threadPriority: Int,
) {
    EFFICIENT(
        "Efficient",
        "Waits until a song has played a little, and measures at the lowest priority. Least battery and heat",
        settleSeconds = 12,
        lookAhead = 1,
        threadPriority = Process.THREAD_PRIORITY_LOWEST,
    ),
    BALANCED(
        "Balanced",
        "Measures the playing song and the next one in the background. Recommended",
        settleSeconds = 10,
        lookAhead = 1,
        threadPriority = Process.THREAD_PRIORITY_LOWEST,
    ),
    PERFORMANCE(
        "Performance",
        "Measures straight away and two songs ahead, so every transition is ready. Most battery and heat",
        settleSeconds = 0,
        lookAhead = 2,
        threadPriority = Process.THREAD_PRIORITY_DEFAULT,
    ),
}

/**
 * Measures songs for Automix: tempo, beat grid, key, where the music really starts and ends, the
 * energy curve and where a transition could enter and leave — everything [planTransition] reads.
 *
 * [analysisFor] is asked every tick of the transition loop, so it never blocks: a song not yet
 * measured reads as no evidence, which the planner answers with a plain crossfade. Work happens
 * on one background thread, a song at a time, and anything no longer near the playhead is
 * dropped before it starts and abandoned if it has.
 *
 * Measured songs are kept on disk (see [AnalysisStore]), so a song is measured once and stays
 * measured.
 */
class MixAnalyzer(
    context: Context,
    /** Opens [id]'s audio for reading, or null when it can't be read right now. */
    private val openSource: (id: String, abort: () -> Boolean) -> MediaDataSource?,
) {
    private val store = AnalysisStore(context)
    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val restored = ConcurrentHashMap.newKeySet<String>()
    private val failures = ConcurrentHashMap<String, Failure>()

    /** The songs worth measuring right now — the playing one and the next few. */
    @Volatile
    private var wanted: Set<String> = emptySet()

    @Volatile
    var performance: AutomixPerformance = AutomixPerformance.BALANCED

    private class Failure(val count: Int, val at: Long)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "exhale-automix-analysis").apply { isDaemon = true }
    }

    /** What is known about [id] right now. Never computes, never blocks. */
    fun analysisFor(id: String): TrackAnalysis = results[id] ?: TrackAnalysis(trackId = id)

    fun isAnalysing(id: String): Boolean = id in running

    /** Whether [id] has a measurement the planner can use. */
    fun isMeasured(id: String): Boolean = results[id]?.isUsable == true

    /** Replaces the set of songs worth measuring; work for anything else is dropped. */
    fun focusOn(ids: Set<String>) {
        wanted = ids
    }

    /**
     * Asks for [id] to be measured if it hasn't been. Cheap enough to call on every tick:
     * a song already measured, queued, or given up on for now returns straight away.
     */
    fun request(id: String, durationMs: Long) {
        if (id.isBlank() || results.containsKey(id)) return
        restore(id)
        failures[id]?.let { failure ->
            if (failure.count >= MAX_ATTEMPTS) return
            if (System.currentTimeMillis() - failure.at < RETRY_AFTER_MS) return
        }
        if (!queued.add(id)) return
        executor.execute {
            try {
                if (results.containsKey(id) || id !in wanted) return@execute
                running.add(id)
                Process.setThreadPriority(performance.threadPriority)
                val analysis = measure(id, durationMs)
                if (analysis != null) {
                    results[id] = analysis
                    failures.remove(id)
                    store.save(id, analysis)
                    Log.d(
                        TAG,
                        "Measured $id: bpm=${analysis.bpm} conf=${analysis.beatConfidence} key=${analysis.key} " +
                            "contentEnd=${analysis.contentEndTime} mixOut=${analysis.mixOutCandidates.size}",
                    )
                } else if (id in wanted) {
                    val previous = failures[id]?.count ?: 0
                    failures[id] = Failure(previous + 1, System.currentTimeMillis())
                }
            } catch (error: Throwable) {
                // Throwable: a codec error or an out-of-memory here must cost one song its
                // measurement, never the app.
                Log.w(TAG, "Measuring $id failed", error)
                val previous = failures[id]?.count ?: 0
                failures[id] = Failure(previous + 1, System.currentTimeMillis())
            } finally {
                running.remove(id)
                queued.remove(id)
            }
        }
    }

    /** Looks [id] up on disk, once, off the calling thread. */
    private fun restore(id: String) {
        if (!restored.add(id)) return
        executor.execute {
            if (results.containsKey(id)) return@execute
            store.load(id)?.let { results.putIfAbsent(id, it) }
        }
    }

    private fun measure(id: String, durationMs: Long): TrackAnalysis? {
        if (!TrackFeatures.available) return null
        val abort = { id !in wanted }
        val rate = TrackFeatures.sampleRate
        val expected = durationMs / 1000.0
        val decoded = openSource(id, abort)?.use { source ->
            val end = if (expected > 0) expected + 1.0 else MAX_TRACK_SECONDS
            AudioDecoder.decodeRegionAt(
                source = source,
                startSeconds = 0.0,
                endSeconds = end,
                targetRate = rate,
                resample = { samples, from, to -> TrackFeatures.resample(samples, from, to) },
                abort = abort,
                // Background measurement of whole songs: paced so it never starves playback.
                // Lyric alignment (VersionAligner) is in the way of the words appearing, so it
                // decodes at full speed.
                paced = true,
            )
        } ?: return null
        val pcm = decoded.first
        val seconds = pcm.samples.size / pcm.sampleRate
        // A decode that stopped short measured some other, shorter song: an outro placed minutes
        // early is worse than no outro at all.
        if (expected > 0 && seconds < expected * MIN_DECODED_FRACTION) {
            Log.w(TAG, "Measuring $id refused: decoded ${"%.1f".format(seconds)}s of ${"%.1f".format(expected)}s")
            return null
        }
        val duration = if (expected > 0 && abs(seconds - expected) < 2.0) expected else seconds
        val features = TrackFeatures.analyze(pcm.samples, duration) ?: return null
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = id,
            duration = duration,
            contentEndTime = features.contentEndTime.takeIf { it > 0 } ?: duration,
            bpm = features.bpm,
            beatInterval = features.beatInterval,
            beatConfidence = features.beatConfidence,
            downbeats = features.downbeats.sorted(),
            firstBeat = features.firstBeat,
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
            vocalActivityMask = features.vocalActivityMask,
            vocalProbability = features.vocalProbability,
        )
    }

    fun release() {
        wanted = emptySet()
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "ExhaleAutomix"
        const val MAX_ATTEMPTS = 3
        const val RETRY_AFTER_MS = 30_000L
        const val MIN_DECODED_FRACTION = 0.95
        const val MAX_TRACK_SECONDS = 20.0 * 60.0
    }
}
