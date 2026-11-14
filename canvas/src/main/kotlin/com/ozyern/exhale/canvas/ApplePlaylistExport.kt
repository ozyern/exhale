package com.ozyern.exhale.canvas

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Reads one playlist exported by Music on Mac or iTunes, without Apple credentials. */
object ApplePlaylistExport {
    const val MAX_BYTES = 8 * 1024 * 1024

    fun parse(bytes: ByteArray): ApplePlaylist {
        require(bytes.size <= MAX_BYTES) { "Playlist export is too large" }
        val text = decodeText(bytes)
        val xml = removeAppleDoctype(text)

        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val document = builder.parse(InputSource(StringReader(xml)))
        require(document.documentElement.tagName == "plist") { "Expected an Apple XML playlist" }

        var count = 0
        fun value(element: Element, depth: Int = 0): Any {
            require(depth <= 24 && ++count <= 100_000) { "Playlist export is too complex" }
            return when (element.tagName) {
                "dict" -> {
                    val entries = children(element)
                    require(entries.size % 2 == 0) { "Invalid dictionary" }
                    buildMap {
                        for (index in entries.indices step 2) {
                            val key = entries[index]
                            require(key.tagName == "key") { "Invalid dictionary key" }
                            put(key.textContent, value(entries[index + 1], depth + 1))
                        }
                    }
                }
                "array" -> children(element).map { value(it, depth + 1) }
                "true" -> true
                "false" -> false
                "string", "integer", "real", "date", "data" -> element.textContent
                else -> error("Unsupported playlist value")
            }
        }

        val root = value(children(document.documentElement).single()) as? Map<*, *> ?: error("Invalid playlist export")
        val sourceTracks = root["Tracks"] as? Map<*, *> ?: error("Missing track metadata")
        val playlists = (root["Playlists"] as? List<*>)?.filterIsInstance<Map<*, *>>()
            ?.filter { it["Master"] != true && it["Folder"] != true }.orEmpty()
        require(playlists.size == 1) { "Export one playlist, rather than the entire library" }
        val playlist = playlists.single()
        val title = (playlist["Name"] as? String)?.takeIf { it.isNotBlank() } ?: error("Missing playlist title")
        val items = playlist["Playlist Items"] as? List<*> ?: emptyList<Any>()
        var skipped = 0
        val tracks = items.mapNotNull { item ->
            val id = (item as? Map<*, *>)?.get("Track ID") as? String
            val track = sourceTracks[id] as? Map<*, *>
            val name = track?.get("Name") as? String
            val artist = (track?.get("Artist") ?: track?.get("Album Artist")) as? String
            if (name.isNullOrBlank() || artist.isNullOrBlank() || track["Has Video"] == true || track["Podcast"] == true) {
                skipped++
                null
            } else ApplePlaylistTrack(name, artist, (track["Total Time"] as? String)?.toIntOrNull() ?: 0)
        }
        return ApplePlaylist(title, tracks, skipped)
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf16 = bytes.size >= 2 && (
            bytes[0] == 0.toByte() || bytes[1] == 0.toByte() ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            )
        val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
        return bytes.toString(charset).removePrefix("\uFEFF")
    }

    private fun removeAppleDoctype(text: String): String {
        require(!text.contains("<!ENTITY", ignoreCase = true)) {
            "Entity declarations are unsupported"
        }

        // Android's DOM factory does not support all desktop SAX feature flags.
        val quoted = "[\"'][^\"']*[\"']"
        val xml = text.replace(Regex("<!DOCTYPE\\s+plist\\s+(?:PUBLIC\\s+$quoted\\s+$quoted|SYSTEM\\s+$quoted)\\s*>"), "")
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "Unsupported document type" }
        return xml
    }

    private fun children(element: Element): List<Element> = buildList {
        for (index in 0 until element.childNodes.length) {
            (element.childNodes.item(index) as? Element)?.let { add(it) }
        }
    }
}
