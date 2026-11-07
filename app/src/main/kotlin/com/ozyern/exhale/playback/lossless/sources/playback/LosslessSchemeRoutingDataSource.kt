/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0.
 * Origin: playback/MusicService.kt (SchemeRoutingDataSource / ResolvedSchemeRoutingDataSource).
 *
 * Routes the custom lossless URI schemes to their dedicated data sources:
 *   deezer://stream?…      → DeezerDecryptingDataSource (Blowfish-CBC chunk decryption over HTTP)
 *   tidal-dash://stream?…  → TidalProgressiveDashDataSource (stitches DASH segments progressively)
 * Everything else falls through to the player's normal (cached) factory. Sits *below*
 * ResolvingDataSource so it sees the already-resolved URI.
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ozyern.exhale.playback.lossless.sources.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerCrypto
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerDecryptingDataSource
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAudioProvider
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalProgressiveDashDataSource
import okhttp3.OkHttpClient
import java.util.Locale

@OptIn(UnstableApi::class)
class LosslessSchemeRoutingDataSource(
    private val defaultFactory: DataSource.Factory,
    private val deezerFactory: DataSource.Factory,
    private val tidalProgressiveDashFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase(Locale.US)
        val factory =
            when (scheme) {
                DeezerCrypto.SCHEME -> deezerFactory
                TidalAudioProvider.PROGRESSIVE_DASH_SCHEME -> tidalProgressiveDashFactory
                else -> defaultFactory
            }
        val selected = factory.createDataSource()
        transferListeners.forEach(selected::addTransferListener)
        delegate = selected
        return selected.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    class Factory(
        private val defaultFactory: DataSource.Factory,
        private val deezerFactory: DataSource.Factory,
        private val tidalProgressiveDashFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LosslessSchemeRoutingDataSource(defaultFactory, deezerFactory, tidalProgressiveDashFactory)
    }

    companion object {
        /** True for URIs that must bypass the HTTP cache / pre-fetch paths and go through this router. */
        fun isCustomLosslessScheme(uri: Uri?): Boolean {
            val scheme = uri?.scheme?.lowercase(Locale.US) ?: return false
            return scheme == DeezerCrypto.SCHEME || scheme == TidalAudioProvider.PROGRESSIVE_DASH_SCHEME
        }

        fun isCustomLosslessScheme(uri: String?): Boolean {
            val lower = uri?.lowercase(Locale.US) ?: return false
            return lower.startsWith("${DeezerCrypto.SCHEME}://") ||
                lower.startsWith("${TidalAudioProvider.PROGRESSIVE_DASH_SCHEME}://")
        }

        /**
         * Builds the router with the standard upstreams: Deezer decrypts over [httpUpstream]
         * (plain HTTP, uncached — the ciphertext is useless in the shared cache and the plaintext
         * must never be written to it), Tidal DASH stitches segments with [httpClient].
         */
        fun create(
            defaultFactory: DataSource.Factory,
            httpUpstream: DataSource.Factory,
            httpClient: OkHttpClient,
        ): Factory =
            Factory(
                defaultFactory = defaultFactory,
                deezerFactory = DeezerDecryptingDataSource.Factory(httpUpstream),
                tidalProgressiveDashFactory = TidalProgressiveDashDataSource.Factory(httpClient),
            )
    }
}
