/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The idea — the cover itself carried between the mini player and the player — follows
 * the reference design's PlayerDock (github.com/kushagrasinghx/the reference design, GPL-3.0). The path, the lift, the
 * landing and the parallel motion around it are Exhale's.
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.ui.utils.highRes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How the player draws its cover at rest — its slow zoom and drift, the sink and dim of a pause,
 * its corners, the dissolve at its foot — so the travelling cover can leave from exactly that
 * picture and land on exactly it. Filled in place each frame; nothing is allocated per frame.
 */
class CoverLook {
    var scale = 1f

    /** Drift across the picture, as fractions of its width and height. */
    var shiftX = 0f
    var shiftY = 0f

    /** Where the zoom is centred, as a fraction of the height. */
    var pivotY = 0.5f

    /** Black laid over it, 0..1. */
    var dim = 0f
    var cornerDp = 0f

    /** The share of the height, at the foot, that dissolves; and the alpha left at the very foot. */
    var footFade = 0f
    var footFloor = 1f

    fun reset() {
        scale = 1f
        shiftX = 0f
        shiftY = 0f
        pivotY = 0.5f
        dim = 0f
        cornerDp = 0f
        footFade = 0f
        footFloor = 1f
    }
}

/**
 * Opening the player is one object travelling, not a card changing shape.
 *
 * The cover is lifted out of the mini player and carried up into its place in the player — on
 * a gentle curve, coming toward you a little on the way and settling back — while everything else
 * moves *with* it rather than after it: the sheet slides up beneath, the player's controls rise
 * into place a beat behind, the page underneath steps back, and the dock sinks away. Closing runs
 * the same path home, and the mini cover takes the landing with a small give. That is the
 * "parallel" in ColorOS's Parallel Animations: several things moving at once, each on its own
 * curve, all hung off one value.
 *
 * The one value is the player sheet's own progress, which is a spring: a drag, a flick or a tap
 * that reverses the player half-way simply changes where that spring is going, and every part of
 * this follows from wherever it already is. Nothing is a timed animation that has to finish first.
 *
 * The two ends report themselves. The mini player's cover ([reportMini]) is measured only with
 * the player closed, so it never carries the transforms the flight puts on the screen. The
 * player's cover ([reportFull]) is measured against the player itself ([fullSpace]) — a frame
 * of reference that moves and scales with it — so it is known from the first frame of the very
 * first open, before the player has ever been at rest. While the cover is in flight both real
 * covers stand aside ([inFlight]) and [CoverFlightOverlay] draws the travelling one over
 * everything.
 */
@Stable
class CoverFlight {
    /** The player sheet's progress: 0 closed on the mini player, 1 open. Set by the host. */
    var progress: () -> Float = { 0f }

    /** Whether the player on screen takes part. Styles without a full-bleed cover keep their morph. */
    var enabled by mutableStateOf(false)

    private class MiniEnd(val owner: Any, val rect: Rect, val corner: Dp, val visible: () -> Boolean) {
        /** Gone from the screen, but still the best idea of where the cover lives. */
        var released = false
    }

    // Every place a mini cover has been seen at rest, by slot: the sheet's pill, the dock's open
    // pill, the dock's folded row. Kept per slot rather than "the latest" — the latest was often a
    // pill nobody could see (the dock measures its own while the sheet's shows), or the shape the
    // dock was in when the player opened rather than the one it is in when it closes, and the cover
    // flew home to the wrong place. A close aims at the slot on screen now.
    private val minis = LinkedHashMap<Any, MiniEnd>()

    private var space: LayoutCoordinates? = null
    private var spaceAtRest: Offset? = null

    // The player's cover, relative to [fullBase] — the window position of what it was measured
    // against, or null for "where the overlay sits" (a player not yet seen at rest fills the
    // window, as the overlay does).
    private var fullRect: Rect? = null
    private var fullBase: Offset? = null
    private var fullLook: ((CoverLook) -> Unit)? = null
    private val look = CoverLook()

