/*
 * Ported from AETHER (github.com/GHOSTxASR/AETHER, GPL-3.0): its cold-start film, a logo under a
 * swelling liquid lens. The optical model there is adapted from Travis Fischer's MIT-licensed
 * recreation of the liquid-lens effect. Adapted for Exhale: its own mark and wordmark, and its
 * reduced-motion setting.
 */

package com.ozyern.exhale.ui.component.lensboot

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.PI

/*
 * The liquid-lens boot film, ported from the HorizonX "Liquid Lens Logo" export the listener
 * supplied (its optical model, shot curves and grade constants are adapted there from Travis
 * Fischer's MIT-licensed recreation; notice in third_party/NOTICES.md). Two shots: a dome of oil
 * swells over the symbol, magnifies it, smears its edges into spectral colour and drains away; then
 * a chrome ball widens into a long low lens over the wordmark, ripples and falls flat while the
 * type decodes into the clean wordmark. A frame is a pure function of film time.
 */
internal object LensFilm {
    const val DURATION = 4f

    /** World units: the frame is 2 tall, y up; a narrow stage widens until this half-width fits. */
    const val CONTENT_HALF_WIDTH = 0.82f

    fun viewHalf(aspect: Float) = max(1f, CONTENT_HALF_WIDTH / max(aspect, 1e-3f))

    /** Monotone cubic Hermite (Fritsch–Carlson) through [t, v] keys, held flat outside them. */
    class Curve(vararg keys: Float) {
        private val t = FloatArray(keys.size / 2) { keys[it * 2] }
        private val v = FloatArray(keys.size / 2) { keys[it * 2 + 1] }
        private val m = FloatArray(t.size)

        init {
            val n = t.size
            val secant = FloatArray(max(0, n - 1)) { (v[it + 1] - v[it]) / (t[it + 1] - t[it]) }
            for (i in 1 until n - 1) m[i] = (secant[i - 1] + secant[i]) / 2f
            for (i in 0 until n - 1) {
                val s = secant[i]
                if (s == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
                val a = max(0f, m[i] / s)
                val b = max(0f, m[i + 1] / s)
                if (a == 0f) m[i] = 0f
                if (b == 0f) m[i + 1] = 0f
                val len = hypot(a, b)
                if (len > 3f) { m[i] = 3f * a * s / len; m[i + 1] = 3f * b * s / len }
            }
        }

        fun at(x: Float): Float {
            val n = t.size
            if (n == 0) return 0f
            if (n == 1 || x <= t[0]) return v[0]
            if (x >= t[n - 1]) return v[n - 1]
            var i = 0
            while (x > t[i + 1]) i++
            val dt = t[i + 1] - t[i]
            val u = (x - t[i]) / dt
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * v[i] + (u3 - 2 * u2 + u) * dt * m[i] + (-2 * u3 + 3 * u2) * v[i + 1] + (u3 - u2) * dt * m[i + 1]
        }
    }

    class Profile(val crown: Float, val shoulder: Float, val flat: Float, val edge: Float)

    class Dome(
        val centerX: Float, val centerY: Float, val exponent: Float, val aspect: Float,
        val from: Profile, val to: Profile,
        val radius: Curve, val height: Curve, val morph: Curve, val settle: Curve,
    )

    class Look(
        val dome: Float = 1f, val ringWidth: Float = 0.05f, val ringWavelength: Float = 0f, val ringSpeed: Float = 0f,
        val ringOffset: Float = 0f, val rippleWavelength: Float = 0.055f, val rippleSpeed: Float = 0.4f, val rippleReach: Float = 0.9f,
        val depth: Float = 1f, val ior: Float = 1.47f, val dispersion: Float = 16f, val dispersionKnee: Float = 0f, val maxSlope: Float = 0f,
        val defocus: Float = 0.05f, val reflect: Float = 1f, val reveal: Float = 1f, val revealSoft: Float = 0.06f,
        val softbox: Float = 20f, val horizon: Float = 6f, val ringLight: Float = 0f, val ringLightWidth: Float = 0.45f,
        val exposure: Float = 1f, val vignette: Float = 0.25f, val saturation: Float = 1.5f, val black: Float = 0.012f,
        val balance: FloatArray = floatArrayOf(0.97f, 1f, 1.03f),
    )

