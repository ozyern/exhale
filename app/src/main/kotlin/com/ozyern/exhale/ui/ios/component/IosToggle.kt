/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosCircle
import com.ozyern.exhale.ui.ios.foundation.IosTheme

/** SwiftUI's ToggleStyle. */
enum class IosToggleStyle { Switch, Checkbox, Button }

/**
 * SwiftUI's Toggle.
 *
 * The switch keeps iOS's own geometry: a 51x31pt capsule with a 27pt knob, and green
 * — not the accent — when on, which is what SwiftUI's default toggle does.
 */
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    style: IosToggleStyle = IosToggleStyle.Switch,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val colors = IosTheme.colors
    val onColor = when {
        tint.isSpecified -> tint
        style == IosToggleStyle.Switch -> colors.systemGreen
        else -> IosTheme.tint
    }

    val toggleModifier = Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
    )

    when (style) {
        IosToggleStyle.Switch -> Row(
            modifier = modifier.fillMaxWidth().then(toggleModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (label != null) IosText(label)
            IosSwitchTrack(checked, onColor, enabled)
        }

        IosToggleStyle.Checkbox -> Row(
            modifier = modifier.then(toggleModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(ContinuousRoundedRectangle(6.dp))
                    .background(if (checked) onColor else Color.Transparent)
                    .border(
                        width = if (checked) 0.dp else 1.5.dp,
                        color = colors.tertiaryLabel,
                        shape = ContinuousRoundedRectangle(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) IosCheckmark(Modifier.size(width = 13.dp, height = 10.dp), Color.White)
            }
            if (label != null) IosText(label)
        }

        IosToggleStyle.Button -> IosButton(
            onClick = { onCheckedChange(!checked) },
            modifier = modifier,
            style = if (checked) IosButtonStyle.BorderedProminent else IosButtonStyle.Bordered,
            tint = onColor,
            enabled = enabled,
        ) {
            if (label != null) IosText(label)
        }
    }
}

@Composable
private fun IosSwitchTrack(checked: Boolean, onColor: Color, enabled: Boolean) {
    val colors = IosTheme.colors
    val track by animateColorAsState(
        targetValue = if (checked) onColor else colors.systemGray5,
        label = "IosSwitchTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 900f),
        label = "IosSwitchKnob",
    )

    Box(
        modifier = Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(IosCapsule)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = IosCircle)
                .clip(IosCircle)
                .background(Color.White)
        )
    }
}
