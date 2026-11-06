/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import android.view.View
import android.view.PixelCopy
import androidx.core.view.drawToBitmap
import com.ozyern.exhale.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max

object ComposeToImage {

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity      -> this
        is ContextWrapper -> baseContext.findActivity()
        else             -> null
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        val config = bitmap.config
        if (config != Bitmap.Config.HARDWARE && config != null) return bitmap
        return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bitmap
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pixelCopyViewBitmap(view: View): Bitmap? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        val activity = view.context.findActivity() ?: return null
        val window   = activity.window ?: return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect   = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = suspendCancellableCoroutine { cont ->
            PixelCopy.request(window, rect, bitmap, { cont.resume(it) }, Handler(Looper.getMainLooper()))
        }
        return if (result == PixelCopy.SUCCESS) bitmap else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API: capturing a View
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun captureViewBitmap(
        view: View,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        backgroundColor: Int? = null,
    ): Bitmap {
        val fallbackBitmap = runCatching { view.drawToBitmap() }.getOrElse {
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                backgroundColor?.let { Canvas(bmp).drawColor(it) }
            }
        }
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopyViewBitmap(view) ?: fallbackBitmap
        } else fallbackBitmap

        val needsScale = (targetWidth  != null && targetWidth  > 0 && targetWidth  != original.width) ||
                (targetHeight != null && targetHeight > 0 && targetHeight != original.height)
        val base = if (needsScale) {
            val safeOriginal = ensureSoftwareBitmap(original)
            val tw = targetWidth  ?: original.width
            val th = targetHeight ?: (original.height * tw / original.width)
            ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeOriginal, tw, th, true))
        } else ensureSoftwareBitmap(original)

        if (backgroundColor != null) {
            val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply { drawColor(backgroundColor); drawBitmap(base, 0f, 0f, null) }
            return out
        }
        return base
    }

    fun cropBitmap(source: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val safeLeft   = left.coerceIn(0, safeSource.width.coerceAtLeast(1) - 1)
        val safeTop    = top.coerceIn(0, safeSource.height.coerceAtLeast(1) - 1)
        val safeWidth  = width.coerceIn(1, safeSource.width - safeLeft)
        val safeHeight = height.coerceIn(1, safeSource.height - safeTop)
        return ensureSoftwareBitmap(Bitmap.createBitmap(safeSource, safeLeft, safeTop, safeWidth, safeHeight))
    }

    fun fitBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int, backgroundColor: Int): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val out    = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)
        val scale   = minOf(targetWidth.toFloat() / safeSource.width.coerceAtLeast(1),
            targetHeight.toFloat() / safeSource.height.coerceAtLeast(1))
        val scaledW = (safeSource.width  * scale).toInt().coerceAtLeast(1)
        val scaledH = (safeSource.height * scale).toInt().coerceAtLeast(1)
        val scaled  = if (scaledW != safeSource.width || scaledH != safeSource.height) {
            ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeSource, scaledW, scaledH, true))
        } else safeSource
        canvas.drawBitmap(scaled, ((targetWidth - scaled.width) / 2f), ((targetHeight - scaled.height) / 2f), null)
        return out
    }

    fun lyricCardColors(art: Bitmap?): Pair<Int, Int> {
        val fallback = 0xFF3A3A3C.toInt() to 0xFF1C1C1E.toInt()
        val palette = art?.let { runCatching { Palette.from(ensureSoftwareBitmap(it)).maximumColorCount(16).generate() }.getOrNull() }
            ?: return fallback
        val base = (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb ?: return fallback
        val deep = (palette.darkVibrantSwatch ?: palette.darkMutedSwatch)?.rgb ?: base
        return legibleUnderWhite(base, 0.42f) to legibleUnderWhite(deep, 0.2f)
    }

    private fun legibleUnderWhite(color: Int, maxLightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtMost(0.8f)
        hsl[2] = hsl[2].coerceAtMost(maxLightness)
        return ColorUtils.HSLToColor(hsl)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Saving a bitmap
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A private copy of [bitmap] for a share sheet. On Android 10 and up this used to go into
     * Pictures/Exhale, so every image shared also landed in the gallery; sharing is not saving.
     */
    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        val cachePath = File(context.cacheDir, "images").also { it.mkdirs() }
        val imageFile = File(cachePath, "$fileName.png")
        FileOutputStream(imageFile).use { safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", imageFile)
    }
}