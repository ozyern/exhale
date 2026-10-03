/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.export

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * MP3 encoding through LAME (cpp/lame, LGPL), for people who want an .mp3 on their phone.
 *
 * The song is decoded from the file Exhale saves anyway and encoded once at 320 kbps, the highest
 * rate MP3 has. It is a conversion, so it can only lose a little against the original — the
 * setting says so — but it is the least it can lose.
 */
object Mp3Encoder {
    private val loaded = runCatching { System.loadLibrary("exhale_analysis") }.isSuccess

    val available: Boolean get() = loaded

    class Tags(val title: String?, val artist: String?, val album: String?, val year: String?, val cover: ByteArray?)

    /** Decodes [source] (any audio Android can read) and writes it to [target] as a tagged MP3. */
    fun convert(source: File, target: File, tags: Tags, kbps: Int = 320) {
        check(loaded) { "MP3 encoder unavailable" }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var handle = 0L
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).first { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            target.outputStream().buffered().use { out ->
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        else -> if (index >= 0) {
                            if (info.size > 0) {
                                if (handle == 0L) handle = open(rate, channels, kbps, tags)
                                val shorts = codec.getOutputBuffer(index)!!.duplicate().apply {
                                    position(info.offset)
                                    limit(info.offset + info.size)
                                    order(ByteOrder.nativeOrder())
                                }.asShortBuffer()
                                val pcm = ShortArray(shorts.remaining()).also { shorts.get(it) }
                                val frames = pcm.size / channels
                                // More than two channels: the front pair.
                                val stereo = if (channels <= 2) pcm else ShortArray(frames * 2) { i ->
                                    pcm[(i / 2) * channels + (i % 2)]
                                }
                                write(out, nativeEncode(handle, stereo, frames))
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                if (handle != 0L) write(out, nativeFlush(handle))
            }
        } finally {
            if (handle != 0L) nativeClose(handle)
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun open(rate: Int, channels: Int, kbps: Int, tags: Tags): Long {
        val handle = nativeInit(rate, minOf(channels, 2), kbps, tags.title, tags.artist, tags.album, tags.year, tags.cover)
        check(handle != 0L) { "LAME refused $rate Hz / $channels ch" }
        return handle
    }

    private fun write(out: OutputStream, bytes: ByteArray?) {
        if (bytes != null && bytes.isNotEmpty()) out.write(bytes)
    }

    @JvmStatic private external fun nativeInit(
        sampleRate: Int, channels: Int, kbps: Int,
        title: String?, artist: String?, album: String?, year: String?, cover: ByteArray?,
    ): Long

    @JvmStatic private external fun nativeEncode(handle: Long, pcm: ShortArray, frames: Int): ByteArray?
    @JvmStatic private external fun nativeFlush(handle: Long): ByteArray?
    @JvmStatic private external fun nativeClose(handle: Long)
}
