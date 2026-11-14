/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.ozyern.exhale.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.component.IosBadge
import com.ozyern.exhale.ui.ios.component.IosButton
import com.ozyern.exhale.ui.ios.component.IosButtonBorderShape
import com.ozyern.exhale.ui.ios.component.IosButtonRole
import com.ozyern.exhale.ui.ios.component.IosButtonSizing
import com.ozyern.exhale.ui.ios.component.IosButtonStyle
import com.ozyern.exhale.ui.ios.component.IosCheckmark
import com.ozyern.exhale.ui.ios.component.IosChevron
import com.ozyern.exhale.ui.ios.component.IosDetent
import com.ozyern.exhale.ui.ios.component.IosList
import com.ozyern.exhale.ui.ios.component.IosListStyle
import com.ozyern.exhale.ui.ios.component.IosMenu
import com.ozyern.exhale.ui.ios.component.IosMenuItem
import com.ozyern.exhale.ui.ios.component.IosMenuSection
import com.ozyern.exhale.ui.ios.component.IosRowStyle
import com.ozyern.exhale.ui.ios.component.IosNavigationRow
import com.ozyern.exhale.ui.ios.component.IosSheet
import com.ozyern.exhale.ui.ios.component.IosSlider
import com.ozyern.exhale.ui.ios.component.IosSliderTick
import com.ozyern.exhale.ui.ios.component.IosTab
import com.ozyern.exhale.ui.ios.component.IosTabBar
import com.ozyern.exhale.ui.ios.component.IosTabRole
import com.ozyern.exhale.ui.ios.component.IosText
import com.ozyern.exhale.ui.ios.component.IosToggle
import com.ozyern.exhale.ui.ios.component.IosToggleStyle
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosControlSize
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme

/**
 * Every component in one file, laid out for Android Studio's preview pane so the look
 * can be checked without installing the app.
 *
 * The glass surfaces fall back to a flat translucent fill in previews — Layoutlib has
 * no RenderEffect, so the refraction only shows on a device running API 31+.
 */

@Composable
private fun CatalogSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IosText(
            text = title,
            color = IosTheme.colors.secondaryLabel,
            style = IosTheme.typography.footnote,
        )
        content()
    }
}

/** A stand-in for artwork, so glass has something worth refracting. */
@Composable
private fun PhotoBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF9500),
                        Color(0xFFFF2D55),
                        Color(0xFF5856D6),
                        Color(0xFF32ADE6),
                    )
                )
            )
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CatalogScaffold(dark: Boolean, content: @Composable () -> Unit) {
    IosTheme(darkTheme = dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IosTheme.colors.systemGroupedBackground)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) { content() }
        }
    }
}

@Composable
private fun ButtonCatalog() {
    CatalogSection("Button styles") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IosButton("Tap Me", {}, style = IosButtonStyle.Plain)
            IosButton("Tap Me", {}, style = IosButtonStyle.Borderless)
            IosButton("Tap Me", {}, style = IosButtonStyle.Bordered)
            IosButton("Tap Me", {}, style = IosButtonStyle.BorderedProminent)
        }
    }

    CatalogSection("Button roles — destructive") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IosButton("Tap Me", {}, style = IosButtonStyle.Plain, role = IosButtonRole.Destructive)
            IosButton("Tap Me", {}, style = IosButtonStyle.Borderless, role = IosButtonRole.Destructive)
            IosButton("Tap Me", {}, style = IosButtonStyle.Bordered, role = IosButtonRole.Destructive)
            IosButton("Tap Me", {}, style = IosButtonStyle.BorderedProminent, role = IosButtonRole.Destructive)
        }
    }

    CatalogSection("Button tint") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IosButton("Green", {}, style = IosButtonStyle.Bordered, tint = IosTheme.colors.systemGreen)
            IosButton("Purple", {}, style = IosButtonStyle.BorderedProminent, tint = IosTheme.colors.systemPurple)
            IosButton("Orange", {}, style = IosButtonStyle.Borderless, tint = IosTheme.colors.systemOrange)
        }
    }

    CatalogSection("Button border shape") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IosButton("Capsule", {}, style = IosButtonStyle.BorderedProminent)
            IosButton(
                "Rounded", {},
                style = IosButtonStyle.BorderedProminent,
                borderShape = IosButtonBorderShape.RoundedRectangle(),
            )
            IosButton(
                "R12", {},
                style = IosButtonStyle.BorderedProminent,
                borderShape = IosButtonBorderShape.RoundedRectangle(12.dp),
            )
            IosButton(
                onClick = {},
                style = IosButtonStyle.BorderedProminent,
                borderShape = IosButtonBorderShape.Circle,
            ) { IosText("1") }
        }
    }

    CatalogSection("Control size — mini / small / regular / large / extraLarge") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IosControlSize.entries.forEach { size ->
                IosButton(
                    title = size.name.take(3),
                    onClick = {},
                    style = IosButtonStyle.Bordered,
                    controlSize = size,
                )
            }
        }
    }

    CatalogSection("Button sizing — flexible") {
        IosButton("Flexible", {}, style = IosButtonStyle.BorderedProminent, sizing = IosButtonSizing.Flexible)
    }

    CatalogSection("Glass button styles (flat fill in preview, refracted on device)") {
        PhotoBackdrop {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosButton("Tap Me", {}, style = IosButtonStyle.Glass)
                    IosButton("Tinted Tap Me", {}, style = IosButtonStyle.Glass, tint = IosTheme.colors.systemRed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosButton("Tap Me", {}, style = IosButtonStyle.GlassProminent)
                    IosButton("Tap Me", {}, style = IosButtonStyle.GlassProminent, tint = IosTheme.colors.systemRed)
                }
            }
        }
    }
}

