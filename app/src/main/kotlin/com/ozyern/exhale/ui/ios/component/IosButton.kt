/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosCircle
import com.ozyern.exhale.ui.ios.foundation.IosControlSize
import com.ozyern.exhale.ui.ios.foundation.IosGlassSpec
import com.ozyern.exhale.ui.ios.foundation.IosGlassStyle
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import com.ozyern.exhale.ui.ios.foundation.iosGlass

/**
 * SwiftUI's ButtonStyle. [Glass] and [GlassProminent] are the iOS 26 materials; the
 * other four have been there since iOS 15.
 */
enum class IosButtonStyle { Plain, Borderless, Bordered, BorderedProminent, Glass, GlassProminent }

/** SwiftUI's ButtonRole. */
enum class IosButtonRole { Cancel, Destructive, Confirm }

/** SwiftUI's buttonSizing(_:). */
enum class IosButtonSizing { Fitted, Flexible }

/** SwiftUI's buttonBorderShape(_:). iOS 26 defaults buttons to [Capsule]. */
@Immutable
sealed interface IosButtonBorderShape {
    data object Capsule : IosButtonBorderShape
    data object Circle : IosButtonBorderShape

    /** A null radius follows the control size, matching bare .roundedRectangle. */
    data class RoundedRectangle(val radius: Dp? = null) : IosButtonBorderShape
}

/** Height, horizontal padding and corner radius per [IosControlSize]. */
@Immutable
private data class ButtonMetrics(val height: Dp, val padding: Dp, val radius: Dp)

private fun metricsFor(size: IosControlSize): ButtonMetrics = when (size) {
    IosControlSize.Mini -> ButtonMetrics(24.dp, 10.dp, 7.dp)
    IosControlSize.Small -> ButtonMetrics(28.dp, 12.dp, 8.dp)
    IosControlSize.Regular -> ButtonMetrics(34.dp, 14.dp, 10.dp)
    IosControlSize.Large -> ButtonMetrics(44.dp, 20.dp, 12.dp)
    IosControlSize.ExtraLarge -> ButtonMetrics(50.dp, 24.dp, 14.dp)
}

/**
 * SwiftUI's Button.
 *
 * The role only recolours styles that carry a tint: Destructive turns
 * [Borderless][IosButtonStyle.Borderless] and [Bordered][IosButtonStyle.Bordered]
 * labels red and fills [BorderedProminent][IosButtonStyle.BorderedProminent] red,
 * while [Plain][IosButtonStyle.Plain] keeps the primary label colour — which is what
 * iOS does.
 */
@Composable
fun IosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Plain,
    role: IosButtonRole? = null,
    tint: Color = Color.Unspecified,
    sizing: IosButtonSizing = IosButtonSizing.Fitted,
    borderShape: IosButtonBorderShape = IosButtonBorderShape.Capsule,
    enabled: Boolean = true,
    controlSize: IosControlSize = IosTheme.controlSize,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val metrics = metricsFor(controlSize)

    // Destructive overrides the ambient tint; an explicit tint still wins over the
    // theme accent, exactly as .tint(_:) does inside a role'd button on iOS.
    val resolvedTint = when {
        role == IosButtonRole.Destructive -> colors.systemRed
        tint.isSpecified -> tint
        else -> IosTheme.tint
    }

    val shape: Shape = when (borderShape) {
        IosButtonBorderShape.Capsule -> IosCapsule
        IosButtonBorderShape.Circle -> IosCircle
        is IosButtonBorderShape.RoundedRectangle ->
            ContinuousRoundedRectangle(borderShape.radius ?: metrics.radius)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "IosButtonPress",
    )

    val contentColor = when (style) {
        // Plain is the only style that ignores the tint entirely.
        IosButtonStyle.Plain -> colors.label
        IosButtonStyle.Borderless, IosButtonStyle.Bordered -> resolvedTint
        IosButtonStyle.BorderedProminent, IosButtonStyle.GlassProminent -> colors.onAccent
        IosButtonStyle.Glass ->
            if (tint.isSpecified || role == IosButtonRole.Destructive) resolvedTint else colors.label
    }

    val surface = when (style) {
        IosButtonStyle.Plain, IosButtonStyle.Borderless -> Modifier
        IosButtonStyle.Bordered -> Modifier.clip(shape).background(colors.secondarySystemFill)
        IosButtonStyle.BorderedProminent -> Modifier.clip(shape).background(resolvedTint)
        IosButtonStyle.Glass -> Modifier.iosGlass(
            shape = shape,
            style = IosGlassStyle.Regular,
            spec = IosGlassSpec.Control,
            tint = if (tint.isSpecified) tint else Color.Unspecified,
        )

        IosButtonStyle.GlassProminent -> Modifier.iosGlass(
            shape = shape,
            style = IosGlassStyle.Prominent,
            spec = IosGlassSpec.Control,
            tint = resolvedTint,
        )
    }

    val hasChrome = style != IosButtonStyle.Plain && style != IosButtonStyle.Borderless
    val sizeModifier = when {
        borderShape == IosButtonBorderShape.Circle -> Modifier.size(metrics.height)
        sizing == IosButtonSizing.Flexible ->
            Modifier.fillMaxWidth().defaultMinSize(minHeight = metrics.height)

        hasChrome -> Modifier.defaultMinSize(minHeight = metrics.height)
        else -> Modifier
    }

    Row(
        modifier = modifier
            .scale(pressScale)
            .then(sizeModifier)
            .then(surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (hasChrome && borderShape != IosButtonBorderShape.Circle) {
                    Modifier.padding(horizontal = metrics.padding)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.3f),
            LocalIosTextStyle provides IosTheme.typography.body,
        ) {
            content()
        }
    }
}

/** The string overload, matching Button("Tap Me", action:). */
@Composable
fun IosButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Plain,
    role: IosButtonRole? = null,
    tint: Color = Color.Unspecified,
    sizing: IosButtonSizing = IosButtonSizing.Fitted,
    borderShape: IosButtonBorderShape = IosButtonBorderShape.Capsule,
    enabled: Boolean = true,
    controlSize: IosControlSize = IosTheme.controlSize,
) = IosButton(
    onClick = onClick,
    modifier = modifier,
    style = style,
    role = role,
    tint = tint,
    sizing = sizing,
    borderShape = borderShape,
    enabled = enabled,
    controlSize = controlSize,
) {
    IosText(title)
}
