/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.foundation.BottomRoundedShape
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import com.ozyern.exhale.ui.ios.foundation.TopRoundedShape

/** SwiftUI's ListStyle. */
enum class IosListStyle { Plain, Inset, Grouped, InsetGrouped, Sidebar }

/** SwiftUI's listRowSeparator(_:) / listSectionSeparator(_:). */
enum class IosSeparatorVisibility { Automatic, Visible, Hidden }

/** How one row is configured — the SwiftUI listRow* modifiers, gathered up. */
@Immutable
data class IosRowStyle(
    /** listRowBackground(_:) — Unspecified keeps the style's own row colour. */
    val background: Color = Color.Unspecified,
    /** listRowInsets(_:) — Null keeps the style default. */
    val insets: PaddingValues? = null,
    val separator: IosSeparatorVisibility = IosSeparatorVisibility.Automatic,
    /** listRowSeparatorTint(_:). */
    val separatorTint: Color = Color.Unspecified,
    val minHeight: Dp = IosMetrics.ListRowHeight,
)

/**
 * The DSL an [IosList] body is written in.
 *
 * Rows are collected as lambdas before anything is emitted, so a section knows its
 * own length and can round the first and last row's corners without giving up
 * laziness — each row still becomes its own lazy item.
 */
class IosListScope internal constructor(
    private val lazyScope: LazyListScope,
    private val style: IosListStyle,
) {
    /** A bare row, outside any section. */
    fun row(
        key: Any? = null,
        rowStyle: IosRowStyle = IosRowStyle(),
        content: @Composable RowScope.() -> Unit,
    ) = section(key = key) { row(rowStyle = rowStyle, content = content) }

    /** SwiftUI's Section, with the optional header and footer. */
    fun section(
        key: Any? = null,
        header: (@Composable () -> Unit)? = null,
        footer: (@Composable () -> Unit)? = null,
        content: IosSectionScope.() -> Unit,
    ) {
        val rows = IosSectionScope().apply(content).rows
        if (rows.isEmpty() && header == null && footer == null) return

        val grouped = style == IosListStyle.InsetGrouped || style == IosListStyle.Grouped ||
            style == IosListStyle.Inset || style == IosListStyle.Sidebar
        val horizontalInset = when (style) {
            IosListStyle.InsetGrouped, IosListStyle.Inset, IosListStyle.Sidebar -> IosMetrics.ListRowInset
            else -> 0.dp
        }

        if (header != null) {
            lazyScope.item(key = key?.let { "$it-header" }) {
                IosSectionHeader(header, horizontalInset, grouped)
            }
        }

        rows.forEachIndexed { index, row ->
            lazyScope.item(key = key?.let { "$it-$index" }) {
                IosListRowContainer(
                    style = style,
                    rowStyle = row.style,
                    isFirst = index == 0,
                    isLast = index == rows.lastIndex,
                    horizontalInset = horizontalInset,
                    content = row.content,
                )
            }
        }

        if (footer != null) {
            lazyScope.item(key = key?.let { "$it-footer" }) {
                IosSectionFooter(footer, horizontalInset)
            }
        }

        lazyScope.item(key = key?.let { "$it-spacer" }) {
            Box(Modifier.height(if (grouped) IosMetrics.SectionSpacing else 0.dp))
        }
    }
}

/** Collects a section's rows before they are emitted. */
class IosSectionScope internal constructor() {
    internal val rows = mutableListOf<SectionRow>()

    fun row(rowStyle: IosRowStyle = IosRowStyle(), content: @Composable RowScope.() -> Unit) {
        rows += SectionRow(rowStyle, content)
    }

    /** The common case of a homogeneous list of items. */
    fun <T> rows(
        items: List<T>,
        rowStyle: IosRowStyle = IosRowStyle(),
        content: @Composable RowScope.(T) -> Unit,
    ) = items.forEach { item -> row(rowStyle) { content(item) } }
}

internal data class SectionRow(
    val style: IosRowStyle,
    val content: @Composable RowScope.() -> Unit,
)

/**
 * SwiftUI's List.
 *
 * [IosListStyle.InsetGrouped] is the settings look: a grouped background with each
 * section on its own rounded card.
 */
@Composable
fun IosList(
    modifier: Modifier = Modifier,
    style: IosListStyle = IosListStyle.InsetGrouped,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = 20.dp),
    content: IosListScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val background = when (style) {
        IosListStyle.Plain, IosListStyle.Inset -> colors.systemBackground
        IosListStyle.Grouped, IosListStyle.InsetGrouped, IosListStyle.Sidebar ->
            colors.systemGroupedBackground
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(background),
        state = state,
        contentPadding = contentPadding,
    ) {
        IosListScope(this, style).content()
    }
}

