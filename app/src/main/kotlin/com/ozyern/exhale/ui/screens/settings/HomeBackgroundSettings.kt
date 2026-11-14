/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Slider
import com.ozyern.exhale.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.HomeBackgroundAnimateKey
import com.ozyern.exhale.constants.HomeBackgroundBlurKey
import com.ozyern.exhale.constants.HomeBackgroundDimKey
import com.ozyern.exhale.constants.HomeBackgroundEnabledKey
import com.ozyern.exhale.constants.HomeBackgroundIsVideoKey
import com.ozyern.exhale.constants.HomeBackgroundPathKey
import com.ozyern.exhale.constants.HomeBackgroundQualityKey
import com.ozyern.exhale.constants.LibraryBackgroundMode
import com.ozyern.exhale.constants.LibraryBackgroundModeKey
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.HomeVideoBackground
import com.ozyern.exhale.ui.component.rememberHomeBackgroundTargetSize
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** File size limits for picked background images and videos. */
private const val MaxHomeBackgroundImageBytes = 20L * 1024 * 1024
private const val MaxHomeBackgroundVideoBytes = 80L * 1024 * 1024

/** Reads the picked file size without opening its stream; returns -1 when unavailable. */
private fun mediaSizeBytes(context: android.content.Context, uri: Uri): Long {
    return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
    } ?: -1L
}

private fun copyRawMedia(context: android.content.Context, source: Uri, destination: File): String? {
    val input = context.contentResolver.openInputStream(source) ?: return null
    input.use { stream ->
        destination.outputStream().use { output -> stream.copyTo(output) }
    }
    return destination.absolutePath
}


/**
 * Copies picked media into app storage so it survives picker permission changes.
 * Images are resized through Coil to preserve EXIF orientation; videos and GIFs are copied as-is.
 * Returns the saved path, or null on failure.
 */
private suspend fun copyBackgroundMedia(
    context: android.content.Context,
    source: Uri,
    isVideo: Boolean,
    isGif: Boolean = false,
    maxWidth: Int,
    maxHeight: Int,
): String? = runCatching {
    val ext = if (isVideo) "mp4" else if (isGif) "gif" else "jpg"
    val dest = File(context.filesDir, "home_background_${System.currentTimeMillis()}.$ext")

    if (isVideo || isGif) {
        // Preserve the original GIF and avoid transcoding video during the pick.
        return@runCatching copyRawMedia(context, source, dest)
    }

    val request = ImageRequest.Builder(context)
        .data(source)
        .size(maxWidth, maxHeight)
        .allowHardware(false) // compress() needs readable pixels
        .build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()

    if (bitmap == null) {
        // Keep the original when Coil cannot resize the image.
        return@runCatching copyRawMedia(context, source, dest)
    }

    dest.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
    }
    dest.absolutePath
}.getOrNull()

