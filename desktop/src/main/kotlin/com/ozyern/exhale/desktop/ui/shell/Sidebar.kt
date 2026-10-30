/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.Pin
import com.ozyern.exhale.desktop.data.SidebarPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.SidebarShape
import com.ozyern.exhale.desktop.ui.glass.fadeOutBottom
import com.ozyern.exhale.desktop.ui.glass.progressiveBlur
import com.ozyern.exhale.desktop.ui.glass.recordBackdrop
import com.ozyern.exhale.desktop.ui.glass.sidebarGlass
import com.ozyern.exhale.desktop.ui.library.LibrarySection
import com.ozyern.exhale.desktop.ui.isPlaceholderArtwork
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** The gap between the sidebar and the window's edges: the Music app floats it as a pane inside the window. */
val SidebarInset = 8.dp

/** The top of the pane, where macOS keeps the traffic lights and Exhale the sidebar button. */
private val SidebarHeader = 44.dp

/** You, at the foot. The rows scroll on under it, into the blur. */
private val SidebarFooter = 50.dp

/** How tall the blur's soft top edge is, above the footer. */
private val BlurFeather = 64.dp


private val RowShape = RoundedCornerShape(8.dp)

/** A row's height when the list is long enough to scroll. */
private val RowHeight = 33.dp

/** The most a row grows to when there are few enough to fill the pane: airy, never stretched. */
private val RowHeightMax = 40.dp

/** A heading's share of the column: its height and the space above and below it. */
private val HeadingSpan = 35.dp

/** How tall the rows are drawn: set by the pane so a short list fills it, as the Music app's does. */
private val LocalRowHeight = androidx.compose.runtime.compositionLocalOf { RowHeight }

/** The Music app's accent: only the row you're on takes it, symbol and label both. */
private val MusicAccent = com.ozyern.exhale.desktop.ui.AppleColors.Accent

/** macOS's dark sidebar text and symbols: near-white, the symbols a step quieter. */
private val RowInk = com.ozyern.exhale.desktop.ui.AppleColors.Label

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
    Flac("FLAC Store"),
    AllPlaylists("All Playlists", LibrarySection.AllPlaylists),
    Favorites("Favorite Songs", LibrarySection.Favorites),
    Settings("Settings"),
}

/** Rows Edit Sidebar can switch off, by the key [SidebarPrefs.off] keeps. Search always stays. */
private const val PinsKey = "Pins"

