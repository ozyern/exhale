/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.menu

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ozyern.exhale.R
import com.ozyern.exhale.export.SongExporter
import com.ozyern.exhale.models.MediaMetadata

/**
 * "Save to device": the songs as tagged .m4a files in Music/Exhale, for other players and sharing.
 * One row for every menu, so the wording, icon and behaviour can't drift between them.
 *
 * [songs] is a function so an album or playlist menu only builds its list when this is tapped.
 */
@Composable
fun SaveToDeviceItem(
    songs: () -> List<MediaMetadata>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(text = stringResource(R.string.save_to_device)) },
        leadingContent = { Icon(painter = painterResource(R.drawable.save_to_device), contentDescription = null) },
        modifier = Modifier.clickable {
            val list = songs()
            onDismiss()
            if (SongExporter.get(context).enqueue(list) == 0) return@clickable
            Toast.makeText(context, context.getString(R.string.save_to_device_started), Toast.LENGTH_SHORT).show()
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
