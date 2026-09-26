/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.constants.SabrinaThemeKey
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The decorative half of the Sabrina palette.
 *
 * A palette on its own repaints the app; it does not decorate it. These are the ornaments — ribbon
 * bows, hearts and four-point sparkles — drawn as vector paths rather than shipped as assets, so
 * they cost nothing in the APK, tint themselves from the live colour scheme, and stay sharp at any
 * size on any density.
 *
 * Everything here is inert. Nothing is clickable, nothing consumes a touch, nothing occupies layout
 * space that content needed. Switching the theme off removes every one of them.
 *
 * Shapes are authored in a 100x100 box and scaled at draw time, which is the only reason the same
 * bow can be an 18dp mark beside a section header and a charm tied to an album cover.
 */

/** One line at a call site, so a screen can decorate itself without a flag plumbed through it. */
@Composable
fun sabrinaDecorEnabled(): Boolean {
    val enabled by rememberPreference(SabrinaThemeKey, defaultValue = false)
    return enabled
}

/* ------------------------------------------------------------------- the shapes */

/**
 * A ribbon bow: two loops fanning outward from a centre knot, with two crossed tails falling from
 * under it.
 *
 * The loops go *out*, not up. A first pass had them rising from the knot at an angle, which is what
 * a bow looks like in a diagram and what a moth looks like on screen — at 17dp it read as two wings
 * and a body. Sweeping them sideways so they are widest at the far edge and pinched at the knot is
 * the whole difference, and it is what still survives at header size.
 */
private fun bowPath(): Path = Path().apply {
    // Left loop: out along the top edge, round the far edge, back under to the knot.
    moveTo(47f, 43f)
    cubicTo(33f, 17f, 8f, 15f, 3f, 37f)
    cubicTo(1f, 58f, 17f, 71f, 35f, 63f)
    cubicTo(41f, 60f, 45f, 53f, 47f, 49f)
    close()
    // Right loop, mirrored about x = 50.
    moveTo(53f, 43f)
    cubicTo(67f, 17f, 92f, 15f, 97f, 37f)
    cubicTo(99f, 58f, 83f, 71f, 65f, 63f)
    cubicTo(59f, 60f, 55f, 53f, 53f, 49f)
    close()
    // Tails. They cross under the knot — each starts on its own side and finishes past the centre —
    // which is what stops them reading as two legs.
    moveTo(47f, 58f)
    cubicTo(43f, 70f, 37f, 79f, 27f, 90f)
    cubicTo(34f, 86f, 39f, 84f, 44f, 83f)
    cubicTo(47f, 74f, 50f, 66f, 52f, 59f)
    close()
    moveTo(53f, 58f)
    cubicTo(57f, 70f, 63f, 79f, 73f, 90f)
    cubicTo(66f, 86f, 61f, 84f, 56f, 83f)
    cubicTo(53f, 74f, 50f, 66f, 48f, 59f)
    close()
    // The knot last, so it covers the seams where four sub-paths meet at the centre.
    addOval(Rect(left = 39f, top = 41.5f, right = 61f, bottom = 60.5f))
}

private fun heartPath(): Path = Path().apply {
    moveTo(50f, 88f)
    cubicTo(14f, 62f, 6f, 40f, 20f, 26f)
    cubicTo(32f, 14f, 46f, 20f, 50f, 32f)
    cubicTo(54f, 20f, 68f, 14f, 80f, 26f)
    cubicTo(94f, 40f, 86f, 62f, 50f, 88f)
    close()
}

/**
 * A four-point star with concave sides.
 *
 * Straight-edged stars read as "rating" or "favourite" — an affordance. The pinched waist is what
 * makes this one read as a glint instead, and that is the only reason it can be scattered across a
 * screen without looking like a row of buttons nobody can press.
 */
private fun sparklePath(): Path = Path().apply {
    moveTo(50f, 2f)
    cubicTo(55f, 34f, 66f, 45f, 98f, 50f)
    cubicTo(66f, 55f, 55f, 66f, 50f, 98f)
    cubicTo(45f, 66f, 34f, 55f, 2f, 50f)
    cubicTo(34f, 45f, 45f, 34f, 50f, 2f)
    close()
}

/** The three marks, built once per process rather than once per frame. */
private object Charms {
    val bow = bowPath()
    val heart = heartPath()
    val sparkle = sparklePath()
    val all = listOf(bow, heart, sparkle)
}

/**
 * Draws one charm centred on [center], scaled so its 100x100 authoring box becomes [size] px.
 *
 * The transform is applied to the canvas rather than baked into the path: the three paths are
 * shared singletons, and a transformed copy per charm per frame is exactly the allocation this
 * exists to avoid.
 */
private fun DrawScope.drawCharm(
    path: Path,
    center: Offset,
    size: Float,
    degrees: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.004f || size <= 0.5f) return
    withTransform({
        rotate(degrees = degrees, pivot = center)
        translate(left = center.x - size / 2f, top = center.y - size / 2f)
        scale(scaleX = size / 100f, scaleY = size / 100f, pivot = Offset.Zero)
    }) {
        drawPath(path = path, color = color, alpha = alpha.coerceIn(0f, 1f))
    }
}

/* -------------------------------------------------------------- a single ornament */

/**
 * One static bow, for tying onto furniture — a section header, a corner, a label.
 *
 * Static on purpose. A bow beside a heading that also wobbled would be a second thing competing
 * with the heading for attention; the drifting is kept to the backdrop, where nothing is trying to
 * be read.
 */
@Composable
fun SabrinaBow(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    rotation: Float = -8f,
    alpha: Float = 1f,
) {
    Canvas(modifier = modifier.size(size)) {
        drawCharm(
            path = Charms.bow,
            center = center,
            size = this.size.minDimension,
            degrees = rotation,
            color = color,
            alpha = alpha,
        )
    }
}

