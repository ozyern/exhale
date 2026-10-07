/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

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
    // space above it — mirroring Apple Music's Search layout. When false the classic
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + AppBarHeight)
                .background(color = MaterialTheme.colorScheme.surface)
        )

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
        val surfaceColor = if (inputAtBottom) {
            activeOverlayColor.copy(alpha = animationProgress)
        } else {
            colors.containerColor
        }
        Surface(
            shape = animatedShape,
            color = surfaceColor,
            contentColor = contentColorFor(colors.containerColor),
            tonalElevation = if (inputAtBottom) 0.dp else tonalElevation,
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
                        // also happens to be the motion Apple Music uses.
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
                            ) {
                                content()
                            }
                        }
                    }

                    val pillHorizontalPadding = lerp(0.dp, SearchBarHorizontalPadding, animationProgress)
                    val pillBottomPadding = lerp(0.dp, bottomBarPadding, animationProgress)
                    // Apple-Music search field: a heavily-frosted, pill-shaped glass capsule that
                    // reads as part of the floating bottom chrome — NOT a flat Material text field.
                    // Uses the same genuine Kyant liquid-glass backdrop as the nav bar so content
                    // scrolling behind it refracts through the pill. When the field is active a
                    // "Cancel" text button slides in on the trailing edge (outside the capsule),
                    // exactly like iOS.
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

                        AnimatedVisibility(
                            visible = active,
                            enter = fadeIn(tween(200)) + expandHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                expandFrom = Alignment.Start,
                            ),
                            exit = fadeOut(tween(150)) + shrinkHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                shrinkTowards = Alignment.Start,
                            ),
                        ) {
                            // The cancel affordance, as glass rather than as a bare glyph.
                            //
                            // It sits immediately beside a frosted pill, which is what made the
                            // old version stand out: one half of the row was a surface and the
                            // other was ink printed on the background. Same disc, same lens, same
                            // press as the back arrow and the app-bar search icon.
                            LiquidGlassIconButton(
                                onClick = { onActiveChange(false) },
                                icon = R.drawable.expand_more, // Closes search; the X inside the field only clears it.
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = MaterialTheme.colorScheme.primary,
                                diameter = 34.dp,
                                iconSize = 17.dp,
                                modifier = Modifier.padding(start = 6.dp),
                                // The pill's own backdrop. This sits in chrome, immediately beside
                                // the bar, so it can and should bend exactly the pixels the bar is
                                // bending — anything else and two touching surfaces would be
                                // showing two different views of what is behind them.
                                backdrop = LocalAppBackdrop.current,
                            )
                        }
                    }
                }
            } else {
                Column {
                    inputField(Modifier.padding(animatedInputFieldPadding))

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
    val isDark = isSystemInDarkTheme()
    // Thicker frost than the nav dock: this is a place to type, so it has to read as a solid
    // surface. Same correction as the dock — the old Kyant path refracted an empty backdrop
    // and painted nothing but its own film.
    val glass = rememberChromeGlassModifier(
        shape = shape,
        dark = isDark,
        tintAlpha = if (isDark) 0.42f else 0.38f,
        blurRadius = 56.dp,
        quality = 0.5f,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        Box(modifier = modifier.then(glass)) { content() }
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
// 44dp: a slim, sleek pill matching Apple Music's search-field proportions —
// noticeably thinner than the stock M3 48dp input row.
val InputFieldHeight = 44.dp
private val SearchBarCornerRadius: Dp = InputFieldHeight / 2
internal val SearchBarVerticalPadding: Dp = 8.dp
internal val SearchBarHorizontalPadding: Dp = 12.dp
val SearchBarIconOffsetX: Dp = 4.dp
private const val AnimationDurationMillis: Int = 300