package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrangementTest {

    private fun ids(vararg values: Int) = values.map(::CardId)

    /** Tens digit as the group, so the expectations read at a glance. */
    private val tens: (CardId) -> Int? = { it.value / 10 }

    @Test
    fun groupsCollectAndNothingInsideThemMoves() {
        // 11 21 12 22 13 -> the ones in the order they were, then the twos.
        val result = Arrangement.groupBy(ids(11, 21, 12, 22, 13), keyOf = tens)

        assertEquals(ids(11, 12, 13, 21, 22), result)
    }

    @Test
    fun groupsAppearWhereTheirFirstMemberWas() {
        // The twenties came first, so the twenties stay first. Deferring to the
        // existing layout is what makes this read as sliding together rather
        // than as a sort.
        val result = Arrangement.groupBy(ids(21, 11, 22, 12), keyOf = tens)

        assertEquals(ids(21, 22, 11, 12), result)
    }

    @Test
    fun aComparatorCanDecideTheGroupOrderInstead() {
        val result = Arrangement.groupBy(
            ids(21, 11, 22, 12),
            groupOrder = compareByDescending { it },
            keyOf = tens,
        )

        assertEquals(ids(21, 22, 11, 12), result)

        val ascending = Arrangement.groupBy(
            ids(21, 11, 22, 12),
            groupOrder = compareBy { it },
            keyOf = tens,
        )

        assertEquals(ids(11, 12, 21, 22), ascending)
    }

    @Test
    fun tidyingTwiceChangesNothingTheSecondTime() {
        // The property that makes a tidy button safe to press. A sort has it
        // too; what a sort does not have is the one above it.
        val start = ids(31, 12, 21, 13, 22, 11)
        val once = Arrangement.groupBy(start, keyOf = tens)
        val twice = Arrangement.groupBy(once, keyOf = tens)

        assertEquals(once, twice)
    }

    @Test
    fun tidyingIsAlwaysAPermutation() {
        val start = ids(31, 12, 21, 13, 22, 11, 12)

        listOf<Comparator<Int>?>(null, compareBy { it }, compareByDescending { it }).forEach { order ->
            val result = Arrangement.groupBy(start, groupOrder = order, keyOf = tens)
            assertEquals(
                start.sortedBy { it.value },
                result.sortedBy { it.value },
                "a tidy must not invent, lose or duplicate a card",
            )
        }
    }

    @Test
    fun cardsWithNoKeyGoLastInTheOrderTheyWereIn() {
        // Spells have no Level. Slotting them in among the Level 1s would be
        // inventing an answer rather than admitting there isn't one.
        val keyOf: (CardId) -> Int? = { if (it.value >= 20) null else it.value / 10 }
        val result = Arrangement.groupBy(ids(25, 11, 27, 12, 26), keyOf = keyOf)

        assertEquals(ids(11, 12, 25, 27, 26), result)
    }

    @Test
    fun everythingUnkeyedIsLeftExactlyAsItWas() {
        val result = Arrangement.groupBy(ids(3, 1, 2)) { null }

        assertEquals(ids(3, 1, 2), result)
    }

    @Test
    fun oneGroupIsLeftExactlyAsItWas() {
        val result = Arrangement.groupBy(ids(11, 13, 12), keyOf = tens)

        assertEquals(ids(11, 13, 12), result)
    }

    @Test
    fun emptyAndSingleAreHandled() {
        assertEquals(emptyList(), Arrangement.groupBy(emptyList(), keyOf = tens))
        assertEquals(ids(11), Arrangement.groupBy(ids(11), keyOf = tens))
    }

    @Test
    fun gatheringCopiesBringsThemToTheFirstOne() {
        // The gentlest tidy: the stray third copy snaps back beside its siblings
        // and every other card is exactly where it was left.
        val result = Arrangement.gatherCopies(ids(1, 2, 1, 3, 4, 1))

        assertEquals(ids(1, 1, 1, 2, 3, 4), result)
    }

    @Test
    fun gatheringADeckWithNoStraysDoesNothing() {
        val already = ids(1, 1, 2, 3, 3, 4)

        assertEquals(already, Arrangement.gatherCopies(already))
    }
}

class RearrangeTest {

    private fun deck(vararg values: Int) =
        Deck(main = values.map(::CardId), extra = emptyList(), side = emptyList())

    private fun ids(vararg values: Int) = values.map(::CardId)

    @Test
    fun aPermutationIsAccepted() {
        val result = DeckEditor.rearrange(deck(1, 2, 3), DeckSection.MAIN, ids(3, 1, 2))

        assertEquals(ids(3, 1, 2), (result as DeckEdit.Applied).deck.main)
    }

    @Test
    fun thePreviousOrderIsAccepted() {
        val start = deck(1, 2, 3)

        assertEquals(start, (DeckEditor.rearrange(start, DeckSection.MAIN, ids(1, 2, 3)) as DeckEdit.Applied).deck)
    }

    @Test
    fun copiesAreCountedNotJustNames() {
        // Two copies of 1 and one of 2 is not the same deck as one of 1 and two
        // of 2, and a set comparison would have said it was.
        assertTrue(
            DeckEditor.rearrange(deck(1, 1, 2), DeckSection.MAIN, ids(1, 2, 2)) is DeckEdit.Rejected,
        )
    }

    @Test
    fun losingOrInventingACardIsRejected() {
        assertTrue(DeckEditor.rearrange(deck(1, 2, 3), DeckSection.MAIN, ids(1, 2)) is DeckEdit.Rejected)
        assertTrue(DeckEditor.rearrange(deck(1, 2, 3), DeckSection.MAIN, ids(1, 2, 3, 4)) is DeckEdit.Rejected)
        assertTrue(DeckEditor.rearrange(deck(1, 2, 3), DeckSection.MAIN, ids(1, 2, 9)) is DeckEdit.Rejected)
    }

