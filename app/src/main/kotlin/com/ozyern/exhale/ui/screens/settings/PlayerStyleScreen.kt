/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ClassicPlayerKey
import com.ozyern.exhale.constants.PlayerBackgroundStyle
import com.ozyern.exhale.constants.PlayerBackgroundStyleKey
import com.ozyern.exhale.constants.PlayerDesignStyle
import com.ozyern.exhale.constants.PlayerDesignStyleKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

/** Every player there is to choose: Now Playing, and the eight earlier layouts, refreshed. */
enum class PlayerStyleOption(val label: String, val detail: String, val design: PlayerDesignStyle?) {
    NOW_PLAYING("Now Playing", "The cover up top; lyrics and queue inside", null),
    LIQUID_GLASS("Liquid Glass", "Every control a piece of glass over the cover's colour", null),
    APPLE_MUSIC("Apple Music", "A floating cover over its own colours", PlayerDesignStyle.V8),
    IMMERSIVE("Immersive", "The cover fills the screen", PlayerDesignStyle.V7),
    CLASSIC("Classic", "Centred cover, round play button", PlayerDesignStyle.V1),
    MODERN("Modern", "Wide pill controls", PlayerDesignStyle.V2),
    MINIMAL("Minimal", "Just the glyphs", PlayerDesignStyle.V3),
    CINEMATIC("Cinematic", "Edge-to-edge cover, bold type", PlayerDesignStyle.V4),
    LITTLE("Little", "A compact card that fills as it plays", PlayerDesignStyle.V5),
    EXPRESSIVE("Expressive", "Soft, shaped buttons", PlayerDesignStyle.V6),
}

fun currentPlayerStyle(classic: Boolean, design: PlayerDesignStyle, liquidGlass: Boolean = false): PlayerStyleOption = when {
    !classic && liquidGlass -> PlayerStyleOption.LIQUID_GLASS
    !classic -> PlayerStyleOption.NOW_PLAYING
    else -> PlayerStyleOption.entries.firstOrNull { it.design == design } ?: PlayerStyleOption.APPLE_MUSIC
}

/**
 * The player's look, picked from pictures rather than names: each layout drawn small in the app's
 * own colours, so the choice is made by seeing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStyleScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val (classic, setClassic) = rememberPreference(ClassicPlayerKey, false)
    val (design, setDesign) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V8)
    val (background, setBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val (liquidGlass, setLiquidGlass) = rememberPreference(com.ozyern.exhale.constants.LiquidGlassPlayerKey, false)
    val selected = currentPlayerStyle(classic, design, liquidGlass)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            // Below the content, not around the viewport: the page scrolls on under the dock.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Text(
            text = "Layout",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        PlayerStyleOption.entries.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                pair.forEach { option ->
                    StyleCard(
                        option = option,
                        selected = option == selected,
                        onClick = {
                            if (option.design == null) {
                                setLiquidGlass(option == PlayerStyleOption.LIQUID_GLASS)
                                setClassic(false)
                            } else {
                                setDesign(option.design)
                                setClassic(true)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // The earlier layouts draw their own ground; Now Playing's is always the live cover.
        if (selected.design != null) {
            Text(
                text = "Background",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                PlayerBackgroundStyle.entries
                    .filter { it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    .forEach { style ->
                        BackgroundSwatch(style = style, selected = style == background, onClick = { setBackground(style) })
                    }
            }
            if (background == PlayerBackgroundStyle.CUSTOM) {
                PreferenceGroup {
                    PreferenceEntry(
                        title = { Text(stringOf(R.string.customized_background)) },
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        onClick = { navController.navigate("customize_background") },
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    SettingsTopAppBar(
        title = { Text("Player style") },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

@Composable
private fun stringOf(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun StyleCard(option: PlayerStyleOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val ring by animateColorAsState(if (selected) accent else ink.copy(alpha = 0.10f), label = "styleRing")
    val lift by animateFloatAsState(if (selected) 1f else 0.97f, label = "styleLift")
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .clip(RoundedCornerShape(22.dp))
            .background(ink.copy(alpha = 0.05f))
            .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.56f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                drawPreview(option, accent, ink)
            }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent),
                ) {
                    Icon(
                        painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) accent else ink,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = option.detail,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** A cover stand-in: the accent, washed through a second hue, so every preview has "artwork". */
