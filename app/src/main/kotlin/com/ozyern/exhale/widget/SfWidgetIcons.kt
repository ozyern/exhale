package com.ozyern.exhale.widget

import com.composables.sfsymbols.monochrome.SFSpeakerWave2Fill
import com.composables.sfsymbols.monochrome.SFHeadphones
import com.composables.sfsymbols.monochrome.SFAirpodsPro
import com.composables.sfsymbols.monochrome.SFAirplayAudio
import com.composables.sfsymbols.monochrome.SFBackwardFill
import com.composables.sfsymbols.monochrome.SFForwardFill
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFPlayFill
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.composables.sfsymbols.SfSymbols

/** Draws the app's SF symbols into bitmaps, since RemoteViews cannot host Compose. */
internal object SfWidgetIcons {
    val Play: ImageVector get() = SfSymbols.Monochrome.SFPlayFill
    val Pause: ImageVector get() = SfSymbols.Monochrome.SFPauseFill
    val Next: ImageVector get() = SfSymbols.Monochrome.SFForwardFill
    val Previous: ImageVector get() = SfSymbols.Monochrome.SFBackwardFill
    val AirPlay: ImageVector get() = SfSymbols.Monochrome.SFAirplayAudio
    val AirPods: ImageVector get() = SfSymbols.Monochrome.SFAirpodsPro
    val Headphones: ImageVector get() = SfSymbols.Monochrome.SFHeadphones
    val Speaker: ImageVector get() = SfSymbols.Monochrome.SFSpeakerWave2Fill

    private val cache = object : android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun render(vector: ImageVector, sizePx: Int, color: Int): Bitmap {
        val key = "${vector.name}:$sizePx:$color"
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = minOf(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
        canvas.translate((sizePx - vector.viewportWidth * scale) / 2f, (sizePx - vector.viewportHeight * scale) / 2f)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        draw(vector.root, canvas, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    private fun draw(group: VectorGroup, canvas: Canvas, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> draw(node, canvas, paint)
                is VectorPath -> {
                    val path: Path = node.pathData.toPath().asAndroidPath()
                    path.fillType =
                        if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
    }
}
