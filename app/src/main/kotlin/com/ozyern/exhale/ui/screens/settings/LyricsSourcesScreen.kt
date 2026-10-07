/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * One list for which lyrics sources are asked and in what order, after BitChord's sources page
 * (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DefaultProviderOrder
import com.ozyern.exhale.constants.EnableBetterLyricsKey
import com.ozyern.exhale.constants.EnableBinimumLyricsKey
import com.ozyern.exhale.constants.EnableGeniusLyricsKey
import com.ozyern.exhale.constants.EnableKugouKey
import com.ozyern.exhale.constants.EnableLrcLibKey
import com.ozyern.exhale.constants.EnableLyricsPlusKey
import com.ozyern.exhale.constants.EnableMegalobizLyricsKey
import com.ozyern.exhale.constants.EnableMusixmatchLyricsKey
import com.ozyern.exhale.constants.EnablePaxSenixLyricsKey
import com.ozyern.exhale.constants.EnableSimpMusicLyricsKey
import com.ozyern.exhale.constants.EnableUnisonLyricsKey
import com.ozyern.exhale.constants.LegacyDefaultProviderOrder
import com.ozyern.exhale.constants.PreferWordSyncedLyricsKey
import com.ozyern.exhale.constants.PreferredLyricsProvider
import com.ozyern.exhale.constants.PreferredLyricsProviderKey
import com.ozyern.exhale.constants.ProviderOrderKey
import com.ozyern.exhale.constants.detail
import com.ozyern.exhale.constants.displayName
import com.ozyern.exhale.constants.wordSynced
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.roundToInt

/** The switch that decides whether a source is contacted at all. */
fun PreferredLyricsProvider.enabledKey(): Preferences.Key<Boolean> = when (this) {
    PreferredLyricsProvider.LRCLIB -> EnableLrcLibKey
    PreferredLyricsProvider.KUGOU -> EnableKugouKey
    PreferredLyricsProvider.BETTER_LYRICS -> EnableBetterLyricsKey
    PreferredLyricsProvider.SIMPMUSIC -> EnableSimpMusicLyricsKey
    PreferredLyricsProvider.LYRICS_PLUS -> EnableLyricsPlusKey
    PreferredLyricsProvider.BINIMUM -> EnableBinimumLyricsKey
    PreferredLyricsProvider.UNISON -> EnableUnisonLyricsKey
    PreferredLyricsProvider.MEGALOBIZ -> EnableMegalobizLyricsKey
    PreferredLyricsProvider.GENIUS -> EnableGeniusLyricsKey
    PreferredLyricsProvider.PAXSENIX -> EnablePaxSenixLyricsKey
    PreferredLyricsProvider.MUSIXMATCH -> EnableMusixmatchLyricsKey
    PreferredLyricsProvider.LRC_RED -> com.ozyern.exhale.constants.EnableLrcRedLyricsKey
}

/** The order in force: what was saved, with anything added since after it. */
fun lyricsSourceOrder(saved: String?): List<PreferredLyricsProvider> {
    val parsed = saved.orEmpty().split(",")
        .mapNotNull { name -> PreferredLyricsProvider.entries.find { it.name == name.trim() } }
        .distinct()
    if (parsed.isEmpty() || parsed == LegacyDefaultProviderOrder) return DefaultProviderOrder
    return com.ozyern.exhale.constants.withNewSources(parsed)
}

/**
 * Every lyrics source in the order it is trusted, each with its own switch, in one list. Drag a
 * source by its handle to move it; the one at the top is asked first and wins whenever it has the
 * song. All of them are asked at once, so order costs nothing in speed — it only decides whose
 * answer is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSourcesScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val (savedOrder, setSavedOrder) = rememberPreference(ProviderOrderKey, "")
    val (_, setPreferred) = rememberPreference(PreferredLyricsProviderKey, PreferredLyricsProvider.BINIMUM.name)
    val (preferWord, setPreferWord) = rememberPreference(PreferWordSyncedLyricsKey, true)
    var order by remember(savedOrder) { mutableStateOf(lyricsSourceOrder(savedOrder)) }

    fun save(newOrder: List<PreferredLyricsProvider>) {
        setSavedOrder(newOrder.joinToString(",") { it.name })
        newOrder.firstOrNull()?.let { setPreferred(it.name) }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            // Below the content, not around the viewport: the page scrolls on under the dock.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceGroup {
            SwitchPreference(
                title = { Text("Prefer word-by-word timing") },
                description = if (preferWord) {
                    "A source further down with word timing wins over a line-timed one above it"
                } else {
                    "The highest source with synced lyrics wins, word- or line-timed"
                },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = preferWord,
                onCheckedChange = setPreferWord,
            )
        }

        PreferenceGroup(title = "Order — drag to change") {
            ReorderableSources(
                order = order,
                onMove = { order = it },
                onSettle = { save(order) },
            )
        }

        Text(
            text = "Every source on the list is asked at the same time; the order only decides whose answer " +
                "you see. Before any of them, Binimum is asked which recording this is, so the sources that can " +
                "match on the recording itself — not just its name — never return the wrong edit's words.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )

        Text(
            text = "Restore default order",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    order = DefaultProviderOrder
                    save(DefaultProviderOrder)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    SettingsTopAppBar(
        title = { Text("Lyrics sources") },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

/**
 * The list itself: fixed-height rows, so a drag can say which slot it is over by distance alone.
 * The row under the finger follows it and swaps with a neighbour as soon as it is past half of it.
 */
@Composable
private fun ReorderableSources(
    order: List<PreferredLyricsProvider>,
    onMove: (List<PreferredLyricsProvider>) -> Unit,
    onSettle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { SOURCE_ROW_HEIGHT.toPx() }
    val latestOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<PreferredLyricsProvider?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }

    Column {
        order.forEachIndexed { index, source ->
          key(source) {
            val isDragging = dragging == source
            val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "sourceLift")
            val enabledKey = source.enabledKey()
            val (enabled, setEnabled) = rememberPreference(enabledKey, true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset.floatValue else 0f
                        scaleX = 1f + 0.02f * lift
                        scaleY = 1f + 0.02f * lift
                    }
                    .shadow((8 * lift).dp, RoundedCornerShape(14.dp), clip = false)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = lift),
                        RoundedCornerShape(14.dp),
                    )
                    .fillMaxWidth()
                    .height(SOURCE_ROW_HEIGHT)
                    .padding(start = 6.dp, end = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .pointerInput(source) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = source
                                    dragOffset.floatValue = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    dragging = null
                                    dragOffset.floatValue = 0f
                                    onSettle()
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragOffset.floatValue = 0f
                                    onSettle()
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset.floatValue += amount.y
                                val current = latestOrder.indexOf(source)
                                val steps = (dragOffset.floatValue / rowPx).roundToInt()
                                val target = (current + steps).coerceIn(0, latestOrder.lastIndex)
                                if (target != current) {
                                    val moved = latestOrder.toMutableList().apply { add(target, removeAt(current)) }
                                    dragOffset.floatValue -= (target - current) * rowPx
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onMove(moved)
                                }
                            }
                        },
                ) {
                    Icon(
                        painterResource(R.drawable.drag_handle),
                        contentDescription = "Move ${source.displayName()}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "${index + 1}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.displayName(),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                        )
                        Spacer(Modifier.width(6.dp))
                        SyncBadge(wordSynced = source.wordSynced)
                    }
                    Text(
                        text = source.detail(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                LiquidToggle(checked = enabled, onCheckedChange = setEnabled)
            }
          }
        }
    }
}

/** "Word" for per-word timing, "Line" for whole lines. */
@Composable
internal fun SyncBadge(wordSynced: Boolean, onDark: Boolean = false) {
    val ink = if (onDark) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    val accent = if (onDark) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary
    Text(
        text = if (wordSynced) "Word" else "Line",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (wordSynced) accent else ink.copy(alpha = 0.6f),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background((if (wordSynced) accent else ink).copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private val SOURCE_ROW_HEIGHT = 64.dp
