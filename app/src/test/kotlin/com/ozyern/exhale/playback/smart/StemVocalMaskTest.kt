package com.ozyern.exhale.playback.smart

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StemVocalMaskTest {
    @Test
    fun `loudness follows where the vocal stem is singing`() {
        val rate = 8000.0
        val samples = FloatArray((rate * 20).toInt()) { i ->
            val second = i / rate
            if (second in 5.0..10.0) (0.5 * sin(2 * PI * 440 * second)).toFloat() else 0f
        }
        val times = DoubleArray(20) { it + 0.5 }
        val mask = StemVocalMask.fromLoudness(StemVocalMask.loudnessAt(samples, rate, times, 0.5))

        assertTrue("silence reads as no vocal", mask[1] < 0.1 && mask[15] < 0.1)
        assertTrue("singing reads as active", mask[7] >= 0.6)
    }

    @Test
    fun `a silent stem produces an all-zero mask`() {
        assertEquals(0.0, StemVocalMask.fromLoudness(DoubleArray(10)).sum(), 0.0)
    }

    @Test
    fun `a quiet bleed next to loud singing stays below the active threshold`() {
        val loudness = DoubleArray(20) { if (it < 10) 1.0 else 0.02 }
        val mask = StemVocalMask.fromLoudness(loudness)
        assertTrue(mask[12] < 0.1)
        assertEquals(1.0, mask[3], 1e-9)
    }
}
