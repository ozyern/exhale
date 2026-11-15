/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.canvas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One of the albums Apple Music puts under an artist's "Essential Albums". */
@Serializable
data class AppleEssentialAlbum(
    val name: String,
    val artwork: AppleArtwork?,
    /** Apple's short editorial line for it, plain text; null when Apple wrote none. */
    val note: String?,
    val year: String?,
)

/** What Apple says an album is: its genre, for the line under its title. */
@Serializable
data class AppleAlbumInfo(val genre: String?)

internal object AppleEssentialAlbumsParser {
    /** Albums in an artist response's `featured-albums` view: Apple's Essential Albums shelf. */
    fun parse(root: JsonObject): List<AppleEssentialAlbum> {
        val artist = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        val view = ((artist["views"] as? JsonObject)?.get("featured-albums") as? JsonObject)
            ?.get("data") as? JsonArray ?: return emptyList()
        return view.mapNotNull { item ->
            val attrs = (item as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
            val name = attrs.text("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val notes = attrs["editorialNotes"] as? JsonObject
            AppleEssentialAlbum(
                name = name,
                artwork = attrs["artwork"]?.let(AppleMusicDataMapper::artwork),
                note = (notes?.text("short") ?: notes?.text("tagline") ?: notes?.text("standard"))
                    ?.let(::plainText)?.takeIf { it.isNotBlank() },
                year = attrs.text("releaseDate")?.take(4)?.takeIf { it.length == 4 },
            )
        }
    }

    /** First genre that says something: Apple lists "Music" on nearly everything. */
    fun genre(attrs: JsonObject): String? =
        (attrs["genreNames"] as? JsonArray)
            ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.firstOrNull { it.isNotBlank() && !it.equals("Music", ignoreCase = true) }

    /** Editorial notes carry light HTML (`<i>`, `<br />`) and entities. */
    fun plainText(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
}
