/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.desktop.ui.glass.LiquidSlider
import com.ozyern.exhale.desktop.ui.glass.LiquidToggle
import com.ozyern.exhale.desktop.ui.glass.LocalPageBackdrop
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The liquid toggle and slider at rest, drawn over a coloured page, for judging by eye. */
class LiquidControlsPreviewTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() {
        val scene = ImageComposeScene(width = 900, height = 760, density = Density(2f)) {
            val page = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().layerBackdrop(page).background(
                        Brush.linearGradient(listOf(Color(0xFF1B1030), Color(0xFF3A1F5C), Color(0xFF0E2A3A))),
                    ),
                )
                CompositionLocalProvider(LocalPageBackdrop provides page) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            LiquidToggle(checked = true, onCheckedChange = {})
                            LiquidToggle(checked = false, onCheckedChange = {})
                            LiquidToggle(checked = true, onCheckedChange = {}, accent = Color(0xFFFF9F0A))
                            LiquidToggle(checked = true, onCheckedChange = {}, enabled = false)
                        }
                        LiquidSlider(value = 0.62f, onValueChange = {}, modifier = Modifier.width(380.dp))
                        LiquidSlider(value = 4f, onValueChange = {}, valueRange = -12f..12f, steps = 23, accent = Color(0xFFBF5AF2), modifier = Modifier.width(380.dp))
                        com.ozyern.exhale.desktop.ui.glass.LiquidSegmented(listOf("Off", "5.1", "7.1"), 1, {})
                        Box(Modifier.width(400.dp)) { com.ozyern.exhale.desktop.ui.glass.LiquidSegmented(listOf("Normal", "Sped up", "Nightcore", "Slowed", "Daycore", "Vaporwave"), 2, {}, accent = Color(0xFF30D158), fill = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            com.ozyern.exhale.desktop.ui.glass.LiquidButton(onClick = {}) { androidx.compose.material3.Text("Clear cache", color = Color.White) }
                            com.ozyern.exhale.desktop.ui.glass.LiquidButton(onClick = {}) { androidx.compose.material3.Text("Sign out", color = Color(0xFFFF6B6B)) }
                        }
                    }
                }
            }
        }
        // A few frames, so the springs that place everything have settled.
        repeat(30) { scene.render(it * 16_000_000L) }
        val image = scene.render(1_000_000_000L)
        val out = File(System.getProperty("java.io.tmpdir"), "exhale-liquid-controls.png")
        image.encodeToData(EncodedImageFormat.PNG)!!.bytes.let(out::writeBytes)
        println("saved ${out.absolutePath}")
        assertTrue(out.length() > 0)

        // Held: the first toggle's thumb, and the first slider's thumb dragged a little.
        var t = 1_000_000_000L
        fun frames(n: Int) = repeat(n) { t += 16_000_000L; scene.render(t) }
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(140f, 84f))
        frames(40)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(140f, 84f))
        frames(5)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(527f, 184f))
        frames(10)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(560f, 184f))
        frames(40)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(560f, 184f))
        frames(30)
        // The 6-way picker's pill, held and dragged toward the next segment.
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(370f, 494f))
        frames(10)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(420f, 494f))
        frames(30)
        // Hover the first button.
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(420f, 494f))
        frames(20)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(130f, 600f))
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(150f, 604f))
        frames(30)
        val held = scene.render(t + 16_000_000L)
        val heldOut = File(System.getProperty("java.io.tmpdir"), "exhale-liquid-controls-held.png")
        held.encodeToData(EncodedImageFormat.PNG)!!.bytes.let(heldOut::writeBytes)
        println("saved ${heldOut.absolutePath}")
        scene.close()
    }
}
