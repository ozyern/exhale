/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * The ⋯ menu as the Android app draws it (OverlayMenu + the iOS 27 menu panel), sized for a pointer:
 * a dark frosted panel that grows out of the ⋯ it was opened from, the item at its head with a heart,
 * a strip of quick actions sitting bare on the panel, then groups of rows split by hairline bands, each
 * row a symbol, a title and, where Android has one, a line saying what it does.
 *
 * Phone → desktop: 270 → 250 dp wide, corners 32 → 22, 17 → 13 sp titles, 44 → ~30 dp rows.
 */
object OptionsMenuStyle {
    /** The Android menu's dark panel fill (IosMenuMaterial.DarkPanelFill). */
    val PanelFill = Color(0xFF1C1C1E)
    val Label = Color(0xFFF8F8FA)
    val SecondaryLabel = Color.White.copy(alpha = 0.68f)
    val Separator = Color.White.copy(alpha = 0.06f)
    val Highlight = Color.White.copy(alpha = 0.09f)
    val Width = 250.dp
    val Shape = RoundedCornerShape(22.dp)
}

/**
 * The panel. Place it inside the Box that holds the ⋯ (as a DropdownMenu would be): it lines its right
 * edge up with that Box's, opens below it when there is room and above it otherwise, and grows from the
 * ⋯ on the Android menu's springs — width leading, height a little softer. Escape or a click outside
 * closes it.
 */
@Composable
fun OptionsMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scaleX = remember { Animatable(0.12f) }
    val scaleY = remember { Animatable(0.12f) }
    val alpha = remember { Animatable(0f) }
    // Stays composed until the close has played, then drops out.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            shown = true
            scaleX.snapTo(0.12f); scaleY.snapTo(0.12f); alpha.snapTo(0f)
            launch { alpha.animateTo(1f, tween(90)) }
            launch { scaleX.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 700f)) }
            scaleY.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
        } else if (shown) {
            launch { scaleX.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
            launch { scaleY.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
            alpha.animateTo(0f, tween(140, delayMillis = 40))
            shown = false
        }
    }
    if (!shown) return

    var origin by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val position = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val margin = 8
                val gap = 6
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val fitsBelow = anchorBounds.bottom + gap + popupContentSize.height <= windowSize.height - margin
                val y = (if (fitsBelow) anchorBounds.bottom + gap else anchorBounds.top - gap - popupContentSize.height)
                    .coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
                if (popupContentSize.width > 0 && popupContentSize.height > 0) {
                    val ox = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
                    val oy = if (fitsBelow) 0f else 1f
                    if (origin.pivotFractionX != ox || origin.pivotFractionY != oy) origin = TransformOrigin(ox, oy)
                }
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .padding(18.dp) // room for the shadow inside the popup's bounds
                .graphicsLayer {
                    transformOrigin = origin
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                    this.alpha = alpha.value
                }
                .width(OptionsMenuStyle.Width)
                .heightIn(max = 640.dp)
                .shadow(28.dp, OptionsMenuStyle.Shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(OptionsMenuStyle.Shape)
                .background(OptionsMenuStyle.PanelFill.copy(alpha = 0.96f))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), OptionsMenuStyle.Shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            content = content,
        )
    }
}

/** The item the menu is for: its cover, title, a line under it, and the heart on the right. */
@Composable
fun OptionsHeader(
    image: String?,
    title: String,
    subtitle: String?,
    roundImage: Boolean = false,
    liked: Boolean? = null,
    onLike: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (roundImage) CircleShape else RoundedCornerShape(6.dp)
        Box(Modifier.size(40.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))) {
            AsyncImage(image?.resizedThumbnail(120), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OptionsMenuStyle.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.5.sp, color = OptionsMenuStyle.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (liked != null) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (hovered) OptionsMenuStyle.Highlight else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onLike)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (liked) OptionsIcons.HeartFill else OptionsIcons.Heart,
                    contentDescription = if (liked) "Remove from Favorite Songs" else "Add to Favorite Songs",
                    // Android tints the filled heart with the theme's error red.
                    tint = if (liked) Color(0xFFFF453A) else OptionsMenuStyle.Label,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
    OptionsSeparator(Modifier.padding(bottom = 4.dp))
}