    /** Scale the mini cover lands at, settling to 1 — the give at the end of a close. */
    internal val landing = Animatable(1f)

    // Where the mini cover was last seen at rest, kept for the life of the process. Coming back to
    // the app builds a new activity, and with the player open the mini cover is never measured at
    // rest before the first close — which without this was a plain slide down, no cover.
    private fun mini(): MiniEnd? =
        minis.values.lastOrNull { it.visible() && !it.released }
            ?: minis.values.lastOrNull { it.visible() }
            ?: minis.values.lastOrNull()
            ?: lastMini

    /** True while the travelling cover stands in for both real ones. */
    fun inFlight(): Boolean {
        // Progress is read first, every time. Callers decide visibility in draw and layout
        // blocks, which re-run only when what they last read changes: returning before reading it
        // — the player not built yet on a cold start, no mini cover measured yet — left those
        // blocks deaf to the sheet moving, and the whole first flight drew nothing.
        val p = progress()
        if (!enabled || mini() == null) return false
        return p > REST && p < 1f - REST
    }

    /**
     * A mini player's cover, as it is laid out. Only measured with the player closed. [slot] names
     * the place it sits (one per place, whatever song is in it), and [visible] says whether that
     * place is the one showing the song right now.
     */
    fun Modifier.reportMini(
        owner: Any,
        corner: Dp,
        slot: Any = owner,
        visible: () -> Boolean = { true },
    ): Modifier = this
        .onGloballyPositioned { coordinates ->
            if (progress() <= REST) {
                minis.remove(slot)
                minis[slot] = MiniEnd(owner, coordinates.windowRect(), corner, visible).also { lastMini = it }
            }
        }
        .graphicsLayer {
            // Stands aside while the travelling cover is its stand-in, and takes the landing.
            alpha = if (inFlight()) 0f else 1f
            val s = landing.value
            scaleX = s
            scaleY = s
        }

    /**
     * A mini cover has left the screen. Forgotten if another is there to aim at; kept otherwise,
     * because the mini player above the dock is taken down while the player is open and only
     * comes back once a close is already under way — too late to be measured at rest — and the
     * close still has to fly to where it was. The next cover measured at rest replaces it.
     */
    fun releaseMini(owner: Any) {
        // Kept, marked: the slot is still where the cover goes until something replaces it.
        minis.values.forEach { if (it.owner === owner) it.released = true }
    }

    /** The player itself: the frame of reference its cover is measured in. */
    fun Modifier.fullSpace(): Modifier = onGloballyPositioned { coordinates ->
        space = coordinates
        if (progress() >= 1f - REST) spaceAtRest = coordinates.localToWindow(Offset.Zero)
    }

    /**
     * The player's cover, as it is laid out, and [look] — how it is drawn on top of that — so the
     * flight can match it. Hidden while the travelling cover stands in for it.
     */
    fun Modifier.reportFull(look: ((CoverLook) -> Unit)? = null): Modifier = this
        .onGloballyPositioned { coordinates ->
            val reference = space?.takeIf { it.isAttached }
            if (reference != null) {
                fullRect = reference.localBoundingBoxOf(coordinates, clipBounds = false)
                fullBase = spaceAtRest
            } else if (progress() >= 1f - REST) {
                fullRect = coordinates.windowRect()
                fullBase = Offset.Zero
            } else {
                return@onGloballyPositioned
            }
            fullLook = look
        }
        .graphicsLayer { if (inFlight()) alpha = 0f }

