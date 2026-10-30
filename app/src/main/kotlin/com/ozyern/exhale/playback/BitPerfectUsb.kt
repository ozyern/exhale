/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import timber.log.Timber

/**
 * Bit-perfect playback to a USB DAC (Android 14+), the way UAPP and Poweramp's Hi-Res output do it.
 *
 * Normally everything an app plays goes through Android's mixer: resampled to the phone's output
 * rate (often 48 kHz, so a 44.1 kHz song is resampled), mixed with other sounds and scaled by the
 * system volume. With a bit-perfect mixer attribute set on the DAC, the framework opens the DAC
 * at the track's own rate and format and hands the samples over untouched.
 *
 * Two consequences the setting has to be honest about: the system volume no longer scales the
 * signal (the DAC's own volume does, or it plays at full level), and while it is active other
 * apps' sounds do not mix into the DAC.
 *
 * The mixer can only be bit-perfect for a format the app actually writes, and Exhale's sink writes
 * float or 16-bit PCM, so those are the two encodings considered — float first, since that is what
 * the processing chain produces with no quantisation at all.
 */
object BitPerfectUsb {

    private val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** The DAC a bit-perfect mixer is currently set on, so it can be cleared again. */
    private var appliedOn: AudioDeviceInfo? = null

    /**
     * The encoding the DAC is bit-perfect at right now ([C.ENCODING_PCM_FLOAT] or
     * [C.ENCODING_PCM_16BIT]), or null when bit-perfect is not active. The sink reads this to write
     * exactly that encoding.
     */
    @Volatile
    var activeEncoding: Int? = null
        private set

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun usbOutput(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    /**
     * Sets, moves or clears the bit-perfect mixer for a track at [sampleRate] Hz. Called when the
     * setting changes, when a DAC comes or goes, and when a track with a new format is about to
     * open its AudioTrack.
     */
    fun update(audioManager: AudioManager, enabled: Boolean, sampleRate: Int) {
        if (!supported) return
        runCatching { updateApi34(audioManager, enabled, sampleRate) }
            .onFailure { Timber.w(it, "Bit-perfect USB update failed") }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateApi34(audioManager: AudioManager, enabled: Boolean, sampleRate: Int) {
        val device = if (enabled && sampleRate > 0) usbOutput(audioManager) else null
        val pick = device?.let { bestMixer(audioManager, it, sampleRate) }
        if (device == null || pick == null) {
            clear(audioManager)
            return
        }
        if (appliedOn != null && appliedOn?.id != device.id) clear(audioManager)
        if (audioManager.setPreferredMixerAttributes(mediaAttributes, device, pick)) {
            appliedOn = device
            activeEncoding = when (pick.format.encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> C.ENCODING_PCM_FLOAT
                else -> C.ENCODING_PCM_16BIT
            }
            Timber.i("Bit-perfect USB: ${pick.format.sampleRate} Hz, encoding ${pick.format.encoding}")
        } else {
            clear(audioManager)
        }
    }

    /** The DAC's bit-perfect mode for this rate, stereo, float preferred over 16-bit. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun bestMixer(audioManager: AudioManager, device: AudioDeviceInfo, sampleRate: Int): AudioMixerAttributes? =
        audioManager.getSupportedMixerAttributes(device)
            .filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRate &&
                    it.format.channelMask == AudioFormat.CHANNEL_OUT_STEREO &&
                    it.format.encoding in WritableEncodings
            }
            .minByOrNull { WritableEncodings.indexOf(it.format.encoding) }

    private fun clear(audioManager: AudioManager) {
        val device = appliedOn ?: run { activeEncoding = null; return }
        if (supported) {
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
        }
        appliedOn = null
        activeEncoding = null
    }

    private val WritableEncodings = listOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)
}
