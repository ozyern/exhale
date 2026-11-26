/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop

import coil3.SingletonImageLoader
import kotlin.concurrent.thread

/**
 * Keeps native memory from piling up behind a quiet Java heap.
 *
 * Covers, backgrounds and video frames hold their pixels outside the heap, behind wrappers of a few
 * bytes each, freed only once the garbage collector finds the wrapper unreachable. The heap is capped
 * at 1 GB and the moving cover keeps young collections running all the time, which both stops G1's
 * idle-time collection from ever firing and promotes the wrappers that live a little while (artwork
 * evicted from Coil's memory cache, say) into the old generation, which nothing then sweeps. Their
 * pixels stayed allocated: the process grew to many gigabytes over a long session.
 *
 * So every [INTERVAL_MS] the guard asks for a collection. With -XX:+ExplicitGCInvokesConcurrent that
 * is a concurrent cycle, no pause, and it finds those wrappers, so their native memory is freed. While
 * the window is hidden or minimised the decoded-artwork cache is emptied too: it refills from the disk
 * cache in milliseconds when the window comes back.
 */
object MemoryGuard {
    private const val INTERVAL_MS = 30_000L

    @Volatile private var started = false

    /** Hidden to the tray or minimised: nothing on screen needs decoded artwork. */
    @Volatile var windowAway: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed && value) trim()
        }

    fun start() {
        if (started) return
        started = true
        thread(name = "exhale-memory-guard", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            while (true) {
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@thread
                }
                if (windowAway) trim() else System.gc()
            }
        }
    }

    /** Drops the decoded artwork and reclaims what the drop let go. */
    private fun trim() {
        runCatching { SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).memoryCache?.clear() }
        System.gc()
    }
}
