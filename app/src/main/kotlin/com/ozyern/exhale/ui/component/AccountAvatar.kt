package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.screens.library.apple.SfStyle
import java.util.Locale

/** Shared reference avatar for tab headers and the iPad account footer. */
@Composable
fun AccountAvatar(
    imageUrl: String?,
    name: String = "",
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val sizePx = with(LocalDensity.current) { size.toPx() }
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.radialGradient(
                listOf(Color(0xFFAABEDB), Color(0xFF7B80AE)),
                center = Offset(sizePx / 2f, 0f), radius = sizePx,
            ))
            .border(0.33.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            name.isNotBlank() -> Text(
                text = name.trim().split(Regex("\\s+")).take(2)
                    .joinToString("") { it.take(1) }.uppercase(Locale.ROOT),
                style = if (size <= 32.dp) SfStyle(13.sp, 18.sp, FontWeight.SemiBold)
                    else SfStyle(20.sp, 24.sp, FontWeight.SemiBold),
                color = Color.White,
            )
            else -> SymbolIcon(symbol = sf("person.fill"), contentDescription = null, modifier = Modifier.size(if (size <= 32.dp) 20.dp else 26.dp), tint = Color.White)
        }
    }
}
