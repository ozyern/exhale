/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.utils.MenuIconChrome
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.ui.utils.vibrantChrome
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.isSpecified
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfEffectScope
import com.composables.sfsymbols.SfRenderingMode
import com.composables.sfsymbols.SfSymbol
import com.composables.sfsymbols.SfSymbolEffect
import com.composables.sfsymbols.SfSymbolIcon
import com.composables.sfsymbols.SfSymbolTransition
import com.composables.sfsymbols.SfWeight

/** Default glyph size for [SymbolIcon]s that do not name one; menus shrink it. */
val LocalSymbolIconSize = androidx.compose.runtime.compositionLocalOf { 24.dp }

/** Additive-chrome strength applied to every [SymbolIcon] below (0 = none). Hearts never get it. */
val LocalSymbolChrome = androidx.compose.runtime.compositionLocalOf { 0f }

/** Transport controls, chevrons and the ellipsis never bounce (see [SymbolIcon]'s `still` check). */
private val TransportSymbols = setOf(
    "play", "play.fill", "pause", "pause.fill", "forward", "forward.fill", "backward", "backward.fill",
    "forward.end", "forward.end.fill", "backward.end", "backward.end.fill", "gobackward", "goforward",
)

/** One shared default: a fresh effect object per recomposition restarts the effect on every icon. */
private val DefaultBounce = SfSymbolEffect.Bounce(scope = SfEffectScope.ByLayer)

/** Entry bounces already played this process, so scrolling or recomposition never replays them. */
private val playedEntries = java.util.Collections.synchronizedSet(HashSet<Any>())

/**
 * App-wide SF Symbol: bold weight, hierarchical layers, optional chrome.
 *
 * Motion policy (one place, so nothing animates by accident): an icon is still unless a call
 * site opts in. Opt-ins are [bounceOnEnter] (once per composition, or once per process with an
 * [enterKey]), [bounceOnChange] (the symbol swapped), [selected] (flipped to true, tab landing),
 * [replayOn] (any change of the key, e.g. the nav bar folding), [bounceOnTouch] (a tap, never a
 * drag) and [interactionSource] (the parent's press). Chevrons, the ellipsis and transport
 * controls never bounce. A changed [symbol] always morphs; [variableValue] fills variable symbols.
 */