/**
 * The Music app's sidebar on macOS 26, row for row: a pane of glass floating inside the window, its
 * rows set small and regular with plain grey symbols. Only the row you're on takes the accent, on a
 * soft grey fill. Search, Home, New and Radio; then Library, opening with Pins; then Store; then
 * your playlists with their covers; and you at the foot, over a blur the rows scroll on into.
 *
 * Headings fold their rows away (the chevron that shows on hover), Edit Sidebar puts a checkbox on
 * every row, the right edge drags to resize, and the sidebar button in the title bar hides it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppSidebar(
    selected: SidebarItem?,
    onSelect: (SidebarItem) -> Unit,
    player: DesktopPlayer,
    glassBackdrop: com.kyant.backdrop.Backdrop?,
    width: Dp,
    onResize: (Dp) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    selectedPage: String? = null,
) {
    val appBackdrop = glassBackdrop ?: LocalAppBackdrop.current
    val prefs by DesktopPrefs.sidebar.collectAsState()
    val pins by DesktopPrefs.pins.collectAsState()
    val account by AccountSession.account.collectAsState()
    val navigator = LocalNavigator.current
    var editing by remember { mutableStateOf(false) }
    // Your playlists, once you're signed in: what the Music app lists under Playlists.
    val playlists by produceState(emptyList<PlaylistItem>(), account) {
        value = if (account == null || !AccountSession.isSignedIn) emptyList() else withContext(Dispatchers.IO) {
            YouTube.library("FEmusic_liked_playlists").completed().getOrNull()?.items.orEmpty()
                .filterIsInstance<PlaylistItem>()
                // The "New playlist" tile and auto-generated mixes aren't playlists you'd pick from a list.
                .filter { it.id.isNotBlank() && !it.id.startsWith("RDCLAK") }
        }
    }
    // On a pinned page or one of your playlists, its row is the one lit, not the page you opened it from.
    val pageOn = selectedPage != null && (pins.any { it.id == selectedPage } || playlists.any { it.id == selectedPage })
    val selected = if (pageOn) null else selected

    fun shown(key: String) = editing || key !in prefs.off
    fun open(heading: String) = editing || heading !in prefs.folded
    fun toggleRow(key: String) = DesktopPrefs.updateSidebar { it.copy(off = if (key in it.off) it.off - key else it.off + key) }
    fun hide() = DesktopPrefs.updateSidebar { it.copy(hidden = true) }

    // What a right-click anywhere on the pane offers, under whatever the row itself adds.
    val paneMenu: @Composable ColumnScope.(() -> Unit) -> Unit = { dismiss ->
        MenuAction(if (editing) "Done Editing" else "Edit Sidebar") { editing = !editing; dismiss() }
        MenuAction("Hide Sidebar") { hide(); dismiss() }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .padding(start = SidebarInset, top = SidebarInset, bottom = SidebarInset)
            .width(width)
            .fillMaxHeight()
            .then(if (appBackdrop != null) Modifier.sidebarGlass(appBackdrop) else Modifier.background(Color(0xF5222224), SidebarShape))
            // Rows and the blur at the foot stay inside the pane's rounded corners.
            .clip(SidebarShape),
    ) {
        // The Music app's sidebar runs down to you at the foot. With fewer rows than fill the pane, they
        // share out the height left over, up to RowHeightMax; with more, they keep RowHeight and scroll.
        val rowCount = run {
            fun count(vararg items: SidebarItem) = items.count { shown(it.name) }
            var n = count(SidebarItem.Search, SidebarItem.Home, SidebarItem.New, SidebarItem.Radio)
            if (open("Library")) {
                if (shown(PinsKey)) n += 1 + if (prefs.pinsOpen && !editing) pins.size else 0
                n += count(SidebarItem.RecentlyAdded, SidebarItem.Artists, SidebarItem.Albums, SidebarItem.Songs, SidebarItem.MusicVideos)
            }
            if (open("Store")) n += count(SidebarItem.Flac)
            if (open("Playlists")) n += count(SidebarItem.AllPlaylists, SidebarItem.Favorites) + if (editing) 0 else playlists.size
            n.coerceAtLeast(1)
        }
        val forRows = maxHeight - SidebarHeader - (SidebarFooter + 14.dp) - HeadingSpan * 3
        val fitted = (forRows / rowCount).coerceIn(RowHeight, RowHeightMax)
        val rowHeight by androidx.compose.animation.core.animateDpAsState(fitted, tween(220), label = "sidebar_row_height")
        val rows = rememberLayerBackdrop()
        androidx.compose.runtime.CompositionLocalProvider(LocalRowHeight provides rowHeight) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = SidebarHeader)
                .clipToBounds()
                // The sharp rows leave as the blurred copy below takes over.
                .fadeOutBottom(fadeLength = 40.dp, fadeEnd = SidebarFooter - 12.dp)
                .recordBackdrop(rows)
                .verticalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = SidebarFooter + 14.dp),
        ) {
            @Composable
            fun Item(icon: ImageVector, item: SidebarItem) {
                if (!shown(item.name)) return
                SidebarRow(
                    icon = icon,
                    label = item.label,
                    on = selected == item,
                    editing = editing && item != SidebarItem.Search,
                    checked = item.name !in prefs.off,
                    onCheck = { toggleRow(item.name) },
                    menu = paneMenu,
                ) { onSelect(item) }
            }

            Item(SidebarIcons.Search, SidebarItem.Search)
            Item(SidebarIcons.Home, SidebarItem.Home)
            Item(SidebarIcons.Grid, SidebarItem.New)
            Item(SidebarIcons.Radio, SidebarItem.Radio)

            SidebarSection("Library", open("Library"), editing, paneMenu) {
                if (shown(PinsKey)) {
                    SidebarRow(
                        icon = SidebarIcons.Pin,
                        label = "Pins",
                        on = false,
                        disclosure = prefs.pinsOpen,
                        onDisclosure = { DesktopPrefs.updateSidebar { it.copy(pinsOpen = !it.pinsOpen) } },
                        editing = editing,
                        checked = PinsKey !in prefs.off,
                        onCheck = { toggleRow(PinsKey) },
                        menu = paneMenu,
                    ) { DesktopPrefs.updateSidebar { it.copy(pinsOpen = !it.pinsOpen) } }
                    AnimatedVisibility(
                        visible = prefs.pinsOpen && !editing,
                        enter = expandVertically(spring(1f, 500f)) + fadeIn(tween(140)),
                        exit = shrinkVertically(spring(1f, 600f)) + fadeOut(tween(100)),
                    ) {
                        Column {
                            pins.forEach { pin ->
                                SidebarRow(
                                    icon = if (pin.thumbnail == null) SidebarIcons.Note else null,
                                    label = pin.title,
                                    on = selectedPage == pin.id,
                                    artwork = pin.thumbnail,
                                    roundArt = pin.kind == Pin.Kind.Artist,
                                    indent = 16.dp,
                                    menu = { dismiss ->
                                        MenuAction("Unpin") { DesktopPrefs.togglePin(pin); dismiss() }
                                        MenuDivider()
                                        paneMenu(dismiss)
                                    },
                                ) {
                                    when (pin.kind) {
                                        Pin.Kind.Song -> player.playItem(
                                            SongItem(
                                                id = pin.id,
                                                title = pin.title,
                                                artists = listOfNotNull(pin.subtitle?.let { Artist(name = it, id = null) }),
                                                thumbnail = pin.thumbnail.orEmpty(),
                                            ),
                                        )
                                        Pin.Kind.Album -> navigator?.openAlbum?.invoke(pin.id, pin.title, pin.thumbnail)
                                        Pin.Kind.Playlist -> navigator?.openPlaylist?.invoke(pin.id, pin.title, pin.thumbnail)
                                        Pin.Kind.Artist -> navigator?.openArtist?.invoke(pin.id, pin.title, pin.thumbnail)
                                    }
                                }
                            }
                        }
                    }
                }
                Item(SidebarIcons.Clock, SidebarItem.RecentlyAdded)
                Item(SidebarIcons.Mic, SidebarItem.Artists)
                Item(SidebarIcons.Stack, SidebarItem.Albums)
                Item(SidebarIcons.Note, SidebarItem.Songs)
                Item(SidebarIcons.Video, SidebarItem.MusicVideos)
            }

            SidebarSection("Store", open("Store"), editing, paneMenu) {
                Item(SidebarIcons.Bag, SidebarItem.Flac)
            }

            SidebarSection("Playlists", open("Playlists"), editing, paneMenu) {
                Item(SidebarIcons.Playlists, SidebarItem.AllPlaylists)
                Item(SidebarIcons.StarSquare, SidebarItem.Favorites)
                if (!editing) {
                    playlists.forEach { playlist ->
                        SidebarRow(
                            icon = null,
                            label = playlist.title,
                            on = selectedPage == playlist.id,
                            artwork = playlist.thumbnail,
                            menu = { dismiss ->
                                MenuAction("Play") { player.playItem(playlist); dismiss() }
                                MenuAction(if (DesktopPrefs.isPinned(playlist.id)) "Unpin" else "Pin") {
                                    DesktopPrefs.togglePin(Pin(Pin.Kind.Playlist, playlist.id, playlist.title, playlist.author?.name, playlist.thumbnail))
                                    dismiss()
                                }
                                MenuAction("Copy Link") {
                                    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(playlist.shareLink), null) }
                                    dismiss()
                                }
                                MenuDivider()
                                paneMenu(dismiss)
                            },
                        ) { navigator?.openPlaylist?.invoke(playlist.id, playlist.title, playlist.thumbnail) }
                    }
                }
            }
        }

        // The foot: the rows above blur into it, and you sit on top.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SidebarFooter + BlurFeather)
                .clipToBounds()
                // Heavy enough that a row under you is a smear of its colours, as in the Music app. Over it a
                // frosted band, lighter than the pane, so the foot reads as frost even when your playlists
                // end above it and there is nothing passing under you to smear.
                .progressiveBlur(rows, featherHeight = BlurFeather, blurRadius = 28.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.White.copy(alpha = 0.035f),
                        1f to Color.White.copy(alpha = 0.085f),
                    ),
                ),
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SidebarFooter).padding(horizontal = 4.dp, vertical = 5.dp)) {
            if (editing) {
                DoneButton(Modifier.align(Alignment.CenterEnd)) { editing = false }
            } else {
                AccountRow(
                    name = account?.name,
                    avatar = account?.avatar,
                    on = selected == SidebarItem.Settings,
                    onSettings = { onSelect(SidebarItem.Settings) },
                    onSignIn = onSignIn,
                    paneMenu = paneMenu,
                )
            }
        }

        // The right edge drags the sidebar wider or narrower, as in the Music app.
        val density = LocalDensity.current
        val latestWidth by rememberUpdatedState(width)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 4.dp)
                .width(8.dp)
                .fillMaxHeight()
                .padding(vertical = 18.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(density) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = latestWidth.toPx() },
                        onDragEnd = { DesktopPrefs.updateSidebar { it.copy(width = latestWidth.value) } },
                    ) { change, amount ->
                        change.consume()
                        dragged += amount
                        onResize(with(density) { dragged.toDp() }.coerceIn(SidebarPrefs.MinWidth.dp, SidebarPrefs.MaxWidth.dp))
                    }
                },
        )
        }
    }
}

/**
 * A heading and the rows under it. Hovering it shows a chevron at its end; clicking folds the rows
 * away and brings them back, as the Music app's headings do.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarSection(
    title: String,
    open: Boolean,
    editing: Boolean,
    paneMenu: @Composable ColumnScope.(() -> Unit) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, tween(160), label = "sidebar_heading_chevron")
    Box {
        Row(
            Modifier
                .padding(top = 10.dp, bottom = 5.dp)
                .fillMaxWidth()
                .height(20.dp)
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menu = true }
                .clickable(interactionSource = interaction, indication = null, enabled = !editing) {
                    DesktopPrefs.updateSidebar { it.copy(folded = if (title in it.folded) it.folded - title else it.folded + title) }
                }
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                modifier = Modifier.weight(1f),
            )
            if (!editing) {
                Icon(
                    SidebarIcons.Chevron,
                    contentDescription = if (open) "Hide $title" else "Show $title",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hovered) 0.55f else 0f),
                    modifier = Modifier.size(11.dp).rotate(turn),
                )
            }
        }
        SidebarMenu(menu, onDismiss = { menu = false }) { paneMenu { menu = false } }
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(spring(1f, 500f)) + fadeIn(tween(160)),
        exit = shrinkVertically(spring(1f, 600f)) + fadeOut(tween(110)),
    ) {
        Column(content = content)
    }
}

/**
 * One row: a symbol (or a cover) and its name. Only the row you're on takes the accent, on a grey
 * fill; hovering shows a fainter one. [disclosure] gives the row a chevron in the gutter, as Pins has;
 * under Edit Sidebar a checkbox takes the gutter instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarRow(
    icon: ImageVector?,
    label: String,
    on: Boolean,
    artwork: String? = null,
    roundArt: Boolean = false,
    indent: Dp = 0.dp,
    disclosure: Boolean? = null,
    onDisclosure: () -> Unit = {},
    editing: Boolean = false,
    checked: Boolean = true,
    onCheck: () -> Unit = {},
    menu: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val ink = RowInk
    val accent = MusicAccent
    // As in the Music app: plain symbols in the label colour, and the row you're on in the accent —
    // symbol and label both — over a soft grey plate.
    val lit = on && !editing
    val fill by animateFloatAsState(if (lit) 0.10f else if (hovered || menuOpen) 0.05f else 0f, tween(110), label = "sidebar_row")
    val glyph by animateColorAsState(
        when {
            editing && !checked -> ink.copy(alpha = 0.4f)
            lit -> accent
            else -> ink.copy(alpha = 0.85f)
        },
        tween(110),
        label = "sidebar_glyph",
    )
    val text by animateColorAsState(
        when {
            editing && !checked -> ink.copy(alpha = 0.4f)
            lit -> accent
            else -> ink
        },
        tween(110),
        label = "sidebar_text",
    )
    val turn by animateFloatAsState(if (disclosure == true) 90f else 0f, tween(160), label = "sidebar_disclosure")
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(LocalRowHeight.current)
                .clip(RowShape)
                .background(ink.copy(alpha = fill))
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { if (menu != null && it.buttons.isSecondaryPressed) menuOpen = true }
                .clickable(interactionSource = interaction, indication = null, onClick = if (editing) onCheck else onClick)
                .padding(start = indent, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The gutter: Pins' chevron, or Edit Sidebar's checkbox.
            Box(Modifier.width(14.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when {
                    editing -> Checkbox(checked)
                    disclosure != null -> Icon(
                        SidebarIcons.Chevron,
                        contentDescription = if (disclosure) "Collapse" else "Expand",
                        tint = ink.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(11.dp)
                            .rotate(turn)
                            .clickable(indication = null, interactionSource = null, onClick = onDisclosure),
                    )
                }
            }
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = glyph, modifier = Modifier.size(25.dp))
                } else {
                    val shape: Shape = if (roundArt) CircleShape else RoundedCornerShape(4.dp)
                    Box(Modifier.size(24.dp).clip(shape).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        val cover = artwork?.takeUnless { it.isPlaceholderArtwork() }
                        if (cover != null) {
                            AsyncImage(cover.resizedThumbnail(80), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp))
                        } else {
                            // The Music app's coverless playlist: a grey tile with a note on it.
                            Icon(SidebarIcons.Note, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 14.5.sp, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (menu != null) SidebarMenu(menuOpen, onDismiss = { menuOpen = false }) { menu { menuOpen = false } }
    }
}

/** You, at the foot: your picture and name. Clicking opens what's yours — Settings, or signing in. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AccountRow(
    name: String?,
    avatar: String?,
    on: Boolean,
    onSettings: () -> Unit,
    onSignIn: () -> Unit,
    paneMenu: @Composable ColumnScope.(() -> Unit) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onSurface
    val fill by animateFloatAsState(if (hovered || menuOpen) 0.06f else 0f, tween(110), label = "sidebar_account")
    Box {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ink.copy(alpha = fill))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { menuOpen = true }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp))
                } else {
                    Icon(SidebarIcons.Person, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                name ?: "Not Signed In",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.primary else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SidebarMenu(menuOpen, onDismiss = { menuOpen = false }) {
            if (name == null) MenuAction("Sign In…") { onSignIn(); menuOpen = false }
            // Your listening, by the month: Exhale's answer to the Music app's Replay, kept with what's yours.
            LocalNavigator.current?.let { nav -> MenuAction("Your Sound Chem") { nav.openSoundChem(); menuOpen = false } }
            MenuAction("Settings…") { onSettings(); menuOpen = false }
            MenuDivider()
            paneMenu { menuOpen = false }
        }
    }
}

@Composable
private fun DoneButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** A macOS checkbox: a small rounded square, filled with the accent and ticked when on. */
@Composable
private fun Checkbox(checked: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) accent else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(4.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(SidebarIcons.Tick, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun SidebarMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 13.sp) },
        onClick = onClick,
        modifier = Modifier.height(30.dp),
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
}

