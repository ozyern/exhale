/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.ozyern.exhale.desktop.player.DesktopPlayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem

/** Seconds for each equalizer bar to rise and fall. */
private val EqualizerPeriods = floatArrayOf(1.04f, 0.76f, 1.22f)

/**
 * YouTube's stand-in for a playlist with no cover of its own (a dark square with a stray mark on it).
 * Drawn as the Music app draws a coverless playlist instead: a grey tile with a note.
 */
fun String.isPlaceholderArtwork(): Boolean = "playlist-empty-state" in this

/** YouTube serves thumbnails at whatever size the URL asks for; the feed asks for tiny ones. */
fun String.resizedThumbnail(px: Int): String =
    replace(Regex("=w\\d+-h\\d+"), "=w$px-h$px").replace(Regex("=s\\d+(?=[-&]|$)"), "=s$px")

fun YTItem.subtitle(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> listOfNotNull(artists?.joinToString { it.name }, year?.toString()).joinToString(" · ")
    is PlaylistItem -> listOfNotNull(author?.name, songCountText).joinToString(" · ")
    is ArtistItem -> subscriberCountText ?: monthlyListenerCountText ?: "Artist"
}.takeIf { !it.isNullOrBlank() }

/** Press squish + hover lift shared by every card, as springs so an interrupted press never snaps. */
@Composable
private fun rememberCardMotion(interaction: MutableInteractionSource): Pair<Float, Boolean> {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.02f)
    return scale to hovered
}

