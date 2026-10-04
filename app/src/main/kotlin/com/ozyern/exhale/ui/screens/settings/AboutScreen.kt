/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ozyern.exhale.utils.DeviceNames
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ozyern.exhale.utils.Updater
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Offset
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
import com.ozyern.exhale.utils.rememberAppIconPack
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
    // The project's own channel first — releases and test builds land there — then the maintainer.
    SocialLink(R.drawable.telegram, "Exhale on Telegram", "@exhalemusic", "https://t.me/exhalemusic"),
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
 *  1. a tall **poster** — the mark and the brand set large on lit colour, with the build under it
 *     and one pill for the only action the page has;
 *  2. **grouped key/value rows** underneath, label left and value right.
 *
 * There is no pair of stat tiles between them any more: they stated the version and the
 * architecture, which is what the poster already says and what the table below says again.
 *
 * The rows in group 2 deliberately carry no icon pucks. A puck earns its place when it distinguishes
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
    com.ozyern.exhale.ui.component.ColorOsType {
        AboutScreenContent(navController, scrollBehavior)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(
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
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                ),
            contentPadding = PaddingValues(
                start = pad, end = pad, top = 4.dp,
                bottom = 40.dp + LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "hero") {
                AboutHero(
                    onSecretUnlocked = { showEasterEgg = true },
                    onUpdateClick = { navController.navigate("settings/update") },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            // (Version and architecture are on the poster and in the table below it; a pair of
            // cards repeating them between the two was the same fact three times on one screen.)

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
 * The poster: the mark, the name, the build and the state, on the release's own artwork.
 *
 * The same card the Updates page opens on - see [SettingsPosterCard]. A phone's About page and its
 * Software-update page show the same picture for the same reason: they are two views of one fact,
 * which release you are running, and both say at the foot of the card whether that release is
 * current. Tapping it goes where that sentence points: the Updates page.
 *
 * It also counts long presses. Nothing visible acknowledges them until the fourth, at which point
 * the card starts leaning into each press a little harder than a normal one would - enough that
 * someone holding it realises something is happening, and invisible to someone who is not. On the
 * seventh, [ExhaleBreathingEgg] opens. It moved off the tap when the tap became navigation; an
 * easter egg is meant to be hunted, and a hold is a better hiding place than a tap that now does
 * something.
 */
@Composable
private fun AboutHero(
    onSecretUnlocked: () -> Unit,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var taps by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "aboutHeroPress",
    )

    // Each tap on the hidden side throws another handful of confetti.
    var burst by remember { mutableIntStateOf(0) }

    LaunchedEffect(taps) {
        if (taps in 1 until SecretTapCount) {
            kotlinx.coroutines.delay(2_500)
            taps = 0
        }
    }

    // Asked once, on arrival, and quiet about it: until the answer comes back the card simply
    // does not claim a state. Same question the Updates page asks, so the two never disagree.
    var latestVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Updater.getLatestVersionName().onSuccess { latestVersion = it }
    }
    val status = latestVersion?.let {
        if (Updater.hasUpdate(it, BuildConfig.VERSION_NAME)) {
            stringResource(R.string.update_status_available)
        } else {
            stringResource(R.string.update_status_up_to_date)
        }
    }

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (flipped) {
                        burst++
                        // On the hidden side, taps are also the older egg: seven of them and it breathes.
                        val next = taps + 1
                        if (next >= SecretTapCount) {
                            taps = 0
                            onSecretUnlocked()
                        } else {
                            taps = next
                        }
                    } else {
                        onUpdateClick()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    flipped = !flipped
                    burst = 0
                    taps = 0
                },
            ),
    ) {
        // The About egg: hold the card and it turns graphite, confetti falls and piles up,
        // and the tagline comes up behind it. Hold again for the release card.
        androidx.compose.animation.AnimatedContent(
            targetState = flipped,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(320), initialScale = 0.97f)) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            },
            label = "aboutEgg",
            modifier = Modifier.fillMaxSize(),
        ) { hidden ->
            if (!hidden) {
                AboutReleaseCard(status = status, modifier = Modifier.fillMaxSize())
            } else {
                AboutConfettiEgg(
                    tagline = stringResource(R.string.about_tagline).replace(", ", ",\n"),
                    burst = burst,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp)),
                )
            }
        }
    }
}

/**
 * The front of the About card, laid out like ColorOS 17's "About device": the release's gold
 * artwork filling a rounded square, the glowing wordmark across its middle, the version under it in
 * light figures, and a frosted pill at the foot saying whether this release is current.
 */
@Composable
private fun AboutReleaseCard(status: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        Image(
            painter = painterResource(R.drawable.exhale_about_card),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // A faint darkening at the foot so the pill and the version sit on the picture rather
        // than fight its brightest highlights.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.35f),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Spacer(Modifier.weight(1f))
            com.ozyern.exhale.ui.component.ExhaleGlowWordmark(fontSize = 58.sp)
            com.ozyern.exhale.ui.component.ColorOsVersionText(BuildConfig.VERSION_NAME, fontSize = 19.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            if (status != null) {
                val pillShape = RoundedCornerShape(50)
                Text(
                    text = status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.28f), pillShape)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            } else {
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

private data class ConfettiShape(
    val x: Float,
    val y: Float,
    val size: Float,
    val aspect: Float,
    val angle: Float,
    val colorIndex: Int,
    val round: Boolean,
)

/**
 * The pair of cards under the poster: the two facts worth reading first.
 *
 * Phone About pages put exactly two here, side by side, because two is what fits at a size you can
 * read at a glance - everything else belongs in the table below. Version and architecture are ours:
 * the first is what you tell someone when something is wrong, the second is which build you are
 * actually on.
 */
@Composable
private fun AboutStatPair(
    version: String,
    architecture: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AboutStatCard(
            icon = R.drawable.info,
            label = stringResource(R.string.update_installed_version),
            value = version,
            modifier = Modifier.weight(1f),
        )
        AboutStatCard(
            icon = R.drawable.memory,
            label = stringResource(R.string.about_architecture),
            value = architecture,
            modifier = Modifier.weight(1f),
        )
    }
}

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
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(26.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            fontSize = 17.sp,
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
    // A coloured glyph on nothing, like every other settings row - see SettingsRow. These were the
    // last blue pucks left in the app: a column of identical primary-tinted tiles, all one colour
    // because they all took the theme accent rather than one of their own.
    val accent = IosAutoColors[(title.hashCode() and 0x7fffffff) % IosAutoColors.size]

    AboutRowScaffold(onClick = onClick) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(25.dp),
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
