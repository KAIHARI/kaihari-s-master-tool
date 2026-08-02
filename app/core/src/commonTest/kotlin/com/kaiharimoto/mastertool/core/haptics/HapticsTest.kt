package com.kaiharimoto.mastertool.core.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticsTest {

    @Test
    fun everyHapticSaysSomething() {
        Haptic.entries.forEach { haptic ->
            assertTrue(haptic.pattern.pulses.isNotEmpty(), "$haptic has no pulses")
            assertTrue(haptic.strength in 0f..1f, "$haptic strength ${haptic.strength}")
            assertTrue(haptic.crispness in 0f..1f, "$haptic crispness ${haptic.crispness}")
            haptic.pattern.pulses.forEach {
                assertTrue(it.amplitude in 0f..1f, "$haptic amplitude ${it.amplitude}")
                assertTrue(it.durationMs > 0, "$haptic has a pulse of no length")
            }
        }
    }

    @Test
    fun nothingOutlastsTheGestureThatCausedIt() {
        // A buzz you can still feel after you have stopped thinking about the
        // gesture reads as a malfunction, not as feedback. The riffle is the
        // one exception, and it earns it by being a genuinely long gesture.
        Haptic.entries.filter { it != Haptic.SHUFFLE }.forEach {
            assertTrue(it.pattern.totalMillis <= 60, "$it runs for ${it.pattern.totalMillis}ms")
        }
        assertTrue(Haptic.SHUFFLE.pattern.totalMillis < 200)
    }

    @Test
    fun landingIsFirmerThanLifting() {
        // Picking a card up is a release of contact; putting it down is the
        // contact. They are not the same event and must not feel the same.
        assertTrue(Haptic.LAND.strength > Haptic.LIFT.strength)
        assertTrue(Haptic.LAND.crispness > Haptic.LIFT.crispness)
    }

    @Test
    fun theDetentIsTheSharpestThingOnTheTable() {
        // It is the only haptic reporting a boundary rather than a contact,
        // and a boundary you can feel is what makes the twist doable blind.
        assertEquals(Haptic.DETENT, Haptic.entries.maxBy { it.crispness })
        assertEquals(Haptic.DETENT, Haptic.entries.minBy { it.pattern.totalMillis })
    }

    @Test
    fun aRiffleFadesRatherThanStopping() {
        val pulses = Haptic.SHUFFLE.pattern.pulses

        assertTrue(pulses.size >= 4, "a shuffle is a run of taps, not a tap")
        assertEquals(
            pulses.map { it.amplitude }.sortedDescending(),
            pulses.map { it.amplitude },
            "it should die away",
        )
        assertEquals(
            pulses.map { it.gapMs }.sortedDescending(),
            pulses.map { it.gapMs },
            "and speed up as it does",
        )
    }

    // ---- the shape handed to a platform -----------------------------------------

    @Test
    fun aSinglePulseIsOneTimingAndOneLevel() {
        val pattern = HapticPattern.single(0.5f, 20)

        assertEquals(listOf(20L), pattern.timings())
        assertEquals(listOf(127), pattern.motorLevels())
        assertEquals(20, pattern.totalMillis)
    }

    @Test
    fun aGapBecomesAnEntryOfItsOwnAtNoStrength() {
        // Which is what Android's waveform API wants: durations and amplitudes
        // index-aligned, with a pause spelled as a zero.
        val pattern = HapticPattern(
            listOf(HapticPulse(1f, 10, gapMs = 30), HapticPulse(0.5f, 20)),
        )

        assertEquals(listOf(10L, 30L, 20L), pattern.timings())
        assertEquals(listOf(255, 0, 127), pattern.motorLevels())
        assertEquals(60, pattern.totalMillis)
    }

    @Test
    fun everyPatternLinesUpWithItsOwnLevels() {
        Haptic.entries.forEach {
            assertEquals(
                it.pattern.timings().size,
                it.pattern.motorLevels().size,
                "$it would hand the platform mismatched arrays",
            )
        }
    }

    @Test
    fun motorLevelsStayInsideTheRangeAPlatformAccepts() {
        Haptic.entries.forEach { haptic ->
            haptic.pattern.motorLevels().forEach {
                assertTrue(it in 0..255, "$haptic asked for $it")
            }
        }
    }

    // ---- the score ------------------------------------------------------------------

    @Test
    fun landingOnACardIsNotTheSameAsLandingOnFelt() {
        assertEquals(Haptic.STACK, HapticScore.landing(ontoCard = true))
        assertEquals(Haptic.LAND, HapticScore.landing(ontoCard = false))
    }

    @Test
    fun landingOnACardIsTwoContactsBecauseThereAreTwoSurfaces() {
        assertEquals(2, Haptic.STACK.pattern.pulses.size)
        assertTrue(
            Haptic.STACK.pattern.pulses[1].amplitude < Haptic.STACK.pattern.pulses[0].amplitude,
        )
    }
}
