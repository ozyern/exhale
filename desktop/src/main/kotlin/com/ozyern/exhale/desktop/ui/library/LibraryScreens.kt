/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.library

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.StationCard
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.home.SectionHeader
import com.ozyern.exhale.desktop.ui.home.ShelfPlaceholder
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.pages.BrowseResult
import com.ozyern.exhale.innertube.pages.MoodAndGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Android Mood & Genres tab: every category as a gradient tile, grouped under its heading. */
@Composable
fun MoodAndGenresScreen(insets: ChromeInsets, onOpen: (browseId: String, params: String?, title: String) -> Unit) {
    val groups by produceState<List<MoodAndGenres>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { YouTube.moodAndGenres().getOrNull() }.orEmpty()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(210.dp),
        contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 36.dp, bottom = insets.bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { LargeTitle("Mood & Genres", Modifier.padding(bottom = 8.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) { com.ozyern.exhale.desktop.ui.pages.FlacShelf() }
        val loaded = groups
        if (loaded == null) {
            items(12) { Box(Modifier.fillMaxWidth().height(88.dp).glassPlate(RoundedCornerShape(16.dp))) }
        } else {
            loaded.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(group.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(group.items, key = { "${group.title}:${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                    MoodButton(item.title, item.stripeColor) { onOpen(item.endpoint.browseId, item.endpoint.params, item.title) }
                }
            }
        }
    }
}

/** Android's MoodAndGenresButton: the category's stripe colour as a diagonal gradient, title bottom-left. */
@Composable
private fun MoodButton(title: String, stripeColor: Long, onClick: () -> Unit) {
    val base = Color(stripeColor)
    val dark = Color(red = base.red * 0.65f, green = base.green * 0.65f, blue = base.blue * 0.65f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.02f)
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(base, dark), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f)),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A YouTube Music browse page — what a Mood & Genres tile opens. */
@Composable
fun BrowseScreen(
    browseId: String,
    params: String?,
    title: String,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
) {
    val result by produceState<BrowseResult?>(initialValue = null, browseId, params) {
        value = withContext(Dispatchers.IO) { YouTube.browse(browseId, params).getOrNull() }
    }
    val start = insets.start + 20.dp
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    val open = rememberItemOpener(player)
    LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = insets.bottom + 16.dp), modifier = Modifier.fillMaxSize()) {
        item { LargeTitle(result?.title ?: title, Modifier.padding(start = start, end = 40.dp)) }
        val loaded = result
        if (loaded == null) {
            items(count = 3) { ShelfPlaceholder(rowPadding, start) }
        } else {
            loaded.items.forEachIndexed { index, section ->
                item(key = "h$index") { Box(Modifier.padding(start = start, top = 28.dp, bottom = 10.dp)) { SectionHeader(null, section.title ?: "") } }
                item(key = "r$index") {
                    LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(section.items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                            ItemCard(item, item.id == state.current?.id, onClick = { open(item) }, onPlay = { player.playNow(item) })
                        }
                    }
                }
            }
        }
    }
}

private val LibraryTabs = listOf("Recently played", "Artists", "Stations")

/** The Library tab: what's been played, the artists picked in Your Music Taste, and their stations. */
@Composable
fun LibraryScreen(
    feed: HomeFeed,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onOpenSettings: () -> Unit,
    /** The section to show: the sidebar picks it; the chips move it and report back. */
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
) {
    val history by DesktopPrefs.history.collectAsState()
    val artists by DesktopPrefs.artists.collectAsState()
    val open = rememberItemOpener(player)
    val navigator = LocalNavigator.current
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val items: List<YTItem> = when (tab) {
        0 -> history
        1 -> artists.map { ArtistItem(id = it.id, title = it.name, thumbnail = it.thumbnail, shuffleEndpoint = null, radioEndpoint = null) }
        else -> feed.topPicks.map { it.item }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(184.dp),
        contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 36.dp, bottom = insets.bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LargeTitle("Library", Modifier.weight(1f))
                GlassIconButton(NavIcons.Stats, "Your Sound Chem", { navigator?.openSoundChem?.invoke() })
                GlassIconButton(NavIcons.Settings, "Settings", onOpenSettings, Modifier.padding(start = 10.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryTabs.forEachIndexed { index, label ->
                    LibraryChip(label, index == tab) {
                        tab = index
                        onTabChange(index)
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when (tab) {
                        0 -> "Songs you play show up here."
                        1 -> "Pick artists in Your Music Taste."
                        else -> "Stations appear once Home has loaded."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        if (tab == 2) {
            // Stations are artist radios: square station cards that play, not artist photos.
            itemsIndexed(feed.topPicks, key = { i, pick -> "stations:$i:${pick.item.id}" }) { _, pick ->
                StationCard(pick.title, pick.eyebrow, pick.image, onClick = { player.playItem(pick.item) }, artSize = 184.dp)
            }
        } else {
            itemsIndexed(items, key = { i, item -> "$tab:$i:${item.id}" }) { _, item ->
                val active = item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id)
                ItemCard(item, active, onClick = { open(item) }, artSize = 184.dp, onPlay = { player.playNow(item) })
            }
        }
    }
}

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f), label = "chip_bg")
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "chip_fg")
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
    Spacer(Modifier.padding(0.dp))
}
