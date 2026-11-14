package com.ozyern.exhale.ui.player

import com.ozyern.exhale.ui.ios.foundation.iosMenuSurface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ShowAudioQualityBadgeKey
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.utils.VibrantChromeInk
import com.ozyern.exhale.ui.utils.vibrantChrome
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.utils.rememberPreference
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Shows the active track's audio quality between the elapsed and remaining times. */
@Composable
fun AudioQualityBadge(
    playerConnection: PlayerConnection,
    tint: Color,
    modifier: Modifier = Modifier,
    chrome: Boolean = false,
    /** "Audio Quality Settings" in the tap card; null hides that button. */
    onOpenQualitySettings: (() -> Unit)? = null,
) {
    var showCard by remember { mutableStateOf(false) }
    val showBadge by rememberPreference(ShowAudioQualityBadgeKey, defaultValue = true)
    if (!showBadge) return
    val playerFormat = rememberLiveAudioFormat(playerConnection)
    // Prefer database metadata for its bitrate, but only when it matches the live codec.
    val currentFormat = playerConnection.currentFormat.collectAsState(initial = null).value
        ?.takeIf { it.agreesWith(playerFormat) }
    val mime = currentFormat?.mimeType ?: playerFormat?.sampleMimeType
    val codecs = currentFormat?.codecs ?: playerFormat?.codecs
    val bitrate = currentFormat?.bitrate?.takeIf { it > 0 }
        ?: playerFormat?.averageBitrate?.takeIf { it > 0 }
        ?: playerFormat?.bitrate?.takeIf { it > 0 }
    val detailBitrate = currentFormat?.bitrate?.takeIf { it > 0 }
        ?: playerFormat?.averageBitrate?.takeIf { it > 0 }
    val sampleRate = currentFormat?.sampleRate?.takeIf { it > 0 }
        ?: playerFormat?.sampleRate?.takeIf { it > 0 }
    val quality = audioQualityLabel(
        mime = mime,
        codecs = codecs,
        bitrate = bitrate,
        sampleRate = sampleRate,
    ) ?: return

    if (showCard) {
        AudioQualityCard(
            quality = quality,
            detail = audioQualityDetail(mime, codecs, detailBitrate, sampleRate),
            onOpenSettings = onOpenQualitySettings,
            onDismiss = { showCard = false },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = ContinuousRoundedRectangle(5.dp),
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(ContinuousRoundedRectangle(5.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable { showCard = true }
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        if (quality.lossless) {
            Icon(
                painter = painterResource(R.drawable.ic_lossless),
                contentDescription = null,
                tint = if (chrome) VibrantChromeInk else tint,
                modifier = Modifier
                    .size(width = 15.dp, height = 8.dp)
                    .vibrantChrome(chrome),
            )
        }
        Text(
            text = quality.label,
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = if (chrome) VibrantChromeInk else tint,
            maxLines = 1,
            modifier = Modifier.vibrantChrome(chrome),
        )
    }
}

/** What the badge says. [lossless] adds the waveform glyph. */
data class AudioQuality(val label: String, val lossless: Boolean)

/**
 * Apple's wording: "Lossless" up to 48 kHz, "Hi-Res Lossless" above it, "Dolby Atmos" for
 * E-AC-3 JOC. Lossy streams keep the codec + bitrate so the badge still says something
 * useful (Apple shows nothing there; YouTube/Opus is the common case here). Null = no
 * format known yet, badge hidden rather than guessing.
 */
fun audioQualityLabel(mime: String?, codecs: String?, bitrate: Int?, sampleRate: Int?): AudioQuality? {
    val normalizedMime = mime.orEmpty().lowercase()
    val normalizedCodecs = codecs.orEmpty().lowercase()
    if (normalizedMime.isEmpty() && normalizedCodecs.isEmpty()) return null

    fun containsFormat(format: String) =
        format in normalizedMime || format in normalizedCodecs

    return when {
        containsFormat("ec-3") || containsFormat("eac3") || containsFormat("joc") || containsFormat("atmos") ->
            AudioQuality("Dolby Atmos", lossless = false)
        containsFormat("flac") || containsFormat("alac") -> {
            val label = if ((sampleRate ?: 0) > 48_000) "Hi-Res Lossless" else "Lossless"
            AudioQuality(label, lossless = true)
        }
        else -> {
            val codec = when {
                containsFormat("opus") -> "Opus"
                containsFormat("mp4a") || containsFormat("aac") -> "AAC"
                containsFormat("mp3") || containsFormat("mpeg") -> "MP3"
                containsFormat("vorbis") -> "Vorbis"
                else -> return null
            }
            AudioQuality(bitrate?.let { "$codec ${it / 1000} kbps" } ?: codec, lossless = false)
        }
    }
}

/** Card detail line, Apple's style: "24-bit/96 kHz FLAC", "Opus · 151 kbps · 48 kHz". */
fun audioQualityDetail(mime: String?, codecs: String?, bitrate: Int?, sampleRate: Int?): String {
    val audioFormat = (mime.orEmpty() + " " + codecs.orEmpty()).lowercase()
    val khz = sampleRate?.let { if (it % 1000 == 0) "${it / 1000} kHz" else "%.1f kHz".format(it / 1000f) }
    val codec = when {
        "flac" in audioFormat -> "FLAC"
        "alac" in audioFormat -> "ALAC"
        "ec-3" in audioFormat || "eac3" in audioFormat -> "Dolby Digital Plus"
        "opus" in audioFormat -> "Opus"
        "mp4a" in audioFormat || "aac" in audioFormat -> "AAC"
        "mp3" in audioFormat || "mpeg" in audioFormat -> "MP3"
        else -> "Audio"
    }
    return if (codec == "FLAC" || codec == "ALAC") {
        listOfNotNull(khz, codec).joinToString(" ")
    } else {
        listOfNotNull(codec, bitrate?.let { "${it / 1000} kbps" }, khz).joinToString(" · ")
    }
}

/**
 * Tap card from the Apple Music reference: centred liquid-glass panel, big tinted waveform,
 * tier + detail, a translucent "Audio Quality Settings" pill and a white "OK" pill.
 * Every size is a fraction of the screen width so it reads the same on any phone or tablet.
 */
@Composable
private fun AudioQualityCard(
    quality: AudioQuality,
    detail: String,
    onOpenSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Measured off the reference capture, as fractions of the card width so it scales.
    // Window px through the current density: V2 rescales dp, so screenWidthDp would overshoot.
    val windowPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val shortSide = with(androidx.compose.ui.platform.LocalDensity.current) { minOf(windowPx.width, windowPx.height).toDp() }
    val cardW = shortSide * 0.79f
    val edge = cardW * 0.047f // pills to card edge, and card bottom
    val textInset = cardW * 0.09f - edge
    val pillH = cardW * 0.149f
    // Same elastic pull + bloom as the menus.
    val menuStretch = androidx.compose.runtime.rememberCoroutineScope().let { scope ->
        remember(scope) { com.ozyern.exhale.ui.component.backdrop.catalog.utils.InteractiveHighlight(animationScope = scope, radiusScale = 0.6f, sheenAlpha = 0.06f, spotAlpha = 0.32f, releasePulse = true) }
    }
    V2Floating(onDismiss = onDismiss, placement = Centered) {
        androidx.compose.runtime.CompositionLocalProvider(com.ozyern.exhale.ui.ios.foundation.LocalMenuLiquid provides true) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .width(cardW)
                    .iosMenuSurface(ContinuousRoundedRectangle(cardW * 0.085f), menuStretch)
                    .padding(start = edge, end = edge, bottom = edge, top = if (quality.lossless) cardW * 0.13f else cardW * 0.09f),
            ) {
                if (quality.lossless) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lossless),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = cardW * 0.13f)
                            .size(width = cardW * 0.21f, height = cardW * 0.21f * 8f / 15f),
                    )
                }
                Text(
                    quality.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = textInset),
                )
                Text(
                    detail,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(start = textInset, top = 4.dp, bottom = cardW * 0.074f),
                )
                if (onOpenSettings != null) {
                    AudioQualityCardPill(
                        text = androidx.compose.ui.res.stringResource(R.string.audio_quality_settings),
                        height = pillH,
                        fill = Color.White.copy(alpha = 0.16f),
                        textColor = Color.White,
                        onClick = { onDismiss(); onOpenSettings() }, // navigates away: no close animation
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(cardW * 0.026f))
                }
                AudioQualityCardPill(
                    text = androidx.compose.ui.res.stringResource(android.R.string.ok),
                    height = pillH,
                    fill = Color.White,
                    textColor = Color.Black,
                    onClick = LocalV2FloatingClose.current ?: onDismiss,
                )
            }
        }
    }
}

