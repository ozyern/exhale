/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.LocalPlayerAwareWindowInsets

/**
 * Where a collection page's header goes in the wide layout: a column of its own on the left, the
 * page's list on the right, as Apple Music lays out an album or a playlist on an unfolded foldable.
 *
 * The header is still composed where it always was — the first item of the page's list, with all
 * the page's state in reach — and only *draws* here: it publishes itself into this slot and leaves
 * a spacer in the list. See [publishToCollectionPane].
 */
class CollectionPaneSlot {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalCollectionPane = staticCompositionLocalOf<CollectionPaneSlot?> { null }

/** Two panes in the wide layout, the page alone everywhere else. */
@Composable
fun CollectionPanes(content: @Composable () -> Unit) {
    if (!LocalWideChrome.current) {
        content()
        return
    }
    val slot = remember { CollectionPaneSlot() }
    CompositionLocalProvider(LocalCollectionPane provides slot) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(0.42f),
            ) {
                slot.content?.invoke()
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(0.58f),
            ) {
                content()
            }
        }
    }
}

/**
 * Hands [pane] to the enclosing [CollectionPanes] when there is one, and returns whether it did.
 *
 * The list item that calls this pins itself, so it stays composed after it scrolls out of the
 * list: the pane is that item's content, and an unpinned item scrolled away would leave the pane
 * showing a stale copy — a like button that no longer changes when tapped. In the list it is a
 * spacer the height of the page's top bar, so the first row clears the back button.
 */
@Composable
fun publishToCollectionPane(pane: @Composable () -> Unit): Boolean {
    val slot = LocalCollectionPane.current ?: return false
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(pinnable) {
        val handle = pinnable?.pin()
        onDispose { handle?.release() }
    }
    SideEffect { slot.content = pane }
    Spacer(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppBarHeight),
    )
    return true
}

/**
 * A collection's header standing in its own column: the sleeve as large as the column allows,
 * then the title, the credit in the accent, a line of facts and the round actions, centred on the
 * column's middle, on the page's own ground.
 */
@Composable
fun CollectionPaneHero(
    title: String,
    meta: String,
    credit: String?,
    palette: ArtworkPalette?,
    artwork: @Composable BoxScope.() -> Unit,
    onCreditClick: (() -> Unit)? = null,
    badges: @Composable () -> Unit = {},
    /** Lay the sleeve's wash over the ground, for pages whose list sits on one (an album's). */
    wash: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val ground = palette?.background ?: MaterialTheme.colorScheme.surface
    val ink = palette?.onBackground ?: MaterialTheme.colorScheme.onSurface
    val quiet = palette?.onBackgroundVariant ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = palette?.accent ?: MaterialTheme.colorScheme.primary
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val sleeveShape = RoundedCornerShape(12.dp)
    val body: @Composable () -> Unit = {
        if (wash && palette != null) ArtworkWash(palette = palette, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (wash && palette != null) Modifier else Modifier.background(ground))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = bottom),
        ) {
            // What is left for the sleeve once the text and the buttons below it have their room.
            val sleeve = minOf(maxWidth, maxHeight - 210.dp).coerceAtLeast(120.dp)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(sleeve)
                        .shadow(24.dp, sleeveShape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                        .clip(sleeveShape),
                ) {
                    artwork()
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!credit.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = credit,
                        fontSize = 17.sp,
                        color = accent,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .let { if (onCreditClick != null) it.clickable(onClick = onCreditClick) else it },
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta.replace("  ·  ", " • ").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = quiet,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                badges()
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
    }
    if (palette != null) {
        CompositionLocalProvider(LocalReleasePalette provides palette) { body() }
    } else {
        body()
    }
}