@Composable
fun SymbolIcon(
    symbol: SfSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    tint: Color = Color.Unspecified,
    weight: SfWeight = SfWeight.Bold,
    effect: SfSymbolEffect? = DefaultBounce,
    mode: SfRenderingMode = SfRenderingMode.Hierarchical,
    bounceOnEnter: Boolean = false,
    enterKey: Any? = null,
    bounceOnChange: Boolean = false,
    bounceOnTouch: Boolean = false,
    selected: Boolean? = null,
    replayOn: Any? = null,
    variableValue: Float? = null,
    chrome: Float = LocalSymbolChrome.current,
    interactionSource: InteractionSource? = null,
) {
    val still = symbol.name in TransportSymbols || symbol.name.startsWith("chevron.") ||
        symbol.name == "ellipsis" || symbol.name.startsWith("ellipsis.")
    val animated = bounceOnEnter || bounceOnChange || bounceOnTouch || selected != null ||
        replayOn != null || interactionSource != null
    val effect = if (still || !animated) null else effect
    var taps by remember { mutableIntStateOf(0) }
    if (bounceOnEnter && !still) LaunchedEffect(Unit) { if (enterKey == null || playedEntries.add(enterKey)) taps++ }
    var firstSymbol by remember { mutableStateOf(true) }
    LaunchedEffect(symbol.name) { if (firstSymbol) firstSymbol = false else if (bounceOnChange) taps++ }
    if (selected != null) {
        var seen by remember { mutableStateOf(selected) }
        LaunchedEffect(selected) {
            if (selected && !seen) taps++
            seen = selected
        }
    }
    if (replayOn != null) {
        var seenKey by remember { mutableStateOf(replayOn) }
        LaunchedEffect(replayOn) {
            if (replayOn != seenKey) taps++
            seenKey = replayOn
        }
    }
    val shownValue by animateFloatAsState(variableValue ?: 1f, label = "symbolValue")
    if (interactionSource != null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { if (it is PressInteraction.Press) taps++ }
        }
    }
    // Chrome is Apple's vibrant fill: neutral glyphs are drawn light grey and colour-dodged onto the
    // glass, so the backdrop's hue comes through. Dark glass only (dodge washes light glass out);
    // hearts and explicitly coloured glyphs (selected red, destructive) keep their own colour.
    val chromeStrength = if (chrome > 0f) chrome else if (symbol.name == "chevron.left") MenuIconChrome else 0f
    // Light chrome (menus): keep the plain tint and add only a faint additive lift.
    val light = chromeStrength > 0f && chromeStrength <= LightChromeMax
    val vibrant = chromeStrength > LightChromeMax && IosTheme.colors.isDark && !symbol.name.startsWith("heart") &&
        !(tint.isSpecified && tint.isColorful())
    // Dodge alone is invisible on pure black (0 x anything = 0), so a faint additive pass sits under it.
    var base = when {
        vibrant -> modifier.vibrantChrome(bleed = ChromeBleed).additiveChrome(true, 0.5f, bleed = ChromeBleed)
        light && IosTheme.colors.isDark && !symbol.name.startsWith("heart") ->
            modifier.additiveChrome(true, chromeStrength, bleed = ChromeBleed)
        else -> modifier
    }
    if (interactionSource == null && effect != null && bounceOnTouch) {
        base = base.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Initial pass sees the up before children do; a drag cancels it, so scrolls never bounce.
                val up = waitForUpOrCancellation(PointerEventPass.Initial)
                if (up != null) taps++
            }
        }
    }
    SfSymbolIcon(
        symbol = symbol,
        modifier = base,
        size = if (size.isSpecified) size else LocalSymbolIconSize.current,
        weight = weight,
        mode = mode,
        tint = if (vibrant) VibrantIconInk else if (tint.isSpecified) tint else IosTheme.contentColor,
        variableValue = if (variableValue != null) shownValue else null,
        effect = effect,
        trigger = taps,
        transition = SfSymbolTransition.MagicReplace,
        contentDescription = contentDescription,
    )
}

/** Resolves a legacy `SFHeartFill` or Apple `heart.fill` name. */
fun sf(name: String): SfSymbol =
    symbolCache.getOrPut(name) { SfSymbol.named(name) ?: error("Unknown SF Symbol: $name") }

private val symbolCache = java.util.concurrent.ConcurrentHashMap<String, SfSymbol>()

/** Snaps a 0..1 volume to whole speaker waves: full volume lights all three, lower levels drop bars. */
fun volumeBars(level: Float): Float = kotlin.math.ceil(level.coerceIn(0f, 1f) * 3f) / 3f

/** Download arrow inside a ring that fills with the download's progress (spins while queued). */
@Composable
fun DownloadRingIcon(
    download: androidx.media3.exoplayer.offline.Download?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val color = if (tint.isSpecified) tint else IosTheme.tint
    val target = download?.takeIf { it.percentDownloaded >= 0f }?.let { it.percentDownloaded / 100f }
    val progress by animateFloatAsState(target ?: 0f, label = "downloadProgress")
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "downloadSpin")
        .animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    androidx.compose.foundation.layout.Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                color,
                if (target == null) spin.value - 90f else -90f,
                if (target == null) 90f else 360f * progress,
                false, topLeft, arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        SymbolIcon(symbol = sf("arrow.down"), contentDescription = null, size = size * 0.5f, tint = color, bounceOnTouch = false)
    }
}

/** Chrome strengths at or below this are the light menu variant. */
private const val LightChromeMax = 0.3f

/** Room around a chromed glyph for its bounce to scale into without being cut off. */
private val ChromeBleed = 12.dp

/** Ink for chromed glyphs: lighter than [com.ozyern.exhale.ui.utils.VibrantChromeInk] (about 3.6x dodge) so they stay readable. */
internal val VibrantIconInk = Color(0xFFB4B4B4)

/** True for a clearly saturated colour; greys, whites and blacks count as neutral. */
private fun Color.isColorful(): Boolean = maxOf(red, green, blue) - minOf(red, green, blue) > 0.25f
