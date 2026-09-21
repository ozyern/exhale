/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.settingsIconPuck
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.ui.component.ExhaleBreathingEgg
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.utils.backToMain
import kotlin.math.PI
import kotlin.math.sin
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

// ─── People and links ─────────────────────────────────────────────────────────

private const val LeadDeveloperName = "Aditya Jha"
private const val LeadDeveloperHandle = "ozyern"

/**
 * GitHub serves every user's avatar at `github.com/<handle>.png`, so the maintainer's picture
 * follows whatever they set on their profile instead of being pinned to a numeric asset id that
 * silently rots the day they change it.
 */
private const val LeadDeveloperAvatar = "https://github.com/$LeadDeveloperHandle.png"
private const val LeadDeveloperUrl = "https://github.com/$LeadDeveloperHandle"

private const val LicenseUrl = "https://github.com/ozyern/Exhale/blob/master/LICENSE"

private data class SocialLink(
    val iconRes: Int,
    val label: String,
    val handle: String,
    val url: String,
)

private val SocialLinks = listOf(
    SocialLink(R.drawable.github, "GitHub", "@ozyern", "https://github.com/ozyern"),
    SocialLink(R.drawable.telegram, "Telegram", "@ozyern", "https://t.me/ozyern"),
    SocialLink(
        R.drawable.instagram,
        "Instagram",
        "@imozyern",
        "https://www.instagram.com/imozyern/",
    ),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * About.
 *
 * Laid out the way OxygenOS 16 lays out "About device", because that shape is right for a page that
 * is mostly facts about a build:
 *
 *  1. a tall **statement card** — the brand, set large, with a graphic band along the bottom edge;
 *  2. a **two-up pair of stat cards** for the two facts worth reading first (version, architecture);
 *  3. **grouped key/value rows** underneath, label left and value right.
 *
 * The rows in group 3 deliberately carry no icon pucks. A puck earns its place when it distinguishes
 * one destination from its neighbours in a long list of destinations; on a table where every row is
 * a fact about the same app, thirteen identical accent squares are decoration that makes the values
 * harder to scan, not easier. Rows that *go* somewhere — the maintainer, the social links, the
 * licence — keep theirs, because those are destinations again.
 *
 * The statement card keeps exactly one flourish, and it is hidden. See [ExhaleBreathingEgg].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showEasterEgg by remember { mutableStateOf(false) }

    // Everything the page states about the build, assembled once. `remember` with no keys
    // because none of it can change without the process restarting.
    val buildFacts = remember {
        BuildFacts(
            version = BuildConfig.VERSION_NAME,
            build = BuildConfig.VERSION_CODE.toString(),
            packageName = BuildConfig.APPLICATION_ID,
            architecture = BuildConfig.ARCHITECTURE,
            buildType = BuildConfig.BUILD_TYPE,
            commit = BuildConfig.GIT_COMMIT.take(7).ifBlank { "—" },
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
        )
    }

    if (showEasterEgg) {
        ExhaleBreathingEgg(onDismiss = { showEasterEgg = false })
    }

    val pad = SettingsDimensions.ScreenHorizontalPadding
    val spacing = SettingsDimensions.SectionSpacing

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent, so the album-art wash `SettingsPage` lays down is what you see behind
        // the groups. The app bar above stays opaque on purpose: the large title has rows
        // sliding under it as the list scrolls, and a translucent bar there would show them.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsLargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    LiquidBackButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        icon = R.drawable.chevron_back,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 40.dp),
        ) {
            item(key = "hero") {
                AboutHero(
                    onSecretUnlocked = { showEasterEgg = true },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            item(key = "stats") {
                // The two facts someone opening About is most likely here for, given the weight a
                // pair of side-by-side cards carries. Everything else stays in the table below.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AboutStatCard(
                        icon = R.drawable.info,
                        label = stringResource(R.string.about_version),
                        value = BuildConfig.VERSION_NAME,
                        modifier = Modifier.weight(1f),
                    )
                    AboutStatCard(
                        icon = R.drawable.token,
                        label = stringResource(R.string.about_architecture),
                        value = BuildConfig.ARCHITECTURE,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "maintainer") {
                Column(modifier = Modifier.padding(bottom = spacing)) {
                    SettingsSectionHeader(stringResource(R.string.about_maintainer))
                    AboutGroup {
                        AboutPersonRow(
                            avatarUrl = LeadDeveloperAvatar,
                            name = LeadDeveloperName,
                            role = stringResource(R.string.about_lead_developer),
                            onClick = { uriHandler.openUri(LeadDeveloperUrl) },
                        )
                    }
                }
            }

            item(key = "social") {
                Column(modifier = Modifier.padding(bottom = spacing)) {
                    SettingsSectionHeader(stringResource(R.string.about_connect))
                    AboutGroup {
                        SocialLinks.forEachIndexed { index, link ->
                            if (index > 0) AboutDivider()
                            AboutRow(
                                icon = link.iconRes,
                                title = link.label,
                                value = link.handle,
                                onClick = { uriHandler.openUri(link.url) },
                            )
                        }
                    }
                }
            }

            item(key = "updates") {
                Column(modifier = Modifier.padding(bottom = spacing)) {
                    SettingsSectionHeader(stringResource(R.string.updates))
                    AboutGroup {
                        // About is where people come looking for this, and until now the only
                        // route to it was back out to Settings and down a different branch.
                        AboutRow(
                            icon = R.drawable.update,
                            title = stringResource(R.string.update_check_now),
                            value = null,
                            onClick = { navController.navigate("settings/update") },
                        )
                        AboutDivider()
                        AboutRow(
                            icon = R.drawable.history,
                            title = stringResource(R.string.view_changelog),
                            value = null,
                            onClick = { navController.navigate("settings/changelog") },
                        )
                    }
                }
            }

            item(key = "info") {
                Column(modifier = Modifier.padding(bottom = spacing)) {
                    SettingsSectionHeader(stringResource(R.string.about_information))
                    AboutGroup {
                        // The table answers the question this page is actually opened for, which
                        // is almost never "what version am I on" in isolation — it is "what
                        // exactly am I running", asked because something is wrong. Three rows
                        // naming the app and nothing naming the phone or the build left the other
                        // half of that answer somewhere in Android's own settings.
                        AboutValueRow(
                            title = stringResource(R.string.update_installed_version),
                            value = buildFacts.version,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_build),
                            value = buildFacts.build,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_build_type),
                            value = buildFacts.buildType,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_commit),
                            value = buildFacts.commit,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_package),
                            value = buildFacts.packageName,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_android),
                            value = buildFacts.android,
                        )
                        AboutPlainDivider()
                        AboutValueRow(
                            title = stringResource(R.string.about_device),
                            value = buildFacts.device,
                        )
                        AboutDivider()
                        // One tap instead of eight fields transcribed by hand into a bug report,
                        // half of them wrong. No chevron: it does something here rather than
                        // going somewhere.
                        AboutRow(
                            icon = R.drawable.content_copy,
                            title = stringResource(R.string.about_copy_build_info),
                            value = stringResource(R.string.about_copy_build_info_desc),
                            showChevron = false,
                            onClick = {
                                clipboard.setText(AnnotatedString(buildFacts.asReport()))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.about_build_info_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                }
            }

            item(key = "license") {
                Column {
                    SettingsSectionHeader(stringResource(R.string.about_legal))
                    AboutGroup {
                        AboutRow(
                            icon = R.drawable.policy,
                            title = "GNU General Public License v3.0",
                            value = null,
                            onClick = { uriHandler.openUri(LicenseUrl) },
                        )
                    }
                }
            }
        }
    }
}