/**
 * Drawings after SF Symbols at their Regular weight: one even, fairly light stroke with round ends
 * and joins, softly rounded corners, and fills only where the symbol itself is solid (note heads, the
 * radio's dot). Drawn on a 24-unit grid with ~2 units of margin, as SF's sidebar glyphs sit.
 */
private fun lineIcon(name: String, pathData: String, dot: String? = null, stroke: Float = 2.05f): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    if (dot != null) builder.addPath(pathData = addPathNodes(dot), fill = SolidColor(Color.Black))
    return builder.build()
}

/**
 * One of Phosphor's glyphs (phosphoricons.com, MIT; see NOTICE): drawn on a 256-unit grid as filled
 * outlines, at the regular weight that sits closest to SF Symbols' — Apple's own symbols are licensed
 * for Apple's platforms only. [inner], if given, is drawn again scaled down by [innerScale] about the
 * centre: how star.square puts its star in the square.
 */
private fun phosphor(name: String, pathData: String, inner: String? = null, innerScale: Float = 0.5f): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    if (inner != null) {
        builder.addGroup(scaleX = innerScale, scaleY = innerScale, pivotX = 128f, pivotY = 128f)
            .addPath(pathData = addPathNodes(inner), fill = SolidColor(Color.Black))
            .clearGroup()
    }
    return builder.build()
}

