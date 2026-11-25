/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Draws Android vector drawables to a PNG, old set over new set, so an icon redraw can be looked at
 * without a phone. Reads the real XML files, so what is drawn is what ships.
 */
class IconPreviewTest {
    private class Shape(val d: String, val fill: Boolean, val stroke: Boolean, val width: Float, val cap: StrokeCap, val join: StrokeJoin, val evenOdd: Boolean)

    private fun load(file: File): List<Shape> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(file)
        val paths = doc.getElementsByTagName("path")
        return (0 until paths.length).map { i ->
            val e = paths.item(i) as Element
            Shape(
                d = e.getAttribute("android:pathData"),
                fill = e.hasAttribute("android:fillColor"),
                stroke = e.hasAttribute("android:strokeColor"),
                width = e.getAttribute("android:strokeWidth").toFloatOrNull() ?: 0f,
                cap = if (e.getAttribute("android:strokeLineCap") == "round") StrokeCap.Round else StrokeCap.Butt,
                join = if (e.getAttribute("android:strokeLineJoin") == "round") StrokeJoin.Round else StrokeJoin.Miter,
                evenOdd = e.getAttribute("android:fillType") == "evenOdd",
            )
        }
    }

    private fun DrawScope.draw(shapes: List<Shape>, color: Color) {
        val unit = size.minDimension / 24f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            for (s in shapes) {
                val path = PathParser().parsePathString(s.d).toPath()
                path.fillType = if (s.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                if (s.fill) drawPath(path, color, style = Fill)
                if (s.stroke) drawPath(path, color, style = Stroke(s.width, cap = s.cap, join = s.join))
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun preview() {
        // Only when there is an old set to compare against: copy the drawables to <temp>/icons-old first.
        val temp = File(System.getProperty("java.io.tmpdir"))
        val oldDir = File(temp, "icons-old").takeIf { it.isDirectory } ?: return
        val newDir = File(System.getProperty("user.dir")).parentFile.resolve("app/src/main/res/drawable")
        val out = File(temp, "render").apply { mkdirs() }
        val names = listOf("home_outlined", "home_filled", "search", "search_filled", "radio", "radio_filled", "library_outlined", "library_filled")
        val accent = Color(0xFF5AA0FF)
        val scene = ImageComposeScene(1100, 420, Density(1f)) {
            Column(Modifier.background(Color(0xFF121216)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                for (dir in listOf(oldDir, newDir)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                        for (n in names) {
                            val shapes = load(File(dir, "$n.xml"))
                            val lit = n.endsWith("filled") || n == "search_filled"
                            Canvas(Modifier.size(96.dp)) { draw(shapes, if (lit) accent else Color(0xFFDADDE6)) }
                        }
                    }
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(out, "icons.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
    }
}