/** The shelf card: square artwork (round for artists), title, subtitle. */
@Composable
fun ItemCard(
    item: YTItem,
    isActive: Boolean,
    onClick: () -> Unit,
    artSize: Dp? = null,
    onPlay: (() -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val cardSize = artSize ?: if (settings.gridSize == "SMALL") 136.dp else 168.dp
    val interaction = remember { MutableInteractionSource() }
    val (scale, hovered) = rememberCardMotion(interaction)
    val artShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(settings.artworkCorner.dp)
    // This card's place in the composition: the same every time its page is shown, so the cover
    // can fly out to an album and back into exactly this card.
    val sharedKey = "art:${currentCompositeKeyHash.toString(36)}"
    val flies = item is AlbumItem || item is PlaylistItem

    // Where the pointer is over the artwork (0..1 each way): the cover tilts toward it and catches
    // a highlight there, like a card held under a light. A mouse is the one thing a PC has that a
    // phone doesn't, so this is desktop-only motion.
    val tiltOn = settings.cardTilt && !settings.reducedMotion && !Bench.off("tilt")
    // Pointer, tilt and shine live outside composition: a mouse moving over a card only redraws it,
    // where three animated states used to recompose the card on every pointer event.
    val pointer = remember { mutableStateOf<Offset?>(null) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val motion = rememberCoroutineScope()
    // Songs and artists fetch artwork sized to the card; albums and playlists keep the larger size the
    // page they open shows first, so the cover in flight is already in memory.
    val artPx = if (flies) 544 else thumbnailSizeFor(cardSize.value * LocalDensity.current.density)

    Column(
        modifier = Modifier
            .width(cardSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                if (flies) SharedArt.claim(sharedKey)
                onClick()
            },
    ) {
        Box(
            Modifier
                .sharedArtwork(if (flies) sharedKey else null, artShape)
                .size(cardSize)
                .pointerInput(tiltOn) {
                    if (!tiltOn) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: continue
                            val at = if (event.type == PointerEventType.Exit) {
                                null
                            } else {
                                Offset((position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
                            }
                            val entering = (pointer.value == null) != (at == null)
                            pointer.value = at
                            motion.launch { tiltX.animateTo(at?.let { (0.5f - it.y) * 14f } ?: 0f, CardTiltSpring) }
                            motion.launch { tiltY.animateTo(at?.let { (it.x - 0.5f) * 14f } ?: 0f, CardTiltSpring) }
                            if (entering) motion.launch { shine.animateTo(if (at != null) 1f else 0f, tween(220)) }
                        }
                    }
                }
                .graphicsLayer {
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    cameraDistance = 14f * density
                },
        ) {
            if (item.thumbnail?.isPlaceholderArtwork() == true) {
                Box(Modifier.matchParentSize().clip(artShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(NavIcons.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.34f))
                }
            }
            AsyncImage(
                model = item.thumbnail?.takeUnless { it.isPlaceholderArtwork() }?.resizedThumbnail(artPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // A shadow only under the pointer: every card in a shelf casting one at rest was a
                    // large share of what scrolling a page cost.
                    .then(if (Bench.off("shadow")) Modifier else Modifier.shadow(if (hovered) 18.dp else 0.dp, artShape, clip = false))
                    .clip(artShape)
                    // No ring round the playing card: the Music app marks it with the disc alone.
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (tiltOn) {
                Box(
                    Modifier.matchParentSize().clip(artShape).drawBehind {
                        val glow = shine.value
                        if (glow <= 0.01f) return@drawBehind
                        val at = pointer.value ?: Offset(0.5f, 0.3f)
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.24f * glow), Color.Transparent),
                                center = Offset(at.x * size.width, at.y * size.height),
                                radius = size.maxDimension * 0.8f,
                            ),
                        )
                    },
                )
            }
            // An artist is a profile, not something to play: no disc, so no corner of the photo
            // starts music. On an album or playlist the card opens its page and the disc plays it.
            if ((hovered || isActive) && item !is ArtistItem) {
                val play = onPlay.takeIf { item !is SongItem }
                PlayDisc(
                    playing = isActive,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .then(if (play != null) Modifier.clip(CircleShape).clickable(onClick = play) else Modifier),
                )
            }
        }
        // The Music app's caption under artwork: the title in medium white, the subtitle in plain grey —
        // never bold, never the accent.
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle()?.let {
            Text(
                it,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A radio station built on an artist. Square and washed in the artist's colour with a RADIO tag —
 * never the round photo, so it can't pass for the artist's profile. Pressing it plays the station.
 */
@Composable
fun StationCard(title: String, subtitle: String?, image: String?, onClick: () -> Unit, artSize: Dp? = null) {
    val settings = LocalSettings.current
    val cardSize = artSize ?: if (settings.gridSize == "SMALL") 136.dp else 168.dp
    val interaction = remember { MutableInteractionSource() }
    val (scale, hovered) = rememberCardMotion(interaction)
    val shape = RoundedCornerShape(settings.artworkCorner.dp)
    val colors by rememberArtworkColors(image?.resizedThumbnail(120))
    val tint = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "station_lift")

    Column(
        modifier = Modifier
            .width(cardSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(cardSize)
                .shadow(if (hovered) 18.dp else 0.dp, shape, clip = false)
                .clip(shape)
                .background(Brush.linearGradient(listOf(tint, Color(0xFF0E0B12)))),
        ) {
            AsyncImage(
                model = image?.resizedThumbnail(360),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.58f)
                    .graphicsLayer {
                        val grow = 1f + 0.06f * lift
                        scaleX = grow
                        scaleY = grow
                    }
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape),
            )
            Text(
                "RADIO",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14.em),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (hovered) PlayDisc(playing = false, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.95f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(it, fontSize = 13.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The Quick picks hero: tall cover with the title set over a scrim, like the Android carousel. */
@Composable
fun HeroCard(song: SongItem, isActive: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val (scale, hovered) = rememberCardMotion(interaction)
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = Modifier
            .width(250.dp)
            .height(290.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (hovered) 24.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = song.thumbnail.resizedThumbnail(720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )
        if (isActive || hovered) {
            PlayDisc(playing = isActive, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlayDisc(playing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(10.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            EqualizerBars(MaterialTheme.colorScheme.onPrimary, Modifier.size(16.dp))
        } else {
            Icon(TransportIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** The card tilt's spring: a little give, no wobble. */
private val CardTiltSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 260f)

/** Artwork is fetched at the smallest of these that covers the card's pixels. */
internal fun thumbnailSizeFor(px: Float): Int = when {
    px <= 226f -> 226
    px <= 360f -> 360
    px <= 544f -> 544
    else -> 720
}

/**
 * Three bars bouncing out of step — "this is the one playing". One clock for all three, stepping 30
 * times a second: three infinite animations here asked for a new frame of the whole window at the
 * display's full rate for as long as a playing song was anywhere on screen.
 */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalSettings.current.reducedMotion
    val clock = remember { mutableFloatStateOf(0.6f) }
    val focused by androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused)
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            clock.floatValue = (System.nanoTime() - start) / 1_000_000_000f
            delay(if (focused) 33 else UnfocusedTickMs)
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val t = clock.floatValue
        val barWidth = size.width / 5f
        EqualizerPeriods.forEachIndexed { i, period ->
            // Up and back down over [period] seconds, eased like the tweens this replaced.
            val phase = ((t / period) + i * 0.37f) % 1f
            val triangle = if (phase < 0.5f) phase * 2f else 2f - phase * 2f
            val barHeight = size.height * (0.25f + 0.75f * FastOutSlowInEasing.transform(triangle))
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(barWidth * (i * 2f), size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** A song row for lists (search results). The current song sits on a glass plate. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SongRow(song: SongItem, isActive: Boolean, onClick: () -> Unit, player: DesktopPlayer? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isActive -> Modifier.glassPlate(shape, base = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    hovered -> Modifier.glassPlate(shape)
                    else -> Modifier.clip(shape)
                },
            )
            .hoverable(interaction)
            // Right-click is how a song's actions have always opened; the ⋯ is for finding out they exist.
            .then(
                if (player == null) {
                    Modifier
                } else {
                    Modifier.onPointerEvent(PointerEventType.Press) { event ->
                        if (event.buttons.isSecondaryPressed) menuOpen = true
                    }
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail.resizedThumbnail(226),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                song.artists.joinToString { it.name },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        song.album?.let {
            Text(
                it.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.6f).padding(horizontal = 16.dp),
            )
        }
        song.duration?.let {
            Text(formatTime(it.toDouble()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (player != null) {
            Box {
                PressableIcon(onClick = { menuOpen = true }, size = 34.dp) {
                    Icon(
                        NavIcons.More,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hovered || menuOpen) 1f else 0f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                SongActionsMenu(song, player, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
    }
}

fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
