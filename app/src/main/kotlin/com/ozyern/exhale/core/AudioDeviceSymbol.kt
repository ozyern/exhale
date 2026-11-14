/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.composables.sfsymbols.SfSymbol
import com.ozyern.exhale.constants.AudioDeviceIconsKey
import com.ozyern.exhale.constants.AudioDeviceNamesKey
import com.ozyern.exhale.utils.rememberPreference
import org.json.JSONObject

/** Symbols offered by the icon picker, most likely first; names the bundled library lacks are dropped (sf() throws on them). */
val AudioDeviceSymbolChoices: List<String> by lazy { AudioDeviceSymbolCandidates.filter { SfSymbol.named(it) != null } }

private val AudioDeviceSymbolCandidates = listOf(
    "headphones", "airpods", "airpodspro", "airpodsmax", "beats.headphones", "beats.earphones.gen1",
    "earbuds", "hifispeaker.fill", "hifispeaker.2.fill", "homepod.fill", "homepod.2.fill",
    "speaker.wave.2.fill", "car.fill", "tv.fill", "watch.analog", "iphone", "ipad", "laptopcomputer",
    "cable.connector", "hearingdevice.ear", "waveform", "music.note",
)

/** Per-device icon overrides; a device with no pick falls back to [audioDeviceSymbol]. */
class AudioDeviceIcons(private val raw: MutableState<String>) {
    private val picks: Map<String, String>
        get() = runCatching {
            val json = JSONObject(raw.value)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())

    fun pickFor(name: String?): String? = name?.let { picks[it] }

    fun symbol(name: String?, type: AudioDeviceType?): String =
        pickFor(name)?.takeIf { SfSymbol.named(it) != null } ?: audioDeviceSymbol(name, type)

    /** Saves [symbol] for [name]; null clears the pick back to the automatic icon. */
    fun set(name: String, symbol: String?) {
        val json = JSONObject(picks)
        if (symbol == null) json.remove(name) else json.put(name, symbol)
        raw.value = json.toString()
    }
}

@Composable
fun rememberAudioDeviceIcons(): AudioDeviceIcons {
    val raw = rememberPreference(AudioDeviceIconsKey, "{}")
    return remember(raw) { AudioDeviceIcons(raw) }
}

/** Persisted display-name overrides, keyed by the system-reported device name. */
class AudioDeviceNames(private val raw: MutableState<String>) {
    private val labels: Map<String, String>
        get() = runCatching {
            val json = JSONObject(raw.value)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())

    fun displayName(systemName: String?): String {
        val name = systemName.orEmpty()
        return labels[name]?.takeIf { it.isNotBlank() } ?: name
    }

    fun set(systemName: String, displayName: String?) {
        val json = JSONObject(labels)
        val cleaned = displayName?.trim().orEmpty()
        if (cleaned.isBlank() || cleaned == systemName) json.remove(systemName)
        else json.put(systemName, cleaned)
        raw.value = json.toString()
    }
}

@Composable
fun rememberAudioDeviceNames(): AudioDeviceNames {
    val raw = rememberPreference(AudioDeviceNamesKey, "{}")
    return remember(raw) { AudioDeviceNames(raw) }
}

/** SF Symbol name for an output device: product name first (Galaxy Buds, Sony WH, JBL...), then its type. */
fun audioDeviceSymbol(name: String?, type: AudioDeviceType? = null): String {
    val n = name.orEmpty().lowercase()
    fun has(vararg keys: String) = keys.any { it in n }
    return when {
        has("android auto", "carplay", "car ", " car", "vehicle", "toyota", "honda", "hyundai", "kia", "tata", "mahindra", "bmw", "audi", "mercedes", "ford") -> "car.fill"
        has("watch", "wear os", "pixel watch", "galaxy fit", "mi band", "smart band") -> "watch.analog"
        has("tv", "bravia", "chromecast", "android tv", "fire tv", "mi box", "roku", "shield") -> "tv.fill"
        has("soundbar", "sonos", "bose home", "boombox", "echo", "nest audio", "nest mini", "home mini") -> "hifispeaker.2.fill"
        has("buds", "earbud", "airpods", "pods", "ear (", "nothing ear", "wf-", "tws", "earphone", "neckband", "bullets", "rockerz", "airdopes") -> "airpods"
        has("headphone", "headset", "wh-", "xm4", "xm5", "quietcomfort", "jabra", "soundcore", "boat", "px7") -> "headphones"
        isSpeaker(name) -> "hifispeaker.fill"
        else -> when (type) {
            // Earbuds were matched by name above; an unrecognised Bluetooth device is more likely over-ear headphones.
            AudioDeviceType.BLUETOOTH, AudioDeviceType.WIRED_HEADPHONES, AudioDeviceType.USB_HEADSET, null -> "headphones"
            AudioDeviceType.HDMI -> "tv.fill"
            AudioDeviceType.EXTERNAL_SPEAKER -> "hifispeaker.fill"
            AudioDeviceType.PHONE_SPEAKER -> "iphone"
        }
    }
}
