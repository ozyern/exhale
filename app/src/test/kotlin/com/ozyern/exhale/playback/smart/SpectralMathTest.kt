package com.ozyern.exhale.playback.smart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectralMathTest {

    @Test
    fun `fft matches a direct DFT`() {
        val n = SpectralMath.N_FFT
        val signal = FloatArray(n) { (sin(it * 0.01) + 0.5 * cos(it * 0.37)).toFloat() }
        val re = signal.copyOf()
        val im = FloatArray(n)
        SpectralMath.fft(re, im)
        for (bin in intArrayOf(0, 1, 17, 500, 3072)) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * bin * t / n
                sr += signal[t] * cos(angle)
                si += signal[t] * sin(angle)
            }
            assertEquals("re[$bin]", sr, re[bin].toDouble(), 0.05)
            assertEquals("im[$bin]", si, im[bin].toDouble(), 0.05)
        }
    }

    @Test
    fun `stft then istft reconstructs the signal away from the edges`() {
        val hop = 1024
        val bins = 3073 // full half spectrum, so nothing is discarded
        val samples = 40 * hop
        val signal = FloatArray(samples) { (0.6 * sin(it * 0.05) + 0.3 * sin(it * 0.31) + 0.1 * cos(it * 0.9)).toFloat() }
        val frames = samples / hop + 1
        val re = Array(frames) { FloatArray(bins) }
        val im = Array(frames) { FloatArray(bins) }
        val fr = FloatArray(SpectralMath.N_FFT)
        val fi = FloatArray(SpectralMath.N_FFT)
        for (f in 0 until frames) {
            val start = f * hop - SpectralMath.N_FFT / 2
            for (n in 0 until SpectralMath.N_FFT) {
                var index = start + n
                if (index < 0) index = -index
                if (index >= samples) index = 2 * (samples - 1) - index
                fr[n] = signal[index.coerceIn(0, samples - 1)] * SpectralMath.HANN[n]
                fi[n] = 0f
            }
            SpectralMath.fft(fr, fi)
            for (k in 0 until bins) {
                re[f][k] = fr[k]
                im[f][k] = fi[k]
            }
        }
        val out = FloatArray((frames - 1) * hop)
        SpectralMath.istft(re, im, frames, bins, hop, out)

        // Only the first and last half window are partly covered; everything between must match.
        val edge = SpectralMath.N_FFT / 2
        var worst = 0f
        for (i in edge until samples - edge) worst = maxOf(worst, abs(out[i] - signal[i]))
        assertTrue("worst error $worst", worst < 1e-3f)
    }
}
