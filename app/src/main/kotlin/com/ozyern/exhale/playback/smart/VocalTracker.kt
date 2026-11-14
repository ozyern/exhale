/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Convx contributors (adapted from an open-source base)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into the reference app -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.ozyern.exhale.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** The linear-frequency STFT front end open-unmix was trained on. */
object VocalSpectrogram {
    val available: Boolean get() = MelSpectrogram.available
    val bins: Int by lazy { if (available) nativeBins() else 2049 }
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 44_100.0 }
    val hop: Int by lazy { if (available) nativeHop() else 1024 }
    val fftSize: Int by lazy { if (available) nativeFftSize() else 4096 }
    val frameRate: Double get() = sampleRate / hop

    /**
     * Computes the magnitude STFT for planar stereo at [sampleRate].
     *
     * Returns null when the library is missing, the rate is wrong, or the input is shorter than one
     * padded frame, all of which mean "no mask available" rather than an error.
     */
    fun compute(left: FloatArray, right: FloatArray, rate: Double = sampleRate): Spectrogram? {
        if (!available || left.isEmpty() || left.size != right.size) return null
        val values = nativeCompute(left, right, rate)
        if (values.isEmpty()) return null
        return Spectrogram(values, frames = values.size / (CHANNELS * bins), bins = bins)
    }

    /**
     * Bin-major, flattened: channel c, bin b, frame f is at `(c * bins + b) * frames + f`. That
     * ordering is not the natural one for an STFT computed a frame at a time; it is chosen to
     * match the model's `[1, 2, bins, frames]` tensor exactly, so nothing has to transpose.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val bins: Int) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }

    const val CHANNELS = 2

    @JvmStatic private external fun nativeCompute(left: FloatArray, right: FloatArray, rate: Double): FloatArray
    @JvmStatic private external fun nativeBins(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
    @JvmStatic private external fun nativeFftSize(): Int
}

/** Estimates vocal presence with UVR-MDX-NET-9482 (MIT) for transition planning and karaoke attenuation. */
class VocalTracker(private val context: Context) {

    @Volatile private var session: OrtSession? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private val lock = Any()

