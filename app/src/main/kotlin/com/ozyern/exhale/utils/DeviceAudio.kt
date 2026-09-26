/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build

/**
 * What the phone itself does to the sound, and how not to fight it.
 *
 * OnePlus and OPPO ship OReality (Dolby on older builds) in the audio HAL. It attaches to whichever
 * app has opened an audio-effect session — Exhale does, on every track — and then widens and
 * equalises the stream on its way out. Running our own spatial stage on top of that is two
 * virtualizers in series: the stage collapses inward, the centre goes hollow, and the result is the
 * "weird" sound those devices get from music apps that assume they are alone.
 *
 * So on those devices our own spatializer starts off, and the app points at the system's panel
 * instead. Everywhere else it starts on, because there is nothing else doing the work.
 */
object DeviceAudio {
    /** OnePlus, OPPO and Realme all run the same OPlus audio stack. */
    val isOplusDevice: Boolean by lazy {
        val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        listOf("oneplus", "oppo", "realme").any { it in vendor }
    }

    /** What the spatial-audio switch reads before anyone has touched it. */
    val defaultSpatialAudio: Boolean get() = !isOplusDevice

    /**
     * Opens the phone's own effect panel — OReality on OPlus, the stock equaliser elsewhere —
     * bound to the session Exhale is playing on, so what it changes is this app's sound.
     * Returns false when the device has no such panel.
     */
    /**
     * Opens the phone's audio-effect panel for our session - the vendor's, where there is one.
     *
     * `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` is an open door: MusicFX, the stock AOSP
     * equaliser, answers it on almost every device, and on a OnePlus it answers it *alongside*
     * OReality. Handing the intent to the system picked MusicFX - a grey 2011 equaliser that does
     * nothing to a stream OReality is already processing - which is what this row was opening.
     *
     * So the handlers are ranked instead of guessed at: the vendor's panel first, anything
     * third-party next, and AOSP's own last. Nothing here is device-specific beyond the
     * manufacturer prefixes, so a Dirac panel on a Realme or a Sony panel on an Xperia wins the
     * same way OReality does here.
     */
    fun openSystemEffects(context: Context, audioSessionId: Int): Boolean {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val handlers = runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())

        val best = handlers.minByOrNull { effectPanelRank(it.activityInfo.packageName) }
        if (best != null) {
            intent.setClassName(best.activityInfo.packageName, best.activityInfo.name)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
            // The explicit component was refused (an unexported vendor activity, usually); the
            // implicit intent may still be allowed.
            intent.component = null
        }

        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Lower is better. */
    private fun effectPanelRank(packageName: String): Int {
        val pkg = packageName.lowercase()
        return when {
            VendorEffectPrefixes.any { pkg.startsWith(it) || pkg.contains(it) } -> 0
            pkg.startsWith("com.android.") || pkg.contains("musicfx") -> 3
            else -> 1
        }
    }

    private val VendorEffectPrefixes = listOf(
        "com.oplus",
        "com.oppo",
        "com.coloros",
        "com.realme",
        "dirac",
        "com.dolby",
        "com.sonymobile",
        "com.samsung",
        "com.xiaomi",
        "com.miui",
    )
}
