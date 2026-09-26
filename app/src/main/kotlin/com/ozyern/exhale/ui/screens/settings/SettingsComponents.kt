/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.ozyern.exhale.ui.component.liquid.PageBackdropHost
import androidx.compose.runtime.mutableStateOf
import com.ozyern.exhale.ui.component.rememberArtworkAmbientColors
import com.ozyern.exhale.ui.component.AmbientArtworkGlow
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.settingsIconPuck

/**
 * Wraps a settings destination in the Apple-Music grouped-table look.
 *
 * Two things happen here, both once for every settings screen instead of once per screen file:
 *
 *  1. The page is painted with the solid, distinct grouped background — pure black on dark, a
 *     near-white grey on light — so the inset group cards read as plates floating on a ground
 *     rather than as tinted patches of the same sheet.
 *  2. `colorScheme.surface` is re-pointed at that same colour for the subtree, so any Material
 *     component that reaches for `surface` lands on the page rather than a step off it, and the
 *     tonal-elevation tint M3 paints on scrolled surfaces goes away — the flat, single-colour
 *     chrome is the whole point. Only `surface` moves; `onSurface`, the accent and every
 *     container token are untouched, so contrast and the brand tint are unaffected.
 *
 * App bars do not use that colour any more: a flat fill covers the wash this page lays down, and
 * a bar has to be opaque, so they take [SettingsBarGround] instead. See [SettingsTopAppBar].
 */
@Composable
fun SettingsPage(content: @Composable BoxScope.() -> Unit) {
    val pageBackground = SettingsDimensions.screenBackgroundColor()
    val mediaMetadata by LocalPlayerConnection.current?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val ambientColors by rememberArtworkAmbientColors(
        songId = mediaMetadata?.id,
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
    )

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(surface = pageBackground),
    ) {
        // Recorded, not just painted.
        //
        // The ground and the wash go into their own layer, drawn as a sibling beneath everything
        // the settings routes put on the page. That makes them something the glass on this page
        // can legally *sample* — so the back button, the search icon and the grouped cards bend
        // the album-art wash at their rims instead of merely being translucent over it. It also
        // overrides the app-wide backdrop published in MainActivity, which is the right call
        // here: the settings ground is opaque, so the ambient field behind the window is not
        // visible anywhere on this screen and refracting it would be inventing a reflection of
        // something that is not there.
        PageBackdropHost(
            modifier = Modifier.fillMaxSize(),
            background = {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(pageBackground),
                )
                AmbientArtworkGlow(
                    colors = ambientColors,
                    modifier = Modifier.matchParentSize(),
                    intensity = 0.66f,
                )
            },
        ) {
            // The wash the glass cards are glass *of*.
            //
            // This is the whole reason the settings surfaces can be translucent at all: an inset
            // grouped list on a flat page has nothing behind it, so a translucent card there is a
            // smudge, not a lens. Wired in at the page wrapper rather than per screen because
            // every settings route already goes through here (`settingsComposable`), so this is
            // the one place all ~30 of them can be given the same ground without any of them
            // knowing about it.
            //
            // Pulled back to two thirds: settings pages are dense small text, and the intensity
            // that reads as atmosphere behind Home's artwork cards reads as a stain behind a
            // paragraph of labels.
            content()
        }
    }
}

/**
 * The header plate at the top of Settings.
 *
 * Modelled on the account row iOS puts at the top of its Settings app: an oversized rounded-square
 * glyph, the name at headline weight, a quiet second line, and a chevron - because the row *goes*
 * somewhere. It used to be a dead card, which meant the most prominent element on the screen was
 * also the only one that did nothing when you tapped it.
 */
/**
 * The search field that sits under the large title.
 *
 * iOS puts a real, visible search field at the top of Settings; we had a magnifier in the app bar
 * that swapped the entire screen for a search overlay. A tap target you have to already know about
 * is not a search affordance — this one states that the page is searchable before you look for it.
 *
 * It is a button rather than a text field: focus, the keyboard and the result list all belong to
 * the existing search overlay, and duplicating a second editable field here would mean two places
 * holding the same query.
 */
