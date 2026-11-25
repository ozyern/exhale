/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

fun formatSampleRate(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000.0)

/** What Windows actually opened for Exhale, as mpv reports it once a song is playing. */
data class AudioOutput(
    val channels: Int,
    val sampleRate: Int?,
    val codec: String?,
    val driver: String?,
    val exclusive: Boolean,
) {
    val isSurround: Boolean get() = channels >= 6

    val layout: String
        get() = when (channels) {
            8 -> "7.1"
            6 -> "5.1"
            2 -> "Stereo"
            1 -> "Mono"
            else -> "$channels ch"
        }

    fun describe(): String = listOfNotNull(
        layout,
        sampleRate?.let(::formatSampleRate),
        codec?.uppercase(Locale.US),
        driver?.let { "${it.uppercase(Locale.US)} ${if (exclusive) "exclusive" else "shared"}" },
    ).joinToString(" · ")
}

object AudioOutputStatus {
    private val _state = MutableStateFlow<AudioOutput?>(null)
    val state: StateFlow<AudioOutput?> = _state.asStateFlow()

    internal fun publish(output: AudioOutput?) {
        _state.value = output
    }
}

/**
 * Dolby Access, the Store app that turns on Dolby Atmos for Headphones and home theatre. It does its
 * work inside Windows' shared audio path, so the integration is: find it, open it, and hand it
 * something worth spatialising (see [AudioEffects.outputChannels]).
 */
object DolbyAccess {
    private const val StoreProductId = "9N0866FS04W8"

    /** The package family name (e.g. `DolbyLaboratories.DolbyAccess_rz1tebttyb220`), from the per-user package folder. */
    val familyName: String? by lazy {
        val packages = File(System.getenv("LOCALAPPDATA") ?: return@lazy null, "Packages")
        packages.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("DolbyLaboratories.DolbyAccess_") }?.name
    }

    val isInstalled: Boolean get() = familyName != null

    /** Opens Dolby Access, or its Store page when it isn't installed. */
    fun open(): Boolean {
        val family = familyName
        return if (family != null) launch("explorer.exe", "shell:AppsFolder\\$family!App")
        else launch("explorer.exe", "ms-windows-store://pdp/?ProductId=$StoreProductId")
    }

    /** Settings → System → Sound, where spatial sound is chosen per device. */
    fun openSoundSettings(): Boolean = launch("explorer.exe", "ms-settings:sound")

    private fun launch(vararg command: String): Boolean = runCatching {
        ProcessBuilder(*command).start()
        true
    }.getOrDefault(false)
}
