package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a gesture is still holding the card it picked up.
 *
 * A `DragOrigin` into the hand or a pile is an **index**, and an index is only
 * true of the board it was taken from. With two hands on the table the other one
 * can renumber that pile mid-drag, and the gesture would then release, correctly
 * and silently, the card next door.
 *
 * It is not a two-handed problem in principle — tapping a card out of a spread
 * while another is being dragged out of the same spread does it too — it is only
 * that until there were two gestures there was never a second one to be wrong.
 */
class StillHoldsTest {

    private fun table() = PlayField(
        hand = (0 until 4).map { BoardCard(it, CardId(it)) },
        deck = (10 until 16).map { BoardCard(it, CardId(it)) },
    )

    // ---- the hazard ------------------------------------------------------------

    @Test
    fun anIndexIntoAPileGoesStaleWhenSomethingBeforeItLeaves() {
        val before = table()
        val what = DragOrigin.Pile(BoardSlot.Deck, 3)
        val holding = before.cardAt(what)!!.instanceId
        assertTrue(before.stillHolds(what, holding))

        // The other hand takes an earlier card out of the same deck.
        val after = before.copy(deck = before.deck.filterIndexed { index, _ -> index != 1 })

        assertFalse(
            after.stillHolds(what, holding),
            "index 3 now names a different card and nothing said so",
        )
    }

    @Test
    fun anIndexIntoTheHandGoesStaleWhenTheHandIsRearranged() {
        val before = table()
        val what = DragOrigin.Hand(2)
        val holding = before.cardAt(what)!!.instanceId

        val after = before.reorderHand(from = 0, to = 4)!!

        assertFalse(after.stillHolds(what, holding))
    }

    // ---- and what must not trip it ---------------------------------------------

    @Test
    fun aCardOnTheMatIsNamedRatherThanCounted() {
        // `DragOrigin.Mat` carries the instance itself, so nothing that happens
        // to any other card can make it mean something else. Which is why one
        // hand can go on dragging a card across the board while the other empties
        // the graveyard.
        val on = table().playFromHand(1, MatPoint.Centre, CardPosition.FACE_UP_ATK)!!
        val id = on.mat.single().id
        val what = DragOrigin.Mat(id)

        val after = on.handToGraveyard(0)!!

        assertTrue(after.stillHolds(what, id))
    }

    @Test
    fun somethingAfterItLeavingChangesNothing() {
        // Only the cards *before* it renumber it, which is worth pinning: a
        // guard that fired on every change to the pile would put a card back
        // every time the other hand did anything at all.
        val before = table()
        val what = DragOrigin.Pile(BoardSlot.Deck, 1)
        val holding = before.cardAt(what)!!.instanceId

        val after = before.copy(deck = before.deck.filterIndexed { index, _ -> index != 4 })

        assertTrue(after.stillHolds(what, holding))
    }

    @Test
    fun anIndexOffTheEndIsNotHoldingAnything() {
        assertFalse(table().stillHolds(DragOrigin.Pile(BoardSlot.Deck, 99), 10))
        assertFalse(table().stillHolds(DragOrigin.Hand(9), 0))
    }
}
