package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Carrying several cards at once.
 *
 * The single-card versions of these live in `DeckPlacementTest`; this is the
 * same contract with the drop-position arithmetic multiplied, which is where it
 * gets easy to be wrong in one direction only.
 */
class MultiPlacementTest {

    private fun deck(vararg ids: Int) =
        Deck(main = ids.map(::CardId), extra = emptyList(), side = emptyList())

    private fun mainOf(edit: DeckEdit): List<Int> =
        (edit as DeckEdit.Applied).deck.main.map { it.value }

    private fun move(deck: Deck, indices: Collection<Int>, insertBefore: Int) =
        DeckEditor.reorderManyTo(deck, DeckSection.MAIN, indices, insertBefore)

    @Test
    fun aGroupLandsWhereItWasAimed() {
        // 1 2 3 4 5, carrying 1 and 2 to before 4.
        val result = move(deck(1, 2, 3, 4, 5), listOf(0, 1), insertBefore = 3)

        assertEquals(listOf(3, 1, 2, 4, 5), mainOf(result))
    }

    @Test
    fun draggingRightAccountsForTheGapsLeftBehind() {
        // The trap. The drop position describes the list before anything is
        // lifted, so a group moving rightwards has to slide back through its own
        // vacated slots. Without that discount this lands two places too far.
        val result = move(deck(1, 2, 3, 4, 5, 6), listOf(0, 1), insertBefore = 5)

        assertEquals(listOf(3, 4, 5, 1, 2, 6), mainOf(result))
    }

    @Test
    fun draggingLeftNeedsNoDiscount() {
        val result = move(deck(1, 2, 3, 4, 5), listOf(3, 4), insertBefore = 1)

        assertEquals(listOf(1, 4, 5, 2, 3), mainOf(result))
    }

    @Test
    fun aScatteredSelectionGathersInDeckOrder() {
        val result = move(deck(1, 2, 3, 4, 5, 6), listOf(4, 0, 2), insertBefore = 6)

        assertEquals(listOf(2, 4, 6, 1, 3, 5), mainOf(result))
    }

    @Test
    fun theCarriedCardsKeepTheirOrder() {
        // Picking up four cards and putting them down must not shuffle them: it
        // is an arrangement, and it is the arrangement that is being moved.
        val result = move(deck(9, 8, 7, 6, 5), listOf(0, 1, 2), insertBefore = 5)

        assertEquals(listOf(6, 5, 9, 8, 7), mainOf(result))
    }

    @Test
    fun droppingAtTheFrontWorks() {
        assertEquals(
            listOf(4, 5, 1, 2, 3),
            mainOf(move(deck(1, 2, 3, 4, 5), listOf(3, 4), insertBefore = 0)),
        )
    }

    @Test
    fun droppingPastTheEndWorks() {
        assertEquals(
            listOf(3, 4, 5, 1, 2),
            mainOf(move(deck(1, 2, 3, 4, 5), listOf(0, 1), insertBefore = 5)),
        )
    }

    @Test
    fun movingIsAlwaysAPermutation() {
        // The property that matters most: however the arithmetic lands, a move
        // must never invent, lose or duplicate a card.
        val start = deck(1, 2, 3, 4, 5, 6, 7, 8)

        (0..8).forEach { destination ->
            listOf(listOf(0), listOf(0, 1), listOf(2, 5), listOf(1, 3, 6), listOf(0, 7)).forEach { picked ->
                val moved = move(start, picked, destination)
                assertTrue(moved is DeckEdit.Applied, "rejected $picked -> $destination")
                assertEquals(
                    start.main.sortedBy { it.value },
                    moved.deck.main.sortedBy { it.value },
                    "picked $picked to $destination",
                )
            }
        }
    }

    @Test
    fun movingEveryCardChangesNothing() {
        val start = deck(1, 2, 3)
        assertEquals(listOf(1, 2, 3), mainOf(move(start, listOf(0, 1, 2), insertBefore = 0)))
        assertEquals(listOf(1, 2, 3), mainOf(move(start, listOf(0, 1, 2), insertBefore = 3)))
    }

    @Test
    fun movingNothingIsAllowedAndDoesNothing() {
        val start = deck(1, 2, 3)
        assertEquals(start, (move(start, emptyList(), insertBefore = 1) as DeckEdit.Applied).deck)
    }

    @Test
    fun aPositionThatIsNotThereIsRejected() {
        val start = deck(1, 2, 3)

        assertTrue(move(start, listOf(0, 9), insertBefore = 1) is DeckEdit.Rejected)
        assertTrue(move(start, listOf(-1), insertBefore = 1) is DeckEdit.Rejected)
        assertTrue(move(start, listOf(0), insertBefore = 4) is DeckEdit.Rejected)
    }

    @Test
    fun duplicatePositionsAreCountedOnce() {
        // The selection is a set, but the parameter is a collection, and a caller
        // handing the same index twice must not duplicate the card.
        assertEquals(
            listOf(2, 3, 1),
            mainOf(move(deck(1, 2, 3), listOf(0, 0, 0), insertBefore = 3)),
        )
    }

    @Test
    fun theLandingIndexIsWhereTheCardsActuallyEndUp() {
        // Written down once and used twice -- by the move, and by whatever has
        // to know afterwards where the group went. Two copies of this discount
        // is how the two come to disagree.
        val start = deck(1, 2, 3, 4, 5, 6)

        listOf(listOf(0, 1), listOf(2, 5), listOf(1, 3, 4), listOf(5)).forEach { picked ->
            (0..6).forEach { destination ->
                val landed = DeckEditor.landingIndex(picked, destination)
                val moved = (move(start, picked, destination) as DeckEdit.Applied).deck.main

                val carried = picked.sorted().map { start.main[it] }
                assertEquals(
                    carried,
                    moved.subList(landed, landed + carried.size),
                    "picked $picked to $destination",
                )
            }
        }
    }

    @Test
    fun duplicateCardsMoveByPositionNotByPasscode() {
        // Three copies of the same card: moving the first must move the first,
        // and the other two must stay where they are.
        val result = move(deck(7, 7, 7, 1), listOf(0), insertBefore = 4)

        assertEquals(listOf(7, 7, 1, 7), mainOf(result))
    }
}
