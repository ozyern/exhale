/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.playback.audio

/**
 * Whether the playing track is Dolby's to render. A Dolby Atmos (E-AC-3 JOC) or AC-4 track is
 * decoded by the phone's own Dolby decoder and rendered by the phone's Dolby processing; Exhale's
 * effects (the equaliser, the stereo widener, 8D, the vocal remover) stand aside while it plays,
 * since reworking an already-virtualised object mix would wash out the very height and placement
 * it carries. Set from the player's audio input format, read on the audio thread.
 */
object DolbyHandoff {
    @Volatile
    var active: Boolean = false
}
