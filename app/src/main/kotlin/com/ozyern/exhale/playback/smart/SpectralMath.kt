package com.ozyern.exhale.playback.smart

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object SpectralMath {
    const val N_FFT = 6144
    private const val M = N_FFT / 3

    val HANN = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat() }

    private val TWIDDLE: FloatArray by lazy {
        val tw = FloatArray(3 * N_FFT * 2)
        for (s in 0..2) {
            for (k in 0 until N_FFT) {
                val angle = -2.0 * PI * s * k / N_FFT
                tw[(s * N_FFT + k) * 2] = cos(angle).toFloat()
                tw[(s * N_FFT + k) * 2 + 1] = sin(angle).toFloat()
            }
        }
        tw
    }

    private class Scratch {
        val decRe = FloatArray(M)
        val decIm = FloatArray(M)
        val accRe = Array(3) { FloatArray(M) }
        val accIm = Array(3) { FloatArray(M) }
    }

    private val scratch = ThreadLocal.withInitial { Scratch() }

    fun fft(re: FloatArray, im: FloatArray) {
        val w = scratch.get()!!
        for (s in 0..2) {
            for (r in 0 until M) {
                w.decRe[r] = re[3 * r + s]
                w.decIm[r] = im[3 * r + s]
            }
            radix2(w.decRe, w.decIm)
            w.decRe.copyInto(w.accRe[s])
            w.decIm.copyInto(w.accIm[s])
        }
        for (k in 0 until N_FFT) {
            val m = k % M
            var sr = 0f
            var si = 0f
            for (s in 0..2) {
                val wr = TWIDDLE[(s * N_FFT + k) * 2]
                val wi = TWIDDLE[(s * N_FFT + k) * 2 + 1]
                val ar = w.accRe[s][m]
                val ai = w.accIm[s][m]
                sr += wr * ar - wi * ai
                si += wr * ai + wi * ar
            }
            re[k] = sr
            im[k] = si
        }
    }

    fun ifft(re: FloatArray, im: FloatArray) {
        for (i in im.indices) im[i] = -im[i]
        fft(re, im)
        val scale = 1f / N_FFT
        for (i in re.indices) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }

    /** Matches torch `istft(center=True)`; the first and last half window are only partly covered, so callers drop them. */
    fun istft(re: Array<FloatArray>, im: Array<FloatArray>, frames: Int, bins: Int, hop: Int, out: FloatArray) {
        val length = (frames - 1) * hop
        require(out.size >= length)
        out.fill(0f, 0, length)
        val norm = FloatArray(length)
        val fr = FloatArray(N_FFT)
        val fi = FloatArray(N_FFT)
        for (f in 0 until frames) {
            fr.fill(0f)
            fi.fill(0f)
            for (k in 0 until bins) {
                fr[k] = re[f][k]
                fi[k] = im[f][k]
                if (k > 0) {
                    fr[N_FFT - k] = re[f][k]
                    fi[N_FFT - k] = -im[f][k]
                }
            }
            ifft(fr, fi)
            val start = f * hop - N_FFT / 2
            for (n in 0 until N_FFT) {
                val position = start + n
                if (position < 0 || position >= length) continue
                out[position] += fr[n] * HANN[n]
                norm[position] += HANN[n] * HANN[n]
            }
        }
        for (i in 0 until length) if (norm[i] > 1e-6f) out[i] /= norm[i]
    }

    private fun radix2(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
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
        while (size <= n) {
            val angle = -2.0 * PI / size
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until size / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + size / 2] * wRe - im[i + k + size / 2] * wIm
                    val vi = re[i + k + size / 2] * wIm + im[i + k + size / 2] * wRe
                    re[i + k] = (ur + vr).toFloat()
                    im[i + k] = (ui + vi).toFloat()
                    re[i + k + size / 2] = (ur - vr).toFloat()
                    im[i + k + size / 2] = (ui - vi).toFloat()
                    val t = wRe
                    wRe = wRe * wr - wIm * wi
                    wIm = t * wi + wIm * wr
                }
                i += size
            }
            size = size shl 1
        }
    }
}