private fun DrawScope.art(topLeft: Offset, size: Size, radius: Float, accent: Color) {
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(accent, Color(0xFF6A4CFF), Color(0xFFFF6A88)),
            start = topLeft,
            end = Offset(topLeft.x + size.width, topLeft.y + size.height),
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(radius),
    )
}

private fun DrawScope.bar(x: Float, y: Float, w: Float, h: Float, color: Color) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2))

/** Each layout, drawn small: the cover, the title lines, the scrubber and the controls where they sit. */
private fun DrawScope.drawPreview(option: PlayerStyleOption, accent: Color, ink: Color) {
    val w = size.width
    val h = size.height
    val pad = w * 0.10f
    val soft = ink.copy(alpha = 0.10f)
    val line = ink.copy(alpha = 0.55f)
    val faint = ink.copy(alpha = 0.28f)
    fun transport(y: Float, playR: Float, color: Color = ink, playFill: Color? = null, gap: Float = w * 0.22f) {
        val cx = w / 2
        if (playFill != null) drawCircle(playFill, playR * 1.35f, Offset(cx, y))
        drawCircle(color, playR * 0.55f, Offset(cx, y))
        drawCircle(color.copy(alpha = color.alpha * 0.8f), playR * 0.38f, Offset(cx - gap, y))
        drawCircle(color.copy(alpha = color.alpha * 0.8f), playR * 0.38f, Offset(cx + gap, y))
    }
    when (option) {
        PlayerStyleOption.NOW_PLAYING -> {
            drawRect(Color.Black)
            art(Offset(0f, 0f), Size(w, h * 0.56f), 0f, accent)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = h * 0.34f, endY = h * 0.60f),
            )
            bar(pad, h * 0.62f, w * 0.55f, h * 0.030f, Color.White.copy(alpha = 0.9f))
            bar(pad, h * 0.67f, w * 0.35f, h * 0.022f, Color.White.copy(alpha = 0.5f))
            bar(pad, h * 0.74f, w - pad * 2, h * 0.010f, Color.White.copy(alpha = 0.35f))
            transport(h * 0.84f, w * 0.07f, Color.White)
            bar(w * 0.3f, h * 0.94f, w * 0.4f, h * 0.018f, Color.White.copy(alpha = 0.18f))
        }
        PlayerStyleOption.LIQUID_GLASS -> {
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF2A0A0A), Color.Black)))
            val glass = Color.White.copy(alpha = 0.16f)
            drawCircle(glass, w * 0.06f, Offset(pad + w * 0.04f, h * 0.07f))
            drawCircle(glass, w * 0.06f, Offset(w - pad - w * 0.04f, h * 0.07f))
            bar(w * 0.34f, h * 0.055f, w * 0.32f, h * 0.018f, Color.White.copy(alpha = 0.8f))
            val side = w - pad * 2.6f
            art(Offset((w - side) / 2, h * 0.15f), Size(side, side), w * 0.07f, accent)
            bar(pad, h * 0.62f, w * 0.42f, h * 0.034f, Color.White)
            bar(pad, h * 0.67f, w * 0.26f, h * 0.024f, Color.White.copy(alpha = 0.55f))
            drawCircle(glass, w * 0.05f, Offset(w - pad - w * 0.17f, h * 0.645f))
            drawCircle(glass, w * 0.05f, Offset(w - pad - w * 0.05f, h * 0.645f))
            bar(pad, h * 0.73f, w - pad * 2, h * 0.010f, Color.White.copy(alpha = 0.3f))
            drawCircle(Color.White, w * 0.022f, Offset(pad + w * 0.02f, h * 0.735f))
            val y = h * 0.83f
            drawCircle(glass, w * 0.075f, Offset(pad + w * 0.07f, y))
            drawCircle(glass, w * 0.075f, Offset(w - pad - w * 0.07f, y))
            bar(w * 0.3f, y - w * 0.07f, w * 0.4f, w * 0.14f, accent.copy(alpha = 0.55f))
            val by = h * 0.93f
            bar(pad, by - w * 0.05f, w * 0.2f, w * 0.1f, glass)
            bar(w * 0.33f, by - w * 0.05f, w * 0.34f, w * 0.1f, glass)
            bar(w - pad - w * 0.2f, by - w * 0.05f, w * 0.2f, w * 0.1f, glass)
        }
        PlayerStyleOption.APPLE_MUSIC -> {
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF3A2A6E), Color(0xFF14101E))))
            val side = w - pad * 2.4f
            art(Offset((w - side) / 2, h * 0.10f), Size(side, side), w * 0.05f, accent)
            bar(pad, h * 0.62f, w * 0.5f, h * 0.030f, Color.White.copy(alpha = 0.9f))
            bar(pad, h * 0.67f, w * 0.32f, h * 0.022f, Color.White.copy(alpha = 0.5f))
            bar(pad, h * 0.735f, w - pad * 2, h * 0.012f, Color.White.copy(alpha = 0.35f))
            transport(h * 0.83f, w * 0.08f, Color.White)
            bar(pad, h * 0.925f, w - pad * 2, h * 0.010f, Color.White.copy(alpha = 0.25f))
        }
        PlayerStyleOption.IMMERSIVE -> {
            art(Offset(0f, 0f), Size(w, h), 0f, accent)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = h * 0.40f))
            bar(pad, h * 0.66f, w * 0.55f, h * 0.030f, Color.White)
            bar(pad, h * 0.71f, w * 0.35f, h * 0.022f, Color.White.copy(alpha = 0.6f))
            bar(pad, h * 0.78f, w - pad * 2, h * 0.010f, Color.White.copy(alpha = 0.45f))
            transport(h * 0.88f, w * 0.075f, Color.White)
        }
        PlayerStyleOption.CLASSIC -> {
            drawRect(soft)
            val side = w - pad * 2
            art(Offset(pad, h * 0.12f), Size(side, side), w * 0.06f, accent)
            bar(w * 0.22f, h * 0.66f, w * 0.56f, h * 0.030f, line)
            bar(w * 0.32f, h * 0.71f, w * 0.36f, h * 0.022f, faint)
            bar(pad, h * 0.78f, w - pad * 2, h * 0.012f, faint)
            transport(h * 0.88f, w * 0.075f, ink, playFill = accent.copy(alpha = 0.9f))
        }
        PlayerStyleOption.MODERN -> {
            drawRect(soft)
            val side = w - pad * 2
            art(Offset(pad, h * 0.10f), Size(side, side), w * 0.08f, accent)
            bar(pad, h * 0.64f, w * 0.5f, h * 0.030f, line)
            bar(pad, h * 0.69f, w * 0.3f, h * 0.022f, faint)
            bar(pad, h * 0.76f, w - pad * 2, h * 0.012f, faint)
            val y = h * 0.86f
            val ph = h * 0.075f
            bar(w * 0.34f, y - ph / 2, w * 0.32f, ph, accent)
            bar(pad, y - ph * 0.4f, w * 0.18f, ph * 0.8f, ink.copy(alpha = 0.18f))
            bar(w - pad - w * 0.18f, y - ph * 0.4f, w * 0.18f, ph * 0.8f, ink.copy(alpha = 0.18f))
        }
        PlayerStyleOption.MINIMAL -> {
            val side = w - pad * 3
            art(Offset((w - side) / 2, h * 0.14f), Size(side, side), w * 0.04f, accent)
            bar(w * 0.25f, h * 0.66f, w * 0.5f, h * 0.026f, line)
            bar(pad * 1.5f, h * 0.75f, w - pad * 3, h * 0.008f, faint)
            transport(h * 0.86f, w * 0.06f, ink.copy(alpha = 0.8f))
        }
        PlayerStyleOption.CINEMATIC -> {
            drawRect(Color.Black.copy(alpha = 0.85f))
            art(Offset(0f, h * 0.06f), Size(w, w), 0f, accent)
            bar(pad, h * 0.64f, w * 0.7f, h * 0.040f, Color.White)
            bar(pad, h * 0.70f, w * 0.4f, h * 0.022f, Color.White.copy(alpha = 0.55f))
            bar(pad, h * 0.77f, w - pad * 2, h * 0.012f, Color.White.copy(alpha = 0.35f))
            val y = h * 0.87f
            val s = w * 0.15f
            listOf(0.22f, 0.5f, 0.78f).forEachIndexed { i, f ->
                drawRoundRect(
                    if (i == 1) accent else Color.White.copy(alpha = 0.2f),
                    Offset(w * f - s / 2, y - s / 2),
                    Size(s, s),
                    CornerRadius(s * 0.28f),
                )
            }
        }
        PlayerStyleOption.LITTLE -> {
            drawRect(soft)
            val cardTop = h * 0.30f
            val cardH = h * 0.40f
            drawRoundRect(accent.copy(alpha = 0.22f), Offset(pad * 0.6f, cardTop), Size(w - pad * 1.2f, cardH), CornerRadius(w * 0.08f))
            drawRoundRect(accent.copy(alpha = 0.35f), Offset(pad * 0.6f, cardTop), Size((w - pad * 1.2f) * 0.45f, cardH), CornerRadius(w * 0.08f))
            val side = cardH * 0.55f
            art(Offset(pad * 1.2f, cardTop + cardH * 0.12f), Size(side, side), w * 0.04f, accent)
            bar(pad * 1.2f + side + w * 0.05f, cardTop + cardH * 0.2f, w * 0.35f, h * 0.026f, line)
            bar(pad * 1.2f + side + w * 0.05f, cardTop + cardH * 0.34f, w * 0.22f, h * 0.020f, faint)
            transport(cardTop + cardH * 0.82f, w * 0.055f, ink.copy(alpha = 0.85f))
        }
        PlayerStyleOption.EXPRESSIVE -> {
            drawRect(accent.copy(alpha = 0.10f))
            val side = w - pad * 2
            art(Offset(pad, h * 0.10f), Size(side, side), w * 0.16f, accent)
            bar(pad, h * 0.64f, w * 0.52f, h * 0.032f, line)
            bar(pad, h * 0.695f, w * 0.3f, h * 0.022f, faint)
            bar(pad, h * 0.76f, w - pad * 2, h * 0.016f, accent.copy(alpha = 0.45f))
            val y = h * 0.87f
            val big = w * 0.24f
            drawRoundRect(accent, Offset(w / 2 - big / 2, y - big * 0.4f), Size(big, big * 0.8f), CornerRadius(big * 0.3f))
            drawRoundRect(ink.copy(alpha = 0.18f), Offset(pad, y - big * 0.3f), Size(big * 0.7f, big * 0.6f), CornerRadius(big * 0.3f))
            drawRoundRect(ink.copy(alpha = 0.18f), Offset(w - pad - big * 0.7f, y - big * 0.3f), Size(big * 0.7f, big * 0.6f), CornerRadius(big * 0.3f))
        }
    }
}

