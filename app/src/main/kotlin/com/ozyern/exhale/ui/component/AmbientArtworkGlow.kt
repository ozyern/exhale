/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the blobs sit, as a fraction of height. Their own falloff is what ends the effect — past
 * roughly half the viewport a tint stops reading as light from above and starts reading as a
 * coloured screen.
 */
private const val GlowBandFraction = 0.42f

/**
 * How long one full circuit of the drift takes.
 *
 * Long. The light in a room is never quite still, and neither is this -- but a glow you can *see*
 * moving is a screensaver, not a room. Half a minute a lap puts the motion below the threshold
 * where the eye tracks it and leaves only the impression that the page is lit rather than painted.
 */
private const val GlowDriftPeriodMs = 34_000f

private fun glowPhaseNow(): Float =
    (android.os.SystemClock.uptimeMillis() % GlowDriftPeriodMs.toLong()) / GlowDriftPeriodMs

/** How far a blob wanders from where it sits, as a fraction of the width and of the band. */
private const val GlowDriftX = 0.055f
private const val GlowDriftY = 0.10f

/** How much a blob swells and shrinks over its circuit. */
private const val GlowBreath = 0.09f

private const val TwoPi = (2.0 * PI).toFloat()

/** Palettes are keyed by song id and shared process-wide, so returning to Home is free. */
private const val AmbientCacheLimit = 24
private val ambientColorCache = object : LinkedHashMap<String, List<Color>>(
    AmbientCacheLimit,
    0.75f,
    true,
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>) =
        size > AmbientCacheLimit
}

/**
 * The palette of the currently playing artwork, for use as ambient colour.
 *
 * Shares [PlayerColorExtractor] with the full player so Home and the player agree about what a
 * song looks like — a cover that reads teal behind the controls reads teal at the top of Home too.
 * Extraction is a bitmap decode, so results are cached by song id, and nothing runs at all when
 * nothing is playing.
 */
@Composable
fun rememberArtworkAmbientColors(
    songId: String?,
    thumbnailUrl: String?,
): State<List<Color>> {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val colors = remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(songId, thumbnailUrl) {
        if (songId == null || thumbnailUrl == null) {
            colors.value = emptyList()
            return@LaunchedEffect
        }
        ambientColorCache[songId]?.let {
            colors.value = it
            return@LaunchedEffect
        }
        val extracted = runCatching {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                // Palette has to read pixels back, which a hardware bitmap will not allow.
                .allowHardware(false)
                .build()
            val bitmap = withContext(Dispatchers.IO) {
                context.imageLoader.execute(request).image?.toBitmap()
            } ?: return@runCatching emptyList()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap)
                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                    .generate()
            }
            PlayerColorExtractor.extractGradientColors(palette = palette, fallbackColor = fallback)
        }.getOrNull().orEmpty()

        if (extracted.isNotEmpty()) ambientColorCache[songId] = extracted
        colors.value = extracted
    }

    return colors
}

/**
 * A soft wash of the current artwork's colour bleeding down from the top of a scrolling surface.
 *
 * Home was a flat sheet: correct, and completely inert. This puts the record back in the room —
 * the surface picks up whatever is playing, the way light takes the colour of what it falls on,
 * and the screen stops looking like a list on a void.
 *
 * Built to be cheap enough to leave running:
 *
 *  * **Three radial blobs, no blur.** A radial gradient's falloff is already softer than any blur
 *    kernel, so a RenderEffect here would cost a full-screen pass every frame and change nothing
 *    you can see. The player's backdrop makes the same trade for the same reason.
 *  * **Drifting, but only in the draw phase.** The blobs wander a lap every thirty-odd seconds.
 *    This was static at first, on the argument that animating a full-screen effect on the tab the
 *    app opens on is a cost paid forever to move something nobody is watching — and the second
 *    half of that is right, which is what the speed is for: at this rate you cannot catch it
 *    moving, you only ever notice the page is not where you left it. What made it affordable is
 *    that every animated value is read inside the draw lambda, so a frame costs one layer
 *    invalidation and repaints three gradients — no recomposition, no relayout, and nothing at all
 *    for the list scrolling above it. The alternative reading of "static" was a page that is lit
 *    by the record when you arrive and then frozen, which is the exact quality a printed screen
 *    has and a room does not.
 *  * **One clock for every copy.** The drift phase comes from the shared infinite-animation frame
 *    clock, not a per-instance transition, because two copies of this are sometimes meant to be one
 *    wash — see SettingsBarGround.
 *  * **Nothing playing, nothing drawn.** With no colours the layer returns before allocating a
 *    brush.
 *
 * Each blob is painted across the *whole* surface rather than into a band. Clipping to a band is
 * the obvious way to keep the wash at the top, and it is wrong: wherever a blob's falloff has not
 * finished by the band's edge it gets cut by a straight line, which is precisely the seam the
 * effect exists to avoid. The gradients reach transparent on their own, so position and radius are
 * what confine them — nothing has to be clipped at all.
 */
