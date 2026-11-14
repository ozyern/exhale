/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

enum class ReverbType { AUTO, ROOM, PLATE, HALL }

enum class DelayType { AUTO, STEREO, PING_PONG, TAPE, FILTERED, SLAPBACK }

internal class ReverbProgram(
    val preDelayMs: Int,
    val decaySeconds: Float,
    val highCutHz: Float,
    val diffusion: Float,
    val earlyMix: Float,
    val lateGain: Float,
    val combFrames: IntArray,
    val inputDiffusionFrames: IntArray,
    val outputDiffusionFrames: IntArray,
    val earlyTapsMs: IntArray,
) {
    companion object {
        fun forType(type: ReverbType): ReverbProgram = when (type) {
            ReverbType.AUTO, ReverbType.ROOM -> Room
            ReverbType.PLATE -> Plate
            ReverbType.HALL -> Hall
        }

        private val Room = ReverbProgram(
            preDelayMs = 8,
            decaySeconds = 4f,
            highCutHz = 5_200f,
            diffusion = 0.35f,
            earlyMix = 0.38f,
            lateGain = 0.8f,
            combFrames = intArrayOf(743, 1111, 1297, 1531),
            inputDiffusionFrames = intArrayOf(),
            outputDiffusionFrames = intArrayOf(151, 337),
            earlyTapsMs = intArrayOf(3, 7, 11, 17),
        )
        private val Plate = ReverbProgram(
            preDelayMs = 20,
            decaySeconds = 6f,
            highCutHz = 7_000f,
            diffusion = 0.6f,
            earlyMix = 0.08f,
            lateGain = 0.85f,
            combFrames = intArrayOf(1557, 1617, 1491, 1422),
            inputDiffusionFrames = intArrayOf(142, 379),
            outputDiffusionFrames = intArrayOf(225, 556, 441),
            earlyTapsMs = intArrayOf(5, 9, 13, 19),
        )
        private val Hall = ReverbProgram(
            preDelayMs = 34,
            decaySeconds = 8f,
            highCutHz = 3_900f,
            diffusion = 0.52f,
            earlyMix = 0.2f,
            lateGain = 0.75f,
            combFrames = intArrayOf(1913, 2237, 2473, 2953),
            inputDiffusionFrames = intArrayOf(279),
            outputDiffusionFrames = intArrayOf(443, 613, 809),
            earlyTapsMs = intArrayOf(12, 24, 41, 59),
        )
    }
}