@Composable
private fun ControlCatalog() {
    var switchOn by remember { mutableStateOf(true) }
    var checkOn by remember { mutableStateOf(true) }
    var buttonOn by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0.42f) }
    var stepped by remember { mutableFloatStateOf(40f) }

    CatalogSection("Toggle styles") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IosToggle(switchOn, { switchOn = it }, label = "Switch")
            IosToggle(checkOn, { checkOn = it }, label = "Checkbox", style = IosToggleStyle.Checkbox)
            IosToggle(buttonOn, { buttonOn = it }, label = "Button", style = IosToggleStyle.Button)
        }
    }

    CatalogSection("Slider") {
        IosSlider(value, { value = it })
    }

    CatalogSection("Stepped slider with min/max labels") {
        IosSlider(
            value = stepped,
            onValueChange = { stepped = it },
            valueRange = 0f..100f,
            step = 10f,
            minimumValueLabel = { IosText("0", color = IosTheme.colors.secondaryLabel) },
            maximumValueLabel = { IosText("100", color = IosTheme.colors.secondaryLabel) },
        )
    }

    CatalogSection("Custom slider ticks + tint") {
        IosSlider(
            value = value,
            onValueChange = { value = it },
            ticks = listOf(IosSliderTick(0.2f), IosSliderTick(0.6f), IosSliderTick(0.8f)),
            tint = IosTheme.colors.systemGreen,
        )
    }

    CatalogSection("Badge / checkmark / chevron") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IosBadge("42")
            IosBadge("")
            IosCheckmark()
            IosChevron()
        }
    }
}

@Composable
private fun SettingsListCatalog(dark: Boolean) {
    var wifi by remember { mutableStateOf(true) }
    var airplane by remember { mutableStateOf(false) }
    val colors = IosTheme.colors

    IosList(
        modifier = Modifier.fillMaxWidth().height(560.dp),
        style = IosListStyle.InsetGrouped,
    ) {
        section(key = "conn", header = { IosText("Connectivity") }) {
            row {
                IosToggle(
                    checked = airplane,
                    onCheckedChange = { airplane = it },
                    label = "Airplane Mode",
                )
            }
            row { IosToggle(wifi, { wifi = it }, label = "Wi-Fi") }
            row {
                IosNavigationRow(
                    title = "Bluetooth",
                    value = "On",
                    onClick = {},
                    icon = { IosText("B") },
                    iconBackground = colors.systemBlue,
                )
            }
        }

        section(
            key = "media",
            header = { IosText("Playback") },
            footer = { IosText("Lossless streams need a wired or USB output.") },
        ) {
            row {
                IosNavigationRow(
                    title = "Audio Quality",
                    subtitle = "Lossless up to 24-bit/192 kHz",
                    value = "High",
                    onClick = {},
                    icon = { IosText("A") },
                    iconBackground = colors.systemPink,
                )
            }
            row {
                IosNavigationRow(
                    title = "Equalizer",
                    onClick = {},
                    badge = "3",
                    icon = { IosText("E") },
                    iconBackground = colors.systemPurple,
                )
            }
            row(rowStyle = IosRowStyle(background = colors.systemBackground)) {
                IosText("Custom row background")
            }
        }

        section(key = "danger") {
            row {
                IosButton(
                    title = "Delete All Downloads",
                    onClick = {},
                    style = IosButtonStyle.Borderless,
                    role = IosButtonRole.Destructive,
                )
            }
        }
    }
}