@Composable
fun AmbientArtworkGlow(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    /** Scales the whole effect. Lower it on surfaces carrying a lot of small text. */
    intensity: Float = 1f,
) {
    // Dark surfaces take colour readily. On a light surface the same alpha turns the top of the
    // screen into a stain, so it is pulled well back.
    val peakAlpha = (if (isSystemInDarkTheme()) 0.42f else 0.18f) * intensity

    // Crossfade between songs -- swapping palettes instantly is a visible pop at this size.
    val presence by animateFloatAsState(
        targetValue = if (colors.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "ambient_presence",
    )

    val blobs = remember(colors) { colors.take(3) }

    // The drift clock.
    //
    // Driven off the shared infinite-animation frame clock rather than each instance's own
    // `rememberInfiniteTransition`, because two copies of this glow are sometimes meant to be one
    // continuous wash -- the settings app bar draws a screen-height copy inside itself so the
    // colour runs unbroken into the page behind it. Two independent transitions start whenever
    // their composable happens to enter, so they would sit permanently out of phase and put a
    // visible seam along the bottom edge of the bar. One clock, read by everybody, cannot.
    //
    // Written to a plain state that only `drawBehind` reads, so a frame of drift invalidates the
    // draw phase and nothing above it: no recomposition, no relayout, for a full-screen effect
    // that is running the whole time the app is open.
    //
    // Still while nothing plays, and stepped at about 30 a second while something does. Run on
    // every vsync for good, it redrew this — and, through the backdrop layer every piece of glass
    // samples, the whole window — sixty to a hundred and twenty times a second on a page sitting
    // still, for blobs that take most of a minute to go round once. Its phase is the uptime clock,
    // so two copies still drift as one.
    val phase = remember { mutableFloatStateOf(glowPhaseNow()) }
    val playing by (com.ozyern.exhale.LocalPlayerConnection.current?.isPlaying
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val drifting = playing && blobs.isNotEmpty()
    LaunchedEffect(drifting) {
        phase.floatValue = glowPhaseNow()
        if (!drifting) return@LaunchedEffect
        while (true) {
            if (!com.ozyern.exhale.utils.isAppVisible) com.ozyern.exhale.utils.awaitAppVisible()
            androidx.compose.runtime.withFrameMillis { }
            phase.floatValue = glowPhaseNow()
            com.ozyern.exhale.utils.awaitAmbientStep()
        }
    }

    Box(
        modifier = modifier.drawBehind {
            if (presence <= 0.01f || blobs.isEmpty()) return@drawBehind

            val band = size.height * GlowBandFraction
            val radius = size.width * 0.85f
            val turn = phase.floatValue * TwoPi

            blobs.forEachIndexed { index, color ->
                val cx = size.width * (0.24f + 0.26f * index)
                val cy = band * (0.18f + 0.14f * index)

                // Each blob is given its own offset and its own slightly different rate, so the
                // three of them never line up into one shape sliding about. The x and y rates are
                // coprime-ish for the same reason: a circle would read as a circle.
                val angle = turn + index * 2.09f
                val dx = sin(angle) * size.width * GlowDriftX
                val dy = cos(angle * 0.73f) * band * GlowDriftY
                val breath = 1f + GlowBreath * sin(angle * 0.51f)

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = peakAlpha * presence),
                            color.copy(alpha = peakAlpha * 0.30f * presence),
                            Color.Transparent,
                        ),
                        center = Offset(cx + dx, cy + dy),
                        radius = radius * breath,
                    ),
                )
            }
        },
    )
}
