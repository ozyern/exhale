/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowCounterclockwise
import com.composables.sfsymbols.monochrome.SFExclamationmarkTriangle
import com.composables.sfsymbols.monochrome.SFSquareOnSquare
import com.ozyern.exhale.R

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    // ExoPlayer wraps network errors, so inspect nested causes too.
    val causeChain = remember(error) {
        generateSequence(error as Throwable) { it.cause }.take(8).toList()
    }
    val httpStatus = remember(causeChain) {
        causeChain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
    }
    // Prefer the deepest available error message.
    val chainMessage = remember(causeChain) {
        causeChain.reversed().firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
    }
    // Exception types still identify failures with no message.
    val chainTypes = remember(causeChain) {
        causeChain.map { it.javaClass.simpleName }.distinct().joinToString(" <- ")
    }
    val unknownLabel = stringResource(R.string.error_unknown)
    val rawErrorMessage = when {
        httpStatus != null && chainMessage != null -> "HTTP $httpStatus - $chainMessage"
        httpStatus != null -> "HTTP $httpStatus"
        chainMessage != null -> chainMessage
        chainTypes.isNotBlank() -> chainTypes
        else -> unknownLabel
    }

    // Copy the full error details, even when the on-screen message is truncated.
    val copyDetails = remember(error, rawErrorMessage) {
        buildString {
            appendLine("Exhale playback error")
            appendLine("code: ${getErrorCodeName(error.errorCode)} (${error.errorCode})")
            httpStatus?.let { appendLine("http: $it") }
            appendLine("detail: $rawErrorMessage")
            appendLine("chain: $chainTypes")
        }
    }
    val clipboard = LocalClipboardManager.current

    // Age-restricted content typically returns 403 Forbidden or contains age-related messages
    val isAgeRestricted = rawErrorMessage.contains("age", ignoreCase = true) ||
            rawErrorMessage.contains("Sign in to confirm your age", ignoreCase = true) ||
            rawErrorMessage.contains("LOGIN_REQUIRED", ignoreCase = true) ||
            rawErrorMessage.contains("confirm your age", ignoreCase = true) ||
            rawErrorMessage.contains("403", ignoreCase = true) ||
            rawErrorMessage.contains("Response code: 403", ignoreCase = true) ||
            // Only a real 403 means age-gated. IO_BAD_HTTP_STATUS on its own
            // covers 404/429/5xx too, and claiming "age-restricted" for those
            // sent people chasing the wrong problem.
            httpStatus == 403
    
    val errorMessage = if (isAgeRestricted) {
        "This app does not support playing age-restricted songs. We are working on fixing this issue."
    } else {
        rawErrorMessage
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SymbolIcon(symbol = sf("SFExclamationmarkTriangle"), contentDescription = null, size = 48.dp, tint = AppTheme.colorScheme.error)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.error_playback_failed),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Code: ${getErrorCodeName(error.errorCode)} (${error.errorCode})",
            style = AppTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = retry,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colorScheme.primary
                )
            ) {
                SymbolIcon(symbol = sf("SFArrowCounterclockwise"), contentDescription = null, size = 18.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.retry))
            }

            TextButton(
                onClick = { clipboard.setText(AnnotatedString(copyDetails)) },
                shape = RoundedCornerShape(20.dp),
            ) {
                SymbolIcon(symbol = sf("SFSquareOnSquare"), contentDescription = null, size = 18.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.error_copy_details))
            }
        }
    }
}

private fun getErrorCodeName(errorCode: Int): String {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> "REMOTE_ERROR"
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "BEHIND_LIVE_WINDOW"
        PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "FAILED_RUNTIME_CHECK"
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "DECODING_FORMAT_EXCEEDS_CAPABILITIES"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM_UNSPECIFIED"
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "DRM_SCHEME_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM_PROVISIONING_FAILED"
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM_CONTENT_ERROR"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM_LICENSE_ACQUISITION_FAILED"
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM_DISALLOWED_OPERATION"
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM_SYSTEM_ERROR"
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM_DEVICE_REVOKED"
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM_LICENSE_EXPIRED"
        else -> "UNKNOWN_ERROR_$errorCode"
    }
}
