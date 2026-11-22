/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.playback

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import timber.log.Timber
import java.util.Locale

/**
 * What this phone can decode, asked once and remembered. One question: **Dolby Atmos**.
 *
 * E-AC-3 is licensed, so its decoder ships with the vendor's image (`c2.dolby.eac3.decoder` and
 * the like) rather than with Android, and phones that look alike from here differ. Asked before a
 * source is asked for an Atmos rendition, because a stream no renderer takes doesn't fail
 * cleanly: the track selector declines it and the song skips itself with nothing to say why.
 *
 * The answer is Media3's own, since its renderer is the one that acts on it: [MediaCodecUtil]
 * applies the same per-device workarounds and blocklists as the selector it feeds. The platform's
 * `REGULAR_CODECS` list is only the fallback for a query that throws; `ALL_CODECS` would count
 * decoders that only exist for special configurations, which the renderer is never given.
 *
 * Both mimes are asked for: a plain E-AC-3 decoder plays a JOC stream too (its 5.1 core), which is
 * a lesser render but not a failure, and Media3 falls back to one for exactly that reason.
 *
 * Passthrough to an AV receiver is not counted: it depends on the current route, changes as
 * headphones come and go, and is never available on a phone's speaker or over Bluetooth.
 */
object DeviceCodecs {

    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    /** Every Dolby mime that the phone, not Exhale, should render: E-AC-3, its JOC objects, AC-4. */
    private val DOLBY_RENDERED = setOf("audio/eac3-joc", "audio/eac3", "audio/ac4", "audio/ac3")

    @Volatile
    private var probed: Boolean? = null

    /** Stands in for a device in tests. */
    @Volatile
    internal var forced: Boolean? = null

    /** Whether an E-AC-3 (JOC) stream finds a decoder here. Cached: the codec list can't change. */
    val playsDolbyAtmos: Boolean
        get() = forced ?: probed ?: probe().also { probed = it }

    /** Whether a decoded stream of [mime] is Dolby's to render rather than Exhale's effects'. */
    fun isDolbyRendered(mime: String?): Boolean = mime?.lowercase(Locale.ROOT) in DOLBY_RENDERED

    /**
     * Yes on anything that can't be established: a "no" on a capable phone drops it from the tier it
     * pays for, while a "yes" only keeps the behaviour from before this was asked.
     */
    private fun probe(): Boolean {
        media3Decoders()?.let { names ->
            Timber.d("Dolby Atmos decoders (Media3): %s", names.ifEmpty { listOf("none") })
            return names.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.lowercase(Locale.ROOT) in DOLBY_MIMES }
            }.map { it.name }
        }.onSuccess {
            Timber.d("Dolby Atmos decoders (platform): %s", it.ifEmpty { listOf("none") })
        }.map { it.isNotEmpty() }.getOrElse {
            Timber.w(it, "Could not read the codec list; assuming Dolby Atmos plays")
            true
        }
    }

    @OptIn(UnstableApi::class)
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }.map { it.name }.distinct()
    }.getOrElse {
        Timber.w(it, "Media3 could not list the Dolby decoders")
        null
    }
}
