package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Dropping a card at a position, rather than appending it.
 *
 * The index arithmetic here is the part a drag gets wrong: the drop position is
 * measured against the list as it looks before the card is picked up, so moving
 * a card rightwards within its own section has to account for the gap it leaves
 * behind.
 */
class DeckPlacementTest {

    private val ash = TestCards.ashBlossom
    private val maxx = TestCards.maxxC
    private val pot = TestCards.pot
    private val nibiru = TestCards.nibiru
    private val imperm = TestCards.infiniteImpermanence

    private fun main(vararg cards: Card) = Deck(main = cards.map { it.id })

    private fun applied(edit: DeckEdit): Deck {
        assertIs<DeckEdit.Applied>(edit)
        return edit.deck
    }

    private fun names(ids: List<CardId>, pool: List<Card>) =
        ids.map { id -> pool.first { it.id == id }.name }

    // ---- addAt -------------------------------------------------------------

    @Test
    fun addAtInsertsAtTheGivenPosition() {
        val deck = main(pot, nibiru, imperm)
        val result = applied(DeckEditor.addAt(deck, ash, DeckSection.MAIN, 1))

        assertEquals(listOf(pot.id, ash.id, nibiru.id, imperm.id), result.main)
    }

    @Test
    fun addAtClampsAnIndexPastTheEnd() {
        val deck = main(pot)
        val result = applied(DeckEditor.addAt(deck, ash, DeckSection.MAIN, 99))

        assertEquals(listOf(pot.id, ash.id), result.main)
    }

    @Test
    fun addAtClampsANegativeIndex() {
        val deck = main(pot)
        val result = applied(DeckEditor.addAt(deck, ash, DeckSection.MAIN, -5))

        assertEquals(listOf(ash.id, pot.id), result.main)
    }

    @Test
    fun addAtEnforcesTheSameRulesAsAdd() {
        // A card dropped between two others is checked exactly like one tapped in.
        assertIs<DeckEdit.Rejected>(
            DeckEditor.addAt(Deck.EMPTY, TestCards.accesscode, DeckSection.MAIN, 0)
        )
        assertIs<DeckEdit.Rejected>(
            DeckEditor.addAt(Deck.EMPTY, TestCards.token, DeckSection.MAIN, 0)
        )
        // Maxx "C" is Forbidden in the TCG fixture.
        assertIs<DeckEdit.Rejected>(DeckEditor.addAt(Deck.EMPTY, maxx, DeckSection.MAIN, 0))
    }

    @Test
    fun addAtRejectsAFourthCopy() {
        val deck = Deck(main = List(3) { ash.id })
        assertIs<DeckEdit.Rejected>(DeckEditor.addAt(deck, ash, DeckSection.MAIN, 0))
    }

    // ---- reorderTo ---------------------------------------------------------

