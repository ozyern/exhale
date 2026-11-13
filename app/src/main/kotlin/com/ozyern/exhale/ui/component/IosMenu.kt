/*
 * Ported from BitChord (github.com/kushagrasinghx/BitChord, GPL-3.0): its iOS-style menu
 * (ui/components/IosMenu.kt) — the panel's colours and frost, the separator, and the row.
 * Adapted for Exhale: drawable resources rather than image vectors, Exhale's backdrop, and a row
 * that takes the slots Exhale's menus were already written with.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls

/**
 * The colours of an iOS-style menu panel: a charcoal or frost slab with its own label greys,
 * rather than the theme's surface roles — the panel floats over artwork as often as over a page,
 * and the theme's containers were tuned for neither.
 */
@Immutable
data class IosMenuColors(
    val dark: Boolean,
    val fill: Color,
    val rim: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
)

private val DarkMenu = IosMenuColors(
    dark = true,
    fill = Color(0xFF1C1C1E),
    rim = Color.White.copy(alpha = 0.10f),
    label = Color.White,
    secondaryLabel = Color.White.copy(alpha = 0.70f),
    separator = Color.White.copy(alpha = 0.10f),
)

private val LightMenu = IosMenuColors(
    dark = false,
    fill = Color(0xFFF6F6F7),
    rim = Color.Black.copy(alpha = 0.06f),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF1A1A1A).copy(alpha = 0.55f),
    separator = Color.Black.copy(alpha = 0.09f),
)

/** The panel colours for the theme in force. */
@Composable
fun iosMenuColors(): IosMenuColors =
    if (MaterialTheme.colorScheme.background.luminance() >= 0.5f) LightMenu else DarkMenu

/** The panel's silhouette: a large, soft corner. */
val IosMenuShape: Shape = RoundedCornerShape(30.dp)

private val MenuBlur = 24.dp

/**
 * Shadow, slab and rim: the menu's material, without its contents. With a [backdrop] the slab is
 * frost — what is behind blurred hard, its colour drawn up more strongly than the page has it,
 * under a thin film of the panel's own colour. Without one (or where the phone cannot run the
 * glass) it is the solid slab.
 */
@Composable
fun Modifier.iosMenuPanel(backdrop: Backdrop?, colors: IosMenuColors = iosMenuColors()): Modifier {
    val frost = backdrop != null && remember { com.kyant.backdrop.isRuntimeShaderSupported() }
    val shadowed = shadow(
        elevation = 24.dp,
        shape = IosMenuShape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
    val surface = if (frost) {
        val fill = colors.fill.copy(alpha = if (colors.dark) 0.60f else 0.72f)
        shadowed.drawBackdrop(
            backdrop = backdrop!!,
            shape = { IosMenuShape },
            effects = {
                // iOS menus carry the page's hue through more strongly than the page has it.
                colorControls(saturation = 1.8f)
                blur(MenuBlur.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(fill) },
        )
    } else {
        shadowed.clip(IosMenuShape).background(colors.fill)
    }
    return surface.clip(IosMenuShape).border(0.5.dp, colors.rim, IosMenuShape)
}

/** The break between groups: a hairline inset from the panel's edges, with air either side. */
@Composable
fun IosMenuSeparator(colors: IosMenuColors = iosMenuColors(), inset: Dp = 18.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(17.dp)
            .padding(horizontal = inset),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.separator),
        )
    }
}

/**
 * A menu row, with Material's `ListItem` slots so every menu already written with those keeps
 * working. Inside a pop-out it is the iOS row: the glyph leading in its own column so the labels
 * line up, the label at 17sp with an optional dimmer line under it, and a rounded shape for the
 * press to fill. In a sheet it is Material's row, unchanged.
 */
@Composable
fun MenuListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = androidx.compose.material3.ListItemDefaults.colors(),
) {
    if (!LocalMenuIsPopOut.current) {
        androidx.compose.material3.ListItem(
            headlineContent = headlineContent,
            modifier = modifier,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = colors,
        )
        return
    }
    val menu = iosMenuColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(modifier)
            .heightIn(min = 44.dp)
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides menu.label) {
            if (leadingContent != null) {
                Box(Modifier.widthIn(min = 28.dp), contentAlignment = Alignment.Center) { leadingContent() }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                ) { headlineContent() }
                if (supportingContent != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides menu.secondaryLabel,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                    ) { supportingContent() }
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(10.dp))
                trailingContent()
            }
        }
    }
}
