package com.ozyern.exhale.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CachedSpanTest {
    private val chunk = 10L

    @Test
    fun `full window and file tail are served, mid-file partial is not`() {
        assertEquals(chunk, cachedSpan(cached = 25, position = 0, contentLength = 100, max = chunk))
        assertEquals(6L, cachedSpan(cached = 6, position = 94, contentLength = 100, max = chunk))
        // Partial head of a window in the middle: the rest needs the network -> 0.
        assertEquals(0L, cachedSpan(cached = 4, position = 40, contentLength = 100, max = chunk))
        // Unknown length can't prove it reached the end.
        assertEquals(0L, cachedSpan(cached = 4, position = 40, contentLength = -1, max = chunk))
        assertEquals(0L, cachedSpan(cached = -1, position = 0, contentLength = 100, max = chunk))
    }
}