@Composable
fun SettingsSearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Only the press wash is animated now; the resting fill is the shared glass recipe, so this
    // pill is made of the same material as the cards under it instead of being the one element on
    // the page mixing its own flat grey.
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.07f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "searchPillBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsProfileHeader(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.09f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "heroBgAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.HeroCardCornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                } else {
                    Modifier
                }
            )
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 64dp, and with a live accent behind it. At 56dp on a flat tint this card was a settings
        // row with a slightly larger glyph — the most prominent thing on the page was carrying no
        // more visual weight than the five rows under it. The breathing halo is the same idea as
        // About's spectrum band: on a page of static plates, the identity is the one element that
        // should look like the app is running.
        val haloTransition = rememberInfiniteTransition(label = "heroHalo")
        val halo by haloTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "heroHaloPhase",
        )
        val accent = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.26f),
                            accent.copy(alpha = 0.10f),
                        ),
                    )
                )
                // Draw phase, so a permanent animation on the settings root costs one draw
                // invalidation per frame and never recomposes the header or relays it out.
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.10f + 0.22f * halo),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * (0.42f + 0.16f * halo),
                        ),
                        radius = size.minDimension * (0.42f + 0.16f * halo),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // The mark itself, not a tinted glyph of it.
            //
            // `R.drawable.exhale` is the monochrome outline the nav bar uses, and painted in the
            // theme accent it became whatever colour the album art happened to extract - a pink
            // scribble in a pink circle. This is the same gold mark the Updates and About posters
            // carry, so the app introduces itself the same way in all three places.
            Image(
                painter = painterResource(R.drawable.splash_logo_gold),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.settings_hero_subtitle,
                    BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }
    }
}

@Composable
fun SettingsPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.BannerIconSize)
                    .settingsIconPuck(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.security),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.allow),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
    }
}

@Composable
fun SettingsUpdateBanner(
    latestVersion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "updateScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.BannerIconSize)
                    .settingsIconPuck(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "v$latestVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium,
                )
            }

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
    }
}

@Composable
fun SettingsSearchEmpty(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            Text(
                text = stringResource(R.string.no_results_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.search_try_different),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/**
 * The header that sits above every grouped block.
 *
 * One definition, used by the categories, the quick actions and the integrations alike. Before
 * this, two of the three groups on the Settings screen had *no* header at all — they were bare
 * cards floating with nothing naming them, which is the one thing an iOS grouped table never does.
 *
 * It is a **label**, not a title. At `titleLarge`/Bold/`onSurface` — what it used to be — the header
 * was set larger and heavier than the row titles it introduced, so scrolling the page read as a
 * stack of competing headlines with the actual content in between. A grouped list wants its
 * headers quiet: small, tracked, one tone down, doing nothing but naming the plate underneath.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = SettingsDimensions.SectionHeaderHorizontalPadding + 8.dp,
            end = SettingsDimensions.SectionHeaderHorizontalPadding,
            top = 18.dp,
            bottom = 8.dp,
        ),
    )
}

@Composable
fun SettingsGroupCard(
    group: SettingsGroup,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(group.title)

        // Apple Music-style grouped inset list: a Column clipped to 16dp over a frosted
        // translucent surface — NO Material Card. Dividers appear only BETWEEN rows
        // (never top/bottom).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)),
        ) {
            group.items.forEachIndexed { index, item ->
                SettingsRow(
                    item = item,
                    showDivider = index < group.items.size - 1,
                )
            }
        }
    }
}

/**
 * Reusable Apple Music-style grouped inset section: a large bold header above a Column
 * clipped to 16dp over a distinct surface. Dividers are drawn only BETWEEN the supplied
 * rows — never at the very top or bottom of the group.
 */
@Composable
fun InsetGroup(
    title: String,
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)),
        ) {
            rows.forEachIndexed { index, row ->
                row()
                if (index < rows.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                        thickness = SettingsDimensions.DividerThickness,
                        color = SettingsDimensions.dividerColor(),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    item: SettingsItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    // Every row gets a colour, as every row in iOS Settings does: its own if it has one, otherwise a
    // system colour picked from its title so it is the same colour every time.
    // iOS paints every row icon in a saturated system colour; the theme's pastel accents read as
    // disabled next to them. Picked from the title, so a row keeps its colour.
    // A row that was given a colour keeps it. This used to hash the title in every case, which
    // quietly threw away the colours the account sheet had chosen for its four rows and handed two
    // of them the same grey - a grouped list where half the icons are grey squares reads as
    // unfinished, which is exactly what it was.
    val effectiveAccent = item.accentColor.takeIf { it != Color.Unspecified }
        ?: IosAutoColors[(item.title.hashCode() and 0x7fffffff) % IosAutoColors.size]

    // Subtle premium haptic tick on row taps — routed through the app-wide custom
    // LocalHapticFeedback provider, which already respects the user's haptics preference.
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // A pressed row in iOS fills edge to edge with a neutral grey and does not move. The previous
    // behaviour shrank the row to 98% and tinted it with the brand colour, which reads as a
    // *button* — and a grouped table is a list of destinations, not a panel of buttons. Scaling
    // also visibly detached each row from the hairlines above and below it.
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.09f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "rowBgAlpha",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        item.onClick()
                    },
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A coloured glyph, not a coloured tile.
            //
            // iOS fills a rounded square and puts a white glyph on it; ColorOS - which is what this
            // app is read on - draws the glyph itself in its colour and gives it no container at
            // all. The tiles turned every page into a column of solid swatches competing with the
            // artwork wash behind them; the bare glyph keeps the colour coding and hands the row
            // back to its label.
            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(8.dp),
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = effectiveAccent,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.showUpdateIndicator) {
                            effectiveAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                thickness = SettingsDimensions.DividerThickness,
                color = SettingsDimensions.dividerColor(),
            )
        }
    }
}

