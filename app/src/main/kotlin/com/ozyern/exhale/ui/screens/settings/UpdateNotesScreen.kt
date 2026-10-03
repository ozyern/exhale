/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.ColorOsType
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.utils.Updater

/**
 * What changed, read in the app the way ColorOS shows an update's notes: the version in a card of
 * its own, then "About this update" — a card that folds open into the release's sections, each a
 * heading and its points. The release text is GitHub's Markdown, read here for its headings and
 * bullets and set as plain type; the page it came from is one row at the foot for anyone who wants
 * the original.
 */
@Composable
fun UpdateNotesScreen(navController: NavController) {
    ColorOsType { UpdateNotesContent(navController) }
}

private sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Point(val text: String) : NoteBlock
    data class Para(val text: String) : NoteBlock
}

private data class NotesState(val version: String, val blocks: List<NoteBlock>, val url: String?)

@Composable
private fun UpdateNotesContent(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    var state by remember { mutableStateOf<NotesState?>(null) }
    var failed by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val releases = Updater.getCachedReleases()
        val latest = Updater.getLatestReleaseInfo().getOrNull()
        val current = BuildConfig.VERSION_NAME
        // A newer release: its notes, as that is what the user is deciding on. Otherwise the notes
        // of the build they are running.
        val chosen = latest?.takeIf { Updater.hasUpdate(it.tagName.removePrefix("v"), current) }
            ?: releases.firstOrNull { Updater.isSameVersion(it.tagName.removePrefix("v"), current) }
            ?: latest
        if (chosen == null) {
            failed = true
            return@LaunchedEffect
        }
        state = NotesState(
            version = chosen.tagName.removePrefix("v"),
            blocks = parseNotes(chosen.body.orEmpty()),
            url = chosen.htmlUrl,
        )
    }

    val cardShape = RoundedCornerShape(26.dp)
    val card = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = top + 8.dp,
            bottom = 32.dp + LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "bar") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                LiquidBackButton(onClick = navController::navigateUp, icon = R.drawable.chevron_back)
                Spacer(Modifier.size(12.dp))
                Text("Notes", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item(key = "version") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(card)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text("Software version", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Exhale_${state?.version ?: BuildConfig.VERSION_NAME}_${BuildConfig.ARCHITECTURE}(${BuildConfig.GIT_COMMIT.take(7)})",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "about") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(card),
            ) {
                val turn by animateFloatAsState(if (expanded) 0f else 180f, label = "notesChevron")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                ) {
                    Text("About this update", fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.expand_less),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(Modifier.height(10.dp))
                        val s = state
                        when {
                            s != null && s.blocks.isNotEmpty() -> s.blocks.forEach { NoteRow(it) }
                            s != null -> NoteRow(NoteBlock.Para("No notes were published with this release."))
                            failed -> NoteRow(NoteBlock.Para("Couldn't load the notes. Check your connection and try again."))
                            else -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                LoadingRing(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }

        state?.url?.let { url ->
            item(key = "source") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(card)
                        .clickable { uriHandler.openUri(url) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text("View release on GitHub", fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.navigate_next), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(block: NoteBlock) {
    when (block) {
        is NoteBlock.Heading -> Text(
            text = block.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        is NoteBlock.Point -> Text(
            text = "• " + block.text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        is NoteBlock.Para -> Text(
            text = block.text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/** GitHub release Markdown, read for its headings, bullets and paragraphs; inline marks dropped. */
private fun parseNotes(markdown: String): List<NoteBlock> {
    fun clean(t: String) = t
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""[*_`]{1,3}([^*_`]+)[*_`]{1,3}"""), "$1")
        .replace(Regex("""<[^>]+>"""), "")
        .trim()
    val out = mutableListOf<NoteBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += NoteBlock.Para(clean(para.toString()))
        para.clear()
    }
    markdown.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() || line.matches(Regex("""^[-*_]{3,}$""")) -> flush()
            line.startsWith("#") -> {
                flush()
                clean(line.trimStart('#')).takeIf { it.isNotBlank() }?.let { out += NoteBlock.Heading(it) }
            }
            line.matches(Regex("""^([-*+•]|\d+[.)])\s+.*""")) -> {
                flush()
                clean(line.replaceFirst(Regex("""^([-*+•]|\d+[.)])\s+"""), ""))
                    .takeIf { it.isNotBlank() }?.let { out += NoteBlock.Point(it) }
            }
            else -> para.append(if (para.isEmpty()) "" else " ").append(line)
        }
    }
    flush()
    return out
}
