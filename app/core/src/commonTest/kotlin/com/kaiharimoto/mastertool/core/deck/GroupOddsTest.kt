package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HypergeometricTest {

    @Test
    fun everyOutcomeAddsUpToOne() {
        // The property that catches almost any arithmetic slip at once.
        listOf(20, 40, 60).forEach { deck ->
            listOf(1, 3, 9, 12).forEach { group ->
                val total = (0..5).sumOf { Hypergeometric.exactly(deck, group, 5, it) }
                assertEquals(1.0, total, 1e-9, "$group of $deck")
            }
        }
    }

    @Test
    fun aThreeOfInFortyOpensAboutAThirdOfTheTime() {
        // The number every player already knows, which is what makes it the
        // right one to pin: three copies, forty cards, five on the draw.
        assertEquals(0.3376, Hypergeometric.atLeast(40, 3, 5, 1), 0.0001)
    }

    @Test
    fun twelveStartersInFortyOpenOneNearlyAlways() {
        assertEquals(0.8506, Hypergeometric.atLeast(40, 12, 5, 1), 0.0001)
        assertEquals(0.4772, Hypergeometric.atLeast(40, 12, 5, 2), 0.0001)
    }

    @Test
    fun theSixthCardIsWorthMoreThanItLooks() {
        // Going second draws one more, and the difference is the reason people
        // argue about the die roll.
        assertTrue(Hypergeometric.atLeast(40, 3, 6, 1) > Hypergeometric.atLeast(40, 3, 5, 1))
        assertEquals(0.3943, Hypergeometric.atLeast(40, 3, 6, 1), 0.0001)
    }

    @Test
    fun itAgreesWithTheOddsTheStatisticsPanelAlreadyComputed() {
        // Two implementations derived independently -- that one multiplies out
        // 1 - P(none) term by term, this one sums combinations -- so agreement
        // between them is worth more than either being checked against itself.
        val stats = DeckStatistics(
            monsters = 40,
            spells = 0,
            traps = 0,
            byAttribute = emptyMap(),
            byLevel = emptyMap(),
            byRace = emptyMap(),
            byArchetype = emptyMap(),
            unknownCards = 0,
            sectionSize = 40,
        )

        (1..12).forEach { copies ->
            listOf(5, 6).forEach { hand ->
                assertEquals(
                    stats.openingHandOdds(copies, hand),
                    Hypergeometric.atLeast(40, copies, hand, 1),
                    1e-9,
                    "$copies copies, $hand cards",
                )
            }
        }
    }

    @Test
    fun aGroupThatIsTheWholeDeckIsCertain() {
        assertEquals(1.0, Hypergeometric.atLeast(40, 40, 5, 1), 1e-12)
        assertEquals(1.0, Hypergeometric.exactly(40, 40, 5, 5), 1e-12)
        assertEquals(0.0, Hypergeometric.exactly(40, 40, 5, 4), 1e-12)
    }

    @Test
    fun anEmptyGroupNeverShowsUp() {
        assertEquals(0.0, Hypergeometric.atLeast(40, 0, 5, 1))
        assertEquals(1.0, Hypergeometric.exactly(40, 0, 5, 0))
    }

    @Test
    fun askingForMoreThanExistsIsImpossibleRatherThanAnError() {
        // Reachable while a deck is being edited: a group shrinks under a panel
        // that is still asking about two of it.
        assertEquals(0.0, Hypergeometric.atLeast(40, 1, 5, 2))
        assertEquals(0.0, Hypergeometric.exactly(40, 3, 5, 4))
        assertEquals(0.0, Hypergeometric.atLeast(40, 3, 5, 9))
    }

    @Test
    fun nonsenseIsZeroRatherThanNaN() {
        assertEquals(0.0, Hypergeometric.exactly(0, 0, 5, 0))
        assertEquals(0.0, Hypergeometric.exactly(40, -1, 5, 0))
        assertEquals(0.0, Hypergeometric.exactly(40, 50, 5, 1))
        assertEquals(0.0, Hypergeometric.exactly(40, 3, -1, 1))
    }

    @Test
    fun aHandBiggerThanTheDeckIsTheWholeDeck() {
        // A deck part-way through being built, opened in the test-hand panel.
        assertEquals(1.0, Hypergeometric.atLeast(3, 1, 5, 1), 1e-12)
        assertEquals(3.0, Hypergeometric.expected(3, 3, 5), 1e-12)
    }

    @Test
    fun moreCopiesIsNeverWorse() {
        var previous = 0.0
        (0..40).forEach { copies ->
            val odds = Hypergeometric.atLeast(40, copies, 5, 1)
            assertTrue(odds >= previous - 1e-12, "$copies copies read worse than ${copies - 1}")
            previous = odds
        }
    }

    @Test
    fun theAverageIsTheShareOfTheDeckTimesTheHand() {
        assertEquals(1.5, Hypergeometric.expected(40, 12, 5), 1e-12)
        assertEquals(0.0, Hypergeometric.expected(40, 0, 5))
    }

    @Test
    fun theBinomialCoefficientIsExactAtDeckSizes() {
        // Computed by multiplying and dividing in step, which is exact for these
        // and is the reason it is not three factorials.
        assertEquals(1.0, Hypergeometric.choose(60, 0))
        assertEquals(60.0, Hypergeometric.choose(60, 1))
        assertEquals(1770.0, Hypergeometric.choose(60, 2))
        assertEquals(50063860.0, Hypergeometric.choose(60, 6))
        assertEquals(0.0, Hypergeometric.choose(5, 6))
    }
}