/**
 * The ground an opaque settings app bar sits on.
 *
 * [SettingsPage] lays the album-art wash down behind the whole screen, but a settings app bar has
 * to be opaque -- rows slide under it as the list scrolls, and a translucent bar there shows them.
 * So the bar covers the wash with a flat plate, and on any screen where something is playing the
 * result is a page that takes its colour from the artwork with a rectangle of dead grey across the
 * top of it. On the root Settings screen that rectangle is a large title's worth of bar, which is
 * most of what you see when you open the page.
 *
 * This paints the same two layers the page does, so the bar is made of the page rather than laid
 * on top of it, and it still hides everything behind it.
 *
 * The wash is drawn at the height of the **screen**, not of the bar, and clipped. That is the
 * whole trick: [AmbientArtworkGlow] positions its blobs as fractions of the height it is given, so
 * a copy fitted to a 150dp bar would compress the entire gradient into the bar and meet the page's
 * own copy at a visible seam. Given the screen's height it is the same gradient, continuing.
 */
@Composable
fun SettingsBarGround(modifier: Modifier = Modifier) {
    val pageBackground = SettingsDimensions.screenBackgroundColor()
    val mediaMetadata by LocalPlayerConnection.current?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val ambientColors by rememberArtworkAmbientColors(
        songId = mediaMetadata?.id,
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
    )
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = modifier
            .clipToBounds()
            .background(pageBackground),
    ) {
        AmbientArtworkGlow(
            colors = ambientColors,
            // Matched to SettingsPage. Two different intensities would put a step across the
            // bar's bottom edge, which is the seam this exists to remove.
            intensity = 0.66f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(screenHeight),
        )
    }
}

/**
 * The app bar every settings sub-page opens with.
 *
 * A thin wrapper over Material's [TopAppBar] whose only job is to put [SettingsBarGround] behind
 * it instead of a flat fill. The bars were each reaching for the default container colour, which
 * inside [SettingsPage] is the page's ground colour and nothing else -- so every sub-page had the
 * same dead strip across its top as the root screen did.
 *
 * The bar stays opaque. Settings content scrolls underneath it, and a translucent bar here would
 * show rows sliding behind the back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box(modifier) {
        SettingsBarGround(modifier = Modifier.matchParentSize())

        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

/**
 * The large-title bar a settings page opens with.
 *
 * The same statement as [SettingsTopAppBar], one size up: opaque, so the rows sliding under it are
 * hidden, but opaque *as the page* rather than as a flat plate laid over it.
 *
 * This existing is the difference between the root Settings screen and every other page that has
 * a large title. The root screen was fixed by hand and the rest kept
 * `containerColor = screenBackgroundColor()`, which is a solid near-black — so on About, and on
 * every page like it, the biggest single element on the screen was a dead rectangle sitting on top
 * of a page that otherwise takes its colour from whatever is playing. There is no per-page reason
 * for that; there was only no shared piece to reach for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box(modifier) {
        SettingsBarGround(modifier = Modifier.matchParentSize())

        LargeTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            // Transparent in both states, because the ground behind it is doing the work now.
            // Still no tonal-elevation shift on scroll, which is the single most "Android" tell a
            // settings screen has.
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

/**
 * The statement card the About and Updates pages open on.
 *
 * A shipped image, not a drawn gradient. The theme-derived poster was this app's colours, which is
 * a nice idea and the wrong one: a phone's About and Software-update pages open on *the release's*
 * artwork - one picture, the same on every install, that says which thing you are running. So this
 * is Exhale's own gold key art, with a scrim so white type sits on it at any point in the frame.
 *
 * Shared because the two pages make the same statement: what this is, and whether it is current.
 */
