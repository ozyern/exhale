package com.ozyern.exhale.ui.player

import com.ozyern.exhale.ui.utils.resize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavController
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.player.customize.DiyOrientation
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.utils.rememberPreference

/** Keeps the outgoing pane alive while returning to the classic cover. */
@Stable
class PlayerAuxiliaryPageState : MutableState<PlayerInternalState> {
    var showing by mutableStateOf(false)
        private set
    private var retainedPage by mutableStateOf(PlayerInternalState.QUEUE)

    override var value: PlayerInternalState
        get() = retainedPage
        set(page) {
            showing = page != PlayerInternalState.COVER
            if (showing) retainedPage = page
        }

    override fun component1() = value
    override fun component2(): (PlayerInternalState) -> Unit = { value = it }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalAuxiliaryArtworkScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalAuxiliaryVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Joins the classic cover to V2's header only during a cover/pane transition. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.auxiliaryArtworkMorph(): Modifier {
    val shared = LocalAuxiliaryArtworkScope.current ?: return this
    val visibility = LocalAuxiliaryVisibilityScope.current ?: return this
    return with(shared) {
        this@auxiliaryArtworkMorph.sharedElement(
            rememberSharedContentState("classic-player-artwork"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> Motion.artwork() },
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 1f,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.auxiliaryBoundsMorph(key: String, inOverlay: Boolean = true): Modifier {
    val shared = LocalAuxiliaryArtworkScope.current ?: return this
    val visibility = LocalAuxiliaryVisibilityScope.current ?: return this
    return with(shared) {
        this@auxiliaryBoundsMorph.sharedBounds(
            rememberSharedContentState("classic-player-$key"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> Motion.artwork() },
            renderInOverlayDuringTransition = inOverlay,
            zIndexInOverlay = 2f,
        )
    }
}

@Composable
internal fun auxiliaryArtworkTransitionRunning(): Boolean =
    LocalAuxiliaryVisibilityScope.current?.transition?.let {
        it.currentState != it.targetState
    } == true

/**
 * The classic player's queue and lyrics pages live inside the classic layout, so its background and
 * controls stay put. One shared-transition scope spans the whole layout, which lets the title and
 * artist in the controls morph into the page header.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PlayerAuxiliaryPages(
    pages: PlayerAuxiliaryPageState,
    state: BottomSheetState,
    navController: NavController,
    forcedOrientation: DiyOrientation?,
    isPreviewOnly: Boolean,
    coverContent: @Composable () -> Unit,
) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAuxiliaryArtworkScope provides this) {
            coverContent()
        }
    }
}

/**
 * The slot where the classic cover sits. Queue and lyrics use a fading artwork copy in the page
 * header, leaving the main cover in place while the page rises beneath it. [cover] is whatever the
 * classic player shows normally.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ClassicPageRegion(
    pages: PlayerAuxiliaryPageState,
    modifier: Modifier = Modifier,
    fadeOnly: Boolean = false,
    cover: @Composable () -> Unit,
    header: @Composable () -> Unit,
    page: @Composable (PlayerInternalState) -> Unit,
) {
    val reduceMotion by rememberPreference(ReduceDynamicBlurKey, false)
    val systemAnimations = android.animation.ValueAnimator.areAnimatorsEnabled()
    val reduced = reduceMotion || !systemAnimations
    AnimatedContent(
        targetState = if (pages.showing) pages.value else PlayerInternalState.COVER,
        transitionSpec = {
            val enterFade = fadeIn(Motion.gate(reduced, Motion.artwork()))
            val exitFade = fadeOut(Motion.gate(reduced, Motion.artwork()))
            val enter = if (fadeOnly) enterFade else enterFade +
                scaleIn(Motion.gate(reduced, Motion.artwork()), initialScale = if (reduced) 1f else 0.95f)
            val exit = if (fadeOnly) exitFade else exitFade +
                scaleOut(Motion.gate(reduced, Motion.artwork()), targetScale = if (reduced) 1f else 0.97f)
            enter.togetherWith(exit).using(SizeTransform(clip = false)).apply {
                targetContentZIndex = if (targetState != PlayerInternalState.COVER) 1f else 0f
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "classic-player-pages",
    ) { target ->
        CompositionLocalProvider(LocalAuxiliaryVisibilityScope provides this) {
            if (target == PlayerInternalState.COVER) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { cover() }
            } else {
                // Same rise-in as V2's lists.
                val rise = remember { Animatable(0f) }
                LaunchedEffect(Unit) { rise.animateTo(1f, Motion.appear()) }
                Column(Modifier.fillMaxSize()) {
                    header()
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = (1f - rise.value) * size.height
                                alpha = rise.value
                            },
                    ) { page(target) }
                }
            }
        }
    }
}

/** The small cover at the top of a page: the same style as the big one, tapping it goes back. */
@Composable
internal fun ClassicPageHeader(
    artworkUrl: String?,
    mediaId: String?,
    coverStyle: PlayerCoverStyle,
    isPlaying: Boolean,
    cropArtwork: Boolean,
    title: String,
    artist: String,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    actions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rotation = rememberCoverRotation(coverStyle, isPlaying)
    val coverShape = coverStyle.shape(7.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .coverMorph(coverStyle)
                .size(60.dp)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(coverShape)
                .graphicsLayer { rotationZ = if (coverStyle.artSpinsWithShape) 0f else -rotation.value }
                .clickable(onClick = onClick),
        ) {
            ThumbnailImage(
                artworkUri = artworkUrl?.resize(1200, 1200),
                cropArtwork = cropArtwork,
                crossfadeMillis = 300,
                contentKey = mediaId,
                modifier = Modifier.fillMaxSize(),
            )
            coverStyle.Decoration(Modifier.fillMaxSize())
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            com.ozyern.exhale.ui.ios.compat.Text(
                text = title,
                style = com.ozyern.exhale.ui.ios.compat.AppTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.auxiliaryBoundsMorph("title"),
            )
            com.ozyern.exhale.ui.ios.compat.Text(
                text = artist,
                style = com.ozyern.exhale.ui.ios.compat.AppTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.auxiliaryBoundsMorph("artist", inOverlay = false),
            )
        }
        Row(
            modifier = Modifier.auxiliaryBoundsMorph("actionButtons"),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}