@Composable
private fun PlainListCatalog() {
    IosList(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        style = IosListStyle.Plain,
    ) {
        section(key = "plain", header = { IosText("Header") }, footer = { IosText("Footer") }) {
            rows(listOf("Bar1", "Bar2", "Bar3")) { IosText(it) }
        }
    }
}

@Composable
private fun MenuCatalog() {
    val colors = IosTheme.colors
    CatalogSection("Menu") {
        PhotoBackdrop {
            IosMenu(
                sections = listOf(
                    IosMenuSection(
                        header = "foo",
                        items = listOf(
                            IosMenuItem(
                                title = "Button 3",
                                description = "Description 3",
                                icon = { tint -> IosText("3", color = tint) },
                            ),
                            IosMenuItem(
                                title = "Button 2",
                                icon = { tint -> IosText("2", color = tint) },
                                selected = true,
                            ),
                        ),
                    ),
                    IosMenuSection(
                        items = listOf(
                            IosMenuItem(title = "Button 1"),
                            IosMenuItem(title = "Delete", role = IosButtonRole.Destructive),
                        ),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun TabBarCatalog() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        IosTab("Home", { tint -> IosText("1", color = tint) }),
        IosTab("Library", { tint -> IosText("2", color = tint) }, badge = "42"),
        IosTab("Explore", { tint -> IosText("3", color = tint) }),
        IosTab("Search", { tint -> IosText("S", color = tint) }, role = IosTabRole.Search),
    )

    CatalogSection("Tab bar with bottom accessory") {
        PhotoBackdrop {
            IosTabBar(
                tabs = tabs,
                selectedIndex = selected,
                onSelect = { selected = it },
                accessory = { IosText("Now Playing — Foo") },
            )
        }
    }
}

@Composable
private fun SheetCatalog() {
    CatalogSection("Sheet — medium detent, drag indicator") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(ContinuousRoundedRectangle(20.dp))
                .background(IosTheme.colors.systemBackground)
        ) {
            IosSheet(
                isPresented = true,
                onDismissRequest = {},
                detents = listOf(IosDetent.Medium),
                animateEntrance = false,
                cornerRadius = IosMetrics.SheetCornerRadius,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IosText("Sheet Title", style = IosTheme.typography.title3)
                    IosText(
                        text = "Drag the indicator to resize between detents.",
                        color = IosTheme.colors.secondaryLabel,
                    )
                    IosButton("Done", {}, style = IosButtonStyle.BorderedProminent, sizing = IosButtonSizing.Flexible)
                }
            }
        }
    }
}

@Composable
private fun ColorCatalog() {
    val c = IosTheme.colors
    val swatches = listOf(
        "red" to c.systemRed, "orange" to c.systemOrange, "yellow" to c.systemYellow,
        "green" to c.systemGreen, "mint" to c.systemMint, "teal" to c.systemTeal,
        "cyan" to c.systemCyan, "blue" to c.systemBlue, "indigo" to c.systemIndigo,
        "purple" to c.systemPurple, "pink" to c.systemPink, "brown" to c.systemBrown,
    )
    CatalogSection("Standard colors") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.chunked(6).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { (_, color) ->
                        Box(Modifier.size(40.dp).clip(ContinuousRoundedRectangle(10.dp)).background(color))
                    }
                }
            }
        }
    }

    CatalogSection("Grays") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(c.systemGray, c.systemGray2, c.systemGray3, c.systemGray4, c.systemGray5, c.systemGray6)
                .forEach { Box(Modifier.size(40.dp).clip(IosCapsule).background(it)) }
        }
    }
}