@Composable
fun SettingsPosterCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.banner_gold),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Darkest where the words are, so the picture keeps its light in the middle.
        //
        // Deeper at the foot than it looks like it needs to be: the state line and the action pill
        // sit there, and this artwork runs a bright gold arc straight through that band. White
        // 19sp on gold at 55% is legible and looks cheap; at 68% it looks printed.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.38f),
                        0.38f to Color.Black.copy(alpha = 0.12f),
                        0.72f to Color.Black.copy(alpha = 0.40f),
                        1f to Color.Black.copy(alpha = 0.68f),
                    ),
                ),
        )
        // Fills the card, so a caller can weight its blocks apart - the mark sitting high and
        // the state at the foot is the whole shape of a system release card, and centring
        // everything in a column that only wrapped its content left the bottom third empty.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * The mark, the name and the version, as a release poster states them.
 *
 * The order is the one every system About page uses: the product's mark, its name set large, the
 * version under it in a quieter weight. The state of the thing - "up to date", and whatever you
 * can do about it - is [SettingsPosterStatus], a separate block, because it belongs at the foot of
 * the card rather than tucked under the version.
 */
@Composable
fun SettingsPosterMark(
    name: String,
    version: String,
    @DrawableRes markRes: Int,
    modifier: Modifier = Modifier,
    markSize: Dp = 96.dp,
    /** A quiet line above the name - the device this is running on. */
    eyebrow: String? = null,
    /** A quiet line under the version, where the eyebrow would otherwise go. */
    subline: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(markRes),
            contentDescription = null,
            modifier = Modifier.size(markSize),
        )
        if (eyebrow != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = eyebrow,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(2.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        // The wordmark, drawn, not typed.
        //
        // "ColorOS" and "OxygenOS" are set in their own lettering on these cards, and no weight of
        // the UI font gets there: typed in Linotte this was a headline pretending to be a mark.
        // [name] is still taken, as the content description - the card has to say what it is to a
        // screen reader even when what it shows is a picture of a word.
        Image(
            painter = painterResource(R.drawable.wordmark_exhale),
            contentDescription = name,
            modifier = Modifier.height(46.dp),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = version,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.92f),
        )
        if (subline != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subline,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

/**
 * What the release *is* right now, at the foot of the poster: a line of state, and at most one
 * thing to do about it.
 *
 * Separated from [SettingsPosterMark] so the card can push it down. On a phone's software-update
 * page the mark sits in the upper half and this sits near the bottom edge, which is what makes the
 * card read as a poster with a caption rather than as a stack of centred text.
 */
@Composable
fun SettingsPosterStatus(
    status: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = status,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** iOS's system colours, the ones Settings paints its row icons with. *//** iOS's system colours, the ones Settings paints its row icons with. */
internal val IosSystemColors = listOf(
    Color(0xFF0A84FF), // blue
    Color(0xFF30D158), // green
    Color(0xFFFF9F0A), // orange
    Color(0xFFFF375F), // pink
    Color(0xFFBF5AF2), // purple
    Color(0xFF5E5CE6), // indigo
    Color(0xFFFF453A), // red
    Color(0xFF64D2FF), // cyan
    Color(0xFF8E8E93), // grey
)

/**
 * The colours a row may be given *automatically*.
 *
 * Grey is in the palette because iOS paints Settings' own gear with it, and a row that asks for it
 * still gets it. It is out of this list because the automatic pick is a hash of the title, and a
 * one-in-nine chance of grey meant a group could come up with three pale slate squares in a row -
 * which reads as icons that failed to load rather than as a colour scheme.
 */
internal val IosAutoColors = IosSystemColors.dropLast(1)
