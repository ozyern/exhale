/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.R

// Music Together's own pieces, drawn the way Apple draws SharePlay and AirDrop rather than the way a
// settings list is drawn: a centred hero, filled capsule buttons, a QR code to pair with, and alerts
// in iOS's shape. The grouped lists underneath (name, guest rules, people) stay settings rows,
// because that is what iOS uses for them too.

private val SystemGreen = Color(0xFF34C759)

/** A filled (prominent) or tinted capsule, iOS's two button weights. Squashes slightly under the finger. */
@Composable
internal fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    filled: Boolean = true,
    destructive: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.6f, 700f), label = "capsulePress")
    val base = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val container = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        filled -> base
        else -> base.copy(alpha = 0.14f)
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        filled -> if (destructive) Color.White else MaterialTheme.colorScheme.onPrimary
        else -> base
    }
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingRing(Modifier.size(22.dp), stroke = 2.4.dp, color = content)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/**
 * The SharePlay orb: a lit sphere carrying the group glyph, with rings rippling out from it while a
 * room is live, the way AirDrop's radar ripples while it looks for people.
 */
@Composable
internal fun TogetherOrb(active: Boolean, error: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
    // The colour eases between states instead of snapping: blue to red reads as something going
    // wrong, not as a different picture.
    val accent by androidx.compose.animation.animateColorAsState(
        if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        tween(420),
        label = "orbAccent",
    )
    val transition = rememberInfiniteTransition(label = "orbRipple")
    val ripple by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "orbRipplePhase",
    )
    // It breathes: deeper while a room is live, barely at all while idle.
    val breath by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "orbBreath",
    )
    val liveness by androidx.compose.animation.core.animateFloatAsState(
        if (active) 1f else 0f, tween(600), label = "orbLive",
    )
    Box(modifier.size(164.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(164.dp)) {
            val core = 52.dp.toPx()
            val reach = size.minDimension / 2f
            // A soft halo of the orb's own colour, so it sits in light rather than on the page.
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.26f + 0.12f * liveness * breath), Color.Transparent),
                    center = center,
                    radius = reach,
                ),
                radius = reach,
            )
            if (liveness > 0f) {
                repeat(3) { i ->
                    val t = (ripple + i / 3f) % 1f
                    // Eased outward: quick off the orb, slowing as it spreads, like a ring on water.
                    val e = 1f - (1f - t) * (1f - t)
                    val radius = core + (reach - core) * e
                    val a = 0.4f * (1f - t) * liveness
                    drawCircle(accent.copy(alpha = a * 0.35f), radius = radius, style = Stroke(6.dp.toPx()))
                    drawCircle(accent.copy(alpha = a), radius = radius, style = Stroke(1.4.dp.toPx()))
                }
            }
        }
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer {
                    val s = 1f + (0.012f + 0.03f * liveness) * breath
                    scaleX = s
                    scaleY = s
                }
                .shadow(18.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .drawBehind {
                    // Lit from the top left: bright where the light lands, deepening to the far edge.
                    drawRect(
                        Brush.radialGradient(
                            listOf(lerp(accent, Color.White, 0.38f), accent, lerp(accent, Color.Black, 0.38f)),
                            center = Offset(size.width * 0.32f, size.height * 0.26f),
                            radius = size.width * 0.95f,
                        ),
                    )
                }
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = when {
                busy -> 0
                error -> 1
                else -> 2
            }
            // The glyph changes in place — a quick scale and fade — rather than being swapped.
            androidx.compose.animation.AnimatedContent(
                targetState = glyph,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(200)) +
                        androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f)) togetherWith
                        (androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 0.8f))
                },
                label = "orbGlyph",
            ) { g ->
                when (g) {
                    0 -> LoadingRing(Modifier.size(38.dp), stroke = 3.dp, color = Color.White)
                    else -> Icon(
                        painterResource(if (g == 1) R.drawable.error else R.drawable.group),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}

/** Everyone in the room as overlapping faces, as Messages and FaceTime show a group. */
@Composable
internal fun AvatarStack(names: List<String>, hostIndex: Int?, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val shown = names.take(5)
    val extra = names.size - shown.size
    val ring = MaterialTheme.colorScheme.surface
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { index, name ->
            // Each face pops in as it joins, a beat after the one before it.
            val pop = remember(name) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(name) {
                kotlinx.coroutines.delay(60L * index)
                pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
            }
            Box(
                Modifier
                    .offset(x = (-10 * index).dp)
                    .graphicsLayer {
                        val p = pop.value
                        scaleX = 0.4f + 0.6f * p
                        scaleY = 0.4f + 0.6f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(2.dp),
            ) {
                TogetherAvatar(name, host = index == hostIndex, size = 36.dp)
            }
        }
        if (extra > 0) {
            Text(
                "+$extra",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = (-10 * shown.size + 14).dp),
            )
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF3D8BFF), Color(0xFFFF8A3D), Color(0xFF34C759), Color(0xFFB45CF0), Color(0xFFFF5C6C), Color(0xFF1FC8B4),
)

/** A person as Contacts shows one without a photo: their initial on a soft gradient disc. */
@Composable
internal fun TogetherAvatar(name: String, host: Boolean, size: Dp = 36.dp) {
    val tint = if (host) MaterialTheme.colorScheme.primary else AvatarPalette[(name.hashCode() and 0x7fffffff) % AvatarPalette.size]
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.18f), lerp(tint, Color.Black, 0.12f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/**
 * The invite as a QR code on a white card: point the other phone's camera at it and Exhale opens
 * and joins. Always dark on white — cameras read that, and it's what people expect a code to be.
 */
@Composable
internal fun TogetherQrCard(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
            )
        }.getOrNull()
    }
    Box(
        modifier
            .size(236.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxWidth().height(192.dp)) {
                val cells = matrix.width
                val cell = size.minDimension / cells
                val left = (size.width - cell * cells) / 2f
                val top = (size.height - cell * cells) / 2f
                val ink = Color(0xFF111114)
                fun finder(cx: Int, cy: Int) = cx < 7 && cy < 7 || cx >= cells - 7 && cy < 7 || cx < 7 && cy >= cells - 7
                for (y in 0 until cells) for (x in 0 until cells) {
                    if (!matrix[x, y] || finder(x, y)) continue
                    // Modules as rounded dots: the look of an App Clip code, still a standard QR.
                    drawRoundRect(
                        ink,
                        topLeft = Offset(left + x * cell + cell * 0.06f, top + y * cell + cell * 0.06f),
                        size = Size(cell * 0.88f, cell * 0.88f),
                        cornerRadius = CornerRadius(cell * 0.32f),
                    )
                }
                // Finder patterns as soft squircles, drawn whole so they stay crisp and scannable.
                for ((fx, fy) in listOf(0 to 0, cells - 7 to 0, 0 to cells - 7)) {
                    val o = Offset(left + fx * cell, top + fy * cell)
                    drawRoundRect(ink, o, Size(cell * 7, cell * 7), CornerRadius(cell * 2.2f))
                    drawRoundRect(Color.White, o + Offset(cell, cell), Size(cell * 5, cell * 5), CornerRadius(cell * 1.5f))
                    drawRoundRect(ink, o + Offset(cell * 2, cell * 2), Size(cell * 3, cell * 3), CornerRadius(cell * 1.0f))
                }
            }
        }
    }
}

