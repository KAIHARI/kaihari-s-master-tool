package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hand, and which gap in it a finger is over.
 *
 * [HandFan.insertAt] is the inverse of [HandFan.centreOf], and the property that
 * matters is that the two agree: a card released over where the fourth card is
 * drawn is asking to be the fourth card. Everything else about arranging a hand
 * falls out of that, and a version of it written against a remembered copy of a
 * layout would be a caret drawn in a gap the card does not go into.
 */
class HandFanTest {

    private val layout = BoardLayouter.solve(1600f, 1000f, 59f / 86f)
    private val band = layout.hand
    private val card = layout.cardWidth
    private val step = StageTuning.DEFAULT.hand.stepFraction

    private fun centre(index: Int, count: Int) =
        HandFan.centreOf(band, card, index, count, step)

    private fun insert(x: Float, count: Int, moving: Int? = null) =
        HandFan.insertAt(band, card, count, x, step, moving)

    // ---- the inverse ----------------------------------------------------------

    @Test
    fun releasedOverACardItTakesThatCardsPlace() {
        // A card arriving from elsewhere, so the hand really does hold `count`
        // and is about to hold one more. Dropped on the third card's centre, it
        // becomes the third card and pushes that one along.
        val count = 6
        repeat(count) { index ->
            assertEquals(index, insert(centre(index, count), count), "over card $index")
        }
    }

    @Test
    fun releasedPastTheLastCardItGoesOnTheEnd() {
        val count = 6
        assertEquals(count, insert(band.right + card, count))
    }

    @Test
    fun releasedBeforeTheFirstCardItGoesOnTheFront() {
        assertEquals(0, insert(band.left - card, 6))
    }

    @Test
    fun theAnswerIsAlwaysAGapThatExists() {
        // Every x across the whole band and well past both ends, at several hand
        // sizes: the index is always something `List.add` would accept. An
        // insert index out of range is a crash rather than a wrong card.
        listOf(1, 2, 5, 14).forEach { count ->
            var x = band.left - card * 3f
            while (x <= band.right + card * 3f) {
                assertTrue(insert(x, count) in 0..count, "count $count at x $x")
                x += card / 8f
            }
        }
    }

    // ---- a card that came out of the hand it is going back into ---------------

    @Test
    fun theRowIsMeasuredAsItIsDrawnWhileACardIsInTheAir() {
        // Five cards with the first one in the air is a row of *four*, drawn as
        // four, and the finger is over the gaps between those four. Measuring
        // against the five slots the card came from puts every answer half a gap
        // out — which is a card that lands one place from where the caret said.
        val count = 5
        val moving = 0

        // The middle of the drawn row of four is the boundary between its second
        // and third cards; in the full hand's numbering that is gap 3, because
        // everything past the card in the air shifted down when it left.
        val betweenSecondAndThird = (centre(1, 4) + centre(2, 4)) / 2f

        assertEquals(3, insert(betweenSecondAndThird, count, moving))
    }

    @Test
    fun aCardHeldOverItsOwnPlaceAsksForItsOwnPlace() {
        // Which `reorderHand` then reads as no move at all, so a card picked up
        // and put straight back down does not land on the undo stack.
        val count = 5
        listOf(0, 2, 4).forEach { moving ->
            val row = count - 1
            // A gap is claimed by the *centre of the drawn card it comes before*
            // — which is what `releasedOverACardItTakesThatCardsPlace` pins — so
            // the gap the card came out of is the one before the drawn card that
            // closed up behind it. At the end of the hand that index is one past
            // the row, which `centreOf` answers as the position after the last
            // card rather than by refusing.
            val at = insert(centre(moving, row), count, moving)
            assertTrue(at == moving || at == moving + 1, "moving $moving asked for $at")
        }
    }

    // ---- degenerate rows ------------------------------------------------------

    @Test
    fun oneCardHasTwoSidesAndNothingToDivideBy() {
        // The step is zero with a single card, so the "how many boundaries have
        // I passed" arithmetic has nothing to divide by and the answer is which
        // side of it the finger is on.
        val only = centre(0, 1)

        assertEquals(0, insert(only - card, 1))
        assertEquals(1, insert(only + card, 1))
    }

    @Test
    fun anEmptyHandTakesTheCardAtTheFront() {
        assertEquals(0, insert(band.centerX, 0))
    }

    @Test
    fun aBoardWithNoRoomStillAnswers() {
        val flat = BoardLayouter.solve(0f, 0f, 59f / 86f)
        assertEquals(0, HandFan.insertAt(flat.hand, flat.cardWidth, 5, 0f, step))
    }

    // ---- the pose, which is the reading that already shipped -------------------

    @Test
    fun theCardsSitInsideTheBandAndInOrder() {
        val count = 14
        val xs = (0 until count).map { centre(it, count) }

        assertTrue(xs.zipWithNext().all { (a, b) -> b > a }, "the fan must run left to right")
        assertTrue(xs.first() - card / 2f >= band.left - 0.01f, "the first card left the band")
        assertTrue(xs.last() + card / 2f <= band.right + 0.01f, "the last card left the band")
    }

    @Test
    fun aSmallHandIsCentredRatherThanCrowdedToOneEnd() {
        val xs = (0 until 3).map { centre(it, 3) }
        assertEquals(band.centerX, (xs.first() + xs.last()) / 2f, 0.01f)
    }
}