    class Shot(
        val start: Float, val end: Float,
        val slotCenterX: Float, val slotCenterY: Float, val slotW: Float, val slotH: Float,
        val dome: Dome, val look: Look,
        val fade: Curve, val colour: Curve, val ring: Curve, val ringReach: Curve? = null,
        val rippleHeight: Float = 0f, val rippleDecay: Float = 1f,
        val ballRadius: Float = 0f, val ballLift: Curve? = null,
        val gloss: Curve? = null, val dots: Curve? = null, val resolve: Curve? = null, val recede: Curve? = null,
    )

    val SYMBOL = Shot(
        start = 0f, end = 1.465f,
        slotCenterX = -0.0143f, slotCenterY = -0.0038f, slotW = 0.56f, slotH = 0.624f,
        dome = Dome(
            -0.01f, 0f, 3.3f, 1f,
            from = Profile(0.4597f, 0.7245f, 45f, 0.2f), to = Profile(0.1004f, 0.4008f, 45f, 0.2f),
            radius = Curve(0.05f, 0.1f, 0.12f, 0.29f, 0.21f, 0.415f, 0.42f, 0.478f, 0.75f, 0.526f, 1.17f, 0.54f),
            height = Curve(0.05f, 0f, 0.12f, 0.1f, 0.21f, 0.1507f, 0.45f, 0.1796f, 0.75f, 0.2432f, 1f, 0.245f, 1.15f, 0.22f, 1.3f, 0.022f),
            morph = Curve(0.28f, 0f, 0.45f, 1f),
            settle = Curve(0.21f, 0f, 0.6f, 0.7379f, 1f, 1f),
        ),
        look = Look(dispersion = 100f, dispersionKnee = 0.9f, maxSlope = 1.4f, softbox = 4f, ringWidth = 0.02f, dome = 0.9f),
        fade = Curve(0.04f, 0f, 0.25f, 1f),
        colour = Curve(0.1f, 0.3f, 0.21f, 1f),
        ring = Curve(0.1f, 0f, 0.2f, 0.012f, 0.3f, 0.008f, 0.42f, 0f, 0.5f, 0f, 0.62f, 0.012f, 0.85f, 0.008f, 1f, 0f),
        ringReach = Curve(0.1f, 0.05f, 0.42f, 0.05f, 0.55f, 0.03f, 0.63f, 0.07f, 0.8f, 0.16f, 1f, 0.26f),
        recede = Curve(1.2f, 1f, 1.24f, 0.973f, 1.273f, 0.926f, 1.307f, 0.87f, 1.34f, 0.814f, 1.373f, 0.743f, 1.407f, 0.612f, 1.44f, 0.426f, 1.465f, 0.3f),
    )

