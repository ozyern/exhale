package com.ozyern.exhale.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.Image
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.artwork.ArtworkMesh
import com.ozyern.artwork.ArtworkMeshPalette
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.utils.selectedArtworkImage
import com.ozyern.exhale.ui.utils.artworkTransitionRunning
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow

private val meshPaletteCache = android.util.LruCache<String, ArtworkMeshPalette>(64)

data class PlaylistAppearance(val mesh: ArtworkMeshPalette, val background: Color, val foreground: Color)

/**
 * [seed] is an accent colour for pages with no artwork (auto playlists, empty local ones): the mesh is spun
 * from it until artwork arrives, so the background still moves through colour.
 */
@Composable
fun rememberPlaylistAppearance(imageUrl: String?, seed: Int? = null): PlaylistAppearance {
    val sampled = rememberMeshPalette(imageUrl)
    val seeded = remember(seed) { seed?.let(ArtworkMeshPalette::fromSeed) }
    val palette = if (seeded != null && sampled === ArtworkMeshPalette.Fallback) seeded else sampled
    return remember(palette) {
        val average = palette.sourceAverage
        val dark = average.red * .2126f + average.green * .7152f + average.blue * .0722f < .52f
        val neutral = if (dark) .055f else .94f
        val weight = if (dark) .38f else .30f
        PlaylistAppearance(
            palette,
            Color(average.red * weight + neutral * (1f - weight),
                average.green * weight + neutral * (1f - weight),
                average.blue * weight + neutral * (1f - weight)),
            if (dark) Color.White else Color.Black,
        )
    }
}

@Composable
private fun rememberMeshPalette(imageUrl: String?): ArtworkMeshPalette {
    val context = LocalContext.current
    val seed = selectedArtworkImage()
    var palette by remember(imageUrl) {
        val cached = imageUrl?.let { synchronized(meshPaletteCache) { meshPaletteCache.get(it) } }
        val fromSeed = if (cached == null && imageUrl != null && seed != null) {
            runCatching { meshPalette(seed) }.getOrNull()?.also { synchronized(meshPaletteCache) { meshPaletteCache.put(imageUrl, it) } }
        } else {
            null
        }
        mutableStateOf(cached ?: fromSeed ?: ArtworkMeshPalette.Fallback)
    }
    LaunchedEffect(imageUrl, seed) {
        if (imageUrl == null) return@LaunchedEffect
        synchronized(meshPaletteCache) { meshPaletteCache.get(imageUrl) }?.let { palette = it; return@LaunchedEffect }
        try {
            val image = seed ?: (context.imageLoader.execute(
                ImageRequest.Builder(context).data(imageUrl).size(160, 160).allowHardware(false).build(),
            ) as? SuccessResult)?.image
            if (image != null) {
                palette = withContext(Dispatchers.Default) { meshPalette(image) }
                synchronized(meshPaletteCache) { meshPaletteCache.put(imageUrl, palette) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            palette = ArtworkMeshPalette.Fallback
        }
    }
    return palette
}

@Composable
fun ArtworkMeshBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    paused: () -> Boolean = { false },
    appearance: PlaylistAppearance? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion by rememberPreference(ReduceDynamicBlurKey, false)
    val isPaused by rememberUpdatedState(paused)
    // Playlist pages (those passing an appearance) get a livelier mesh than the raw cover average.
    val palette = (appearance?.mesh ?: rememberMeshPalette(imageUrl)).let { sampled ->
        if (appearance == null) sampled else remember(sampled) { sampled.lifted(PlaylistMeshLift) }
    }
    val latestPalette by rememberUpdatedState(palette)
    val navAnimating = artworkTransitionRunning()
    val isNavigating by rememberUpdatedState(navAnimating)
    val mesh = remember { ArtworkMesh() }
    val bitmaps = remember(mesh) {
        val first = IntArray(mesh.width * mesh.height)
        mesh.render(palette, 0f, first)
        List(2) {
            Bitmap.createBitmap(mesh.width, mesh.height, Bitmap.Config.ARGB_8888)
                .also { it.setPixels(first, 0, mesh.width, 0, 0, mesh.width, mesh.height) }
        }
    }
    val images = remember(bitmaps) { bitmaps.map(Bitmap::asImageBitmap) }
    val displayed = remember(images) { mutableStateOf(images.first()) }

    LaunchedEffect(mesh, bitmaps, lifecycle, reduceMotion) {
        val pixels = IntArray(mesh.width * mesh.height)
        var phase = 0f
        var nextBitmap = 1
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousFrame = 0L
            var lastRendered = 0L
            while (true) {
                val frame = withFrameNanos { it }
                if (lastRendered != 0L && (isPaused() || isNavigating)) {
                    snapshotFlow { isPaused() || isNavigating }.first { !it }
                    previousFrame = 0L
                    continue
                }
                if (frame - lastRendered < 33_000_000L) continue
                val elapsed = if (previousFrame == 0L) 0f else ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .1f)
                previousFrame = frame
                lastRendered = frame
                val durationScale = currentCoroutineContext()[MotionDurationScale]
                val systemScale = durationScale?.scaleFactor ?: 1f
                val animate = !reduceMotion && systemScale > 0f
                if (animate) phase = (phase + ArtworkMesh.RadiansPerSecond * elapsed / systemScale) % ArtworkMesh.PhasePeriod
                val bitmap = bitmaps[nextBitmap]
                val renderedPalette = latestPalette
                withContext(Dispatchers.Default) {
                    mesh.render(renderedPalette, phase, pixels)
                    bitmap.setPixels(pixels, 0, mesh.width, 0, 0, mesh.width, mesh.height)
                }
                displayed.value = images[nextBitmap]
                nextBitmap = 1 - nextBitmap
                if (!animate) {
                    snapshotFlow { latestPalette to (durationScale?.scaleFactor ?: 1f) }
                        .first { (nextPalette, nextScale) -> nextPalette != renderedPalette || nextScale != systemScale }
                    previousFrame = 0L
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawImage(
            image = displayed.value,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        if (appearance != null) {
            drawRect(if (appearance.foreground == Color.White) Color.Black.copy(alpha = .03f) else Color.White.copy(alpha = .06f))
        } else {
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .04f), .52f to Color.Black.copy(alpha = .10f), 1f to Color.Black.copy(alpha = .48f)))
        }
    }
}

/** How far a playlist mesh is pulled toward white, so the drift reads as colour rather than shadow. */
private const val PlaylistMeshLift = 0.12f

private fun ArtworkMeshPalette.lifted(amount: Float): ArtworkMeshPalette {
    fun lift(c: com.ozyern.artwork.MeshColor) = com.ozyern.artwork.MeshColor(
        c.red + (1f - c.red) * amount,
        c.green + (1f - c.green) * amount,
        c.blue + (1f - c.blue) * amount,
    )
    return copy(cells = cells.map(::lift), average = lift(average))
}

private fun meshPalette(image: Image): ArtworkMeshPalette {
    val original = image.toBitmap()
    val resized = Bitmap.createScaledBitmap(original, 160, 160, true)
    val software = if (resized.config == Bitmap.Config.HARDWARE) resized.copy(Bitmap.Config.ARGB_8888, false) else resized
    try {
        val pixels = IntArray(160 * 160)
        software.getPixels(pixels, 0, 160, 0, 0, 160, 160)
        return ArtworkMeshPalette.fromPixels(pixels, 160, 160)
    } finally {
        if (software !== resized) software.recycle()
        if (resized !== original) resized.recycle()
    }
}
