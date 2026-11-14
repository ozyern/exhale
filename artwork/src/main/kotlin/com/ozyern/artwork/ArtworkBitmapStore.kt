package com.ozyern.artwork

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias ArtworkBitmapStore = ArtworkImageStore<Bitmap>

@Stable
class ArtworkImageStore<T : Any>(private val capacity: Int = 4) {
    init { require(capacity > 0) }

    val images = mutableStateMapOf<String, T>()
    private val loadLock = Mutex()

    suspend fun load(url: String, loader: suspend (String) -> T?): T? = loadLock.withLock {
        images[url]?.let { return@withLock it }
        val image = loader(url) ?: return@withLock null
        while (images.size >= capacity) images.remove(images.keys.first())
        images[url] = image
        image
    }
}