private object SidebarIcons {
    val Search by lazy { phosphor("Sidebar.Search", "M229.66,218.34l-50.07-50.06a88.11,88.11,0,1,0-11.31,11.31l50.06,50.07a8,8,0,0,0,11.32-11.32ZM40,112a72,72,0,1,1,72,72A72.08,72.08,0,0,1,40,112Z") }
    val Home by lazy { phosphor("Sidebar.Home", "M219.31,108.68l-80-80a16,16,0,0,0-22.62,0l-80,80A15.87,15.87,0,0,0,32,120v96a8,8,0,0,0,8,8h64a8,8,0,0,0,8-8V160h32v56a8,8,0,0,0,8,8h64a8,8,0,0,0,8-8V120A15.87,15.87,0,0,0,219.31,108.68ZM208,208H160V152a8,8,0,0,0-8-8H104a8,8,0,0,0-8,8v56H48V120l80-80,80,80Z") }
    val Grid by lazy { phosphor("Sidebar.Grid", "M104,40H56A16,16,0,0,0,40,56v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V56A16,16,0,0,0,104,40Zm0,64H56V56h48v48Zm96-64H152a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V56A16,16,0,0,0,200,40Zm0,64H152V56h48v48Zm-96,32H56a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V152A16,16,0,0,0,104,136Zm0,64H56V152h48v48Zm96-64H152a16,16,0,0,0-16,16v48a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V152A16,16,0,0,0,200,136Zm0,64H152V152h48v48Z") }
    val Radio by lazy { phosphor("Sidebar.Radio", "M128,88a40,40,0,1,0,40,40A40,40,0,0,0,128,88Zm0,64a24,24,0,1,1,24-24A24,24,0,0,1,128,152Zm73.71,7.14a80,80,0,0,1-14.08,22.2,8,8,0,0,1-11.92-10.67,63.95,63.95,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67,80.08,80.08,0,0,1,14.08,84.47ZM69,103.09a64,64,0,0,0,11.26,67.58,8,8,0,0,1-11.92,10.67,79.93,79.93,0,0,1,0-106.67A8,8,0,1,1,80.29,85.34,63.77,63.77,0,0,0,69,103.09ZM248,128a119.58,119.58,0,0,1-34.29,84,8,8,0,1,1-11.42-11.2,103.9,103.9,0,0,0,0-145.56A8,8,0,1,1,213.71,44,119.58,119.58,0,0,1,248,128ZM53.71,200.78A8,8,0,1,1,42.29,212a119.87,119.87,0,0,1,0-168,8,8,0,1,1,11.42,11.2,103.9,103.9,0,0,0,0,145.56Z") }
    val Pin by lazy { phosphor("Sidebar.Pin", "M216,168h-9.29L185.54,48H192a8,8,0,0,0,0-16H64a8,8,0,0,0,0,16h6.46L49.29,168H40a8,8,0,0,0,0,16h80v56a8,8,0,0,0,16,0V184h80a8,8,0,0,0,0-16ZM86.71,48h82.58l21.17,120H65.54Z") }
    val Clock by lazy { phosphor("Sidebar.Clock", "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm64-88a8,8,0,0,1-8,8H128a8,8,0,0,1-8-8V72a8,8,0,0,1,16,0v48h48A8,8,0,0,1,192,128Z") }
    val Mic by lazy { phosphor("Sidebar.Mic", "M168,16A72.07,72.07,0,0,0,96,88a73.29,73.29,0,0,0,.63,9.42L27.12,192.22A15.93,15.93,0,0,0,28.71,213L43,227.29a15.93,15.93,0,0,0,20.78,1.59l94.81-69.53A73.29,73.29,0,0,0,168,160a72,72,0,1,0,0-144Zm56,72a55.72,55.72,0,0,1-11.16,33.52L134.49,43.16A56,56,0,0,1,224,88ZM54.32,216,40,201.68,102.14,117A72.37,72.37,0,0,0,139,153.86ZM112,88a55.67,55.67,0,0,1,11.16-33.51l78.34,78.34A56,56,0,0,1,112,88Zm-2.35,58.34a8,8,0,0,1,0,11.31l-8,8a8,8,0,1,1-11.31-11.31l8-8A8,8,0,0,1,109.67,146.33Z") }
    val Stack by lazy { phosphor("Sidebar.Stack", "M184,72H40A16,16,0,0,0,24,88V200a16,16,0,0,0,16,16H184a16,16,0,0,0,16-16V88A16,16,0,0,0,184,72Zm0,128H40V88H184V200ZM232,56V176a8,8,0,0,1-16,0V56H64a8,8,0,0,1,0-16H216A16,16,0,0,1,232,56Z") }

