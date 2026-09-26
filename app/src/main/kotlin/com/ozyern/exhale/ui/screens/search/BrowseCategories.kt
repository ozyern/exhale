/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/* --------------------------------------------------------------------- */
/* Apple-Music "Browse Categories" grid — shared by the Search overlay    */
/* empty state and the peer-level Search destination.                     */
/* --------------------------------------------------------------------- */

data class BrowseCategory(
    val title: String,
    val query: String,
    val start: Color,
    val end: Color,
)

/**
 * Curated genre/category buckets shown on the Search landing. Colours echo Apple Music's own
 * category palette and double as the instant placeholder while the artwork streams in.
 * `query` is what gets run as a YouTube Music search when the card is tapped.
 */
val BrowseCategories: List<BrowseCategory> = listOf(
    BrowseCategory("Pop", "Pop hits", Color(0xFFFF6FB5), Color(0xFFFF3D77)),
    BrowseCategory("Hits", "Top hits", Color(0xFFFFC24B), Color(0xFFFF8A3D)),
    BrowseCategory("R&B", "R&B", Color(0xFF9B6BFF), Color(0xFF6A3DFF)),
    BrowseCategory("Holiday", "Holiday", Color(0xFFF0455A), Color(0xFF9C1B2E)),
    BrowseCategory("Live", "Live performances", Color(0xFF7E8CE0), Color(0xFF4B57B0)),
    BrowseCategory("Radio", "Music radio", Color(0xFFFF5B57), Color(0xFFD62E4A)),
    BrowseCategory("Coming Soon", "New releases", Color(0xFFFF7EA3), Color(0xFFE8547E)),
    BrowseCategory("Spatial Audio", "Spatial audio", Color(0xFFFF4D4D), Color(0xFFB01E3C)),
    BrowseCategory("Hip-Hop", "Hip-Hop", Color(0xFF5B8CFF), Color(0xFF2E58D6)),
    BrowseCategory("Rock", "Rock", Color(0xFF8A6FE0), Color(0xFF5A3DAE)),
    BrowseCategory("Country", "Country", Color(0xFFE0A05A), Color(0xFFB4772E)),
    BrowseCategory("Latin", "Latin", Color(0xFFFF6F91), Color(0xFFD63E63)),
    BrowseCategory("Dance", "Dance electronic", Color(0xFF4BC3E0), Color(0xFF2E8CD6)),
    BrowseCategory("Chill", "Chill", Color(0xFF6FD0C0), Color(0xFF3DAE9A)),
    BrowseCategory("Workout", "Workout", Color(0xFFFF8A5B), Color(0xFFD6552E)),
    BrowseCategory("Focus", "Focus", Color(0xFF7E9CE0), Color(0xFF4B6BB0)),
)

/**
 * Process-wide cache of dynamic category artwork, resolved once from YouTube Music (top featured
 * playlist thumbnail for each genre bucket) then held in memory for the app's lifetime — repeat
 * visits to Search render instantly from here + Coil's memory cache.
 */
object BrowseCategoryImages {
    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()
    private val loading = AtomicBoolean(false)

    suspend fun ensureLoaded(categories: List<BrowseCategory>) {
        if (!loading.compareAndSet(false, true)) return
        try {
            coroutineScope {
                categories
                    .filterNot { _images.value.containsKey(it.title) }
                    .chunked(4) // polite concurrency: 4 lookups in flight at a time
                    .forEach { chunk ->
                        chunk.map { category ->
                            async {
                                runCatching {
                                    YouTube.search(
                                        category.query,
                                        YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST,
                                    ).getOrNull()
                                        ?.items
                                        ?.firstNotNullOfOrNull { it.thumbnail }
                                }.getOrNull()?.let { thumbnail ->
                                    _images.update { it + (category.title to thumbnail) }
                                }
                            }
                        }.awaitAll()
                    }
            }
        } finally {
            loading.set(false)
        }
    }
}

/**
 * The 2-column category card grid, mirroring Apple Music's Search landing exactly: photo-backed
 * rounded cards (~1.85:1, the reference's aspect), a dark scrim gradient for text legibility and
 * a bold white bottom-left label. Cards paint their gradient immediately and crossfade the real
 * artwork in as [BrowseCategoryImages] resolves it.
 */
@Composable
fun BrowseCategoriesGrid(
    pureBlack: Boolean,
    onProfileClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    contentPadding: PaddingValues,
    showProfile: Boolean = true,
) {
    val categoryImages by BrowseCategoryImages.images.collectAsState()

    // Resolve artwork off the main thread; no-op once cached.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            BrowseCategoryImages.ensureLoaded(BrowseCategories)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // Apple-Music header: a massive bold "Search" title on the left with a circular
        // profile avatar pinned to the top-right. Spans the full grid width.
        item(span = { GridItemSpan(maxLineSpan) }, key = "browse_header", contentType = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.search),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pureBlack) Color.White else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (showProfile) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (pureBlack) Color.White.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(onClick = onProfileClick),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = "Profile",
                            tint = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        items(BrowseCategories, key = { it.title }, contentType = { "category" }) { category ->
            BrowseCategoryCard(
                category = category,
                imageUrl = categoryImages[category.title],
                onClick = { onCategoryClick(category.query) },
            )
        }
    }
}

@Composable
private fun BrowseCategoryCard(
    category: BrowseCategory,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "categoryPress",
    )
    val interaction = remember { MutableInteractionSource() }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Apple Music reference card geometry: just under 2:1.
            .aspectRatio(1.85f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(category.start, category.end),
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    pressed = true
                    onClick()
                },
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        // Dynamic genre artwork; the gradient behind it doubles as the loading placeholder.
        if (imageUrl != null) {
            AsyncImage(
                model = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        // Dark overlay gradient keeps the bold white title legible over any artwork.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // Heavier at the foot than it looks like it needs to be, because the artwork
                    // these cards use is promo art with its own title set across it: at the old
                    // 0.62 the category's name ("Holiday") sat on top of the picture's name
                    // ("Summer Dance Classics") and the two read as one scrambled line.
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.84f),
                    )
                )
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(14.dp),
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(150)
            pressed = false
        }
    }
}
