/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ozyern.exhale.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.ozyern.exhale.constants.ContentLanguageKey
import com.ozyern.exhale.constants.PreferredArtistsKey
import com.ozyern.exhale.constants.PreferredAudioLanguagesKey
import com.ozyern.exhale.constants.SYSTEM_DEFAULT
import com.ozyern.exhale.constants.SongPreferencesCompletedKey
import com.ozyern.exhale.utils.rememberPreference

/**
 * Two-step "Song Preferences" onboarding.
 *
 * Step 1 (Language) → Step 2 (contextual artists derived from the chosen languages). State is
 * hoisted here and only committed to DataStore on finish, so backing out of Step 2 never leaves
 * a half-written preference. The step swap uses [AnimatedContent] with a directional slide so it
 * reads as a forward/back page turn.
 *
 * [onFinished] lets a host (first-launch gate in MainActivity, or a Settings push) decide what to
 * do after completion — pop the back stack, or swap the start destination.
 */
@Composable
fun SongPreferencesScreen(
    navController: NavController,
    onFinished: () -> Unit = { navController.navigateUp() },
) {
    val (_, setCompleted) = rememberPreference(SongPreferencesCompletedKey, defaultValue = false)
    val (_, setLanguagesCsv) = rememberPreference(PreferredAudioLanguagesKey, defaultValue = "")
    val (_, setArtistsCsv) = rememberPreference(PreferredArtistsKey, defaultValue = "")
    val (contentLanguage, setContentLanguage) = rememberPreference(ContentLanguageKey, defaultValue = SYSTEM_DEFAULT)

    var step by rememberSaveable { mutableStateOf(0) }
    val selectedLanguages = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    // Artists picked from search rather than from the curated roster. Kept here, not in the step,
    // so they survive a trip back to Step 1 and are saved alongside the curated picks.
    val searchedArtists = remember { mutableStateListOf<OnboardingArtist>() }

    // Artists contextual to the current language picks; drop any stale selections when languages change.
    val contextualArtists = remember(selectedLanguages.toList()) {
        artistsForLanguages(selectedLanguages)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val enterOffset: (Int) -> Int = { if (forward) it else -it }
                val exitOffset: (Int) -> Int = { if (forward) -it else it }
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f), enterOffset) +
                    fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220), exitOffset) + fadeOut(tween(160)))
            },
            label = "onboardingStep",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                0 -> LanguageStep(
                    selected = selectedLanguages,
                    onToggle = { code ->
                        if (!selectedLanguages.remove(code)) selectedLanguages.add(code)
                    },
                    onContinue = { step = 1 },
                    // Nothing saved but the fact that it was seen: the app works without either
                    // preference, and a first launch should never be a form you cannot leave.
                    onSkip = {
                        setCompleted(true)
                        onFinished()
                    },
                )

                else -> ArtistStep(
                    artists = contextualArtists,
                    searched = searchedArtists,
                    selected = selectedArtists,
                    onToggle = { artist ->
                        if (!selectedArtists.remove(artist.name)) {
                            selectedArtists.add(artist.name)
                            val curated = contextualArtists.any { it.name == artist.name }
                            if (!curated && searchedArtists.none { it.name == artist.name }) {
                                searchedArtists.add(artist)
                            }
                        }
                    },
                    onBack = { step = 0 },
                    onFinish = {
                        // Commit only artists still valid for the final language set, so backing
                        // out a language never persists an orphaned pick.
                        val validArtistNames = contextualArtists.mapTo(HashSet()) { it.name }
                            .apply { searchedArtists.forEach { add(it.name) } }
                        val committedArtists = selectedArtists.filter { it in validArtistNames }
                        setLanguagesCsv(selectedLanguages.joinToString(","))
                        setArtistsCsv(committedArtists.joinToString(","))
                        // Seed content language from the first pick if the user hasn't set one.
                        // Both sentinels have been written to this key over time — the rest of
                        // the app uses SYSTEM_DEFAULT, this screen used to use "system". Matching
                        // only one of them meant a user who had ever chosen "System default" in
                        // Settings could pick a language here and never have it take effect.
                        selectedLanguages.firstOrNull()?.let { first ->
                            if (contentLanguage == SYSTEM_DEFAULT || contentLanguage == "system") {
                                setContentLanguage(first)
                            }
                        }
                        setCompleted(true)
                        onFinished()
                    },
                )
            }
        }
    }
}

/* --------------------------------------------------------------------- */
/* Step 1 — Language                                                     */
/* --------------------------------------------------------------------- */

@Composable
private fun LanguageStep(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OnboardingHeader(
            step = 1,
            title = "What do you like to listen to?",
            subtitle = "Choose your preferred audio languages. You can change these anytime in Settings.",
        )

        // Scrolls: sixteen chips do not fit above the footer on a small phone, and a list that
        // cannot scroll just hides its last row behind the button.
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OnboardingLanguages.forEach { lang ->
                LanguageChip(
                    language = lang,
                    selected = lang.code in selected,
                    onClick = { onToggle(lang.code) },
                )
            }
        }

        OnboardingFooter(
            primaryLabel = "Continue",
            primaryEnabled = selected.isNotEmpty(),
            onPrimary = onContinue,
            hint = if (selected.isEmpty()) "Pick at least one language" else "${selected.size} selected",
            secondaryLabel = "Not now",
            onSecondary = onSkip,
        )
    }
}

@Composable
private fun LanguageChip(
    language: OnboardingLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "chipScale",
    )
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = language.flag, fontSize = 18.sp)
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
        )
        // The language in its own script, so it is recognisable to the person who reads it.
        if (language.nativeName != language.displayName) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.7f),
            )
        }
        if (selected) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* --------------------------------------------------------------------- */
