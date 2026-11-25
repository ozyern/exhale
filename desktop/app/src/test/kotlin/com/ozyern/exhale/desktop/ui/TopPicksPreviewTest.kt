package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The Android Home "Top Picks for You" cards, drawn with live search artwork. */
class TopPicksPreviewTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() = runBlocking {
        val temp = File(System.getProperty("java.io.tmpdir"))
        assumeTrue(File(temp, "icons-old").isDirectory)
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val songs = listOf("espresso", "blinding lights", "tum hi ho").mapNotNull {
            YouTube.search(it, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
        }
        val http = OkHttpClient()
        val covers: List<Triple<SongItem, ImageBitmap, String>> = songs.mapIndexed { i, s ->
            val url = s.thumbnail.replace(Regex("""w[0-9]+-h[0-9]+"""), "w544-h544")
            val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
            Triple(s, SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(), listOf("KEEP LISTENING", "ON REPEAT", "YOUR PLAYLIST")[i])
        }
        val scene = ImageComposeScene(1170, 1000, Density(3f)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(top = 24.dp)) {
                Text("Top Picks for You", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    covers.forEach { (song, cover, eyebrow) ->
                        Column(Modifier.width(188.dp).clip(RoundedCornerShape(16.dp))) {
                            Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(188.dp))
                            Box(Modifier.fillMaxWidth().height(86.dp).clipToBounds()) {
                                Image(
                                    cover, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().graphicsLayer {
                                        scaleX = 3f; scaleY = 3f; translationY = -size.height * 0.9f
                                    }.blur(40.dp),
                                )
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.42f)))))
                                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(eyebrow, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
                                    Spacer(Modifier.height(2.dp))
                                    Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(song.artists.joinToString { it.name }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(File(temp, "render").apply { mkdirs() }, "toppicks.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
        Unit
    }
}
