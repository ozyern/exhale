/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.ozyern.exhale.ui.utils.Motion
import androidx.compose.foundation.shape.CircleShape
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.player.customize.PlayerIconSlot
import com.ozyern.exhale.ui.player.customize.rememberPlayerIcon

/**
 * @param slot when non-null, the button honours the user's custom glyph for that player control
 *   and falls back to [icon] when none is set. Leave it null everywhere outside the player.
 */
@Composable
fun ResizableIconButton(
    @DrawableRes icon: Int,
    iconVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color = IosTheme.colors.label,
    enabled: Boolean = true,
    indication: Indication? = null,
    slot: PlayerIconSlot? = null,
    onClick: () -> Unit = {},
) {
    val custom = if (slot != null) rememberPlayerIcon(slot) else null
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = Motion.press(),
        label = "ResizableIconPress",
    )
    Image(
        painter = if (custom != null) custom.painter else {
            if (iconVector != null) rememberVectorPainter(iconVector) else painterResource(icon)
        },
        contentDescription = null,
        colorFilter = if (custom == null) ColorFilter.tint(color) else custom.colorFilterFor(color),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                indication = indication,
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.5f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = IosTheme.tint,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = Motion.press(),
        label = "IconButtonPress",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .background(color = containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
