/** GPL-3.0. Third-party portions: see NOTICE. */
package com.ozyern.exhale.ui.component

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** One picked line for the share video: its words, and when it is sung (null when unsynced). */
data class ShareLine(val text: String, val startMs: Long?, val endMs: Long?)

/**
 * The picked lines on the share video's clock: which stretches of the track play, back to back,
 * and when each line is lit in the result.
 */
internal class ShareTimeline(
    /** Start and end of each stretch of the track, in track milliseconds. */
    val segments: List<Pair<Long, Long>>,
    /** Each sung line, by its index among the picked ones, and when it is lit, in output μs. */
    val windows: List<LineWindow>,
    val durationMs: Long,
) {
    /** The picked line lit at [outputUs], or -1 between them. */
    fun lineAt(outputUs: Long): Int = windows.firstOrNull { outputUs in it.fromUs until it.toUs }?.line ?: -1
}

internal class LineWindow(val line: Int, val fromUs: Long, val toUs: Long)

/**
 * The timeline for [lines], or null when they carry no timing. Lines that run on from each other
 * share one stretch, so a verse plays as one take; a gap between picked lines cuts straight on.
 */
internal fun shareTimeline(lines: List<ShareLine>): ShareTimeline? {
    val segments = mutableListOf<Pair<Long, Long>>()
    val windows = mutableListOf<LineWindow>()
    var cursor = 0L
    lines.forEachIndexed { index, line ->
        val start = line.startMs ?: return@forEachIndexed
        val room = MAX_SHARE_MS - cursor
        if (room <= MIN_LINE_MS) return@forEachIndexed
        val end = minOf(line.endMs ?: return@forEachIndexed, start + room)
        if (end - start < MIN_LINE_MS) return@forEachIndexed
        val last = segments.lastOrNull()
        if (last != null && last.second == start) {
            segments[segments.lastIndex] = last.first to end
        } else {
            segments += start to end
        }
        windows += LineWindow(index, cursor * 1000, (cursor + end - start) * 1000)
        cursor += end - start
    }
    return if (windows.isEmpty()) null else ShareTimeline(segments, windows, cursor)
}

/**
 * The lyrics as a story video (1080×1920 MP4): the cover blurred under the song's title and the
 * picked lines, each line lighting up as it is sung. Synced lines are cut from the song itself, the
 * stretches it sings them over laid end to end; with no audio to be had the same video is silent,
 * and unsynced lines are held as a still card for [MIN_LENGTH_MS].
 *
 * Returns the file, or null when nothing could be exported.
 */
@OptIn(UnstableApi::class)
suspend fun exportLyricsVideo(
    context: Context,
    lines: List<ShareLine>,
    title: String,
    artist: String,
    coverUrl: String?,
    /** The song's audio stream, or null for a silent video. */
    audioUrl: String?,
): File? {
    val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
    val out = File(folder, VIDEO_FILE_NAME)
    val timeline = shareTimeline(lines)
    val lengthMs = timeline?.durationMs ?: MIN_LENGTH_MS
    val card = withContext(Dispatchers.Default) {
        LyricsVideoCard(context, lines, title, artist, loadCover(context, coverUrl))
    }
    val backdrop = withContext(Dispatchers.IO) {
        runCatching {
            File(folder, BACKDROP_FILE_NAME).also { file ->
                file.outputStream().use { card.backdrop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }.getOrNull()
    } ?: return null

    suspend fun attempt(audio: String?): Boolean {
        val still = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.fromFile(backdrop))
                .setMimeType(MimeTypes.IMAGE_PNG)
                .setImageDurationMs(lengthMs)
                .build(),
        )
            .setFrameRate(FRAME_RATE)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                        OverlayEffect(listOf(LinesOverlay(card, timeline))),
                    ),
                ),
            )
            .build()
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(still).build())
        if (audio != null && timeline != null) {
            val pieces = timeline.segments.map { (start, end) ->
                EditedMediaItem.Builder(
                    MediaItem.Builder()
                        .setUri(audio)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(start)
                                .setEndPositionMs(end)
                                .build(),
                        )
                        .build(),
                ).setRemoveVideo(true).build()
            }
            sequences += EditedMediaItemSequence.Builder(pieces).build()
        }
        return transform(context, Composition.Builder(sequences).build(), out)
    }

    if (audioUrl != null && timeline != null && attempt(audioUrl)) return out
    return if (attempt(null)) out else null
}

/** The card's lines over each frame: one bitmap per lit line, drawn once and handed back as asked. */
@OptIn(UnstableApi::class)
private class LinesOverlay(private val card: LyricsVideoCard, private val timeline: ShareTimeline?) : BitmapOverlay() {
    private val frames = HashMap<Int, Bitmap>()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val lit = timeline?.lineAt(presentationTimeUs) ?: ALL_LIT
        return frames.getOrPut(lit) { card.lines(lit) }
    }
}