    // Solid, as the Music app's music.note is.
    val Note by lazy { phosphor("Sidebar.Note", "M210.3,56.34l-80-24A8,8,0,0,0,120,40V148.26A48,48,0,1,0,136,184V50.75l69.7,20.91a8,8,0,1,0,4.6-15.32Z") }
    val Video by lazy { phosphor("Sidebar.Video", "M208,40H48A24,24,0,0,0,24,64V176a24,24,0,0,0,24,24H208a24,24,0,0,0,24-24V64A24,24,0,0,0,208,40Zm8,136a8,8,0,0,1-8,8H48a8,8,0,0,1-8-8V64a8,8,0,0,1,8-8H208a8,8,0,0,1,8,8Zm-48,48a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h64A8,8,0,0,1,168,224Zm-3.56-110.66-48-32A8,8,0,0,0,104,88v64a8,8,0,0,0,12.44,6.66l48-32a8,8,0,0,0,0-13.32ZM120,137.05V103l25.58,17Z") }
    val Bag by lazy { phosphor("Sidebar.Bag", "M239.89,198.12l-14.26-120a16,16,0,0,0-16-14.12H176a48,48,0,0,0-96,0H46.33a16,16,0,0,0-16,14.12l-14.26,120A16,16,0,0,0,20,210.6a16.13,16.13,0,0,0,12,5.4H223.92A16.13,16.13,0,0,0,236,210.6,16,16,0,0,0,239.89,198.12ZM128,32a32,32,0,0,1,32,32H96A32,32,0,0,1,128,32ZM32,200,46.33,80H80v24a8,8,0,0,0,16,0V80h64v24a8,8,0,0,0,16,0V80h33.75l14.17,120Z") }
    val Playlists by lazy { phosphor("Sidebar.Playlists", "M216,48H40A16,16,0,0,0,24,64V192a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V64A16,16,0,0,0,216,48ZM104,144V112h48v32Zm48,16v32H104V160ZM40,112H88v32H40Zm64-16V64h48V96Zm64,16h48v32H168Zm48-16H168V64h48ZM88,64V96H40V64ZM40,160H88v32H40Zm176,32H168V160h48v32Z") }

