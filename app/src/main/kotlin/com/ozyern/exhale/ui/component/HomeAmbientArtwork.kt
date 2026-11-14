/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.constants.HomeLiveArtworkKey
import com.ozyern.exhale.utils.rememberPreference

/**
 * Exhale master's live background, behind Home: what is playing, as a slowly moving field of its
 * own colours (see [FluidArtworkBackground]), under a scrim of the page's own colour that deepens
 * down the screen so the feed reads over it at an ordinary brightness.
 *
 * Taken further than master's:
 * - it arrives: faded up over a second when a song first plays, not cut in, and a new song's
 *   field crossfades into the last one's;
 * - it rests: still while the song is paused, and while the player covers Home (nobody can see
 *   it move, and every frame of it is a full-screen shader pass);
 * - it steps back as the feed is read: the scrim deepens and the field drifts up a little as
 *   Home scrolls, so the rows further down sit on a calmer ground than the top of the page;
 * - it suits the theme: a light page lays a heavier scrim of its own colour over the field,
 *   so a pale theme stays pale rather than going muddy.
 *
 * Nothing playing, or turned off in Home background settings: nothing is drawn, and the page's
 * own ground shows.
 */
@Composable
fun BoxScope.HomeAmbientArtwork(
    /** The page's ground, which the scrim is made of. */
    pageBackground: Color,
    /** How far Home has scrolled, in px; read in the draw phase. */
    scrolledPx: () -> Float = { 0f },
) {
    val (enabled) = rememberPreference(HomeLiveArtworkKey, true)
    if (!enabled) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by remember(playerConnection) { playerConnection.mediaMetadata }.collectAsState()
    val isPlaying by remember(playerConnection) { playerConnection.isPlaying }.collectAsState()
    val url = metadata?.thumbnailUrl
    // The player over Home: the field holds still under it.
    val playerCovering by remember {
        derivedStateOf { com.ozyern.exhale.ui.player.PlayerMorph.progress > 0.98f }
    }
    val shown by animateFloatAsState(
        targetValue = if (url != null) 1f else 0f,
        animationSpec = tween(1_000),
        label = "homeAmbientIn",
    )
    if (shown <= 0f && url == null) return

    val dark = isSystemInDarkTheme() || pageBackground.luminance() < 0.4f
    val scrimTop = if (dark) 0.16f else 0.42f
    val scrimFoot = if (dark) 0.50f else 0.74f

    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = shown
                // Drifts up a little as the page is read: a parallax of an eighth, capped.
                translationY = -(scrolledPx() * 0.125f).coerceIn(0f, 64.dp.toPx())
            },
    ) {
        FluidArtworkBackground(
            url = url,
            brightness = 0.66f,
            paused = !isPlaying || playerCovering,
            modifier = Modifier.fillMaxSize(),
        )
    }
    // The scrim, in the page's own colour, deepening down the screen and, as the feed scrolls,
    // all over.
    Box(
        Modifier
            .matchParentSize()
            .drawWithContent {
                val read = (scrolledPx() / (size.height * 0.6f)).coerceIn(0f, 1f)
                val top = scrimTop + (scrimFoot - scrimTop) * 0.6f * read
                val foot = scrimFoot + (0.86f - scrimFoot) * read
                drawRect(
                    Brush.verticalGradient(
                        0f to pageBackground.copy(alpha = top * shown + (1f - shown)),
                        0.55f to pageBackground.copy(alpha = ((top + foot) / 2f) * shown + (1f - shown)),
                        1f to pageBackground.copy(alpha = foot * shown + (1f - shown)),
                    ),
                )
            },
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
