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

/** Native log-mel front end for Beat This. Its sample rate and tensor shape must match the trained model. */
object MelSpectrogram {

    /** True when the native library loaded. Analysis is optional, so this is a fact, not a fault. */
    val available: Boolean = runCatching { System.loadLibrary("exhale_analysis") }.isSuccess

    /** Mel bands per frame; the model's input width. */
    val mels: Int by lazy { if (available) nativeMelCount() else 128 }

    /** The only sample rate the front end accepts. */
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 22_050.0 }

    /** Samples between frame starts; 441 at 22,050 Hz is exactly 20 ms. */
    val hop: Int by lazy { if (available) nativeHop() else 441 }

    /** Frames per second of output, which is what beat times are derived from. */
    val frameRate: Double get() = sampleRate / hop

    /** Computes a spectrogram; returns null when native analysis is unavailable or input is invalid. */
    fun compute(samples: FloatArray, sampleRate: Double = this.sampleRate): Spectrogram? {
        if (!available || samples.isEmpty()) return null
        val values = nativeCompute(samples, sampleRate)
        if (values.isEmpty()) return null
        return Spectrogram(values = values, frames = values.size / mels, mels = mels)
    }

    /**
     * A flattened spectrogram, row-major over frames: frame `f` band `b` lives at
     * `f * mels + b`. Flat because the only consumer hands it straight to an ONNX tensor of
     * shape `[1, frames, mels]`.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val mels: Int) {
        /** Seconds covered, useful for turning frame indices back into track times. */
        val durationSeconds: Double get() = frames / (sampleRate / hop)

        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Spectrogram && frames == other.frames && mels == other.mels &&
                    values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + mels
    }

    /** Resamples mono PCM for [compute], preserving frequencies that decimation would alias. */
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = sampleRate): FloatArray? {
        if (!available || samples.isEmpty() || inputRate <= 0 || outputRate <= 0) return null
        return nativeResample(samples, inputRate, outputRate).takeIf { it.isNotEmpty() }
    }

    @JvmStatic private external fun nativeCompute(samples: FloatArray, sampleRate: Double): FloatArray
    @JvmStatic private external fun nativeResample(
        samples: FloatArray,
        inputRate: Double,
        outputRate: Double,
    ): FloatArray
    @JvmStatic private external fun nativeMelCount(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
}
