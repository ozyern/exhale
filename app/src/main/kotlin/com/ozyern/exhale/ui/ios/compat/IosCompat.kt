/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import zone.ien.hig.CupertinoActivityIndicator
import zone.ien.hig.CupertinoButton
import zone.ien.hig.CupertinoButtonDefaults
import zone.ien.hig.CupertinoHorizontalDivider
import zone.ien.hig.CupertinoIcon
import zone.ien.hig.CupertinoIconButton
import zone.ien.hig.CupertinoText
import zone.ien.hig.CupertinoVerticalDivider
import androidx.compose.runtime.remember
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle

/*
 * Drop-in iOS replacements for the Material 3 widgets, with byte-identical parameter
 * lists so a screen converts by rewriting its imports rather than its call sites:
 *
 *     import androidx.compose.material3.Text   ->   import com.ozyern.exhale.ui.ios.compat.Text
 *
 * Everything here draws with the iOS palette, iOS type and iOS geometry. Nothing here
 * pulls in `androidx.compose.material3`.
 *
 * The widget underneath is compose-hig's (vendored in the `:hig` module) wherever it ships
 * one — see [com.ozyern.exhale.ui.ios.foundation.ProvideHigTheme], which feeds it this app's
 * palette so a compose-hig control and a hand-drawn one agree on colour. What stays
 * hand-drawn is what compose-hig has no counterpart for.
 */

/** The style a bare [Text] inherits — the compat stand-in for M3's LocalTextStyle. */
val LocalTextStyle = LocalIosTextStyle

/** The colour text and icons inherit — the compat stand-in for M3's LocalContentColor. */
val LocalContentColor = LocalIosContentColor

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // The style is resolved here rather than handed to CupertinoText as loose arguments:
    // colour precedence (explicit > style's own > inherited) has to survive, and cupertino
    // resolves its own inherited colour from a local it owns.
    CupertinoText(
        text = text,
        modifier = modifier,
        style = mergeTextStyle(
            style, color, fontSize, fontStyle, fontWeight, fontFamily,
            letterSpacing, textDecoration, textAlign, lineHeight,
        ),
        onTextLayout = onTextLayout ?: {},
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    CupertinoText(
        text = text,
        modifier = modifier,
        style = mergeTextStyle(
            style, color, fontSize, fontStyle, fontWeight, fontFamily,
            letterSpacing, textDecoration, textAlign, lineHeight,
        ),
        onTextLayout = onTextLayout ?: {},
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
private fun mergeTextStyle(
    style: TextStyle,
    color: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration?,
    textAlign: TextAlign?,
    lineHeight: TextUnit,
): TextStyle {
    // An explicit colour wins, then the style's own, then the inherited content colour
    // — the same precedence M3's Text applies.
    val resolved = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> IosTheme.contentColor
    }
    return style.merge(
        color = resolved,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        textDecoration = textDecoration,
        fontStyle = fontStyle,
        letterSpacing = letterSpacing,
    )
}

/** M3's ProvideTextStyle. */
@Composable
fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides value, content = content)
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) = CupertinoIcon(
    painter = painter,
    contentDescription = contentDescription,
    modifier = modifier,
    // Cupertino falls back to its own inherited colour; ours is the one call sites expect,
    // and Color.Unspecified would otherwise reach the tint as-is.
    tint = if (tint.isSpecified) tint else IosTheme.contentColor,
)

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) = Icon(rememberVectorPainter(imageVector), contentDescription, modifier, tint)

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) = Icon(
    BitmapPainter(bitmap),
    contentDescription,
    modifier,
    tint,
)