/** One of the quick actions across the top: a symbol over a short label. */
class OptionsAction(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** The quick actions, bare on the panel as Android's menu panel lays them, three across. */
@Composable
fun OptionsQuickActions(actions: List<OptionsAction>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hovered && action.enabled) OptionsMenuStyle.Highlight else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, enabled = action.enabled, onClick = action.onClick)
                    .graphicsLayer { alpha = if (action.enabled) 1f else 0.4f }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = OptionsMenuStyle.Label, modifier = Modifier.size(20.dp))
                Text(
                    action.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OptionsMenuStyle.Label,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One row of a group: a symbol, the title, and what it does underneath when Android says so. */
class OptionsItem(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A group of rows: inside the panel Android draws no card, only a hairline band above the group
 * (FlatMenuGroup), and the rows straight on the panel.
 */
@Composable
fun OptionsGroup(items: List<OptionsItem>) {
    if (items.isEmpty()) return
    OptionsSeparator(Modifier.padding(vertical = 4.dp))
    items.forEach { OptionsRow(it) }
}

@Composable
private fun OptionsRow(item: OptionsItem) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = if (item.destructive) Color(0xFFFF453A) else OptionsMenuStyle.Label
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) OptionsMenuStyle.Highlight else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = item.onClick)
            .heightIn(min = 30.dp)
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
            Icon(item.icon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.title, fontSize = 13.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.description?.let {
                Text(it, fontSize = 11.sp, color = OptionsMenuStyle.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A free-standing row for any menu in the app, so every popup reads as the Android ⋯ menu: an
 * optional symbol, the title, an optional line under it, and a tick or trailing content on the right.
 * Without an icon the title still lines up with the iconed rows.
 */
@Composable
fun OptionsMenuRow(
    title: String,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean = false,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = if (destructive) Color(0xFFFF453A) else OptionsMenuStyle.Label
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered && enabled) OptionsMenuStyle.Highlight else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .heightIn(min = 30.dp)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 13.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            description?.let {
                Text(it, fontSize = 11.sp, color = OptionsMenuStyle.SecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            trailing != null -> Box(Modifier.padding(start = 8.dp)) { trailing() }
            checked -> Icon(OptionsIcons.Tick, contentDescription = "Selected", tint = ink, modifier = Modifier.padding(start = 8.dp).size(15.dp))
        }
    }
}

/** A row whose title is drawn by the caller (a font preview, say), with the same hover wash and tick. */
@Composable
fun OptionsMenuCustomRow(checked: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) OptionsMenuStyle.Highlight else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { content() }
        if (checked) Icon(OptionsIcons.Tick, contentDescription = "Selected", tint = OptionsMenuStyle.Label, modifier = Modifier.padding(start = 8.dp).size(15.dp))
    }
}

/** A small heading at the top of a menu ("Name style for …"). */
@Composable
fun OptionsMenuTitle(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = OptionsMenuStyle.SecondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** The hairline band between groups of rows. */
@Composable
fun OptionsDivider() = OptionsSeparator(Modifier.padding(vertical = 4.dp))

@Composable
private fun OptionsSeparator(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 8.dp).fillMaxWidth().height(1.dp).background(OptionsMenuStyle.Separator))
}

