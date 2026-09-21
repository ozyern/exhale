/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
 * Moods & Genres, laid out the way Apple Music's Browse lays out its categories: the moods as a row of
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, top = topPadding, end = 16.dp, bottom = bottomPadding + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            NavigationTitle(
                title = stringResource(R.string.mood_and_genres),
                modifier = Modifier.padding(start = 0.dp),
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (sectionIndex == 0) 4.dp else 18.dp, bottom = 2.dp),
                    )
                }
            }
            if (featured) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "row:${section.title}", contentType = "featured") {
                    LazyRow(
                        // Edge to edge: the row runs under the page margins, the way a carousel does.
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.items, key = { "${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                            CategoryTile(
                                title = item.title,
                                stripeColor = item.stripeColor,
                                height = FeaturedTileHeight,
                                large = true,
                                onClick = { open(item) },
                                modifier = Modifier.width(FeaturedTileWidth),
                            )
                        }
                    }
                }
            } else {
                items(section.items, key = { "${section.title}:${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                    CategoryTile(
                        title = item.title,
                        stripeColor = item.stripeColor,
                        height = GenreTileHeight,
                        large = false,
                        onClick = { open(item) },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    )
                }
            }
        }
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
 * One category: a deep tile of its own colour, the name in the corner, and the name again — huge, faint
 * and tilted — bleeding off the far edge, which is what gives a wall of flat colour some texture.
 */
@Composable
fun CategoryTile(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = GenreTileHeight,
    large: Boolean = false,
) {
    val (top, bottom) = remember(stripeColor) { categoryColors(stripeColor) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "categoryPress")
    val shape = RoundedCornerShape(if (large) 22.dp else 18.dp)
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .drawBehind {
                // Light falling on the top of the tile.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        // The echo: the first word, enormous and tilted, most of it outside the tile.
        Text(
            text = title.substringBefore(' ').substringBefore('&').trim(),
            fontSize = if (large) 92.sp else 70.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.13f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(if (large) Alignment.BottomEnd else Alignment.TopEnd)
                .graphicsLayer {
                    // Away from the label: under it on a large card, above it on a small tile.
                    rotationZ = -14f
                    translationX = size.width * 0.18f
                    translationY = size.height * (if (large) 0.30f else -0.28f)
                },
        )
        Text(
            text = title,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(if (large) Alignment.TopStart else Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
private val GenreTileHeight = 104.dp
private val FeaturedTileHeight = 188.dp
private val FeaturedTileWidth = 158.dp