/** A background choice, as a little swatch of what it paints. */
@Composable
private fun BackgroundSwatch(style: PlayerBackgroundStyle, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    val (label, brush) = when (style) {
        PlayerBackgroundStyle.DEFAULT -> "Theme" to Brush.linearGradient(listOf(surface, surface))
        PlayerBackgroundStyle.GRADIENT -> "Gradient" to Brush.verticalGradient(listOf(accent, Color(0xFF221A3A)))
        PlayerBackgroundStyle.CUSTOM -> "Custom" to Brush.linearGradient(listOf(Color(0xFF444444), Color(0xFF888888)))
        PlayerBackgroundStyle.BLUR -> "Blur" to Brush.radialGradient(listOf(accent.copy(alpha = 0.8f), Color(0xFF6A4CFF), Color(0xFF1A1A1A)))
        PlayerBackgroundStyle.COLORING -> "Colour" to Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), accent.copy(alpha = 0.7f)))
        PlayerBackgroundStyle.BLUR_GRADIENT -> "Blur fade" to Brush.verticalGradient(listOf(accent.copy(alpha = 0.8f), Color(0xFF6A4CFF), Color.Black))
        PlayerBackgroundStyle.GLOW -> "Glow" to Brush.radialGradient(listOf(accent, Color(0xFF101010)))
        PlayerBackgroundStyle.GLOW_ANIMATED -> "Live glow" to Brush.radialGradient(listOf(Color(0xFFFF6A88), accent, Color(0xFF101010)))
        PlayerBackgroundStyle.FLUID -> "Fluid" to Brush.sweepGradient(listOf(accent, Color(0xFF6A4CFF), Color(0xFFFF6A88), accent))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 58.dp, height = 78.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
                .border(if (selected) 2.dp else 1.dp, if (selected) accent else ink.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(66.dp),
        )
    }
}
