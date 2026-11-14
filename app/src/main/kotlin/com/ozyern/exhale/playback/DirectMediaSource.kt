package com.ozyern.exhale.playback

import com.ozyern.storage.domain.NetworkMediaUri

/** Media that can reach the data-source chain without resolving a YouTube video ID. */
internal fun isDirectMediaSource(mediaId: String): Boolean =
    mediaId.startsWith("content://") || mediaId.startsWith("file://") || mediaId.startsWith("/storage/") ||
        mediaId.startsWith(NetworkMediaUri.Scheme + "://")
