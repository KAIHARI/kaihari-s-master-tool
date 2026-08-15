package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding the card a gesture is holding, after the board has moved under it.
 *
 * A `DragOrigin` into the hand or a pile is an **index**, and an index is only
 * true of the board it was taken from. With ten lanes on the table another hand
 * can renumber that pile mid-drag, and the gesture would then release, correctly
 * and silently, the card next door.
 *
 * It is not a two-handed problem in principle — tapping a card out of a spread
 * while another is being dragged out of the same spread does it too — it is only
 * that until there were two gestures there was never a second one to be wrong.
 *
 * This used to be `stillHolds`, a boolean, and the release turned false into a
 * refusal: the card left the air and nothing happened. Safe, and kai's third
 * report — *"I'm trying to take 2+ cards out of the deck at the same time and
 * the hold works but when I let one go the other card I'm holding goes back into
 * the deck."* Refusing was never the only safe answer, because the gesture was
 * already carrying the card's own identity: it does not need the index to have
 * survived, it needs to be told where the card is now.
 */
class RebaseTest {

    private fun table() = PlayField(
        hand = (0 until 4).map { BoardCard(it, CardId(it)) },
        deck = (10 until 16).map { BoardCard(it, CardId(it)) },
    )

    // ---- the hazard, and the repair ---------------------------------------------

    @Test
    fun aPileIndexIsRebasedWhenSomethingBeforeItLeaves() {
        val before = table()
        val what = DragOrigin.Pile(BoardSlot.Deck, 3)
        val holding = before.cardAt(what)!!.instanceId
        assertEquals(what, before.rebase(what, holding))

        // The other hand takes an earlier card out of the same deck.
        val after = before.copy(deck = before.deck.filterIndexed { index, _ -> index != 1 })

        assertFalse(after.stillHolds(what, holding), "the index really did go stale")
        assertEquals(
            DragOrigin.Pile(BoardSlot.Deck, 2),
            after.rebase(what, holding),
            "the card is one place nearer the top and the gesture is still holding it",
        )
        assertEquals(holding, after.cardAt(after.rebase(what, holding)!!)!!.instanceId)
    }

    @Test
    fun aHandIndexIsRebasedWhenTheHandIsRearranged() {
        val before = table()
        val what = DragOrigin.Hand(2)
        val holding = before.cardAt(what)!!.instanceId

        val after = before.reorderHand(from = 0, to = 4)!!

        assertFalse(after.stillHolds(what, holding))
        assertEquals(holding, after.cardAt(after.rebase(what, holding)!!)!!.instanceId)
    }

    @Test
    fun twoHandsOutOfTheSameDeckBothLandTheirOwnCard() {
        // kai's report, end to end. Two lanes hold two cards of the deck;
        // nothing commits until a release, so both are still *in* the deck while
        // they are in the air. The first release renumbers the second's index —
        // and the second must still deliver the card it is actually holding.
        val start = table()
        val first = DragOrigin.Pile(BoardSlot.Deck, 0)
        val second = DragOrigin.Pile(BoardSlot.Deck, 3)
        val heldFirst = start.cardAt(first)!!.instanceId
        val heldSecond = start.cardAt(second)!!.instanceId

        val afterFirst = DropCommit.commit(start, first, DropIntent.Graveyard)!!
        val rebased = afterFirst.rebase(second, heldSecond)
        assertEquals(DragOrigin.Pile(BoardSlot.Deck, 2), rebased)

        val afterSecond = DropCommit.commit(afterFirst, rebased!!, DropIntent.Graveyard)!!
        assertEquals(
            listOf(heldSecond, heldFirst),
            afterSecond.graveyard.map { it.instanceId },
            "the second hand delivered somebody else's card",
        )
        assertEquals(4, afterSecond.deck.size)
    }

    // ---- and what must not be repaired ------------------------------------------

    @Test
    fun aCardThatHasGenuinelyLeftIsNotFoundSomewhereElse() {
        // The other hand drew the very card this one is dragging out of the deck
        // fan. It is in the hand now, and it is also in the air. Rewriting the
        // origin to `Hand(n)` would let the drop complete — the same piece of
        // cardboard, a different move, and two entries on the undo stack for one
        // gesture. The search stays inside the pile it came out of.
        val before = table()
        val what = DragOrigin.Pile(BoardSlot.Deck, 0)
        val holding = before.cardAt(what)!!.instanceId

        val after = before.draw()!!

        assertEquals(holding, after.hand.last().instanceId, "the test is about the wrong card")
        assertNull(after.rebase(what, holding))
    }

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
        assertEquals(what, after.rebase(what, id))
    }

    @Test
    fun aMatCardThatHasBeenBuriedIsNoLongerTheMoveItWas() {
        // The one case a rebase deliberately declines. The other hand stacked
        // something on the card this one is dragging, so it has stopped being a
        // top card — and the top of a stack takes the stack with it, so carrying
        // on would be a different move from the one that was started. Putting it
        // back is the honest answer.
        val two = table()
            .playFromHand(0, MatPoint(0.3f, 0.5f), CardPosition.FACE_UP_ATK)!!
            .playFromHand(0, MatPoint(0.7f, 0.5f), CardPosition.FACE_UP_ATK)!!
        val (lower, upper) = two.mat.map { it.id }
        val what = DragOrigin.Mat(lower)

        val stacked = two.stackOnto(upper, lower)!!

        assertNull(stacked.rebase(what, lower))
    }

    @Test
    fun somethingAfterItLeavingChangesNothing() {
        // Only the cards *before* it renumber it, which is worth pinning: a
        // repair that fired on every change to the pile would be doing work on
        // every release anybody makes.
        val before = table()
        val what = DragOrigin.Pile(BoardSlot.Deck, 1)
        val holding = before.cardAt(what)!!.instanceId

        val after = before.copy(deck = before.deck.filterIndexed { index, _ -> index != 4 })

        assertTrue(after.stillHolds(what, holding))
        assertEquals(what, after.rebase(what, holding))
    }

    @Test
    fun anIndexOffTheEndIsNotHoldingAnything() {
        assertFalse(table().stillHolds(DragOrigin.Pile(BoardSlot.Deck, 99), 10))
        assertFalse(table().stillHolds(DragOrigin.Hand(9), 0))
        assertNull(table().rebase(DragOrigin.Pile(BoardSlot.Deck, 99), 999))
        assertNull(table().rebase(DragOrigin.Hand(9), 999))
    }

    @Test
    fun aBuriedCardComesOffTheSideItIsOnNow() {
        // `under` is the top card, then what is beneath it, then its materials,
        // and `liftFromUnder` branches on that same boundary — so an index found
        // by identity always lands on the side the card is on *now*. Which is
        // the side it should come off: a material comes off clean and a card in
        // the pile keeps whatever is attached to it, and those are facts about
        // where the card is rather than about where it was.
        val two = table()
            .playFromHand(0, MatPoint(0.3f, 0.5f), CardPosition.FACE_UP_ATK)!!
            .playFromHand(0, MatPoint(0.7f, 0.5f), CardPosition.FACE_UP_ATK)!!
        val (lower, upper) = two.mat.map { it.id }
        val stacked = two.stackOnto(upper, lower)!!

        val what = DragOrigin.Buried(upper, 1)
        val holding = stacked.cardAt(what)!!.instanceId
        assertEquals(lower, holding, "the buried card is the one that was stacked onto")
        assertEquals(what, stacked.rebase(what, holding))
    }
}
