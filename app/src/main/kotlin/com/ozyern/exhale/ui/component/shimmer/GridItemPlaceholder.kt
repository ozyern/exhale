/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.constants.GridItemSize
import com.ozyern.exhale.constants.GridItemsSizeKey
import com.ozyern.exhale.constants.GridThumbnailHeight
import com.ozyern.exhale.constants.SmallGridThumbnailHeight
import com.ozyern.exhale.constants.ThumbnailCornerRadius
import com.ozyern.exhale.constants.ThumbnailRoundedShape
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.utils.rememberEnumPreference

@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = ThumbnailRoundedShape,
    fillMaxWidth: Boolean = false,
) {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    
    Column(
        modifier =
        if (fillMaxWidth) {
            modifier
                .padding(AppleTokens.ItemGap / 2)
                .fillMaxWidth()
        } else {
            modifier
                .padding(AppleTokens.ItemGap / 2)
                .width(gridHeight)
        },
    ) {
        Spacer(
            modifier =
            if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }.aspectRatio(1f)
                .clip(thumbnailShape)
                .background(AppTheme.colorScheme.onSurface),
        )

        Spacer(modifier = Modifier.height(AppleTokens.ItemGap / 2))

        TextPlaceholder()

        TextPlaceholder()
    }
}
