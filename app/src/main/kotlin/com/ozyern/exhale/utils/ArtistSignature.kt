package com.ozyern.exhale.utils

/** Android entry point for the shared artist wordmark lookup. */
object ArtistSignature {
    suspend fun findUrl(artistName: String): String? = com.ozyern.exhale.canvas.ArtistSignature.findUrl(artistName)
}
