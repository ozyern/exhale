/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ozyern.exhale.ui.component.backdrop.backdrops.LayerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.layerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Provides attached backdrop and Haze wiring for flat screens. Keep [FrostedTopBar] a sibling of the captured scrolling content to avoid self-capture. */
@Stable
class GlassScreenChrome internal constructor(
    val backdrop: LayerBackdrop,
    val hazeState: HazeState,
    private val freeze: com.ozyern.exhale.ui.component.backdrop.backdrops.BackdropFreeze,
) {
    // Reuse the scrolling content's frozen state to avoid re-running its effects on every scroll frame.
    val frozen: () -> Boolean get() = freeze.frozen
    // Keep the haze source around scrollable content only. If it contains the blur overlay,
    // Haze captures that overlay itself and the blur has no useful source.
    /** Apply to a Box that wraps only the screen's scrolling content. */
    fun screenModifier(): Modifier = Modifier.hazeSource(hazeState)

    /** Apply to the screen's scrolling root (LazyColumn/Column/etc). */
    fun contentModifier(): Modifier = Modifier
        .nestedScroll(freeze.connection)
        // Isolate sibling redraws and flatten list content for the backdrop capture.
        .graphicsLayer()
        .layerBackdrop(backdrop, frozen = freeze.frozen)
        .graphicsLayer()
}

@Composable
fun rememberGlassScreenChrome(background: Color): GlassScreenChrome {
    val freeze = rememberBackdropFreeze()
    val backdrop = rememberLayerBackdrop(
        // Fills the screen background into the capture BEFORE content, same
        // reason MainActivity's appBackdrop does: without it the list records
        // onto a transparent canvas and the blur reads as part-transparent /
        // ghosted instead of solid frosted content.
        onDraw = remember(background) {
            val bg = background
            { drawRect(bg); drawContent() }
        }
    )
    val hazeState = remember { HazeState() }
    return remember(backdrop, hazeState, freeze) { GlassScreenChrome(backdrop, hazeState, freeze) }
}
