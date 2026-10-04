/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * A progress ring made of liquid metal: the played part of the track is a band of chrome whose
 * highlights flow round it and ripple across its width, with the faint colour split real polished
 * metal shows at its edges. The unplayed part is a quiet track underneath.
 *
 * The metal only moves while [flowing] is true, so a paused song costs nothing per frame. Below
 * Android 13 there are no runtime shaders, and the ring is a still silver sweep instead.
 */
@Composable
fun Modifier.liquidMetalRing(
    progress: () -> Float,
    flowing: Boolean,
    trackColor: Color,
    strokeWidth: Dp = 3.dp,
    gap: Dp = 2.dp,
): Modifier {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(flowing) {
        if (!flowing) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
            }
        }
    }

    val shader = remember {
        // A shader that fails to compile on some GPU driver gets the silver sweep, not a crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LiquidMetalShader) }.getOrNull()
        } else {
            null
        }
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    return drawBehind {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f + gap.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

        val p = progress().coerceIn(0f, 1f)
        if (p <= 0f) return@drawBehind

        if (shader != null && brush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time.floatValue)
            shader.setFloatUniform("progress", p)
            shader.setFloatUniform("stroke", stroke)
            shader.setFloatUniform("radius", arcSize.width / 2f)
            // The shader draws the arc itself, caps included; this just gives it the pixels.
            drawRect(brush)
        } else {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFE9ECEF), Color(0xFF8D939A), Color(0xFFFFFFFF),
                        Color(0xFF6E747B), Color(0xFFD5D9DE), Color(0xFFE9ECEF),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private const val LiquidMetalShader = """
uniform float2 size;
uniform float time;
uniform float progress;
uniform float stroke;
uniform float radius;

const float TAU = 6.2831853;

// Brightness of polished metal along a flow coordinate: broad mid-greys with a few hard,
// bright reflections, which is what separates chrome from a plain grey gradient.
float chrome(float x) {
    float v = 0.5 + 0.5 * sin(x * TAU);
    v = smoothstep(0.0, 1.0, v);
    float sharp = pow(v, 8.0);
    float second = pow(0.5 + 0.5 * sin(x * TAU * 2.0 + 1.3), 12.0);
    return mix(0.20, 0.82, v) + sharp * 0.45 + second * 0.25;
}

half4 main(float2 p) {
    float2 c = size * 0.5;
    float2 d = p - c;
    float r = length(d);
    // Angle from the top, clockwise, in 0..TAU.
    float a = atan(d.x, -d.y);
    if (a < 0.0) a += TAU;
    float t = a / TAU;

    float halfW = stroke * 0.5;
    float radial = (r - radius) / halfW;
    float edge = 1.0 / halfW;
    float body = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, abs(radial));

    // The played arc, with round ends.
    float endA = progress * TAU;
    float inArc = a <= endA ? 1.0 : 0.0;
    float2 startP = c + float2(0.0, -radius);
    float2 endP = c + radius * float2(sin(endA), -cos(endA));
    float capS = 1.0 - smoothstep(halfW - 1.0, halfW + 1.0, length(p - startP));
    float capE = 1.0 - smoothstep(halfW - 1.0, halfW + 1.0, length(p - endP));
    float mask = max(body * inArc, max(capS, capE));
    if (mask <= 0.0) return half4(0.0);

    // The liquid: reflections drift round the ring and the surface ripples, bending them.
    float wobble = 0.16 * sin(t * TAU * 3.0 + time * 2.2) + 0.08 * sin(t * TAU * 7.0 - time * 3.1);
    float across = clamp(radial, -1.0, 1.0);
    float flow = t * 2.5 - time * 0.32 + wobble + across * 0.18 * sin(time * 1.4 + t * TAU * 2.0);

    // A little dispersion across the width: red and blue sample the reflection slightly apart.
    float disp = 0.045 * across;
    float3 col = float3(chrome(flow - disp), chrome(flow), chrome(flow + disp));
    col *= float3(0.97, 0.99, 1.04);

    // Rounded in section: darker towards both edges, as a cylinder of metal would be.
    col *= 1.0 - 0.38 * across * across;

    return half4(half3(col * mask), half(mask));
}
"""
