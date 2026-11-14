/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/** Error categories, ordered by [PlaybackErrorClassifier.classify] precedence. */
sealed class PlaybackErrorType {
    data object DecoderReclaim : PlaybackErrorType()
    data object AudioRenderer : PlaybackErrorType()
    data object RangeNotSatisfiable : PlaybackErrorType()
    data object PageReload : PlaybackErrorType()
    data object ExpiredUrl : PlaybackErrorType()
    data object Parsing : PlaybackErrorType()
    data object Network : PlaybackErrorType()
    data object Other : PlaybackErrorType()
}

object PlaybackErrorClassifier {
    private val reloadKeywords = listOf(
        "page needs to be reloaded",
        "pagina deve essere ricaricata",
        "la pagina deve essere ricaricata",
        "page must be reloaded",
        "reload",
        "ricaricata",
    )

    fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    fun isExpiredUrlError(error: PlaybackException): Boolean =
        getHttpResponseCode(error) == 403

    fun isRangeNotSatisfiableError(error: PlaybackException): Boolean =
        getHttpResponseCode(error) == 416

    fun isPageReloadError(error: PlaybackException): Boolean {
        return reloadKeywords.any { keyword ->
            error.message?.contains(keyword, ignoreCase = true) == true ||
                error.cause?.message?.contains(keyword, ignoreCase = true) == true ||
                error.cause?.cause?.message?.contains(keyword, ignoreCase = true) == true
        }
    }

    fun isNetworkRelatedError(error: PlaybackException): Boolean {
        val httpCode = getHttpResponseCode(error)
        if (httpCode == 403 || httpCode == 416 || isPageReloadError(error)) {
            return false
        }
        return isNetworkFailure(error)
    }

    private fun isNetworkFailure(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.cause is java.net.ConnectException ||
                error.cause is java.net.UnknownHostException ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

    fun isAudioRendererError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED

    fun isParsingError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

    /** Samsung's resource manager reclaims the FLAC hardware decoder mid-playback. */
    fun isDecoderReclaimError(error: PlaybackException): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return false
        val msg = error.cause?.message ?: return false
        return msg.contains("reclaim", ignoreCase = true)
    }

    fun classify(error: PlaybackException, isNetworkConnected: Boolean): PlaybackErrorType {
        val httpCode = getHttpResponseCode(error)
        return when {
            isDecoderReclaimError(error) -> PlaybackErrorType.DecoderReclaim
            isAudioRendererError(error) -> PlaybackErrorType.AudioRenderer
            httpCode == 416 -> PlaybackErrorType.RangeNotSatisfiable
            isPageReloadError(error) -> PlaybackErrorType.PageReload
            httpCode == 403 -> PlaybackErrorType.ExpiredUrl
            isParsingError(error) -> PlaybackErrorType.Parsing
            !isNetworkConnected || isNetworkFailure(error) -> PlaybackErrorType.Network
            else -> PlaybackErrorType.Other
        }
    }
}