/* Step 2 — Contextual artists                                           */
/* --------------------------------------------------------------------- */

@Composable
private fun ArtistStep(
    artists: List<OnboardingArtist>,
    searched: List<OnboardingArtist>,
    selected: List<String>,
    onToggle: (OnboardingArtist) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    // Resolved artwork URLs keyed by artist name. Seeded from the process cache so returning to
    // Step 2 (or backing in/out of a language) shows already-fetched art instantly without a
    // network round-trip.
    val resolvedImages = remember {
        mutableStateMapOf<String, String?>().apply {
            artists.forEach { a ->
                val cached = if (a.imageUrl != null) a.imageUrl
                else if (OnboardingArtistImageCache.contains(a.name)) OnboardingArtistImageCache.get(a.name)
                else null
                if (cached != null) put(a.name, cached)
            }
        }
    }

    // Fetch any still-unresolved artwork from InnerTube (artist search → first ArtistItem thumb).
    // Keyed on the artist name-set so a changed language selection re-triggers only the new names.
    //
    // Six at a time, not one after another: each is a search round trip, and in sequence a grid
    // of two dozen artists filled in one circle at a time for most of a minute. Results land on
    // the main thread, which is the only thread that touches the cache.
    LaunchedEffect(artists) {
        val gate = Semaphore(6)
        coroutineScope {
            for (artist in artists) {
                val name = artist.name
                if (artist.imageUrl != null || resolvedImages.containsKey(name)) continue
                if (OnboardingArtistImageCache.contains(name)) {
                    OnboardingArtistImageCache.get(name)?.let { resolvedImages[name] = it }
                    continue
                }
                launch {
                    val thumb = gate.withPermit {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                                    ?.items?.filterIsInstance<ArtistItem>()
                                    ?.firstOrNull()
                                    ?.thumbnail
                            }.getOrNull()
                        }
                    }
                    OnboardingArtistImageCache.put(name, thumb)
                    if (thumb != null) resolvedImages[name] = thumb
                }
            }
        }
    }

    // Search, for anyone the curated roster does not have. Debounced, so typing a name is one
    // request rather than one per letter.
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OnboardingArtist>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        results = withContext(Dispatchers.IO) {
            runCatching {
                YouTube.search(q, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    ?.items?.filterIsInstance<ArtistItem>()
                    ?.take(12)
                    ?.map { OnboardingArtist(name = it.title, imageUrl = it.thumbnail, languageCode = "") }
                    // Names are the grid's keys and the saved value; two artists can share one.
                    ?.distinctBy { it.name }
            }.getOrNull().orEmpty()
        }
        searching = false
    }

    // While searching, the grid is the results. Otherwise it is the curated roster, with anything
    // already picked from search at the front so those picks stay visible and can be undone.
    val showingResults = query.trim().length >= 2
    val curatedNames = remember(artists) { artists.mapTo(HashSet()) { it.name } }
    val gridArtists =
        if (showingResults) results
        else searched.filter { it.name !in curatedNames } + artists

    Column(Modifier.fillMaxSize()) {
        OnboardingHeader(
            step = 2,
            title = "Pick a few artists you love",
            subtitle = "We'll tune your recommendations around them.",
            onBack = onBack,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search for an artist") },
            leadingIcon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.close),
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(percent = 50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (gridArtists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        showingResults && searching -> "Searching…"
                        showingResults -> "No artists found for “${query.trim()}”."
                        else -> "No trending artists for that selection yet.\nSearch above, or tap Done to continue."
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showingResults) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                        Text(
                            text = if (searching) "Searching…" else "Results",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                items(gridArtists, key = { it.name }, contentType = { "artist_tile" }) { artist ->
                    ArtistTile(
                        artist = artist,
                        imageUrl = artist.imageUrl ?: resolvedImages[artist.name],
                        selected = artist.name in selected,
                        onClick = { onToggle(artist) },
                    )
                }
            }
        }

        OnboardingFooter(
            primaryLabel = "Done",
            primaryEnabled = true,
            onPrimary = onFinish,
            hint = if (selected.isEmpty()) "Optional — pick any you like" else "${selected.size} selected",
        )
    }
}

@Composable
private fun ArtistTile(
    artist: OnboardingArtist,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Buttery spring physics shared across the app (dampingRatio 0.8, stiffness 300).
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "artistScale",
    )
    // Selection ring fades + scales in as one crisp 3dp outline with a clean 3dp gap to
    // the artwork (never a border painted OVER the image edge — no cropping artifacts).
    val ringAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "artistRing",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            // High-contrast selection outline — drawn on the OUTER box so it never
            // overlaps (or crops into) the circular artwork.
            if (ringAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ringAlpha }
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }

            // Artwork disc, inset from the ring by a constant gap so selection reads as
            // a clean halo, exactly like Apple Music's artist picker.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                // Monogram sits underneath as the base layer, so a tile is never empty while
                // artwork is still resolving; the circular AsyncImage crossfades in on top.
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(imageUrl)
                            .memoryCacheKey(imageUrl)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                }
            }

            // Selected check badge, bottom-end, springs in over the ring.
            val badge by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "badge",
            )
            if (badge > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .scale(badge)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* --------------------------------------------------------------------- */
/* Shared chrome                                                         */
/* --------------------------------------------------------------------- */

@Composable
private fun OnboardingHeader(
    step: Int,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                LiquidBackButton(
                    onClick = onBack,
                    contentDescription = "Back",
                )
                Spacer(Modifier.width(12.dp))
            }
            // Two segments, filled up to this step: where you are and how much is left, at a glance.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i ->
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (i < step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            ),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Step $step of 2",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingFooter(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    hint: String,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
