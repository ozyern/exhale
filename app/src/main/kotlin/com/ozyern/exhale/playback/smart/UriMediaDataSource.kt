package com.ozyern.exhale.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class UriMediaDataSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val channel: FileChannel,
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        return channel.read(ByteBuffer.wrap(buffer, offset, size), position)
    }

    override fun getSize(): Long = channel.size()

    override fun close() {
        runCatching { channel.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): UriMediaDataSource? = runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            UriMediaDataSource(descriptor, FileInputStream(descriptor.fileDescriptor).channel)
        }.getOrNull()
    }
}