    /**
     * Where the travelling cover is at [p], inside a box whose top-left sits at [origin] in the
     * window and which is [width] wide.
     */
    internal fun frame(p: Float, origin: Offset, width: Float, density: Float): Frame? {
        val miniEnd = mini() ?: return null
        val from = miniEnd.rect.translate(-origin)
        val to = fullRect?.translate((fullBase ?: origin) - origin)
            // Never laid out yet: the player's cover is the full-width square at the top.
            ?: Rect(0f, 0f, width, width)

        look.reset()
        fullLook?.invoke(look)

        // The cover leads the sheet a little: it is the thing the eye follows, so it gets there
        // first and the rest arrives around it.
        val u = 1f - (1f - p.coerceIn(0f, 1f)).pow(1.3f)

        // Two legs. Up as a square — the cover is a square, and stretching it on the way reads
        // as a picture being distorted — then, only at the end, out to the player's own shape
        // (the full-bleed banner) if that is not square.
        val side = min(to.width, to.height)
        val square = Rect(Offset(to.center.x - side / 2f, to.top), Size(side, side))
        val k = (u / SQUARE_LEG).coerceIn(0f, 1f)
        // A curved path: across a little sooner than up. From the mini player at the bottom
        // left to the top centre, the cover swings out and rises rather than sliding on a ruler.
        val kx = 1f - (1f - k) * (1f - k)
        val left = lerp(from.left, square.left, kx)
        val top = lerp(from.top, square.top, k)
        val size = lerp(from.width, square.width, k)
        var rect = Rect(left, top, left + size, top + size)
        val m = ((u - SQUARE_LEG) / (1f - SQUARE_LEG)).coerceIn(0f, 1f)
        val toFull = m * m * (3f - 2f * m)
        if (toFull > 0f) {
            rect = Rect(
                lerp(rect.left, to.left, toFull),
                lerp(rect.top, to.top, toFull),
                lerp(rect.right, to.right, toFull),
                lerp(rect.bottom, to.bottom, toFull),
            )
        }

        // Corners grow with the card — a big card with a 10dp corner looks like a cut-out — and
        // relax to the player's own at the end.
        val midCorner = MID_CORNER_DP * density
        val corner = if (m <= 0f) {
            lerp(miniEnd.corner.value * density, midCorner, min(1f, k * 1.6f))
        } else {
            lerp(midCorner, look.cornerDp * density, toFull)
        }
        // Toward the viewer on the way, back down on arrival.
        val lift = sin(PI.toFloat() * k)
        return Frame(rect, corner, lift, toFull, look)
    }

    internal class Frame(
        val rect: Rect,
        val cornerPx: Float,
        val lift: Float,
        /** How far into the last leg, where the cover takes on the player's own look. */
        val toFull: Float,
        val look: CoverLook,
    )

    private fun LayoutCoordinates.windowRect(): Rect {
        val topLeft = localToWindow(Offset.Zero)
        return Rect(topLeft, Size(size.width.toFloat(), size.height.toFloat()))
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        var lastMini: MiniEnd? = null

        /** Progress within this of an end counts as at rest. */
        const val REST = 0.0005f

        /** How much of the trip is spent as a square before opening out to the banner. */
        const val SQUARE_LEG = 0.82f

        /** The corner at the widest point of the flight. */
        const val MID_CORNER_DP = 22f
    }
}

val LocalCoverFlight = staticCompositionLocalOf<CoverFlight?> { null }

/**
 * The travelling cover, drawn over everything while the player opens or closes. Laid out each
 * frame at the flight's rectangle — layout, not a scale, so the square cover is cropped into the
 * banner's shape rather than stretched into it — and nothing at all while the player is at rest.
 */
