/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.innertube.SearchFilter
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.constants.ContentLanguageKey
import com.ozyern.exhale.constants.PreferredArtistsKey
import com.ozyern.exhale.constants.PreferredAudioLanguagesKey
import com.ozyern.exhale.constants.SYSTEM_DEFAULT
import com.ozyern.exhale.constants.SongPreferencesCompletedKey
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.IosSearchField
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.component.IosButton
import com.ozyern.exhale.ui.ios.component.IosButtonBorderShape
import com.ozyern.exhale.ui.ios.component.IosButtonSizing
import com.ozyern.exhale.ui.ios.component.IosButtonStyle
import com.ozyern.exhale.ui.ios.foundation.IosControlSize
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.iosRowPress
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Two-step song preferences: languages, then artists suggested for those languages (plus any
 * found by search). Choices are held here and written to DataStore only on Done, so leaving the
 * artist step never saves half a set. [onFinished] lets the host decide where to go afterwards.
 */
@Composable
fun SongPreferencesScreen(
    navController: NavController,
    onFinished: () -> Unit = { navController.navigateUp() },
) {
    val (_, setCompleted) = rememberPreference(SongPreferencesCompletedKey, defaultValue = false)
    val (savedLanguagesCsv, setLanguagesCsv) = rememberPreference(PreferredAudioLanguagesKey, defaultValue = "")
    val (savedArtistsCsv, setArtistsCsv) = rememberPreference(PreferredArtistsKey, defaultValue = "")
    val (contentLanguage, setContentLanguage) = rememberPreference(ContentLanguageKey, defaultValue = SYSTEM_DEFAULT)

    var step by rememberSaveable { mutableStateOf(0) }
    val selectedLanguages = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    // Picks that came from search rather than the suggestions; kept across a trip back to step one.
    val searchedArtists = remember { mutableStateListOf<OnboardingArtist>() }

    // Reopened from Settings, the screen starts from what was saved last time.
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedLanguagesCsv, savedArtistsCsv) {
        if (seeded) return@LaunchedEffect
        seeded = true
        savedLanguagesCsv.splitCsv().filter { code -> OnboardingLanguages.any { it.code == code } }
            .forEach { if (it !in selectedLanguages) selectedLanguages.add(it) }
        val suggested = artistsForLanguages(selectedLanguages).mapTo(HashSet()) { it.name }
        savedArtistsCsv.splitCsv().forEach { name ->
            if (name !in selectedArtists) selectedArtists.add(name)
            if (name !in suggested && searchedArtists.none { it.name == name }) {
                searchedArtists.add(OnboardingArtist(name = name, imageUrl = null, languageCode = ""))
            }
        }
    }

    val suggestedArtists = remember(selectedLanguages.toList()) { artistsForLanguages(selectedLanguages) }

    Box(
        Modifier
            .fillMaxSize()
            .background(IosTheme.colors.systemGroupedBackground)
            // This screen has no app bar, so its slot is not reserved above the content.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.exclude(WindowInsets(top = AppBarHeight))),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // A navigation push: the new page slides in over the old, which drifts a third
                // of the way under it and dims; going back runs it in reverse.
                val forward = targetState > initialState
                val slide = tween<IntOffset>(PUSH_MS, easing = PushEasing)
                val fade = tween<Float>(PUSH_MS, easing = PushEasing)
                if (forward) {
                    slideInHorizontally(slide) { it } togetherWith
                        (slideOutHorizontally(slide) { -it / 3 } + fadeOut(fade, targetAlpha = 0.6f))
                } else {
                    (slideInHorizontally(slide) { -it / 3 } + fadeIn(fade, initialAlpha = 0.6f)) togetherWith
                        slideOutHorizontally(slide) { it }
                }.apply { targetContentZIndex = if (forward) 1f else 0f }
            },
            label = "songPreferencesStep",
            // A phone's measure on tablets and unfolded foldables.
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
        ) { current ->
            if (current == 0) {
                LanguageStep(
                    selected = selectedLanguages,
                    onToggle = { code -> if (!selectedLanguages.remove(code)) selectedLanguages.add(code) },
                    onContinue = { step = 1 },
                    // Only the fact that it was seen is saved: the app works without either choice.
                    onSkip = {
                        setCompleted(true)
                        onFinished()
                    },
                )
            } else {
                ArtistStep(
                    suggested = suggestedArtists,
                    searched = searchedArtists,
                    selected = selectedArtists,
                    onToggle = { artist ->
                        if (!selectedArtists.remove(artist.name)) {
                            selectedArtists.add(artist.name)
                            val isSuggested = suggestedArtists.any { it.name == artist.name }
                            if (!isSuggested && searchedArtists.none { it.name == artist.name }) {
                                searchedArtists.add(artist)
                            }
                        }
                    },
                    onBack = { step = 0 },
                    onDone = {
                        // Only artists still offered for the final languages, or found by search.
                        val valid = suggestedArtists.mapTo(HashSet()) { it.name }
                            .apply { searchedArtists.forEach { add(it.name) } }
                        setLanguagesCsv(selectedLanguages.joinToString(","))
                        setArtistsCsv(selectedArtists.filter { it in valid }.joinToString(","))
                        // The first language becomes the content language unless one was chosen.
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

private fun String.splitCsv(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private const val PUSH_MS = 400
private val PushEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Composable
private fun LanguageStep(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepToolbar(trailingLabel = "Not Now", onTrailing = onSkip)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "title", contentType = "title") {
                StepTitle(
                    symbol = "globe",
                    title = "Choose Languages",
                    subtitle = "Your recommendations follow the languages you pick. You can change them later in Settings.",
                )
            }
            itemsIndexed(OnboardingLanguages, key = { _, lang -> lang.code }, contentType = { _, _ -> "language" }) { index, lang ->
                LanguageRow(
                    language = lang,
                    selected = lang.code in selected,
                    position = groupPosition(index, OnboardingLanguages.size),
                    onClick = { onToggle(lang.code) },
                )
            }
        }
        StepFooter(
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

private enum class GroupPosition { Single, First, Middle, Last }

private fun groupPosition(index: Int, count: Int) = when {
    count == 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == count - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

/**
 * One row of the inset grouped card. Each row draws its own slice of the card so the list stays
 * lazy: only the ends are rounded, and every row but the last carries a hairline inset to the text.
 */
@Composable
private fun LanguageRow(
    language: OnboardingLanguage,
    selected: Boolean,
    position: GroupPosition,
    onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    val haptic = LocalHapticFeedback.current
    val radius = IosMetrics.GroupedCornerRadius
    val shape = when (position) {
        GroupPosition.Single -> ContinuousRoundedRectangle(radius)
        GroupPosition.First -> ContinuousRoundedRectangle(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
        GroupPosition.Last -> ContinuousRoundedRectangle(topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius)
        GroupPosition.Middle -> ContinuousRoundedRectangle(0.dp)
    }
    val check by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "languageCheck",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondarySystemGroupedBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .iosRowPress(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                })
                .semantics(mergeDescendants = true) { this.selected = selected }
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
                    style = IosTheme.typography.body,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The language in its own script, so the people who read it find it.
                if (language.nativeName != language.displayName) {
                    Text(
                        text = language.nativeName,
                        style = IosTheme.typography.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
            SymbolIcon(
                symbol = sf("checkmark"),
                contentDescription = null,
                tint = IosTheme.tint,
                size = 18.dp,
                modifier = Modifier.graphicsLayer {
                    alpha = check.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * check
                    scaleY = 0.6f + 0.4f * check
                },
            )
        }
        if (position == GroupPosition.First || position == GroupPosition.Middle) {
            Box(
                Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

@Composable
private fun ArtistStep(
    suggested: List<OnboardingArtist>,
    searched: List<OnboardingArtist>,
    selected: List<String>,
    onToggle: (OnboardingArtist) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    // Artwork found by name, seeded from the process cache so revisiting the step is instant.
    val resolvedImages = remember {
        mutableStateMapOf<String, String?>().apply {
            (suggested + searched).forEach { artist ->
                val known = artist.imageUrl
                    ?: OnboardingArtistImageCache.get(artist.name).takeIf { OnboardingArtistImageCache.contains(artist.name) }
                if (known != null) put(artist.name, known)
            }
        }
    }

    // Six lookups at a time; each is a search round trip. Results land on the main thread, the
    // only thread that touches the cache.
    LaunchedEffect(suggested, searched.size) {
        val gate = Semaphore(6)
        coroutineScope {
            for (artist in suggested + searched) {
                val name = artist.name
                if (artist.imageUrl != null || resolvedImages.containsKey(name)) continue
                if (OnboardingArtistImageCache.contains(name)) {
                    OnboardingArtistImageCache.get(name)?.let { resolvedImages[name] = it }
                    continue
                }
                launch {
                    val thumbnail = gate.withPermit {
                        withContext(Dispatchers.IO) {
                            YouTube.search(name, SearchFilter.FILTER_ARTIST).getOrNull()
                                ?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()?.thumbnail
                        }
                    }
                    OnboardingArtistImageCache.put(name, thumbnail)
                    if (thumbnail != null) resolvedImages[name] = thumbnail
                }
            }
        }
    }

    // Search for anyone the suggestions do not have; debounced to one request per pause.
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
            YouTube.search(q, SearchFilter.FILTER_ARTIST).getOrNull()
                ?.items?.filterIsInstance<ArtistItem>()
                ?.take(12)
                ?.map { OnboardingArtist(name = it.title, imageUrl = it.thumbnail, languageCode = "") }
                // Names key the grid and are what is saved; two artists can share one.
                ?.distinctBy { it.name }
                .orEmpty()
        }
        searching = false
    }

    val showingResults = query.trim().length >= 2
    val suggestedNames = remember(suggested) { suggested.mapTo(HashSet()) { it.name } }
    // Searching, the grid is the results; otherwise the suggestions, with searched picks first so
    // they stay visible and can be undone.
    val gridArtists = if (showingResults) results else searched.filter { it.name !in suggestedNames } + suggested

    Column(Modifier.fillMaxSize()) {
        StepToolbar(onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                Column {
                    StepTitle(
                        symbol = "music.mic",
                        title = "Pick Your Artists",
                        subtitle = "Tap the artists you love. Tap again to undo.",
                    )
                    IosSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search Artists",
                        modifier = Modifier.padding(bottom = 8.dp),
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
                        style = IosTheme.typography.body,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    )
                }
            }
            items(gridArtists, key = { it.name }, contentType = { "artist" }) { artist ->
                ArtistBubble(
                    artist = artist,
                    imageUrl = artist.imageUrl ?: resolvedImages[artist.name],
                    selected = artist.name in selected,
                    onClick = { onToggle(artist) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        StepFooter(
            primaryLabel = "Done",
            primaryEnabled = true,
            onPrimary = onDone,
            hint = when (selected.size) {
                0 -> "Optional"
                1 -> "1 artist"
                else -> "${selected.size} artists"
            },
        )
    }
}

/**
 * An artist in a circle. Picked, a ring in the accent closes around it and a check badge pops
 * in at its corner; under the finger it sinks a little and springs back.
 */
@Composable
private fun ArtistBubble(
    artist: OnboardingArtist,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val accent = IosTheme.tint
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "artistPress",
    )
    val pick by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "artistPick",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = artist.name
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The picture steps in from the ring as it closes, so the two never touch.
                    .padding(5.dp * pick.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(colors.tertiarySystemFill),
                contentAlignment = Alignment.Center,
            ) {
                // The initial underneath, so a bubble is never empty while its artwork loads.
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = IosTheme.typography.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondaryLabel,
                )
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // A soft shade under the badge corner keeps the check legible over bright art.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pick.coerceIn(0f, 1f) }
                        .background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))),
                )
            }
            if (pick > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pick.coerceIn(0f, 1f) }
                        .border(width = 2.5.dp, color = accent, shape = CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(28.dp)
                        .graphicsLayer {
                            alpha = pick.coerceIn(0f, 1f)
                            scaleX = 0.4f + 0.6f * pick
                            scaleY = 0.4f + 0.6f * pick
                        }
                        .clip(CircleShape)
                        .background(colors.systemGroupedBackground)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(
                        symbol = sf("checkmark"),
                        contentDescription = null,
                        tint = colors.onAccent,
                        size = 12.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = IosTheme.typography.footnote,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Controls in place of a bar: a back chevron on the left, a text action on the right. Solid, not
 * glass: glass here sampled a backdrop holding this screen, blurring the buttons into themselves.
 */
@Composable
private fun StepToolbar(
    onBack: (() -> Unit)? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IosButton(
                onClick = onBack,
                style = IosButtonStyle.Bordered,
                borderShape = IosButtonBorderShape.Circle,
                controlSize = IosControlSize.Large,
            ) {
                SymbolIcon(symbol = sf("chevron.left"), contentDescription = "Back", size = 18.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailingLabel != null && onTrailing != null) {
            IosButton(
                title = trailingLabel,
                onClick = onTrailing,
                style = IosButtonStyle.Bordered,
                controlSize = IosControlSize.Large,
            )
        }
    }
}

@Composable
private fun StepTitle(symbol: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(IosTheme.tint),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(
                symbol = sf(symbol),
                contentDescription = null,
                tint = IosTheme.colors.onAccent,
                size = 30.dp,
                bounceOnEnter = true,
                enterKey = "songPreferences:$symbol",
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = IosTheme.typography.largeTitle,
            color = IosTheme.colors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = IosTheme.typography.body,
            color = IosTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** The count of what is picked over one prominent capsule that fills the width. */
@Composable
private fun StepFooter(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    hint: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = hint,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "songPreferencesHint",
        ) { text ->
            Text(
                text = text,
                style = IosTheme.typography.footnote,
                color = IosTheme.colors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(10.dp))
        IosButton(
            title = primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            style = IosButtonStyle.BorderedProminent,
            sizing = IosButtonSizing.Flexible,
            controlSize = IosControlSize.ExtraLarge,
        )
    }
}