// ─── Build facts ──────────────────────────────────────────────────────────────

/**
 * Everything the Information table states, in one immutable object.
 *
 * Exists mainly for [asReport]. A "copy build info" row that reassembles the string from eight
 * separate `BuildConfig` and `Build` reads at the call site is a second copy of the table that can
 * silently disagree with the first one; this way the rows and the clipboard are guaranteed to be
 * the same facts.
 */
private data class BuildFacts(
    val version: String,
    val build: String,
    val packageName: String,
    val architecture: String,
    val buildType: String,
    val commit: String,
    val android: String,
    val device: String,
) {
    /** Formatted for pasting straight into an issue: one fact per line, aligned labels. */
    fun asReport(): String = buildString {
        appendLine("Exhale $version ($build)")
        appendLine("Package:      $packageName")
        appendLine("Architecture: $architecture")
        appendLine("Build type:   $buildType")
        appendLine("Commit:       $commit")
        appendLine("Android:      $android")
        append("Device:       $device")
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

/** Taps on the hero needed to open the easter egg. Android's version-tap egg wants seven too. */
private const val SecretTapCount = 7

/**
 * Resting heights of the bars in the band along the bottom of the statement card, as a fraction of
 * the band's height. The live animation swings around these.
 *
 * Hard-coded rather than randomised: a `Random` call in a composable re-rolls on every
 * recomposition, so the silhouette would jump every time the tap counter changed. This is a fixed
 * shape that happens to look arbitrary — which is what the OxygenOS confetti strip is too.
 */
private val HeroBandHeights = listOf(
    0.30f, 0.62f, 0.44f, 0.86f, 0.55f, 1.00f, 0.38f, 0.72f, 0.48f, 0.90f,
    0.34f, 0.66f, 0.95f, 0.42f, 0.58f, 0.80f, 0.36f, 0.68f, 0.50f, 0.28f,
    0.74f, 0.40f, 0.88f, 0.52f, 0.32f, 0.70f, 0.46f, 0.92f,
)

/** Seconds for one full sweep of the band's travelling wave. */
private const val HeroBandPeriodMs = 3400

/**
 * The statement card: brand set large, version under it, and a graphic band across the foot.
 *
 * OxygenOS gives this card the whole top of the page and puts nothing in it but the slogan and an
 * illustration — the identity, at a size no other element on the screen competes with. This is that
 * card: a spectrum band standing in for their confetti strip, since the thing being identified is a
 * music player.
 *
 * It counts taps. Nothing visible acknowledges them until the fourth, at which point the mark
 * starts leaning into each press a little harder than a normal tap would — enough that someone
 * poking at it realises something is happening, and invisible to someone who is not. On the
 * seventh, [ExhaleBreathingEgg] opens.
 */
@Composable
private fun AboutHero(
    onSecretUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var taps by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // The tell. Below the halfway mark this is an ordinary, almost imperceptible press response;
    // past it the mark visibly winds up, which is the only hint the egg exists.
    val warmth = (taps.toFloat() / SecretTapCount).coerceIn(0f, 1f)
    val markScale by animateFloatAsState(
        targetValue = if (isPressed) 1f - 0.04f - 0.10f * warmth else 1f + 0.06f * warmth,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "markScale",
    )
    val markRotation by animateFloatAsState(
        targetValue = if (isPressed) -8f * warmth else 0f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "markRotation",
    )

    // Taps have to be consecutive-ish; wandering off and coming back later starts over.
    LaunchedEffect(taps) {
        if (taps in 1 until SecretTapCount) {
            kotlinx.coroutines.delay(2_500)
            taps = 0
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bandPalette = remember(primary, secondary, tertiary) {
        listOf(primary, tertiary, secondary)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.HeroCardCornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                val next = taps + 1
                if (next >= SecretTapCount) {
                    taps = 0
                    onSecretUnlocked()
                } else {
                    taps = next
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // No app icon above the wordmark. OxygenOS puts nothing in this card but the words and
            // the illustration, and an icon here was competing with the type for the one job the
            // card has. The mark still fronts the row in Settings that navigates here, so it is not
            // lost — it is just not repeated at the destination.
            //
            // The press response therefore moves onto the wordmark itself, which is now the thing
            // being tapped.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = markScale
                    scaleY = markScale
                    rotationZ = markRotation
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // The graphic band. OxygenOS ends its card with a strip of confetti; a music player ends it
        // with a spectrum — and this one is alive, which is the point: on a page that is otherwise
        // a table of static facts, the identity card is the one thing that should look like it is
        // running.
        //
        // Drawn edge to edge inside the card's clip so the bottom corners cut it, which is what
        // makes it read as part of the card rather than a widget sitting on it.
        AboutHeroBand(
            palette = bandPalette,
            warmth = warmth,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }
}

/**
 * The living spectrum band.
 *
 * A travelling wave, not 28 independent oscillators: each bar's phase is offset by its position, so
 * the crest moves left to right across the card instead of the whole band pulsing in unison. The
 * resting silhouette in [HeroBandHeights] is what the wave modulates, which keeps it looking like a
 * spectrum rather than a sine curve.
 *
 * Everything happens in the **draw phase**. One `Animatable`-backed float feeds a `drawBehind`
 * lambda, so an animation running forever on a settings page costs one draw invalidation a frame
 * and never recomposes or re-lays-out anything — the same discipline the dock's capsule uses.
 * Doing this with 28 animated `Modifier.height()` values would relayout the row 60 times a second
 * for as long as the page is open.
 */
@Composable
private fun AboutHeroBand(
    palette: List<Color>,
    warmth: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "heroBand")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HeroBandPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "heroBandPhase",
    )

    Box(
        modifier = modifier.drawBehind {
            val count = HeroBandHeights.size
            val gap = 4.dp.toPx()
            val sidePad = 14.dp.toPx()
            val usable = size.width - sidePad * 2f - gap * (count - 1)
            if (usable <= 0f) return@drawBehind
            val barWidth = usable / count
            val radius = CornerRadius(barWidth.coerceAtMost(6.dp.toPx()) / 2f)

            HeroBandHeights.forEachIndexed { index, rest ->
                // Each bar sits a fixed distance further along the wave than the one before it.
                val swing = sin(phase + index * 0.55f)
                // Amplitude grows with the tap counter: the band winding up is the second, quieter
                // tell that something is behind this card.
                val amplitude = 0.16f + 0.22f * warmth
                val fraction = (rest + swing * amplitude).coerceIn(0.10f, 1f)

                val barHeight = size.height * fraction
                val left = sidePad + index * (barWidth + gap)
                drawRoundRect(
                    color = palette[index % palette.size]
                        .copy(alpha = 0.18f + 0.50f * fraction),
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        },
    )
}

// ─── Stat cards ───────────────────────────────────────────────────────────────

/**
 * One of the two cards in the pair under the hero: a glyph, a quiet label, a loud value.
 *
 * Sized by its content rather than by a fixed height so the pair stays level whichever of the two
 * values wraps — a `height()` here would clip the longer one the first time a version string grew.
 */
@Composable
private fun AboutStatCard(
    icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        // The gap is the design. OxygenOS pins the glyph to the top of these cards and the
        // label/value pair to the bottom, and the empty band between them is what stops a
        // two-line card from reading as a cramped list row.
        Spacer(Modifier.height(44.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Grouped rows ─────────────────────────────────────────────────────────────

@Composable
private fun AboutGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)),
    ) {
        content()
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
        thickness = SettingsDimensions.DividerThickness,
        color = SettingsDimensions.dividerColor(),
    )
}

/** A row fronted by a circular photograph rather than a glyph tile: a person, not a setting. */
@Composable
private fun AboutPersonRow(
    avatarUrl: String,
    name: String,
    role: String,
    onClick: () -> Unit,
) {
    AboutRowScaffold(onClick = onClick) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(SettingsDimensions.RowIconSize)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    shape = CircleShape,
                ),
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AboutChevron()
    }
}

@Composable
private fun AboutRow(
    icon: Int,
    title: String,
    value: String?,
    onClick: () -> Unit,
    // A chevron promises another screen. Rows that act on the spot must not wear one.
    showChevron: Boolean = true,
) {
    AboutRowScaffold(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(SettingsDimensions.RowIconSize)
                .settingsIconPuck(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(SettingsDimensions.RowIconCornerRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }

        if (showChevron) AboutChevron()
    }
}

/**
 * A row that states a fact: label left, value right. No chevron, no press state, no icon —
 * nothing here is tappable and nothing here needs distinguishing from its neighbours.
 *
 * The value is allowed to wrap to three lines and stays right-aligned when it does, which is how
 * OxygenOS handles a long processor name. Ellipsising it instead would hide exactly the tail of the
 * string — the build suffix, the ABI — that someone reading this page came for.
 */
@Composable
private fun AboutValueRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The hairline for a group whose rows have no leading icon: full width, no 66dp indent. */
@Composable
private fun AboutPlainDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = SettingsDimensions.RowHorizontalPadding),
        thickness = SettingsDimensions.DividerThickness,
        color = SettingsDimensions.dividerColor(),
    )
}

@Composable
private fun AboutChevron() {
    Icon(
        painter = painterResource(R.drawable.navigate_next),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.size(SettingsDimensions.ChevronSize),
    )
}

/** Shared row geometry and the iOS press fill, so every About row behaves like a Settings row. */
@Composable
private fun AboutRowScaffold(
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.09f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "aboutRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