/* ---------------------------------------------------------------- the charm field */

private class Charm(
    val path: Path,
    val x: Float,
    val y: Float,
    val size: Float,
    val spin: Float,
    val phase: Float,
    val drift: Float,
    val tint: Int,
    val alpha: Float,
)

private const val CHARM_COUNT = 22

/**
 * A fixed scatter, generated once.
 *
 * Seeded [Random] rather than hand-placed coordinates, so the spread stays even without being
 * arranged by hand — and seeded rather than free, so it stays the *same* even spread on every
 * launch. A backdrop that rearranged itself each time you opened the app would get noticed, and not
 * fondly.
 */
private fun layOutCharms(): List<Charm> {
    val random = Random(seed = 0x5AB21A)
    return List(CHARM_COUNT) { index ->
        Charm(
            // Round-robin rather than random, so a seed that happened to favour one shape cannot
            // produce a sky of nothing but hearts.
            path = Charms.all[index % Charms.all.size],
            x = random.nextFloat(),
            y = random.nextFloat(),
            // Enough spread in scale that the field reads as having depth instead of as one stamp
            // repeated across the window.
            size = 0.045f + random.nextFloat() * 0.075f,
            spin = random.nextFloat() * 360f,
            phase = random.nextFloat() * TWO_PI,
            drift = 0.4f + random.nextFloat(),
            tint = random.nextInt(3),
            alpha = 0.4f + random.nextFloat() * 0.6f,
        )
    }
}

private val TWO_PI = (2.0 * PI).toFloat()

/**
 * The app-wide layer: charms scattered across the whole window, drifting and twinkling.
 *
 * It belongs in the backdrop that the liquid-glass chrome samples, not on top of the content, and
 * that placement is the entire trick. The floating top bar, the navigation bar, the mini-player
 * pill and every bottom sheet are lenses over that backdrop, so the bows and hearts are *refracted*
 * through them and bend as the glass moves. An overlay painted above the content would have had to
 * be faint enough not to interfere with reading, which is another way of saying invisible.
 *
 * One shared phase drives every charm, so this is one animation and one [Canvas] rather than
 * [CHARM_COUNT] of each.
 */
@Composable
fun SabrinaCharmField(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f,
    /**
     * Whether something is playing.
     *
     * The field drifts either way - a still sky would read as a wallpaper someone left on - but it
     * quickens and brightens while there is music, so the decoration belongs to the thing the app
     * is doing rather than sitting beside it.
     */
    playing: Boolean = false,
) {
    val charms = remember { layOutCharms() }

    val transition = rememberInfiniteTransition(label = "sabrinaCharms")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            tween(if (playing) 27_000 else 41_000, easing = LinearEasing),
        ),
        label = "drift",
    )
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            tween(if (playing) 5_200 else 7_400, easing = LinearEasing),
        ),
        label = "twinkle",
    )
    // Eased rather than switched, so starting a song lifts the sky instead of flicking it on.
    val lift by animateFloatAsState(
        targetValue = if (playing) 1.18f else 1f,
        animationSpec = tween(1_200),
        label = "sabrinaLift",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (colors.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val unit = min(w, h)

        charms.forEach { charm ->
            val wobble = phase + charm.phase
            val cx = w * charm.x + unit * charm.drift * 0.06f * sin(wobble)
            val cy = h * charm.y + unit * charm.drift * 0.04f * cos(wobble * 0.8f)
            // Sparkles pulse; bows and hearts hold steady. A whole sky breathing in unison reads as
            // the screen fading in and out, not as glitter.
            val pulse =
                if (charm.path === Charms.sparkle) {
                    0.55f + 0.45f * (0.5f + 0.5f * sin(twinkle + charm.phase * 2f))
                } else {
                    1f
                }
            drawCharm(
                path = charm.path,
                center = Offset(cx, cy),
                size = unit * charm.size,
                degrees = charm.spin + 14f * sin(wobble * 0.6f),
                color = colors[charm.tint % colors.size],
                alpha = (charm.alpha * pulse * alpha * lift).coerceAtMost(1f),
            )
        }
    }
}

/* ----------------------------------------------------------------- the cover trim */

/**
 * The charms on the player's album cover: a bow tied at the top-left corner, sparkles caught on the
 * other three.
 *
 * Corners, because the middle of a cover is the artwork, and decoration that covers the thing being
 * decorated is vandalism. The bow's knot lands on the corner itself so the loops hang off the edge
 * — tied *onto* the cover rather than printed on it.
 */
@Composable
fun SabrinaCoverCharms(
    size: Dp,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sabrinaCover")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(5_200, easing = LinearEasing)),
        label = "coverTwinkle",
    )

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (colors.isEmpty()) return@Canvas
            val edge = this.size.minDimension
            val glint = edge * 0.085f

            fun sparkle(fx: Float, fy: Float, offsetPhase: Float) {
                val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(twinkle + offsetPhase))
                drawCharm(
                    path = Charms.sparkle,
                    center = Offset(this.size.width * fx, this.size.height * fy),
                    size = glint * (0.75f + 0.35f * pulse),
                    degrees = 0f,
                    color = colors[1 % colors.size],
                    alpha = 0.9f * pulse,
                )
            }

            sparkle(0.96f, 0.06f, 0f)
            sparkle(0.05f, 0.95f, 2.1f)
            sparkle(0.94f, 0.93f, 4.2f)

            drawCharm(
                path = Charms.bow,
                center = Offset(this.size.width * 0.03f, this.size.height * 0.04f),
                size = edge * 0.19f,
                degrees = -18f,
                color = colors[0],
                alpha = 0.95f,
            )
        }
    }
}
