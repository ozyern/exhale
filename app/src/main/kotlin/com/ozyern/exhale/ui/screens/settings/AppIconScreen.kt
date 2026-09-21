/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppIconPackKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.AppIconPack
import com.ozyern.exhale.utils.rememberPreference

/**
 * Settings → Appearance → Display → App icon.
 *
 * A page, because the thing being chosen is a picture and pictures want room. The dialog this
 * replaces put two 90dp thumbnails side by side under a heading and a warning, which is enough to
 * tell the two apart but not enough to *choose* between them — at that size the dark mark's glow,
 * which is the entire difference, was four grey pixels.
 *
 * Each option is a full-width row: the icon at the size a home screen actually draws it, its name,
 * a line saying what it is, and a check. Tapping one applies it immediately — there is no confirm
 * button, because the change is instant, obvious and reversible by tapping the other one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconScreen(navController: NavController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // The preference is written alongside, but the *system* is what is read back: a reinstall
    // resets every component to its manifest default while DataStore survives, and a page showing
    // a selection the launcher is not honouring is worse than no page.
    val (storedPack, onStoredPackChange) = rememberPreference(
        AppIconPackKey,
        defaultValue = AppIconPack.DEFAULT_PACK.name,
    )
    var activePack by remember { mutableStateOf(AppIconPack.current(context)) }
    LaunchedEffect(storedPack) { activePack = AppIconPack.current(context) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        // The current mark, big, before the list of choices. A picker that opens on a grid of
        // equal thumbnails makes you hunt for which one you are already using.
        AppIconHero(pack = activePack)

        Spacer(Modifier.height(28.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            AppIconPack.entries.forEach { pack ->
                AppIconOptionRow(
                    pack = pack,
                    selected = pack == activePack,
                    onClick = {
                        if (pack != activePack) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            AppIconPack.applyTo(context, pack)
                            onStoredPackChange(pack.name)
                            activePack = pack
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_icon_applies_to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            // Said on the page, not after the tap. The icon vanishing off the home screen for a
            // second looks exactly like something breaking if nobody warned you.
            Text(
                text = stringResource(R.string.app_icon_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.app_icon)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

/** The selected mark at home-screen size, with its name under it. */
@Composable
private fun AppIconHero(pack: AppIconPack) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AdaptiveIconPreview(
            pack = pack,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(112.dp),
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(pack.labelRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One choosable pack: the mark, its name, what it is, and whether it is on.
 *
 * The row's own border is the selection state rather than a radio button, because the row *is* the
 * thing being selected and a control beside it would be a second target for the same tap.
 */
@Composable
private fun AppIconOptionRow(
    pack: AppIconPack,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "appIconRowBorder",
    )
    val borderWidth by animateDpAsState(
        if (selected) 2.dp else 1.dp,
        label = "appIconRowBorderWidth",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f))
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        AdaptiveIconPreview(
            pack = pack,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(60.dp),
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(pack.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(pack.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Springs in rather than appearing, so the tap has a visible result even when the launcher
        // takes a beat to catch up with the alias flip.
        val checkScale by animateFloatAsState(
            if (selected) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "appIconCheck",
        )

        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkScale }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A pack's launcher icon, drawn the way the launcher will draw it.
 *
 * Adaptive-icon layers are 108dp of which a mask shows the middle 72 — so a preview that renders
 * the layers at their natural size shows a sixth more picture than any home screen ever will, with
 * the bleed margin included and everything reading too small. Scaling by 108/72 inside a clipped
 * shape reproduces the actual crop.
 */
@Composable
private fun AdaptiveIconPreview(
    pack: AppIconPack,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape),
    ) {
        Image(
            painter = painterResource(pack.backgroundRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = ADAPTIVE_ICON_MASK_SCALE
                    scaleY = ADAPTIVE_ICON_MASK_SCALE
                },
        )
        Image(
            painter = painterResource(pack.foregroundRes),
            contentDescription = stringResource(pack.labelRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = ADAPTIVE_ICON_MASK_SCALE
                    scaleY = ADAPTIVE_ICON_MASK_SCALE
                },
        )
    }
}

/** 108dp of layer, 72dp of it visible through the mask. */
private const val ADAPTIVE_ICON_MASK_SCALE = 108f / 72f
