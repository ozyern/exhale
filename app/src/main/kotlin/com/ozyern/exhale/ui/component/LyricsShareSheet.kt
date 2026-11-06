/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.LyricsShareCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sharing chosen lines as a picture, the way Apple Music does: the card shown is the very image
 * that is shared or saved, as a story or as a square.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareSheet(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(LyricsShareCard.Format.Story) }
    var busy by remember { mutableStateOf(false) }
    val title = mediaMetadata.title
    val artist = mediaMetadata.artists.joinToString { it.name }

    val cover by produceState<Bitmap?>(null, mediaMetadata.thumbnailUrl) {
        value = LyricsShareCard.loadCover(context, mediaMetadata.thumbnailUrl)
    }
    // Kept per format and per whether the cover was in it, so switching back is instant and a card
    // drawn before the cover arrived is drawn again once it has.
    val rendered = remember { mutableStateOf<Map<Pair<LyricsShareCard.Format, Boolean>, Bitmap>>(emptyMap()) }
    LaunchedEffect(format, cover) {
        // No cover yet: give it a moment (its arrival restarts this) before settling for none.
        if (cover == null && mediaMetadata.thumbnailUrl != null) kotlinx.coroutines.delay(1_500)
        val key = format to (cover != null)
        if (rendered.value[key] != null) return@LaunchedEffect
        val bitmap = runCatching {
            LyricsShareCard.render(context, cover, title, artist, lyricText, format)
        }.getOrNull() ?: return@LaunchedEffect
        rendered.value = rendered.value + (key to bitmap)
    }
    val current = rendered.value[format to true] ?: rendered.value[format to false]

    fun withImage(action: suspend (Bitmap) -> Unit) {
        val bitmap = current ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                action(bitmap)
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // A phone on its side is short: the sheet scrolls rather than push Share off screen.
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Share Lyrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            // The picture itself, at a height that leaves the buttons in reach.
            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
            val previewHeight = minOf(
                if (format == LyricsShareCard.Format.Story) 400.dp else 300.dp,
                screenHeight * 0.5f,
            )
            Box(
                Modifier
                    .height(previewHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = current, animationSpec = tween(200), label = "lyricsCardPreview") { bitmap ->
                    if (bitmap != null) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        Image(
                            bitmap = image,
                            contentDescription = lyricText,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(previewHeight)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    } else {
                        LoadingRing(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            stroke = 3.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // Story or square, as one capsule.
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(4.dp),
            ) {
                LyricsShareCard.Format.entries.forEach { option ->
                    val selected = option == format
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { format = option }
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (option == LyricsShareCard.Format.Story) "Story" else "Square",
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        withImage { bitmap ->
                            val saved = withContext(Dispatchers.IO) { LyricsShareCard.saveToGallery(context, bitmap) }
                            Toast.makeText(
                                context,
                                if (saved) "Saved to Pictures/Exhale" else "Couldn't save the image",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (saved) onDismiss()
                        }
                    },
                    enabled = current != null && !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Save") }
                Button(
                    onClick = {
                        withImage { bitmap ->
                            val uri = runCatching {
                                withContext(Dispatchers.IO) { LyricsShareCard.shareUri(context, bitmap) }
                            }.getOrNull()
                            if (uri == null) {
                                Toast.makeText(context, "Couldn't prepare the image", Toast.LENGTH_SHORT).show()
                                return@withImage
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                                .onFailure { Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show() }
                            onDismiss()
                        }
                    },
                    enabled = current != null && !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}
