package com.ozyern.exhale.playback.smart

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal class AacFileWriter(file: File, private val sampleRate: Int, bitrate: Int = BITRATE) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var writtenFrames = 0L

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BYTES)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun write(left: FloatArray, right: FloatArray, offset: Int, count: Int) {
        var done = 0
        while (done < count) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(endOfStream = false)
                continue
            }
            val buffer = codec.getInputBuffer(index)!!.apply { clear(); order(ByteOrder.nativeOrder()) }
            val frames = minOf(count - done, buffer.capacity() / 4)
            for (i in 0 until frames) {
                buffer.putShort(pcm(left[offset + done + i]))
                buffer.putShort(pcm(right[offset + done + i]))
            }
            codec.queueInputBuffer(index, 0, frames * 4, writtenFrames * 1_000_000L / sampleRate, 0)
            writtenFrames += frames
            done += frames
            drain(endOfStream = false)
        }
    }

    fun finish() {
        try {
            var queued = false
            while (!queued) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, writtenFrames * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queued = true
                } else {
                    drain(endOfStream = false)
                }
            }
            drain(endOfStream = true)
        } finally {
            release()
        }
    }

    fun abort() = release()

    private fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { if (muxerStarted) muxer.stop() }
        runCatching { muxer.release() }
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0L)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val output = codec.getOutputBuffer(index)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        output.position(info.offset).limit(info.offset + info.size)
                        muxer.writeSampleData(track, output, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
            }
        }
    }

    private fun pcm(sample: Float): Short = (sample * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val BITRATE = 96_000
        const val INPUT_BYTES = 64 * 1024
    }
}