    @Test
    fun reorderToMovesACardRightwards() {
        // [A,B,C,D], drag A into the gap before D. A leaves a hole behind it, so
        // landing "before D" means index 2 once the list has closed up.
        val deck = main(ash, maxx, pot, nibiru)
        val result = applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, fromIndex = 0, insertBefore = 3))

        assertEquals(
            listOf("Maxx \"C\"", "Pot of Prosperity", "Ash Blossom & Joyous Spring", "Nibiru, the Primal Being"),
            names(result.main, listOf(ash, maxx, pot, nibiru)),
        )
    }

    @Test
    fun reorderToMovesACardLeftwards() {
        // Nothing shifts when moving left, so the index is used as given.
        val deck = main(ash, maxx, pot, nibiru)
        val result = applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, fromIndex = 3, insertBefore = 1))

        assertEquals(listOf(ash.id, nibiru.id, maxx.id, pot.id), result.main)
    }

    @Test
    fun reorderToTheEndAppends() {
        val deck = main(ash, maxx, pot)
        val result = applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, fromIndex = 0, insertBefore = 3))

        assertEquals(listOf(maxx.id, pot.id, ash.id), result.main)
    }

    @Test
    fun droppingACardBackWhereItStartedChangesNothing() {
        val deck = main(ash, maxx, pot)

        // Both of these mean "stay put": the gap before yourself, and the gap
        // immediately after yourself.
        assertEquals(deck, applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, 1, 1)))
        assertEquals(deck, applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, 1, 2)))
    }

    @Test
    fun reorderToRejectsIndicesOutsideTheSection() {
        val deck = main(ash, maxx)
        assertIs<DeckEdit.Rejected>(DeckEditor.reorderTo(deck, DeckSection.MAIN, 5, 0))
        assertIs<DeckEdit.Rejected>(DeckEditor.reorderTo(deck, DeckSection.MAIN, 0, 99))
    }

    @Test
    fun reorderIsAlwaysAPermutation() {
        val deck = main(ash, maxx, pot, nibiru, imperm)
        val expected = deck.main.groupingBy { it }.eachCount()

        for (from in deck.main.indices) {
            for (to in 0..deck.main.size) {
                val result = applied(DeckEditor.reorderTo(deck, DeckSection.MAIN, from, to))
                assertEquals(
                    expected,
                    result.main.groupingBy { it }.eachCount(),
                    "contents changed moving $from -> $to",
                )
            }
        }
    }

    // ---- moveAt ------------------------------------------------------------

    @Test
    fun moveAtPlacesTheCardAtThePositionItWasDroppedOn() {
        val deck = Deck(main = listOf(ash.id), side = listOf(pot.id, nibiru.id))
        val result = applied(
            DeckEditor.moveAt(deck, ash, DeckSection.MAIN, 0, DeckSection.SIDE, 1)
        )

        assertEquals(emptyList(), result.main)
        assertEquals(listOf(pot.id, ash.id, nibiru.id), result.side)
    }

    @Test
    fun moveAtWithinOneSectionBehavesLikeAReorder() {
        val deck = main(ash, maxx, pot)
        val result = applied(
            DeckEditor.moveAt(deck, ash, DeckSection.MAIN, 0, DeckSection.MAIN, 3)
        )

        assertEquals(listOf(maxx.id, pot.id, ash.id), result.main)
    }

    @Test
    fun moveAtLeavesTheSourceAloneWhenTheDestinationRefuses() {
        // A Link monster dragged into the Main deck: the move must be all or
        // nothing, or the card is destroyed by a drop that was never legal.
        val deck = Deck(extra = listOf(TestCards.accesscode.id))
        val result = DeckEditor.moveAt(
            deck, TestCards.accesscode, DeckSection.EXTRA, 0, DeckSection.MAIN, 0,
        )

        assertIs<DeckEdit.Rejected>(result)
        assertEquals(listOf(TestCards.accesscode.id), deck.extra)
    }

    @Test
    fun moveAtRefusesWhenTheDestinationIsFull() {
        val deck = Deck(main = listOf(ash.id), side = List(15) { pot.id })
        assertIs<DeckEdit.Rejected>(
            DeckEditor.moveAt(deck, ash, DeckSection.MAIN, 0, DeckSection.SIDE, 0)
        )
    }

    @Test
    fun moveAtRejectsAPositionThatDoesNotHoldThatCard() {
        // Guards against a stale drag: the list may have changed underneath it.
        val deck = Deck(main = listOf(ash.id, maxx.id))
        assertIs<DeckEdit.Rejected>(
            DeckEditor.moveAt(deck, ash, DeckSection.MAIN, 1, DeckSection.SIDE, 0)
        )
    }

    @Test
    fun aMoveNeverChangesHowManyCopiesTheDeckHolds() {
        val deck = Deck(main = List(3) { ash.id })
        val result = applied(
            DeckEditor.moveAt(deck, ash, DeckSection.MAIN, 0, DeckSection.SIDE, 0)
        )

        // Still three copies, so the banlist cannot reject a move.
        assertEquals(3, result.copiesOf(ash.id))
        assertEquals(2, result.main.size)
        assertEquals(1, result.side.size)
    }
}