    private fun session(): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            return runCatching {
                val file = File(context.filesDir, MODEL_ASSET)
                if (!file.exists() || file.length() == 0L) {
                    context.assets.open(MODEL_ASSET).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                // One-time eviction of the retired open-unmix checkpoint (~9 MB).
                File(context.filesDir, STALE_MODEL_ASSET).delete()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(INFERENCE_THREADS)
                    setInterOpNumThreads(1)
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                    addConfigEntry("session.inter_op.allow_spinning", "0")
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Same reasoning as BeatTracker: the arena retains every block it allocates for
                    // the life of the session, which a backgrounded music player cannot justify.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                try {
                    OrtEnvironment.getEnvironment().createSession(file.absolutePath, options).also {
                        sessionOptions = options
                        session = it
                    }
                } catch (error: Throwable) {
                    options.close()
                    throw error
                }
            }.onFailure { Log.w(TAG, "Vocal model unavailable; no mask will be produced", it) }
                .getOrNull()
        }
    }

    /**
     * Returns one vocal-presence value per STFT frame in [0, 1], or null when unavailable.
     *
     * The model's input width is fixed at [FIXED_FRAMES] (~22.8 s), which was chosen upstream to
     * cover a transition overlap plus padding. Shorter input is zero-padded; longer is refused
     * rather than chunked, because a transition never needs more than one window.
     */
    fun track(left: FloatArray, right: FloatArray, rate: Double): FloatArray? {
        if (!VocalSpectrogram.available) return null
        val active = session() ?: return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null

        val started = System.currentTimeMillis()
        val frames = (resampledLeft.size + MdxFront.HOP - 1) / MdxFront.HOP
        if (resampledLeft.size != resampledRight.size || frames > FIXED_FRAMES) {
            Log.d(TAG, "Window of $frames frames exceeds the model's $FIXED_FRAMES")
            return null
        }

        val ratios = MdxFront.ratios(resampledLeft, resampledRight, frames, active)
        if (ratios == null) {
            Log.w(TAG, "Vocal inference failed")
            return null
        }
        val lowBin = floor(LOW_HZ * MdxFront.N_FFT / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * MdxFront.N_FFT / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(MdxFront.DIM_F - 1)
        if (highBin <= lowBin) return FloatArray(0)
        val curve = FloatArray(frames) { frame ->
            var sum = 0.0
            for (bin in lowBin..highBin) sum += ratios[frame][bin]
            (sum / (highBin - lowBin + 1)).toFloat()
        }
        Log.d(
            TAG,
            "vocal mask $frames frames in " +
                "${System.currentTimeMillis() - started}ms",
        )
        return curve
    }

    /**
     * Runs the model's MDX-Net STFT and chunking. Window, hop, padding, and
     * chunk alignment must match training so each output frame maps directly
     * to the vocal mask. Reused scratch buffers are guarded because callers can
     * run on separate threads. Denoise mode stays off to avoid doubling inference.
     */
    private object MdxFront {
        const val N_FFT = 6144
        const val HOP = 1024
        const val DIM_F = 2048
        const val DIM_T = 256
        const val CHUNK = HOP * (DIM_T - 1)
        const val TRIM = N_FFT / 2
        const val GEN = CHUNK - 2 * TRIM
        const val STRIDE = GEN / HOP
        const val LEAD = TRIM / HOP

        private val HANN = FloatArray(N_FFT) {
            (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat()
        }
        private val workRe = FloatArray(N_FFT)
        private val workIm = FloatArray(N_FFT)
        private const val M = N_FFT / 3
        private val decRe = FloatArray(M)
        private val decIm = FloatArray(M)
        private val accRe = Array(3) { FloatArray(M) }
        private val accIm = Array(3) { FloatArray(M) }

        /** Twiddles W_N^(s*k) for the radix-3 combine, real/imag interleaved per s. */
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

        // Allocated once: karaoke calls this every ~5.6 s window, and a fresh ~12MB direct
        // buffer per call is native memory the GC never feels pressure to free. Direct, so
        // the input never lands on the Java heap and ORT reads it where it lies.
        private val backing: ByteBuffer by lazy {
            ByteBuffer.allocateDirect(4 * DIM_F * DIM_T * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        }
        private val mixRe by lazy { Array(2) { Array(DIM_T) { FloatArray(DIM_F) } } }
        private val mixIm by lazy { Array(2) { Array(DIM_T) { FloatArray(DIM_F) } } }

        /**
         * Per-frame vocal ratios `[frame][bin]` in [0, 1], silence baked as 0, or
         * null when inference fails.
         */
        fun ratios(
            left: FloatArray,
            right: FloatArray,
            frames: Int,
            session: OrtSession,
        ): Array<FloatArray>? = synchronized(this) {
            val samples = left.size
            if (samples == 0 || frames <= 0) return@synchronized null
            val out = Array(frames) { FloatArray(DIM_F) }
            val pad = GEN - samples % GEN
            val len = TRIM + samples + pad + TRIM
            val padL = FloatArray(len)
            val padR = FloatArray(len)
            left.copyInto(padL, TRIM, 0, samples)
            right.copyInto(padR, TRIM, 0, samples)

            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, 4, DIM_F.toLong(), DIM_T.toLong())

            var k = 0
            while (k * GEN < TRIM + samples) {
                val start = k * GEN
                stftChunk(padL, padR, start, len, mixRe, mixIm)
                pack(backing.asFloatBuffer(), mixRe, mixIm)
                try {
                    // A fresh view per run rather than rewinding one: FloatBuffer's
                    // own rewind() and position(int) are Java 9 covariant overrides
                    // that Android's FloatBuffer does not declare, so they compile
                    // against the current SDK and throw NoSuchMethodError on the
                    // API 28 devices this has to run on. asFloatBuffer() hands back
                    // a view at position 0 and has been there since API 1.
                    OnnxTensor.createTensor(environment, backing.asFloatBuffer(), shape).use { tensor ->
                        session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
                            // The output buffer, not `outputs.get(0).value`: that
                            // property boxes the [1, 4, 2048, 256] result into one
                            // FloatArray per cell when only magnitudes are read.
                            // Read inside use{} — the view dies with the outputs.
                            reduce((outputs.get(0) as OnnxTensor).floatBuffer, mixRe, mixIm, k, frames, out)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MDX inference failed", e)
                    return@synchronized null
                }
                k++
            }
            out
        }

        /**
         * Streams the vocal stem: for each model chunk, hands [sink] the vocals for `GEN` input
         * samples (left, right, start index, count). The model predicts the vocal spectrogram
         * directly, so its output is inverted back to a waveform; the accompaniment is the mix
         * minus this. False when inference fails.
         */
        fun vocals(
            left: FloatArray,
            right: FloatArray,
            session: OrtSession,
            isCurrent: () -> Boolean,
            sink: (FloatArray, FloatArray, Int, Int) -> Unit,
        ): Boolean = synchronized(this) {
            val samples = left.size
            if (samples == 0) return@synchronized false
            val pad = GEN - samples % GEN
            val len = TRIM + samples + pad + TRIM
            val padL = FloatArray(len)
            val padR = FloatArray(len)
            left.copyInto(padL, TRIM, 0, samples)
            right.copyInto(padR, TRIM, 0, samples)

            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, 4, DIM_F.toLong(), DIM_T.toLong())
            val outRe = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }
            val outIm = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }
            val wave = FloatArray((DIM_T - 1) * HOP)
            val outLeft = FloatArray(GEN)
            val outRight = FloatArray(GEN)

            var k = 0
            while (k * GEN < TRIM + samples) {
                if (!isCurrent()) return@synchronized false
                val start = k * GEN
                stftChunk(padL, padR, start, len, mixRe, mixIm)
                pack(backing.asFloatBuffer(), mixRe, mixIm)
                try {
                    OnnxTensor.createTensor(environment, backing.asFloatBuffer(), shape).use { tensor ->
                        session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
                            val target = (outputs.get(0) as OnnxTensor).floatBuffer
                            for (channel in 0..1) {
                                for (frame in 0 until DIM_T) {
                                    for (bin in 0 until DIM_F) {
                                        val base = bin * DIM_T + frame
                                        outRe[channel][frame][bin] = target.get(base + channel * DIM_F * DIM_T)
                                        outIm[channel][frame][bin] = target.get(base + (2 + channel) * DIM_F * DIM_T)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MDX inference failed", e)
                    return@synchronized false
                }
                for (channel in 0..1) {
                    SpectralMath.istft(outRe[channel], outIm[channel], DIM_T, DIM_F, HOP, wave)
                    // Chunk sample TRIM is input sample k * GEN; the half windows either side are dropped.
                    wave.copyInto(if (channel == 0) outLeft else outRight, 0, TRIM, TRIM + GEN)
                }
                val count = minOf(GEN, samples - start)
                sink(outLeft, outRight, start, count)
                k++
            }
            true
        }

        /** One chunk's complex STFT per channel, torch center=True semantics. */
        private fun stftChunk(
            padL: FloatArray,
            padR: FloatArray,
            start: Int,
            len: Int,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (channel in 0..1) {
                val src = if (channel == 0) padL else padR
                val dstRe = mixRe[channel]
                val dstIm = mixIm[channel]
                for (frame in 0 until DIM_T) {
                    val base = start + frame * HOP - TRIM
                    for (n in 0 until N_FFT) {
                        var idx = base + n
                        if (idx < 0) idx = -idx
                        else if (idx >= len) idx = 2 * (len - 1) - idx
                        workRe[n] = (if (idx < 0 || idx >= len) 0f else src[idx]) * HANN[n]
                        workIm[n] = 0f
                    }
                    fft6144(workRe, workIm)
                    val frameRe = dstRe[frame]
                    val frameIm = dstIm[frame]
                    for (bin in 0 until DIM_F) {
                        frameRe[bin] = workRe[bin]
                        frameIm[bin] = workIm[bin]
                    }
                }
            }
        }

        /**
         * 6144-point FFT: three radix-2 [fft]s over the decimated sequences plus one radix-3
         * combine with precomputed twiddles (verified bit-close against numpy, max err 2.4e-13).
         */
        private fun fft6144(re: FloatArray, im: FloatArray) {
            for (s in 0..2) {
                for (r in 0 until M) {
                    decRe[r] = re[3 * r + s]
                    decIm[r] = im[3 * r + s]
                }
                fft(decRe, decIm)
                decRe.copyInto(accRe[s])
                decIm.copyInto(accIm[s])
            }
            for (k in 0 until N_FFT) {
                val m = k % M
                var sr = 0f
                var si = 0f
                for (s in 0..2) {
                    val wr = TWIDDLE[(s * N_FFT + k) * 2]
                    val wi = TWIDDLE[(s * N_FFT + k) * 2 + 1]
                    val ar = accRe[s][m]
                    val ai = accIm[s][m]
                    sr += wr * ar - wi * ai
                    si += wr * ai + wi * ar
                }
                re[k] = sr
                im[k] = si
            }
        }

        /** Iterative radix-2 FFT, in place, over the array's (power-of-two) length. */
        private fun fft(re: FloatArray, im: FloatArray) {
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

        /**
         * Packs `[leftReal, rightReal, leftImag, rightImag]` — the reference's
         * channel order — dropping the top bin the model never saw.
         */
        private fun pack(
            into: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (c in 0..3) {
                val channel = c and 1
                val src = if (c < 2) mixRe[channel] else mixIm[channel]
                for (bin in 0 until DIM_F) {
                    for (frame in 0 until DIM_T) {
                        into.put(src[frame][bin])
                    }
                }
            }
        }

        /** `|vocals| / |mix|` per cell, averaged over channels, clamped. */
        private fun reduce(
            target: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
            chunk: Int,
            frames: Int,
            out: Array<FloatArray>,
        ) {
            for (frame in 0 until DIM_T) {
                val t = chunk * STRIDE + frame - LEAD
                if (t < 0 || t >= frames) continue
                val row = out[t]
                for (bin in 0 until DIM_F) {
                    val lr = mixRe[0][frame][bin]
                    val li = mixIm[0][frame][bin]
                    val rr = mixRe[1][frame][bin]
                    val ri = mixIm[1][frame][bin]
                    val ml = sqrt(lr * lr + li * li)
                    val mr = sqrt(rr * rr + ri * ri)
                    if (ml <= 1e-6f || mr <= 1e-6f) continue
                    val base = bin * DIM_T + frame
                    val vl = sqrt(
                        target.get(base) * target.get(base) +
                            target.get(base + 2 * DIM_F * DIM_T) *
                                target.get(base + 2 * DIM_F * DIM_T),
                    )
                    val vr = sqrt(
                        target.get(base + DIM_F * DIM_T) * target.get(base + DIM_F * DIM_T) +
                            target.get(base + 3 * DIM_F * DIM_T) *
                                target.get(base + 3 * DIM_F * DIM_T),
                    )
                    row[bin] = ((vl / ml + vr / mr) * 0.5f).coerceIn(0f, 1f)
                }
            }
        }
    }

    /**
     * Separates the vocals of a whole track at [VocalSpectrogram.sampleRate], streaming them to
     * [sink] as (left, right, startSample, count) pieces. False when the model is unavailable,
     * inference fails, or [isCurrent] turns false.
     */
    fun separateVocals(
        left: FloatArray,
        right: FloatArray,
        isCurrent: () -> Boolean,
        sink: (FloatArray, FloatArray, Int, Int) -> Unit,
    ): Boolean {
        val active = session() ?: return false
        return MdxFront.vocals(left, right, active, isCurrent, sink)
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            runCatching { sessionOptions?.close() }
            sessionOptions = null
        }
    }

    companion object {
        private const val TAG = "ExhaleVocalTracker"
        private const val MODEL_ASSET = "uvr_mdxnet_9482.onnx"
        private const val STALE_MODEL_ASSET = "vocals_umxhq_int8.onnx"
        private const val INFERENCE_THREADS = 1

        /** The model's fixed input width, ~22.8 s, chosen upstream to cover a transition overlap. */
        const val FIXED_FRAMES = 960

        /** The band a vocal actually occupies; below and above it the mask says little. */
        private const val LOW_HZ = 200.0
        private const val HIGH_HZ = 4000.0
    }
}