/** A tip card: a coloured glyph, a bold line and a short explanation, as Settings tips look. */
@Composable
internal fun TogetherTip(icon: Int, tint: Color, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

/** iOS's alert: a small centred card, bold title, short message, and buttons divided by hairlines. */
@Composable
internal fun IosAlert(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = "Cancel",
) {
    val dark = isSystemInDarkTheme()
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.14f)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (dark) Color(0xF2272729) else Color(0xF5F7F7F9)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(hairline))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                AlertButton(dismissText, MaterialTheme.colorScheme.primary, bold = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                Box(Modifier.width(0.6.dp).fillMaxHeight().background(hairline))
                AlertButton(
                    confirmText,
                    if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    bold = true,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, color: Color, bold: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.08f else 0f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color)
    }
}

/** The first-run sheet, as Apple introduces a feature: big title, three glyph rows, one Continue. */
@Composable
internal fun TogetherWelcomeSheet(onContinue: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Dialog(onDismissRequest = onContinue, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(if (dark) Color(0xFF1C1C1E) else Color.White)
                .padding(horizontal = 26.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TogetherOrb(active = false, error = false, busy = false, modifier = Modifier.size(120.dp))
            Text("Music Together", fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TogetherTip(R.drawable.group, MaterialTheme.colorScheme.primary, "Same song, same second",
                    "Every phone in the room plays in sync, each on its own speaker or headphones.")
                TogetherTip(R.drawable.wifi, SystemGreen, "No router needed",
                    "Share a Wi-Fi, or turn on one phone's hotspot and join it with the other.")
                TogetherTip(R.drawable.lock, Color(0xFFFF9F0A), "You decide who's in",
                    "Let guests add songs or take control, or ask before anyone joins.")
            }
            Spacer(Modifier.height(30.dp))
            CapsuleButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Opens the phone's hotspot page. Its activity isn't public API, so this tries the page itself,
 * then the network settings that contain it, then Settings.
 */
internal fun openHotspotSettings(context: Context) {
    val attempts = listOf(
        Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
        Intent("android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in attempts) {
        val opened = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (opened) return
    }
}
