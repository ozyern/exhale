/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material path data as an icon, so we get the extended set's glyphs without its 30 MB jar. */
private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

/**
 * The same, with holes: subpaths inside another cut through it rather than filling over it — a record's
 * label, the ring of a magnifier. The dock's glyphs are solid shapes, so their detail has to be cut out.
 */
private fun holedIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
        .build()

/** One piece of a dock glyph: a filled shape (with an alpha, for the record stack's depth) or a line. */
private class Stroke(val pathData: String, val width: Float = 0f, val fillAlpha: Float = 1f, val evenOdd: Boolean = false)

/**
 * The Android dock's own drawables, line for line: each tab has a resting glyph drawn as a line and
 * a selected one that is solid or heavier, so the tab under the lens changes weight, not just colour.
 */
private fun dockIcon(name: String, viewport: Float, vararg parts: Stroke, shiftX: Float = 0f, shiftY: Float = 0f): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = viewport, viewportHeight = viewport)
        .addGroup(translationX = shiftX, translationY = shiftY)
    for (part in parts) {
        val nodes = addPathNodes(part.pathData)
        if (part.width > 0f) {
            builder.addPath(
                pathData = nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = part.width,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        } else {
            builder.addPath(
                pathData = nodes,
                fill = SolidColor(Color.Black),
                fillAlpha = part.fillAlpha,
                pathFillType = if (part.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
    }
    return builder.clearGroup().build()
}

private const val HOUSE = "M4.4,10.9 L12,4.1 L19.6,10.9 V18.1 A2.4,2.4 0 0 1 17.2,20.5 H6.8 A2.4,2.4 0 0 1 4.4,18.1 Z"
private const val HOUSE_WITH_DOOR = "$HOUSE M9.4,20.5 V15.4 A1.3,1.3 0 0 1 10.7,14.1 H13.3 A1.3,1.3 0 0 1 14.6,15.4 V20.5 Z"
private const val STACK_BACK = "M37.3076,2.402C37.2092,0.8448 36.3236,0 34.6671,0L19.9721,0C18.3156,0 17.43,0.8448 17.3316,2.402L37.3076,2.402Z"
private const val STACK_MIDDLE = "M40.7846,8.0341C40.5386,6.3942 39.7349,5.4334 37.9473,5.4334L16.5443,5.4334C14.7566,5.4334 13.953,6.3942 13.707,8.0341L40.7846,8.0341Z"
private const val NOTE =
    "M20.2673,41.9763C18.4304,41.9763 17.1348,40.7671 17.1348,39.0609C17.1348,37.3215 18.2172,36.1951 20.3657,35.7644L22.055,35.4165" +
        "C22.465,35.3337 22.6618,35.0686 22.6618,34.5717L22.6126,22.2637C22.6126,21.6011 23.039,21.187 23.777,21.0213L33.9947,18.8844" +
        "C35.2411,18.6193 35.766,18.9838 35.766,20.0605L35.766,35.2508C35.766,38.4645 33.3387,39.5909 31.7642,39.5909" +
        "C29.9109,39.5909 28.566,38.3651 28.566,36.6755C28.566,34.9858 29.6649,33.9422 31.8626,33.4452L33.4699,33.0974" +
        "C33.9455,32.998 34.1915,32.6667 34.1915,32.1863L34.1423,25.3283C34.1423,24.9141 33.9455,24.8147 33.4535,24.9141L24.8103,26.7197" +
        "C24.3675,26.8191 24.3183,26.8854 24.3183,27.3824L24.3183,37.5865C24.3183,40.7671 21.8418,41.9763 20.2673,41.9763Z"
private const val WAVES_NEAR_L = "M7.86,8.53 A5.4,5.4 0 0 0 7.86,15.47"
private const val WAVES_NEAR_R = "M16.14,8.53 A5.4,5.4 0 0 1 16.14,15.47"
private const val WAVES_FAR_L = "M4.8,5.96 A9.4,9.4 0 0 0 4.8,18.04"
private const val WAVES_FAR_R = "M19.2,5.96 A9.4,9.4 0 0 1 19.2,18.04"

object DockIcons {
    val Home by lazy {
        dockIcon(
            "Exhale.Dock.Home", 24f,
            Stroke(HOUSE, 1.9f),
            Stroke("M9.7,20.5 V15.6 A1.3,1.3 0 0 1 11,14.3 H13 A1.3,1.3 0 0 1 14.3,15.6 V20.5", 1.9f),
        )
    }
    val HomeSelected by lazy {
        dockIcon(
            "Exhale.Dock.HomeSelected", 24f,
            Stroke(HOUSE_WITH_DOOR, evenOdd = true),
            Stroke(HOUSE_WITH_DOOR, 1.3f),
        )
    }
    val Explore by lazy {
        dockIcon(
            "Exhale.Dock.Explore", 24f,
            Stroke("M10.1,12a1.9,1.9 0 1,0 3.8,0a1.9,1.9 0 1,0 -3.8,0z"),
            Stroke(WAVES_NEAR_L, 1.9f), Stroke(WAVES_NEAR_R, 1.9f), Stroke(WAVES_FAR_L, 1.9f), Stroke(WAVES_FAR_R, 1.9f),
        )
    }
    val ExploreSelected by lazy {
        dockIcon(
            "Exhale.Dock.ExploreSelected", 24f,
            Stroke("M9.4,12a2.6,2.6 0 1,0 5.2,0a2.6,2.6 0 1,0 -5.2,0z"),
            Stroke(WAVES_NEAR_L, 2.6f), Stroke(WAVES_NEAR_R, 2.6f), Stroke(WAVES_FAR_L, 2.6f), Stroke(WAVES_FAR_R, 2.6f),
        )
    }
    val Library by lazy {
        dockIcon(
            "Exhale.Dock.Library", 56f,
            Stroke(STACK_BACK, fillAlpha = 0.45f),
            Stroke(STACK_MIDDLE, fillAlpha = 0.72f),
            Stroke("M15.4,13.1 L39.6,13.1 A5,5 0 0 1 44.6,18.1 L44.6,42.5 A5,5 0 0 1 39.6,47.5 L15.4,47.5 A5,5 0 0 1 10.4,42.5 L10.4,18.1 A5,5 0 0 1 15.4,13.1 Z", 3f),
            Stroke(NOTE),
            shiftX = 0.5f, shiftY = 3.5f,
        )
    }
    val LibrarySelected by lazy {
        dockIcon(
            "Exhale.Dock.LibrarySelected", 56f,
            Stroke(STACK_BACK, fillAlpha = 0.45f),
            Stroke(STACK_MIDDLE, fillAlpha = 0.72f),
            Stroke(
                "M40.1121,49C43.8515,49 46,46.83 46,42.6058L46,18.0064C46,13.7989 43.8351,11.6288 39.6037,11.6288L15.3799,11.6288" +
                    "C11.1485,11.6288 9,13.7657 9,18.0064L9,42.6058C9,46.8465 11.1485,49 15.3799,49L40.1121,49Z$NOTE",
                evenOdd = true,
            ),
            shiftX = 0.5f, shiftY = 3.5f,
        )
    }
    val Search by lazy {
        dockIcon(
            "Exhale.Dock.Search", 24f,
            Stroke("M10.6,4.4 A6.2,6.2 0 1 1 10.6,16.8 A6.2,6.2 0 1 1 10.6,4.4 Z", 2.1f),
            Stroke("M15.4,15.4 L20,20", 2.3f),
        )
    }
    val SearchSelected by lazy {
        dockIcon(
            "Exhale.Dock.SearchSelected", 24f,
            Stroke("M10.6,4.2 A6.4,6.4 0 1 1 10.6,17 A6.4,6.4 0 1 1 10.6,4.2 Z", 2.9f),
            Stroke("M15.6,15.6 L20.2,20.2", 3.1f),
        )
    }
}

/**
 * The Music app's player symbols, drawn after SF Symbols rather than Material: solid shapes with softly
 * rounded corners for the transport (play.fill, pause.fill, backward.fill, forward.fill), and an even,
 * round-capped line for the rest (shuffle, repeat, quote.bubble, list.bullet, the speaker's waves).
 */
object TransportIcons {
    // repeat: a rounded loop, an arrowhead at each end.
    val Repeat by lazy {
        dockIcon(
            "Exhale.Repeat", 24f,
            Stroke("M4.5,11.6V9.7a2.7,2.7 0 0 1 2.7,-2.7h12 M16.9,4.4l2.6,2.6l-2.6,2.6", 1.8f),
            Stroke("M19.5,12.4v1.9a2.7,2.7 0 0 1 -2.7,2.7h-12 M7.1,19.6l-2.6,-2.6l2.6,-2.6", 1.8f),
        )
    }

    // repeat.1: the same loop with a small 1 in it.
    val RepeatOne by lazy {
        dockIcon(
            "Exhale.RepeatOne", 24f,
            Stroke("M4.5,11.6V9.7a2.7,2.7 0 0 1 2.7,-2.7h12 M16.9,4.4l2.6,2.6l-2.6,2.6", 1.8f),
            Stroke("M19.5,12.4v1.9a2.7,2.7 0 0 1 -2.7,2.7h-12 M7.1,19.6l-2.6,-2.6l2.6,-2.6", 1.8f),
            Stroke("M11.2,10.3l1.5,-1h1v5.8h-1.25v-4.35l-1.25,0.75z"),
        )
    }

    // play.fill: a triangle with its corners rounded off.
    val Play by lazy { materialIcon("Exhale.Play", "M7.2,5.1c0,-1.15 1.25,-1.85 2.24,-1.25l10.6,6.6c0.95,0.6 0.95,1.95 0,2.55l-10.6,6.6c-0.99,0.6 -2.24,-0.1 -2.24,-1.25z") }

    // pause.fill: two rounded bars.
    val Pause by lazy {
        materialIcon(
            "Exhale.Pause",
            "M6.6,4h2.7a1.3,1.3 0 0 1 1.3,1.3v13.4a1.3,1.3 0 0 1 -1.3,1.3H6.6a1.3,1.3 0 0 1 -1.3,-1.3V5.3A1.3,1.3 0 0 1 6.6,4z" +
                "M14.7,4h2.7a1.3,1.3 0 0 1 1.3,1.3v13.4a1.3,1.3 0 0 1 -1.3,1.3h-2.7a1.3,1.3 0 0 1 -1.3,-1.3V5.3A1.3,1.3 0 0 1 14.7,4z",
        )
    }

    /** The Music app's back and forward: two solid wedges each, rounded, no bar. */
    val Backward by lazy {
        materialIcon(
            "Exhale.Backward",
            "M22,7.7c0,-0.95 -1.05,-1.5 -1.8,-0.95l-7.1,4.3c-0.7,0.45 -0.7,1.45 0,1.9l7.1,4.3c0.75,0.55 1.8,0 1.8,-0.95z" +
                "M12,7.7c0,-0.95 -1.05,-1.5 -1.8,-0.95l-7.1,4.3c-0.7,0.45 -0.7,1.45 0,1.9l7.1,4.3c0.75,0.55 1.8,0 1.8,-0.95z",
        )
    }
    val Forward by lazy {
        materialIcon(
            "Exhale.Forward",
            "M2,7.7c0,-0.95 1.05,-1.5 1.8,-0.95l7.1,4.3c0.7,0.45 0.7,1.45 0,1.9l-7.1,4.3C3.05,17.8 2,17.25 2,16.3z" +
                "M12,7.7c0,-0.95 1.05,-1.5 1.8,-0.95l7.1,4.3c0.7,0.45 0.7,1.45 0,1.9l-7.1,4.3c-0.75,0.55 -1.8,0 -1.8,-0.95z",
        )
    }

    // The Music app uses the same wedges for the previous and next track, not the barred skip glyphs.
    val Next by lazy { Forward }
    val Previous by lazy { Backward }

    // airplay.audio: rings opening downward over a solid wedge.
    val AirPlay by lazy {
        dockIcon(
            "Exhale.AirPlay", 24f,
            Stroke("M6.3,16.6a8,8 0 1 1 11.4,0 M8.9,14.1a4.4,4.4 0 1 1 6.2,0", 1.8f),
            Stroke("M12,14.4l4.5,5.4c0.35,0.42 0.05,1.05 -0.5,1.05H8c-0.55,0 -0.85,-0.63 -0.5,-1.05z"),
        )
    }

    /** Ten seconds back and forward, digits and all — the pair every video player has. */
    val Back10 by lazy {
        materialIcon(
            "Exhale.Back10",
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z" +
                "M10.86,18.94v-4.27h-0.09l-1.77,0.63v0.69l1.01,-0.31v3.26h0.85z" +
                "M15.14,16.47c0,-0.27 -0.03,-0.49 -0.09,-0.69s-0.14,-0.35 -0.25,-0.48 -0.24,-0.22 -0.39,-0.28 -0.32,-0.09 -0.51,-0.09 -0.36,0.03 -0.51,0.09 -0.29,0.15 -0.4,0.28 -0.2,0.29 -0.26,0.48 -0.09,0.42 -0.09,0.69v0.85c0,0.27 0.03,0.49 0.09,0.69s0.15,0.35 0.26,0.48 0.24,0.22 0.39,0.28 0.32,0.09 0.51,0.09 0.36,-0.03 0.51,-0.09 0.28,-0.15 0.39,-0.28 0.19,-0.29 0.25,-0.48 0.09,-0.42 0.09,-0.69v-0.85z" +
                "M14.24,17.38c0,0.16 -0.02,0.29 -0.05,0.4s-0.06,0.2 -0.11,0.27 -0.11,0.12 -0.18,0.14 -0.14,0.05 -0.23,0.05c-0.08,0 -0.16,-0.02 -0.23,-0.05s-0.13,-0.08 -0.18,-0.14 -0.09,-0.16 -0.12,-0.27 -0.04,-0.24 -0.04,-0.4v-1.06c0,-0.16 0.01,-0.29 0.04,-0.4s0.06,-0.19 0.11,-0.26 0.11,-0.11 0.18,-0.14 0.14,-0.04 0.23,-0.04c0.08,0 0.16,0.01 0.23,0.04s0.13,0.07 0.18,0.14 0.09,0.15 0.12,0.26 0.04,0.24 0.04,0.4v1.06z",
        )
    }
    val Forward10 by lazy {
        materialIcon(
            "Exhale.Forward10",
            "M11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.06,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6C16.55,8.99 14.15,8 11.5,8z" +
                "M10.86,18.94v-4.27h-0.09l-1.77,0.63v0.69l1.01,-0.31v3.26h0.85z" +
                "M15.14,16.47c0,-0.27 -0.03,-0.49 -0.09,-0.69s-0.14,-0.35 -0.25,-0.48 -0.24,-0.22 -0.39,-0.28 -0.32,-0.09 -0.51,-0.09 -0.36,0.03 -0.51,0.09 -0.29,0.15 -0.4,0.28 -0.2,0.29 -0.26,0.48 -0.09,0.42 -0.09,0.69v0.85c0,0.27 0.03,0.49 0.09,0.69s0.15,0.35 0.26,0.48 0.24,0.22 0.39,0.28 0.32,0.09 0.51,0.09 0.36,-0.03 0.51,-0.09 0.28,-0.15 0.39,-0.28 0.19,-0.29 0.25,-0.48 0.09,-0.42 0.09,-0.69v-0.85z" +
                "M14.24,17.38c0,0.16 -0.02,0.29 -0.05,0.4s-0.06,0.2 -0.11,0.27 -0.11,0.12 -0.18,0.14 -0.14,0.05 -0.23,0.05c-0.08,0 -0.16,-0.02 -0.23,-0.05s-0.13,-0.08 -0.18,-0.14 -0.09,-0.16 -0.12,-0.27 -0.04,-0.24 -0.04,-0.4v-1.06c0,-0.16 0.01,-0.29 0.04,-0.4s0.06,-0.19 0.11,-0.26 0.11,-0.11 0.18,-0.14 0.14,-0.04 0.23,-0.04c0.08,0 0.16,0.01 0.23,0.04s0.13,0.07 0.18,0.14 0.09,0.15 0.12,0.26 0.04,0.24 0.04,0.4v1.06z",
        )
    }
    val Equalizer by lazy { materialIcon("Exhale.Equalizer", "M10,20h4V4h-4v16zM4,20h4v-8H4v8zM16,9v11h4V9h-4z") }
    // shuffle: two lines that cross, each ending in an arrowhead on the right.
    val Shuffle by lazy {
        dockIcon(
            "Exhale.Shuffle", 24f,
            Stroke("M3.5,16.8h2.3c2,0 3.2,-0.9 4.4,-2.7l3.4,-5.2c1.2,-1.8 2.4,-2.7 4.4,-2.7h2.5 M18.1,4l2.4,2.2l-2.4,2.2", 1.8f),
            Stroke("M3.5,7.2h2.3c2,0 3.2,0.9 4.4,2.7 M13.8,14.1c1.2,1.8 2.4,2.7 4.4,2.7h2.3 M18.1,14.6l2.4,2.2l-2.4,2.2", 1.8f),
        )
    }

    private const val SPEAKER = "M3.6,9.2h3l4.7,-3.9c0.62,-0.52 1.55,-0.08 1.55,0.73v11.94c0,0.81 -0.93,1.25 -1.55,0.73l-4.7,-3.9h-3a1.1,1.1 0 0 1 -1.1,-1.1v-3.4a1.1,1.1 0 0 1 1.1,-1.1z"

    // speaker.wave.3.fill
    val VolumeUp by lazy {
        dockIcon(
            "Exhale.VolumeUp", 24f,
            Stroke(SPEAKER),
            Stroke("M15.4,9.3a3.9,3.9 0 0 1 0,5.4 M17.7,7.1a7,7 0 0 1 0,9.8 M20,4.9a10.1,10.1 0 0 1 0,14.2", 1.6f),
        )
    }

    // speaker.fill
    val VolumeDown by lazy { dockIcon("Exhale.VolumeDown", 24f, Stroke(SPEAKER), Stroke("M15.4,9.3a3.9,3.9 0 0 1 0,5.4", 1.6f)) }

    // speaker.slash.fill
    val VolumeOff by lazy { dockIcon("Exhale.VolumeOff", 24f, Stroke(SPEAKER), Stroke("M15.4,9.2l5,5.6 M20.4,9.2l-5,5.6", 1.7f)) }

    // quote.bubble: the Music app's lyrics button, as its player capsule draws it — a rounded square
    // bubble in outline, tail at lower left, two solid quotation marks inside.
    val Lyrics by lazy {
        dockIcon(
            "Exhale.Lyrics", 24f,
            Stroke(
                "M7.3,4.4h9.4a3.5,3.5 0 0 1 3.5,3.5v5.4a3.5,3.5 0 0 1 -3.5,3.5h-6.1l-3.6,3c-0.4,0.3 -0.95,0 -0.85,-0.5l0.4,-2.6a3.5,3.5 0 0 1 -2.75,-3.4v-5.4a3.5,3.5 0 0 1 3.5,-3.5z",
                1.7f,
            ),
            Stroke(
                "M8.6,8.2h2.1v1.9c0,1.35 -0.68,2.2 -1.9,2.6l-0.37,-0.82c0.57,-0.23 0.88,-0.67 0.9,-1.25H8.6z" +
                    "M13.2,8.2h2.1v1.9c0,1.35 -0.68,2.2 -1.9,2.6l-0.37,-0.82c0.57,-0.23 0.88,-0.67 0.9,-1.25h-0.73z",
            ),
        )
    }

    // list.bullet: three dots, three lines.
    val Queue by lazy {
        dockIcon(
            "Exhale.Queue", 24f,
            Stroke("M5,5.2a1.35,1.35 0 1 1 0,2.7a1.35,1.35 0 1 1 0,-2.7z M5,10.65a1.35,1.35 0 1 1 0,2.7a1.35,1.35 0 1 1 0,-2.7z M5,16.1a1.35,1.35 0 1 1 0,2.7a1.35,1.35 0 1 1 0,-2.7z"),
            Stroke("M9.2,6.55h11 M9.2,12h11 M9.2,17.45h11", 1.8f),
        )
    }

    // chevron.down
    val Collapse by lazy { dockIcon("Exhale.Collapse", 24f, Stroke("M5.8,9.2l6.2,6.2l6.2,-6.2", 2.2f)) }
}

object NavIcons {
    // music.note, for a cover that isn't there.
    val MusicNote by lazy { materialIcon("Exhale.MusicNote", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z") }
    val Back by lazy { materialIcon("Exhale.Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    // ellipsis: SF Symbols' three dots are small and spaced, not Material's heavy 4-unit ones.
    val More by lazy {
        materialIcon(
            "Exhale.More",
            "M5.5,10.4a1.6,1.6 0 1 1 0,3.2a1.6,1.6 0 1 1 0,-3.2z" +
                "M12,10.4a1.6,1.6 0 1 1 0,3.2a1.6,1.6 0 1 1 0,-3.2z" +
                "M18.5,10.4a1.6,1.6 0 1 1 0,3.2a1.6,1.6 0 1 1 0,-3.2z",
        )
    }
    /** A record: a rounded sleeve, the label cut out of it, and the spindle back in the middle. */
    val Library by lazy {
        holedIcon(
            "Exhale.Library",
            "M8,3h8a5,5 0 0 1 5,5v8a5,5 0 0 1 -5,5h-8a5,5 0 0 1 -5,-5v-8a5,5 0 0 1 5,-5z" +
                "M12,8.4a3.6,3.6 0 1 0 0,7.2a3.6,3.6 0 1 0 0,-7.2z" +
                "M12,10.9a1.1,1.1 0 1 0 0,2.2a1.1,1.1 0 1 0 0,-2.2z",
        )
    }
    val Moon by lazy {
        materialIcon("Exhale.Moon", "M9.37,5.51C9.19,6.15 9.1,6.82 9.1,7.5c0,4.08 3.32,7.4 7.4,7.4 0.68,0 1.35,-0.09 1.99,-0.27C17.45,17.19 14.93,19 12,19c-3.86,0 -7,-3.14 -7,-7 0,-2.93 1.81,-5.45 4.37,-6.49zM12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z")
    }
    val Speed by lazy {
        materialIcon("Exhale.Speed", "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58H5.07A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1,10 10,0 0,0 -0.27,-10.44zM10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z")
    }
    val Surround by lazy {
        materialIcon("Exhale.Surround", "M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4zM7.76,16.24l-1.41,1.41C4.78,16.1 4,14.05 4,12c0,-2.05 0.78,-4.1 2.34,-5.66l1.41,1.41C6.59,8.93 6,10.46 6,12S6.59,15.07 7.76,16.24zM12,16c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4 4,1.79 4,4S14.21,16 12,16zM17.66,17.66l-1.41,-1.41C17.41,15.07 18,13.54 18,12s-0.59,-3.07 -1.76,-4.24l1.41,-1.41C19.22,7.9 20,9.95 20,12C20,14.05 19.22,16.1 17.66,17.66zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2S13.1,10 12,10z")
    }
    /** Dolby's double-D: two solid Ds back to back, their bowls meeting in the middle. */
    val DolbyAtmos by lazy {
        materialIcon("Exhale.DolbyAtmos", "M2,5h2.5a7,7 0,0 1,0 14H2z M22,5h-2.5a7,7 0,0 0,0 14H22z")
    }

    /**
     * The Android app's Spatial Audio mark (res/drawable/ic_spatial_audio.xml): a listener, and a ring
     * of sound passing behind them and in front.
     */
    val SpatialAudio by lazy {
        ImageVector.Builder(name = "Exhale.SpatialAudio", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(addPathNodes("M2.8,15.4A9.2,3.1 0,0 1,21.2 15.4"), stroke = SolidColor(Color.Black), strokeAlpha = 0.45f, strokeLineWidth = 1.3f, strokeLineCap = StrokeCap.Round)
            .addPath(addPathNodes("M9.3,9.2a2.7,2.7 0,1 0,5.4 0a2.7,2.7 0,1 0,-5.4 0z"), fill = SolidColor(Color.Black))
            .addPath(addPathNodes("M7.4,17.4c0.5,-2.9 2.3,-4.4 4.6,-4.4s4.1,1.5 4.6,4.4z"), fill = SolidColor(Color.Black))
            .addPath(addPathNodes("M2.8,15.4A9.2,3.1 0,0 0,21.2 15.4"), stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round)
            .build()
    }
    val Headphones by lazy {
        materialIcon("Exhale.Headphones", "M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8H5v-2c0,-3.87 3.13,-7 7,-7s7,3.13 7,7v2h-4v8h3c1.66,0 3,-1.34 3,-3v-7c0,-4.97 -4.03,-9 -9,-9z")
    }
    val HighQuality by lazy {
        materialIcon("Exhale.HighQuality", "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM11,15H9.5v-2h-2v2H6V9h1.5v2.5h2V9H11v6zM18,14c0,0.55 -0.45,1 -1,1h-0.75v1.5h-1.5V15H14c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v4zM14.5,13.5h2v-3h-2v3z")
    }
    val Timer by lazy {
        materialIcon("Exhale.Timer", "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
    }
    val Sparkles by lazy {
        materialIcon("Exhale.Sparkles", "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12L11.5,9.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z")
    }
    val TextFields by lazy { materialIcon("Exhale.TextFields", "M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z") }
    val AlignLeft by lazy { materialIcon("Exhale.AlignLeft", "M15,15H3v2h12v-2zM15,7H3v2h12V7zM3,13h18v-2H3v2zM3,21h18v-2H3v2zM3,3v2h18V3H3z") }
    val Explicit by lazy {
        materialIcon("Exhale.Explicit", "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM15,9h-4v2h4v2h-4v2h4v2H9V7h6v2z")
    }
    val Delete by lazy { materialIcon("Exhale.Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z") }
    /** A screen with a play mark: watch the song's video. */
    val Video by lazy {
        materialIcon("Exhale.Video", "M21,3H3C1.89,3 1,3.89 1,5v12c0,1.1 0.89,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.11 -0.9,-2 -2,-2zM21,17H3V5h18v12zM16,11l-7,4V7z")
    }
    val StarOutline by lazy {
        materialIcon("Exhale.StarOutline", "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z")
    }
    val Image by lazy {
        materialIcon("Exhale.Image", "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z")
    }
    val Glass by lazy {
        materialIcon("Exhale.Glass", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM7,12c0,-2.76 2.24,-5 5,-5v2c-1.65,0 -3,1.35 -3,3H7z")
    }
    val OpenInNew by lazy {
        materialIcon("Exhale.OpenInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val Fullscreen by lazy { materialIcon("Exhale.Fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z") }
    val FullscreenExit by lazy { materialIcon("Exhale.FullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z") }
    val Split by lazy { materialIcon("Exhale.Split", "M3,5v14h18V5H3zM11,17H5V7h6v10zM19,17h-6V7h6v10z") }
    val Keyboard by lazy {
        materialIcon("Exhale.Keyboard", "M20,5H4c-1.1,0 -1.99,0.9 -1.99,2L2,17c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM11,8h2v2h-2V8zM11,11h2v2h-2v-2zM8,8h2v2H8V8zM8,11h2v2H8v-2zM7,13H5v-2h2v2zM7,10H5V8h2v2zM16,17H8v-2h8v2zM16,13h-2v-2h2v2zM16,10h-2V8h2v2zM19,13h-2v-2h2v2zM19,10h-2V8h2v2z")
    }
    val Reset by lazy {
        materialIcon("Exhale.Reset", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    }
    val Sync by lazy {
        materialIcon("Exhale.Sync", "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
    }
    val GitHub by lazy {
        materialIcon("Exhale.GitHub", "M12,0.297c-6.63,0 -12,5.373 -12,12 0,5.303 3.438,9.8 8.205,11.385 0.6,0.113 0.82,-0.258 0.82,-0.577 0,-0.285 -0.01,-1.04 -0.015,-2.04 -3.338,0.724 -4.042,-1.61 -4.042,-1.61C4.422,18.07 3.633,17.7 3.633,17.7c-1.087,-0.744 0.084,-0.729 0.084,-0.729 1.205,0.084 1.838,1.236 1.838,1.236 1.07,1.835 2.809,1.305 3.495,0.998 0.108,-0.776 0.417,-1.305 0.76,-1.605 -2.665,-0.3 -5.466,-1.332 -5.466,-5.93 0,-1.31 0.465,-2.38 1.235,-3.22 -0.135,-0.303 -0.54,-1.523 0.105,-3.176 0,0 1.005,-0.322 3.3,1.23 0.96,-0.267 1.98,-0.399 3,-0.405 1.02,0.006 2.04,0.138 3,0.405 2.28,-1.552 3.285,-1.23 3.285,-1.23 0.645,1.653 0.24,2.873 0.12,3.176 0.765,0.84 1.23,1.91 1.23,3.22 0,4.61 -2.805,5.625 -5.475,5.92 0.42,0.36 0.81,1.096 0.81,2.22 0,1.606 -0.015,2.896 -0.015,3.286 0,0.315 0.21,0.69 0.825,0.57C20.565,22.092 24,17.592 24,12.297c0,-6.627 -5.373,-12 -12,-12")
    }
    val Send by lazy { materialIcon("Exhale.Send", "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z") }
    val Camera by lazy {
        materialIcon("Exhale.Camera", "M12,15.2c1.77,0 3.2,-1.43 3.2,-3.2S13.77,8.8 12,8.8 8.8,10.23 8.8,12s1.43,3.2 3.2,3.2zM9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z")
    }
    val Copy by lazy {
        materialIcon("Exhale.Copy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    }
    val Star by lazy { materialIcon("Exhale.Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z") }
    val Folder by lazy { materialIcon("Exhale.Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z") }
    val Chip by lazy {
        materialIcon("Exhale.Chip", "M15,9H9v6h6V9zM13,13h-2v-2h2v2zM21,11V9h-2V7c0,-1.1 -0.9,-2 -2,-2h-2V3h-2v2h-2V3H9v2H7c-1.1,0 -2,0.9 -2,2v2H3v2h2v2H3v2h2v2c0,1.1 0.9,2 2,2h2v2h2v-2h2v2h2v-2h2c1.1,0 2,-0.9 2,-2v-2h2v-2h-2v-2h2zM17,17H7V7h10v10z")
    }
    val Palette by lazy {
        materialIcon("Exhale.Palette", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    }
    val Lock by lazy {
        materialIcon("Exhale.Lock", "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z")
    }
    val Storage by lazy { materialIcon("Exhale.Storage", "M2,20h20v-4H2v4zM4,17h2v2H4v-2zM2,4v4h20V4H2zM6,7H4V5h2v2zM2,14h20v-4H2v4zM4,11h2v2H4v-2z") }
    val Backup by lazy {
        materialIcon("Exhale.Backup", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM14,13v4h-4v-4H7l5,-5 5,5h-3z")
    }
    val Link by lazy {
        materialIcon("Exhale.Link", "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    }
    val Stats by lazy { materialIcon("Exhale.Stats", "M5,9.2h3L8,19L5,19zM10.6,5h2.8v14h-2.8zM16.2,13L19,13v6h-2.8z") }
    val Download by lazy { materialIcon("Exhale.Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val Logout by lazy { materialIcon("Exhale.Logout", "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z") }
    val ChevronRight by lazy { materialIcon("Exhale.ChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val Update by lazy {
        materialIcon("Exhale.Update", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")
    }
    val Info by lazy { materialIcon("Exhale.Info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z") }
    val Language by lazy {
        materialIcon("Exhale.Language", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2H4.26zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8H5.08c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2zM14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z")
    }
    /** A solid house with its doorway cut away at the foot — one shape, no hairlines. */
    val Home by lazy {
        materialIcon(
            "Exhale.Home",
            "M12,2.6c0.43,0 0.85,0.15 1.19,0.42l7.05,5.66C20.72,9.06 21,9.64 21,10.26V19c0,1.1 -0.9,2 -2,2h-3.75" +
                "c-0.41,0 -0.75,-0.34 -0.75,-0.75V16c0,-0.97 -0.78,-1.75 -1.75,-1.75h-1.5C10.28,14.25 9.5,15.03 9.5,16v4.25" +
                "c0,0.41 -0.34,0.75 -0.75,0.75H5c-1.1,0 -2,-0.9 -2,-2v-8.74c0,-0.62 0.28,-1.2 0.76,-1.58l7.05,-5.66" +
                "C11.15,2.75 11.57,2.6 12,2.6z",
        )
    }

    /** A magnifier with real weight: a thick ring and a rounded handle. */
    val Search by lazy {
        holedIcon(
            "Exhale.Search",
            "M10.6,2.8a7.8,7.8 0 1 0 0,15.6a7.8,7.8 0 1 0 0,-15.6z" +
                "M10.6,5.6a5,5 0 1 1 0,10a5,5 0 1 1 0,-10z" +
                "M16.25,15.05a1.45,1.45 0 0 1 2.05,0l2.9,2.9a1.45,1.45 0 0 1 -2.05,2.05l-2.9,-2.9a1.45,1.45 0 0 1 0,-2.05z",
        )
    }

    /** Mood & Genres: three rounded bars, the shape music makes. */
    val New by lazy {
        materialIcon(
            "Exhale.New",
            "M6.25,9.5a2,2 0 0 1 2,2v7a2,2 0 0 1 -4,0v-7a2,2 0 0 1 2,-2z" +
                "M12,3.5a2,2 0 0 1 2,2v13a2,2 0 0 1 -4,0v-13a2,2 0 0 1 2,-2z" +
                "M17.75,12.5a2,2 0 0 1 2,2v4a2,2 0 0 1 -4,0v-4a2,2 0 0 1 2,-2z",
        )
    }
    val Radio by lazy {
        materialIcon("Exhale.Radio", "M3.24,6.15C2.51,6.43 2,7.17 2,8v12c0,1.1 0.89,2 2,2h16c1.11,0 2,-0.9 2,-2V8c0,-1.11 -0.89,-2 -2,-2H8.3l8.26,-3.34L15.88,1 3.24,6.15zM7,20c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM20,12h-2v-2h-2v2H4V8h16v4z")
    }
    val Recent by lazy {
        materialIcon("Exhale.Recent", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z")
    }
    val Artists by lazy {
        materialIcon("Exhale.Artists", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    }
    val Taste by lazy {
        materialIcon("Exhale.Taste", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    }
    /** Where the sound goes: a screen with a wedge rising into it. */
    val Output by lazy {
        materialIcon("Exhale.Output", "M6,22h12l-6,-6zM21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h4v-2H3V5h18v12h-4v2h4c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    }
    val Check by lazy { materialIcon("Exhale.Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Settings by lazy {
        materialIcon("Exhale.Settings", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")
    }
}
