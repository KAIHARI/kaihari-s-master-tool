package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.search.CardIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckValidatorTest {

    private val index = CardIndex.build(TestCards.all)
    private val lookup = { id: CardId -> index.byId(id) }

    /** A legal 40-card main deck built from filler cards plus the given extras. */
    private fun legalDeck(
        main: List<CardId> = emptyList(),
        extra: List<CardId> = emptyList(),
        side: List<CardId> = emptyList(),
    ): Pair<Deck, CardIndex> {
        val filler = (1..40).map { TestCards.monster(900_000 + it, "Filler $it") }
        val pool = CardIndex.build(TestCards.all + filler)
        val fillerIds = filler.map { it.id }.take(40 - main.size)
        return Deck(main = main + fillerIds, extra = extra, side = side) to pool
    }

    @Test
    fun acceptsALegalDeck() {
        val (deck, pool) = legalDeck()
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(result.isLegal, result.errors.joinToString { it.message })
    }

    @Test
    fun rejectsMainDeckUnderForty() {
        val deck = Deck(main = List(39) { CardId(900_000 + it) })
        val result = DeckValidator.validate(deck, { TestCards.monster(it.value, "x") })
        assertFalse(result.isLegal)
        assertTrue(result.errors.any { it.section == DeckSection.MAIN })
    }

    @Test
    fun rejectsMainDeckOverSixty() {
        val deck = Deck(main = List(61) { CardId(900_000 + it) })
        val result = DeckValidator.validate(deck, { TestCards.monster(it.value, "x") })
        assertFalse(result.isLegal)
    }

    @Test
    fun rejectsOversizedExtraAndSide() {
        val (deck, pool) = legalDeck(extra = List(16) { TestCards.accesscode.id })
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(result.errors.any { it.section == DeckSection.EXTRA })
    }

    @Test
    fun reportsForbiddenCards() {
        val (deck, pool) = legalDeck(main = listOf(TestCards.maxxC.id))
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(result.errors.any { it.message.contains("Forbidden") })
    }

    @Test
    fun reportsExceedingALimitedCard() {
        val (deck, pool) = legalDeck(main = List(2) { TestCards.ashBlossom.id })
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(result.errors.any { it.message.contains("limited to 1") })
    }

    @Test
    fun countsCopiesAcrossSectionsTogether() {
        // Three in main plus one in side is four copies and must be reported.
        val (deck, pool) = legalDeck(
            main = List(3) { TestCards.nibiru.id },
            side = listOf(TestCards.nibiru.id),
        )
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(result.errors.any { it.cardId == TestCards.nibiru.id })
    }

    @Test
    fun reportsUnknownPasscodes() {
        val deck = Deck(main = List(40) { CardId(424242) })
        val result = DeckValidator.validate(deck, lookup)
        assertTrue(result.errors.any { it.message.contains("not in the card database") })
    }

    @Test
    fun reportsCardSittingInTheWrongSection() {
        val (deck, pool) = legalDeck(main = listOf(TestCards.accesscode.id))
        val result = DeckValidator.validate(deck, { pool.byId(it) })
        assertTrue(
            result.errors.any { it.message.contains("cannot be in the Main Deck") },
            result.errors.joinToString { it.message },
        )
    }

    @Test
    fun warnsAboutDeckSizeAboveForty() {
        val deck = Deck(main = List(45) { CardId(900_000 + it) })
        val result = DeckValidator.validate(deck, { TestCards.monster(it.value, "x") })
        assertTrue(result.warnings.any { it.message.contains("maximises consistency") })
        assertTrue(result.isLegal, "45 cards is legal, only worth flagging")
    }

    @Test
    fun emptyDeckIsIllegalButProducesOneClearError() {
        val result = DeckValidator.validate(Deck.EMPTY, lookup)
        assertFalse(result.isLegal)
        assertEquals(1, result.errors.size)
    }
}