/**
 * Draws the share card: [backdrop] is the blurred cover with the title, and [lines] the words over
 * it with one of them lit ([ALL_LIT] for a still card, -1 for none).
 */
private class LyricsVideoCard(
    context: Context,
    private val picked: List<ShareLine>,
    title: String,
    artist: String,
    cover: Bitmap?,
) {
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.sfprodisplaybold) ?: Typeface.DEFAULT_BOLD
    private val margin = WIDTH * 0.08f
    private val textWidth = (WIDTH - margin * 2).toInt()

    val backdrop: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF1C1C1E.toInt())
        if (cover != null) {
            // The cover, blurred: shrunk, softened and stretched back over the whole frame.
            val small = Bitmap.createScaledBitmap(cover, 64, 64, true)
            val soft = com.ozyern.exhale.ui.player.softened(small, radius = 3, passes = 3)
            val scale = maxOf(WIDTH / soft.width.toFloat(), HEIGHT / soft.height.toFloat())
            val w = (soft.width * scale).toInt()
            val h = (soft.height * scale).toInt()
            val left = (WIDTH - w) / 2
            val top = (HEIGHT - h) / 2
            canvas.drawBitmap(soft, null, Rect(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(0x8C000000.toInt())
            // The sharp cover, small, above the title.
            val side = (WIDTH * 0.22f).toInt()
            val art = Rect(margin.toInt(), (HEIGHT * 0.14f).toInt(), margin.toInt() + side, (HEIGHT * 0.14f).toInt() + side)
            canvas.drawBitmap(cover, null, art, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 52f; typeface = bold }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt(); textSize = 40f; typeface = bold }
        val titleTop = HEIGHT * 0.14f + WIDTH * 0.22f + 36f
        canvas.drawText(ellipsize(title, titlePaint), margin, titleTop + 52f, titlePaint)
        canvas.drawText(ellipsize(artist, artistPaint), margin, titleTop + 104f, artistPaint)
    }

    private fun ellipsize(text: String, paint: TextPaint): String =
        android.text.TextUtils.ellipsize(text, paint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString()

    /** The lines, with [lit] in full white and the rest held back; transparent everywhere else. */
    fun lines(lit: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var y = HEIGHT * 0.42f
        picked.forEachIndexed { index, line ->
            val on = lit == ALL_LIT || index == lit
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (on) 0xFFFFFFFF.toInt() else 0x66FFFFFF
                textSize = 64f
                typeface = bold
            }
            val layout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.08f)
                .build()
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 36f
        }
        return bitmap
    }
}

private suspend fun loadCover(context: Context, url: String?): Bitmap? {
    url ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(url).size(720, 720).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }
}

@OptIn(UnstableApi::class)
private suspend fun transform(context: Context, composition: Composition, out: File): Boolean =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                        .build(),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Timber.w(exportException, "Lyrics video export failed")
                        if (cont.isActive) cont.resume(false)
                    }
                })
                .build()
            out.delete()
            transformer.start(composition, out.path)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
        }
    }

/** [video] handed to another app through the app's FileProvider. */
fun shareVideoIntent(context: Context, video: File): Intent? = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", video)
    Intent(Intent.ACTION_SEND)
        .setType(VIDEO_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}.getOrNull()

/** Copies [video] into Movies/Exhale, where a gallery will find it. */
suspend fun saveVideoToGallery(context: Context, video: File, label: String): Boolean = withContext(Dispatchers.IO) {
    val name = "exhale-lyrics-${label.replace(' ', '-').lowercase(Locale.ROOT)}.mp4"
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Exhale")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("no row")
            try {
                resolver.openOutputStream(uri)?.use { sink -> video.inputStream().use { it.copyTo(sink) } } ?: error("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Exhale")
            dir.mkdirs()
            video.copyTo(File(dir, name), overwrite = true)
        }
        true
    }.getOrDefault(false)
}

private const val WIDTH = 1080
private const val HEIGHT = 1920
private const val ALL_LIT = Int.MAX_VALUE
private const val SHARE_FOLDER = "shared"
private const val VIDEO_FILE_NAME = "lyrics.mp4"
private const val BACKDROP_FILE_NAME = "lyrics-backdrop.png"
private const val VIDEO_MIME = "video/mp4"

/** Long enough to read the lines of a still card in a story. */
private const val MIN_LENGTH_MS = 10_000L

/** One story clip's limit. */
private const val MAX_SHARE_MS = 60_000L

/** Shorter than this and a "line" is a stamp glitch, not something sung. */
private const val MIN_LINE_MS = 300L

private const val FRAME_RATE = 30
private const val VIDEO_BITRATE = 10_000_000