/** Settings for the custom background image and video used on Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBackgroundControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (enabled, onEnabledChange) = rememberPreference(HomeBackgroundEnabledKey, defaultValue = false)
    val (path, onPathChange) = rememberPreference(HomeBackgroundPathKey, defaultValue = "")
    val (blur, onBlurChange) = rememberPreference(HomeBackgroundBlurKey, defaultValue = 48f)
    val (dim, onDimChange) = rememberPreference(HomeBackgroundDimKey, defaultValue = 0.4f)
    val (animate, onAnimateChange) = rememberPreference(HomeBackgroundAnimateKey, defaultValue = false)
    val (isVideo, onIsVideoChange) = rememberPreference(HomeBackgroundIsVideoKey, defaultValue = false)
    val (quality, onQualityChange) = rememberPreference(HomeBackgroundQualityKey, defaultValue = 1f)
    val (targetWidth, targetHeight) = rememberHomeBackgroundTargetSize(quality)

    var showBlurDialog by rememberSaveable { mutableStateOf(false) }
    var showDimDialog by rememberSaveable { mutableStateOf(false) }
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    fun applyPickedMedia(uri: Uri, video: Boolean, gif: Boolean = false) {
        val previous = path
        scope.launch {
            val newPath = withContext(Dispatchers.IO) {
                copyBackgroundMedia(context, uri, video, gif, targetWidth, targetHeight)
            }
            if (newPath != null) {
                onPathChange(newPath)
                onIsVideoChange(video)
                if (!enabled) onEnabledChange(true)
                if (previous.isNotEmpty()) withContext(Dispatchers.IO) { File(previous).delete() }
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
        val video = mimeType?.startsWith("video/") == true
        val gif = mimeType == "image/gif"
        val cap = if (video) MaxHomeBackgroundVideoBytes else MaxHomeBackgroundImageBytes
        val size = mediaSizeBytes(context, uri)
        if (size > cap) {
            val capMb = cap / (1024 * 1024)
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.home_background_too_large, "${capMb}MB"),
                android.widget.Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        if (video) {
            // Video decode/blur runs every frame, unlike a static image — warn
            // before committing since this can visibly cost battery/frame time
            // on lower-end devices.
            pendingVideoUri = uri
        } else {
            applyPickedMedia(uri, video = false, gif = gif)
        }
    }

    val videoUriToConfirm = pendingVideoUri
    if (videoUriToConfirm != null) {
        DefaultDialog(
            onDismiss = { pendingVideoUri = null },
            buttons = {
                TextButton(onClick = { pendingVideoUri = null }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    applyPickedMedia(videoUriToConfirm, video = true)
                    pendingVideoUri = null
                }) { Text(stringResource(R.string.home_background_video_confirm)) }
            }
        ) {
            Text(
                text = stringResource(R.string.home_background_video_warning),
                style = AppTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    Column(
        Modifier.padding(horizontal = 16.dp),
    ) {
        // Live preview: image with the same blur + dim the home screen applies.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (path.isNotEmpty() && isVideo) {
                HomeVideoBackground(path = path, blur = blur, dim = dim)
            } else if (path.isNotEmpty()) {
                // This preview uses a live blur; HomeImageBackground uses a cached blur.
                val previewRequest = remember(path) {
                    ImageRequest.Builder(context)
                        .data(File(path))
                        .build()
                }
                AsyncImage(
                    model = previewRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dim)),
                )
            } else {
                Text(
                    text = stringResource(R.string.home_background_image_none),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        IosSettingsGroup(
            title = stringResource(R.string.home_background),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.home_background_enable)) },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange
                        )
                    },
                    onClick = { onEnabledChange(!enabled) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.image),
                    title = { Text(stringResource(R.string.home_background_image)) },
                    description = {
                        Text(
                            when {
                                path.isEmpty() -> stringResource(R.string.home_background_image_none)
                                isVideo -> stringResource(R.string.home_background_video_set)
                                else -> stringResource(R.string.home_background_image_set)
                            }
                        )
                    },
                    onClick = {
                        pickLauncher.launch(
                            PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.sliders),
                    title = { Text(stringResource(R.string.home_background_blur)) },
                    onClick = { showBlurDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.home_background_dim)) },
                    onClick = { showDimDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.sliders),
                    title = { Text(stringResource(R.string.home_background_quality)) },
                    description = { Text(stringResource(R.string.home_background_quality_desc)) },
                    onClick = { showQualityDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.home_background_animate)) },
                    description = { Text(stringResource(R.string.home_background_animate_desc)) },
                    trailingContent = {
                        Switch(
                            checked = animate,
                            onCheckedChange = onAnimateChange
                        )
                    },
                    onClick = { onAnimateChange(!animate) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.delete),
                    title = { Text(stringResource(R.string.home_background_remove)) },
                    onClick = {
                        val current = path
                        if (current.isNotEmpty()) scope.launch(Dispatchers.IO) { File(current).delete() }
                        onPathChange("")
                        onIsVideoChange(false)
                        onEnabledChange(false)
                    }
                ),
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // What the Library screen falls back to when the custom image above is
        // off/unset — plain, a flat theme-color wash, or a blurred thumbnail
        // pulled from the library's own content.
        val (libraryBackgroundMode, onLibraryBackgroundModeChange) = rememberEnumPreference(
            LibraryBackgroundModeKey,
            LibraryBackgroundMode.PLAIN,
        )
        IosSettingsGroup(
            title = stringResource(R.string.library_background),
            items = LibraryBackgroundMode.entries.map { mode ->
                IosSettingsItem(
                    icon = painterResource(
                        when (mode) {
                            LibraryBackgroundMode.PLAIN -> R.drawable.close
                            LibraryBackgroundMode.THEME -> R.drawable.palette
                            LibraryBackgroundMode.THUMBNAIL_BLUR -> R.drawable.image
                        }
                    ),
                    title = {
                        Text(
                            stringResource(
                                when (mode) {
                                    LibraryBackgroundMode.PLAIN -> R.string.library_background_plain
                                    LibraryBackgroundMode.THEME -> R.string.library_background_theme
                                    LibraryBackgroundMode.THUMBNAIL_BLUR -> R.string.library_background_thumbnail_blur
                                }
                            )
                        )
                    },
                    trailingContent = {
                        if (mode == libraryBackgroundMode) {
                            SymbolIcon(symbol = sf("checkmark"), contentDescription = null)
                        }
                    },
                    onClick = { onLibraryBackgroundModeChange(mode) }
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showBlurDialog) {
        var tempValue by remember { mutableFloatStateOf(blur) }
        DefaultDialog(
            onDismiss = { tempValue = blur; showBlurDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 48f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = blur; showBlurDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onBlurChange(tempValue); showBlurDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.home_background_blur), style = AppTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.0f".format(tempValue), style = AppTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showDimDialog) {
        var tempValue by remember { mutableFloatStateOf(dim) }
        DefaultDialog(
            onDismiss = { tempValue = dim; showDimDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 0.4f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = dim; showDimDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onDimChange(tempValue); showDimDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.home_background_dim), style = AppTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.0f%%".format(tempValue * 100), style = AppTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showQualityDialog) {
        var tempValue by remember { mutableFloatStateOf(quality) }
        DefaultDialog(
            onDismiss = { tempValue = quality; showQualityDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 1f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = quality; showQualityDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onQualityChange(tempValue); showQualityDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.home_background_quality), style = AppTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "%.0f%%".format(tempValue * 100), style = AppTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0.3f..1f, modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.home_background_quality_reapply_hint),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
