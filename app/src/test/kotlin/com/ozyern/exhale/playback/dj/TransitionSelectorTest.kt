package com.ozyern.exhale.playback.dj

import com.ozyern.exhale.playback.audio.DjMixTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransitionSelectorTest {

    @Test
    fun `creative transitions off means nothing but a transparent mix, ever`() {
        val style = TransitionSelector.select(
            tier = DjMixTier.PLAIN_CROSSFADE,
            outgoing = analysis(confidence = 0.9f, profile = loudThroughout()),
            incoming = analysis(confidence = 0.9f, profile = loudThroughout()),
            outgoingDurationMs = DURATION_MS,
            creativeEnabled = false,
        )

        assertEquals(TransitionStyle.TRANSPARENT, style)
    }

    @Test
    fun `any analysed pair gets an effect when creative is on`() {
        for (tier in DjMixTier.values()) {
            val style = select(tier = tier, index = 0)
            assertNotEquals(TransitionStyle.TRANSPARENT, style)
        }
    }

    @Test
    fun `rotation walks the sequence by transition index`() {
        val picked = (0L..5L).map { select(index = it) }

        assertEquals(
            listOf(
                TransitionStyle.REVERB_BLOOM,
                TransitionStyle.DELAY_BLEND,
                TransitionStyle.ECHO_FREEZE,
                TransitionStyle.REVERB_BLOOM,
                TransitionStyle.DELAY_BLEND,
                TransitionStyle.TAPE_STOP,
            ),
            picked,
        )
    }

    @Test
    fun `the brake falls back when tempos are hopeless`() {
        assertNotEquals(TransitionStyle.TAPE_STOP, select(tier = DjMixTier.PLAIN_CROSSFADE, index = 5))
    }

    @Test
    fun `the brake lands on a confident pair with a strong intro`() {
        assertEquals(TransitionStyle.TAPE_STOP, select(tier = DjMixTier.FULL_DJ, index = 5))
    }

    @Test
    fun `the brake is withheld when the incoming track opens quietly`() {
        val style = select(tier = DjMixTier.FULL_DJ, index = 5, incoming = analysis(0.9f, quietIntro()))

        assertNotEquals(TransitionStyle.TAPE_STOP, style)
    }

    @Test
    fun `the same move does not land twice in a row`() {
        val first = select(index = 0)
        val second = select(index = 0, recent = listOf(first))

        assertNotEquals(first, second)
    }

    private fun select(
        tier: DjMixTier = DjMixTier.FULL_DJ,
        index: Long,
        recent: List<TransitionStyle> = emptyList(),
        incoming: TrackAnalysis = analysis(0.9f, loudThroughout()),
    ) = TransitionSelector.select(
        tier = tier,
        outgoing = analysis(0.9f, loudThroughout()),
        incoming = incoming,
        outgoingDurationMs = DURATION_MS,
        creativeEnabled = true,
        recentStyles = recent,
        transitionIndex = index,
    )

    @Test
    fun `a null outgoing analysis means no effect`() {
        val style = TransitionSelector.select(
            tier = DjMixTier.PLAIN_CROSSFADE,
            outgoing = null,
            incoming = null,
            outgoingDurationMs = DURATION_MS,
            creativeEnabled = true,
        )

        assertEquals(TransitionStyle.TRANSPARENT, style)
    }

    private fun analysis(confidence: Float, profile: ByteArray) = TrackAnalysis(
        grid = BeatGrid(bpm = 128f, anchorBeatMs = 0f, confidence = confidence),
        energyProfile = profile,
    )

    private fun loudThroughout() = ByteArray(SECONDS) { 220.toByte() }

    private fun quietIntro() = ByteArray(SECONDS) { second -> (if (second < 25) 30 else 220).toByte() }

    private companion object {
        const val SECONDS = 180
        const val DURATION_MS = SECONDS * 1000L
    }
}
