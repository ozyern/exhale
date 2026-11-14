package com.ozyern.exhale.ui.theme.colorengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Covers the parts of the ported colour engine that are pure maths — everything from
 * quantised clusters onward. The bitmap sampling stages need `android.graphics`, so they
 * are exercised on-device rather than here.
 */
class ColorEngineTest {

    // Colour models.

    @Test
    fun `sRGB round trips through LAB`() {
        // A byte triple -> LAB -> sRGB should land back on the same byte, within the
        // LUT's interpolation error. If this drifts, every colour the engine emits does.
        val samples = listOf(
            Triple(0, 0, 0),
            Triple(255, 255, 255),
            Triple(255, 0, 0),
            Triple(0, 128, 64),
            Triple(18, 52, 86),
            Triple(200, 190, 120),
        )
        for ((red, green, blue) in samples) {
            val color = rgb8ToLab(red, green, blue).color.toColor()
            assertEquals("red of ($red,$green,$blue)", red / 255f, color.red, 2f / 255f)
            assertEquals("green of ($red,$green,$blue)", green / 255f, color.green, 2f / 255f)
            assertEquals("blue of ($red,$green,$blue)", blue / 255f, color.blue, 2f / 255f)
        }
    }

    @Test
    fun `deltaE is zero for identical colours and grows with difference`() {
        val red = rgb8ToLab(220, 30, 30).color
        val nearRed = rgb8ToLab(226, 36, 34).color
        val blue = rgb8ToLab(30, 30, 220).color
        for (algorithm in DeltaEAlgorithm.entries) {
            assertEquals(algorithm.name, 0.0, red.deltaE(red, algorithm), 1e-6)
            assertTrue(
                "$algorithm: near-red should be closer than blue",
                red.deltaE(nearRed, algorithm) < red.deltaE(blue, algorithm),
            )
        }
    }

    @Test
    fun `LCh round trips and hue distance wraps`() {
        val source = rgb8ToLab(40, 160, 210).color
        val roundTripped = source.toLCh().toLab()
        assertEquals(source.l, roundTripped.l, 1e-9)
        assertEquals(source.a, roundTripped.a, 1e-9)
        assertEquals(source.b, roundTripped.b, 1e-9)

        assertEquals(20.0, hueDistance(350.0, 10.0), 1e-9)
        assertEquals(180.0, hueDistance(0.0, 180.0), 1e-9)
        assertEquals(0.0, hueDistance(90.0, 450.0), 1e-9)
    }

    @Test
    fun `gamut fitting produces representable colours`() {
        // An absurd chroma at a mid lightness is far outside sRGB; the fit must pull it
        // back in without moving the hue.
        val impossible = LChColor(l = 55.0, c = 200.0, h = 140.0)
        val fitted = fitToSRGBGamut(impossible)
        assertTrue("fitted chroma should shrink", fitted.c < impossible.c)
        assertEquals("hue must not move", impossible.h, fitted.h, 1e-9)
        assertTrue("fitted colour must be in gamut", fitted.toLab().isInSRGBGamut)
    }

    // Determinism.

    @Test
    fun `SplitMix64 is reproducible for a seed and differs between seeds`() {
        val first = SplitMix64(0x1234_5678L)
        val second = SplitMix64(0x1234_5678L)
        val other = SplitMix64(0x1234_5679L)
        val firstRun = List(8) { first.nextUnitDouble() }
        assertEquals(firstRun, List(8) { second.nextUnitDouble() })
        assertTrue(
            "a different seed must not replay the same stream",
            firstRun != List(8) { other.nextUnitDouble() },
        )
        assertTrue("unit doubles stay in range", firstRun.all { it in 0.0..1.0 })
    }

    // Roles and schemes.

    private fun cluster(
        l: Float,
        a: Float,
        b: Float,
        weighted: Float,
        population: Float,
        text: Float = 0f,
        saliency: Float = 0.5f,
        id: Int,
    ) = Cluster(
        point = LabPoint(l, a, b),
        weightedShare = weighted,
        populationShare = population,
        textAffinity = text,
        meanSaliency = saliency,
        count = 64,
        stableID = id,
    )

    /** A dark navy field, a vivid orange accent, and white type — a typical album cover. */
    private fun sampleClusters() = listOf(
        cluster(l = 18f, a = 6f, b = -28f, weighted = 0.62f, population = 0.58f, id = 1),
        cluster(l = 64f, a = 44f, b = 58f, weighted = 0.16f, population = 0.14f, id = 2),
        cluster(l = 94f, a = 0f, b = 1f, weighted = 0.12f, population = 0.10f, text = 0.62f, id = 3),
        cluster(l = 40f, a = 3f, b = -12f, weighted = 0.10f, population = 0.18f, id = 4),
    )

    private fun contrastRatio(first: LabColor, second: LabColor): Double {
        val brighter = max(first.relativeLuminance, second.relativeLuminance)
        val darker = min(first.relativeLuminance, second.relativeLuminance)
        return (brighter + 0.05) / (darker + 0.05)
    }

    private fun toneStatistics(tone: ImageTone) = ToneStatistics(
        tone = tone,
        confidence = 0.8f,
        weightedMean = if (tone == ImageTone.DARK) 24f else 78f,
        weightedMedian = if (tone == ImageTone.DARK) 21f else 80f,
        percentile65 = if (tone == ImageTone.DARK) 30f else 86f,
        darkCoverage = if (tone == ImageTone.DARK) 0.72f else 0.12f,
        lightCoverage = if (tone == ImageTone.DARK) 0.12f else 0.72f,
        chromaticCoverage = 0.28f,
        strongChromaticCoverage = 0.14f,
        chromaticConfidence = 0.4f,
    )

