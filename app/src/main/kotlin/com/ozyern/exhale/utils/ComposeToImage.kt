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
import com.ozyern.exhale.ui.component.LyricsBackgroundType
import com.ozyern.exhale.ui.component.LyricsCardConfig
import com.ozyern.exhale.ui.component.LyricsGlassStyle
import com.ozyern.exhale.ui.component.LyricsLayoutStyle
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

    // ─────────────────────────────────────────────────────────────────────────
    // Loading the artwork (shared by every renderer)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadCoverArt(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        return runCatching {
            val loader  = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).size(512).allowHardware(false).build()
            loader.execute(request).image?.toBitmap()
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: blurred artwork background + scrim
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawAlbumBackground(
        canvas: Canvas,
        cardSize: Int,
        coverArt: Bitmap?,
        dimAlpha: Float,
        cornerRadius: Float,
    ) {
        val rect = RectF(0f, 0f, cardSize.toFloat(), cardSize.toFloat())
        val path = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }

        if (coverArt != null) {
            // Start from the original, unscaled
            val scaled = ensureSoftwareBitmap(
                Bitmap.createScaledBitmap(
                    coverArt,
                    cardSize,
                    cardSize,
                    false  // Cambia a false para mejor calidad
                )
            )
            // A lighter blur
            val blurred = if (coverArt.width > cardSize * 2) {
                // A large original needs less reducing
                scaleBlurQuality(scaled, 12) // a lighter blur
            } else {
                scaleBlurQuality(scaled, 18)
            }
            canvas.withClip(path) { drawBitmap(blurred, 0f, 0f, null) }
        } else {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, Paint().apply {
                color = 0xFF0F0F0F.toInt(); isAntiAlias = true
            })
        }

        // Scrim
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, Paint().apply {
            color = android.graphics.Color.argb((dimAlpha * 255).toInt(), 0, 0, 0)
            isAntiAlias = true
        })
    }

    private fun scaleBlurQuality(source: Bitmap, strength: Int): Bitmap {
        val safe = ensureSoftwareBitmap(source)

        // An image that is already small is left as it is
        if (safe.width <= 400 || safe.height <= 400) {
            return safe
        }

        // A gentler reduction, to keep quality
        val targetSize = max(safe.width / 4, 200) // Máximo reducción a 1/4
        val smallW = targetSize
        val smallH = (safe.height * targetSize / safe.width).coerceAtLeast(100)

        val small = Bitmap.createScaledBitmap(safe, smallW, smallH, true)
        val result = Bitmap.createScaledBitmap(small, safe.width, safe.height, true)

        small.recycle() // Limpia memoria
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: a frosted panel on the canvas
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawFrostedPanel(
        canvas: Canvas,
        cardSize: Int,
        coverArt: Bitmap?,
        style: LyricsGlassStyle,
        panelRect: RectF,
        cornerRadius: Float,
    ) {
        val path = Path().apply { addRoundRect(panelRect, cornerRadius, cornerRadius, Path.Direction.CW) }

        if (coverArt != null) {
            val left   = panelRect.left.toInt().coerceIn(0, cardSize - 1)
            val top    = panelRect.top.toInt().coerceIn(0, cardSize - 1)
            val width  = panelRect.width().toInt().coerceIn(1, cardSize - left)
            val height = panelRect.height().toInt().coerceIn(1, cardSize - top)
            val scaled = ensureSoftwareBitmap(Bitmap.createScaledBitmap(coverArt, cardSize, cardSize, true))
            val crop   = Bitmap.createBitmap(scaled, left, top, width, height)
            val frosted = scaleBlur(crop, 12)
            canvas.withClip(path) { drawBitmap(frosted, panelRect.left, panelRect.top, null) }
        }

        fun composeColor(c: androidx.compose.ui.graphics.Color, a: Float): Int =
            android.graphics.Color.argb((a * 255).toInt(),
                (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, Paint().apply {
            color = composeColor(style.surfaceTint, style.surfaceAlpha); isAntiAlias = true
        })
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, Paint().apply {
            color = composeColor(style.overlayColor, style.overlayAlpha); isAntiAlias = true
        })
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, Paint().apply {
            this.style = Paint.Style.STROKE; strokeWidth = 1.5f
            color = android.graphics.Color.argb(25, 255, 255, 255); isAntiAlias = true
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: lyrics text, fitted to the height
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildFittedLyricsLayout(
        lyrics: String,
        paint: TextPaint,
        maxWidth: Int,
        availableHeight: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        initialTextSize: Float,
        minTextSize: Float = 22f,
        lineSpacing: Float = 1.35f,
    ): StaticLayout {
        var textSize = initialTextSize
        var layout: StaticLayout
        do {
            paint.textSize = textSize
            layout = StaticLayout.Builder.obtain(lyrics, 0, lyrics.length, paint, maxWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(if (lineSpacing < 1.3f) 0f else 8f, lineSpacing)
                .setMaxLines(12)
                .build()
            if (layout.height > availableHeight) textSize -= 2f else break
        } while (textSize > minTextSize)
        return layout
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: the song row (artwork, title, artist)
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawMetadataRow(
        canvas: Canvas,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        cardSize: Int,
        contentLeft: Float,
        contentTop: Float,
        contentRight: Float,
        showCover: Boolean,
        showTitle: Boolean,
        showArtist: Boolean,
        mainTextColor: Int,
        secondaryTextColor: Int,
    ) {
        val coverSize       = (cardSize * 0.16f)
        val imageCornerRadius = cardSize * 0.035f
        var textStartX      = contentLeft

        if (showCover && coverArt != null) {
            val rect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
            val path = Path().apply { addRoundRect(rect, imageCornerRadius, imageCornerRadius, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArt, null, rect, null) }
            canvas.drawRoundRect(rect, imageCornerRadius, imageCornerRadius, Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1f
                color = android.graphics.Color.argb(38, 255, 255, 255); isAntiAlias = true
            })
            textStartX = contentLeft + coverSize + cardSize * 0.04f
        }

        val titlePaint = TextPaint().apply {
            color    = mainTextColor
            textSize = cardSize * 0.038f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true; letterSpacing = -0.02f
        }
        val artistPaint = TextPaint().apply {
            color    = secondaryTextColor
            textSize = cardSize * 0.028f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textMaxWidth = (contentRight - textStartX).toInt().coerceAtLeast(1)
        if (showTitle) {
            val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(1).build()
            val artistLayout = StaticLayout.Builder.obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(1).build()

            val imageCenter     = contentTop + (if (showCover && coverArt != null) coverSize / 2f else titleLayout.height / 2f)
            val textBlockHeight = titleLayout.height + (if (showArtist) artistLayout.height + 6f else 0f)
            val textBlockY      = imageCenter - textBlockHeight / 2f

            canvas.withTranslation(textStartX, textBlockY) {
                titleLayout.draw(this)
                if (showArtist) {
                    translate(0f, titleLayout.height.toFloat() + 6f)
                    artistLayout.draw(this)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: the Exhale signature
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawBranding(
        context: Context,
        canvas: Canvas,
        cardSize: Int,
        x: Float,
        y: Float,
        circleColor: Int,
        logoTint: Int,
        textColor: Int,
    ) {
        // The app's own icon, in colour, the way Apple Music signs its cards with its icon.
        val baseSize  = cardSize.toFloat()
        val logoSize  = (baseSize * 0.055f).toInt()
        val appNamePaint = TextPaint().apply {
            color    = textColor; textSize = baseSize * 0.028f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true; letterSpacing = 0.02f
        }
        val circleRadius = logoSize / 2f
        val circleX = x + circleRadius
        val circleY = y - circleRadius
        drawAppIcon(context, canvas, circleX - circleRadius, circleY - circleRadius, logoSize.toFloat())
        canvas.drawText(
            context.getString(R.string.app_name),
            circleX + circleRadius + 10f,
            circleY + appNamePaint.textSize * 0.3f,
            appNamePaint,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ▸ MAIN API: picks the renderer for the config
    // ─────────────────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImageWithConfig(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        outputSize: Int = 1080,
    ): Bitmap = withContext(Dispatchers.Default) {
        val coverArt = loadCoverArt(context, coverArtUrl)
        when (config.layoutStyle) {
            LyricsLayoutStyle.Vivid          -> renderVivid(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.GlassCard      -> renderGlassCard(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.Minimal        -> renderMinimal(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.CoverFocused   -> renderCoverFocused(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.Centered       -> renderCentered(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.BlurWash       -> renderBlurWash(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
            LyricsLayoutStyle.StreamingModern -> renderStreamingModern(context, coverArt, songTitle, artistName, lyrics, config, outputSize)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ▸ LEGACY API: unchanged, backward compatible
    // ─────────────────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        glassStyle: LyricsGlassStyle? = null,
    ): Bitmap = createLyricsImageWithConfig(
        context    = context,
        coverArtUrl = coverArtUrl,
        songTitle  = songTitle,
        artistName = artistName,
        lyrics     = lyrics,
        config     = LyricsCardConfig(glassStyle = glassStyle ?: LyricsGlassStyle.FrostedDark),
        outputSize = minOf(width, height),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 1 — Glass Card (matches the Compose design)
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderGlassCard(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val style         = config.glassStyle
        val bitmap        = createBitmap(cardSize, cardSize)
        val canvas        = Canvas(bitmap)
        val outerCorner   = cardSize * 0.06f
        val margin        = cardSize * 0.04f
        val panelRect     = RectF(margin, margin, cardSize - margin, cardSize - margin)
        val panelCorner   = cardSize * 0.05f
        val contentPad    = (config.cardPadding.value / 340f) * cardSize

        // Background
        drawAlbumBackground(canvas, cardSize, coverArt, style.backgroundDimAlpha, outerCorner)
        // Frosted panel
        drawFrostedPanel(canvas, cardSize, coverArt, style, panelRect, panelCorner)

        val mainColor = style.textColor.toArgb()
        val secColor  = style.secondaryTextColor.toArgb()
        val contentLeft  = panelRect.left + contentPad
        val contentRight = panelRect.right - contentPad
        val contentTop   = panelRect.top + contentPad

        // Metadata
        if (config.showTitle || config.showCoverArt || config.showArtist) {
            drawMetadataRow(canvas, coverArt, songTitle, artistName, cardSize,
                contentLeft, contentTop, contentRight,
                config.showCoverArt, config.showTitle, config.showArtist,
                mainColor, secColor)
        }

        // Letras
        val lyricsPaint    = TextPaint().apply { color = mainColor; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.01f }
        val lyricsMaxWidth = (panelRect.width() * 0.85f).toInt()
        val logoH          = (panelRect.height() * 0.09f)
        val lyricsTop      = panelRect.top + panelRect.height() * 0.24f
        val lyricsBottom   = panelRect.bottom - (logoH + contentPad)

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics           = lyrics,
            paint            = lyricsPaint,
            maxWidth         = lyricsMaxWidth,
            availableHeight  = lyricsBottom - lyricsTop,
            alignment        = config.textAlign.toLayoutAlignment(),
            initialTextSize  = (cardSize * 0.055f) * config.textSizeMultiplier,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation(panelRect.left + (panelRect.width() - lyricsMaxWidth) / 2f, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Branding
        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x          = contentLeft,
                y          = panelRect.bottom - contentPad * 0.4f,
                circleColor = secColor,
                logoTint    = if (style.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor   = secColor)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 2 — Minimal
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderMinimal(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val isDark       = config.glassStyle.isDark
        val bgColor      = if (isDark) 0xFF0F0F0F.toInt() else 0xFFF5F5F5.toInt()
        val mainColor    = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
        val secondaryClr = if (isDark) 0x80FFFFFF.toInt() else 0x801A1A1A.toInt()
        val accentCompose = config.glassStyle.surfaceTint
        val accent = if (accentCompose == androidx.compose.ui.graphics.Color.Black ||
            accentCompose == androidx.compose.ui.graphics.Color.White)
            0xFF6366F1.toInt()
        else accentCompose.copy(alpha = 1f).toArgb()

        val bitmap  = createBitmap(cardSize, cardSize)
        val canvas  = Canvas(bitmap)
        val corner  = cardSize * 0.06f
        val padding = (config.cardPadding.value / 340f) * cardSize
        val size    = cardSize.toFloat()

        // Solid background
        canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, Paint().apply {
            color = bgColor; isAntiAlias = true
        })

        // Accent line along the top
        val accentPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(0f, 0f, size * 0.5f, 0f,
                accent, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(RectF(0f, 0f, size, 4f), accentPaint)

        // Comilla decorativa
        val quotePaint = TextPaint().apply {
            color    = (accent and 0x00FFFFFF) or 0x3F000000 // accent @ 25% alpha
            textSize = cardSize * 0.23f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("\u201C", padding, padding + quotePaint.textSize * 0.7f, quotePaint)

        // Letras
        val lyricsPaint = TextPaint().apply {
            color    = mainColor
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val lyricsMaxWidth  = (size - padding * 2).toInt()
        val quoteSpaceUsed  = cardSize * 0.18f
        val bottomReserved  = cardSize * 0.25f
        val lyricsAreaTop   = padding + quoteSpaceUsed
        val lyricsAreaH     = size - lyricsAreaTop - bottomReserved

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = lyricsMaxWidth,
            availableHeight = lyricsAreaH,
            alignment       = config.textAlign.toLayoutAlignment(),
            initialTextSize = (cardSize * 0.065f) * config.textSizeMultiplier,
            minTextSize     = 24f,
        )
        val lyricsY = lyricsAreaTop + (lyricsAreaH - lyricsLayout.height) / 2f
        canvas.withTranslation(padding, lyricsY) { lyricsLayout.draw(this) }

        // Divider and song row
        val metaTop = size - bottomReserved + padding * 0.5f
        canvas.drawLine(padding, metaTop, size - padding, metaTop, Paint().apply {
            color = (mainColor and 0x00FFFFFF) or 0x1A000000; strokeWidth = 1.5f; isAntiAlias = true
        })
        val metaY = metaTop + padding * 0.8f
        val titlePaint = TextPaint().apply {
            color = mainColor; textSize = cardSize * 0.036f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val artistPaint = TextPaint().apply {
            color = secondaryClr; textSize = cardSize * 0.028f; isAntiAlias = true
        }
        if (config.showTitle) canvas.drawText(songTitle, padding, metaY, titlePaint)
        if (config.showArtist) canvas.drawText(artistName, padding, metaY + cardSize * 0.044f, artistPaint)

        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x = padding, y = size - padding * 0.2f,
                circleColor = secondaryClr, logoTint = if (isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secondaryClr)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 3 — Cover Focused
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderCoverFocused(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val style      = config.glassStyle
        val mainColor  = style.textColor.toArgb()
        val secColor   = style.secondaryTextColor.toArgb()
        val bitmap     = createBitmap(cardSize, cardSize)
        val canvas     = Canvas(bitmap)
        val corner     = cardSize * 0.06f
        val padding    = (config.cardPadding.value / 340f) * cardSize
        val size       = cardSize.toFloat()

        // Blurred background
        drawAlbumBackground(canvas, cardSize, coverArt, 0.55f, corner)

        // Large artwork, centred in the top half
        if (config.showCoverArt && coverArt != null) {
            val artSize   = size * 0.4f
            val artLeft   = (size - artSize) / 2f
            val artTop    = padding
            val artRect   = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
            val artCorner = cardSize * 0.04f
            val path = Path().apply { addRoundRect(artRect, artCorner, artCorner, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArt, null, artRect, null) }
            canvas.drawRoundRect(artRect, artCorner, artCorner, Paint().apply {
                this.style = Paint.Style.STROKE; strokeWidth = 2f
                color = android.graphics.Color.argb(51, 255, 255, 255); isAntiAlias = true
            })
        }

        // Title and artist under the artwork
        val artBottom = padding + size * 0.4f + padding * 0.5f
        val titlePaint = TextPaint().apply {
            color = mainColor; textSize = cardSize * 0.042f; typeface = Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; letterSpacing = -0.02f
        }
        val artistPaint = TextPaint().apply {
            color = secColor; textSize = cardSize * 0.030f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        if (config.showTitle) canvas.drawText(songTitle, size / 2f, artBottom, titlePaint)
        if (config.showArtist) canvas.drawText(artistName, size / 2f, artBottom + cardSize * 0.05f, artistPaint)

        // Divider
        val divY = artBottom + cardSize * 0.09f
        canvas.drawLine(padding * 2, divY, size - padding * 2, divY, Paint().apply {
            color = android.graphics.Color.argb(38, 255, 255, 255); strokeWidth = 1.5f; isAntiAlias = true
        })

        // Lyrics in the bottom half
        val lyricsPaint = TextPaint().apply {
            color = mainColor; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.01f
        }
        val lyricsMaxWidth  = (size * 0.82f).toInt()
        val logoH           = cardSize * 0.09f
        val lyricsTop       = divY + padding * 0.8f
        val lyricsBottom    = size - (if (config.showBranding) logoH else padding)

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = lyricsMaxWidth,
            availableHeight = lyricsBottom - lyricsTop,
            alignment       = config.textAlign.toLayoutAlignment(),
            initialTextSize = (cardSize * 0.048f) * config.textSizeMultiplier,
            minTextSize     = 20f,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation((size - lyricsMaxWidth) / 2f, lyricsY) { lyricsLayout.draw(this) }

        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x = padding, y = size - padding * 0.2f,
                circleColor = secColor, logoTint = if (style.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secColor)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 4 — Centered
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderCentered(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val style     = config.glassStyle
        val mainColor = style.textColor.toArgb()
        val secColor  = style.secondaryTextColor.toArgb()
        val bitmap    = createBitmap(cardSize, cardSize)
        val canvas    = Canvas(bitmap)
        val corner    = cardSize * 0.06f
        val padding   = (config.cardPadding.value / 340f) * cardSize
        val size      = cardSize.toFloat()

        // Background with a three-stop scrim
        drawAlbumBackground(canvas, cardSize, coverArt, 0.60f, corner)
        // Extra scrim at the edges
        val extraScrimPaint = Paint().apply {
            shader = RadialGradient(size / 2f, size / 2f, size * 0.5f,
                intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(100, 0, 0, 0)),
                floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, extraScrimPaint)

        // Metadatos arriba centrados
        val titlePaint = TextPaint().apply {
            color = mainColor; textSize = cardSize * 0.038f; typeface = Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; letterSpacing = 0.01f
        }
        val artistPaint = TextPaint().apply {
            color = secColor; textSize = cardSize * 0.028f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; letterSpacing = 0.04f
        }
        var headerBottom = padding
        if (config.showTitle) {
            canvas.drawText(songTitle, size / 2f, padding + cardSize * 0.05f, titlePaint)
            headerBottom = padding + cardSize * 0.07f
        }
        if (config.showArtist) {
            canvas.drawText(artistName, size / 2f, headerBottom + cardSize * 0.04f, artistPaint)
            headerBottom += cardSize * 0.05f
        }

        // Lyrics centred in the room left
        val lyricsPaint = TextPaint().apply {
            color = mainColor; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f
        }
        val lyricsMaxWidth = (size * 0.82f).toInt()
        val logoH          = if (config.showBranding) cardSize * 0.09f else 0f
        val lyricsAreaTop  = headerBottom + padding * 0.5f
        val lyricsAreaBottom = size - logoH - padding

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = lyricsMaxWidth,
            availableHeight = lyricsAreaBottom - lyricsAreaTop,
            alignment       = Layout.Alignment.ALIGN_CENTER,
            initialTextSize = (cardSize * 0.068f) * config.textSizeMultiplier,
            minTextSize     = 22f,
        )
        val lyricsY = lyricsAreaTop + (lyricsAreaBottom - lyricsAreaTop - lyricsLayout.height) / 2f
        canvas.withTranslation((size - lyricsMaxWidth) / 2f, lyricsY) { lyricsLayout.draw(this) }

        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x = padding, y = size - padding * 0.2f,
                circleColor = secColor, logoTint = if (style.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secColor)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 5 — Blur Wash
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderBlurWash(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val style     = config.glassStyle
        val mainColor = style.textColor.toArgb()
        val secColor  = style.secondaryTextColor.toArgb()
        val bitmap    = createBitmap(cardSize, cardSize)
        val canvas    = Canvas(bitmap)
        val corner    = cardSize * 0.06f
        val padding   = (config.cardPadding.value / 340f) * cardSize
        val size      = cardSize.toFloat()

        // Background at full blur (30)
        if (coverArt != null) {
            val scaled  = ensureSoftwareBitmap(Bitmap.createScaledBitmap(coverArt, cardSize, cardSize, true))
            val blurred = scaleBlur(scaled, 30)
            val path    = Path().apply { addRoundRect(RectF(0f, 0f, size, size), corner, corner, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(blurred, 0f, 0f, null) }
        } else {
            canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, Paint().apply {
                color = 0xFF121232.toInt(); isAntiAlias = true
            })
        }
        canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, Paint().apply {
            color = android.graphics.Color.argb(96, 0, 0, 0); isAntiAlias = true
        })

        // Song header
        val metaBottom = if (config.showTitle || config.showCoverArt || config.showArtist) {
            drawMetadataRow(canvas, coverArt, songTitle, artistName, cardSize,
                padding, padding, size - padding,
                config.showCoverArt, config.showTitle, config.showArtist,
                mainColor, secColor)
            padding + cardSize * 0.20f
        } else padding

        // Frosted panel for the lyrics
        val panelPad    = padding * 0.5f
        val logoH       = if (config.showBranding) cardSize * 0.10f else 0f
        val panelTop    = metaBottom + panelPad
        val panelBottom = size - logoH - panelPad
        val panelRect   = RectF(panelPad, panelTop, size - panelPad, panelBottom)
        val panelCorner = cardSize * 0.04f

        drawFrostedPanel(canvas, cardSize, coverArt, style, panelRect, panelCorner)

        // Lyrics on the panel
        val lyricsPaint = TextPaint().apply {
            color = mainColor; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.01f
        }
        val innerPad       = panelRect.width() * 0.06f
        val lyricsMaxWidth = (panelRect.width() - innerPad * 2).toInt()
        val lyricsLayout   = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = lyricsMaxWidth,
            availableHeight = panelRect.height() - innerPad * 2,
            alignment       = config.textAlign.toLayoutAlignment(),
            initialTextSize = (cardSize * 0.052f) * config.textSizeMultiplier,
            minTextSize     = 20f,
        )
        val lyricsX = panelRect.left + innerPad
        val lyricsY = panelRect.top + (panelRect.height() - lyricsLayout.height) / 2f
        canvas.withTranslation(lyricsX, lyricsY) { lyricsLayout.draw(this) }

        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x = padding, y = size - padding * 0.2f,
                circleColor = secColor, logoTint = if (style.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secColor)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 6 — Streaming Modern
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderStreamingModern(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val accentCompose = config.glassStyle.surfaceTint
        val accentArgb    = if (accentCompose == androidx.compose.ui.graphics.Color.Black ||
            accentCompose == androidx.compose.ui.graphics.Color.White)
            0xFF6366F1.toInt() else accentCompose.copy(alpha = 1f).toArgb()

        val mainColor = 0xFFFFFFFF.toInt()
        val secColor  = 0x8CFFFFFF.toInt()
        val bitmap    = createBitmap(cardSize, cardSize)
        val canvas    = Canvas(bitmap)
        val corner    = cardSize * 0.06f
        val padding   = (config.cardPadding.value / 340f) * cardSize
        val size      = cardSize.toFloat()

        // Dark gradient background
        canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, Paint().apply {
            shader = LinearGradient(0f, 0f, size, size,
                intArrayOf(0xFF0D0D1A.toInt(), 0xFF191928.toInt()),
                null, Shader.TileMode.CLAMP)
            isAntiAlias = true
        })

        // Accent glow in the top right corner
        canvas.drawCircle(size * 0.85f, -size * 0.12f, size * 0.55f, Paint().apply {
            shader = RadialGradient(size * 0.85f, -size * 0.12f, size * 0.55f,
                intArrayOf((accentArgb and 0x00FFFFFF) or 0x38000000, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            isAntiAlias = true
        })

        // Header
        var headerY = padding
        if (config.showCoverArt && coverArt != null) {
            val artSize = size * 0.13f
            val artRect = RectF(padding, padding, padding + artSize, padding + artSize)
            val artCorner = cardSize * 0.025f
            val path = Path().apply { addRoundRect(artRect, artCorner, artCorner, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArt, null, artRect, null) }
            canvas.drawRoundRect(artRect, artCorner, artCorner, Paint().apply {
                this.style = Paint.Style.STROKE; strokeWidth = 1.5f
                shader = LinearGradient(artRect.left, artRect.top, artRect.right, artRect.bottom,
                    intArrayOf((accentArgb and 0x00FFFFFF) or 0x66000000, android.graphics.Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
                isAntiAlias = true
            })

            val titlePaint = TextPaint().apply {
                color = mainColor; textSize = cardSize * 0.036f
                typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f
            }
            val artistPaint = TextPaint().apply {
                color = secColor; textSize = cardSize * 0.026f; isAntiAlias = true; letterSpacing = 0.01f
            }
            val textLeft  = padding + artSize + cardSize * 0.035f
            val imgCenter = padding + artSize / 2f
            if (config.showTitle) canvas.drawText(songTitle, textLeft, imgCenter - cardSize * 0.01f, titlePaint)
            if (config.showArtist) canvas.drawText(artistName, textLeft, imgCenter + cardSize * 0.04f, artistPaint)

            headerY = padding + artSize + padding * 0.6f
        } else {
            val titlePaint = TextPaint().apply {
                color = mainColor; textSize = cardSize * 0.036f
                typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f
            }
            if (config.showTitle) {
                canvas.drawText(songTitle, padding, padding + cardSize * 0.05f, titlePaint)
                headerY = padding + cardSize * 0.07f
            }
            if (config.showArtist) {
                val artistPaint = TextPaint().apply {
                    color = secColor; textSize = cardSize * 0.026f; isAntiAlias = true
                }
                canvas.drawText(artistName, padding, headerY + cardSize * 0.04f, artistPaint)
                headerY += cardSize * 0.05f
            }
        }

        // Accent divider
        canvas.drawLine(padding, headerY, size * 0.6f, headerY, Paint().apply {
            shader = LinearGradient(padding, headerY, size * 0.6f, headerY,
                intArrayOf(accentArgb, android.graphics.Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            strokeWidth = 1.5f; isAntiAlias = true
        })
        headerY += padding * 0.6f

        // Quotation mark and lyrics
        val quotePaint = TextPaint().apply {
            color    = (accentArgb and 0x00FFFFFF) or 0x8C000000.toInt()
            textSize = cardSize * 0.10f
            typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText("\u201C", padding, headerY + quotePaint.textSize * 0.7f, quotePaint)
        val quoteH = quotePaint.textSize * 0.75f

        val lyricsPaint = TextPaint().apply {
            color = mainColor; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f
        }
        val logoH          = if (config.showBranding) cardSize * 0.09f else 0f
        val lyricsMaxWidth = (size - padding * 2).toInt()
        val lyricsAreaTop  = headerY + quoteH + padding * 0.3f
        val lyricsAreaBottom = size - logoH - padding

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = lyricsMaxWidth,
            availableHeight = lyricsAreaBottom - lyricsAreaTop,
            alignment       = config.textAlign.toLayoutAlignment(),
            initialTextSize = (cardSize * 0.058f) * config.textSizeMultiplier,
            minTextSize     = 22f,
        )
        val lyricsY = lyricsAreaTop + (lyricsAreaBottom - lyricsAreaTop - lyricsLayout.height) / 2f
        canvas.withTranslation(padding, lyricsY) { lyricsLayout.draw(this) }

        if (config.showBranding) {
            drawBranding(context, canvas, cardSize,
                x = padding, y = size - padding * 0.2f,
                circleColor = secColor, logoTint = 0xDD000000.toInt(), textColor = secColor)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDERER 7 — Vivid (Apple Music's lyric card)
    // The artwork's own colours as the ground, the lines large and left-set at the top, the song
    // along the bottom with the app icon at its end.
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderVivid(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Int,
    ): Bitmap {
        val size    = cardSize.toFloat()
        val bitmap  = createBitmap(cardSize, cardSize)
        val canvas  = Canvas(bitmap)
        val corner  = size * 0.06f
        val padding = (config.cardPadding.value / 340f) * size * 1.15f
        val (top, bottom) = lyricCardColors(coverArt)

        canvas.drawRoundRect(RectF(0f, 0f, size, size), corner, corner, Paint().apply {
            shader = LinearGradient(0f, 0f, size * 0.35f, size, top, bottom, Shader.TileMode.CLAMP)
            isAntiAlias = true
        })

        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.linotte) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD
        val white = 0xFFFFFFFF.toInt()
        val secondary = 0x9EFFFFFF.toInt()

        // Footer: artwork, title and artist, app icon.
        val footerH   = size * 0.12f
        val footerTop = size - padding - footerH
        var textLeft  = padding
        if (config.showCoverArt && coverArt != null) {
            val artRect = RectF(padding, footerTop, padding + footerH, footerTop + footerH)
            val artCorner = size * 0.018f
            val path = Path().apply { addRoundRect(artRect, artCorner, artCorner, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArt, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG)) }
            textLeft += footerH + size * 0.035f
        }
        val iconSize  = size * 0.085f
        val textRight = size - padding - if (config.showBranding) iconSize + size * 0.035f else 0f
        val textWidth = (textRight - textLeft).coerceAtLeast(1f)
        val titlePaint = TextPaint().apply {
            color = white; textSize = size * 0.036f; this.typeface = typeface; isAntiAlias = true
        }
        val artistPaint = TextPaint().apply {
            color = secondary; textSize = size * 0.031f; this.typeface = typeface; isAntiAlias = true
        }
        val title  = TextUtils.ellipsize(songTitle, titlePaint, textWidth, TextUtils.TruncateAt.END).toString()
        val artist = TextUtils.ellipsize(artistName, artistPaint, textWidth, TextUtils.TruncateAt.END).toString()
        val showTitle  = config.showTitle && title.isNotBlank()
        val showArtist = config.showArtist && artist.isNotBlank()
        val lineGap = size * 0.012f
        val blockH = (if (showTitle) titlePaint.textSize else 0f) +
            (if (showArtist) artistPaint.textSize else 0f) +
            (if (showTitle && showArtist) lineGap else 0f)
        var baseline = footerTop + (footerH - blockH) / 2f
        if (showTitle) {
            baseline += titlePaint.textSize * 0.85f
            canvas.drawText(title, textLeft, baseline, titlePaint)
            baseline += titlePaint.textSize * 0.15f + lineGap
        }
        if (showArtist) {
            baseline += artistPaint.textSize * 0.85f
            canvas.drawText(artist, textLeft, baseline, artistPaint)
        }
        if (config.showBranding) {
            drawAppIcon(context, canvas, size - padding - iconSize, footerTop + (footerH - iconSize) / 2f, iconSize)
        }

        // Lyrics, top-aligned like Apple's, as large as the space allows.
        val lyricsPaint = TextPaint().apply {
            color = white; this.typeface = typeface; isAntiAlias = true; letterSpacing = -0.01f
        }
        val lyricsTop    = padding
        val lyricsBottom = footerTop - padding * 0.8f
        val layout = buildFittedLyricsLayout(
            lyrics          = lyrics,
            paint           = lyricsPaint,
            maxWidth        = (size - padding * 2).toInt(),
            availableHeight = lyricsBottom - lyricsTop,
            alignment       = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.1f * config.textSizeMultiplier,
            minTextSize     = 22f,
            lineSpacing     = 1.12f,
        )
        canvas.withTranslation(padding, lyricsTop) { layout.draw(this) }
        return bitmap
    }

    /**
     * The two colours of a Vivid card, from [art]: its most vivid colour over its deep one, each
     * held dark enough that white text stays readable on it. Shared with the on-screen preview so
     * the PNG is the card the listener chose.
     */
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

    /** The launcher icon, adaptive mask and all, drawn into a [size]-square at ([left], [top]). */
    private fun drawAppIcon(context: Context, canvas: Canvas, left: Float, top: Float, size: Float) {
        val px = size.toInt().coerceAtLeast(1)
        val icon = runCatching { context.getDrawable(R.mipmap.ic_launcher)?.toBitmap(px, px) }.getOrNull() ?: return
        canvas.drawBitmap(icon, null, RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaleBlur(source: Bitmap, strength: Int): Bitmap {
        val safe = ensureSoftwareBitmap(source)

        // Reduce less, for better quality
        val factor = (1f / (strength / 2f).coerceAtLeast(1F)).coerceIn(0.3f, 1f)
        val smallW = (safe.width * factor).toInt().coerceAtLeast(100)
        val smallH = (safe.height * factor).toInt().coerceAtLeast(100)
        val small = Bitmap.createScaledBitmap(safe, smallW, smallH, true)
        return Bitmap.createScaledBitmap(small, safe.width, safe.height, true)
    }

    /** Compose's TextAlign as the Canvas equivalent */
    private fun androidx.compose.ui.text.style.TextAlign.toLayoutAlignment() = when (this) {
        androidx.compose.ui.text.style.TextAlign.Start,
        androidx.compose.ui.text.style.TextAlign.Left  -> Layout.Alignment.ALIGN_NORMAL
        androidx.compose.ui.text.style.TextAlign.End,
        androidx.compose.ui.text.style.TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
        else                                            -> Layout.Alignment.ALIGN_CENTER
    }

    /** A Compose Color as an ARGB Int */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
        android.graphics.Color.argb(
            (alpha * 255).toInt(), (red * 255).toInt(),
            (green * 255).toInt(), (blue * 255).toInt(),
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Saving a bitmap
    // ─────────────────────────────────────────────────────────────────────────

    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Exhale")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw IllegalStateException("Failed to create new MediaStore record")
            context.contentResolver.openOutputStream(uri)?.use { safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images").also { it.mkdirs() }
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", imageFile)
        }
    }
}