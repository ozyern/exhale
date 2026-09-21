/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The cover is shrunk to this many pixels a side: the field is soft gradients, and this is all it samples. */
private const val Side = 48

/**
 * Apple Music's moving artwork: four slowly counter-rotating, domain-warped samplings of the cover,
 * averaged, pushed in saturation and vignetted — evaluated on the GPU in one pass.
 *
 * The Windows app works the same field out on the CPU because a desktop window is enormous; on a phone a
 * single AGSL pass over a 48-pixel texture costs next to nothing, and the shader's own animation clock
 * only invalidates drawing, never composition. Needs `RuntimeShader` (Android 13), which is this app's
 * minimum.
 */
private const val FluidAgsl = """
uniform shader cover;
uniform shader previous;
uniform float2 size;
uniform float time;
uniform float blend;
uniform float brightness;

half3 field(float2 p, float time, float aspect, bool useCover) {
    // Five samplings of the cover, weighted steeply.
    //
    // Weight is the whole difference between a field and a smudge. Averaging layers evenly pulls every
    // pixel toward the cover's mean colour, and a mean is grey — six flat layers made the phone's
    // background milky where the desktop's was deep. A dominant first layer keeps the cover's own
    // contrast; the rest ride under it as detail.
    half3 acc = half3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float dir = (mod(fi, 2.0) < 0.5) ? 1.0 : -1.0;
        float a = time * (0.045 + 0.02 * fi) * dir + fi * 1.9;
        float c = cos(a);
        float s = sin(a);
        float scale = 1.0 / (1.35 + 0.45 * fi);
        float2 q = float2(c * p.x - s * p.y, s * p.x + c * p.y) * scale;
        float u = q.x + 0.10 * sin(time * 0.17 + fi * 2.3 + q.y * 4.0) + 0.012 * sin(time * 0.31 + fi + q.y * 4.5) + 0.5 + sin(time * 0.05 + fi) * 0.18;
        float v = q.y + 0.10 * cos(time * 0.13 + fi * 1.1 + q.x * 4.0) + 0.012 * cos(time * 0.27 + fi + q.x * 4.5) + 0.5 + cos(time * 0.04 + fi * 1.7) * 0.18;
        float2 uv = clamp(float2(u, v), 0.02, 0.98) * $Side.0;
        half w = half(1.9 / (1.0 + 1.5 * fi));
        acc += (useCover ? cover.eval(uv).rgb : previous.eval(uv).rgb) * w;
    }
    return acc / 4.27;
}

half4 main(float2 fragCoord) {
    float aspect = size.x / size.y;
    float2 p = (fragCoord / size - 0.5) * float2(aspect, 1.0);
    half3 col = mix(field(p, time, aspect, false), field(p, time, aspect, true), half(blend));

    // Light moving through it, rather than paint laid over it. Three slow pools brighten where they
    // fall and leave the rest alone — added flat, they only washed the colour out.
    half lift = half(0.0);
    for (int j = 0; j < 3; j++) {
        float fj = float(j);
        float2 centre = (float2(0.5 + 0.34 * sin(time * 0.11 + fj * 2.1), 0.5 + 0.30 * cos(time * 0.09 + fj * 1.7)) - 0.5) * float2(aspect, 1.0);
        float d = length(p - centre);
        lift += half(exp(-d * d * 5.0) * (0.55 + 0.45 * sin(time * 0.4 + fj * 1.3)));
    }
    col *= half(1.0) + lift * half(0.35);

    // Saturation first, then a gentle S-curve: the cover's colour, with its own darks kept dark.
    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = max(half3(luma) + (col - half3(luma)) * 1.55, half3(0.0));
    col = clamp(col * col * (half3(3.0) - half3(2.0) * col), half3(0.0), half3(1.4));

    float vignette = 1.0 - smoothstep(0.2, 1.3, length(p));
    col *= half(brightness * (0.60 + 0.40 * vignette));

    // A whisper of noise: without it the smooth gradients band on an 8-bit panel.
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += half((n - 0.5) * 0.012);
    return half4(col, 1.0);
}
"""

/** A cover and the texture the shader reads it through, made once rather than every frame. */
private class Cover(bitmap: Bitmap) {
    val shader: BitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
}

/**
 * The cover at [url] as a slowly moving field of its own colours. A new cover cross-fades in over 1.4s
 * rather than cutting. [paused] stops the clock (a hidden or collapsed player draws nothing anyone sees).
 */
@Composable
fun FluidArtworkBackground(
    url: String?,
    modifier: Modifier = Modifier,
    brightness: Float = 0.9f,
    paused: Boolean = false,
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<Cover?>(null) }
    var previous by remember { mutableStateOf<Cover?>(null) }
    val blend = remember { Animatable(1f) }
    val clock = remember { mutableFloatStateOf(0f) }
    val isPaused by rememberUpdatedState(paused)

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(Side, Side).allowHardware(false).build()
        val bitmap = runCatching { withContext(Dispatchers.IO) { context.imageLoader.execute(request).image?.toBitmap() } }
            .getOrNull() ?: return@LaunchedEffect
        // Exactly Side × Side, in software memory: the shader indexes it in pixels.
        val small = if (bitmap.width == Side && bitmap.height == Side) bitmap else Bitmap.createScaledBitmap(bitmap, Side, Side, true)
        previous = current ?: Cover(small)
        current = Cover(small)
        blend.snapTo(0f)
        blend.animateTo(1f, tween(1_400, easing = LinearOutSlowInEasing))
    }

    // One frame clock; drawing reads it, composition never does.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L && !isPaused) clock.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    // A shader that fails to compile on some driver must cost the background, not the player.
    val shader = remember { runCatching { RuntimeShader(FluidAgsl) }.getOrNull() }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                val now = current ?: return@drawBehind
                if (shader == null || brush == null) return@drawBehind
                shader.setInputShader("cover", now.shader)
                shader.setInputShader("previous", (previous ?: now).shader)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", clock.floatValue)
                shader.setFloatUniform("blend", blend.value)
                shader.setFloatUniform("brightness", brightness)
                drawRect(brush)
            },
    )
}
