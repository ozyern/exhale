package com.ozyern.exhale.playback.dj

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coefficient-level check for the DjFilterAudioProcessor bass-swap fix:
 * cascading two Biquad stages must roll off faster than one (24 dB/oct vs
 * 12 dB/oct), and a shared crossover must leave both filters near -3dB at
 * the cutoff rather than deep in the stopband — which is what happens when
 * the low-pass and high-pass cutoffs disagree, the bug this replaces.
 */
class BiquadCrossoverTest {

    private val sampleRate = 44100

    /** Steady-state gain of [stages] cascaded biquads at [freqHz], discarding
     *  the settling transient before measuring. */
    private fun cascadeGain(stages: List<Biquad>, freqHz: Float): Double {
        val samplesPerCycle = sampleRate / freqHz
        val total = (200 * samplesPerCycle).toInt()
        val settle = total / 2
        var inSum = 0.0
        var outSum = 0.0
        for (i in 0 until total) {
            val t = i / sampleRate.toDouble()
            val x = sin(2.0 * PI * freqHz * t)
            var y = x
            for (stage in stages) y = stage.process(y)
            if (i >= settle) {
                inSum += x * x
                outSum += y * y
            }
        }
        return sqrt(outSum / inSum)
    }

    @Test
    fun `two cascaded low-pass stages roll off faster than one`() {
        val cutoff = 250f
        val testFreq = 500f // one octave above cutoff

        val gainSingle = cascadeGain(listOf(Biquad().apply { setLowPass(sampleRate, cutoff) }), testFreq)
        val gainDouble = cascadeGain(
            listOf(
                Biquad().apply { setLowPass(sampleRate, cutoff) },
                Biquad().apply { setLowPass(sampleRate, cutoff) },
            ),
            testFreq,
        )

        assertTrue("single=$gainSingle double=$gainDouble", gainDouble < gainSingle)
    }

    @Test
    fun `shared crossover leaves both filters near -3dB at the cutoff, not deep in the stopband`() {
        val crossover = 250f

        val lowGain = cascadeGain(
            listOf(
                Biquad().apply { setLowPass(sampleRate, crossover) },
                Biquad().apply { setLowPass(sampleRate, crossover) },
            ),
            crossover,
        )
        val highGain = cascadeGain(
            listOf(
                Biquad().apply { setHighPass(sampleRate, crossover) },
                Biquad().apply { setHighPass(sampleRate, crossover) },
            ),
            crossover,
        )

        // A double-biquad Butterworth section sits around -3 to -4dB (~0.6-0.7
        // linear) at its own cutoff. Either side falling well below that at the
        // *shared* cutoff would mean the old mismatched-cutoff notch is back.
        assertTrue("low=$lowGain", lowGain > 0.4)
        assertTrue("high=$highGain", highGain > 0.4)
    }
}
