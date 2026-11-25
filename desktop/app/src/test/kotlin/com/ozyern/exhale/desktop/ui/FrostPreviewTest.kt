package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Page heading under the mini player's pane: old frost (13dp, 0.30) vs new (28dp, 0.42). */
class FrostPreviewTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() {
        val temp = File(System.getProperty("java.io.tmpdir"))
        assumeTrue(File(temp, "icons-old").isDirectory)
        val scene = ImageComposeScene(1080, 560, Density(2.625f)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF5A0F2E)).padding(16.dp)) {
                for ((blur, tint) in listOf(13.dp to 0.30f, 28.dp to 0.42f)) Pane(blur, tint)
            }
        }
        File(File(temp, "render").apply { mkdirs() }, "frost.png").writeBytes(scene.render().encodeToData()!!.bytes)
        scene.close()
    }

    @androidx.compose.runtime.Composable
    private fun Pane(blur: Dp, tint: Float) {
        Box(Modifier.fillMaxWidth().height(80.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(40.dp))) {
            Text("Dancing on your own", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).blur(blur))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tint)))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 60.dp)) {
                Text("Espresso/Bed Chem (Live", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Sabrina Carpenter", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