class GroupOddsTest {

    @Test
    fun aDeckWithNoGapsIsOneGroupOfEverything() {
        val odds = GroupOdds.forGroups(Breaks.NONE, sectionSize = 40)

        assertEquals(1, odds.size)
        assertEquals(40, odds.first().size)
        assertEquals(1.0, odds.first().one, 1e-12)
    }

    @Test
    fun theGapsSomebodyDrewAreTheGroupsThatGetMeasured() {
        // The whole idea: an arrangement made to be looked at turns out to be an
        // arrangement that can be measured, and nothing new was asked of anyone.
        val odds = GroupOdds.forGroups(Breaks(setOf(9, 15)), sectionSize = 40)

        assertEquals(listOf(9, 6, 25), odds.map { it.size })
        assertTrue(odds.all { it.deckSize == 40 })
    }

    @Test
    fun theNumbersForOneGroupAgreeWithEachOther() {
        val group = GroupOdds(size = 12, deckSize = 40, handSize = 5)

        assertEquals(1.0, group.none + group.one, 1e-12, "either you see one or you do not")
        assertTrue(group.two < group.one)
        assertEquals(1.5, group.average, 1e-12)
    }

    @Test
    fun anEmptySectionHasNothingToMeasure() {
        assertEquals(emptyList(), GroupOdds.forGroups(Breaks(setOf(3)), sectionSize = 0))
    }

    @Test
    fun gapsPastTheEndOfTheSectionAreIgnoredHereToo() {
        // Breaks are clamped on the way out rather than guarded on the way in,
        // so this inherits that -- but it is worth saying, because a group of no
        // cards would read as a nought per cent line in a panel.
        val odds = GroupOdds.forGroups(Breaks(setOf(3, 90)), sectionSize = 10)

        assertEquals(listOf(3, 7), odds.map { it.size })
    }
}

/**
 * Two cards, one hand.
 *
 * The question a deck is actually built around — a deck is a handful of two-card
 * openings and thirty-odd cards that make them likelier — and until now the only
 * odds this program could give were about one card or one pile.
 */
class BothAppearTest {

    @Test
    fun threeAndThreeOutOfFortyOnFive() {
        // (C(40,5) - 2*C(37,5) + C(34,5)) / C(40,5), which is 64470/658008.
        // Worth reading twice: three of each in forty, and you open the pair one
        // game in ten. That number is why decks run more than one of them.
        val odds = Hypergeometric.bothAppear(deckSize = 40, first = 3, second = 3, handSize = 5)

        assertEquals(0.0980, odds, 0.0005)
    }

    @Test
    fun itIsNotTheProductOfTheTwoSeparateAnswers() {
        // The cards come out of the same deck, so the two are not independent.
        // Multiplying is close enough to look right, which is the danger.
        val together = Hypergeometric.bothAppear(40, 3, 3, 5)
        val apart = Hypergeometric.atLeast(40, 3, 5, 1) * Hypergeometric.atLeast(40, 3, 5, 1)

        assertTrue(together < apart, "drawing one uses up a slot the other needed")
        assertTrue(together > apart * 0.8, "and not by much, which is why it looks right")
    }

    @Test
    fun aCardThatIsNotInTheDeckIsNeverOpened() {
        assertEquals(0.0, Hypergeometric.bothAppear(40, 0, 3, 5))
        assertEquals(0.0, Hypergeometric.bothAppear(40, 3, 0, 5))
    }

    @Test
    fun aHandThatIsTheWholeDeckHoldsEverything() {
        assertEquals(1.0, Hypergeometric.bothAppear(40, 3, 3, 40), 1e-9)
    }

    @Test
    fun aDeckMadeOnlyOfTheTwoOfThemStillMissesSometimes() {
        // Twenty and twenty, and a five-card hand is still all one half about
        // one game in twenty-one. I expected certainty here and was wrong, which
        // is the same shape of wrong the test above exists to catch.
        assertEquals(0.9529, Hypergeometric.bothAppear(40, 20, 20, 5), 0.0005)
    }

    @Test
    fun nonsenseIsZeroRatherThanAThrow() {
        // Reached while a deck is being edited, not by a mistake worth stopping
        // for -- the same rule the rest of this object follows.
        assertEquals(0.0, Hypergeometric.bothAppear(0, 3, 3, 5))
        assertEquals(0.0, Hypergeometric.bothAppear(40, 3, 3, 0))
        assertEquals(0.0, Hypergeometric.bothAppear(4, 3, 3, 5), "more copies than deck")
    }

    @Test
    fun moreCopiesIsNeverWorse() {
        var last = 0.0
        for (copies in 1..3) {
            val odds = Hypergeometric.bothAppear(40, copies, 3, 5)
            assertTrue(odds > last, "$copies copies should beat ${copies - 1}")
            last = odds
        }
    }
}
