package com.kaiharimoto.mastertool.core.motion

import com.kaiharimoto.mastertool.core.motion.Settle.Care
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Claims about how a card comes to rest.
 *
 * Every one of these is something that would be visible on the table if it
 * stopped being true, which is the bar `GoldenStageTest`'s KDoc sets for the
 * tests around it. A recording of three floats would catch a regression here
 * without anybody being able to say what had regressed.
 */
class SettleTest {

    private val ids = (0 until 4000).toList()

    /** Pearson's, for saying that two of these channels are unrelated. */
    private fun correlation(a: List<Float>, b: List<Float>): Float {
        val ma = a.average().toFloat()
        val mb = b.average().toFloat()
        val cov = a.indices.sumOf { ((a[it] - ma) * (b[it] - mb)).toDouble() }
        val va = a.sumOf { ((it - ma) * (it - ma)).toDouble() }
        val vb = b.sumOf { ((it - mb) * (it - mb)).toDouble() }
        return (cov / kotlin.math.sqrt(va * vb)).toFloat()
    }

    @Test
    fun aCardLandsTheSameWayEveryTimeItIsAsked() {
        // The pose is recomputed whenever the board changes and read again by
        // the springs on every frame. A landing that differed between two of
        // those reads would be a card that shivers where it lies.
        ids.take(200).forEach { id ->
            assertEquals(Settle.of(id, Care.PLACED), Settle.of(id, Care.PLACED))
        }
    }

    @Test
    fun nothingLandsFurtherOutThanTheCareItWasGiven() {
        Care.entries.forEach { care ->
            ids.forEach { id ->
                val landing = Settle.of(id, care)
                assertTrue(
                    abs(landing.turnDegrees) <= care.degrees,
                    "$care id=$id turned ${landing.turnDegrees} past ${care.degrees}",
                )
                assertTrue(
                    abs(landing.slipX) <= care.slip && abs(landing.slipY) <= care.slip,
                    "$care id=$id slipped ${landing.slipX},${landing.slipY} past ${care.slip}",
                )
            }
        }
    }

    @Test
    fun aSquaredPileIsTidierThanAThrownOneForEveryCardInIt() {
        // Not merely on average. A deck with one card in it lying further out
        // than the graveyard beside it is the whole feature failing on the one
        // board somebody is looking at.
        ids.forEach { id ->
            val squared = abs(Settle.of(id, Care.SQUARED).turnDegrees)
            val placed = abs(Settle.of(id, Care.PLACED).turnDegrees)
            val thrown = abs(Settle.of(id, Care.THROWN).turnDegrees)
            assertTrue(squared <= placed && placed <= thrown, "id=$id $squared $placed $thrown")
        }
    }

    @Test
    fun consecutiveInstanceIdsLandNowhereNearEachOther() {
        // A dealt hand is five *consecutive* instance ids, and that is the one
        // input a cheap `id * prime` hash fails on: consecutive ids come out of
        // it in an arithmetic progression, so a hand of five fans out in an even
        // ramp that reads as drawn rather than as dealt. What says that is not
        // happening is that neighbouring ids are uncorrelated.
        //
        // Stated as a correlation and not as "no hand leans one way", which is
        // what this test said first and was wrong to: five fair coins come up
        // the same way once in sixteen tries, so a hand that happens to lean is
        // the feature working, not failing.
        val turns = ids.map { Settle.of(it, Care.PLACED).turnDegrees }
        assertTrue(
            abs(correlation(turns.dropLast(1), turns.drop(1))) < 0.1f,
            "neighbouring ids correlate at ${correlation(turns.dropLast(1), turns.drop(1))}",
        )

        // And a hand leaning entirely one way stays the rare case it should be.
        val oneWay = (0 until ids.size - 5).count { first ->
            val hand = (first until first + 5).map { Settle.of(it, Care.PLACED).turnDegrees }
            hand.all { it < 0f } || hand.all { it > 0f }
        }
        val rate = oneWay.toFloat() / (ids.size - 5)
        assertTrue(rate < 0.12f, "one hand in ${1 / rate} leans entirely one way")
    }

    @Test
    fun theWholeHandDoesNotLeanOffToOneSide() {
        // Every channel has to be centred, or a board of sixty cards is sixty
        // cards all rotated the same couple of degrees — which is not a mess,
        // it is a crooked mat.
        Care.entries.forEach { care ->
            val turns = ids.map { Settle.of(it, care).turnDegrees }
            val slips = ids.map { Settle.of(it, care).slipX }
            assertTrue(
                abs(turns.average()) < care.degrees * 0.05f,
                "$care turns average ${turns.average()}",
            )
            assertTrue(
                abs(slips.average()) < care.slip * 0.05f,
                "$care slips average ${slips.average()}",
            )
        }
    }

    @Test
    fun theTurnAndTheTwoSlipsAreNotTheSameNumberWearingThreeHats() {
        // Derived from one hash by shifting, they correlate — and a hand where
        // every card that leans left is also low and to the left is a pattern
        // rather than an unevenness.
        val turns = ids.map { Settle.of(it, Care.PLACED).turnDegrees }
        val xs = ids.map { Settle.of(it, Care.PLACED).slipX }
        val ys = ids.map { Settle.of(it, Care.PLACED).slipY }

        assertTrue(abs(correlation(turns, xs)) < 0.1f, "turn/x ${correlation(turns, xs)}")
        assertTrue(abs(correlation(turns, ys)) < 0.1f, "turn/y ${correlation(turns, ys)}")
        assertTrue(abs(correlation(xs, ys)) < 0.1f, "x/y ${correlation(xs, ys)}")
    }

    @Test
    fun aCarriedCardIsExact() {
        // A finger does not land a card approximately. `Landing.Square` is what
        // the call site uses while a card is in the air, and it has to be the
        // identity or a pickup would nudge the card sideways.
        assertEquals(0f, Settle.Landing.Square.turnDegrees)
        assertEquals(0f, Settle.Landing.Square.slipX)
        assertEquals(0f, Settle.Landing.Square.slipY)
    }
}
