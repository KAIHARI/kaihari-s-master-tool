package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /**
     * The drawn row for a hand of [count] with [moving] in the air, and the
     * gap that a finger at [x] is asking for.
     *
     * A card in the air no longer leaves its place standing: the row closes up
     * behind it, which is what a hand does and what the measurement always
     * assumed. What changed is that the *drawing* now agrees.
     */
    private fun insert(x: Float, count: Int, moving: Int? = null) = HandFan.insertAt(
        band, card,
        HandFan.row(count, lifted = setOfNotNull(moving)),
        count, x, step,
    )

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

        // Just past the boundary between the drawn row's second and third cards;
        // in the full hand's numbering that is gap 3, because everything past
        // the card in the air shifted down when it left.
        //
        // Six tenths of the way rather than the midpoint, and that is not
        // fussiness. The midpoint is the rounding boundary itself, so which side
        // of it `roundToInt` lands on is decided by the last bit of a float — and
        // when the hand's default step went from 0.62 of a card to 0.74 the
        // arithmetic came out at 1.4999999 instead of 1.5 and this test failed
        // without anything about the hand having changed. A probe on a tie is a
        // test of the FPU.
        val pastTheSecondCard = centre(1, 4) * 0.4f + centre(2, 4) * 0.6f

        assertEquals(3, insert(pastTheSecondCard, count, moving))
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
        assertEquals(0, HandFan.insertAt(flat.hand, flat.cardWidth, HandRow.of(5), 5, 0f, step))
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

    // ---- the row a hand is actually drawn as ----------------------------------

    @Test
    fun aCardInTheAirLeavesNoPlaceStandingAndTheRestCloseUp() {
        val row = HandFan.row(5, lifted = setOf(2))
        assertEquals(listOf(0, 1, 3, 4), row.places)
        assertEquals(4, row.size)
        assertEquals(null, row.placeOf(2))
        assertEquals(2, row.placeOf(3))
    }

    @Test
    fun aPlaceIsHeldOpenWhereTheCardIsGoing() {
        // The whole of kai's fourth report. A caret painted in a gap says where
        // a card will go; a gap that is really there shows you.
        val row = HandFan.row(5, lifted = setOf(0), opening = listOf(3))
        assertEquals(listOf(1, 2, null, 3, 4), row.places)
        assertEquals(listOf(2), row.open)
        assertEquals(2, row.openingFor(3, 5))
    }

    @Test
    fun tenHandsCanHoldTenPlacesOpen() {
        // Nothing here is written against there being one carry: the table has
        // ten lanes and a hand of fourteen can legally have several of its cards
        // in the air at once, each with a place waiting for it.
        val row = HandFan.row(6, lifted = setOf(0, 5), opening = listOf(2, 4))
        assertEquals(listOf(1, null, 2, 3, null, 4), row.places)
        assertEquals(listOf(1, 4), row.open)
    }

    @Test
    fun aGapPastTheEndIsAPlaceOnTheEnd() {
        val row = HandFan.row(3, lifted = setOf(1), opening = listOf(3))
        assertEquals(listOf(0, 2, null), row.places)
        assertEquals(3, row.gapAfter(2, 3))
    }

    @Test
    fun theRowASettledCarryAsksForIsTheRowItIsAlreadyDrawnAs() {
        // The property the whole arrangement rests on, and the one that could
        // have made it oscillate: the gap `insertAt` names is fed back in as the
        // place the row holds open, which moves every card after it. If that
        // were not a fixed point the hand would flicker between two layouts at
        // sixty frames a second.
        //
        // Swept over every hand size the band lays out differently, every card
        // that could be in the air, and four hundred positions across the whole
        // band and well past both ends.
        listOf(1, 2, 3, 5, 9, 10, 14).forEach { count ->
            (0 until count).forEach { moving ->
                var x = band.left - card * 2f
                while (x <= band.right + card * 2f) {
                    val lifted = setOf(moving)
                    // One step of feedback, then it must not move again. The
                    // first answer is read off a row with nothing open in it, so
                    // it is allowed to differ by a spelling — with the only card
                    // of a hand in the air, "before it" and "after it" are the
                    // same empty row.
                    val opened = HandFan.insertAt(
                        band, card, HandFan.row(count, lifted), count, x, step,
                    )
                    var asked = HandFan.insertAt(
                        band, card, HandFan.row(count, lifted, listOf(opened)), count, x, step,
                    )
                    repeat(4) {
                        val row = HandFan.row(count, lifted, opening = listOf(asked))
                        val again = HandFan.insertAt(band, card, row, count, x, step)
                        assertEquals(
                            asked, again,
                            "hand $count, card $moving in the air, finger at $x: " +
                                "the row asked for gap $asked and then for $again",
                        )
                        asked = again
                    }
                    x += card / 8f
                }
            }
        }
    }

    @Test
    fun anOpenPlaceIsWhereTheCardWillActuallyBe() {
        // `openingFor` is the inverse of the row: the place held open for a gap
        // has to be the place the card lands in when the row closes over it.
        // Otherwise the hole is one slot from the card that fills it, which is
        // worse than no hole at all.
        listOf(3, 5, 9, 14).forEach { count ->
            (0 until count).forEach { moving ->
                (0..count).forEach { at ->
                    val row = HandFan.row(count, setOf(moving), listOf(at))
                    val place = row.openingFor(at, count)
                    assertNotNull(place, "hand $count, card $moving, gap $at has no place")
                    // Round-tripped rather than compared to `at` directly,
                    // because two gap numbers can name one physical place: with
                    // card 0 in the air, gaps 0 and 1 are both the front of the
                    // row and `reorderHand` refuses both as no move at all. What
                    // has to be true is that the hole you can see is the hole a
                    // finger over it asks for.
                    val asked = HandFan.insertAt(
                        band, card, row, count,
                        HandFan.centreOf(band, card, place, row.size, step),
                        step,
                    )
                    assertEquals(
                        place,
                        row.openingFor(asked, count),
                        "hand $count, card $moving: the place held open for gap $at is not it",
                    )
                }
            }
        }
    }

    @Test
    fun aRowWithNothingInTheAirIsTheRowThatAlwaysShipped() {
        // Every value bit-identical to the old arithmetic when nothing is being
        // carried, which is the state the hand is in almost all of the time.
        listOf(1, 5, 14).forEach { count ->
            val row = HandRow.of(count)
            assertEquals((0 until count).toList(), row.places)
            (0 until count).forEach {
                assertEquals(centre(it, count), HandFan.centreOf(band, card, it, row.size, step))
            }
        }
    }
}
