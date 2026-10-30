/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import org.json.JSONArray
import java.util.Locale

/**
 * The immersive formats Exhale recognises in what it plays. YouTube Music only ever streams stereo;
 * these come from music on the PC (a Lossless source folder), where Dolby Atmos music is sold and kept
 * as Dolby Digital Plus (E-AC-3 carrying Atmos' objects) or Dolby TrueHD.
 */
enum class ImmersiveFormat(val label: String) {
    DolbyAtmos("Dolby Atmos"),
    DolbyTrueHD("Dolby TrueHD"),
    DolbyDigitalPlus("Dolby Digital Plus"),
    DolbyDigital("Dolby Digital"),
    Eclipsa("Eclipsa Audio"),
}

/** The track mpv has open, as its demuxer and decoder describe it. */
data class SourceAudio(
    val codec: String?,
    val profile: String?,
    val channels: Int?,
    val format: ImmersiveFormat?,
) {
    /** More than two channels in the music itself: an upmix would only take it apart. */
    val isMultichannel: Boolean get() = (channels ?: 2) > 2
}

object ImmersiveAudio {
    /**
     * The selected audio track out of mpv's `track-list`. ffmpeg names Atmos in the codec profile —
     * "Dolby Digital Plus + Dolby Atmos" for E-AC-3 with Atmos (JOC), "Dolby TrueHD + Dolby Atmos" —
     * so that is what tells Atmos from a plain 5.1 or 7.1 Dolby track.
     */
    fun fromTrackList(json: String?): SourceAudio? {
        if (json.isNullOrBlank()) return null
        val tracks = runCatching { JSONArray(json) }.getOrNull() ?: return null
        val track = (0 until tracks.length())
            .map { tracks.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "audio" && it.optBoolean("selected") }
            ?: return null
        val codec = track.optString("codec").ifBlank { null }
        val profile = track.optString("codec-profile").ifBlank { null }
        val channels = track.optInt("demux-channel-count", 0).takeIf { it > 0 }
        return SourceAudio(codec, profile, channels, classify(codec, profile))
    }

    fun classify(codec: String?, profile: String?): ImmersiveFormat? {
        val c = codec?.lowercase(Locale.US).orEmpty()
        val p = profile?.lowercase(Locale.US).orEmpty()
        return when {
            "atmos" in p -> ImmersiveFormat.DolbyAtmos
            c == "truehd" || c == "mlp" -> ImmersiveFormat.DolbyTrueHD
            c == "eac3" -> ImmersiveFormat.DolbyDigitalPlus
            c == "ac3" -> ImmersiveFormat.DolbyDigital
            "iamf" in c || "iamf" in p -> ImmersiveFormat.Eclipsa
            else -> null
        }
    }

    /**
     * Eclipsa Audio's renditions on YouTube: IAMF, with codec strings like `iamf.001.001.Opus`.
     * Exhale's audio engine can unpack IAMF but has no renderer for it, so these are never picked
     * while a regular rendition of the same song exists (see StreamResolver).
     */
    fun isEclipsa(mimeType: String): Boolean = "iamf" in mimeType.lowercase(Locale.US)

    /** What mpv passes through untouched to an HDMI receiver or soundbar, when Dolby passthrough is on. */
    const val PassthroughCodecs = "ac3,eac3,truehd"
}
