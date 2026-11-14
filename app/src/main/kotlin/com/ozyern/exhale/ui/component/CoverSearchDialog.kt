/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.utils.SongBundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CoverSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    apply: suspend (fullUrl: String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<SongBundle.CoverResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        delay(400)
        results = SongBundle.searchCovers(query)
        loading = false
    }

    Dialog(onDismissRequest = { if (!applying) onDismiss() }) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Material3's OutlinedTextField crashes against the foundation version on the classpath.
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) CircularProgressIndicator(Modifier.padding(4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(results, key = { it.fullUrl }) { cover ->
                    AsyncImage(
                        model = cover.thumbUrl,
                        contentDescription = cover.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !applying) {
                                applying = true
                                scope.launch {
                                    val ok = apply(cover.fullUrl)
                                    applying = false
                                    if (ok) onDismiss()
                                    else Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                }
                            },
                    )
                }
            }
            TextButton(onClick = onDismiss, enabled = !applying, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
