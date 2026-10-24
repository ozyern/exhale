/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.pages

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.data.DesktopPrefs

/**
 * How an artist's name is set at the top of their page — the Android app's ArtistNameFont, the same
 * faces and numbers.
 *
 * The Music app draws each artist in a face of their own. Exhale can't know the face an artist's
 * label uses, so it lets the listener choose: a default for every artist, and one for an artist alone,
 * remembered per artist. Clicking the name on an artist's page offers both.
 */
enum class ArtistNameFont(val label: String) {
    CLASSIC("Classic"),
    HEADLINE("Headline"),
    POSTER("Poster"),
    STAMP("Stamp"),
    WIDE("Wide"),
    ROUNDED("Rounded"),
    GEOMETRIC("Geometric"),
    SOFT("Soft"),
    SERIF("Serif"),
    MONO("Mono");

    val family: FontFamily by lazy {
        when (this) {
            CLASSIC -> FontFamily(face("sfprodisplaybold", FontWeight.Bold))
            // The Music app's condensed news-gothic credit, as on Bruno Mars's page.
            HEADLINE -> FontFamily(face("anybody", FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(820), FontVariation.width(78f))))
            // The Chainsmokers' page: tall, tight, black — and worn, see [distressed].
            STAMP -> FontFamily(face("anybody", FontWeight.Black, FontVariation.Settings(FontVariation.weight(900), FontVariation.width(55f))))
            POSTER -> FontFamily(face("anybody", FontWeight.Black, FontVariation.Settings(FontVariation.weight(900), FontVariation.width(62f))))
            WIDE -> FontFamily(face("anybody", FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(800), FontVariation.width(150f))))
            ROUNDED -> FontFamily(face("unbounded_semibold", FontWeight.SemiBold))
            GEOMETRIC -> FontFamily(face("poppins", FontWeight.Bold))
            SOFT -> FontFamily(face("linotte", FontWeight.Bold))
            SERIF -> FontFamily.Serif
            MONO -> FontFamily.Monospace
        }
    }

    /** Faces this wide set smaller, so a long name still fits in two lines. */
    val scale: Float
        get() = when (this) {
            WIDE, ROUNDED -> 0.78f
            POSTER -> 1.12f
            HEADLINE -> 1.06f
            STAMP -> 1.18f
            MONO -> 0.82f
            else -> 1f
        }

    val uppercase: Boolean get() = this == POSTER || this == WIDE || this == HEADLINE || this == STAMP

    /** Letters worn through in places, like a print from a tired stamp. */
    val distressed: Boolean get() = this == STAMP

    /** Sized to the name: a short one big, a long one smaller, so it fits in two lines over the photo. */
    fun size(name: String): TextUnit {
        val base = when {
            name.length <= 10 -> 76f
            name.length <= 16 -> 62f
            name.length <= 24 -> 50f
            else -> 40f
        }
        return (base * scale).sp
    }

    private fun face(file: String, weight: FontWeight, variation: FontVariation.Settings? = null) =
        javaClass.getResourceAsStream("/font/artist/$file.ttf")!!.use { it.readAllBytes() }.let { bytes ->
            // The identity names the variation too: the font cache keys on it.
            if (variation == null) Font("artist-$file", bytes, weight) else Font("artist-$file-$name", bytes, weight, variationSettings = variation)
        }

    companion object {
        private const val DefaultKey = "artist_name_font"
        private const val PerArtistPrefix = "artist_name_font."

        fun of(value: String?): ArtistNameFont? = entries.firstOrNull { it.name == value }

        /** Bumped on every change, so open pages pick it up. */
        val changes = mutableStateOf(0)

        /** The face every artist gets unless they have their own. */
        var default: ArtistNameFont
            get() = of(DesktopPrefs.raw(DefaultKey)) ?: CLASSIC
            set(value) {
                DesktopPrefs.putAll(mapOf(DefaultKey to value.name))
                changes.value++
            }

        /** The face chosen for [artistId] alone, or null to follow the default. */
        fun forArtist(artistId: String): ArtistNameFont? = of(DesktopPrefs.raw(PerArtistPrefix + artistId))

        fun setForArtist(artistId: String, font: ArtistNameFont?) {
            DesktopPrefs.putAll(mapOf(PerArtistPrefix + artistId to font?.name))
            changes.value++
        }

        /** What [artistId]'s name is set in. */
        fun resolve(artistId: String): ArtistNameFont = forArtist(artistId) ?: default
    }
}

/**
 * Wears [font]'s letters through in a fixed scatter of specks and scratches when the face is
 * [ArtistNameFont.distressed]; otherwise leaves them alone. Fixed, so a name doesn't shimmer as it
 * redraws.
 */
fun Modifier.artistNameTexture(font: ArtistNameFont): Modifier {
    if (!font.distressed) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val rnd = java.util.Random(7L)
            val area = size.width * size.height
            val specks = (area / 90f).toInt().coerceIn(200, 2600)
            repeat(specks) {
                val r = (0.4f + rnd.nextFloat() * rnd.nextFloat() * 2.6f) * density
                drawCircle(Color.Black, radius = r, center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), alpha = 0.55f + rnd.nextFloat() * 0.45f, blendMode = BlendMode.DstOut)
            }
            repeat(18) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val len = (6f + rnd.nextFloat() * 22f) * density
                drawLine(
                    Color.Black,
                    start = Offset(x, y),
                    end = Offset(x + len, y + (rnd.nextFloat() - 0.5f) * 4f * density),
                    strokeWidth = (0.6f + rnd.nextFloat()) * density,
                    alpha = 0.7f,
                    blendMode = BlendMode.DstOut,
                )
            }
        }
}
