package com.ozyern.exhale.artistvideo

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView

/** Keeps the player's surface lifecycle intact and reports displayed video frames. */
class VideoFrameTextureView(context: Context) : TextureView(context) {
    var onFrame: (() -> Unit)? = null
    private var playerListener: SurfaceTextureListener? = null

    // ExoPlayer checks listener identity before clearing/rebinding its surface.
    // Return its delegate; TextureView itself dispatches through the wrapper below.
    override fun getSurfaceTextureListener(): SurfaceTextureListener? = playerListener

    override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
        playerListener = listener
        if (listener == null) {
            super.setSurfaceTextureListener(null)
            return
        }
        super.setSurfaceTextureListener(object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) =
                listener.onSurfaceTextureAvailable(surface, width, height)

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) =
                listener.onSurfaceTextureSizeChanged(surface, width, height)

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean =
                listener.onSurfaceTextureDestroyed(surface)

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                listener.onSurfaceTextureUpdated(surface)
                onFrame?.invoke()
            }
        })
    }
}