/** The menu's own symbols, drawn to sit with the app's SF-style line icons. */
object OptionsIcons {
    private fun line(name: String, path: String, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    private const val HeartPath =
        "M12,20.3C11.7,20.3 11.4,20.2 11.2,20C6.3,15.9 3,12.9 3,9.2C3,6.4 5.2,4.2 7.9,4.2C9.6,4.2 11.1,5 12,6.3C12.9,5 14.4,4.2 16.1,4.2C18.8,4.2 21,6.4 21,9.2C21,12.9 17.7,15.9 12.8,20C12.6,20.2 12.3,20.3 12,20.3Z"

    val Heart: ImageVector by lazy { line("Options.Heart", HeartPath) }
    val HeartFill: ImageVector by lazy {
        ImageVector.Builder(name = "Options.HeartFill", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(HeartPath), fill = SolidColor(Color.Black))
            .build()
    }

    /** text.line.first.and.arrowtriangle.forward: Play Next. */
    val PlayNext: ImageVector by lazy { line("Options.PlayNext", "M9,6H20M9,12H20M9,18H20M3.5,4.5L6.5,6L3.5,7.5Z") }

    /** text.badge.plus: Add (to the end of the queue, desktop's stand-in for Add to playlist). */
    val Add: ImageVector by lazy { line("Options.Add", "M4,6H15M4,11H15M4,16H10M17.5,13V21M13.5,17H21.5") }

    /** square.and.arrow.up: Share. */
    val Share: ImageVector by lazy { line("Options.Share", "M12,3.5V14.5M8.2,7.2L12,3.5L15.8,7.2M8.5,10H6.5A1.5,1.5 0 0 0 5,11.5V19A1.5,1.5 0 0 0 6.5,20.5H17.5A1.5,1.5 0 0 0 19,19V11.5A1.5,1.5 0 0 0 17.5,10H15.5") }

    /** dot.radiowaves.left.and.right: Start radio. */
    val Radio: ImageVector by lazy { line("Options.Radio", "M12,11.2A0.8,0.8 0 1 1 12,12.8A0.8,0.8 0 1 1 12,11.2ZM8.5,8.5A5,5 0 0 0 8.5,15.5M15.5,8.5A5,5 0 0 1 15.5,15.5M5.6,5.6A9,9 0 0 0 5.6,18.4M18.4,5.6A9,9 0 0 1 18.4,18.4") }

    /** music.note.list: Add to queue. */
    val Queue: ImageVector by lazy { line("Options.Queue", "M3.5,6H13.5M3.5,11H13.5M3.5,16H9M17,17.5V5.5L21,4.5M17,17.5A2,2 0 1 1 13,17.5A2,2 0 1 1 17,17.5Z") }

    /** pin: Pin / Unpin. */
    val Pin: ImageVector by lazy { line("Options.Pin", "M9,4H15M10,4L9,12L6,15H18L15,12L14,4M12,15V21") }

    /** music.mic: View artist. */
    val Artist: ImageVector by lazy { line("Options.Artist", "M12,12A3.5,3.5 0 1 0 12,5A3.5,3.5 0 1 0 12,12ZM5,20C5.8,16.6 8.6,14.5 12,14.5C15.4,14.5 18.2,16.6 19,20") }

    /** square.stack: View album. */
    val Album: ImageVector by lazy { line("Options.Album", "M6,7.5H18A1.5,1.5 0 0 1 19.5,9V19A1.5,1.5 0 0 1 18,20.5H6A1.5,1.5 0 0 1 4.5,19V9A1.5,1.5 0 0 1 6,7.5ZM7,4.5H17M12,12V16.5M12,16.5A1.4,1.4 0 1 1 10.6,15.1") }

    /** link: Copy link. */
    val Link: ImageVector by lazy { line("Options.Link", "M10,14L14,10M8.5,11.5L6.5,13.5A3.5,3.5 0 0 0 11.5,18.5L13.5,16.5M15.5,12.5L17.5,10.5A3.5,3.5 0 0 0 12.5,5.5L10.5,7.5") }

    /** checkmark: the chosen row. */
    val Tick: ImageVector by lazy { line("Options.Tick", "M5,12.5L10,17.5L19,7", stroke = 2.2f) }

    /** sidebar.left: Edit / Hide Sidebar. */
    val Sidebar: ImageVector by lazy { line("Options.Sidebar", "M5,4.5H19A1.5,1.5 0 0 1 20.5,6V18A1.5,1.5 0 0 1 19,19.5H5A1.5,1.5 0 0 1 3.5,18V6A1.5,1.5 0 0 1 5,4.5ZM9.5,4.5V19.5") }

    /** pencil: Edit. */
    val Edit: ImageVector by lazy { line("Options.Edit", "M4.5,19.5L5.3,15.6L15.8,5.1A1.9,1.9 0 0 1 18.9,8.2L8.4,18.7Z M14,6.9L17.1,10") }

    /** eye.slash: Hide. */
    val Hide: ImageVector by lazy { line("Options.Hide", "M3,12C5,8 8.2,6 12,6C15.8,6 19,8 21,12C19,16 15.8,18 12,18C8.2,18 5,16 3,12ZM12,9.5A2.5,2.5 0 1 1 12,14.5A2.5,2.5 0 1 1 12,9.5ZM4,4L20,20") }

    /** person.crop.circle: Sign in. */
    val Person: ImageVector by lazy { line("Options.Person", "M12,3.5A8.5,8.5 0 1 1 12,20.5A8.5,8.5 0 1 1 12,3.5ZM12,7.5A3,3 0 1 1 12,13.5A3,3 0 1 1 12,7.5ZM6.5,17.8C7.8,16 9.7,15 12,15C14.3,15 16.2,16 17.5,17.8") }

    /** speaker.wave.2: an audio output. */
    val Speaker: ImageVector by lazy { line("Options.Speaker", "M4,9.5H7L11.5,5.5V18.5L7,14.5H4ZM15,9A4,4 0 0 1 15,15M17.5,6.5A7.5,7.5 0 0 1 17.5,17.5") }

    /** iphone: the paired phone. */
    val Phone: ImageVector by lazy { line("Options.Phone", "M8.5,2.5H15.5A1.5,1.5 0 0 1 17,4V20A1.5,1.5 0 0 1 15.5,21.5H8.5A1.5,1.5 0 0 1 7,20V4A1.5,1.5 0 0 1 8.5,2.5ZM10.5,18.5H13.5") }

    /** textformat: a type style. */
    val TypeStyle: ImageVector by lazy { line("Options.TypeStyle", "M4,19L9,5L14,19M5.8,14H12.2M15.5,12.5H20.5M18,10V19") }

    /** arrow.uturn.backward: back to the default. */
    val Reset: ImageVector by lazy { line("Options.Reset", "M9,7L5,11L9,15M5,11H14.5A4.5,4.5 0 0 1 14.5,20H11") }

    /** play.fill. */
    val Play: ImageVector by lazy { line("Options.Play", "M8,5.5V18.5L18.5,12Z") }

    /** shuffle. */
    val Shuffle: ImageVector by lazy { line("Options.Shuffle", "M3.5,7H7C11,7 13,17 17,17H20.5M3.5,17H7C8.6,17 9.8,15.4 10.8,13.5M13.2,10.5C14.2,8.6 15.4,7 17,7H20.5M18.5,5L20.5,7L18.5,9M18.5,15L20.5,17L18.5,19") }
}
