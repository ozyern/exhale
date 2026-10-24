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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.ui.screens.settings.SettingsDimensions
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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        // Centred at a phone's measure on a tablet or an unfolded foldable, so the chips wrap into
        // a block and Continue is a button rather than a 1000dp bar.
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // A UIKit push: the new page comes in from the edge over the old one, which slides a
                // third of the way under it and dims; going back runs it in reverse.
                val forward = targetState > initialState
                val curve = tween<androidx.compose.ui.unit.IntOffset>(OnboardingPushMs, easing = OnboardingPushEasing)
                val fade = tween<Float>(OnboardingPushMs, easing = OnboardingPushEasing)
                if (forward) {
                    slideInHorizontally(curve) { it } togetherWith
                        (slideOutHorizontally(curve) { -it / 3 } + fadeOut(fade, targetAlpha = 0.6f))
                } else {
                    (slideInHorizontally(curve) { -it / 3 } + fadeIn(fade, initialAlpha = 0.6f)) togetherWith
                        slideOutHorizontally(curve) { it }
                }.apply { targetContentZIndex = if (forward) 1f else 0f }
            },
            label = "onboardingStep",
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
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

private const val OnboardingPushMs = 400
private val OnboardingPushEasing = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

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
        OnboardingNavBar(trailingLabel = "Not Now", onTrailing = onSkip)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            item(key = "title") {
                OnboardingTitle(
                    title = "Choose Languages",
                    subtitle = "Pick the languages you listen to. Your recommendations follow them, and you can change them later in Settings.",
                )
            }
            item(key = "group") {
                // One grouped inset list, as iOS picks a language: a row each, a checkmark on the
                // ones chosen, hairlines inset to the label.
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)),
                ) {
                    OnboardingLanguages.forEachIndexed { index, lang ->
                        LanguageRow(
                            language = lang,
                            selected = lang.code in selected,
                            onClick = { onToggle(lang.code) },
                            showDivider = index < OnboardingLanguages.lastIndex,
                        )
                    }
                }
            }
        }
        OnboardingFooter(
            primaryLabel = "Continue",
            primaryEnabled = selected.isNotEmpty(),
            onPrimary = onContinue,
            hint = when (selected.size) {
                0 -> "Choose at least one"
                1 -> "1 language"
                else -> "${selected.size} languages"
            },
        )
    }
}

@Composable
private fun LanguageRow(
    language: OnboardingLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A pressed row fills edge to edge with a neutral wash and does not move.
    val wash by animateFloatAsState(if (pressed) 0.09f else 0f, tween(if (pressed) 0 else 220), label = "rowWash")
    val check by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "rowCheck",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
                .clickable(interactionSource = interaction, indication = null) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Text(text = language.flag, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The language in its own script, so the person who reads it finds it.
                if (language.nativeName != language.displayName) {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.check),
                contentDescription = if (selected) "Selected" else null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = check.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * check
                        scaleY = 0.6f + 0.4f * check
                    },
            )
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp),
                thickness = SettingsDimensions.DividerThickness,
                color = SettingsDimensions.dividerColor(),
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
    // Step 2 (or backing in/out of a language) shows already-fetched art instantly.
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

    // Six at a time, not one after another: each is a search round trip. Results land on the main
    // thread, which is the only thread that touches the cache.
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
        OnboardingNavBar(leadingLabel = "Back", onLeading = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                Column {
                    OnboardingTitle(
                        title = "Tell Us What You Like",
                        subtitle = "Tap the artists you love. Tap again to undo.",
                        horizontalPadding = 0.dp,
                    )
                    IosSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Artists",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (gridArtists.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    Text(
                        text = when {
                            showingResults && searching -> "Searching…"
                            showingResults -> "No Results for “${query.trim()}”"
                            else -> "No suggestions for these languages yet. Search for an artist, or tap Done."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    )
                }
            }
            items(gridArtists, key = { it.name }, contentType = { "artist_tile" }) { artist ->
                ArtistTile(
                    artist = artist,
                    imageUrl = artist.imageUrl ?: resolvedImages[artist.name],
                    selected = artist.name in selected,
                    onClick = { onToggle(artist) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        OnboardingFooter(
            primaryLabel = "Done",
            primaryEnabled = true,
            onPrimary = onFinish,
            hint = when (selected.size) {
                0 -> "Optional"
                1 -> "1 artist"
                else -> "${selected.size} artists"
            },
        )
    }
}

/**
 * One artist bubble, as Apple Music's picker draws it: the artwork in a circle, and picked, the
 * circle fills with the accent and a white check, with a little bounce under the finger.
 */
@Composable
private fun ArtistTile(
    artist: OnboardingArtist,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pressed it sinks a little; let go it springs back past full size and settles.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "artistScale",
    )
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 520f),
        label = "artistFill",
    )
    val accent = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(interactionSource = interaction, indication = null) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            // The initial underneath, so a bubble is never empty while its artwork loads.
            Text(
                text = artist.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (fill > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.82f * fill.coerceIn(0f, 1f))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                alpha = fill.coerceIn(0f, 1f)
                                scaleX = 0.5f + 0.5f * fill
                                scaleY = 0.5f + 0.5f * fill
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* --------------------------------------------------------------------- */
/* Shared chrome                                                         */
/* --------------------------------------------------------------------- */

/** An iOS navigation bar: a plain text button either side, in the accent, and nothing else. */
@Composable
private fun OnboardingNavBar(
    leadingLabel: String? = null,
    onLeading: (() -> Unit)? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingLabel != null && onLeading != null) {
            NavBarButton(onClick = onLeading) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.chevron_back),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(leadingLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailingLabel != null && onTrailing != null) {
            NavBarButton(onClick = onTrailing) {
                Text(trailingLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NavBarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // iOS bar buttons dim while held rather than showing a ripple.
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer { alpha = if (pressed) 0.35f else 1f }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The page's large title and its explanation, at iOS's sizes: 34 bold over 15 grey. */
@Composable
private fun OnboardingTitle(
    title: String,
    subtitle: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Column(Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp, bottom = 20.dp)) {
        Text(
            text = title,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** iOS's search field: a grey rounded field 36dp tall, the glass inside it, and a clear button. */
@Composable
private fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.search),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 17.sp, color = ink, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(ink.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.close),
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

/** The one filled control: a full-width 52dp capsule in the accent, with a quiet count above it. */
@Composable
private fun OnboardingFooter(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    hint: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.75f, stiffness = 1220f), label = "primaryPress")
    val enabledAlpha by animateFloatAsState(if (primaryEnabled) 1f else 0.4f, tween(200), label = "primaryEnabled")
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                    alpha = enabledAlpha
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = interaction, indication = null, enabled = primaryEnabled, onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = primaryLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
