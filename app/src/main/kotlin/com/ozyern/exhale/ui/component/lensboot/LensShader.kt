/*
 * Ported from AETHER (github.com/GHOSTxASR/AETHER, GPL-3.0): its cold-start film, a logo under a
 * swelling liquid lens. The optical model there is adapted from Travis Fischer's MIT-licensed
 * recreation of the liquid-lens effect. Adapted for Exhale: its own mark and wordmark, and its
 * reduced-motion setting.
 */

package com.ozyern.exhale.ui.component.lensboot

/**
 * The export's REFRACT and GRADE passes as one AGSL pass. Left out: the eight motion-blur
 * subframes and the bloom chain (a phone draws one frame at a time), and mip-level defocus (runtime
 * shaders sample without mips). Added: pixels the oil doesn't reach skip the per-wavelength loop.
 */
internal const val LENS_SHADER = """
uniform float2 res;
uniform float viewHalf;
uniform float aspect;

uniform shader markTex;
uniform float4 markRect;
uniform float2 markSize;
uniform float3 ink;
uniform float3 floorCol;

uniform float depth;
uniform float ior;
uniform float dispersion;
uniform float knee;
uniform float slopeCap;
uniform float samples;
uniform float reflectAmt;
uniform float softbox;
uniform float walls;
uniform float ringLight;
uniform float ringSpread;
uniform float4 ball;
uniform float2 domeCenter;
uniform float3 dome;
uniform float domeAspect;
uniform float3 shapeP;
uniform float edgeP;
uniform float4 wave;
uniform float wavePhase;
uniform float4 ripple;
uniform float reveal;
uniform float revealSoft;
uniform float seed;

uniform float typed;
uniform shader typeMap;
uniform float2 typeOrigin;
uniform float2 typeCell;
uniform float2 typeDims;
uniform shader atlas;
uniform float2 glyphPx;
uniform float resolve;
uniform float dots;

uniform float3 balance;
uniform float vignette;
uniform float exposure;
uniform float fade;
uniform float saturation;
uniform float blackLevel;

float noise2(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float footprint(float2 o) {
    float2 d = abs(o) / (dome.x * float2(domeAspect, 1.0));
    return pow(pow(d.x, dome.z) + pow(d.y, dome.z), 1.0 / dome.z);
}

float domeHeight(float2 p) {
    if (dome.x <= 0.0) { return 0.0; }
    float2 o = p - domeCenter;
    float2 d = abs(o) / (dome.x * float2(domeAspect, 1.0));
    float rho = pow(pow(d.x, dome.z) + pow(d.y, dome.z), 1.0 / dome.z);
    float beyond = length(o) * (1.0 - 1.0 / max(rho, 0.0001));
    float e = (beyond - wave.w) / max(wave.y, 0.0001);
    float train = wave.z > 0.0 ? cos(6.2831853 * (beyond - wave.w) / wave.z - wavePhase) : 1.0;
    float w = wave.x * exp(-e * e) * train;
    if (rho >= 1.0 || dome.y <= 0.0) { return w; }
    float r2 = dot(d, d);
    float body = exp(-shapeP.x * r2 - shapeP.z * r2 * r2);
    return w + dome.y * body * pow(1.0 - pow(rho, shapeP.y), edgeP);
}

float rippleHeight(float2 p) {
    if (ripple.x <= 0.0 || dome.x <= 0.0) { return 0.0; }
    float r = length(p - domeCenter);
    float f = 1.0 - smoothstep(0.0, ripple.w * dome.x, r);
    return ripple.x * f * sin(6.2831853 * r / ripple.y - ripple.z);
}

float heightAt(float2 p) { return domeHeight(p) + rippleHeight(p); }

float gauss2(float x, float mu, float s1, float s2) {
    float t = (x - mu) / (x < mu ? s1 : s2);
    return exp(-0.5 * t * t);
}

float3 response(float lambda) {
    float l = lambda * 1000.0;
    float X = 1.056 * gauss2(l, 599.8, 37.9, 31.0) + 0.362 * gauss2(l, 442.0, 16.0, 26.7) - 0.065 * gauss2(l, 501.1, 20.4, 26.2);
    float Y = 0.821 * gauss2(l, 568.8, 46.9, 40.5) + 0.286 * gauss2(l, 530.9, 16.3, 31.1);
    float Z = 1.217 * gauss2(l, 437.0, 11.8, 36.0) + 0.681 * gauss2(l, 459.0, 26.0, 13.8);
    return float3x3(3.2406, -0.9689, 0.0557, -1.5372, 1.8758, -0.2040, -0.4986, 0.0415, 1.0570) * float3(X, Y, Z);
}

float3 studio(float3 r) {
    if (r.z <= 0.0) { return float3(0.0); }
    float2 s = r.xy / max(r.z, 0.05);
    float key = 1.0 - smoothstep(0.0, 0.3, length(max(abs(s - float2(-1.5, 0.1)) - float2(0.25, 0.55), 0.0)));
    float fill = 1.0 - smoothstep(0.0, 0.4, length(max(abs(s - float2(1.8, -0.2)) - float2(0.2, 0.7), 0.0)));
    float wl = walls * smoothstep(1.2, 4.0, length(s));
    float ring = exp(-pow((length(s) - 0.8) / ringSpread, 2.0)) * smoothstep(0.15, 0.4, length(s));
    return float3(0.95, 0.97, 1.0) * (softbox * (key + 0.25 * fill) + ringLight * ring + wl);
}

float litAt(float2 q) {
    if (reveal < 0.5 || dome.x <= 0.0) { return 1.0; }
    float rho = footprint(q - domeCenter);
    return 1.0 - smoothstep(1.0 - revealSoft, 1.0, rho);
}

float markAt(float2 q) {
    float2 uv = (q - markRect.xy) / markRect.zw;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) { return 0.0; }
    return float(markTex.eval(float2(uv.x, 1.0 - uv.y) * markSize).a);
}

float typeAt(float2 q) {
    float2 g = (q - typeOrigin) / typeCell;
    float2 c = floor(g);
    if (c.x < 0.0 || c.y < 0.0 || c.x >= typeDims.x || c.y >= typeDims.y) { return 0.0; }
    half4 t = typeMap.eval(c + 0.5);
    float idx = floor(float(t.r) * 255.0 + 0.5);
    float bright = float(t.g) > 0.5 ? 1.0 : dots;
    float2 f = fract(g);
    float2 inner = clamp(float2(f.x, 1.0 - f.y) * glyphPx, float2(0.5), glyphPx - 0.5);
    return bright * float(atlas.eval(float2(idx * glyphPx.x + inner.x, inner.y)).a);
}

float floorInk(float2 q) {
    if (typed < 0.5) { return markAt(q); }
    float ty = typeAt(q);
    if (resolve <= 0.0) { return ty; }
    float order = noise2(floor((q - typeOrigin) / typeCell));
    return mix(ty, markAt(q), clamp((resolve * 1.3 - order) / 0.3, 0.0, 1.0));
}

float indexAt(float n589, float lambda, float localDispersion) {
    float B = 0.0044 * localDispersion;
    return n589 + B * (1.0 / (lambda * lambda) - 1.0 / (0.5893 * 0.5893));
}

float3 encodeSrgb(float3 c) {
    return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}

float3 grade(float3 c, float2 uv) {
    c *= balance;
    float2 v = (uv - 0.5) * float2(aspect, 1.0);
    c *= 1.0 - vignette * dot(v, v);
    c *= exposure * fade;
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = max(float3(lum) + (c - float3(lum)) * saturation, 0.0);
    float3 over = max(c - 0.8, 0.0);
    c = min(c, 0.8) + over / (1.0 + over / 0.2);
    float3 s = encodeSrgb(clamp(c, 0.0, 1.0));
    return max(s - blackLevel, 0.0) / (1.0 - blackLevel);
}

half4 main(float2 fragCoord) {
    float2 uv = float2(fragCoord.x / res.x, 1.0 - fragCoord.y / res.y);
    float2 p = (uv * 2.0 - 1.0) * float2(aspect, 1.0) * viewHalf;
    float3 V = float3(0.0, 0.0, -1.0);

    if (ball.w > 0.0) {
        float2 d = p - ball.xy;
        float r2 = ball.w * ball.w - dot(d, d);
        if (r2 > 0.0) {
            float zHit = ball.z + sqrt(r2);
            if (zHit > heightAt(p)) {
                float3 N = normalize(float3(d, zHit - ball.z));
                float3 R = reflect(V, N);
                float2 s = R.xy / max(R.z, 0.05);
                float rim = 0.12 * smoothstep(0.4, 3.0, length(s));
                float3 col = 0.5 * (studio(R) - float3(walls * smoothstep(1.2, 4.0, length(s)))) + rim + 0.015;
                col *= mix(1.0, 0.35, smoothstep(0.9, 0.995, R.z));
                return half4(half3(grade(col, uv)), 1.0);
            }
        }
    }

    float r0 = pow((ior - 1.0) / (ior + 1.0), 2.0);
    // Far from the oil the ray goes straight through: one sample, no spectrum.
    if (dome.x <= 0.0 || footprint(p - domeCenter) > 1.0 + (wave.w + 3.0 * wave.y + 0.05) / max(dome.x, 0.0001)) {
        float3 flatCol = mix(floorCol, ink, floorInk(p) * litAt(p)) * (1.0 - r0);
        return half4(half3(grade(flatCol, uv)), 1.0);
    }

    float h = 0.0015;
    float z = heightAt(p);
    float zx1 = heightAt(p + float2(h, 0.0));
    float zx0 = heightAt(p - float2(h, 0.0));
    float zy1 = heightAt(p + float2(0.0, h));
    float zy0 = heightAt(p - float2(0.0, h));
    float2 grad = float2(zx1 - zx0, zy1 - zy0) / (2.0 * h);
    if (slopeCap > 0.0) { grad *= min(1.0, slopeCap / max(length(grad), 0.000001)); }
    float3 N = normalize(float3(-grad, 1.0));

    float g2 = dot(grad, grad);
    float k2 = knee * knee;
    float localDispersion = dispersion * (k2 > 0.0 ? g2 / (g2 + k2) : 1.0);

    float jitter = noise2(fragCoord + seed * 17.0);
    float3 sum = float3(0.0);
    float3 norm = float3(0.0);
    for (int i = 0; i < 12; i++) {
        if (float(i) >= samples) { break; }
        float lambda = mix(0.38, 0.72, (float(i) + jitter) / samples);
        float3 w = response(lambda);
        float3 T = refract(V, N, 1.0 / indexAt(ior, lambda, localDispersion));
        float2 q = p + T.xy * (z + depth) / max(-T.z, 0.001);
        sum += w * mix(floorCol, ink, floorInk(q) * litAt(q));
        norm += w;
    }
    float3 col = sum / norm;
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    float lowest = min(col.r, min(col.g, col.b));
    if (lowest < 0.0) { col = lum > 0.0 ? mix(float3(lum), col, lum / (lum - lowest)) : float3(0.0); }

    float cosI = clamp(N.z, 0.0, 1.0);
    float F = r0 + (1.0 - r0) * pow(1.0 - cosI, 5.0);
    col = col * (1.0 - F) + reflectAmt * F * studio(reflect(V, N));
    return half4(half3(grade(col, uv)), 1.0);
}
"""
