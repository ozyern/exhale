/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.artist

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.ozyern.exhale.R

/**
 * How an artist's name is set at the top of their page.
 *
 * Apple Music 7 draws each artist in a face of their own. Exhale cannot know the face an artist's
 * label uses, so it lets the listener choose: a default for every artist in Settings, and — by
 * holding the name — one for this artist alone, remembered per artist.
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

    val family: FontFamily
        get() = when (this) {
            CLASSIC -> FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))
            // Apple Music's condensed news-gothic credit, as on Bruno Mars's page.
            HEADLINE -> FontFamily(
                Font(
                    R.font.anybody,
                    weight = FontWeight.ExtraBold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(820), FontVariation.width(78f)),
                ),
            )
            // The Chainsmokers' page: tall, tight, black — and worn, see [distressed].
            STAMP -> FontFamily(
                Font(
                    R.font.anybody,
                    weight = FontWeight.Black,
                    variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(55f)),
                ),
            )
            POSTER -> FontFamily(
                Font(
                    R.font.anybody,
                    weight = FontWeight.Black,
                    variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.width(62f)),
                ),
            )
            WIDE -> FontFamily(
                Font(
                    R.font.anybody,
                    weight = FontWeight.ExtraBold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(150f)),
                ),
            )
            ROUNDED -> FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))
            GEOMETRIC -> FontFamily(Font(R.font.poppins, FontWeight.Bold))
            SOFT -> FontFamily(Font(R.font.linotte, FontWeight.Bold))
            SERIF -> FontFamily.Serif
            MONO -> FontFamily.Monospace
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

    fun size(name: String): TextUnit {
        val base = when {
            name.length <= 10 -> 54f
            name.length <= 16 -> 44f
            name.length <= 24 -> 36f
            else -> 30f
        }
        return (base * scale).sp
    }

    companion object {
        private const val PREFS = "artist_name_fonts"

        fun of(value: String?): ArtistNameFont? = entries.firstOrNull { it.name == value }

        /** The face chosen for [artistId] alone, or null to follow the default. */
        fun forArtist(context: Context, artistId: String): ArtistNameFont? =
            of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(artistId, null))

        /** How many artists have a face of their own. */
        fun overrideCount(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.size

        /** Every artist back on the default face. */
        fun clearOverrides(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }

        fun setForArtist(context: Context, artistId: String, font: ArtistNameFont?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (font == null) remove(artistId) else putString(artistId, font.name)
            }.apply()
        }
    }
}

/**
 * Wears [font]'s letters through in a fixed scatter of specks and scratches when the face is
 * [ArtistNameFont.distressed]; otherwise leaves them alone. Fixed, so a name does not shimmer as it
 * recomposes.
 */
fun androidx.compose.ui.Modifier.artistNameTexture(font: ArtistNameFont): androidx.compose.ui.Modifier {
    if (!font.distressed) return this
    return this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val rnd = java.util.Random(7L)
            val area = size.width * size.height
            val specks = (area / 90f).toInt().coerceIn(200, 2600)
            repeat(specks) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val r = (0.4f + rnd.nextFloat() * rnd.nextFloat() * 2.6f) * density
                drawCircle(
                    androidx.compose.ui.graphics.Color.Black,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                    alpha = 0.55f + rnd.nextFloat() * 0.45f,
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                )
            }
            repeat(18) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val len = (6f + rnd.nextFloat() * 22f) * density
                drawLine(
                    androidx.compose.ui.graphics.Color.Black,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x + len, y + (rnd.nextFloat() - 0.5f) * 4f * density),
                    strokeWidth = (0.6f + rnd.nextFloat()) * density,
                    alpha = 0.7f,
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                )
            }
        }
}
