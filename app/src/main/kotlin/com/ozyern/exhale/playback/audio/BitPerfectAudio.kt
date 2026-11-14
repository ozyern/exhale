/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.media.AudioAttributes as PlatformAudioAttributes

/** Requests a matching bit-perfect PCM mixer on supported Android 14+ USB routes. */
object BitPerfectAudio {

    private val platformAttributes: PlatformAudioAttributes by lazy {
        PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    @SuppressLint("NewApi")
    private fun usbOutputDevice(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.getAudioDevicesForAttributes(platformAttributes)
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

    @SuppressLint("NewApi")
    fun isHardwareBitPerfectAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        val device = usbOutputDevice(audioManager) ?: return false
        return audioManager.getSupportedMixerAttributes(device)
            .any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
    }

    /** [encoding] uses Android AudioFormat's PCM constants. */
    @SuppressLint("NewApi")
    fun requestBitPerfectMixer(context: Context, sampleRate: Int, encoding: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        val device = usbOutputDevice(audioManager) ?: return false
        val attributes = platformAttributes
        val mixerAttributes = audioManager.getSupportedMixerAttributes(device)
            .firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRate &&
                    it.format.encoding == encoding
            } ?: return false
        return audioManager.setPreferredMixerAttributes(attributes, device, mixerAttributes)
    }

    @SuppressLint("NewApi")
    fun clearBitPerfectMixer(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val device = usbOutputDevice(audioManager) ?: return
        audioManager.clearPreferredMixerAttributes(platformAttributes, device)
    }
}
