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
 * The mixer can only be bit-perfect for a format the app actually writes. Exhale writes float,
 * 32-bit integer (carried through Media3's float path, see [Int32Carriage]) or 16-bit PCM, in that
 * order of preference: the first two pass any source up to 24-bit through unchanged.
 */
object BitPerfectUsb {

    private val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** The DAC a bit-perfect mixer is currently set on, so it can be cleared again. */
    private var appliedOn: AudioDeviceInfo? = null

    /**
     * The encoding the DAC is bit-perfect at right now ([C.ENCODING_PCM_FLOAT],
     * [C.ENCODING_PCM_32BIT] or [C.ENCODING_PCM_16BIT]), or null when bit-perfect is not active. The
     * sink reads this to write exactly that encoding.
     */
    @Volatile
    var activeEncoding: Int? = null
        private set(value) {
            field = value
            active.value = value != null
        }

    /**
     * Whether a DAC is taking the stream bit-perfect right now, for everything else that must then
     * leave the samples alone: see where the service reads it.
     */
    val active = kotlinx.coroutines.flow.MutableStateFlow(false)

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
    /**
     * The source's bits per sample, from the file's own format (16 for lossy and CD-quality, 24 for
     * most hi-res). Kept so a call that only knows the rate — a DAC plugged in, the setting turned on —
     * still chooses the right mode.
     */
    @Volatile
    var sourceBits: Int = 16

    /** What was last applied, so a track that changes nothing makes no call to the framework. */
    @Volatile private var appliedRate = 0
    @Volatile private var appliedBits = 0

    /** Why the DAC is not bit-perfect right now, for the output's status line; null when it is or is off. */
    @Volatile
    var unavailableReason: String? = null
        private set

    fun update(audioManager: AudioManager, enabled: Boolean, sampleRate: Int, bits: Int = sourceBits) {
        if (!supported) return
        sourceBits = bits
        runCatching { updateApi34(audioManager, enabled, sampleRate, bits) }
            .onFailure { Timber.w(it, "Bit-perfect USB update failed") }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Synchronized
    private fun updateApi34(audioManager: AudioManager, enabled: Boolean, sampleRate: Int, bits: Int) {
        val device = if (enabled && sampleRate > 0) usbOutput(audioManager) else null
        if (device == null) {
            unavailableReason = null
            clear(audioManager)
            return
        }
        // Already set for exactly this: called on every track configure, and the framework call is
        // a round trip to the audio server that briefly reroutes the output.
        if (appliedOn?.id == device.id && appliedRate == sampleRate && activeEncoding != null) {
            // The mode does not depend on the depth; only whether coming down to 16-bit is dithered does.
            appliedBits = bits
            return
        }
        val pick = bestMixer(audioManager, device, sampleRate, bits)
        if (pick == null) {
            unavailableReason = "DAC has no bit-perfect mode at $sampleRate Hz"
            Timber.i("Bit-perfect USB unavailable: %s", unavailableReason)
            clear(audioManager)
            return
        }
        if (appliedOn != null && appliedOn?.id != device.id) clear(audioManager)
        if (audioManager.setPreferredMixerAttributes(mediaAttributes, device, pick)) {
            appliedOn = device
            appliedRate = sampleRate
            appliedBits = bits
            unavailableReason = null
            activeEncoding = when (pick.format.encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> C.ENCODING_PCM_FLOAT
                AudioFormat.ENCODING_PCM_32BIT -> C.ENCODING_PCM_32BIT
                else -> C.ENCODING_PCM_16BIT
            }
            Timber.i("Bit-perfect USB: ${pick.format.sampleRate} Hz, encoding ${pick.format.encoding}, source $bits-bit")
        } else {
            unavailableReason = "the system refused the bit-perfect mode"
            clear(audioManager)
        }
    }

    /**
     * The DAC's bit-perfect mode for this rate, stereo: float, then 32-bit integer, then 16-bit.
     *
     * Float carries 24 bits of mantissa and a 32-bit integer has room for all 24, so a source up to
     * 24-bit passes through either unchanged. A DAC with only 16-bit (or a 24-bit packed mode, which
     * nothing here can write) gets 16-bit; a deeper file is then reduced with dither ([needsDither])
     * rather than having its low bits cut off, and still reaches the DAC at its own rate, unresampled.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun bestMixer(audioManager: AudioManager, device: AudioDeviceInfo, sampleRate: Int, @Suppress("UNUSED_PARAMETER") bits: Int): AudioMixerAttributes? =
        audioManager.getSupportedMixerAttributes(device)
            .filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRate &&
                    it.format.channelMask == AudioFormat.CHANNEL_OUT_STEREO &&
                    it.format.encoding in WritableEncodings
            }
            .minByOrNull { WritableEncodings.indexOf(it.format.encoding) }

    /**
     * True when the DAC takes 16-bit but the file is deeper: the one case bit-perfect has to lose
     * bits, and the place TPDF dither turns that loss into a flat noise floor ~96 dB down instead of
     * distortion that follows the music.
     */
    val needsDither: Boolean
        get() = activeEncoding == C.ENCODING_PCM_16BIT && appliedBits > 16

    private fun clear(audioManager: AudioManager) {
        appliedRate = 0
        appliedBits = 0
        val device = appliedOn ?: run { activeEncoding = null; return }
        if (supported) {
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
        }
        appliedOn = null
        activeEncoding = null
    }

    /** Bits per sample of a Media3 PCM encoding, 16 where it is not said (lossy codecs). */
    fun bitsOf(pcmEncoding: Int): Int = when (pcmEncoding) {
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    /** Best first: float and 32-bit integer carry any source up to 24-bit untouched; 16-bit last. */
    private val WritableEncodings = listOf(
        AudioFormat.ENCODING_PCM_FLOAT,
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_16BIT,
    )
}
