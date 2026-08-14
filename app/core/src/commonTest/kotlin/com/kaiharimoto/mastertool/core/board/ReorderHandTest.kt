package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Arranging your hand, which is the most ordinary thing anybody does with one
 * and was the one thing this table could not do at all.
 *
 * The index is a **gap**, not a card — zero before everything, `hand.size` after
 * everything — because that is the number a finger is pointing at and the number
 * `List.add` takes. The two no-ops fall out of it for free: a card dropped in the
 * gap before itself and a card dropped in the gap after itself both describe the
 * hand it is already in.
 */
class ReorderHandTest {

    private fun hand(size: Int) = PlayField(hand = (0 until size).map { BoardCard(it, CardId(it)) })

    private fun PlayField.order() = hand.map { it.instanceId }

    // ---- the move -------------------------------------------------------------

    @Test
    fun aCardMovesToTheFront() {
        val done = assertNotNull(hand(5).reorderHand(from = 3, to = 0))
        assertEquals(listOf(3, 0, 1, 2, 4), done.order())
    }

    @Test
    fun aCardMovesToTheBack() {
        val done = assertNotNull(hand(5).reorderHand(from = 1, to = 5))
        assertEquals(listOf(0, 2, 3, 4, 1), done.order())
    }

    @Test
    fun aCardMovesForwardsPastOne() {
        // Gap 3 in a hand of five is between the third and fourth cards. Moving
        // card 1 there leaves it after card 2, which is one place along — the
        // shift that makes "to" and "from" not directly comparable.
        val done = assertNotNull(hand(5).reorderHand(from = 1, to = 3))
        assertEquals(listOf(0, 2, 1, 3, 4), done.order())
    }

    @Test
    fun aCardMovesBackwardsPastOne() {
        val done = assertNotNull(hand(5).reorderHand(from = 3, to = 2))
        assertEquals(listOf(0, 1, 3, 2, 4), done.order())
    }

    // ---- and it is never anything but a rearrangement --------------------------

    @Test
    fun everyMoveKeepsEveryCard() {
        // Exhaustive over a hand of five: no gap loses a card, duplicates one,
        // or reorders anything it was not asked to.
        val start = hand(5)
        for (from in 0 until 5) {
            for (to in 0..5) {
                val done = start.reorderHand(from, to) ?: continue
                assertEquals(
                    start.order().sorted(),
                    done.order().sorted(),
                    "from $from to $to lost or gained a card",
                )
                assertEquals(
                    start.order().filterNot { it == from },
                    done.order().filterNot { it == from },
                    "from $from to $to disturbed the other cards",
                )
            }
        }
    }

    // ---- what is not a move ----------------------------------------------------

    @Test
    fun bothGapsBesideACardAreItsOwnPlace() {
        assertNull(hand(5).reorderHand(from = 2, to = 2))
        assertNull(hand(5).reorderHand(from = 2, to = 3))
    }

    @Test
    fun aGapThatDoesNotExistIsNotAMove() {
        assertNull(hand(5).reorderHand(from = 2, to = 6))
        assertNull(hand(5).reorderHand(from = 2, to = -1))
    }

    @Test
    fun aCardThatIsNotThereIsNotAMove() {
        assertNull(hand(5).reorderHand(from = 9, to = 0))
        assertNull(hand(0).reorderHand(from = 0, to = 0))
    }

    // ---- and a card arriving from elsewhere lands where it was aimed ------------

    @Test
    fun aCardOffTheMatLandsAtTheGapItWasDroppedOn() {
        val start = hand(3).playFromHand(0, MatPoint.Centre, CardPosition.FACE_UP_ATK)!!
        val id = start.mat.single().id

        val done = assertNotNull(start.toHand(id, at = 1))

        assertEquals(listOf(1, 0, 2), done.order())
    }

    @Test
    fun aCardOffTheMatWithNoGapNamedGoesOnTheEnd() {
        // Which is what every caller that is not a drag wants: a card sent back
        // by a menu item has not been aimed anywhere.
        val start = hand(3).playFromHand(0, MatPoint.Centre, CardPosition.FACE_UP_ATK)!!
        val id = start.mat.single().id

        val done = assertNotNull(start.toHand(id))

        assertEquals(listOf(1, 2, 0), done.order())
    }
}
