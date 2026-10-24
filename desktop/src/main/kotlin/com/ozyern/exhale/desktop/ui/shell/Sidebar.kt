/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import com.ozyern.exhale.desktop.ui.library.LibrarySection
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.frostedPane
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SidebarWidth = 220.dp

/** Every place the sidebar can take you: the Music app's own rows, and FLAC under Store. */
enum class SidebarItem(val label: String, val section: LibrarySection? = null) {
    Search("Search"),
    Home("Home"),
    New("New", LibrarySection.New),
    Radio("Radio", LibrarySection.Radio),
    RecentlyAdded("Recently Added", LibrarySection.RecentlyAdded),
    Artists("Artists", LibrarySection.Artists),
    Albums("Albums", LibrarySection.Albums),
    Songs("Songs", LibrarySection.Songs),
    MusicVideos("Music Videos", LibrarySection.MusicVideos),
    Flac("FLAC"),
    AllPlaylists("All Playlists", LibrarySection.AllPlaylists),
    Favorites("Favorite Songs", LibrarySection.Favorites),
    Settings("Settings"),
}

/**
 * The Music app's sidebar on macOS: a pane of glass inset from the window's edges, its rows set small
 * and regular with plain grey symbols. Only the row you're on takes the accent, on a soft grey fill;
 * nothing grows, moves or turns bold. Search, Home, Browse, Radio and FLAC at the top, then Library,
 * then your playlists with their covers, and you at the foot.
 */
