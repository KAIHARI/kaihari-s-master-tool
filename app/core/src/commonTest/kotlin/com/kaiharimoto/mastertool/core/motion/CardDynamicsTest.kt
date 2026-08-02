package com.kaiharimoto.mastertool.core.motion

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardDynamicsTest {

    private val cardWidth = 120f

    @Test
    fun aCardThatIsNotMovingIsSquare() {
        assertEquals(Bank.Level, CardDynamics.bank(Vec3.Zero, cardWidth))
    }

    @Test
    fun aFingerRestingOnACardDoesNotMakeItShiver() {
        // A held finger produces a pixel or two of tremor every single frame.
        // Without the dead zone the card sits there vibrating, which is much
        // worse than it sounds.
        val tremor = CardDynamics.bank(Vec3(14f, -9f, 0f), cardWidth)

        assertEquals(Bank.Level, tremor)
    }

    @Test
    fun theLeadingEdgeGoesAway() {
        // The whole sign convention in one test. Swept right, the right edge
        // knifes back; pushed away from the player, the far edge dips.
        val right = CardDynamics.bank(Vec3(600f, 0f, 0f), cardWidth)
        val away = CardDynamics.bank(Vec3(0f, -600f, 0f), cardWidth)

        assertTrue(right.rotY > 1f, "a positive turn pushes the right edge back: ${right.rotY}")
        assertTrue(away.rotX > 1f, "a positive tilt pulls the near edge forward: ${away.rotX}")
    }

    @Test
    fun theOppositeSweepBanksTheOppositeWay() {
        val right = CardDynamics.bank(Vec3(600f, 0f, 0f), cardWidth)
        val left = CardDynamics.bank(Vec3(-600f, 0f, 0f), cardWidth)

        assertTrue(abs(right.rotY + left.rotY) < 1e-4f, "${right.rotY} against ${left.rotY}")
        assertTrue(abs(right.rotZ + left.rotZ) < 1e-4f)
    }

    @Test
    fun theCardTrailsTheHandRatherThanLeadingIt() {
        val swept = CardDynamics.bank(Vec3(900f, 0f, 0f), cardWidth)

        assertTrue(swept.rotZ < 0f, "the twist opposes the bank: ${swept.rotZ}")
        assertTrue(
            abs(swept.rotZ) < abs(swept.rotY) / 2f,
            "and stays a secondary motion: ${swept.rotZ} against ${swept.rotY}",
        )
    }

    @Test
    fun aFasterSweepBanksFurtherUntilItStops() {
        val speeds = listOf(200f, 400f, 660f, 1_000f, 4_000f)
        val banks = speeds.map { CardDynamics.bank(Vec3(it, 0f, 0f), cardWidth).rotY }

        assertEquals(banks.sorted(), banks, "faster is never flatter")
        assertTrue(banks.last() <= 13.0001f, "and it is capped: ${banks.last()}")
        assertTrue(banks.first() < banks.last(), "but it does vary: $banks")
    }

    @Test
    fun aFlickCannotTurnTheCardEdgeOnAndLoseIt() {
        val absurd = CardDynamics.bank(Vec3(90_000f, -90_000f, 0f), cardWidth)

        listOf(absurd.rotX, absurd.rotY, absurd.rotZ).forEach {
            assertTrue(abs(it) <= 13.0001f, "went past the ceiling: $it")
        }
    }

    @Test
    fun theSameFlickBanksTheSameOnAnySizeOfTable() {
        // Scaled by the card rather than by pixels: the fitter solves for very
        // different card sizes on a phone and on a desk monitor, and a gesture
        // that felt right on one should not go limp on the other.
        val small = CardDynamics.bank(Vec3(60f * 5f, 0f, 0f), 60f)
        val large = CardDynamics.bank(Vec3(240f * 5f, 0f, 0f), 240f)

        assertTrue(abs(small.rotY - large.rotY) < 1e-3f, "${small.rotY} against ${large.rotY}")
    }

    @Test
    fun aTableWithNoCardsOnItDoesNotDivideByZero() {
        assertEquals(Bank.Level, CardDynamics.bank(Vec3(500f, 500f, 0f), cardWidth = 0f))
    }

    @Test
    fun banksAddUp() {
        assertEquals(Bank(3f, 5f, 7f), Bank(1f, 2f, 3f) + Bank(2f, 3f, 4f))
    }
}
