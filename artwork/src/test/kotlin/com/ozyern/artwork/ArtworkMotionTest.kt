package com.ozyern.artwork

import org.junit.Assert.*
import org.junit.Test

class ArtworkMotionTest {
    @Test
    fun startsAndLandsExactlyWithoutOvershoot() {
        assertEquals(0f, ArtworkMotion.progress(0f), 0f)
        assertEquals(1f, ArtworkMotion.progress(1f), 0f)
        var previous = 0f
        for (frame in 0..1000) {
            val current = ArtworkMotion.progress(frame / 1000f)
            assertTrue(current in previous..1f)
            previous = current
        }
    }

    @Test
    fun invalidProgressCannotReachTheRenderMatrix() {
        assertEquals(1f, ArtworkMotion.progress(Float.NaN), 0f)
        assertEquals(1f, ArtworkMotion.progress(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, ArtworkMotion.progress(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, ArtworkMotion.progress(-1f), 0f)
        assertEquals(1f, ArtworkMotion.progress(2f), 0f)
    }
}
