/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.settingsGlassGroup
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.MediaInfo
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard

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

    // What the page is about, then what is known about it.
    //
    // It used to open on a centred "Details" banner and a card of stacked label/value pairs, with
    // the song itself nowhere on the page - you could read nine facts about a track without being
    // told which track. An iOS info sheet leads with the item: its artwork, its name, who made it.
    val trackFacts = buildList {
        val current = song
        if (current != null) {
            add(stringResource(R.string.song_title) to current.title)
            add(stringResource(R.string.song_artists) to current.artists.joinToString { it.name })
            add(stringResource(R.string.media_id) to current.id)
        }
    }

    val audioFacts = buildList {
        val format = currentFormat
        if (format != null) {
            add("Itag" to (format.itag?.toString() ?: unknown))
            add(stringResource(R.string.mime_type) to (format.mimeType ?: unknown))
            add(stringResource(R.string.codecs) to (format.codecs ?: unknown))
            add(
                stringResource(R.string.bitrate) to
                    (format.bitrate?.let { "${it / 1000} Kbps" } ?: unknown)
            )
            add(
                stringResource(R.string.sample_rate) to
                    (format.sampleRate?.let { "$it Hz" } ?: unknown)
            )
            add(
                stringResource(R.string.volume) to
                    "${((playerConnection?.player?.volume ?: 1f) * 100).toInt()}%"
            )
            add(
                stringResource(R.string.file_size) to
                    (format.contentLength?.let { Formatter.formatShortFileSize(context, it) } ?: unknown)
            )
        }
    }

    val statFacts = buildList {
        val media = info
        if (media != null) {
            add(stringResource(R.string.subscribers) to (media.subscribers ?: unknown))
            add(
                stringResource(R.string.views) to
                    (media.viewCount?.toInt()?.let { numberFormatter(it) } ?: unknown)
            )
            add(
                stringResource(R.string.likes) to
                    (media.like?.toInt()?.let { numberFormatter(it) } ?: unknown)
            )
            add(
                stringResource(R.string.dislikes) to
                    (media.dislike?.toInt()?.let { numberFormatter(it) } ?: unknown)
            )
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .padding(windowInsets.asPaddingValues())
            .padding(horizontal = 16.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item(key = "header") {
            MediaInfoHeader(
                thumbnailUrl = song?.thumbnailUrl ?: info?.let { null },
                title = song?.title ?: info?.title.orEmpty(),
                subtitle = song?.artists?.joinToString { it.name } ?: info?.author.orEmpty(),
            )
        }

        if (trackFacts.isNotEmpty()) {
            item(key = "trackHeader") { SectionTitle(stringResource(R.string.details)) }
            item(key = "track") {
                FactGroup(facts = trackFacts, onCopy = ::copy)
            }
        }

        if (audioFacts.isNotEmpty()) {
            item(key = "audioHeader") { SectionTitle(stringResource(R.string.audio)) }
            item(key = "audio") {
                FactGroup(facts = audioFacts, onCopy = ::copy)
            }
        }

        if (info == null) {
            item(key = "loading") {
                ShimmerHost {
                    Column(Modifier.padding(top = 20.dp)) {
                        repeat(3) { TextPlaceholder() }
                    }
                }
            }
        } else {
            if (statFacts.isNotEmpty()) {
                item(key = "statsHeader") { SectionTitle(stringResource(R.string.information)) }
                item(key = "stats") {
                    FactGroup(facts = statFacts, onCopy = ::copy)
                }
            }

            val description = info?.description.orEmpty()
            if (description.isNotBlank()) {
                item(key = "descriptionHeader") {
                    SectionTitle(stringResource(R.string.description))
                }
                item(key = "description") {
                    DescriptionGroup(text = description)
                }
            }
        }

        item(key = "tail") { Spacer(Modifier.height(32.dp)) }
    }
}

/**
 * The song this page is about: artwork, name, artist.
 *
 * The same shape every iOS info sheet opens with, and the reason is practical - the facts below
 * are a list of strings with no context of their own, so without this the page could be about
 * anything.
 */
@Composable
private fun MediaInfoHeader(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A grouped table of label/value pairs, hairlines between them and nowhere else. */
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
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                )
            }
            MediaRow(label = label, value = value, onClick = { onCopy(value) })
        }
    }
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
            .padding(16.dp),
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

/* ============================================================
 * COMPONENTS (EXPRESSIVE STYLE)
 * ============================================================ */

@Composable
private fun SectionTitle(title: String) {
    // A grouped-table header: left, small, one tone down, no icon. Centred with a coloured glyph
    // beside it, each heading was competing with the card it introduced - and there are five of
    // them on this sheet, so the page read as five little banners with data squeezed between.
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    // Label and value on one line, the way a table of facts is read: the eye runs down the left
    // for the thing it wants and right for the answer. Stacked - label, then value on its own
    // line under an indent - each fact took two lines and thirty of them took a scroll.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
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


