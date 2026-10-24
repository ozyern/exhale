/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.home

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.glass.LocalPageBackdrop
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.LocalNavigator
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.ozyern.exhale.desktop.data.AppInfo

/**
 * The Android LiquidGlassIconButton: a round control cut from the page's glass, that squishes under
 * the press and lifts a little under the pointer.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    content: (@Composable () -> Unit)? = null,
) {
    val backdrop = LocalPageBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.92f, hoverScale = 1.04f)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (backdrop != null) Modifier.liquidGlass(CircleShape, backdrop, tintAlpha = 0.42f, blurRadius = 56.dp)
                else Modifier.glassPlate(CircleShape, base = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)),
            )
            .clip(CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Home's top row on Android: the Exhale logo on the left, the account circle on the right. */
@Composable
fun HomeHeader(onOpenSettings: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(appIcon, contentDescription = "Exhale", modifier = Modifier.size(44.dp).clip(CircleShape))
        Spacer(Modifier.weight(1f))
        AccountButton(onOpenSettings, onSignIn)
    }
}

@Composable
fun AccountButton(onOpenSettings: () -> Unit, onSignIn: () -> Unit) {
    val account by AccountSession.account.collectAsState()
    var open by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    Box {
        GlassIconButton(NavIcons.Artists, "Account", onClick = { open = !open }) {
            val avatar = account?.avatar
            if (avatar != null) {
                AsyncImage(avatar, contentDescription = account?.name, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
            } else {
                Icon(NavIcons.Artists, contentDescription = "Account", modifier = Modifier.size(22.dp))
            }
        }
        if (open) {
            val position = remember(gap) { BelowAnchorEnd(gap) }
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val navigator = LocalNavigator.current
                AccountMenu(
                    onOpenSettings = {
                        open = false
                        onOpenSettings()
                    },
                    onOpenSoundChem = {
                        open = false
                        navigator?.openSoundChem?.invoke()
                    },
                    onSignIn = {
                        open = false
                        onSignIn()
                    },
                    onSignOut = {
                        open = false
                        AccountSession.signOut()
                    },
                )
            }
        }
    }
}

/** Hangs the menu under the button with their right edges lined up, and never lets it leave the window. */
private class BelowAnchorEnd(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * The account menu: who's signed in, set on a wash of the accent with their picture in a ring, then
 * where to go from here as proper rows — Sound Chem, Settings, signing out — and which Exhale this is.
 * It grows out of the button's corner rather than blinking on.
 */
@Composable
private fun AccountMenu(onOpenSettings: () -> Unit, onOpenSoundChem: () -> Unit, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val account by AccountSession.account.collectAsState()
    val signedIn = account != null || AccountSession.isSignedIn
    val scheme = MaterialTheme.colorScheme
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 460f)) }
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .graphicsLayer {
                val t = appear.value
                alpha = t.coerceIn(0f, 1f)
                val s = 0.9f + 0.1f * t
                scaleX = s
                scaleY = s
                translationY = -(1f - t) * 12.dp.toPx()
                transformOrigin = TransformOrigin(1f, 0f)
            }
            .width(340.dp)
            .shadow(30.dp, shape, clip = false)
            .clip(shape)
            .background(Color(0xF7151216))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.26f), Color.Transparent)))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
        ) {
            val current = account
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(66.dp)
                            .background(Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)), CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF151216)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (current?.name ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        AsyncImage(current?.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: "Signed in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        (current?.email ?: current?.handle)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "YouTube Music",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                            modifier = Modifier.clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            } else {
                Column {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(NavIcons.Artists, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("You're not signed in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Sign in for your library, your likes and a Home built from your account instead of your location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Sign in to YouTube Music") }
                }
            }
        }

        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            MenuRow(NavIcons.Stats, "Your Sound Chem", "Your month in music", scheme.primary, onClick = onOpenSoundChem)
            MenuRow(NavIcons.Settings, "Settings", "Playback, lyrics, look and feel", scheme.secondary, onClick = onOpenSettings)
            if (signedIn) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                MenuRow(NavIcons.Logout, "Sign out", null, scheme.error, destructive = true, onClick = onSignOut)
            }
        }
        Text(
            "Exhale ${AppInfo.VERSION} for Windows",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, accent: Color, destructive: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val nudge by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "menu_row")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f * nudge))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f + 0.08f * nudge)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (destructive) accent else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        if (!destructive) {
            Icon(
                NavIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { translationX = 4.dp.toPx() * nudge },
            )
        }
    }
}
