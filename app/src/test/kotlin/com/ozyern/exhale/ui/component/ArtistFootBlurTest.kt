package com.ozyern.exhale.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hero foot blur curve fitted on the iOS 27 artist page references. */
class ArtistFootBlurTest {
    @Test
    fun `player hero mask preserves the top and reaches transparency at the foot`() {
        val stops = heroFadeOutStops()
        assertEquals(1f, stops.first().second.alpha, 0f)
        assertEquals(0.78f, stops[1].first, 0f)
        assertEquals(1f, stops.last().first, 0f)
        assertEquals(0f, stops.last().second.alpha, 0f)
        assertTrue(stops.any { it.second.alpha in 0.4f..0.6f })
        for ((before, after) in stops.zipWithNext()) {
            assertTrue(after.first >= before.first)
            assertTrue(after.second.alpha <= before.second.alpha)
            assertTrue(before.second.alpha - after.second.alpha < 0.07f)
        }
    }
    @Test
    fun `blur leaves zero smoothly, hits the measured points, caps`() {
        assertEquals(0f, artistFootBlurPt(0.5f), 0f)
        assertEquals(0f, artistFootBlurPt(0.78f), 0f)
        // Just past the knee the blur is still invisible: no step for the eye to catch.
        assertTrue(artistFootBlurPt(0.80f) < 0.2f)
        assertEquals(12f, artistFootBlurPt(0.995f), 1f)
        assertEquals(48f, artistFootBlurPt(1.3f), 0f)
        var previous = 0f
        var f = 0.70f
        while (f <= 1.3f) {
            val v = artistFootBlurPt(f)
            assertTrue("blur step at $f", v - previous < 0.4f)
            previous = v
            f += 0.001f
        }
    }

    @Test
    fun `live hero blur is the foot at rest, grows with scroll, and never steps`() {
        for (f in listOf(0.2f, 0.78f, 0.9f, 1.0f, 1.2f)) {
            assertEquals(artistFootBlurPt(f), artistHeroBlurPt(f, 0f), 0.001f)
        }
        // Scrolled all the way: the whole photo is blurred.
        assertTrue(artistHeroBlurPt(0f, 1f) > 20f)
        var p = 0f
        while (p <= 1f) {
            var previous = artistHeroBlurPt(0f, p)
            var f = 0.001f
            while (f <= ArtistHeroExtent) {
                val v = artistHeroBlurPt(f, p)
                assertTrue("step at f=$f p=$p", kotlin.math.abs(v - previous) < 0.5f)
                assertTrue("shrinks with scroll at f=$f p=$p", v + 0.001f >= artistHeroBlurPt(f, (p - 0.05f).coerceAtLeast(0f)))
                previous = v
                f += 0.001f
            }
            p += 0.05f
        }
    }

    @Test
    fun `wash is continuous and smooth end to end`() {
        assertEquals(0f, artistWash(0.3f), 0f)
        assertEquals(0f, artistWash(307f / 393f), 0f)
        assertEquals(0.5f, artistWash((307f / 393f + 1.21f) / 2f), 0.001f)
        assertEquals(1f, artistWash(1.21f), 0f)
        assertEquals(1f, artistWash(ArtistHeroExtent), 0f)
        // No jumps anywhere, and no slope break bigger than a fine sampling step can show.
        var previous = artistWash(0f)
        var previousSlope = 0f
        var f = 0.001f
        while (f <= ArtistHeroExtent) {
            val v = artistWash(f)
            val slope = (v - previous) / 0.001f
            assertTrue("jump at $f", kotlin.math.abs(v - previous) < 0.004f)
            assertTrue("slope break at $f", kotlin.math.abs(slope - previousSlope) < 0.2f)
            previous = v
            previousSlope = slope
            f += 0.001f
        }
    }
}