@Composable
private fun AudioQualityCardPill(text: String, height: androidx.compose.ui.unit.Dp, fill: Color, textColor: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(ContinuousRoundedRectangle(percent = 50))
            .background(fill)
            .clickable(onClick = onClick),
    ) {
        Text(text, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Format the active player is decoding right now; null until it reports one. */
@Composable
fun rememberLiveAudioFormat(playerConnection: PlayerConnection?): androidx.media3.common.Format? {
    val activePlayer = playerConnection?.service?.playerFlow?.collectAsStateWithLifecycle()?.value
    var playerFormat by remember(activePlayer) {
        mutableStateOf((activePlayer as? ExoPlayer)?.audioFormat)
    }
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                playerFormat = (activePlayer as? ExoPlayer)?.audioFormat
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerFormat = (activePlayer as? ExoPlayer)?.audioFormat
            }
        }
        activePlayer?.addListener(listener)
        onDispose { activePlayer?.removeListener(listener) }
    }
    return playerFormat
}

/** "flac", "alac", "opus", "eac3", "aac", "mp3", or null when unrecognised. */
internal fun codecFamily(mime: String?, codecs: String?): String? {
    val m = (mime.orEmpty() + " " + codecs.orEmpty()).lowercase()
    return when {
        "flac" in m -> "flac"
        "alac" in m -> "alac"
        "opus" in m -> "opus"
        "ec-3" in m || "eac3" in m || "ac-4" in m -> "eac3"
        "mp4a" in m || "aac" in m -> "aac"
        "mpeg" in m || "mp3" in m -> "mp3"
        else -> null
    }
}

/**
 * The stored row describes what is decoding. Rows go stale: a download, a prefetch or
 * another source rewrites them, and cached/downloaded playback never does -- which is how
 * the badge and Song info could say Opus while FLAC played, or the other way round.
 */
fun com.ozyern.exhale.db.entities.FormatEntity.agreesWith(live: androidx.media3.common.Format?): Boolean {
    val liveFamily = codecFamily(live?.sampleMimeType, live?.codecs) ?: return true
    return codecFamily(mimeType, codecs) == liveFamily
}
