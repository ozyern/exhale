package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.music.spine.SpineModule

@Composable
internal fun ModuleLogo(module: SpineModule, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .background(AppTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(module.name.take(1), color = AppTheme.colorScheme.onSurfaceVariant)
        val image = module.logo?.takeIf(String::isNotBlank) ?: module.icon?.takeIf(String::isNotBlank)
        if (image != null) AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
