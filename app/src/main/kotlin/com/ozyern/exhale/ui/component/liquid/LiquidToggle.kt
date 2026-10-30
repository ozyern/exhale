package com.ozyern.exhale.ui.component.liquid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

/**
 * The app's toggle, as ColorOS draws it: a wide pill, ColorOS blue when on and a light grey when
 * off, with a round white thumb.
 *
 * Its motion is ColorOS's own, measured frame by frame from a screen recording of the system
 * switch: the thumb glides across in about 280 ms on a strong ease-out with no bounce, and the
 * track's colour follows the thumb the whole way (through a soft grey-blue, as Compose blends
 * colours in Oklab) rather than flipping at either end. The thumb stays a disc throughout. The
 * spring and the stretch while held that this used to have were not in the original.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop = rememberInContentBackdrop(),
) {
    val dark = isSystemInDarkTheme()
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(ToggleTravelMs, easing = ColorOsSwitchEasing),
        label = "toggle",
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // ColorOS's off track: a light grey that reads on both the dark card and the pressed row.
    val offTrack = if (dark) Color(0xFF8A8A8E) else Color(0xFFD5D5D8)
    val track = lerp(offTrack, ColorOsSwitchBlue, progress)
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .drawBehind {
                val radius = size.height / 2f
                drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
                val inset = 3.dp.toPx()
                val thumbHeight = size.height - inset * 2f
                val thumbWidth = thumbHeight
                val travel = size.width - inset * 2f - thumbWidth
                val left = inset + travel * progress
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, inset + 1.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbHeight / 2f),
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(left, inset),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbHeight / 2f),
                )
            },
    )
}

/** ColorOS's switch blue. */
private val ColorOsSwitchBlue = Color(0xFF1F7BFF)

/** How long the thumb takes to cross, from the recording: about eight frames at 30 fps. */
private const val ToggleTravelMs = 280

/** Quick off the mark and a long gentle landing, with no overshoot. */
private val ColorOsSwitchEasing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/**
 * The previous toggle: liquid glass with lens refraction and a smooth damped animation.
 */
@Suppress("unused")
@Composable
private fun LiquidToggleClassic(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Kept for API compatibility only. In-content toggles never consume the app content
    // layer (that would crash); the thumb refracts its own local track layer instead.
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop = rememberInContentBackdrop()
) {
    val isLightTheme = !isSystemInDarkTheme()
    // The ON track is the app's brand colour, not iOS system green. Every interactive tint in the
    // app (active tab icon, row accents, sliders) resolves to `colorScheme.primary`; hard-coding a
    // green here made the one control the user touches most the only thing on screen that ignored
    // their theme.
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onCheckedChange(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (checked) 0f else 1f
                    onCheckedChange(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }

    LaunchedEffect(checked) {
        snapshotFlow { checked }
            .collectLatest { isChecked ->
                val target = if (isChecked) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        // Track background
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(RoundedCornerShape(50)) // Capsule
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(64f.dp, 28f.dp)
        )

        // Thumb
        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, fraction)
                    else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .semantics {
                    role = Role.Switch
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    // IMPORTANT: consume ONLY the local track layer (a sibling drawn before
                    // the thumb), never the app content backdrop. This toggle lives inside
                    // the NavHost content layer; consuming that same ancestor layer here is a
                    // re-entrant GraphicsLayer draw and throws IllegalStateException on the
                    // first frame (this was the Settings "crash on click"). The `backdrop`
                    // parameter is intentionally ignored for in-content components.
                    backdrop = rememberBackdrop(trackBackdrop) { drawBackdrop ->
                        val progress = dampedDragAnimation.pressProgress
                        val scaleX = lerp(2f / 3f, 0.75f, progress)
                        val scaleY = lerp(0f, 0.75f, progress)
                        scale(scaleX, scaleY) {
                            drawBackdrop()
                        }
                    },
                    shape = { RoundedCornerShape(50) }, // Capsule
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
