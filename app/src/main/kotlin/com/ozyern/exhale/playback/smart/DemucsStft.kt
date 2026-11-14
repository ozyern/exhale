package com.ozyern.exhale.playback.smart

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** Demucs' own spectrogram framing (4096 window, 1024 hop, Nyquist bin dropped, 1/sqrt(n) scaling). */
internal object DemucsStft {
    const val N_FFT = 4096
    const val HOP = 1024
    const val BINS = 2048
    private const val PAD = HOP / 2 * 3
    private const val LEAD_FRAMES = 2

    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat() }
    private val cosTable = FloatArray(N_FFT / 2) { cos(2.0 * PI * it / N_FFT).toFloat() }
    private val sinTable = FloatArray(N_FFT / 2) { (-sin(2.0 * PI * it / N_FFT)).toFloat() }

    fun frames(length: Int) = ceil(length / HOP.toDouble()).toInt()

    /** Fills `re[bin][frame]` and `im[bin][frame]` for one channel of [length] samples. */
    fun forward(x: FloatArray, length: Int, re: Array<FloatArray>, im: Array<FloatArray>) {
        val frames = frames(length)
        val fr = FloatArray(N_FFT)
        val fi = FloatArray(N_FFT)
        val scale = 1f / sqrt(N_FFT.toFloat())
        for (f in 0 until frames) {
            val start = f * HOP - PAD
            for (n in 0 until N_FFT) {
                fr[n] = x[reflect(start + n, length)] * window[n]
                fi[n] = 0f
            }
            fft(fr, fi)
            for (bin in 0 until BINS) {
                re[bin][f] = fr[bin] * scale
                im[bin][f] = fi[bin] * scale
            }
        }
    }

    /** Inverse of [forward]: overlap-adds `[bin][frame]` spectra into [out], `length` samples. */
    fun inverse(re: Array<FloatArray>, im: Array<FloatArray>, length: Int, out: FloatArray) {
        val frames = frames(length)
        val total = frames * HOP + 2 * PAD
        val acc = FloatArray(total + N_FFT)
        val norm = FloatArray(total + N_FFT)
        val offset = N_FFT / 2
        val fr = FloatArray(N_FFT)
        val fi = FloatArray(N_FFT)
        val unscale = sqrt(N_FFT.toFloat())
        for (f in -LEAD_FRAMES until frames + LEAD_FRAMES) {
            val position = f * HOP + offset
            if (f in 0 until frames) {
                fr.fill(0f)
                fi.fill(0f)
                for (bin in 0 until BINS) {
                    fr[bin] = re[bin][f] * unscale
                    fi[bin] = im[bin][f] * unscale
                    if (bin > 0) {
                        fr[N_FFT - bin] = fr[bin]
                        fi[N_FFT - bin] = -fi[bin]
                    }
                }
                ifft(fr, fi)
                for (n in 0 until N_FFT) acc[position + n] += fr[n] * window[n]
            }
            for (n in 0 until N_FFT) norm[position + n] += window[n] * window[n]
        }
        for (i in 0 until length) {
            val at = PAD + i + offset
            out[i] = if (norm[at] > 1e-6f) acc[at] / norm[at] else 0f
        }
    }

    private fun reflect(index: Int, length: Int): Int = when {
        index < 0 -> -index
        index >= length -> 2 * (length - 1) - index
        else -> index
    }.coerceIn(0, length - 1)

    private fun ifft(re: FloatArray, im: FloatArray) {
        for (i in im.indices) im[i] = -im[i]
        fft(re, im)
        val scale = 1f / N_FFT
        for (i in re.indices) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until N_FFT) {
            var bit = N_FFT shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= N_FFT) {
            val half = size / 2
            val step = N_FFT / size
            var i = 0
            while (i < N_FFT) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] += vr
                    im[a] += vi
                }
                i += size
            }
            size = size shl 1
        }
    }
}