    @Test
    fun anEmptySectionIsFine() {
        val result = DeckEditor.rearrange(deck(), DeckSection.MAIN, emptyList())

        assertTrue(result is DeckEdit.Applied)
    }

    @Test
    fun otherSectionsAreUntouched() {
        val start = Deck(main = ids(1, 2), extra = ids(8), side = ids(9))
        val result = DeckEditor.rearrange(start, DeckSection.MAIN, ids(2, 1)) as DeckEdit.Applied

        assertEquals(ids(8), result.deck.extra)
        assertEquals(ids(9), result.deck.side)
    }
}

/**
 * Carrying a group by arrow key.
 *
 * The drag form of this is `reorderManyTo`, which is told where to land. This is
 * the keyboard form, which is only told "one further that way" — so the whole
 * question is what "one further" means once the group is out of the list.
 */
class CarryTest {

    private fun deck(vararg values: Int) =
        Deck(main = values.map(::CardId), extra = emptyList(), side = emptyList())

    private fun carry(deck: Deck, indices: Collection<Int>, delta: Int) =
        (DeckEditor.carry(deck, DeckSection.MAIN, indices, delta) as DeckEdit.Applied)
            .deck.main.map { it.value }

    @Test
    fun oneCardMovesOnePlace() {
        assertEquals(listOf(2, 1, 3, 4), carry(deck(1, 2, 3, 4), listOf(0), delta = 1))
        assertEquals(listOf(1, 3, 2, 4), carry(deck(1, 2, 3, 4), listOf(2), delta = -1))
    }

    @Test
    fun aGroupMovesAsOne() {
        assertEquals(listOf(3, 1, 2, 4, 5), carry(deck(1, 2, 3, 4, 5), listOf(0, 1), delta = 1))
        assertEquals(listOf(4, 5, 1, 2, 3), carry(deck(1, 2, 3, 4, 5), listOf(3, 4), delta = -3))
    }

    @Test
    fun aRowIsJustABiggerDelta() {
        // Down in a three-column grid is +3, and the card has to come out one
        // row below where it was rather than three places along a list it is no
        // longer in.
        assertEquals(listOf(2, 3, 4, 1, 5, 6, 7), carry(deck(1, 2, 3, 4, 5, 6, 7), listOf(0), delta = 3))
    }

    @Test
    fun runningOffTheEndClampsInsteadOfRefusing() {
        // The key is held down. A group already at the bottom reporting an error
        // on every repeat would be shouting about pushing it as far as it goes.
        assertEquals(listOf(1, 2, 3), carry(deck(1, 2, 3), listOf(2), delta = 5))
        assertEquals(listOf(1, 2, 3), carry(deck(1, 2, 3), listOf(0), delta = -5))
        assertEquals(listOf(2, 3, 1), carry(deck(1, 2, 3), listOf(1, 2), delta = -9))
    }

    @Test
    fun aScatteredGroupGathersAtItsFirstCard() {
        // The first card is what the group is anchored on, however far apart the
        // rest of it is.
        assertEquals(listOf(2, 1, 4, 3, 5), carry(deck(1, 2, 3, 4, 5), listOf(0, 3), delta = 1))
    }

    @Test
    fun carryingIsAlwaysAPermutation() {
        val start = deck(1, 2, 3, 4, 5, 6)

        (-4..4).forEach { delta ->
            listOf(listOf(0), listOf(2, 3), listOf(1, 4), listOf(0, 1, 2), listOf(5)).forEach { picked ->
                val moved = DeckEditor.carry(start, DeckSection.MAIN, picked, delta)
                assertTrue(moved is DeckEdit.Applied, "rejected $picked by $delta")
                assertEquals(
                    start.main.sortedBy { it.value },
                    moved.deck.main.sortedBy { it.value },
                    "picked $picked by $delta",
                )
            }
        }
    }

    @Test
    fun theLandingIndexIsWhereTheCardsActuallyEndUp() {
        // Written once and used twice, by the move and by whatever re-selects
        // the cards afterwards.
        val start = deck(1, 2, 3, 4, 5, 6)

        listOf(listOf(0), listOf(2, 3), listOf(1, 4), listOf(0, 1, 2)).forEach { picked ->
            (-3..3).forEach { delta ->
                val landed = DeckEditor.carryLanding(picked, delta, count = 6)
                val moved = (DeckEditor.carry(start, DeckSection.MAIN, picked, delta) as DeckEdit.Applied)
                    .deck.main

                val carried = picked.sorted().map { start.main[it] }
                assertEquals(
                    carried,
                    moved.subList(landed, landed + carried.size),
                    "picked $picked by $delta",
                )
            }
        }
    }

    @Test
    fun noDeltaGathersButDoesNotMove() {
        assertEquals(listOf(1, 3, 2, 4), carry(deck(1, 2, 3, 4), listOf(0, 2), delta = 0))
    }

    @Test
    fun carryingNothingIsAllowedAndDoesNothing() {
        val start = deck(1, 2, 3)
        assertEquals(
            start,
            (DeckEditor.carry(start, DeckSection.MAIN, emptyList(), 2) as DeckEdit.Applied).deck,
        )
    }

    @Test
    fun aPositionThatIsNotThereIsRejected() {
        assertTrue(DeckEditor.carry(deck(1, 2, 3), DeckSection.MAIN, listOf(7), 1) is DeckEdit.Rejected)
    }
}
