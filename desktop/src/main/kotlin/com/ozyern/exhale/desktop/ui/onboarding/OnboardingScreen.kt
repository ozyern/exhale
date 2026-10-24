/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.PreferredArtist
import com.ozyern.exhale.desktop.ui.AmbientGlow
import com.ozyern.exhale.desktop.ui.ExhaleAccent
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.glass.LocalPageBackdrop
import com.ozyern.exhale.desktop.ui.glass.PageBackdropHost
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private data class OnboardingLanguage(val code: String, val displayName: String, val nativeName: String)

/** Same headline set as the Android onboarding. */
private val OnboardingLanguages = listOf(
    OnboardingLanguage("en", "English", "English"),
    OnboardingLanguage("hi", "Hindi", "हिन्दी"),
    OnboardingLanguage("es", "Spanish", "Español"),
    OnboardingLanguage("ko", "Korean", "한국어"),
    OnboardingLanguage("ja", "Japanese", "日本語"),
    OnboardingLanguage("pt", "Portuguese", "Português"),
    OnboardingLanguage("fr", "French", "Français"),
    OnboardingLanguage("de", "German", "Deutsch"),
    OnboardingLanguage("ar", "Arabic", "العربية"),
    OnboardingLanguage("it", "Italian", "Italiano"),
    OnboardingLanguage("ru", "Russian", "Русский"),
    OnboardingLanguage("tr", "Turkish", "Türkçe"),
    OnboardingLanguage("id", "Indonesian", "Bahasa Indonesia"),
    OnboardingLanguage("th", "Thai", "ไทย"),
    OnboardingLanguage("zh", "Chinese", "中文"),
    OnboardingLanguage("pl", "Polish", "Polski"),
)

/** Same rosters as the Android onboarding's TrendingArtistsByLanguage. */
private val TrendingArtistsByLanguage: Map<String, List<String>> = mapOf(
    "en" to listOf("Sabrina Carpenter", "Taylor Swift", "The Weeknd", "Billie Eilish", "Drake", "Ariana Grande", "Kendrick Lamar", "Dua Lipa", "Post Malone", "Olivia Rodrigo", "Ed Sheeran", "Bruno Mars"),
    "hi" to listOf("Arijit Singh", "Shreya Ghoshal", "A.R. Rahman", "Neha Kakkar", "Pritam", "Badshah", "Jubin Nautiyal", "Sonu Nigam", "Diljit Dosanjh", "Sunidhi Chauhan"),
    "es" to listOf("Bad Bunny", "Karol G", "Peso Pluma", "Shakira", "Rauw Alejandro", "Feid", "Rosalía", "J Balvin", "Maluma"),
    "ko" to listOf("BTS", "BLACKPINK", "NewJeans", "SEVENTEEN", "Stray Kids", "IVE", "aespa", "TWICE", "IU", "LE SSERAFIM"),
    "ja" to listOf("YOASOBI", "Kenshi Yonezu", "Ado", "Fujii Kaze", "Official HIGE DANdism", "King Gnu", "Aimer", "LiSA"),
    "pt" to listOf("Anitta", "Luísa Sonza", "Marília Mendonça", "Henrique & Juliano", "Jorge & Mateus", "Ludmilla"),
    "fr" to listOf("Aya Nakamura", "Gims", "Ninho", "Angèle", "Stromae", "PNL"),
    "de" to listOf("Rammstein", "Apache 207", "RAF Camora", "Peter Fox", "Ayliva"),
    "ar" to listOf("Amr Diab", "Elissa", "Nancy Ajram", "Tamer Hosny"),
    "it" to listOf("Måneskin", "Lazza", "Geolier", "Marco Mengoni"),
    "ru" to listOf("Miyagi", "Morgenshtern", "Zivert", "Egor Kreed"),
    "tr" to listOf("Tarkan", "Sezen Aksu", "Murda", "Mabel Matiz"),
    "id" to listOf("Tulus", "Raisa", "Rizky Febian", "Mahalini"),
    "th" to listOf("Bodyslam", "Three Man Down", "Bowkylion"),
    "zh" to listOf("Jay Chou", "JJ Lin", "Eason Chan", "G.E.M."),
    "pl" to listOf("Sanah", "Dawid Podsiadło", "Mata"),
)

private val OnboardingGlow = listOf(ExhaleAccent, Color(0xFFE7C08A), Color(0xFFB6A4E4))

/** Artist name → YouTube Music artist. Process-wide so going back and forth costs nothing. */
private val resolvedArtists = mutableStateMapOf<String, PreferredArtist?>()

private suspend fun searchArtists(query: String): List<PreferredArtist> = withContext(Dispatchers.IO) {
    YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items
        ?.filterIsInstance<ArtistItem>()
        ?.map { PreferredArtist(it.id, it.title, it.thumbnail) }
        .orEmpty()
}

/**
 * First-run taste setup: languages first (which also fixes the region YouTube
 * localises for), then artists to build Home around.
 */
