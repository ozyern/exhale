/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Crossfade improvements ported from ArchiveTune (github.com/koiverse):
 *  - Equal-power volume curve (sin/cos) — elimina el "dip" perceptual en el punto medio
 *  - Gapless album skip — no hace crossfade entre pistas del mismo álbum
 *  - Buffer check antes de iniciar — evita arranque con rebuffering
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.Timeline
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.playback.automix.AutomixPerformance
import com.ozyern.exhale.playback.automix.CrossfadeMode
import com.ozyern.exhale.playback.automix.MixAnalyzer
import com.ozyern.exhale.playback.automix.TransitionFilterProcessor
import com.ozyern.exhale.playback.automix.TransitionFilters
import com.ozyern.exhale.playback.automix.TransitionPlan
import com.ozyern.exhale.playback.automix.TransitionStyle
import com.ozyern.exhale.playback.automix.TransitionTrackInfo
import com.ozyern.exhale.playback.automix.planTransition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

internal class CrossfadeAudio(
    private val player: ExoPlayer,
    private val database: MusicDatabase,
    private val crossfadeDurationMs: MutableStateFlow<Int>,
    private val playbackFadeFactor: MutableStateFlow<Float>,
    private val playerVolume: MutableStateFlow<Float>,
    private val audioFocusVolumeFactor: MutableStateFlow<Float>,
    private val audioNormalizationEnabled: MutableStateFlow<Boolean>,
    private val maxSafeGainFactor: Float,
    private val overlapPlayerFactory: () -> ExoPlayer,
    private val onCrossfadeStart: (MediaItem) -> Unit = {},
    /** Automix: transitions timed and blended from the songs themselves. See [automixTick]. */
    private val automixEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val analyzer: MixAnalyzer? = null,
    /** Filters on the main player (the song leaving) and the overlap player (the song arriving). */
    private val filters: TransitionFilters = TransitionFilters.None,
    /** False while Automix mustn't run, such as in a listen-together session. */
    private val automixAllowed: () -> Boolean = { true },
) {
    // ── Estado del loop ───────────────────────────────────────────────────────

    private var loopJob: Job? = null

    // ── Estado del overlap player ─────────────────────────────────────────────

    private var overlapPlayer: ExoPlayer? = null
    private var overlapPrimedIndex: Int = C.INDEX_UNSET
    private var overlapPrimedMediaId: String? = null
    private var crossfadeActive = false
    private var crossfadeTargetIndex: Int = C.INDEX_UNSET
    private var crossfadeTargetMediaId: String? = null
    private var crossfadeStartElapsedMs: Long = 0L
    private var crossfadeActiveDurationMs: Int = 0
    private var overlapNormalizeFactor: Float = 1f

    // ── Estado del handoff ────────────────────────────────────────────────────

    private var handoffActive = false
    private var handoffStartElapsedMs: Long = 0L
    private var handoffDurationMs: Int = 0
    private var handoffTargetPositionMs: Long = 0L
    private var handoffLastSyncSeekElapsedMs: Long = 0L
    private var handoffSeekIssued = false
    private var handoffRampStarted = false

    // Constantes de handoff
    private val handoffReseekMinIntervalMs = 180L
    private val handoffDriftCorrectionThresholdMs = 220L
    private val handoffRampStartDriftToleranceMs = 120L
    private val handoffTimeoutMs = 5000L

    // ── Buffer mínimo antes de arrancar el crossfade ──────────────────────────

    /** Cuánto buffer (ms) necesita el overlap player antes de permitir beginOverlapCrossfade.
     *  Se calcula como fadeMs + 2s, acotado entre 3s y 10s. */
    private fun requiredStartBufferMs(fadeMs: Int): Long =
        (fadeMs.toLong() + 2_000L).coerceIn(3_000L, 10_000L)

    // ── API pública ───────────────────────────────────────────────────────────

    fun isCrossfading(): Boolean = crossfadeActive

    /**
     * True when a move of the main player onto [mediaItem] is the crossfade's own ending — the
     * main player catching up with the song already fading in — rather than a skip. MusicService
     * treats such a move as the song simply playing on.
     */
    fun adoptsTransitionTo(mediaItem: MediaItem?): Boolean =
        crossfadeActive && !handoffActive && !crossfadeTargetMediaId.isNullOrBlank() &&
            mediaItem?.mediaId == crossfadeTargetMediaId

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        handleMediaItemTransition(mediaItem, reason)
    }

    fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            stop(resetMainFade = true)
        }
    }

    fun stop(resetMainFade: Boolean) {
        stopOverlapCrossfade(resetMainFade = resetMainFade)
    }

    fun release() {
        analyzer?.release()
        loopJob?.cancel()
        loopJob = null
        stopOverlapCrossfade(resetMainFade = true)
        runCatching { overlapPlayer?.release() }
        overlapPlayer = null
    }

    // ── Loop principal ────────────────────────────────────────────────────────

    private suspend fun runLoop() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            if (automixEnabled.value && analyzer != null && automixAllowed()) {
                delay(automixTick())
                continue
            } else if (mix != null) {
                abandonMix()
            }

            val fadeMs = crossfadeDurationMs.value

            if (fadeMs <= 0) {
                stopOverlapCrossfade(resetMainFade = true)
                delay(250)
                continue
            }

            if (!player.playWhenReady) {
                stopOverlapCrossfade(resetMainFade = true)
                delay(150)
                continue
            }

            // Durante el handoff solo actualizar volúmenes
            if (handoffActive) {
                updateVolumes()
                delay(50)
                continue
            }

            if (!crossfadeActive && (player.playbackState != Player.STATE_READY || !player.isPlaying)) {
                stopOverlapCrossfade(resetMainFade = true)
                delay(150)
                continue
            }

            val durationMs = player.duration
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val nextIndex = player.nextMediaItemIndex

            // No crossfade en repeat-one
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                stopOverlapCrossfade(resetMainFade = true)
                delay(150)
                continue
            }

            if (!crossfadeActive && (nextIndex == C.INDEX_UNSET || durationMs <= 0 || durationMs == C.TIME_UNSET)) {
                stopOverlapCrossfade(resetMainFade = true)
                delay(150)
                continue
            }

            // ── Skip gapless para álbumes ─────────────────────────────────────
            // Si la transición es entre pistas del mismo álbum y el crossfade no está
            // activo todavía, lo omitimos para respetar el gapless del álbum.
            if (!crossfadeActive && nextIndex != C.INDEX_UNSET) {
                val currentItem =
                    runCatching { player.getMediaItemAt(player.currentMediaItemIndex) }.getOrNull()
                val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull()
                if (currentItem != null && nextItem != null && isGaplessAlbumTransition(
                        currentItem,
                        nextItem
                    )
                ) {
                    unprimeOverlap()
                    delay(150)
                    continue
                }
            }

            if (crossfadeActive) {
                val targetId = crossfadeTargetMediaId
                val currentId = player.currentMediaItem?.mediaId
                val onTarget = !targetId.isNullOrBlank() && targetId == currentId

                if (!onTarget && (nextIndex == C.INDEX_UNSET || durationMs <= 0 || durationMs == C.TIME_UNSET)) {
                    stopOverlapCrossfade(resetMainFade = true)
                    delay(150)
                    continue
                }

                val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
                val tooFarFromEnd = !onTarget && remainingMs > fadeMs.toLong() + 2000L
                val nextChanged =
                    !onTarget && crossfadeTargetIndex != C.INDEX_UNSET && nextIndex != crossfadeTargetIndex
                if (tooFarFromEnd || nextChanged) {
                    stopOverlapCrossfade(resetMainFade = true)
                    delay(100)
                    continue
                }

                updateVolumes()
                delay(50)
                continue
            }

            val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
            val preloadWindowMs = fadeMs.toLong() + 1200L

            if (remainingMs in 1L..preloadWindowMs) {
                primeOverlapForNext(nextIndex)
            } else {
                unprimeOverlap()
            }

            // ── Iniciar crossfade cuando quede tiempo suficiente ──────────────
            if (overlapPrimedIndex == nextIndex && remainingMs in 1L..fadeMs.toLong()) {
                // Verificar buffer mínimo ANTES de comenzar — evita arrancar con rebuffering
                val overlap = overlapPlayer
                if (overlap != null && hasEnoughBuffer(overlap, requiredStartBufferMs(fadeMs))) {
                    beginOverlapCrossfade(fadeMs = fadeMs, remainingMs = remainingMs)
                }
                // Si no hay buffer suficiente, el próximo tick lo intentará de nuevo
                delay(50)
                continue
            }

            if (playbackFadeFactor.value != 1f) playbackFadeFactor.value = 1f
            delay(100)
        }
    }

    // ── Automix ───────────────────────────────────────────────────────────────
    //
    // The Automix transition engine, on Exhale's two players. The plan comes
    // from [planTransition] (adapted from GPL-3.0 sources, see its header): where in the leaving song to
    // start, how long to overlap, where in the arriving song to come in and at what tempo, and how
    // to blend — an equal-power fade, a filter sweep, or a bass swap between beat-matched songs.
    //
    // Arm: a few seconds before the start, the overlap player loads the arriving song paused at
    // its cue. Fade: at the start it plays, and the two ride the plan's gains and filters, with
    // the arriving song held on the beat against the leaving one. End: the main player jumps to
    // the arriving song where the overlap has got to, and the usual handoff takes it from there —
    // whatever of the leaving song the plan chose to leave out is simply never heard.

    /** One transition in flight, frozen when it is armed. */
    private class Mix(
        val targetIndex: Int,
        val targetId: String,
        val item: MediaItem,
        val startMs: Long,
        val endMs: Long,
        val fadeMs: Long,
        val cueMs: Long,
        val rate: Double,
        val plan: TransitionPlan,
    ) {
        var playing = false
        var announced = false
        var lockedCorrection = 0.0
    }

    private var mix: Mix? = null
    private var mixSeekPending = false
    private var lastPlanKey: String? = null
    private var lastPlan: TransitionPlan? = null
    private var lastPlanAt = 0L

    /** Songs the transition could not use this time round, so it isn't re-armed on every tick. */
    private var mixDeclinedFor: String? = null

    /** One Automix tick; returns how long to wait before the next. */
    private suspend fun automixTick(): Long {
        val analyzer = analyzer ?: return 250

        if (handoffActive) {
            updateVolumes()
            return 50
        }

        val active = mix
        if (active != null && active.playing) {
            if (!player.playWhenReady) {
                // Paused mid-transition: both hold where they are, and pick up together.
                overlapPlayer?.playWhenReady = false
                return 120
            }
            overlapPlayer?.let { if (!it.playWhenReady) it.playWhenReady = true }
            driveMix(active)
            return MIX_STEP_MS
        }

        if (!player.playWhenReady || player.playbackState != Player.STATE_READY) {
            if (active != null) abandonMix()
            return 150
        }

        val current = player.currentMediaItem ?: return 250
        val durationMs = player.duration
        val nextIndex = player.nextMediaItemIndex
        focusAnalysis(current, nextIndex)

        if (player.repeatMode == Player.REPEAT_MODE_ONE || nextIndex == C.INDEX_UNSET ||
            durationMs <= 0L || durationMs == C.TIME_UNSET
        ) {
            if (active != null) abandonMix()
            return 250
        }

        val next = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull() ?: return 250
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val transitionKey = "${current.mediaId}>${next.mediaId}"

        if (active != null) {
            // Armed. The queue moved under it, or someone scrubbed away: start again from nothing.
            if (active.targetId != next.mediaId || positionMs < active.startMs - ARM_LEAD_MS - 1_500L) {
                abandonMix()
                return 100
            }
            return driveArmed(active, positionMs)
        }

        if (mixDeclinedFor == transitionKey) return 250

        val plan = planFor(current, next, nextIndex, durationMs, positionMs, transitionKey)
        if (plan == null || plan.blocked || plan.fadeMs <= 0L || plan.transitionStyle == TransitionStyle.GAPLESS) {
            if (playbackFadeFactor.value != 1f) playbackFadeFactor.value = 1f
            return 250
        }

        val startMs = (plan.transitionStart * 1000).roundToLong()
        val endMs = (plan.transitionEnd * 1000).roundToLong().coerceAtMost(durationMs)
        val untilStart = startMs - positionMs
        if (untilStart > ARM_LEAD_MS) return if (untilStart > 20_000L) 500 else 200
        // Too late to be worth it — the leaving song is nearly at the plan's end already.
        if (positionMs >= endMs - MIN_LATE_FADE_MS) {
            mixDeclinedFor = transitionKey
            return 250
        }

        armMix(nextIndex, next, plan, startMs, endMs, positionMs)
        return MIX_STEP_MS
    }

    /** Keeps the analyzer on the playing song and the next few, and asks for any not yet measured. */
    private fun focusAnalysis(current: MediaItem, nextIndex: Int) {
        val analyzer = analyzer ?: return
        val performance = analyzer.performance
        val ahead = buildList {
            var index = nextIndex
            repeat(performance.lookAhead) {
                if (index == C.INDEX_UNSET) return@repeat
                runCatching { player.getMediaItemAt(index) }.getOrNull()?.let { add(index to it) }
                index = player.currentTimeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
            }
        }
        analyzer.focusOn(setOf(current.mediaId) + ahead.map { it.second.mediaId })
        val playedMs = player.currentPosition
        if (playedMs < performance.settleSeconds * 1000L) return
        // Not while the player is still filling its own buffer. The measurement reads the whole
        // song through the same cache and the same connection, and started three seconds in it
        // starved a 1.5s playback buffer: the song stopped to buffer about six seconds in. Once the
        // player is well ahead — the whole song in, or half a minute past the playhead — the
        // measurement's reads are mostly cache hits and cost playback nothing.
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val buffered = player.bufferedPosition
        val wellAhead = (duration > 0 && buffered >= duration - 1_000L) || buffered - playedMs >= ANALYSIS_BUFFER_AHEAD_MS
        if (!wellAhead || player.playbackState == Player.STATE_BUFFERING) return
        analyzer.request(current.mediaId, duration)
        // The next songs only once this one is measured: it's the half of the transition that
        // comes first.
        if (!analyzer.isMeasured(current.mediaId) && analyzer.isAnalysing(current.mediaId)) return
        ahead.forEach { (index, item) -> analyzer.request(item.mediaId, durationOf(index, item)) }
    }

    private fun durationOf(index: Int, item: MediaItem): Long {
        val timeline = player.currentTimeline
        if (!timeline.isEmpty && index in 0 until timeline.windowCount) {
            timeline.getWindow(index, Timeline.Window()).durationMs
                .takeIf { it != C.TIME_UNSET && it > 0 }
                ?.let { return it }
        }
        return (item.metadata?.duration ?: 0).coerceAtLeast(0).toLong() * 1000L
    }

    private fun planFor(
        current: MediaItem,
        next: MediaItem,
        nextIndex: Int,
        durationMs: Long,
        positionMs: Long,
        transitionKey: String,
    ): TransitionPlan? {
        val analyzer = analyzer ?: return null
        val currentAnalysis = analyzer.analysisFor(current.mediaId)
        val nextAnalysis = analyzer.analysisFor(next.mediaId)
        val key = "$transitionKey|${currentAnalysis.isUsable}|${nextAnalysis.isUsable}|$durationMs"
        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastPlanKey && now - lastPlanAt < REPLAN_MS) return lastPlan
        val fallbackSeconds = crossfadeDurationMs.value.takeIf { it > 0 }?.div(1000.0) ?: DEFAULT_FALLBACK_SECONDS
        val plan = runCatching {
            planTransition(
                analysis = currentAnalysis,
                nextAnalysis = nextAnalysis,
                currentTrack = current.transitionInfo(durationMs),
                nextTrack = next.transitionInfo(durationOf(nextIndex, next)),
                currentTime = positionMs / 1000.0,
                duration = durationMs / 1000.0,
                fadeSeconds = fallbackSeconds,
                mode = CrossfadeMode.SMART,
                // An album played in order stays gapless, as it always has in Exhale.
                albumSequential = true,
            )
        }.onFailure { timber.log.Timber.tag(AUTOMIX_TAG).w(it, "Planning failed") }.getOrNull()
        if (plan != null && plan.reason != lastPlan?.reason) {
            timber.log.Timber.tag(AUTOMIX_TAG).d(
                "plan %s: %s %s fade=%dms cue=%.2f rate=%.4f",
                transitionKey, plan.reason, plan.transitionStyle, plan.fadeMs, plan.incomingCueTime, plan.incomingPlaybackRate,
            )
        }
        lastPlanKey = key
        lastPlan = plan
        lastPlanAt = now
        return plan
    }

    private fun MediaItem.transitionInfo(durationMs: Long): TransitionTrackInfo {
        val meta = metadata
        return TransitionTrackInfo(
            id = mediaId,
            durationMs = durationMs,
            title = meta?.title ?: mediaMetadata.title?.toString().orEmpty(),
            artist = meta?.artists?.joinToString(", ") { it.name } ?: mediaMetadata.artist?.toString().orEmpty(),
            album = meta?.album?.title ?: mediaMetadata.albumTitle?.toString().orEmpty(),
            albumId = meta?.album?.id.orEmpty(),
        )
    }

    private suspend fun armMix(
        nextIndex: Int,
        next: MediaItem,
        plan: TransitionPlan,
        startMs: Long,
        endMs: Long,
        positionMs: Long,
    ) {
        stopOverlapCrossfade(resetMainFade = false)
        val rate = plan.incomingPlaybackRate.takeIf { it.isFinite() && it in 0.85..1.15 } ?: 1.0
        // Armed late — the plan's start already passed — the arriving song comes in later by the
        // same amount, so the two still line up where the plan meant them to.
        val late = (positionMs - startMs).coerceAtLeast(0L)
        val cueMs = ((plan.incomingCueTime * 1000).roundToLong() + (late * rate).roundToLong()).coerceAtLeast(0L)
        val fadeMs = (endMs - startMs).coerceAtLeast(MIN_LATE_FADE_MS)

        val overlap = ensureOverlapPlayer()
        overlap.clearMediaItems()
        overlap.volume = 0f
        overlap.playWhenReady = false
        overlap.setMediaItem(next, cueMs)
        overlap.playbackParameters = player.playbackParameters.withSpeed(player.playbackParameters.speed * rate.toFloat())
        overlap.prepare()
        filters.incoming(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
        overlapNormalizeFactor = fetchNormalizeFactorForMediaId(next.mediaId)
        overlapPrimedIndex = nextIndex
        overlapPrimedMediaId = next.mediaId

        mix = Mix(
            targetIndex = nextIndex,
            targetId = next.mediaId,
            item = next,
            startMs = startMs,
            endMs = endMs,
            fadeMs = fadeMs,
            cueMs = (plan.incomingCueTime * 1000).roundToLong().coerceAtLeast(0L),
            rate = rate,
            plan = plan,
        )
        timber.log.Timber.tag(AUTOMIX_TAG).d(
            "armed %s at %dms: start=%d end=%d cue=%d rate=%.4f style=%s",
            next.mediaId, positionMs, startMs, endMs, cueMs, rate, plan.transitionStyle,
        )
    }

    /** Waits for the plan's start, to the frame, and starts the arriving song on it. */
    private suspend fun driveArmed(active: Mix, positionMs: Long): Long {
        val overlap = overlapPlayer ?: run { abandonMix(); return 150 }
        val untilStart = active.startMs - positionMs
        if (untilStart > MIX_STEP_MS * 2) return (untilStart - MIX_STEP_MS).coerceAtMost(MIX_STEP_MS * 2)
        if (overlap.playbackState != Player.STATE_READY) {
            if (positionMs > active.startMs + ARRIVAL_GRACE_MS) {
                // The arriving song never loaded in time: the songs meet the ordinary way.
                mixDeclinedFor = "${player.currentMediaItem?.mediaId}>${active.targetId}"
                abandonMix()
            }
            return 30
        }
        if (untilStart > 0) delay(untilStart)
        startMix(active)
        return MIX_STEP_MS
    }

    private fun startMix(active: Mix) {
        val overlap = overlapPlayer ?: return abandonMix()
        overlap.volume = 0f
        overlap.playWhenReady = true
        active.playing = true
        AutomixTransition.active.value = true
        crossfadeActive = true
        crossfadeTargetIndex = active.targetIndex
        crossfadeTargetMediaId = active.targetId
        crossfadeStartElapsedMs = android.os.SystemClock.elapsedRealtime()
        crossfadeActiveDurationMs = active.fadeMs.toInt()
    }

    private fun driveMix(active: Mix) {
        val overlap = overlapPlayer ?: return abandonMix()
        if (overlap.playbackState == Player.STATE_IDLE || overlap.playbackState == Player.STATE_ENDED) {
            return abandonMix()
        }
        val incomingPosition = overlap.currentPosition.coerceAtLeast(0L)
        val remainingIncoming = overlap.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?.minus(active.cueMs)?.coerceAtLeast(0L)
        val span = minOf(active.fadeMs, remainingIncoming?.div(3) ?: Long.MAX_VALUE).coerceAtLeast(1L)
        val progress = ((incomingPosition - active.cueMs).toFloat() / span).coerceIn(0f, 1f)

        // The main player leaves through playbackFadeFactor; the overlap arrives at its own volume.
        playbackFadeFactor.value = fallGain(progress)
        val baseOverlapVolume =
            (playerVolume.value * overlapNormalizeFactor * audioFocusVolumeFactor.value).coerceIn(0f, 1f)
        overlap.volume = (baseOverlapVolume * riseGain(progress)).coerceIn(0f, maxSafeGainFactor)
        rideFilters(active.plan, progress)
        holdOnTheBeat(active, overlap)

        if (!active.announced && progress >= 0.5f) {
            // The player shows the arriving song once it is the louder of the two.
            active.announced = true
            onCrossfadeStart(active.item)
        }
        if (progress >= 1f) finishMix(active)
    }

    /**
     * Keeps the arriving song where the plan put it against the leaving one. Both players report
     * where their audio actually is, so any difference is the start's own latency plus drift, and
     * a nudge of a percent or two to the arriving song's tempo — too little to hear — pulls it
     * back onto the beat within a second or so.
     */
    private fun holdOnTheBeat(active: Mix, overlap: ExoPlayer) {
        if (active.plan.transitionStyle != TransitionStyle.DJ_BLEND) return
        val main = player.currentPosition
        if (main < active.startMs || main > active.endMs) return
        val expected = active.cueMs + ((main - active.startMs) * active.rate)
        val drift = overlap.currentPosition - expected
        val correction = if (abs(drift) < BEAT_LOCK_TOLERANCE_MS) 0.0 else (-drift / BEAT_LOCK_RESPONSE_MS).coerceIn(-0.025, 0.025)
        if (abs(correction - active.lockedCorrection) < 0.002) return
        active.lockedCorrection = correction
        val base = player.playbackParameters
        runCatching {
            overlap.playbackParameters = base.withSpeed((base.speed * active.rate * (1.0 + correction)).toFloat())
        }
    }

    /** The plan's end: the main player jumps to where the arriving song has got to. */
    private fun finishMix(active: Mix) {
        val overlap = overlapPlayer ?: return abandonMix()
        filters.open()
        runCatching { overlap.playbackParameters = player.playbackParameters }
        if (!active.announced) {
            active.announced = true
            onCrossfadeStart(active.item)
        }
        val index = when {
            player.currentMediaItem?.mediaId == active.targetId -> player.currentMediaItemIndex
            runCatching { player.getMediaItemAt(player.nextMediaItemIndex).mediaId }.getOrNull() == active.targetId ->
                player.nextMediaItemIndex
            else -> (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == active.targetId
            } ?: C.INDEX_UNSET
        }
        if (index == C.INDEX_UNSET) return abandonMix()
        if (index == player.currentMediaItemIndex) {
            // Already there — the leaving song ran out as the fade did.
            beginHandoffFromOverlap()
            return
        }
        mixSeekPending = true
        player.seekTo(index, overlap.currentPosition.coerceAtLeast(0L))
        // The seek above normally lands in handleMediaItemTransition synchronously; if the
        // timeline didn't report it, the handoff is started here instead.
        if (mix != null && !handoffActive) {
            mixSeekPending = false
            beginHandoffFromOverlap(alreadySeeked = true)
        }
    }

    /** Drops the transition in flight and gives the main player its full volume back. */
    private fun abandonMix() {
        mix = null
        AutomixTransition.active.value = false
        filters.open()
        stopOverlapCrossfade(resetMainFade = true)
    }

    private fun rideFilters(plan: TransitionPlan, progress: Float) {
        when (plan.transitionStyle) {
            TransitionStyle.DJ_FILTER -> rideFilterSweep(plan, progress)
            TransitionStyle.DJ_BLEND ->
                if (plan.bassSwap) rideBassSwap(plan, progress) else rideVocalSeparation(plan, progress)
            TransitionStyle.GAPLESS -> filters.open()
            TransitionStyle.EQUAL_POWER -> rideVocalSeparation(plan, progress)
        }
    }

    private fun rideVocalSeparation(plan: TransitionPlan, progress: Float) {
        val amount = plan.vocalOverlap.coerceIn(0.0, 1.0)
        if (amount <= 0.0) {
            filters.open()
            return
        }
        val open = TransitionFilterProcessor.OPEN_HZ.toDouble()
        val floor = glide(open, VOCAL_SEPARATION_FLOOR_HZ, amount)
        filters.outgoing(
            glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
            TransitionFilterProcessor.OFF_HZ,
        )
        filters.incoming(
            TransitionFilterProcessor.OPEN_HZ,
            entryHighPass(progress, amount, VOCAL_SEPARATION_HIGH_PASS_HZ, ENTRY_OPEN_BY),
        )
    }

    private fun rideFilterSweep(plan: TransitionPlan, progress: Float) {
        val sweep = plan.filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            filters.open()
            return
        }
        val open = TransitionFilterProcessor.OPEN_HZ.toDouble()
        val entry = glide(open, FILTER_ENTRY_HZ, sweep)
        val floor = glide(open, FILTER_FLOOR_HZ, sweep)
        val cutoff = glide(entry, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE))
        filters.outgoing(cutoff.toFloat(), TransitionFilterProcessor.OFF_HZ)
        filters.incoming(
            TransitionFilterProcessor.OPEN_HZ,
            entryHighPass(progress, sweep, ENTRY_HIGH_PASS_HZ, ENTRY_OPEN_BY),
        )
    }

    private fun entryHighPass(progress: Float, amount: Double, topHz: Double, openBy: Double): Float {
        val remaining = (1.0 - progress / openBy).coerceIn(0.0, 1.0)
        return glide(TransitionFilterProcessor.OFF_HZ.toDouble(), topHz, amount * remaining.pow(ENTRY_SHAPE)).toFloat()
    }

    private fun glide(from: Double, to: Double, amount: Double): Double =
        from * (to / from).pow(amount.coerceIn(0.0, 1.0))

    private fun rideBassSwap(plan: TransitionPlan, progress: Float) {
        val swapAt = plan.bassSwapFraction.coerceIn(0.05, 0.95)
        val handover = ((progress - swapAt) / BASS_SWAP_WIDTH * 0.5 + 0.5).coerceIn(0.0, 1.0)
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)
        val entry = maxOf(
            bassCutoff(1.0 - handover),
            entryHighPass(
                progress,
                1.0,
                glide(BLEND_ENTRY_HIGH_PASS_HZ, BLEND_ENTRY_CLASH_HIGH_PASS_HZ, clash),
                BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash,
            ),
        )
        filters.incoming(TransitionFilterProcessor.OPEN_HZ, entry)
        filters.outgoing(blendExitLowPass(progress, clash), bassCutoff(handover))
    }

    private fun blendExitLowPass(progress: Float, clash: Double): Float {
        val from = BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash
        val amount = ((progress - from) / (1.0 - from)).coerceIn(0.0, 1.0)
        val floor = glide(BLEND_EXIT_LOW_PASS_HZ, BLEND_EXIT_CLASH_LOW_PASS_HZ, clash)
        return glide(TransitionFilterProcessor.OPEN_HZ.toDouble(), floor, amount).toFloat()
    }

    private fun bassCutoff(amount: Double): Float =
        glide(TransitionFilterProcessor.OFF_HZ.toDouble(), BASS_SWAP_HZ, amount).toFloat()

    private fun riseGain(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private fun fallGain(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private companion object {
        /** How far ahead the player must have buffered before Automix may read the song. */
        private const val ANALYSIS_BUFFER_AHEAD_MS = 30_000L
        const val AUTOMIX_TAG = "ExhaleAutomix"
        const val DEFAULT_FALLBACK_SECONDS = 6.0
        const val ARM_LEAD_MS = 4_000L
        const val ARRIVAL_GRACE_MS = 1_500L
        const val MIN_LATE_FADE_MS = 1_500L
        const val MIX_STEP_MS = 30L
        const val REPLAN_MS = 1_000L
        const val BEAT_LOCK_TOLERANCE_MS = 12.0
        const val BEAT_LOCK_RESPONSE_MS = 900.0

        // The filter shapes (CrossfadeController), unchanged.
        const val FILTER_ENTRY_HZ = 7_000.0
        const val FILTER_FLOOR_HZ = 300.0
        const val BASS_SWAP_HZ = 200.0
        const val BASS_SWAP_WIDTH = 0.10
        const val FILTER_SWEEP_SHAPE = 0.75
        const val ENTRY_HIGH_PASS_HZ = 1_200.0
        const val ENTRY_OPEN_BY = 0.6
        const val ENTRY_SHAPE = 0.35
        const val VOCAL_SEPARATION_FLOOR_HZ = 1_600.0
        const val VOCAL_SEPARATION_HIGH_PASS_HZ = 700.0
        const val BLEND_ENTRY_HIGH_PASS_HZ = 520.0
        const val BLEND_ENTRY_OPEN_BY = 0.45
        const val BLEND_ENTRY_CLASH_HIGH_PASS_HZ = 950.0
        const val BLEND_ENTRY_CLASH_OPEN_BY = 0.7
        const val BLEND_EXIT_CLASH_FROM = 0.12
        const val BLEND_EXIT_CLASH_LOW_PASS_HZ = 1_100.0
        const val BLEND_EXIT_FROM = 0.3
        const val BLEND_EXIT_LOW_PASS_HZ = 2_200.0
    }

    // ── Helpers de buffer ─────────────────────────────────────────────────────

    /**
     * Verifica si [targetPlayer] tiene al menos [minMs] de audio bufferizado,
     * o si el track es tan corto que está prácticamente completo en buffer.
     */
    private fun hasEnoughBuffer(targetPlayer: ExoPlayer, minMs: Long): Boolean {
        if (minMs <= 0L) return true
        if (targetPlayer.playbackState != Player.STATE_READY) return false

        val duration = targetPlayer.duration
        val buffered = targetPlayer.totalBufferedDuration.coerceAtLeast(0L)
        if (buffered >= minMs) return true

        // Track corto: aceptar si ya está casi completamente bufferizado
        return duration != C.TIME_UNSET &&
                targetPlayer.bufferedPosition >= duration - 150L
    }

    // ── Detección de transición gapless ───────────────────────────────────────

    /**
     * Devuelve true si [current] y [target] pertenecen al mismo álbum,
     * indicando que la transición debe ser gapless (sin crossfade).
     */
    private fun isGaplessAlbumTransition(current: MediaItem, target: MediaItem): Boolean {
        val albumA = current.metadata?.album?.id?.takeIf { it.isNotBlank() }
            ?: current.metadata?.album?.title?.takeIf { it.isNotBlank() }
            ?: current.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }

        val albumB = target.metadata?.album?.id?.takeIf { it.isNotBlank() }
            ?: target.metadata?.album?.title?.takeIf { it.isNotBlank() }
            ?: target.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }

        return albumA != null && albumA == albumB
    }

    // ── Gestión del overlap player ────────────────────────────────────────────

    private suspend fun primeOverlapForNext(nextIndex: Int) {
        val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull() ?: return
        val nextMediaId = nextItem.mediaId

        if (overlapPrimedIndex == nextIndex && overlapPrimedMediaId == nextMediaId) return

        stopOverlapCrossfade(resetMainFade = false)

        val overlap = ensureOverlapPlayer()
        overlap.clearMediaItems()
        overlap.setMediaItem(nextItem)
        overlap.prepare()
        overlap.playWhenReady = true
        overlap.volume = 0f

        overlapNormalizeFactor = fetchNormalizeFactorForMediaId(nextMediaId)
        overlapPrimedIndex = nextIndex
        overlapPrimedMediaId = nextMediaId
    }

    private fun unprimeOverlap() {
        if (crossfadeActive) return
        if (overlapPrimedIndex == C.INDEX_UNSET && overlapPrimedMediaId == null) return
        stopOverlapCrossfade(resetMainFade = false)
    }

    private fun beginOverlapCrossfade(fadeMs: Int, remainingMs: Long) {
        if (overlapPlayer == null) return

        val targetIndex = overlapPrimedIndex
        if (targetIndex != C.INDEX_UNSET && targetIndex < player.mediaItemCount) {
            onCrossfadeStart(player.getMediaItemAt(targetIndex))
        }

        crossfadeActive = true
        crossfadeStartElapsedMs = android.os.SystemClock.elapsedRealtime()
        crossfadeActiveDurationMs = min(fadeMs.toLong(), remainingMs).toInt().coerceAtLeast(1)
        crossfadeTargetIndex = overlapPrimedIndex
        crossfadeTargetMediaId = overlapPrimedMediaId
    }

    // ── Actualización de volúmenes (equal-power) ──────────────────────────────

    /**
     * Aplica la curva de crossfade equal-power usando sin/cos.
     *
     * Con una curva lineal la suma de potencias cae ~3 dB en t=0.5.
     * Con sin/cos se mantiene constante (sin²θ + cos²θ = 1),
     * eliminando el "dip" perceptual en el centro del fundido.
     *
     * @param t          Progreso normalizado [0..1]
     * @param baseVolume Volumen base del player principal
     * @param outgoing   Player que está saliendo (volumen decrece: cos)
     * @param incoming   Player que está entrando  (volumen crece:  sin)
     */
    private fun applyEqualPowerVolumes(
        t: Float,
        baseVolume: Float,
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
    ) {
        val clamped = t.coerceIn(0f, 1f)
        val radians = clamped.toDouble() * (PI / 2.0)

        // outgoing: 1→0 siguiendo coseno
        // incoming: 0→1 siguiendo seno
        outgoing.volume = (baseVolume * cos(radians).toFloat()).coerceIn(0f, maxSafeGainFactor)
        incoming.volume = (baseVolume * sin(radians).toFloat()).coerceIn(0f, maxSafeGainFactor)
    }

    private fun updateVolumes() {
        val overlap = overlapPlayer ?: run {
            stopOverlapCrossfade(resetMainFade = true)
            return
        }

        val baseOverlapVolume =
            (playerVolume.value * overlapNormalizeFactor * audioFocusVolumeFactor.value)
                .coerceIn(0f, 1f)

        // ── Fase de handoff ───────────────────────────────────────────────────
        if (handoffActive) {
            val nowElapsedMs = android.os.SystemClock.elapsedRealtime()

            val overlapDead =
                overlap.playbackState == Player.STATE_IDLE || overlap.playbackState == Player.STATE_ENDED
            val handoffElapsed = nowElapsedMs - handoffStartElapsedMs
            val handoffTimedOut = handoffElapsed >= handoffTimeoutMs

            if (overlapDead || handoffTimedOut) {
                completeHandoffFromOverlap()
                return
            }

            val overlapPositionMs = overlap.currentPosition.coerceAtLeast(0L)
            val mainPositionMs = player.currentPosition.coerceAtLeast(0L)
            val positionDriftMs = mainPositionMs - overlapPositionMs

            val shouldResyncMainToOverlap =
                !handoffSeekIssued || (
                        abs(positionDriftMs) > handoffDriftCorrectionThresholdMs &&
                                nowElapsedMs - handoffLastSyncSeekElapsedMs >= handoffReseekMinIntervalMs
                        )

            if (shouldResyncMainToOverlap) {
                handoffSeekIssued = true
                handoffTargetPositionMs = overlapPositionMs
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    player.seekTo(currentIndex, handoffTargetPositionMs)
                    handoffLastSyncSeekElapsedMs = nowElapsedMs
                }
                playbackFadeFactor.value = 0f
                overlap.volume = baseOverlapVolume
                return
            }

            if (!handoffRampStarted) {
                val bufferedMs = player.totalBufferedDuration.coerceAtLeast(0L)
                val mainStable =
                    player.playbackState == Player.STATE_READY &&
                            player.isPlaying &&
                            bufferedMs >= 1200L &&
                            abs(positionDriftMs) <= handoffRampStartDriftToleranceMs

                if (!mainStable) {
                    playbackFadeFactor.value = 0f
                    overlap.volume = baseOverlapVolume
                    return
                }

                handoffRampStarted = true
                handoffStartElapsedMs = nowElapsedMs
            }

            val denom = handoffDurationMs.toLong().coerceAtLeast(1L)
            val elapsed = (nowElapsedMs - handoffStartElapsedMs).coerceAtLeast(0L)
            val t = (elapsed.toFloat() / denom.toFloat()).coerceIn(0f, 1f)

            // Handoff: ramp lineal corto (450 ms) — suficiente para que no se note
            playbackFadeFactor.value = t
            overlap.volume = (baseOverlapVolume * (1f - t)).coerceIn(0f, 1f)

            if (t >= 1f) completeHandoffFromOverlap()
            return
        }

        // ── Fase de crossfade activo (equal-power) ────────────────────────────
        val denom = crossfadeActiveDurationMs.toLong().coerceAtLeast(1L)
        val elapsed = (android.os.SystemClock.elapsedRealtime() - crossfadeStartElapsedMs).coerceAtLeast(0L)
        val t = (elapsed.toFloat() / denom.toFloat()).coerceIn(0f, 1f)

        // Calculamos el factor del player principal a través del flujo playbackFadeFactor
        // para que el volumen final del player principal sea:
        //   player.volume = playerVolume * normalizeFactor * audioFocusFactor * playbackFadeFactor
        //                 = baseMainVolume * cos(t * π/2)
        //
        // Necesitamos que playbackFadeFactor = cos(t * π/2), ya que los demás factores
        // se combinan en el collect de MusicService.
        val radians = t.toDouble() * (PI / 2.0)
        playbackFadeFactor.value = cos(radians).toFloat().coerceIn(0f, 1f)

        // El overlap no pasa por playbackFadeFactor, así que aplicamos el volumen completo
        // directamente con la curva seno.
        overlap.volume =
            (baseOverlapVolume * sin(radians).toFloat()).coerceIn(0f, maxSafeGainFactor)
    }

    // ── Transición de MediaItem ───────────────────────────────────────────────

    private fun handleMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!crossfadeActive) return

        val targetId = crossfadeTargetMediaId
        val newId = mediaItem?.mediaId

        if ((reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
            !targetId.isNullOrBlank() && targetId == newId
        ) {
            // A skip onto the song already fading in finishes the fade rather than restarting it.
            val seeked = mixSeekPending
            mixSeekPending = false
            beginHandoffFromOverlap(alreadySeeked = seeked)
            return
        }

        stopOverlapCrossfade(resetMainFade = true)
    }

    // ── Handoff: traspaso del overlap al player principal ─────────────────────

    private fun beginHandoffFromOverlap(alreadySeeked: Boolean = false) {
        val overlap = overlapPlayer ?: run {
            stopOverlapCrossfade(resetMainFade = true)
            return
        }

        val overlapPositionMs = overlap.currentPosition.coerceAtLeast(0L)

        // Automix: whatever the arriving song's filter and tempo were doing, the main player takes
        // it over plain, so the overlap comes back to plain before the two are lined up.
        if (mix != null) {
            filters.open()
            runCatching { overlap.playbackParameters = player.playbackParameters }
            mix = null
        }

        handoffActive = true
        handoffSeekIssued = alreadySeeked
        handoffRampStarted = false
        handoffTargetPositionMs = overlapPositionMs
        handoffStartElapsedMs = android.os.SystemClock.elapsedRealtime()
        handoffLastSyncSeekElapsedMs = if (alreadySeeked) android.os.SystemClock.elapsedRealtime() else 0L
        handoffDurationMs = 450
        playbackFadeFactor.value = 0f
    }

    private fun completeHandoffFromOverlap() {
        val overlap = overlapPlayer ?: run {
            stopOverlapCrossfade(resetMainFade = true)
            return
        }

        runCatching {
            overlap.volume = 0f
            overlap.stop()
            overlap.clearMediaItems()
        }

        handoffActive = false
        handoffStartElapsedMs = 0L
        handoffDurationMs = 0
        handoffTargetPositionMs = 0L
        handoffLastSyncSeekElapsedMs = 0L
        handoffSeekIssued = false
        handoffRampStarted = false

        crossfadeActive = false
        AutomixTransition.active.value = false
        crossfadeTargetIndex = C.INDEX_UNSET
        crossfadeTargetMediaId = null
        crossfadeActiveDurationMs = 0
        overlapNormalizeFactor = 1f
        overlapPrimedIndex = C.INDEX_UNSET
        overlapPrimedMediaId = null

        playbackFadeFactor.value = 1f
    }

    // ── Stop / reset ──────────────────────────────────────────────────────────

    private fun stopOverlapCrossfade(resetMainFade: Boolean) {
        if (mix != null) {
            filters.open()
            mix = null
        }
        mixSeekPending = false
        crossfadeActive = false
        AutomixTransition.active.value = false
        crossfadeTargetIndex = C.INDEX_UNSET
        crossfadeTargetMediaId = null
        crossfadeActiveDurationMs = 0
        overlapNormalizeFactor = 1f
        overlapPrimedIndex = C.INDEX_UNSET
        overlapPrimedMediaId = null
        handoffActive = false
        handoffStartElapsedMs = 0L
        handoffDurationMs = 0
        handoffTargetPositionMs = 0L
        handoffLastSyncSeekElapsedMs = 0L
        handoffSeekIssued = false
        handoffRampStarted = false

        overlapPlayer?.let { overlap ->
            runCatching {
                overlap.volume = 0f
                overlap.stop()
                overlap.clearMediaItems()
                overlap.playbackParameters = player.playbackParameters
            }
        }

        if (resetMainFade) {
            playbackFadeFactor.value = 1f
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ensureOverlapPlayer(): ExoPlayer {
        val existing = overlapPlayer
        if (existing != null) return existing
        return overlapPlayerFactory().also { overlapPlayer = it }
    }

    private suspend fun fetchNormalizeFactorForMediaId(mediaId: String): Float {
        if (!audioNormalizationEnabled.value) return 1f

        val format = withContext(Dispatchers.IO) {
            database.format(mediaId).first()
        }

        val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb ?: return 1f
        var factor = 10f.pow((-loudness.toFloat()) / 20f)
        if (factor > 1f) factor = min(factor, maxSafeGainFactor)
        return factor
    }
}

/**
 * True while an Automix transition is audibly carrying the music on the second player — the mix
 * itself and the hand-off after it. The main player is busy re-cueing then and reports buffering
 * and pauses that the listener never hears; the UI reads this to keep showing "playing".
 */
object AutomixTransition {
    val active = kotlinx.coroutines.flow.MutableStateFlow(false)
}
