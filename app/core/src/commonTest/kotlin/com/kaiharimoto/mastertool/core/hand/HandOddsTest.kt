package com.kaiharimoto.mastertool.core.hand

import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.model.Attribute
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandOddsTest {

    private fun atLeastOne(key: String, hand: Int = 5) = HandConstraint(key, min = 1, max = hand)

    private fun p(
        sizes: Map<String, Int>,
        deck: Int,
        hand: Int,
        vararg constraints: HandConstraint,
    ) = HandOdds.probability(sizes, deck, hand, HandQuery(constraints.toList()))

    @Test
    fun binomialMatchesKnownValues() {
        assertEquals(1.0, HandOdds.binomial(10, 0))
        assertEquals(10.0, HandOdds.binomial(10, 1))
        assertEquals(120.0, HandOdds.binomial(10, 3))
        assertEquals(0.0, HandOdds.binomial(3, 5))
        // C(60,6) exactly, the largest denominator the app can produce.
        assertEquals(50_063_860.0, HandOdds.binomial(60, 6))
    }

    @Test
    fun singleGroupAtLeastOneMatchesTheExistingHypergeometric() {
        // The single-card calculator that already ships must be the special
        // case of this one — same denominator stance, same numbers.
        val stats = DeckStatistics(
            monsters = 0, spells = 0, traps = 0,
            byAttribute = emptyMap<Attribute, Int>(), byLevel = emptyMap(),
            byRace = emptyMap(), byArchetype = emptyMap(),
            unknownCards = 0, sectionSize = 40,
        )
        for (copies in 1..3) {
            for (hand in intArrayOf(5, 6)) {
                val expected = stats.openingHandOdds(copies, hand)
                val actual = p(mapOf("x" to copies), deck = 40, hand = hand, atLeastOne("x", hand))
                assertTrue(
                    abs(expected - actual) < 1e-12,
                    "copies=$copies hand=$hand: $expected vs $actual",
                )
            }
        }
    }

    @Test
    fun twoGroupJointOddsMatchAHandComputedCase() {
        // 40 cards, 12 starters, 9 handtraps. P(≥1 starter AND ≥1 handtrap)
        // in 5, by inclusion–exclusion on the complements:
        // 1 - P(no starter) - P(no handtrap) + P(neither).
        val none = { k: Int -> HandOdds.binomial(40 - k, 5) / HandOdds.binomial(40, 5) }
        val expected = 1 - none(12) - none(9) + HandOdds.binomial(40 - 21, 5) / HandOdds.binomial(40, 5)

        val actual = p(
            mapOf("starters" to 12, "traps" to 9),
            deck = 40, hand = 5,
            atLeastOne("starters"), atLeastOne("traps"),
        )
        assertTrue(abs(expected - actual) < 1e-12, "$expected vs $actual")
    }

    @Test
    fun exactlyZeroIsTheComplementOfAtLeastOne() {
        val sizes = mapOf("bricks" to 6)
        val zero = p(sizes, 40, 5, HandConstraint("bricks", 0, 0))
        val some = p(sizes, 40, 5, atLeastOne("bricks"))
        assertTrue(abs((zero + some) - 1.0) < 1e-12)
    }

    @Test
    fun twoPlusOfAGroup() {
        // P(≥2 of 6 bricks in 5 of 40) = 1 - P(0) - P(1).
        val c = HandOdds::binomial
        val total = c(40, 5)
        val expected = 1 - c(34, 5) / total - c(6, 1) * c(34, 4) / total

        val actual = p(mapOf("bricks" to 6), 40, 5, HandConstraint("bricks", 2, 5))
        assertTrue(abs(expected - actual) < 1e-12, "$expected vs $actual")
    }

    @Test
    fun degenerateInputsStayCalm() {
        // Empty deck, empty group, impossible minimum, hand bigger than deck.
        assertEquals(0.0, p(mapOf("x" to 3), deck = 0, hand = 5, atLeastOne("x")))
        assertEquals(0.0, p(mapOf("x" to 0), deck = 40, hand = 5, atLeastOne("x")))
        assertEquals(0.0, p(mapOf("x" to 2), deck = 40, hand = 5, HandConstraint("x", 3, 5)))
        // Hand ≥ deck draws everything: constraints on real counts hold.
        assertEquals(1.0, p(mapOf("x" to 3), deck = 5, hand = 9, atLeastOne("x")))
        // A constraint on a group the map does not know is a constraint on
        // nothing: min 0 passes, min 1 cannot.
        assertEquals(1.0, p(mapOf("x" to 3), 40, 5, HandConstraint("ghost", 0, 5)))
        assertEquals(0.0, p(mapOf("x" to 3), 40, 5, atLeastOne("ghost")))
    }

    @Test
    fun duplicateConstraintsIntersect() {
        val sizes = mapOf("x" to 12)
        val merged = p(
            sizes, 40, 5,
            HandConstraint("x", 1, 5),
            HandConstraint("x", 0, 2),
        )
        val direct = p(sizes, 40, 5, HandConstraint("x", 1, 2))
        assertTrue(abs(merged - direct) < 1e-12)
    }

    @Test
    fun unconstrainedQueryIsCertain() {
        assertEquals(1.0, p(emptyMap(), 40, 5))
    }
}