@Composable
fun OnboardingScreen(
    initialLanguages: List<String>,
    initialArtists: List<PreferredArtist>,
    onFinish: (languages: List<String>, artists: List<PreferredArtist>) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var step by remember { mutableStateOf(0) }
    val languages = remember { mutableStateListOf<String>().apply { addAll(initialLanguages) } }
    val picked = remember { mutableStateListOf<PreferredArtist>().apply { addAll(initialArtists) } }

    PageBackdropHost(
        modifier = Modifier.fillMaxSize(),
        background = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            AmbientGlow(OnboardingGlow, Modifier.fillMaxSize(), intensity = 0.8f)
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(appIcon, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text("Exhale", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.height(24.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { if (targetState > initialState) it / 8 else -it / 8 }) togetherWith
                            fadeOut(tween(160))
                    },
                    label = "onboarding_step",
                ) { current ->
                    if (current == 0) LanguageStep(languages) else ArtistStep(languages, picked)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step == 1) TextButton(onClick = { step = 0 }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (step == 0) "${languages.size} selected" else "${picked.size} artists selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(20.dp))
                Button(
                    onClick = { if (step == 0) step = 1 else onFinish(languages.toList(), picked.toList()) },
                    enabled = if (step == 0) languages.isNotEmpty() else picked.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                ) {
                    Text(if (step == 0) "Continue" else "Start listening", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun StepHeader(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.widthIn(max = 720.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.03).em))
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(languages: SnapshotStateList<String>) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            eyebrow = "Step 1 of 2",
            title = "What do you listen to?",
            subtitle = "Pick every language you listen to. The first one sets your region, so Home stops guessing from where you are.",
        )
        Spacer(Modifier.height(32.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 1000.dp),
        ) {
            OnboardingLanguages.forEach { language ->
                LanguageChip(
                    language = language,
                    order = languages.indexOf(language.code),
                    onClick = {
                        if (language.code in languages) languages.remove(language.code) else languages.add(language.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(language: OnboardingLanguage, order: Int, onClick: () -> Unit) {
    val selected = order >= 0
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .then(
                if (selected) Modifier.clip(shape).background(MaterialTheme.colorScheme.primary)
                else Modifier.glassPlate(shape, base = if (hovered) Color.White.copy(alpha = 0.06f) else Color.Unspecified),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text("${order + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(language.nativeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (language.nativeName != language.displayName) {
                Text(
                    language.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class ArtistTileModel(val key: String, val name: String, val artist: PreferredArtist?)

@Composable
private fun ArtistStep(languages: List<String>, picked: SnapshotStateList<PreferredArtist>) {
    val scope = rememberCoroutineScope()
    val suggestions = remember(languages.toList()) {
        languages.flatMap { TrendingArtistsByLanguage[it].orEmpty() }.distinct()
    }
    LaunchedEffect(suggestions) {
        val gate = Semaphore(4)
        suggestions.filterNot { it in resolvedArtists }.forEach { name ->
            launch {
                gate.withPermit {
                    resolvedArtists[name] = searchArtists(name).firstOrNull()
                }
            }
        }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PreferredArtist>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            results = searchArtists(q).take(12)
            searching = false
        }
    }

    val tiles = buildList<ArtistTileModel> {
        val seen = HashSet<String>()
        fun addTile(tile: ArtistTileModel) {
            if (seen.add(tile.artist?.id ?: tile.key)) add(tile)
        }
        results.forEach { addTile(ArtistTileModel(it.id, it.name, it)) }
        suggestions.forEach { name ->
            val resolved = resolvedArtists[name]
            // Looked up and not found: leave it out rather than show a dead tile.
            if (name in resolvedArtists && resolved == null) return@forEach
            addTile(ArtistTileModel("suggest_$name", name, resolved))
        }
        picked.forEach { addTile(ArtistTileModel(it.id, it.name, it)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                StepHeader(
                    eyebrow = "Step 2 of 2",
                    title = "Pick artists you love",
                    subtitle = "Home is built around them — stations, their music, and artists like them. Can't see someone? Search for them.",
                )
                Spacer(Modifier.height(24.dp))
                ArtistSearchField(query, { query = it }, onSearch = ::search, searching = searching)
                Spacer(Modifier.height(8.dp))
            }
        }
        items(tiles, key = { it.key }) { tile ->
            val artist = tile.artist
            ArtistTile(
                tile = tile,
                selected = artist != null && picked.any { it.id == artist.id },
                onClick = {
                    if (artist == null) return@ArtistTile
                    val existing = picked.indexOfFirst { it.id == artist.id }
                    if (existing >= 0) picked.removeAt(existing) else picked.add(artist)
                },
            )
        }
    }
}

@Composable
private fun ArtistSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, searching: Boolean) {
    val shape = RoundedCornerShape(26.dp)
    val backdrop = LocalPageBackdrop.current
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(52.dp)
            .then(if (backdrop != null) Modifier.liquidGlass(shape, backdrop, tintAlpha = 0.42f, blurRadius = 56.dp) else Modifier.glassPlate(shape))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search for an artist", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            )
        }
        if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun ArtistTile(tile: ArtistTileModel, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, spring(0.72f, 520f), label = "artist_tile")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = tile.artist != null, onClick = onClick),
    ) {
        Box(Modifier.size(136.dp)) {
            if (tile.artist == null) {
                Box(Modifier.fillMaxSize().glassPlate(CircleShape), contentAlignment = Alignment.Center) {
                    Text(tile.name.take(1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AsyncImage(
                    model = tile.artist.thumbnail?.resizedThumbnail(400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(if (selected || hovered) 18.dp else 6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(32.dp)
                        .shadow(8.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NavIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tile.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