@Composable
private fun IosSectionHeader(header: @Composable () -> Unit, inset: Dp, grouped: Boolean) {
    val colors = IosTheme.colors
    Box(
        Modifier.padding(
            start = inset + IosMetrics.ListRowInset,
            end = inset + IosMetrics.ListRowInset,
            bottom = if (grouped) 8.dp else 6.dp,
        )
    ) {
        CompositionLocalProvider(
            // iOS 26 dropped the all-caps grouped header: it is sentence case now.
            LocalIosTextStyle provides IosTheme.typography.subheadline,
            LocalIosContentColor provides colors.secondaryLabel,
        ) { header() }
    }
}

@Composable
private fun IosSectionFooter(footer: @Composable () -> Unit, inset: Dp) {
    val colors = IosTheme.colors
    Box(
        Modifier.padding(
            start = inset + IosMetrics.ListRowInset,
            end = inset + IosMetrics.ListRowInset,
            top = 8.dp,
        )
    ) {
        CompositionLocalProvider(
            LocalIosTextStyle provides IosTheme.typography.footnote,
            LocalIosContentColor provides colors.secondaryLabel,
        ) { footer() }
    }
}

@Composable
private fun IosListRowContainer(
    style: IosListStyle,
    rowStyle: IosRowStyle,
    isFirst: Boolean,
    isLast: Boolean,
    horizontalInset: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val carded = style == IosListStyle.InsetGrouped || style == IosListStyle.Inset ||
        style == IosListStyle.Sidebar

    val radius = IosMetrics.GroupedCornerRadius
    val shape: Shape = if (!carded) {
        RectangleShape
    } else {
        // Only the outer corners of a section round; the joins between rows stay square.
        when {
            isFirst && isLast -> ContinuousRoundedRectangle(radius)
            isFirst -> TopRoundedShape(radius)
            isLast -> BottomRoundedShape(radius)
            else -> RectangleShape
        }
    }

    val rowBackground = when {
        rowStyle.background.isSpecified -> rowStyle.background
        style == IosListStyle.Plain || style == IosListStyle.Grouped -> colors.systemBackground
        else -> colors.secondarySystemGroupedBackground
    }

    val insets = rowStyle.insets
        ?: PaddingValues(horizontal = IosMetrics.ListRowInset, vertical = 11.dp)

    val separatorVisible = when (rowStyle.separator) {
        IosSeparatorVisibility.Hidden -> false
        IosSeparatorVisibility.Visible -> true
        IosSeparatorVisibility.Automatic -> !isLast
    }

    Column(
        Modifier
            .padding(horizontal = horizontalInset)
            .clip(shape)
            .background(rowBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = rowStyle.minHeight)
                .padding(insets),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompositionLocalProvider(
                LocalIosTextStyle provides IosTheme.typography.body,
                LocalIosContentColor provides colors.label,
            ) { content() }
        }

        if (separatorVisible) {
            Box(
                Modifier
                    // The separator starts at the text, not the card edge — the inset
                    // that makes an iOS list read as a list.
                    .padding(start = IosMetrics.ListRowInset)
                    .fillMaxWidth()
                    .height(IosMetrics.SeparatorThickness)
                    .background(
                        if (rowStyle.separatorTint.isSpecified) rowStyle.separatorTint
                        else colors.separator
                    )
            )
        }
    }
}

/**
 * A settings row: leading icon tile, title, optional detail value, trailing chevron.
 * This is the shape almost every row in Settings.app takes.
 */
@Composable
fun IosNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    iconBackground: Color = Color.Unspecified,
    badge: String? = null,
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(IosMetrics.IconTileSize)
                    .clip(ContinuousRoundedRectangle(IosMetrics.IconTileRadius))
                    .background(if (iconBackground.isSpecified) iconBackground else IosTheme.tint),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalIosContentColor provides Color.White) { icon() }
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            IosText(title)
            if (subtitle != null) {
                IosText(
                    text = subtitle,
                    color = colors.secondaryLabel,
                    style = IosTheme.typography.footnote,
                )
            }
        }

        if (badge != null) IosBadge(badge)
        if (value != null) IosText(value, color = colors.secondaryLabel)
        IosChevron()
    }
}

/** The trailing disclosure chevron, drawn rather than pulled from an icon pack. */
@Composable
fun IosChevron(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val colors = IosTheme.colors
    val stroke = if (color.isSpecified) color else colors.tertiaryLabel
    Canvas(modifier.size(width = 8.dp, height = 13.dp)) {
        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width * 0.95f, size.height / 2f)
                lineTo(size.width * 0.15f, size.height)
            },
            color = stroke,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
