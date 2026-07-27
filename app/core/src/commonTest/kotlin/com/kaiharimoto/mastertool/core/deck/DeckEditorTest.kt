package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckEditorTest {

    private fun Deck.applyAdd(card: com.kaiharimoto.mastertool.core.model.Card, section: DeckSection? = null): Deck {
        val result = DeckEditor.add(this, card, section ?: card.requiredSection())
        assertIs<DeckEdit.Applied>(result)
        return result.deck
    }

    @Test
    fun addsToTheSectionTheCardBelongsIn() {
        val deck = Deck.EMPTY.applyAdd(TestCards.nibiru)
        assertEquals(1, deck.main.size)
        assertTrue(deck.extra.isEmpty())
    }

    @Test
    fun extraDeckCardsDefaultToExtra() {
        val deck = Deck.EMPTY.applyAdd(TestCards.accesscode)
        assertEquals(1, deck.extra.size)
        assertTrue(deck.main.isEmpty())
    }

    @Test
    fun pendulumFusionCountsAsExtraDeck() {
        // Reads as a Pendulum monster but is summoned from the Extra Deck.
        assertTrue(TestCards.pendulumFusion.isExtraDeck)
        val deck = Deck.EMPTY.applyAdd(TestCards.pendulumFusion)
        assertEquals(1, deck.extra.size)
    }

    @Test
    fun rejectsExtraDeckCardInMain() {
        val result = DeckEditor.add(Deck.EMPTY, TestCards.accesscode, DeckSection.MAIN)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.WRONG_SECTION, result.reason)
    }

    @Test
    fun rejectsMainDeckCardInExtra() {
        val result = DeckEditor.add(Deck.EMPTY, TestCards.nibiru, DeckSection.EXTRA)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.WRONG_SECTION, result.reason)
    }

    @Test
    fun sideDeckAcceptsBothKinds() {
        var deck = Deck.EMPTY.applyAdd(TestCards.nibiru, DeckSection.SIDE)
        deck = deck.applyAdd(TestCards.accesscode, DeckSection.SIDE)
        assertEquals(2, deck.side.size)
    }

    @Test
    fun enforcesThreeCopyLimit() {
        var deck = Deck.EMPTY
        repeat(3) { deck = deck.applyAdd(TestCards.nibiru) }
        val fourth = DeckEditor.add(deck, TestCards.nibiru)
        assertIs<DeckEdit.Rejected>(fourth)
        assertEquals(RejectionReason.COPY_LIMIT, fourth.reason)
    }

    @Test
    fun enforcesLimitedBanStatus() {
        val deck = Deck.EMPTY.applyAdd(TestCards.ashBlossom)
        val second = DeckEditor.add(deck, TestCards.ashBlossom)
        assertIs<DeckEdit.Rejected>(second)
        assertEquals(RejectionReason.COPY_LIMIT, second.reason)
    }

    @Test
    fun forbiddenCardsCannotBeAdded() {
        val result = DeckEditor.add(Deck.EMPTY, TestCards.maxxC)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.COPY_LIMIT, result.reason)
        assertTrue(DeckEditor.isForbidden(TestCards.maxxC))
    }

    @Test
    fun copyLimitCountsAcrossMainAndSideTogether() {
        // Two in main plus one in side is three copies; a fourth anywhere is illegal.
        var deck = Deck.EMPTY
        repeat(2) { deck = deck.applyAdd(TestCards.nibiru) }
        deck = deck.applyAdd(TestCards.nibiru, DeckSection.SIDE)

        val result = DeckEditor.add(deck, TestCards.nibiru, DeckSection.SIDE)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.COPY_LIMIT, result.reason)
    }

    @Test
    fun tokensAreNotDeckCards() {
        val result = DeckEditor.add(Deck.EMPTY, TestCards.token, DeckSection.MAIN)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.NOT_PLAYABLE, result.reason)
    }

    @Test
    fun mainDeckCapsAtSixty() {
        val deck = Deck(main = List(60) { CardId(it + 1) })
        val result = DeckEditor.add(deck, TestCards.nibiru)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.SECTION_FULL, result.reason)
    }

    @Test
    fun extraAndSideCapAtFifteen() {
        val extraFull = Deck(extra = List(15) { CardId(it + 1) })
        assertIs<DeckEdit.Rejected>(DeckEditor.add(extraFull, TestCards.accesscode))

        val sideFull = Deck(side = List(15) { CardId(it + 100) })
        assertIs<DeckEdit.Rejected>(DeckEditor.add(sideFull, TestCards.nibiru, DeckSection.SIDE))
    }

    @Test
    fun removesASingleCopyNotAll() {
        var deck = Deck.EMPTY
        repeat(3) { deck = deck.applyAdd(TestCards.nibiru) }
        val result = DeckEditor.remove(deck, TestCards.nibiru.id, DeckSection.MAIN)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(2, result.deck.main.size)
    }

    @Test
    fun removingAnAbsentCardIsRejected() {
        val result = DeckEditor.remove(Deck.EMPTY, TestCards.nibiru.id, DeckSection.MAIN)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.NOT_PRESENT, result.reason)
    }

    @Test
    fun moveBetweenSectionsKeepsTotalCopies() {
        val deck = Deck.EMPTY.applyAdd(TestCards.nibiru)
        val result = DeckEditor.move(deck, TestCards.nibiru, DeckSection.MAIN, DeckSection.SIDE)
        assertIs<DeckEdit.Applied>(result)
        assertTrue(result.deck.main.isEmpty())
        assertEquals(1, result.deck.side.size)
        assertEquals(1, result.deck.copiesOf(TestCards.nibiru.id))
    }

    @Test
    fun rejectedMoveLeavesSourceUntouched() {
        // Destination is full, so the copy must stay where it was.
        val deck = Deck(
            main = listOf(TestCards.nibiru.id),
            side = List(15) { CardId(it + 200) },
        )
        val result = DeckEditor.move(deck, TestCards.nibiru, DeckSection.MAIN, DeckSection.SIDE)
        assertIs<DeckEdit.Rejected>(result)
        assertEquals(RejectionReason.SECTION_FULL, result.reason)
        assertEquals(deck, deck) // unchanged: the editor is pure and returned no new deck
    }

    @Test
    fun moveAtCopyLimitStillSucceeds() {
        // Moving does not change the number of copies, so the banlist cannot block it.
        val deck = Deck(main = listOf(TestCards.ashBlossom.id))
        val result = DeckEditor.move(deck, TestCards.ashBlossom, DeckSection.MAIN, DeckSection.SIDE)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(1, result.deck.side.size)
    }

    @Test
    fun setCountClampsToBanlist() {
        val result = DeckEditor.setCount(Deck.EMPTY, TestCards.ashBlossom, DeckSection.MAIN, count = 3)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(1, result.deck.main.size, "Ash Blossom is Limited")
    }

    @Test
    fun setCountClampsToSectionCapacity() {
        val deck = Deck(extra = List(14) { CardId(it + 300) })
        val result = DeckEditor.setCount(deck, TestCards.accesscode, DeckSection.EXTRA, count = 3)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(15, result.deck.extra.size)
    }

    @Test
    fun setCountToZeroRemovesEveryCopy() {
        var deck = Deck.EMPTY
        repeat(3) { deck = deck.applyAdd(TestCards.nibiru) }
        val result = DeckEditor.setCount(deck, TestCards.nibiru, DeckSection.MAIN, count = 0)
        assertIs<DeckEdit.Applied>(result)
        assertTrue(result.deck.main.isEmpty())
    }

    @Test
    fun setCountAccountsForCopiesInOtherSections() {
        val deck = Deck(side = listOf(TestCards.nibiru.id, TestCards.nibiru.id))
        val result = DeckEditor.setCount(deck, TestCards.nibiru, DeckSection.MAIN, count = 3)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(1, result.deck.main.size, "two copies already sit in the side deck")
        assertEquals(3, result.deck.copiesOf(TestCards.nibiru.id))
    }

    @Test
    fun reorderMovesWithinSection() {
        val deck = Deck(main = listOf(CardId(1), CardId(2), CardId(3)))
        val result = DeckEditor.reorder(deck, DeckSection.MAIN, fromIndex = 0, toIndex = 2)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(listOf(CardId(2), CardId(3), CardId(1)), result.deck.main)
    }

    @Test
    fun removeAllClearsEverySection() {
        val deck = Deck(
            main = listOf(TestCards.nibiru.id, CardId(999)),
            side = listOf(TestCards.nibiru.id),
        )
        val result = DeckEditor.removeAll(deck, TestCards.nibiru.id)
        assertIs<DeckEdit.Applied>(result)
        assertEquals(0, result.deck.copiesOf(TestCards.nibiru.id))
        assertEquals(1, result.deck.main.size)
    }

    @Test
    fun remainingCopiesDrivesTheBadge() {
        assertEquals(3, DeckEditor.remainingCopies(Deck.EMPTY, TestCards.nibiru))
        assertEquals(1, DeckEditor.remainingCopies(Deck.EMPTY, TestCards.ashBlossom))
        assertEquals(0, DeckEditor.remainingCopies(Deck.EMPTY, TestCards.maxxC))
        val deck = Deck(main = List(2) { TestCards.nibiru.id })
        assertEquals(1, DeckEditor.remainingCopies(deck, TestCards.nibiru))
    }

    @Test
    fun sideDeckAcceptanceIsNotConfusedByExtraDeckFlag() {
        assertTrue(DeckEditor.sectionAccepts(TestCards.accesscode, DeckSection.SIDE))
        assertTrue(DeckEditor.sectionAccepts(TestCards.nibiru, DeckSection.SIDE))
        assertFalse(DeckEditor.sectionAccepts(TestCards.accesscode, DeckSection.MAIN))
        assertFalse(DeckEditor.sectionAccepts(TestCards.nibiru, DeckSection.EXTRA))
    }
}
