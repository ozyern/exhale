/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.MediaInfo
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.utils.makeTimeString

/**
 * The song's details: what it is first, then what it sounds like, then everything else.
 *
 * It was a small thumbnail beside the title and then three identical tables, so the one thing
 * people open it for (how good is this stream) was a row among nine. Now it opens on the cover at
 * size, as the iOS music app's info sheet does; the numbers worth a glance sit in a strip of their
 * own; and the audio card leads with the codec and the bitrate in large type, its details under it.
 */
@Composable
fun ShowMediaInfo(videoId: String) {

    if (videoId.isBlank()) return

    val windowInsets = WindowInsets.systemBars
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current

    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var song by remember { mutableStateOf<Song?>(null) }
    var currentFormat by remember { mutableStateOf<FormatEntity?>(null) }

    LaunchedEffect(videoId) {
        info = YouTube.getMediaInfo(videoId).getOrNull()
    }

    LaunchedEffect(videoId) {
        database.song(videoId).collect { song = it }
    }

    LaunchedEffect(videoId) {
        database.format(videoId).collect { currentFormat = it }
    }

    fun copy(text: String) {
        val cm =
            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    val unknown = stringResource(R.string.unknown)
    val entity = song?.song

    // Album · year · length, the line under the artist; each part only when it is known.
    val metaLine = listOfNotNull(
        song?.album?.title ?: entity?.albumName,
        entity?.year?.toString(),
        entity?.duration?.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
    ).joinToString(" · ")

    val stats = buildList {
        val media = info ?: return@buildList
        media.viewCount?.let { add(formatCompactCount(it.toLong()) to stringResource(R.string.views)) }
        media.like?.let { add(formatCompactCount(it.toLong()) to stringResource(R.string.likes)) }
        // YouTube stopped publishing dislikes; a "0" there would be a lie, so it shows only when real.
        media.dislike?.takeIf { it > 0 }?.let { add(formatCompactCount(it.toLong()) to stringResource(R.string.dislikes)) }
        media.subscribers?.let { add(it.substringBefore(" subscriber") to stringResource(R.string.subscribers)) }
    }

    val audioFacts = buildList {
        val format = currentFormat ?: return@buildList
        add(stringResource(R.string.mime_type) to (format.mimeType ?: unknown))
        add(stringResource(R.string.codecs) to (format.codecs ?: unknown))
        add("Itag" to (format.itag?.toString() ?: unknown))
        add(
            stringResource(R.string.file_size) to
                (format.contentLength?.let { Formatter.formatShortFileSize(context, it) } ?: unknown),
        )
        add(
            stringResource(R.string.volume) to
                "${((playerConnection?.player?.volume ?: 1f) * 100).toInt()}%",
        )
    }

    val detailFacts = buildList {
        val current = song
        if (current != null) {
            add(stringResource(R.string.song_title) to current.title)
            add(stringResource(R.string.song_artists) to current.artists.joinToString { it.name })
            (current.album?.title ?: entity?.albumName)?.let { add(stringResource(R.string.media_info_album) to it) }
            entity?.duration?.takeIf { it > 0 }?.let {
                add(stringResource(R.string.media_info_duration) to makeTimeString(it * 1000L))
            }
            entity?.year?.let { add(stringResource(R.string.media_info_released) to it.toString()) }
        }
        info?.author?.let { add(stringResource(R.string.media_info_channel) to it) }
        info?.uploadDate?.let { add(stringResource(R.string.media_info_uploaded) to it) }
        add(stringResource(R.string.media_id) to videoId)
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(windowInsets.asPaddingValues())
            .padding(horizontal = 16.dp),
    ) {
        item(key = "header") {
            MediaInfoHeader(
                thumbnailUrl = song?.thumbnailUrl,
                title = song?.title ?: info?.title.orEmpty(),
                artist = song?.artists?.joinToString { it.name } ?: info?.author.orEmpty(),
                meta = metaLine,
                explicit = entity?.explicit == true,
            )
        }

        if (info == null) {
            item(key = "loading") {
                ShimmerHost {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        repeat(2) { TextPlaceholder() }
                    }
                }
            }
        } else if (stats.isNotEmpty()) {
            item(key = "stats") { StatStrip(stats) }
        }

        currentFormat?.let { format ->
            item(key = "audioHeader") { SectionTitle(stringResource(R.string.audio)) }
            item(key = "audio") {
                AudioCard(format = format, facts = audioFacts, unknown = unknown, onCopy = ::copy)
            }
        }

        item(key = "detailsHeader") { SectionTitle(stringResource(R.string.details)) }
        item(key = "details") { FactGroup(facts = detailFacts, onCopy = ::copy) }

        val description = info?.description.orEmpty()
        if (description.isNotBlank()) {
            item(key = "descriptionHeader") { SectionTitle(stringResource(R.string.description)) }
            item(key = "description") { DescriptionGroup(text = description) }
        }

        item(key = "tail") {
            Text(
                text = stringResource(R.string.media_info_tap_to_copy),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 32.dp),
            )
        }
    }
}

