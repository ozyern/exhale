package com.ozyern.exhale.playback.audio

import com.ozyern.exhale.playback.dj.BeatGrid
import com.ozyern.exhale.playback.dj.MusicalKey
import org.junit.Assert.assertEquals
import org.junit.Test

class DjSetOrdererTest {

    private fun grid(bpm: Float, confidence: Float = 0.9f) = BeatGrid(bpm, 0f, confidence)
    private fun key(pitchClass: Int, minor: Boolean = false) = MusicalKey(pitchClass, minor, 0.9f)

    @Test
    fun `chain prefers a full-dj-tier neighbor over a plain-crossfade one`() {
        // Seed at 128 BPM / C major. "close" is a perfect mix partner (126 BPM,
        // compatible key); "far" only qualifies for a plain crossfade (100 BPM).
        val analyses = mapOf(
            "seed" to (grid(128f) to key(0)),
            "far" to (grid(100f) to key(0)),
            "close" to (grid(126f) to key(7)), // G major: compatible with C (a fifth)
        )

        val ordered = DjSetOrderer.order(listOf("seed", "far", "close"), analyses)

        assertEquals(listOf("seed", "close", "far"), ordered)
    }

    @Test
    fun `unanalyzed songs don't crash and just fall to the end`() {
        val analyses = mapOf("seed" to (grid(128f) to key(0)), "good" to (grid(127f) to key(0)))

        val ordered = DjSetOrderer.order(listOf("seed", "unknown", "good"), analyses)

        assertEquals(listOf("seed", "good", "unknown"), ordered)
    }

    @Test
    fun `pools of two or fewer are returned unchanged`() {
        assertEquals(listOf("a", "b"), DjSetOrderer.order(listOf("a", "b"), emptyMap()))
        assertEquals(listOf("a"), DjSetOrderer.order(listOf("a"), emptyMap()))
    }
}