@Composable
fun CoverFlightOverlay(flight: CoverFlight, modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val url = metadata?.thumbnailUrl ?: return
    val context = LocalContext.current
    // The player's own request — the same data and size — so the flight and the player share one
    // decoded bitmap, with the mini player's thumbnail standing in until that is in memory.
    val request = remember(context, url) { com.ozyern.exhale.ui.component.playerArtworkRequest(context, url) }

    // The landing: a close carries the cover's shrinking into the mini cover, which gives a
    // little under it and springs back — momentum, not a bounce added on top.
    LaunchedEffect(flight) {
        var wasAway = false
        snapshotFlow { flight.progress() <= 0.0005f }
            .distinctUntilChanged()
            .collect { home ->
                if (!home) {
                    wasAway = true
                    flight.landing.snapTo(1f)
                } else if (wasAway && flight.enabled) {
                    wasAway = false
                    flight.landing.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                        initialVelocity = -1.3f,
                    )
                }
            }
    }

    // The player's full-size cover, fetched as soon as the song changes rather than when the
    // player opens: fetched then, the flight landed on a grey placeholder that sat there for most
    // of a second before the cover arrived. A moment after the song starts, so it never competes
    // with the song's own stream for the network, and only while the app is on screen.
    // Before the player has ever been opened too: the first open of the session is the one most
    // likely to find nothing in memory.
    LaunchedEffect(request) {
        kotlinx.coroutines.delay(700)
        com.ozyern.exhale.utils.awaitAppVisible()
        context.imageLoader.enqueue(request)
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    // Worked out in layout, used again in draw for the same frame.
    val current = remember { arrayOfNulls<CoverFlight.Frame>(1) }
    // Only there while the player is away from the mini player: a full-size cover decoded for
    // every song that plays, with the player never opened, would be work for nothing.
    val away by remember { derivedStateOf { flight.enabled && flight.progress() > 0.0005f } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.localToWindow(Offset.Zero) },
    ) {
        // Always composed: building it on the first frame of a close was one more thing in a frame that
        // already ran long. It is the image the prefetch above loads, so this decodes nothing extra.
        // The mini player's own thumbnail underneath, so there is always a picture in flight: a
        // full-size cover not yet in memory left the flight empty — a plain sheet sliding up — and
        // the cover popped in once the player had already arrived.
        val thumb = com.ozyern.exhale.ui.component.rememberPinnedArtworkRequest(url)
        // Built with the song, not with the player: created on the player's first open, the images
        // were still loading for the whole of that first flight, and it flew nothing.
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val frame = if (flight.inFlight()) {
                        flight.frame(flight.progress(), origin, constraints.maxWidth.toFloat(), density)
                    } else {
                        null
                    }
                    current[0] = frame
                    if (frame == null) {
                        val placeable = measurable.measure(Constraints.fixed(1, 1))
                        return@layout layout(0, 0) { placeable.place(-10_000, -10_000) }
                    }
                    val w = max(1, frame.rect.width.roundToInt())
                    val h = max(1, frame.rect.height.roundToInt())
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(frame.rect.left.roundToInt(), frame.rect.top.roundToInt())
                    }
                }
                .graphicsLayer {
                    if (!flight.inFlight()) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    // Read again so a change of progress redraws even when layout settles first.
                    flight.progress()
                    val frame = current[0] ?: return@graphicsLayer
                    val look = frame.look
                    val w = frame.toFull
                    alpha = 1f
                    // Never negative, whatever the look it is easing into says: that throws mid-draw.
                    shape = RoundedCornerShape(frame.cornerPx.coerceAtLeast(0f))
                    clip = true
                    // Depth on the way — a little bigger, a soft shadow at the top of the arc —
                    // and, on the last leg, the player's own zoom and drift, so the hand-over is
                    // the same picture in the same place.
                    val s = (1f + LIFT_SCALE * frame.lift) * (1f + (look.scale - 1f) * w)
                    scaleX = s
                    scaleY = s
                    translationX = look.shiftX * size.width * w
                    translationY = look.shiftY * size.height * w
                    transformOrigin = TransformOrigin(0.5f, 0.5f + (look.pivotY - 0.5f) * w)
                    shadowElevation = LIFT_SHADOW_DP * density * frame.lift
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                    // The foot's dissolve needs its own layer to cut into; only while it shows.
                    compositingStrategy = if (look.footFade > 0f && w > 0f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithContent {
                    drawContent()
                    val frame = current[0] ?: return@drawWithContent
                    val look = frame.look
                    val w = frame.toFull
                    if (w <= 0f) return@drawWithContent
                    if (look.dim > 0f) drawRect(Color.Black.copy(alpha = look.dim * w))
                    if (look.footFade > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black,
                                    Color.Black.copy(alpha = 1f + (look.footFloor - 1f) * w),
                                ),
                                startY = size.height * (1f - look.footFade),
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val LIFT_SCALE = 0.035f
private const val LIFT_SHADOW_DP = 22f
