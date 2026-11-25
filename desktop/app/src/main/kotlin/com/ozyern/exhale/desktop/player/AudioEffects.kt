/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.EqualizerPresets
import com.ozyern.exhale.desktop.data.Settings
import java.util.Locale

/**
 * Player & audio settings as an mpv audio filter chain (ffmpeg lavfi): the equalizer, skip silence
 * and the spatial upmix. What Android does with AudioEffect and ExoPlayer, done by the decoder here.
 */
object AudioEffects {
    /**
     * [outputChannels] is what the device really opened, once known. The surround bed is laid out only
     * for a device that took one: laid out for a stereo device, mpv folds the six or eight channels back
     * into two, the delayed copies landing on top of the originals — a hollow, phasey sound — and the
     * sum running past full scale.
     */
    fun filterChain(settings: Settings, outputChannels: Int? = null, linearPhase: Boolean = true, sourceChannels: Int? = null): String {
        // Music that is already surround — Dolby Atmos' bed, a 5.1 or 7.1 Dolby track — keeps its own
        // channels. The upmix below lays a bed out of a stereo pair; given a real one it would rebuild
        // the surrounds from the fronts and throw the originals away.
        val settings = if ((sourceChannels ?: 2) > 2) settings.copy(spatialAudio = "OFF") else settings
        val filters = buildList {
            if (settings.equalizerEnabled) {
                val gains = EqualizerPresets.frequencies.indices.map { settings.equalizerBands.getOrElse(it) { 0 } }
                // Headroom for the boost, so raised bands don't clip.
                val peak = gains.maxOrNull() ?: 0
                if (peak > 0) add("volume=-${peak}dB")
                if (gains.any { it != 0 }) {
                    if (linearPhase) {
                        // One linear-phase FIR drawn through the ten band gains, rather than ten peaking
                        // biquads in a row. The biquads' skirts overlap — two neighbouring bands at +6 dB
                        // lift the frequencies between them past +6, past the headroom above — and each
                        // shifts the phase around its band. The FIR follows the curve as set, smoothly from
                        // band to band, with every frequency delayed alike: the music's timing untouched.
                        val entries = EqualizerPresets.frequencies.mapIndexed { index, frequency -> "entry($frequency,${gains[index]})" }
                        add("firequalizer=gain_entry='${entries.joinToString(";")}':gain='cubic_interpolate(f)':zero_phase=on")
                    } else {
                        EqualizerPresets.frequencies.forEachIndexed { index, frequency ->
                            val gain = gains[index]
                            if (gain != 0) add("equalizer=f=$frequency:t=o:w=1:g=$gain")
                        }
                    }
                }
            }
            if (settings.skipSilence) {
                add("silenceremove=start_periods=1:start_duration=0.05:start_threshold=-50dB:stop_periods=-1:stop_duration=1.5:stop_threshold=-50dB")
            }
            // YouTube only ever sends stereo. Spread into a surround bed, it gives Dolby Atmos for
            // Headphones (or Windows Sonic) channels to place around the listener instead of two.
            //
            // A placement, not a decode. This used to be ffmpeg's `surround` filter, which is a
            // Dolby Pro Logic *decoder*: it assumes the stereo it is given was matrix-encoded and
            // recovers the rear channels from the out-of-phase content. Ordinary music is not
            // matrix-encoded, so what it recovers is whatever the mix happens to have out of phase
            // - reverb tails, wide synths, half the hi-hat - thrown behind the listener while the
            // centre is pulled out of the front pair. That is the hollow, swirling sound the
            // toggles produced, and no amount of "quality" settings fixes it, because the filter
            // is answering a different question.
            //
            // So the fronts are passed through untouched, the centre is a level-matched sum, and
            // the rears and sides are quiet copies delayed by a few milliseconds. The delay is the
            // point: identical audio from front and back combs, while a 10-16ms offset reads as
            // room rather than as a second copy of the track (Haas). Nothing is invented and
            // nothing is subtracted, so a stereo mix stays the mix - it just has somewhere to sit
            // when Windows Sonic or Atmos places it.
            //
            // LFE is left silent on purpose, by not naming it (pan rejects "LFE=0"): a full-range copy of the music in a channel the
            // receiver expects to be low-passed is how an upmix ends up boomy.
            val opened = outputChannels ?: 0
            val layout = when {
                settings.spatialAudio == "SURROUND_71" && opened >= 8 -> "7.1"
                settings.spatialAudio != "OFF" && opened >= 6 -> "5.1"
                else -> null
            }
            when (layout) {
                "7.1" -> {
                    add(
                        "pan=7.1|FL=FL|FR=FR|FC=0.3*FL+0.3*FR" +
                            "|BL=0.28*FL|BR=0.28*FR|SL=0.22*FL|SR=0.22*FR"
                    )
                    add("adelay=0|0|0|0|16|16|10|10")
                }

                "5.1" -> {
                    add("pan=5.1|FL=FL|FR=FR|FC=0.3*FL+0.3*FR|BL=0.30*FL|BR=0.30*FR")
                    add("adelay=0|0|0|0|14|14")
                }
            }
        }
        // 8D goes before the upmix: it works on the two ears, and the surround bed is then laid
        // out from what it made.
        val linear = filters.toMutableList()
        val spatialAt = linear.indexOfFirst { it.startsWith("pan=") }
        val beforeUpmix = if (spatialAt < 0) linear else linear.subList(0, spatialAt).toList()
        val upmix = if (spatialAt < 0) emptyList() else linear.subList(spatialAt, linear.size).toList()
        if (!settings.eightD) {
            return if (filters.isEmpty()) "" else "lavfi=[${filters.joinToString(",")}]"
        }
        val graph = buildString {
            // A filter graph's first stage can't be a label; `anull` stands in when nothing comes before.
            append((beforeUpmix.ifEmpty { listOf("anull") }).joinToString(","))
            append(",").append(eightD(settings.eightDRate))
            if (upmix.isNotEmpty()) append(",").append(upmix.joinToString(","))
        }
        return "lavfi=[$graph]"
    }

