/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

/**
 * EXHALE_BENCH=1: a self-driving run through the app that records how long every frame took, then
 * writes a report and quits. No input is simulated — the app scrolls its own lists and opens its own
 * pages — so it measures exactly what a listener would see, and nothing else on the PC is touched.
 * The report goes to EXHALE_BENCH_OUT, or exhale-bench.txt in the temp folder.
 */
object Bench {
    val enabled: Boolean = System.getenv("EXHALE_BENCH") == "1"

    /**
     * EXHALE_BENCH_OFF=glass,backdrop,shadow,shared,tilt,fluid switches single effects off for a run,
     * to see what each one costs. Empty (the default) leaves everything as the listener has it.
     */
    private val disabled: Set<String> = (System.getenv("EXHALE_BENCH_OFF") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun off(effect: String): Boolean = effect in disabled

    @Volatile var homeList: LazyListState? = null
    @Volatile var pageList: LazyListState? = null

    class Phase(val name: String) {
        val frames = ArrayList<Float>()
    }
}

/** Lets the bench find a scrolling list. Does nothing unless the bench is running. */
@Composable
fun BenchList(state: LazyListState, home: Boolean) {
    if (!Bench.enabled) return
    DisposableEffect(state) {
        if (home) Bench.homeList = state else Bench.pageList = state
        onDispose {
            if (home && Bench.homeList === state) Bench.homeList = null
            if (!home && Bench.pageList === state) Bench.pageList = null
        }
    }
}

@Composable
fun BenchRunner(openArtist: () -> Unit, back: () -> Unit, openPlayer: () -> Unit, closePlayer: () -> Unit, pages: List<Pair<String, () -> Unit>> = emptyList()) {
    if (!Bench.enabled) return
    LaunchedEffect(Unit) {
        val phases = ArrayList<Bench.Phase>()

        suspend fun measure(name: String, block: suspend () -> Unit) {
            val phase = Bench.Phase(name)
            phases += phase
            coroutineScope {
                val recorder = launch {
                    var last = -1L
                    while (isActive) {
                        withFrameNanos { now ->
                            if (last > 0) {
                                val ms = (now - last) / 1_000_000f
                                phase.frames += ms
                                // Long frames, with the wall clock, to line up against a flight recording.
                                if (ms > 30f) println("BENCH-LONG $name %.1f ms at %d".format(ms, System.currentTimeMillis()))
                            }
                            last = now
                        }
                    }
                }
                block()
                recorder.cancel()
            }
        }

        suspend fun scroll(state: LazyListState?, distance: Float) {
            state ?: return
            state.animateScrollBy(distance, tween(2_200, easing = LinearEasing))
            state.animateScrollBy(-distance, tween(2_200, easing = LinearEasing))
        }

        // Home's feed and the artwork arrive; the page run also waits out the warm-up of the heavier pages.
        delay(if (System.getenv("EXHALE_BENCH_MODE") == "pages") 30_000 else 16_000)
        if (System.getenv("EXHALE_BENCH_MODE") == "pages") {
            // Every sidebar page twice over: the first visit builds it, the second shows what a return costs.
            measure("idle, Home on screen") { delay(2_000) }
            repeat(2) { round ->
                for ((name, open) in pages) measure("$name #${round + 1}") { open(); delay(1_200) }
            }
            writeReport(phases)
        }
        if (System.getenv("EXHALE_BENCH_MODE") == "player") {
            // Just the dock-to-player animation, three times over, so a first-open hitch and the steady
            // state can be told apart.
            measure("idle, Home on screen") { delay(2_000) }
            repeat(3) { round ->
                measure("open the player #${round + 1}") { openPlayer(); delay(1_300) }
                measure("player open #${round + 1}") { delay(1_500) }
                measure("close the player #${round + 1}") { closePlayer(); delay(1_300) }
                measure("home after close #${round + 1}") { delay(800) }
            }
            writeReport(phases)
        }
        measure("idle, Home on screen") { delay(3_000) }
        measure("scroll Home") { scroll(Bench.homeList, 5_000f) }
        measure("scroll Home again") { scroll(Bench.homeList, 5_000f) }
        measure("open an artist page") { openArtist(); delay(2_500) }
        measure("scroll the artist page") { scroll(Bench.pageList, 3_000f) }
        measure("back to Home") { back(); delay(1_500) }
        measure("open the player") { openPlayer(); delay(1_600) }
        measure("idle, player open") { delay(2_000) }
        measure("close the player") { closePlayer(); delay(1_600) }
        writeReport(phases)
    }
}

/** Frame percentiles per phase, to EXHALE_BENCH_OUT (or the temp folder); then the app quits. */
private fun writeReport(phases: List<Bench.Phase>) {
    val report = buildString {
        val idle = phases.first().frames.sorted()
        val vsync = idle.getOrElse(idle.size / 2) { 16.7f }
        appendLine("Exhale frame report · ${java.time.LocalDateTime.now()}")
        appendLine("off=${System.getenv("EXHALE_BENCH_OFF").orEmpty()} mode=${System.getenv("EXHALE_BENCH_MODE").orEmpty()}")
        appendLine("renderApi=${System.getProperty("skiko.renderApi")} gpu=${System.getProperty("skiko.directx.gpu.priority")} vsync≈%.2f ms (%.0f Hz)".format(vsync, 1000f / vsync))
        appendLine()
        appendLine("%-26s %6s %7s %7s %7s %7s %7s %6s".format("phase", "frames", "fps", "p50", "p95", "p99", "max", "janks"))
        for (phase in phases) {
            val f = phase.frames.sorted()
            if (f.isEmpty()) {
                appendLine("%-26s (no frames)".format(phase.name))
                continue
            }
            fun pct(p: Float) = f[((f.size - 1) * p).toInt()]
            val fps = 1000f / (f.sum() / f.size)
            // A jank: a frame that took more than twice the display's own interval.
            val janks = f.count { it > vsync * 2.2f }
            appendLine("%-26s %6d %7.1f %7.2f %7.2f %7.2f %7.2f %6d".format(phase.name, f.size, fps, pct(0.5f), pct(0.95f), pct(0.99f), f.last(), janks))
        }
    }
    val out = System.getenv("EXHALE_BENCH_OUT")?.let(::File) ?: File(System.getProperty("java.io.tmpdir"), "exhale-bench.txt")
    out.writeText(report)
    println(report)
    exitProcess(0)
}
