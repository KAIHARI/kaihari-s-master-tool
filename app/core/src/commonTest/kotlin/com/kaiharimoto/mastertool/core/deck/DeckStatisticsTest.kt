package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckStatisticsTest {

    private val filler = (1..60).map { TestCards.monster(1000 + it, "Filler $it") }
    private val byId = (TestCards.all + filler).associateBy { it.id }
    private fun lookup(id: CardId): Card? = byId[id]

    /** A 40-card main deck holding [copies] of Ash Blossom, padded with fillers. */
    private fun mainDeckOf(copies: Int, size: Int = 40): Deck = Deck(
        main = List(copies) { TestCards.ashBlossom.id } +
            filler.take(size - copies).map { it.id },
    )

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(
            abs(expected - actual) < 1e-6,
            "expected $expected but was $actual",
        )
    }

    @Test
    fun countsCardTypes() {
        val deck = Deck(
            main = listOf(TestCards.ashBlossom.id, TestCards.pot.id, TestCards.infiniteImpermanence.id),
        )
        val stats = DeckStatistics.of(deck, ::lookup)

        assertEquals(1, stats.monsters)
        assertEquals(1, stats.spells)
        assertEquals(1, stats.traps)
        assertEquals(3, stats.total)
        assertEquals(3, stats.sectionSize)
    }

    // Hypergeometric reference values, 40-card deck, 5-card opening hand:
    //   1 copy  -> 1 - C(39,5)/C(40,5) = 0.125
    //   2 copies-> 1 - C(38,5)/C(40,5) = 0.2371796...
    //   3 copies-> 1 - C(37,5)/C(40,5) = 0.3375503...

    @Test
    fun openingHandOddsMatchTheHypergeometricDistribution() {
        assertClose(0.125, DeckStatistics.of(mainDeckOf(1), ::lookup).openingHandOdds(1))
        assertClose(
            1.0 - 501942.0 / 658008.0,
            DeckStatistics.of(mainDeckOf(2), ::lookup).openingHandOdds(2),
        )
        assertClose(
            1.0 - 435897.0 / 658008.0,
            DeckStatistics.of(mainDeckOf(3), ::lookup).openingHandOdds(3),
        )
    }

    @Test
    fun goingSecondDrawsSixCards() {
        // 1 - C(37,6)/C(40,6): the number that matters when you lose the roll.
        assertClose(
            1.0 - 2324784.0 / 3838380.0,
            DeckStatistics.of(mainDeckOf(3), ::lookup).openingHandOdds(3, handSize = 6),
        )
    }

    @Test
    fun aBiggerDeckLowersTheOdds() {
        val forty = DeckStatistics.of(mainDeckOf(3, size = 40), ::lookup).openingHandOdds(3)
        val sixty = DeckStatistics.of(mainDeckOf(3, size = 60), ::lookup).openingHandOdds(3)

        assertTrue(sixty < forty, "60 cards should be less consistent than 40")
    }

    @Test
    fun unknownPasscodesStillCountTowardsTheDeckSize() {
        // A passcode the database has not seen is still a card you shuffle. If it
        // were dropped from the denominator the odds would be computed for a
        // 39-card deck and read as more consistent than the deck really is.
        val deck = Deck(
            main = List(3) { TestCards.ashBlossom.id } +
                filler.take(36).map { it.id } +
                CardId(99999999),
        )
        val stats = DeckStatistics.of(deck, ::lookup)

        assertEquals(1, stats.unknownCards)
        assertEquals(39, stats.total)
        assertEquals(40, stats.sectionSize)
        assertClose(1.0 - 435897.0 / 658008.0, stats.openingHandOdds(3))
    }

    @Test
    fun oddsAreZeroWhenTheCardIsNotInTheDeck() {
        assertEquals(0.0, DeckStatistics.of(mainDeckOf(3), ::lookup).openingHandOdds(0))
    }

    @Test
    fun oddsAreZeroForAnEmptySection() {
        assertEquals(0.0, DeckStatistics.of(Deck.EMPTY, ::lookup).openingHandOdds(3))
    }

    @Test
    fun readsTheRequestedSection() {
        val deck = Deck(
            main = listOf(TestCards.ashBlossom.id),
            extra = listOf(TestCards.accesscode.id, TestCards.pendulumFusion.id),
        )
        val extra = DeckStatistics.of(deck, ::lookup, DeckSection.EXTRA)

        assertEquals(2, extra.sectionSize)
        assertEquals(2, extra.monsters)
    }
}