/**
 * iOS's spinner: twelve tapering spokes, not a Material arc. Cupertino's, which steps
 * spoke by spoke the way the platform one does.
 *
 * [strokeCap] is accepted and ignored — the spokes are always round-capped on iOS, and
 * dropping the parameter would break the ~30 call sites that pass M3's default.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.5.dp,
    strokeCap: StrokeCap = StrokeCap.Round,
) = CupertinoActivityIndicator(
    modifier = modifier,
    size = 20.dp,
    color = if (color.isSpecified) color else IosTheme.colors.secondaryLabel,
    strokeWidth = strokeWidth,
)

/**
 * Determinate variant, same call shape as M3's. Cupertino's indicator takes a progress
 * fraction on a second overload and lights that share of its spokes — iOS's determinate
 * spinner, rather than the arc M3 sweeps.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.5.dp,
    strokeCap: StrokeCap = StrokeCap.Round,
) = CupertinoActivityIndicator(
    modifier = modifier,
    progress = progress().coerceIn(0f, 1f),
    size = 20.dp,
    color = if (color.isSpecified) color else IosTheme.tint,
    strokeWidth = strokeWidth,
)

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val tint = if (color.isSpecified) color else IosTheme.tint
    val track = if (trackColor.isSpecified) trackColor else IosTheme.colors.systemGray5
    val transition = rememberInfiniteTransition(label = "LinearProgress")
    val animationProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "LinearProgressAnimation",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val h = size.height
        drawRoundRect(color = track, cornerRadius = CornerRadius(h / 2f))
        val segmentWidth = size.width * 0.3f
        val x = (size.width + segmentWidth) * animationProgress - segmentWidth
        drawRoundRect(
            color = tint,
            topLeft = Offset(x.coerceIn(0f, size.width - segmentWidth), 0f),
            size = Size(segmentWidth, h),
            cornerRadius = CornerRadius(h / 2f),
        )
    }
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val tint = if (color.isSpecified) color else IosTheme.tint
    val track = if (trackColor.isSpecified) trackColor else IosTheme.colors.systemGray5
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val h = size.height
        drawRoundRect(
            color = track,
            cornerRadius = CornerRadius(h / 2f),
        )
        drawRoundRect(
            color = tint,
            size = Size(size.width * progress().coerceIn(0f, 1f), h),
            cornerRadius = CornerRadius(h / 2f),
        )
    }
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.33.dp,
    color: Color = Color.Unspecified,
) = CupertinoHorizontalDivider(
    modifier = modifier,
    thickness = thickness,
    color = if (color.isSpecified) color else IosTheme.colors.separator,
)

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.33.dp,
    color: Color = Color.Unspecified,
) = CupertinoVerticalDivider(
    modifier = modifier,
    thickness = thickness,
    color = if (color.isSpecified) color else IosTheme.colors.separator,
)

/**
 * M3's IconButton, drawn iOS-style: no ripple, no 48dp Material touch circle drawn in
 * the surface — just the glyph, dimmed on press.
 *
 * The 44dp box stays ours: cupertino's icon button sizes itself to its glyph, and the
 * app's layouts were built against Apple's 44pt minimum target being reserved here.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    CupertinoIconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        enabled = enabled,
        // Plain: no filled capsule behind the glyph. The tint below is what the glyph
        // takes, and cupertino dims it on press for us.
        colors = CupertinoButtonDefaults.plainButtonColors(
            contentColor = IosTheme.tint,
            disabledContentColor = IosTheme.colors.tertiaryLabel,
        ),
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit,
) = CompatCupertinoButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    border = border,
    interactionSource = interactionSource,
    contentPadding = contentPadding,
    contentColor = colors?.let { if (enabled) it.contentColor else it.disabledContentColor } ?: IosTheme.tint,
    background = colors?.let { if (enabled) it.containerColor else it.disabledContainerColor } ?: Color.Unspecified,
    content = content,
)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = CompatCupertinoButton(
    onClick, modifier, enabled, shape, border, interactionSource, contentPadding,
    contentColor = colors?.let { if (enabled) it.contentColor else it.disabledContentColor } ?: IosTheme.colors.onAccent,
    background = colors?.let { if (enabled) it.containerColor else it.disabledContainerColor } ?: IosTheme.tint,
    content = content,
)

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = CompatCupertinoButton(
    onClick, modifier, enabled, shape, border, interactionSource, contentPadding,
    contentColor = colors?.let { if (enabled) it.contentColor else it.disabledContentColor } ?: IosTheme.tint,
    background = colors?.let { if (enabled) it.containerColor else it.disabledContainerColor } ?: IosTheme.colors.secondarySystemFill,
    content = content,
)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = CompatCupertinoButton(
    onClick, modifier, enabled, shape, border, interactionSource, contentPadding,
    contentColor = colors?.let { if (enabled) it.contentColor else it.disabledContentColor } ?: IosTheme.tint,
    background = colors?.let { if (enabled) it.containerColor else it.disabledContainerColor } ?: Color.Unspecified,
    outlined = true,
    content = content,
)

/**
 * The one button the four Material button roles above all resolve to: a [CupertinoButton]
 * whose fill and label colour the caller supplies.
 *
 * iOS has no elevation and no tonal ladder, so "filled", "tonal", "text" and "outlined"
 * differ only in those two colours plus whether a hairline is drawn — which is exactly
 * what the callers pass down. [shape] is honoured when a caller names one; otherwise the
 * capsule, which is what iOS uses at every size.
 */
@Composable
private fun CompatCupertinoButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    border: androidx.compose.foundation.BorderStroke?,
    interactionSource: MutableInteractionSource?,
    contentPadding: PaddingValues,
    contentColor: Color,
    background: Color,
    outlined: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    CupertinoButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 34.dp),
        enabled = enabled,
        shape = shape ?: IosCapsule,
        colors = CupertinoButtonDefaults.plainButtonColors(
            // Unspecified means "no fill" for the text/outlined roles. Cupertino needs a
            // real colour, and Transparent is that same nothing.
            containerColor = if (background.isSpecified) background else Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = if (background.isSpecified) background else Color.Transparent,
            disabledContentColor = IosTheme.colors.tertiaryLabel,
        ),
        border = border ?: if (outlined) {
            androidx.compose.foundation.BorderStroke(1.dp, IosTheme.colors.separator)
        } else {
            null
        },
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    ) {
        // Cupertino gives the slot a RowScope already; the spacing and the body text style
        // are ours, so a button's label keeps matching the rest of the app.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides IosTheme.typography.body,
                content = { content() },
            )
        }
    }
}