/**
 * The song this page is about, centred and at size: the cover with a real shadow under it, the
 * title, the artist, and album · year · length. Everything below is about this, so it comes first.
 */
@Composable
private fun MediaInfoHeader(
    thumbnailUrl: String?,
    title: String,
    artist: String,
    meta: String,
    explicit: Boolean,
) {
    val artShape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 20.dp),
    ) {
        Box(
            Modifier
                .size(188.dp)
                .shadow(18.dp, artShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (explicit) {
                Spacer(Modifier.width(6.dp))
                ExplicitBadge()
            }
        }
        if (artist.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExplicitBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
    ) {
        Text("E", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
    }
}

/** The numbers worth a glance, side by side in one card, hairlines between them. */
@Composable
private fun StatStrip(stats: List<Pair<String, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius))
            .padding(vertical = 14.dp),
    ) {
        stats.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * What the stream is, in large type (the codec as a badge, then bitrate and sample rate), and the
 * finer details as rows under it.
 */
@Composable
private fun AudioCard(
    format: FormatEntity,
    facts: List<Pair<String, String>>,
    unknown: String,
    onCopy: (String) -> Unit,
) {
    val headline = listOfNotNull(
        format.bitrate?.let { "${it / 1000} kbps" },
        format.sampleRate?.let { "%.1f kHz".format(it / 1000f).replace(".0 ", " ") },
    ).joinToString(" · ").ifBlank { unknown }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopy(headline) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = codecName(format.codecs, format.mimeType),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = headline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.media_info_stream),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        facts.forEach { (label, value) ->
            Hairline()
            MediaRow(label = label, value = value, onClick = { onCopy(value) })
        }
    }
}

/** "Opus" or "AAC" rather than "opus" or "mp4a.40.2". */
private fun codecName(codecs: String?, mimeType: String?): String {
    val raw = (codecs ?: mimeType ?: "").lowercase()
    return when {
        "opus" in raw -> "OPUS"
        "mp4a" in raw || "aac" in raw -> "AAC"
        "flac" in raw -> "FLAC"
        "vorbis" in raw -> "VORBIS"
        "mp3" in raw || "mpeg" in raw -> "MP3"
        raw.isBlank() -> "AUDIO"
        else -> raw.substringBefore('.').uppercase()
    }
}

/** A table of label/value pairs, hairlines between them and nowhere else. */
@Composable
private fun FactGroup(
    facts: List<Pair<String, String>>,
    onCopy: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
    ) {
        facts.forEachIndexed { index, (label, value) ->
            if (index > 0) Hairline()
            MediaRow(label = label, value = value, onClick = { onCopy(value) })
        }
    }
}

/** Inset on both sides, as the Settings cards' dividers are. */
@Composable
private fun Hairline() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}

/**
 * The description, which is prose and not a fact.
 *
 * Collapsed to four lines with a "more" that opens it, because a YouTube description can be two
 * screens of timestamps and hashtags and it used to push every table on this page below the fold.
 */
@Composable
private fun DescriptionGroup(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius))
            .clickable { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize(),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (expanded) R.string.less else R.string.more),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    // Small, grey, in line with the row text: a header that introduces its card, not a banner.
    Text(
        text = title,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    // Label and value on one line, the way a table of facts is read: the eye runs down the left
    // for the thing it wants and right for the answer.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
