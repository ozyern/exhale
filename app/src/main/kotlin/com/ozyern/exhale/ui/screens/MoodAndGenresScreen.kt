/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.utils.resize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.pages.MoodAndGenres
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.viewmodels.MoodAndGenresViewModel

/**
 * Moods & Genres, laid out the way the iOS music app's Browse lays out its categories: the moods as a row of
 * large featured cards you swipe through, then every genre as a two-column grid of colour tiles.
 *
 * It used to be one flat grid of short strips — YouTube's pastel stripe colour behind a small white
 * label — built from Explore's shelf, which is a fifth of the catalogue with its sections thrown away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsState()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val topPadding = with(density) { windowInsets.getTop(this).toDp() }
    val bottomPadding = with(density) { windowInsets.getBottom(this).toDp() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            gridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val open: (MoodAndGenres.Item) -> Unit = { item ->
        navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
    }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, top = topPadding, end = 16.dp, bottom = bottomPadding + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The title is pinned over the grid (see below), as Home's is; this only clears it.
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            androidx.compose.foundation.layout.Spacer(
                Modifier.height(com.ozyern.exhale.ui.component.PinnedPageHeaderHeight - 12.dp),
            )
        }

        val loaded = sections
        if (loaded == null) {
            items(8) {
                ShimmerHost {
                    TextPlaceholder(height = GenreTileHeight, shape = RoundedCornerShape(20.dp))
                }
            }
            return@LazyVerticalGrid
        }

        loaded.forEachIndexed { sectionIndex, section ->
            // The first section — moods — is the featured row; everything after it is the grid.
            val featured = sectionIndex == 0 && loaded.size > 1
            if (section.title.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "title:${section.title}", contentType = "section") {
                    Text(
                        text = section.title,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        letterSpacing = (-0.3).sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = if (sectionIndex == 0) 6.dp else 20.dp, bottom = 2.dp),
                    )
                }
            }
            if (featured) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "row:${section.title}", contentType = "featured") {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // One wide card and the edge of the next, whatever the phone.
                        val cardWidth = minOf(maxWidth * 0.86f, 380.dp)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.items, key = { "${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                                CategoryTile(
                                    title = item.title,
                                    stripeColor = item.stripeColor,
                                    covers = rememberCategoryCovers(item.endpoint.browseId, item.endpoint.params),
                                    height = cardWidth / 1.6f,
                                    large = true,
                                    onClick = { open(item) },
                                    modifier = Modifier.width(cardWidth),
                                )
                            }
                        }
                    }
                }
            } else {
                items(section.items, key = { "${section.title}:${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                    CategoryTile(
                        title = item.title,
                        stripeColor = item.stripeColor,
                        covers = rememberCategoryCovers(item.endpoint.browseId, item.endpoint.params),
                        height = GenreTileHeight,
                        large = false,
                        onClick = { open(item) },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    )
                }
            }
        }
    }

    com.ozyern.exhale.ui.component.PinnedPageHeader(
        title = stringResource(R.string.mood_and_genres),
        scrolled = { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 },
        modifier = Modifier.align(Alignment.TopStart),
    )
    }
}

/**
 * YouTube's stripe colour, made into something white type can sit on.
 *
 * The stripes are mostly pastels — a pale yellow, a light grey — which under white text read as a
 * washed-out label. The hue is kept; saturation is lifted and the value brought down into the band
 * where the colour is rich and the text is legible. Near-greys stay neutral, but deep.
 */
internal fun categoryColors(stripeColor: Long): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(Color(stripeColor).toArgb(), hsv)
    val grey = hsv[1] < 0.12f
    val saturation = if (grey) 0.10f else hsv[1].coerceAtLeast(0.62f).coerceAtMost(0.92f)
    val top = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation * 0.92f, if (grey) 0.46f else 0.80f)))
    val bottom = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 14f) % 360f, saturation, if (grey) 0.24f else 0.44f)))
    return top to bottom
}

/**
 * One category, as Apple Music's Browse draws one: the category's own artwork edge to edge, its
 * colour washed up from the foot so the name — bold, white, bottom left — always has something to
 * sit on. Until the artwork arrives the tile is that colour alone, so nothing is ever blank.
 *
 * It used to stack a huge tilted echo of the name, tilted overlapping covers and an extra-bold label
 * on one tile; three textures fighting for a 100dp box read as noise, not as a category.
 */
@Composable
fun CategoryTile(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    covers: List<String> = emptyList(),
    height: Dp = GenreTileHeight,
    large: Boolean = false,
) {
    val (top, bottom) = remember(stripeColor) { categoryColors(stripeColor) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "categoryPress")
    val shape = RoundedCornerShape(if (large) 20.dp else 14.dp)
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        // The category's own artwork, edge to edge, faded in once fetched.
        val artAlpha by animateFloatAsState(if (covers.isEmpty()) 0f else 1f, tween(260), label = "categoryArt")
        covers.firstOrNull()?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artAlpha },
            )
        }
        // The category's colour, washed up from the foot: it ties the picture to its category and
        // is what the name is legible on, over a bright cover or a dark one.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to bottom.copy(alpha = 0.15f),
                        1f to bottom.copy(alpha = 0.92f),
                    ),
                ),
        )
        Text(
            text = title,
            fontSize = if (large) 24.sp else 17.sp,
            lineHeight = if (large) 28.sp else 21.sp,
            letterSpacing = if (large) (-0.4).sp else 0.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (large) 18.dp else 12.dp, end = 12.dp, bottom = if (large) 16.dp else 10.dp),
        )
    }
}

/** Kept for Explore's shelf, which draws the same tile at its own size. */
@Composable
fun MoodAndGenresButton(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = CategoryTile(title = title, stripeColor = stripeColor, onClick = onClick, modifier = modifier, height = MoodAndGenresButtonHeight)

val MoodAndGenresButtonHeight = 88.dp
private val GenreTileHeight = 110.dp

/** Covers for a category, fetched once and kept for the session. */
private object CategoryCovers {
    val cache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    suspend fun load(browseId: String, params: String?): List<String> {
        val key = "$browseId|$params"
        cache[key]?.let { return it }
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.ozyern.exhale.innertube.YouTube.browse(browseId, params).getOrNull()
                ?.items.orEmpty()
                .flatMap { it.items }
                .mapNotNull { it.thumbnail }
                .distinct()
                .take(3)
                // Full-bleed now, on a card up to 380dp wide: a 360px thumbnail went soft.
                .map { it.resize(720, 720) }
        }
        cache[key] = found
        return found
    }
}

@Composable
private fun rememberCategoryCovers(browseId: String, params: String?): List<String> {
    val covers by produceState(initialValue = CategoryCovers.cache["$browseId|$params"].orEmpty(), browseId, params) {
        if (value.isEmpty()) value = runCatching { CategoryCovers.load(browseId, params) }.getOrNull().orEmpty()
    }
    return covers
}
