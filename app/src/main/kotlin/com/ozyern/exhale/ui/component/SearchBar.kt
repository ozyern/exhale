/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.component.foldingdock.foldingDockGlass
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.TonalElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppBarHeight
import kotlin.math.max

@ExperimentalMaterial3Api
@Composable
fun TopSearch(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    tonalElevation: Dp = TonalElevation,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
    // When true the input field is docked at the BOTTOM of the (full-screen) search
    // surface as a floating frosted pill, with the result/browse content filling the
    // space above it — mirroring the iOS music app's Search layout. When false the classic
    // top-anchored search bar layout is used.
    inputAtBottom: Boolean = false,
    // Space reserved below the docked pill so it floats above the system nav bar and
    // the app's own floating nav / mini-player. Only used when [inputAtBottom] is true.
    bottomBarPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A critically-damped spring rather than a fixed-duration linear-ish tween: it leaves
    // fast on the first frames (where the gesture's intent is legible) and settles without
    // overshoot, which is what makes the expansion read as physical instead of timed.
    // Damping is exactly 1, so the value still lands on 1f/0f precisely and the `== 1f`
    // shape check below stays exact.
    val animationProgress: Float by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 420f,
            visibilityThreshold = 0.001f,
        ),
        label = "SearchBarAnimation",
    )

    val defaultInputFieldShape = SearchBarDefaults.inputFieldShape
    val defaultFullScreenShape = SearchBarDefaults.fullScreenShape
    val animatedShape by remember {
        derivedStateOf {
            when {
                shape == defaultInputFieldShape -> {
                    val animatedRadius = SearchBarCornerRadius * (1 - animationProgress)
                    RoundedCornerShape(CornerSize(animatedRadius))
                }
                animationProgress == 1f -> defaultFullScreenShape
                else -> shape
            }
        }
    }

    val topInset = windowInsets.asPaddingValues().calculateTopPadding()
    val startInset = windowInsets.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
    val endInset = windowInsets.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)

    val topPadding = SearchBarVerticalPadding + topInset
    val animatedSurfaceTopPadding = lerp(topPadding, 0.dp, animationProgress)
    val animatedInputFieldPadding by remember {
        derivedStateOf {
            PaddingValues(
                start = startInset * animationProgress,
                top = topPadding * animationProgress,
                end = endInset * animationProgress,
                bottom = SearchBarVerticalPadding * animationProgress,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.offset { IntOffset(x = 0, y = 0) },
        propagateMinConstraints = true,
    ) {
        val height: Dp
        val width: Dp
        val startPadding: Dp
        val endPadding: Dp
        // The height the result list is measured at, ONCE, regardless of how tall the
        // surface currently is. See the `requiredHeight` note in the docked branch below.
        val contentTargetHeight: Dp
        with(LocalDensity.current) {
            val startWidth = constraints.maxWidth.toFloat()
            val startHeight = max(constraints.minHeight, InputFieldHeight.roundToPx())
                .coerceAtMost(constraints.maxHeight)
                .toFloat()
            val endWidth = constraints.maxWidth.toFloat()
            val endHeight = constraints.maxHeight.toFloat()

            contentTargetHeight =
                (endHeight - InputFieldHeight.roundToPx() - bottomBarPadding.roundToPx())
                    .coerceAtLeast(0f)
                    .toDp()
            height = lerp(startHeight, endHeight, animationProgress).toDp()
            width = lerp(startWidth, endWidth, animationProgress).toDp()
            startPadding = lerp(
                (SearchBarHorizontalPadding + startInset).roundToPx().toFloat(),
                0f,
                animationProgress
            ).toDp()
            endPadding = lerp(
                (SearchBarHorizontalPadding + endInset).roundToPx().toFloat(),
                0f,
                animationProgress
            ).toDp()
        }

        // No band behind the bar. There was an opaque strip the height of the status bar and the
        // bar, in the surface colour, so the page could never pass under the field and the glass
        // had nothing to bend — it read as a flat dark bar with a rim.

        // Bottom-docked (Apple-Music) layout: while IDLE the outer surface is 100% transparent —
        // the browse content paints its own background and any container color here would read
        // as an opaque slab behind the docked pill. But once the field goes ACTIVE the overlay
        // owns the whole screen: it MUST be fully opaque, otherwise the category grid and other
        // route UI bleed straight through the history list and pill row (transparency bug).
        // Fading with animationProgress keeps the open/close transition smooth.
        val activeOverlayColor = if (colors.containerColor == Color.Black) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.background
        }
        // Both layouts: the page fades in behind the field as search opens, and until then there is
        // no surface at all — the field is a pill of glass on its own. At the top it used to be a
        // flat Material bar in surfaceContainerLow, the one piece of chrome with no glass in it.
        val surfaceColor = activeOverlayColor.copy(alpha = animationProgress)
        Surface(
            shape = animatedShape,
            color = surfaceColor,
            contentColor = contentColorFor(colors.containerColor),
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(
                    top = animatedSurfaceTopPadding,
                    start = startPadding,
                    end = endPadding,
                )
                .size(width = width, height = height),
        ) {
            // The input field is identical in both layouts; only its position differs.
            val inputField: @Composable (Modifier) -> Unit = { fieldModifier ->
                SearchBarInputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    active = active,
                    onActiveChange = onActiveChange,
                    modifier = fieldModifier,
                    enabled = enabled,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    colors = TextFieldDefaults.colors(
                        // iOS-style typing field: the text you type and the caret both carry
                        // the app accent, and there is NO Material underline in any state.
                        focusedTextColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    interactionSource = interactionSource,
                    focusRequester = focusRequester,
                    leftFocusRequester = leftFocusRequester,
                )
            }

            if (inputAtBottom) {
                // Apple-Music layout: browse/result content fills the top, the search
                // field floats as a frosted pill docked at the bottom of the screen.
                Column(Modifier.fillMaxSize()) {
                    if (animationProgress > 0) {
                        // PERF: the surface's height animates from the collapsed bar to the
                        // full screen, and this used to be `weight(1f)` — so the entire
                        // result list (two LazyColumns behind a Crossfade, plus the browse
                        // grid) was re-measured and re-laid-out on EVERY frame of the
                        // expansion, from one pixel tall upward. That is the search-page
                        // stutter: not the blur, not the network, a full layout pass 60×/s
                        // over the heaviest subtree in the app.
                        //
                        // `requiredHeight` overrides the incoming constraint, so the list is
                        // measured once at its final height and the growing surface simply
                        // clips it. Anchoring to the bottom means the window opens upward
                        // from the field — the list is revealed rather than stretched, which
                        // also happens to be the motion the iOS music app uses.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .requiredHeight(contentTargetHeight)
                                    .fillMaxWidth()
                                    // The docked layout bypasses the classic top-anchored inset
                                    // handling, so the safe-area inset must be applied here —
                                    // without it "Search history" rams into the status bar and
                                    // camera cutout. 12dp of extra breathing room below the inset.
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                                    )
                                    .padding(top = 12.dp)
                                    // Read inside graphicsLayer, not as a composition-phase
                                    // `alpha()` — this keeps the fade in the draw phase and off
                                    // the recomposition path entirely.
                                    .graphicsLayer { alpha = animationProgress },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Suggestions and results at a readable measure on a tablet.
                                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().weight(1f)) {
                                    content()
                                }
                            }
                        }
                    }

                    // The reference's typing field floats inside the screen rather than across it:
                    // about 36dp in from either side, plus the dock's own centring on a wide window.
                    val pillHorizontalPadding = lerp(
                        0.dp,
                        TypingPillSideInset + com.ozyern.exhale.ui.utils.safeHorizontalChromeInset(),
                        animationProgress,
                    )
                    val pillBottomPadding = lerp(0.dp, bottomBarPadding, animationProgress)
                    // The typing field: one capsule of the dock's clear glass riding the keyboard,
                    // its way out the arrow inside it — no separate cancel disc beside it.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(
                                start = pillHorizontalPadding,
                                end = pillHorizontalPadding,
                                bottom = pillBottomPadding,
                            ),
                    ) {
                        SearchFrostedPill(
                            contentColor = contentColorFor(colors.containerColor),
                            modifier = Modifier.weight(1f),
                        ) {
                            // NO animated padding here: that padding (which includes the status-bar
                            // inset) belongs to the classic top-anchored expansion. Applied to the
                            // docked pill it ballooned the capsule into a chunky slab when focused.
                            // The pill keeps its slim InputFieldHeight in every state.
                            inputField(Modifier)
                        }
                    }
                }
            } else {
                Column {
                    // The field at the top is the dock's glass — the reference design's — so the bar at the
                    // top and the bar at the bottom are one material.
                    Box(
                        Modifier
                            .padding(animatedInputFieldPadding)
                            .padding(horizontal = lerp(0.dp, 12.dp, animationProgress))
                            .clearGlass(RoundedCornerShape(percent = 50))
                            .clip(RoundedCornerShape(percent = 50)),
                    ) {
                        inputField(Modifier)
                    }

                    if (animationProgress > 0) {
                        // Draw-phase, matching the docked branch above: `animationProgress`
                        // ticks every frame of the expansion, and reading it during composition
                        // recomposed the entire result list underneath on each one.
                        Column(Modifier.graphicsLayer { alpha = animationProgress }) {
                            HorizontalDivider(color = colors.dividerColor)
                            content()
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = active) {
        onActiveChange(false)
    }
}

/**
 * Frosted, pill-shaped glass capsule hosting the docked search input — the Apple-Music look.
 *
 * Draws a genuine Kyant liquid-glass backdrop (blur + lens refraction) with a milky translucent
 * film on top so it stays legible on the first frame, then clips its children to the capsule. No
 * Material underline / container: the [SearchBarInputField] renders with transparent containers so
 * only this glass shell shows.
 */
@Composable
private fun SearchFrostedPill(
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    // The reference's typing field is the dock's clear lens glass: the page shows through it, bent
    // at the rim, rather than a thick frosted slab.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        Box(
            modifier = modifier
                .clip(shape)
                .foldingDockGlass(shape),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarInputField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
) {
    val focused = interactionSource.collectIsFocusedAsState().value
    val textColor = LocalTextStyle.current.color.takeOrElse {
        if (focused) colors.focusedTextColor else colors.unfocusedTextColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(InputFieldHeight),
    ) {
        if (leadingIcon != null) {
            Spacer(Modifier.width(SearchBarIconOffsetX))
            leadingIcon()
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .then(
                    if (leftFocusRequester != null) {
                        Modifier.focusProperties {
                            left = leftFocusRequester
                        }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (upEvent != null) {
                            onActiveChange(true)
                        }
                    }
                }
                .semantics {
                    contentDescription = "Search"
                    if (active) {
                        stateDescription = "Suggestions available"
                    }
                }
                .onKeyEvent {
                    if (it.key == Key.Enter) {
                        onSearch(query.text)
                        return@onKeyEvent true
                    }
                    false
                },
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query.text) }),
            interactionSource = interactionSource,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = query.text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = colors,
                    contentPadding = PaddingValues(),
                    container = {},
                )
            },
        )

        if (trailingIcon != null) {
            trailingIcon()
            Spacer(Modifier.width(SearchBarIconOffsetX))
        }
    }
}

// Measurement specs
// 44dp: a slim, sleek pill matching the iOS music app's search-field proportions —
// noticeably thinner than the stock M3 48dp input row.
val InputFieldHeight = 44.dp
private val SearchBarCornerRadius: Dp = InputFieldHeight / 2
internal val SearchBarVerticalPadding: Dp = 8.dp
internal val SearchBarHorizontalPadding: Dp = 12.dp
val SearchBarIconOffsetX: Dp = 4.dp
private const val AnimationDurationMillis: Int = 300
/** How far in from each side the typing field floats, as the reference sets it. */
private val TypingPillSideInset = 36.dp