@Composable
fun AppSidebar(
    selected: SidebarItem?,
    onSelect: (SidebarItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedPlaylist: String? = null,
) {
    val backdrop = LocalAppBackdrop.current
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val account by AccountSession.account.collectAsState()
    val navigator = LocalNavigator.current
    // Your playlists, once you're signed in: what the Music app lists under Playlists.
    val playlists by produceState(emptyList<PlaylistItem>(), account) {
        value = if (account == null || !AccountSession.isSignedIn) emptyList() else withContext(Dispatchers.IO) {
            YouTube.library("FEmusic_liked_playlists").completed().getOrNull()?.items.orEmpty()
                .filterIsInstance<PlaylistItem>()
                // The "New playlist" tile and auto-generated mixes aren't playlists you'd pick from a list.
                .filter { it.id.isNotBlank() && !it.id.startsWith("RDCLAK") }
        }
    }
    // On one of your playlists, its row is the one lit, not the page you opened it from.
    val playlistOn = selectedPlaylist != null && playlists.any { it.id == selectedPlaylist }
    val selected = if (playlistOn) null else selected
    Column(
        modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            // Edge to edge down the window's left side, as macOS draws a source list: frosted, square,
            // and parted from the page by one hairline. The title bar sits over its top.
            .then(if (backdrop != null) Modifier.frostedPane(backdrop, tintAlpha = 0.55f) else Modifier)
            .drawWithContent {
                drawContent()
                drawLine(divider, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
            }
            .padding(start = 10.dp, end = 10.dp, top = LocalTitleBarHeight.current + 2.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            @Composable
            fun Row(icon: ImageVector, item: SidebarItem) = SidebarRow(icon, item.label, selected == item) { onSelect(item) }
            Row(SidebarIcons.Search, SidebarItem.Search)
            Row(SidebarIcons.Home, SidebarItem.Home)
            Row(SidebarIcons.Browse, SidebarItem.New)
            Row(SidebarIcons.Radio, SidebarItem.Radio)

            SidebarHeading("Library")
            Row(SidebarIcons.Clock, SidebarItem.RecentlyAdded)
            Row(SidebarIcons.Mic, SidebarItem.Artists)
            Row(SidebarIcons.Album, SidebarItem.Albums)
            Row(SidebarIcons.Note, SidebarItem.Songs)
            Row(SidebarIcons.Video, SidebarItem.MusicVideos)

            SidebarHeading("Store")
            Row(SidebarIcons.Disc, SidebarItem.Flac)

            SidebarHeading("Playlists")
            Row(SidebarIcons.Playlists, SidebarItem.AllPlaylists)
            Row(SidebarIcons.Star, SidebarItem.Favorites)
            if (playlists.isNotEmpty()) {
                playlists.forEach { playlist ->
                    SidebarRow(
                        icon = null,
                        label = playlist.title,
                        on = playlistOn && selectedPlaylist == playlist.id,
                        artwork = playlist.thumbnail,
                    ) { navigator?.openPlaylist?.invoke(playlist.id, playlist.title, playlist.thumbnail) }
                }
            }
        }
        // You, at the foot, as the Music app keeps it; it's the way into Settings.
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val on = selected == SidebarItem.Settings
        val fill by animateFloatAsState(if (on) 0.09f else if (hovered) 0.045f else 0f, tween(120), label = "sidebar_account")
        Row(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = fill))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onSelect(SidebarItem.Settings) }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = account?.avatar
                if (avatar != null) {
                    AsyncImage(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp))
                } else {
                    Icon(SidebarIcons.Person, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.width(9.dp))
            Text(
                account?.name ?: "Settings",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SidebarHeading(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 3.dp),
    )
}

/** One row: a symbol (or a playlist's cover) and its name. The accent and a grey fill mark where you are. */
@Composable
private fun SidebarRow(icon: ImageVector?, label: String, on: Boolean, artwork: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateFloatAsState(if (on) 0.09f else if (hovered) 0.045f else 0f, tween(120), label = "sidebar_row")
    val glyph by animateColorAsState(if (on) accent else ink.copy(alpha = 0.78f), tween(120), label = "sidebar_glyph")
    val text by animateColorAsState(if (on) accent else ink, tween(120), label = "sidebar_text")
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(ink.copy(alpha = fill))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, null, tint = glyph, modifier = Modifier.size(17.dp))
            } else {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.1f))) {
                    if (artwork != null) {
                        AsyncImage(artwork.resizedThumbnail(72), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(label, fontSize = 13.sp, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Line drawings in SF Symbols' manner: one even stroke, round ends, nothing filled but a dot. */
private fun lineIcon(name: String, pathData: String, dot: String? = null): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    if (dot != null) builder.addPath(pathData = addPathNodes(dot), fill = SolidColor(Color.Black))
    return builder.build()
}

private object SidebarIcons {
    val Search by lazy { lineIcon("Sidebar.Search", "M10.5,4a6.5,6.5 0 1 1 0,13a6.5,6.5 0 1 1 0,-13z M15.4,15.4L20,20") }
    val Home by lazy { lineIcon("Sidebar.Home", "M4,10.4L12,4l8,6.4V19a1.5,1.5 0 0 1 -1.5,1.5H14.5V15.2h-5v5.3H5.5A1.5,1.5 0 0 1 4,19z") }
    val Browse by lazy {
        lineIcon(
            "Sidebar.Browse",
            "M6,4.5h3a1.5,1.5 0 0 1 1.5,1.5v3a1.5,1.5 0 0 1 -1.5,1.5h-3a1.5,1.5 0 0 1 -1.5,-1.5v-3a1.5,1.5 0 0 1 1.5,-1.5z" +
                "M15,4.5h3a1.5,1.5 0 0 1 1.5,1.5v3a1.5,1.5 0 0 1 -1.5,1.5h-3a1.5,1.5 0 0 1 -1.5,-1.5v-3a1.5,1.5 0 0 1 1.5,-1.5z" +
                "M6,13.5h3a1.5,1.5 0 0 1 1.5,1.5v3a1.5,1.5 0 0 1 -1.5,1.5h-3a1.5,1.5 0 0 1 -1.5,-1.5v-3a1.5,1.5 0 0 1 1.5,-1.5z" +
                "M15,13.5h3a1.5,1.5 0 0 1 1.5,1.5v3a1.5,1.5 0 0 1 -1.5,1.5h-3a1.5,1.5 0 0 1 -1.5,-1.5v-3a1.5,1.5 0 0 1 1.5,-1.5z",
        )
    }
    val Radio by lazy {
        lineIcon(
            "Sidebar.Radio",
            "M8.6,8.6a4.8,4.8 0 0 0 0,6.8 M15.4,8.6a4.8,4.8 0 0 1 0,6.8 M5.7,5.7a8.9,8.9 0 0 0 0,12.6 M18.3,5.7a8.9,8.9 0 0 1 0,12.6",
            dot = "M12,10.3a1.7,1.7 0 1 1 0,3.4a1.7,1.7 0 1 1 0,-3.4z",
        )
    }
    val Disc by lazy {
        lineIcon("Sidebar.Disc", "M12,3.8a8.2,8.2 0 1 1 0,16.4a8.2,8.2 0 1 1 0,-16.4z M12,9.6a2.4,2.4 0 1 1 0,4.8a2.4,2.4 0 1 1 0,-4.8z")
    }
    val Clock by lazy { lineIcon("Sidebar.Clock", "M12,3.8a8.2,8.2 0 1 1 0,16.4a8.2,8.2 0 1 1 0,-16.4z M12,7.8v4.6l3,1.9") }
    val Mic by lazy { lineIcon("Sidebar.Mic", "M12,3.5a3,3 0 0 1 3,3v5a3,3 0 0 1 -6,0v-5a3,3 0 0 1 3,-3z M6.5,11.2a5.5,5.5 0 0 0 11,0 M12,16.7V20.5 M9,20.5h6") }
    val Album by lazy { lineIcon("Sidebar.Album", "M7,8.5h10a2,2 0 0 1 2,2v7.5a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-7.5a2,2 0 0 1 2,-2z M7.5,5.8h9 M9.5,3.4h5") }
    val Note by lazy {
        lineIcon("Sidebar.Note", "M9.5,17.5V6.2l9.5,-2v11.3 M9.5,9.2l9.5,-2", dot = "M6.9,15.2a2.6,2.6 0 1 1 0,5.2a2.6,2.6 0 1 1 0,-5.2z M16.4,13.2a2.6,2.6 0 1 1 0,5.2a2.6,2.6 0 1 1 0,-5.2z")
    }
    val Video by lazy { lineIcon("Sidebar.Video", "M5,5.5h14a1.8,1.8 0 0 1 1.8,1.8v8.4a1.8,1.8 0 0 1 -1.8,1.8h-14a1.8,1.8 0 0 1 -1.8,-1.8v-8.4a1.8,1.8 0 0 1 1.8,-1.8z M8.5,20.5h7 M10.4,8.9v5.2l4.2,-2.6z") }
    val Playlists by lazy {
        lineIcon(
            "Sidebar.Playlists",
            "M5,5h3v3h-3z M10.5,5h3v3h-3z M16,5h3v3h-3z M5,10.5h3v3h-3z M10.5,10.5h3v3h-3z M16,10.5h3v3h-3z M5,16h3v3h-3z M10.5,16h3v3h-3z M16,16h3v3h-3z",
        )
    }
    val Star by lazy { lineIcon("Sidebar.Star", "M12,3.6l2.5,5.2 5.7,0.8 -4.1,4 1,5.7 -5.1,-2.7 -5.1,2.7 1,-5.7 -4.1,-4 5.7,-0.8z") }
    val Person by lazy { lineIcon("Sidebar.Person", "M12,4.5a3.6,3.6 0 1 1 0,7.2a3.6,3.6 0 1 1 0,-7.2z M5,20c0.9,-3.6 3.7,-5.4 7,-5.4s6.1,1.8 7,5.4") }
}