    /**
     * "8D": the song circles the listener's head, as the Android app's EightDAudioProcessor does,
     * from stock ffmpeg filters. The three cues the ear needs to hear it go *round* rather than swing:
     *
     *  - equal-power panning — gains on the cosine and sine of the pan angle, so the level holds
     *    steady as it sweeps instead of dipping through the middle;
     *  - the far ear hearing it later — up to 0.66 ms, a head's width at the speed of sound — made
     *    by blending in a delayed copy as the sound moves toward the other ear;
     *  - the rear shadow — behind the head the highs soften and the level drops about 1.5 dB.
     *
     * The stereo signal is split into three pairs (dry, delayed, low-passed), merged into six
     * channels, and `aeval` mixes them back to two along the orbit at time `t`. A limiter keeps the
     * ear the sound is turned toward from clipping. Headphones only, really.
     */
    private fun eightD(rate: Float): String {
        val hz = String.format(Locale.US, "%.3f", rate.coerceIn(0.05f, 0.4f))
        val depth = "0.85"
        // st(0): pan, -1 left … 1 right. st(1): how far behind, 0 … depth. st(2): pan angle.
        val setup = "st(0,sin(2*PI*$hz*t)*$depth);st(1,max(0,-cos(2*PI*$hz*t))*$depth);st(2,(ld(0)+1)*PI/4)"
        // Each ear: its dry signal, toward the delayed copy when it is the far ear, toward the
        // low-passed copy as the sound goes behind, then its share of the pan and the rear dip.
        fun ear(dry: Int, late: Int, shade: Int, far: String, gain: String) =
            "$setup;st(3,val($dry)+(val($late)-val($dry))*max(0,$far));" +
                "(ld(3)+(val($shade)-ld(3))*ld(1)*0.55)*$gain(ld(2))*1.4142*(1-ld(1)*0.16)"
        val left = ear(dry = 0, late = 2, shade = 4, far = "ld(0)", gain = "cos")
        val right = ear(dry = 1, late = 3, shade = 5, far = "-ld(0)", gain = "sin")
        return "aformat=channel_layouts=stereo,asplit=3[ed][el][es];" +
            "[el]adelay=delays=0.66|0.66:all=1[eld];" +
            "[es]lowpass=f=3200[esl];" +
            "[ed][eld][esl]amerge=inputs=3," +
            "aeval=exprs='$left|$right':channel_layout=stereo," +
            "alimiter=limit=0.95:level=0"
    }

    /** The sounds the Sound row offers, as (key, label, speed): pitch moves with the speed, like a record. */
    val SoundPresets = listOf(
        Triple("NORMAL", "Normal", 1f),
        Triple("SPED_UP", "Sped up", 1.25f),
        Triple("NIGHTCORE", "Nightcore", 1.19f),
        Triple("SLOWED", "Slowed", 0.85f),
        Triple("DAYCORE", "Daycore", 0.84f),
        Triple("VAPORWAVE", "Vaporwave", 0.79f),
    )

    /** The speed mpv plays at: a Sound preset's, or the plain speed setting when the preset is Normal. */
    fun effectiveSpeed(settings: Settings): Float =
        SoundPresets.firstOrNull { it.first == settings.soundPreset && it.first != "NORMAL" }?.third ?: settings.playbackSpeed

    /** A preset changes the pitch with the speed; the plain speed setting keeps the pitch. */
    fun pitchCorrection(settings: Settings): Boolean =
        SoundPresets.none { it.first == settings.soundPreset && it.first != "NORMAL" }

    /**
     * What mpv asks Windows for. One layout, not a list: given a list, mpv opens whichever best fits the
     * audio it is handed — stereo, until the upmix is in, and the upmix waits on the device. A device that
     * can't open it gets its own mix format instead, and [filterChain] then leaves the music in stereo.
     */
    fun outputChannels(settings: Settings): String = when (settings.spatialAudio) {
        "SURROUND_71" -> "7.1"
        "SURROUND_51" -> "5.1"
        else -> "auto-safe"
    }

    /**
     * For when the device doesn't run at the stream's rate (AAC is 44.1 kHz, most Windows devices 48):
     * SoX's very-high-quality resampler, a steep filter that keeps the top octave, and noise-shaped
     * dither whenever the output drops to integer samples (exclusive mode on a 16- or 24-bit device).
     */
    fun resamplerOptions(settings: Settings): String =
        if (settings.hqResampler) "resampler=soxr,precision=28,cutoff=0.97,dither_method=triangular_hp" else ""
}
