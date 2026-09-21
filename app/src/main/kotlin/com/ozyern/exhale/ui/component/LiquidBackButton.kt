/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.R

/**
 * The back affordance, as a disc of glass. Every screen in the app that can be backed out of uses
 * this one.
 *
 * It is [LiquidGlassIconButton] with the back arrow pre-filled — the same pane, the same lens, the
 * same press physics as the app-bar search icon and the clear-search "x", because a user has no
 * reason to expect the round control in the top-left to be made of something different from the
 * round control in the top-right.
 *
 * The whole of the glass — why refraction is possible inside content at all, what happens on a
 * page that has not published a backdrop, and what the press is doing — is documented on
 * [LiquidGlassIconButton] and on `LocalPageBackdrop`.
 */
@Composable
fun LiquidBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    @DrawableRes icon: Int = R.drawable.chevron_back,
    enabled: Boolean = true,
    contentDescription: String? = null,
    /** Icon tint. Defaults to the on-surface colour; pass white over dark full-bleed artwork. */
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    LiquidGlassIconButton(
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        contentDescription = contentDescription,
        tint = tint,
    )
}
