/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What the bundled libmpv can do with immersive formats: its mpv and ffmpeg versions, and whether
 * its decoders and demuxers include Dolby (AC-3, E-AC-3, TrueHD) and IAMF, Eclipsa Audio's format.
 * EXHALE_SMOKE=1 to run.
 */
class ImmersiveCapabilityProbe {
    @Test
    fun capabilities() {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        Mpv(object : Mpv.Listener {}).use { mpv ->
            println("mpv-version: ${mpv.getProperty("mpv-version")}")
            println("ffmpeg-version: ${mpv.getProperty("ffmpeg-version")}")
            val decoders = mpv.getProperty("decoder-list").orEmpty()
            for (codec in listOf("ac3", "eac3", "truehd", "mlp", "iamf", "opus", "aac", "flac")) {
                println("decoder $codec: ${Regex("\"codec\":\"$codec\"").containsMatchIn(decoders)}")
            }
            val demuxers = mpv.getProperty("demuxer-lavf-list").orEmpty()
            for (demuxer in listOf("eac3", "ac3", "truehd", "iamf", "mov", "matroska")) {
                println("demuxer $demuxer: ${Regex("(^|[,\\[\" ])$demuxer([,\\]\" ]|$)").containsMatchIn(demuxers)}")
            }
            val encoders = mpv.getProperty("encoder-list").orEmpty()
            println("encoder eac3: ${Regex("\"codec\":\"eac3\"").containsMatchIn(encoders)}  (encoding available: ${encoders.isNotBlank()})")
        }
    }
}
