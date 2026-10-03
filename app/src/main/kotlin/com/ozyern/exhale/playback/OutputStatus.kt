/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C

/** What the phone's audio track was actually opened with — read, not assumed. */
data class OutputStatus(
    val sampleRateHz: Int,
    val encoding: Int,
    val offload: Boolean,
) {
    /** "16-bit PCM", "32-bit float", or the bitstream it is passing through. */
    val encodingLabel: String
        get() = when (encoding) {
            C.ENCODING_PCM_16BIT -> "16-bit PCM"
            C.ENCODING_PCM_24BIT -> "24-bit PCM"
            C.ENCODING_PCM_32BIT -> "32-bit PCM"
            C.ENCODING_PCM_FLOAT -> "32-bit float"
            C.ENCODING_PCM_8BIT -> "8-bit PCM"
            else -> if (offload) "Offloaded" else "Encoded"
        }
}
