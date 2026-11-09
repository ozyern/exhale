/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
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
class CollectionPaneSlot(
    /**
     * A phone on its side, as the iPhone's music app sets a release there: the sleeve at the full
     * height of the screen down the left, dissolving into the page's colours, with the title and the
     * buttons over its foot and the tracks down the right. The pane is drawn under the whole page
     * (the page is transparent over it), so the colours run on unbroken behind the list.
     */
    val bleed: Boolean = false,
) {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalCollectionPane = staticCompositionLocalOf<CollectionPaneSlot?> { null }

/** The share of a phone-on-its-side window the sleeve's column takes; the list has the rest. */
const val BleedPaneFraction = 0.5f

/** Whether this window lays a collection out as [CollectionPaneSlot.bleed]. */
@Composable
fun isPhoneLandscapeWindow(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return !LocalWideChrome.current &&
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
}

/** Two panes in the wide layout and on a phone on its side, the page alone everywhere else. */
@Composable
fun CollectionPanes(content: @Composable () -> Unit) {
    val phoneSide = isPhoneLandscapeWindow()
    if (!LocalWideChrome.current && !phoneSide) {
        content()
        return
    }
    val slot = remember(phoneSide) { CollectionPaneSlot(bleed = phoneSide) }
    if (phoneSide) {
        CompositionLocalProvider(LocalCollectionPane provides slot) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val split = maxWidth * BleedPaneFraction
                Box(Modifier.fillMaxSize()) { slot.content?.invoke() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = split),
                ) {
                    content()
                }
            }
        }
        return
    }
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
    explicit: Boolean = false,
    lossless: Boolean = false,
    losslessLabel: String? = "Lossless",
    badges: @Composable () -> Unit = {},
    /** Lay the sleeve's wash over the ground, for pages whose list sits on one (an album's). */
    wash: Boolean = false,
    /** The release's editorial note; shown under the buttons on a phone on its side. */
    about: String? = null,
    actions: @Composable () -> Unit,
) {
    if (LocalCollectionPane.current?.bleed == true) {
        val body: @Composable () -> Unit = {
            BleedPaneHero(
                title = title,
                meta = meta,
                credit = credit,
                palette = palette,
                artwork = artwork,
                onCreditClick = onCreditClick,
                explicit = explicit,
                lossless = lossless,
                losslessLabel = losslessLabel,
                about = about,
                runsUnderList = wash,
                actions = actions,
            )
        }
        if (palette != null) {
            CompositionLocalProvider(LocalReleasePalette provides palette) { body() }
        } else {
            body()
        }
        return
    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
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
                    if (explicit) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(com.ozyern.exhale.R.drawable.explicit),
                            contentDescription = null,
                            tint = quiet,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
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
                if (meta.isNotBlank() || lossless) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.3.sp),
                                fontWeight = FontWeight.Medium,
                                color = quiet,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (lossless) {
                            if (meta.isNotBlank()) {
                                Text(
                                    text = " \u2022 ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = quiet,
                                )
                            }
                            Icon(
                                painter = painterResource(com.ozyern.exhale.R.drawable.ic_apple_lossless),
                                contentDescription = null,
                                tint = quiet,
                                modifier = Modifier.size(width = 17.dp, height = 12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = losslessLabel ?: "Lossless",
                                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.2.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = quiet,
                            )
                        }
                    }
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

/**
 * The phone-on-its-side hero (see [CollectionPaneSlot.bleed]). It is drawn under the whole window:
 * the ground and the sleeve's wash everywhere, the sleeve itself full height down the left fading
 * out to the right and towards the foot, and the words and buttons centred over that foot. The
 * list's column lies over the right half with no ground of its own.
 */
@Composable
private fun BleedPaneHero(
    title: String,
    meta: String,
    credit: String?,
    palette: ArtworkPalette?,
    artwork: @Composable BoxScope.() -> Unit,
    onCreditClick: (() -> Unit)?,
    explicit: Boolean,
    lossless: Boolean,
    losslessLabel: String?,
    about: String?,
    runsUnderList: Boolean,
    actions: @Composable () -> Unit,
) {
    val ground = palette?.background ?: MaterialTheme.colorScheme.surface
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safeStart = with(density) { WindowInsets.safeDrawing.getLeft(density, layoutDirection).toDp() }
    val words = Color.White
    val quietWords = Color.White.copy(alpha = 0.64f)

    // Arrives the way the sleeve does on iOS: settling from a touch larger as it fades up.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ground),
    ) {
        if (palette != null) ArtworkWash(palette = palette, modifier = Modifier.fillMaxSize(), washFraction = 0.9f)
        val columnWidth = maxWidth * BleedPaneFraction
        // A little wider than its column, so the fade runs on under the first of the tracks.
        // Only where the list has no ground of its own (an album); a playlist page covers it.
        val artWidth = if (runsUnderList) minOf(maxWidth * 0.62f, columnWidth + 120.dp) else columnWidth
        Box(
            Modifier
                .fillMaxHeight()
                .width(artWidth)
                .graphicsLayer {
                    alpha = shown.value
                    val s = 1.04f - 0.04f * shown.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    // Its own layer, so the fades are masks over the finished picture.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            0.66f to Color.Black,
                            0.86f to Color.Black.copy(alpha = 0.4f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.5f to Color.Black,
                            0.84f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize(), content = artwork)
        }
        // Weight under the words, so white type reads over a pale sleeve.
        // Across the whole window: stopped at the column's edge, it left a seam down the middle.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to ground.copy(alpha = 0.6f))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(columnWidth)
                .padding(start = safeStart + 20.dp, end = 20.dp, bottom = bottom + 8.dp)
                .graphicsLayer {
                    alpha = shown.value
                    translationY = (1f - shown.value) * 24.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = words,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (explicit) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(com.ozyern.exhale.R.drawable.explicit),
                        contentDescription = null,
                        tint = quietWords,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (!credit.isNullOrBlank()) {
                Text(
                    text = credit,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = words.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .let { if (onCreditClick != null) it.clickable(onClick = onCreditClick) else it },
                )
            }
            if (meta.isNotBlank() || lossless) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotBlank()) {
                        Text(meta, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = quietWords, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (lossless) {
                        if (meta.isNotBlank()) Text(" · ", fontSize = 12.sp, color = quietWords)
                        Icon(
                            painter = painterResource(com.ozyern.exhale.R.drawable.ic_apple_lossless),
                            contentDescription = null,
                            tint = quietWords,
                            modifier = Modifier.size(width = 14.dp, height = 10.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(losslessLabel ?: "Lossless", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = quietWords)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                actions()
            }
            if (!about.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                var expanded by remember(about) { mutableStateOf(false) }
                Text(
                    text = about,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = quietWords,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .animateContentSize(),
                )
            }
        }
    }
}
