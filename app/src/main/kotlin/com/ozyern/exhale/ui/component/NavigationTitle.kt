/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ozyern.exhale.R

import com.ozyern.exhale.LocalAmbientBackgroundColor
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.LocalPageMetrics
import com.ozyern.exhale.ui.theme.LocalAccentTextColor
import com.ozyern.exhale.ui.theme.isHueless
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
) {
    // Headings stay neutral per theme; only hero screens keep the
    // artwork-derived tint (provided into this local), so a section title
    // matches the screen it is on.
    val ambient = LocalAmbientBackgroundColor.current.value
    val contentColor = color ?: if (ambient.isSpecified && !ambient.isHueless()) {
        LocalAccentTextColor.current
    } else {
        AppTheme.colorScheme.onSurface
    }
    val chevron = onClick != null && LocalPageMetrics.current.chevronHeaders

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(
                start = LocalPageMetrics.current.gutter,
                end = LocalPageMetrics.current.gutter,
                top = LocalPageMetrics.current.headerTop,
                bottom = LocalPageMetrics.current.headerBottom,
            )
    ) {
        thumbnail?.invoke()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                label?.let { label ->
                    Text(
                        text = label,
                        style = AppTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.6f),
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = title,
                    fontSize = AppleTokens.SectionHeader,
                    lineHeight = AppleTokens.SectionHeaderLineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                    color = contentColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
            // The whole header is clickable; the chevron marks the linked section.
            if (chevron) {
                // SF chevron.right at Title2 bold: a short heavy stroke about the
                // title's cap height. The monochrome SF vector is the regular weight
                // and reads thin and oversized beside a bold header, so draw it.
                val chevronColor = AppTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.padding(start = 6.dp, top = 1.dp).size(width = 9.dp, height = 15.dp)) {
                    val stroke = 2.6.dp.toPx()
                    val inset = stroke / 2
                    drawPath(
                        Path().apply {
                            moveTo(inset, inset)
                            lineTo(size.width - inset, size.height / 2)
                            lineTo(inset, size.height - inset)
                        },
                        color = chevronColor,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        val action = onPlayAllClick ?: onClick.takeUnless { chevron }
        if (action != null) {
            Text(
                text = stringResource(
                    if (onPlayAllClick != null) R.string.play_all else R.string.see_all
                ),
                fontSize = AppleTokens.ItemTitle,
                lineHeight = AppleTokens.ItemTitleLineHeight,
                fontWeight = FontWeight.Normal,
                color = contentColor.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.clickable(onClick = action),
            )
        }
    }
}
