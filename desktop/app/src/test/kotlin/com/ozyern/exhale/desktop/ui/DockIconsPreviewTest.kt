/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Test
import java.io.File

/**
 * The desktop dock's glyphs, resting over selected, drawn to a PNG so they can be laid beside the
 * Android drawables they were copied from. Opt in with EXHALE_RENDER_DOCK_ICONS=1.
 */
class DockIconsPreviewTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun preview() {
        if (System.getenv("EXHALE_RENDER_DOCK_ICONS") != "1") return
        val out = File(System.getProperty("java.io.tmpdir"), "render").apply { mkdirs() }
        val resting = listOf(DockIcons.Home, DockIcons.Explore, DockIcons.Library, DockIcons.Search)
        val selected = listOf(DockIcons.HomeSelected, DockIcons.ExploreSelected, DockIcons.LibrarySelected, DockIcons.SearchSelected)
        val scene = ImageComposeScene(560, 300, Density(1f)) {
            Column(Modifier.background(Color(0xFF121216)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    resting.forEach { Icon(it, null, tint = Color(0xFFDADDE6), modifier = Modifier.size(96.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    selected.forEach { Icon(it, null, tint = Color(0xFF5AA0FF), modifier = Modifier.size(96.dp)) }
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(out, "dock-icons.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
    }
}