    @Test
    fun `background role is the dominant field, accent is the vivid colour`() {
        val clusters = sampleClusters()
        val roles = selectRoles(clusters, identityClusters = clusters.filter { hasIdentityEvidence(it) })
        assertEquals("dominant navy should own the background", 1, roles.background.stableID)
        assertNotNull("a vivid orange should be found as the accent", roles.accent)
        assertEquals(2, roles.accent!!.stableID)
    }

    @Test
    fun `both schemes meet their text contrast targets and stay in gamut`() {
        val clusters = sampleClusters()
        val identity = clusters.filter { hasIdentityEvidence(it) }
        val roles = selectRoles(clusters, identity)
        val style = StyleProfile(ColorStyle.BALANCED)
        val statistics = toneStatistics(ImageTone.DARK)
        val backgrounds = makeBackgroundPair(clusters, roles, statistics, style)
        val meshSources = makeMeshSourceColors(clusters, roles, statistics, style)

        // Three stops is the floor a mesh gradient can be rendered from at all.
        assertTrue("mesh needs at least 3 stops", meshSources.size >= 3)

        for (isDark in listOf(true, false)) {
            val background = if (isDark) backgrounds.dark else backgrounds.light
            val scheme = makeScheme(
                roles = roles,
                background = background,
                meshColors = makeSchemeMeshColors(meshSources, background, isDark),
                isDark = isDark,
                style = style,
            )
            val backgroundLab = background.toLab()

            // The four text tiers are built against 4.5 / 3.5 / 3.0 / 2.5. Allow a small
            // slack: enforceContrast binary-searches lightness and can stop a hair short.
            val targets = listOf(4.5, 3.5, 3.0, 2.5)
            val texts = listOf(
                scheme.primaryText,
                scheme.secondaryText,
                scheme.tertiaryText,
                scheme.quaternaryText,
            )
            texts.forEachIndexed { index, color ->
                val lab = rgb8ToLab(
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt(),
                ).color
                val ratio = contrastRatio(backgroundLab, lab)
                assertTrue(
                    "dark=$isDark tier $index ratio $ratio below ${targets[index]}",
                    ratio >= targets[index] - 0.25,
                )
            }

            // Every emitted colour must be a real sRGB colour, not a clipped one.
            val emitted = texts + listOf(
                scheme.background,
                scheme.accent,
                scheme.surface,
                scheme.border,
                scheme.tint,
            ) + scheme.meshColors
            for (color in emitted) {
                assertTrue(
                    "channel out of range: $color",
                    color.red in 0f..1f && color.green in 0f..1f && color.blue in 0f..1f,
                )
            }

            // Text must actually differentiate: primary is the strongest of the four.
            val primaryRatio = contrastRatio(
                backgroundLab,
                rgb8ToLab(
                    (scheme.primaryText.red * 255).toInt(),
                    (scheme.primaryText.green * 255).toInt(),
                    (scheme.primaryText.blue * 255).toInt(),
                ).color,
            )
            val quaternaryRatio = contrastRatio(
                backgroundLab,
                rgb8ToLab(
                    (scheme.quaternaryText.red * 255).toInt(),
                    (scheme.quaternaryText.green * 255).toInt(),
                    (scheme.quaternaryText.blue * 255).toInt(),
                ).color,
            )
            assertTrue(
                "dark=$isDark primary ($primaryRatio) should out-contrast quaternary ($quaternaryRatio)",
                primaryRatio > quaternaryRatio,
            )
        }
    }

    @Test
    fun `dark artwork keeps a dark native background and a light counterpart`() {
        val clusters = sampleClusters()
        val roles = selectRoles(clusters, clusters.filter { hasIdentityEvidence(it) })
        val backgrounds = makeBackgroundPair(
            clusters,
            roles,
            toneStatistics(ImageTone.DARK),
            StyleProfile(ColorStyle.BALANCED),
        )
        assertTrue("dark background lightness ${backgrounds.dark.l}", backgrounds.dark.l <= 36.0)
        assertTrue("light background lightness ${backgrounds.light.l}", backgrounds.light.l >= 60.0)
    }

    @Test
    fun `role selection is stable across repeated runs`() {
        // Determinism is the engine's contract; a HashMap iteration leaking into an
        // ordering decision is exactly what this catches.
        val clusters = sampleClusters()
        val identity = clusters.filter { hasIdentityEvidence(it) }
        val style = StyleProfile(ColorStyle.BALANCED)
        val statistics = toneStatistics(ImageTone.DARK)
        val first = makeBackgroundPair(clusters, selectRoles(clusters, identity), statistics, style)
        repeat(5) {
            val again = makeBackgroundPair(clusters, selectRoles(clusters, identity), statistics, style)
            assertTrue(abs(first.dark.l - again.dark.l) < 1e-9)
            assertTrue(abs(first.dark.c - again.dark.c) < 1e-9)
            assertTrue(abs(first.dark.h - again.dark.h) < 1e-9)
        }
    }

    @Test
    fun `pastel style is calmer than vibrant`() {
        val clusters = sampleClusters()
        val identity = clusters.filter { hasIdentityEvidence(it) }
        val roles = selectRoles(clusters, identity)
        val statistics = toneStatistics(ImageTone.DARK)

        fun accentChroma(style: ColorStyle): Double {
            val profile = StyleProfile(style)
            val backgrounds = makeBackgroundPair(clusters, roles, statistics, profile)
            val scheme = makeScheme(
                roles = roles,
                background = backgrounds.dark,
                meshColors = emptyList(),
                isDark = true,
                style = profile,
            )
            val color = scheme.accent
            return rgb8ToLab(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            ).color.toLCh().c
        }

        assertTrue(accentChroma(ColorStyle.PASTEL) < accentChroma(ColorStyle.VIBRANT))
    }
}
