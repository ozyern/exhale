package com.ozyern.exhale.ui.lyricsflow

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The lyric clock, driven the way the app drives it: a frame every 8.3 ms, and a reading of the
 * player every 100 ms that is coarse (the audio output's position moves in steps) and a little
 * noisy. The words should move a frame's worth every frame, never stand still and never jump.
 */
class LyricClockSmoothnessTest {
    @Test
    fun movesEvenlyBetweenNoisyReadings() {
        val rnd = Random(7)
        val clock = LyricClock(0)
        val frame = 1000.0 / 120.0
        var now = 1_000.0
        var nextReading = now
        var reported = 0L
        var sampledAt = now
        var last = clock.frame(now, 0, now, 0)
        var worstStep = 0.0
        var stalls = 0
        var maxLag = 0.0
        for (i in 0 until 120 * 30) {
            now += frame
            if (now >= nextReading) {
                // Where the song is, in 20 ms steps, with up to 15 ms of noise either way.
                val truth = now - 1_000.0
                reported = ((truth / 20.0).toLong() * 20L + rnd.nextLong(-15, 16)).coerceAtLeast(0)
                sampledAt = now
                // The main thread sees it up to 40 ms later; the stamp still says when it was taken.
                nextReading = now + 100.0
            }
            val shown = clock.frame(now, reported, sampledAt, 0)
            val step = (shown - last).toDouble()
            if (i > 240) {
                worstStep = maxOf(worstStep, kotlin.math.abs(step - frame))
                if (step <= 0) stalls++
                maxLag = maxOf(maxLag, kotlin.math.abs((now - 1_000.0) - shown))
            }
            last = shown
        }
        println("worst step deviation=$worstStep ms, stalls=$stalls, max lag=$maxLag ms")
        assertTrue("stalled $stalls frames", stalls == 0)
        assertTrue("a frame moved ${worstStep} ms off its share", worstStep < 6.0)
        assertTrue("drifted $maxLag ms from the song", maxLag < 60.0)
    }
}