    val WORDMARK = Shot(
        start = 1.465f, end = DURATION,
        slotCenterX = 0f, slotCenterY = 0.01f, slotW = 1.09f, slotH = 0.34f,
        dome = Dome(
            0f, 0f, 2f, 1f,
            from = Profile(0f, 0f, 2f, 0.8f), to = Profile(0f, 0f, 11.67f, 2.84f),
            radius = Curve(0f, 0.15f, 0.135f, 0.24f, 0.295f, 0.35f, 0.375f, 0.4f, 0.6f, 0.5f, 0.94f, 0.66f, 1.2f, 0.65f, 1.5f, 0.62f, 1.8f, 0.6f),
            height = Curve(0f, 0.0136f, 0.135f, 0.041f, 0.295f, 0.0682f, 0.375f, 0.0864f, 0.6f, 0.0385f, 0.94f, 0.0409f, 1.2f, 0.012f, 1.6f, 0f),
            morph = Curve(0f, 0f),
            settle = Curve(0.375f, 0f, 0.58f, 1f),
        ),
        look = Look(
            ringWavelength = 0.06f, ringWidth = 0.06f, ringSpeed = 0.08f, softbox = 2f, horizon = 0.25f,
            ringLight = 1.3f, rippleWavelength = 0.08f, dispersion = 60f, dispersionKnee = 0.3f,
        ),
        fade = Curve(0f, 0.3f, 0.035f, 1f),
        colour = Curve(0.375f, 0.2f, 0.58f, 1f),
        ring = Curve(0.7f, 0f, 0.9f, 0.003f, 1f, 0.003f, 1.2f, 0.0035f, 1.45f, 0.0025f, 1.62f, 0f),
        rippleHeight = 0.005f, rippleDecay = 0.5f,
        ballRadius = 0.065f, ballLift = Curve(0.25f, 0f, 0.4f, -0.2f),
        gloss = Curve(0.25f, 1f, 0.42f, 0.1f, 0.6f, 0f),
        dots = Curve(0.9f, 1f, 1.2f, 0f),
        resolve = Curve(1.25f, 0f, 1.6f, 1f),
    )

    val SHOTS = listOf(SYMBOL, WORDMARK)

    fun shotAt(t: Float): Shot = SHOTS.firstOrNull { t >= it.start && t < it.end } ?: SHOTS.last()

    /** The dome and everything moving at time [t] into [shot]. */
    class Frame(
        val radius: Float, val height: Float, val exponent: Float, val aspect: Float,
        val crown: Float, val shoulder: Float, val flat: Float, val edge: Float,
        val ring: Float, val ringReach: Float, val ripple: Float,
        val wavePhase: Float, val ripplePhase: Float,
        val ballZ: Float, val dispersion: Float, val reflect: Float, val fade: Float,
        val dots: Float, val resolve: Float, val recede: Float,
    )

    fun frame(shot: Shot, t: Float): Frame {
        val d = shot.dome
        val look = shot.look
        val settle = d.settle.at(t)
        val morph = d.morph.at(t)
        fun mix(a: Float, b: Float) = a + (b - a) * settle
        val height = max(0f, d.height.at(t)) * look.dome
        return Frame(
            radius = d.radius.at(t),
            height = height,
            exponent = 2f + (d.exponent - 2f) * morph,
            aspect = 1f + (d.aspect - 1f) * morph,
            crown = mix(d.from.crown, d.to.crown),
            shoulder = mix(d.from.shoulder, d.to.shoulder),
            flat = mix(d.from.flat, d.to.flat),
            edge = mix(d.from.edge, d.to.edge),
            ring = shot.ring.at(t),
            ringReach = shot.ringReach?.at(t) ?: look.ringOffset,
            ripple = if (shot.rippleHeight > 0f) shot.rippleHeight * exp(-t / shot.rippleDecay) * (1f - settle) else 0f,
            wavePhase = if (look.ringWavelength > 0f) (2f * PI.toFloat() * look.ringSpeed * t) / look.ringWavelength else 0f,
            ripplePhase = (2f * PI.toFloat() * look.rippleSpeed * t) / look.rippleWavelength,
            ballZ = if (shot.ballRadius > 0f) height + (shot.ballLift?.at(t) ?: 0f) else 0f,
            dispersion = look.dispersion * shot.colour.at(t),
            reflect = look.reflect * (shot.gloss?.at(t) ?: 1f),
            fade = shot.fade.at(t),
            dots = shot.dots?.at(t) ?: 1f,
            resolve = shot.resolve?.at(t) ?: 0f,
            recede = shot.recede?.at(t) ?: 1f,
        )
    }
}
