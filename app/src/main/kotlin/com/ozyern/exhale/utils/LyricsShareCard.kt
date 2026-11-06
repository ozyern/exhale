/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * The lyric card, as Apple Music shares one: a card washed in the cover's colours, the cover small
 * at its top with the song and the artist beside it, the chosen lines large and bold under them, and
 * the app's own mark at the foot. As a story, the card floats on a soft field of the cover; as a
 * square, the card is the whole picture.
 *
 * One renderer for the preview and for the file: the sheet shows exactly the bitmap that is shared,
 * so the two can never disagree.
 */
object LyricsShareCard {
    enum class Format(val width: Int, val height: Int) {
        Story(1080, 1920),
        Square(1080, 1080),
    }

    /** The cover, once per song, for every render of its card. */
    suspend fun loadCover(context: Context, url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        val request = ImageRequest.Builder(context).data(url).size(720, 720).allowHardware(false).build()
        runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }

    suspend fun render(
        context: Context,
        cover: Bitmap?,
        title: String,
        artist: String,
        lyrics: String,
        format: Format,
    ): Bitmap = withContext(Dispatchers.Default) {
        @Suppress("NAME_SHADOWING")
        val title = cardTitle(title)
        val w = format.width
        val h = format.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val (top, bottom) = ComposeToImage.lyricCardColors(cover)

        val cardRect: RectF
        val cardRadius: Float
        if (format == Format.Story) {
            drawCoverField(canvas, cover, w, h, top)
            val cardWidth = w * 0.80f
            val content = measure(context, cardWidth, title, artist, lyrics, maxCardHeight = h * 0.78f)
            val left = (w - cardWidth) / 2f
            val topY = (h - content.height) / 2f
            cardRect = RectF(left, topY, left + cardWidth, topY + content.height)
            cardRadius = 52f
            // A soft shadow under the card, as a sticker sits on the story.
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x00000000
                setShadowLayer(48f, 0f, 18f, 0x66000000)
            }
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, shadow)
            drawCard(canvas, cardRect, cardRadius, top, bottom)
            drawContent(context, canvas, cardRect, cover, content)
        } else {
            cardRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            cardRadius = 0f
            drawCard(canvas, cardRect, cardRadius, top, bottom)
            val content = measure(context, w.toFloat(), title, artist, lyrics, maxCardHeight = h.toFloat())
            // Centred in the square: the card's content as one block.
            val offset = ((h - content.height) / 2f).coerceAtLeast(0f)
            drawContent(context, canvas, RectF(0f, offset, w.toFloat(), offset + content.height), cover, content)
        }
        out
    }

    /** YouTube's upload labels — "(Official Music Video)", "[Lyrics]" — are not the song's name. */
    private val UploadLabel = Regex(
        """\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|mv|hd|4k)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    internal fun cardTitle(title: String): String = title.replace(UploadLabel, "").trim().ifBlank { title }

    // ---- Layout -------------------------------------------------------------------------------

    private const val Pad = 64f
    private const val ArtSize = 136f
    private const val HeaderGap = 56f
    private const val FooterGap = 60f
    private const val LogoSize = 72f

    private class Content(
        val height: Float,
        val title: StaticLayout,
        val artist: StaticLayout,
        val lyrics: StaticLayout,
        val headerTextWidth: Int,
    )

    private fun boldFace(context: Context): Typeface =
        runCatching { ResourcesCompat.getFont(context, R.font.sfprodisplaybold) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE, spacing: Float = 1f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /**
     * Lays the card out at [cardWidth], shrinking the lyrics until the whole card fits in
     * [maxCardHeight] — a long selection gets smaller type, never a card off the edge.
     */
    private fun measure(context: Context, cardWidth: Float, title: String, artist: String, lyrics: String, maxCardHeight: Float): Content {
        val bold = boldFace(context)
        val inner = (cardWidth - 2 * Pad).toInt()
        val headerTextWidth = (inner - ArtSize - 32f).toInt()
        val titleLayout = layout(title, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = 40f
            color = 0xFFFFFFFF.toInt()
        }, headerTextWidth, maxLines = 2)
        val artistLayout = layout(artist, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 36f
            color = 0xB3FFFFFF.toInt()
        }, headerTextWidth, maxLines = 1)
        val fixed = Pad + ArtSize + HeaderGap + FooterGap + LogoSize + Pad
        var size = 72f
        var lyricsLayout: StaticLayout
        while (true) {
            lyricsLayout = layout(lyrics.trim(), TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold
                textSize = size
                color = 0xFFFFFFFF.toInt()
                letterSpacing = -0.01f
            }, inner, spacing = 1.08f)
            if (fixed + lyricsLayout.height <= maxCardHeight || size <= 34f) break
            size -= 4f
        }
        // Even at the smallest size, never past the card: what does not fit ends in an ellipsis.
        if (fixed + lyricsLayout.height > maxCardHeight) {
            val lineHeight = lyricsLayout.height / lyricsLayout.lineCount.coerceAtLeast(1)
            val lines = ((maxCardHeight - fixed) / lineHeight).toInt().coerceAtLeast(1)
            lyricsLayout = layout(lyrics.trim(), TextPaint(lyricsLayout.paint), inner, maxLines = lines, spacing = 1.08f)
        }
        return Content(fixed + lyricsLayout.height, titleLayout, artistLayout, lyricsLayout, headerTextWidth)
    }

    // ---- Drawing ------------------------------------------------------------------------------

    /** The story's ground: the cover enlarged into a soft field of its own colours, dimmed. */
    private fun drawCoverField(canvas: Canvas, cover: Bitmap?, w: Int, h: Int, fallback: Int) {
        if (cover == null) {
            canvas.drawColor(fallback)
            return
        }
        // The middle of the cover at the story's own shape — squeezing a square into 9:16 smeared it —
        // shrunk small, blurred there where it is cheap, and drawn back up with filtering.
        val targetRatio = w.toFloat() / h
        val srcW: Int
        val srcH: Int
        if (cover.width.toFloat() / cover.height > targetRatio) {
            srcH = cover.height
            srcW = (cover.height * targetRatio).toInt().coerceAtLeast(1)
        } else {
            srcW = cover.width
            srcH = (cover.width / targetRatio).toInt().coerceAtLeast(1)
        }
        val crop = Bitmap.createBitmap(cover, (cover.width - srcW) / 2, (cover.height - srcH) / 2, srcW, srcH)
        val small = Bitmap.createScaledBitmap(crop, 36, 64, true).copy(Bitmap.Config.ARGB_8888, true)
        repeat(3) { boxBlur(small, radius = 3) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        canvas.drawColor(0x38000000)
    }

    /** One horizontal and one vertical box pass over a small bitmap, in place; three approach a Gaussian. */
    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val tmp = IntArray(w * h)
        fun pass(from: IntArray, to: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var n = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val p = if (horizontal) from[o * w + j] else from[j * w + o]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                    val idx = if (horizontal) o * w + i else i * w + o
                    to[idx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(src, tmp, horizontal = true)
        pass(tmp, src, horizontal = false)
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun drawCard(canvas: Canvas, rect: RectF, radius: Float, top: Int, bottom: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (radius > 0f) {
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0x24FFFFFF
            })
        }
    }

    private fun drawContent(context: Context, canvas: Canvas, card: RectF, cover: Bitmap?, content: Content) {
        val left = card.left + Pad
        var y = card.top + Pad

        // The cover, small, with Apple's soft corner.
        val art = RectF(left, y, left + ArtSize, y + ArtSize)
        if (cover != null) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(art, 18f, 18f, Path.Direction.CW) })
            val side = minOf(cover.width, cover.height)
            val src = android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            canvas.drawBitmap(cover, src, art, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            canvas.drawRoundRect(art, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        }

        // The song and the artist beside it, centred on the cover as a block.
        val textBlock = content.title.height + 6f + content.artist.height
        val textX = art.right + 32f
        var textY = art.top + (ArtSize - textBlock) / 2f
        canvas.save()
        canvas.translate(textX, textY)
        content.title.draw(canvas)
        canvas.restore()
        textY += content.title.height + 6f
        canvas.save()
        canvas.translate(textX, textY)
        content.artist.draw(canvas)
        canvas.restore()

        // The lines.
        y = art.bottom + HeaderGap
        canvas.save()
        canvas.translate(left, y)
        content.lyrics.draw(canvas)
        canvas.restore()
        y += content.lyrics.height + FooterGap

        // The app's mark and its name, where Apple puts its own.
        val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.logo_gold) }.getOrNull()
        val logoRect = RectF(left - 8f, y - 4f, left - 8f + LogoSize, y - 4f + LogoSize)
        logo?.let { canvas.drawBitmap(it, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)) }
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFace(context)
            textSize = 40f
            color = 0xE6FFFFFF.toInt()
        }
        val baseline = logoRect.centerY() - (name.descent() + name.ascent()) / 2f
        canvas.drawText("Exhale", logoRect.right + 10f, baseline, name)
    }

    // ---- Out ----------------------------------------------------------------------------------

    /** A private copy for the share sheet: nothing lands in the gallery just because it was shared. */
    fun shareUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "images").also { it.mkdirs() }
        // Only the newest card is kept; each share used to leave another behind.
        dir.listFiles { f -> f.name.startsWith("lyrics_") }?.forEach { it.delete() }
        val file = File(dir, "lyrics_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }

    /** Into Pictures/Exhale, where the gallery finds it. True when it was written. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val name = "Exhale lyrics ${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Exhale")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No MediaStore record")
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("No output stream")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Exhale").also { it.mkdirs() }
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        true
    }.getOrDefault(false)
}