    // star.square: a rounded square with a solid star in it.
    val StarSquare by lazy {
        phosphor(
            "Sidebar.StarSquare",
            "M200,32H56A24,24,0,0,0,32,56V200a24,24,0,0,0,24,24H200a24,24,0,0,0,24-24V56A24,24,0,0,0,200,32Zm8,168a8,8,0,0,1-8,8H56a8,8,0,0,1-8-8V56a8,8,0,0,1,8-8H200a8,8,0,0,1,8,8Z",
            inner = "M234.29,114.85l-45,38.83L203,211.75a16.4,16.4,0,0,1-24.5,17.82L128,198.49,77.47,229.57A16.4,16.4,0,0,1,53,211.75l13.76-58.07-45-38.83A16.46,16.46,0,0,1,31.08,86l59-4.76,22.76-55.08a16.36,16.36,0,0,1,30.27,0l22.75,55.08,59,4.76a16.46,16.46,0,0,1,9.37,28.86Z",
            innerScale = 0.52f,
        )
    }
    val Chevron by lazy { lineIcon("Sidebar.Chevron", "M9,5l7,7l-7,7", stroke = 2.6f) }
    val Tick by lazy { lineIcon("Sidebar.Tick", "M5.5,12.5l4.2,4.2L18.5,7.5", stroke = 3f) }

    // person.crop.circle: a head and shoulders inside a ring.
    val Person by lazy {
        lineIcon("Sidebar.Person", "M12,3.6a8.4,8.4 0 1 1 0,16.8a8.4,8.4 0 1 1 0,-16.8z M12,7.4a2.9,2.9 0 1 1 0,5.8a2.9,2.9 0 1 1 0,-5.8z M6.4,18.2c1.2,-2 3.2,-3 5.6,-3s4.4,1 5.6,3")
    }
}
