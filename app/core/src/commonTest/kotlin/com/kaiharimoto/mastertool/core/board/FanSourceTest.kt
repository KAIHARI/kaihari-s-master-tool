package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a card came out of the spread that is open, which decides whether
 * taking it squares the pile back up.
 *
 * Every case here passed trivially before this file existed, because the check
 * the table actually ran was `from.pile == fanned` — a [BoardSlot] against a
 * [DragOrigin]. Kotlin allows it (two sealed interfaces could in principle share
 * a subclass) and it is always false, so a card dragged out of a spread deck left
 * the deck lying open across the board. Only *tapping* a card closed the fan,
 * which is why it read as "the drag is broken" rather than as one dead comparison.
 */
class FanSourceTest {

    private val deckFan = DragOrigin.Pile(BoardSlot.Deck, 0)

    // ---- the bug, stated ------------------------------------------------------

    @Test
    fun aCardDraggedOutOfTheSpreadDeckCameFromIt() {
        // The index is where in the spread the finger landed, and it is never 0
        // for anything but the top card — which is exactly why comparing the
        // whole origin against the fan would not have worked either.
        assertTrue(DragOrigin.Pile(BoardSlot.Deck, 17).cameFrom(deckFan))
    }

    @Test
    fun theTopCardOfTheSpreadDeckCameFromItToo() {
        assertTrue(DragOrigin.Pile(BoardSlot.Deck, 0).cameFrom(deckFan))
    }

    @Test
    fun aCardFromAnotherPileDidNot() {
        assertFalse(DragOrigin.Pile(BoardSlot.Graveyard, 3).cameFrom(deckFan))
    }

    // ---- a stack on the mat, which spreads by the same feature -----------------

    @Test
    fun aBuriedCardCameFromTheStackItIsBuriedIn() {
        // `Buried` names the card on top and how far down; the fan is open on
        // that top card. Two names for one object, which is what `fanSource` is.
        assertTrue(DragOrigin.Buried(under = 7, index = 2).cameFrom(DragOrigin.Mat(7)))
    }

    @Test
    fun aBuriedCardDidNotComeFromSomeOtherStack() {
        assertFalse(DragOrigin.Buried(under = 7, index = 2).cameFrom(DragOrigin.Mat(9)))
    }

    @Test
    fun theTopCardOfAnOpenStackCameFromIt() {
        // It is the one card of a spread stack that is not `Buried` — the fan is
        // opened *on* it — and dragging it away takes the whole pile with it, so
        // the spread it left behind is about nothing and has to close.
        assertTrue(DragOrigin.Mat(7).cameFrom(DragOrigin.Mat(7)))
    }

    // ---- nothing open ---------------------------------------------------------

    @Test
    fun nothingCameFromAFanThatIsNotOpen() {
        assertFalse(DragOrigin.Pile(BoardSlot.Deck, 4).cameFrom(null))
        assertFalse(DragOrigin.Mat(7).cameFrom(null))
    }

    @Test
    fun aHandCardNeverCameFromAFan() {
        // You cannot search your hand — you can already see all of it — so a
        // hand card is never in a spread, whatever happens to be open.
        assertFalse(DragOrigin.Hand(2).cameFrom(deckFan))
        assertFalse(DragOrigin.Hand(2).cameFrom(DragOrigin.Mat(7)))
    }
}