@Composable
private fun TypographyCatalog() {
    val t = IosTheme.typography
    CatalogSection("Text styles") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IosText("Large Title", style = t.largeTitle)
            IosText("Title", style = t.title)
            IosText("Title 2", style = t.title2)
            IosText("Title 3", style = t.title3)
            IosText("Headline", style = t.headline)
            IosText("Body", style = t.body)
            IosText("Callout", style = t.callout)
            IosText("Subheadline", style = t.subheadline)
            IosText("Footnote", style = t.footnote)
            IosText("Caption", style = t.caption)
            IosText("Caption 2", style = t.caption2)
        }
    }
}

@Preview(name = "Buttons — light", showBackground = true, heightDp = 1100)
@Composable
private fun PreviewButtonsLight() = CatalogScaffold(dark = false) { ButtonCatalog() }

@Preview(name = "Buttons — dark", showBackground = true, heightDp = 1100, backgroundColor = 0xFF000000)
@Composable
private fun PreviewButtonsDark() = CatalogScaffold(dark = true) { ButtonCatalog() }

@Preview(name = "Controls — light", showBackground = true, heightDp = 900)
@Composable
private fun PreviewControlsLight() = CatalogScaffold(dark = false) { ControlCatalog() }

@Preview(name = "Controls — dark", showBackground = true, heightDp = 900, backgroundColor = 0xFF000000)
@Composable
private fun PreviewControlsDark() = CatalogScaffold(dark = true) { ControlCatalog() }

@Preview(name = "Settings list — light", showBackground = true, heightDp = 700)
@Composable
private fun PreviewSettingsLight() = IosTheme(darkTheme = false) { SettingsListCatalog(false) }

@Preview(name = "Settings list — dark", showBackground = true, heightDp = 700, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSettingsDark() = IosTheme(darkTheme = true) { SettingsListCatalog(true) }

@Preview(name = "Plain list", showBackground = true, heightDp = 300)
@Composable
private fun PreviewPlainList() = IosTheme(darkTheme = false) { PlainListCatalog() }

@Preview(name = "Menu + tab bar + sheet — light", showBackground = true, heightDp = 1200)
@Composable
private fun PreviewChromeLight() = CatalogScaffold(dark = false) {
    MenuCatalog()
    TabBarCatalog()
    SheetCatalog()
}

@Preview(name = "Menu + tab bar + sheet — dark", showBackground = true, heightDp = 1200, backgroundColor = 0xFF000000)
@Composable
private fun PreviewChromeDark() = CatalogScaffold(dark = true) {
    MenuCatalog()
    TabBarCatalog()
    SheetCatalog()
}

@Preview(name = "Foundations — light", showBackground = true, heightDp = 900)
@Composable
private fun PreviewFoundationsLight() = CatalogScaffold(dark = false) {
    ColorCatalog()
    TypographyCatalog()
}

@Preview(name = "Foundations — dark", showBackground = true, heightDp = 900, backgroundColor = 0xFF000000)
@Composable
private fun PreviewFoundationsDark() = CatalogScaffold(dark = true) {
    ColorCatalog()
    TypographyCatalog()
}

/** Everything at once, for a single scroll-through of the whole library. */
@Preview(name = "Everything — light", showBackground = true, widthDp = 400, heightDp = 3200)
@Composable
private fun PreviewEverythingLight() = CatalogScaffold(dark = false) {
    ButtonCatalog()
    ControlCatalog()
    MenuCatalog()
    TabBarCatalog()
    SheetCatalog()
    ColorCatalog()
    TypographyCatalog()
    Spacer(Modifier.height(8.dp))
}

@Preview(name = "Everything — dark", showBackground = true, widthDp = 400, heightDp = 3200, backgroundColor = 0xFF000000)
@Composable
private fun PreviewEverythingDark() = CatalogScaffold(dark = true) {
    ButtonCatalog()
    ControlCatalog()
    MenuCatalog()
    TabBarCatalog()
    SheetCatalog()
    ColorCatalog()
    TypographyCatalog()
    Spacer(Modifier.height(8.dp))
}
