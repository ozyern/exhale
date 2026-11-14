/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.ComposeShader
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Slowly advancing phase in radians. It only runs while [visible] is true and [paused] is false,
 * the app is resumed, motion is not frozen or reduced, and animations are enabled.
 */
@Composable
internal fun rememberMeshPhase(visible: State<Boolean>, paused: () -> Boolean): State<Float> {
    val (reduceMotion) = rememberPreference(ReduceDynamicBlurKey, false)
    val lifecycleOwner = LocalLifecycleOwner.current
    val motionFrozen = LocalAppMotionFrozen.current
    val currentPaused = rememberUpdatedState(paused)
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lifecycleOwner, motionFrozen, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val durationScale = currentCoroutineContext()[MotionDurationScale]
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                visible.value && !motionFrozen.value && !currentPaused.value() &&
                    (durationScale?.scaleFactor ?: 1f) > 0f
            }.collectLatest { running ->
                if (!running) return@collectLatest
                var lastUpdate = withFrameNanos { it }
                while (currentCoroutineContext().isActive) {
                    val frame = withFrameNanos { it }
                    if (frame - lastUpdate >= MeshFrameIntervalNanos) {
                        val elapsedSeconds = ((frame - lastUpdate) / 1_000_000_000f)
                            .coerceAtMost(0.1f)
                        val scale = durationScale?.scaleFactor ?: 1f
                        if (scale > 0f) {
                            phase.floatValue = (phase.floatValue +
                                elapsedSeconds * MeshRadiansPerSecond / scale) % MeshCycleRadians
                        }
                        lastUpdate = frame
                    }
                }
            }
        }
    }
    return phase
}

@Composable
fun AnimatedChromeMesh(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paused: () -> Boolean = { false },
    primaryColor: Color = IosTheme.tint,
    secondaryColor: Color = IosTheme.colors.systemIndigo,
    alpha: Float = 0.2f,
    fadeAtBottom: Boolean = true,
) {
    if (!enabled || alpha <= 0f) return

    val visible = remember { mutableStateOf(false) }
    val phase = rememberMeshPhase(visible, paused)

    Box(
        // Isolate mesh redraws from cached artwork and backdrop layers.
        modifier = modifier.graphicsLayer().onGloballyPositioned { coordinates ->
            val onScreen = coordinates.isAttached && !coordinates.boundsInWindow().isEmpty
            if (visible.value != onScreen) visible.value = onScreen
        }.drawWithCache {
            val width = size.width
            val height = size.height
            val opacity = alpha.coerceIn(0f, 1f)
            val colors = arrayOf(primaryColor, secondaryColor, lerp(primaryColor, secondaryColor, 0.5f))
            val fadeStart = if (fadeAtBottom) AndroidColor.WHITE else AndroidColor.TRANSPARENT
            val fadeEnd = if (fadeAtBottom) AndroidColor.TRANSPARENT else AndroidColor.WHITE
            val fade = LinearGradient(
                0f, 0f, 0f, height.coerceAtLeast(1f),
                intArrayOf(fadeStart, AndroidColor.WHITE, fadeEnd),
                if (fadeAtBottom) floatArrayOf(0f, 0.22f, 1f) else floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            val radius = max(width * 0.58f, height * 1.35f).coerceAtLeast(1f)
            val centerY = height * if (fadeAtBottom) 0.2f else 0.75f
            val paints = Array(colors.size) { index ->
                val color = colors[index]
                val radial = RadialGradient(
                    width * MeshAnchors[index], centerY, radius,
                    intArrayOf(
                        color.copy(alpha = color.alpha * opacity).toArgb(),
                        color.copy(alpha = color.alpha * opacity * 0.7f).toArgb(),
                        color.copy(alpha = 0f).toArgb(),
                    ),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP,
                )
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = ComposeShader(radial, fade, PorterDuff.Mode.DST_IN)
                }
            }

            onDrawBehind {
                val drift = phase.value
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val clipSave = nativeCanvas.save()
                    nativeCanvas.clipRect(0f, 0f, width, height)
                    for (index in paints.indices) {
                        val offset = width * 0.12f *
                            sin(drift * MeshSpeeds[index] + MeshOffsets[index])
                        val blobSave = nativeCanvas.save()
                        nativeCanvas.translate(offset, 0f)
                        nativeCanvas.drawRect(-offset, 0f, width - offset, height, paints[index])
                        nativeCanvas.restoreToCount(blobSave)
                    }
                    nativeCanvas.restoreToCount(clipSave)
                }
            }
        },
    )
}

private const val MeshFrameIntervalNanos = 33_000_000L
private val MeshCycleRadians = (2 * PI).toFloat()
private val MeshRadiansPerSecond = MeshCycleRadians / 32f
private val MeshAnchors = floatArrayOf(0.12f, 0.5f, 0.88f)
private val MeshSpeeds = floatArrayOf(1f, 1f, 2f)
private val MeshOffsets = floatArrayOf(0f, 2.1f, 4.2f)
