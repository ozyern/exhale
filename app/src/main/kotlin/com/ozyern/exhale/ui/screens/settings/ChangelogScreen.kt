/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.constants.UpdateChannel
import com.ozyern.exhale.constants.UpdateChannelKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.MarkdownText
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.NightlyInfo
import com.ozyern.exhale.utils.ReleaseInfo
import com.ozyern.exhale.utils.Updater
import com.ozyern.exhale.utils.VersionChangelog
import androidx.compose.foundation.clickable
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // The current channel
    var updateChannel by remember { mutableStateOf(UpdateChannel.STABLE) }

    // Stable releases
    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }

    // Nightly
    var nightlyInfo by remember { mutableStateOf<NightlyInfo?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load the current channel from DataStore
    LaunchedEffect(Unit) {
        updateChannel = context.dataStore.data.map {
            it[UpdateChannelKey]?.let { value ->
                try {
                    UpdateChannel.valueOf(value)
                } catch (e: Exception) {
                    UpdateChannel.STABLE
                }
            } ?: UpdateChannel.STABLE
        }.first()
    }

    // Loads the content for the channel
    suspend fun loadContent() {
        if (updateChannel == UpdateChannel.NIGHTLY) {
            // Only NIGHTLY uses getLatestReleaseInfo
            Updater.getLatestReleaseInfo().onSuccess { releaseInfo ->
                nightlyInfo = NightlyInfo(
                    versionName = releaseInfo.tagName,
                    apkUrl = "",
                    changelog = releaseInfo.body,
                    publishedAt = releaseInfo.publishedAt
                )
                error = null
            }.onFailure { e ->
                if (nightlyInfo == null) {
                    error = e.message ?: "Error loading nightly info"
                }
            }
            isLoading = false
        } else {
            // STABLE does NOT use getLatestReleaseInfo, only getAllReleases
            Updater.getAllReleases(forceRefresh = true).onSuccess { result ->
                releases = result
                error = null
                isLoading = false
            }.onFailure { e ->
                if (releases.isEmpty()) {
                    error = e.message ?: "Error loading releases"
                }
                isLoading = false
            }
        }
    }

    // Each version's own notes: this build's first, then every earlier one, from the files the
    // APK carries — readable offline. The copies on GitHub replace them as they arrive, so a fix
    // to a version's notes reaches everyone without an update.
    var versionNotes by remember { mutableStateOf(VersionChangelog.all(context)) }
    LaunchedEffect(Unit) {
        val refreshed = versionNotes.map { entry ->
            VersionChangelog.remote(entry.version)?.let { entry.copy(markdown = it) } ?: entry
        }
        versionNotes = refreshed
    }

    // Load again whenever the channel changes
    LaunchedEffect(updateChannel) {
        isLoading = true
        error = null

        if (updateChannel == UpdateChannel.STABLE) {
            // Stable releases try the cache first
            val cachedReleases = Updater.getCachedReleases()
            if (cachedReleases.isNotEmpty()) {
                releases = cachedReleases
                isLoading = false
            }
        }

        loadContent()
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = {
                    Text(
                        if (updateChannel == UpdateChannel.NIGHTLY)
                            "Nightly Changelog"
                        else
                            stringResource(R.string.changelog)
                    )
                },
                navigationIcon = {
                    LiquidBackButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        icon = R.drawable.chevron_back,
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            when {
                // Stable builds read their own notes, bundled, so there is nothing to wait for.
                updateChannel == UpdateChannel.STABLE && versionNotes.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp + paddingValues.calculateTopPadding())) }
                        items(versionNotes, key = { it.version }) { entry ->
                            VersionNotesCard(
                                entry = entry,
                                isCurrent = Updater.isSameVersion(entry.version, VersionChangelog.currentVersion),
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }

                isLoading -> {
                    LoadingRing(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null &&
                        ((updateChannel == UpdateChannel.STABLE && releases.isEmpty()) ||
                                (updateChannel == UpdateChannel.NIGHTLY && nightlyInfo == null)) -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading changelog: ${error ?: "Unknown error"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            isLoading = true
                            error = null
                            coroutineScope.launch {
                                loadContent()
                            }
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                updateChannel == UpdateChannel.NIGHTLY && nightlyInfo != null -> {
                    // Nightly content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp + paddingValues.calculateTopPadding())) }

                        item {
                            NightlyChangelogCard(nightlyInfo = nightlyInfo!!)
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }

                updateChannel == UpdateChannel.STABLE && releases.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_releases),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                updateChannel == UpdateChannel.STABLE -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp + paddingValues.calculateTopPadding())) }

                        items(releases) { release ->
                            ReleaseCard(release = release)
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@SuppressLint("NonObservableLocale")
@Composable
private fun NightlyChangelogCard(nightlyInfo: NightlyInfo) {
    // Accepts dd-MM-yyyy too (as in "30-05-2026")
    val inputDateFormat1 = SimpleDateFormat("dd-MM-yyyy", LocalLocale.current.platformLocale)
    val inputDateFormat2 = SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale)
    val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", LocalLocale.current.platformLocale)

    val formattedDate = remember(nightlyInfo.publishedAt) {
        try {
            // Try dd-MM-yyyy first
            val date = inputDateFormat1.parse(nightlyInfo.publishedAt)
                ?: inputDateFormat2.parse(nightlyInfo.publishedAt)
            date?.let { displayDateFormat.format(it) } ?: nightlyInfo.publishedAt
        } catch (e: Exception) {
            nightlyInfo.publishedAt
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nightly ${nightlyInfo.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The changelog, rendered as Markdown
            if (!nightlyInfo.changelog.isNullOrBlank()) {
                MarkdownText(
                    markdown = nightlyInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "No changelog available for this nightly build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One version's notes. The version this build is opens by itself and says so; earlier versions
 * start folded to their first paragraph and open on a tap.
 */
@Composable
private fun VersionNotesCard(entry: VersionChangelog.Entry, isCurrent: Boolean) {
    var open by remember(entry.version) { mutableStateOf(isCurrent) }
    // The file's own "# Exhale x.y.z" heading is the card's title; the body is the rest.
    val body = remember(entry.markdown) {
        entry.markdown.lines().dropWhile { it.isBlank() }.let { lines ->
            if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines
        }.joinToString("\n").trim()
    }
    val preview = remember(body) {
        body.split(Regex("\n\\s*\n")).firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("_") }.orEmpty()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent) { open = !open },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrent) 0.45f else 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exhale ${entry.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isCurrent) "This version" else if (open) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(
                markdown = if (open) body else preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseInfo) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    val formattedDate = remember(release.publishedAt) {
        try {
            val date = dateFormat.parse(release.publishedAt.substring(0, 10))
            date?.let { displayDateFormat.format(it) } ?: release.publishedAt
        } catch (e: Exception) {
            release.publishedAt
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.name.ifBlank { release.tagName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!release.body.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                MarkdownText(
                    markdown = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}