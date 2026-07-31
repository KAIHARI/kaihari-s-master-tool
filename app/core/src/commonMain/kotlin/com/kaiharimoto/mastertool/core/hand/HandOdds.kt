package com.kaiharimoto.mastertool.core.hand

/**
 * A requirement on one group in the opening hand: between [min] and [max]
 * copies of it, inclusive. "At least one starter" is `min=1, max=hand`;
 * "no bricks" is `min=0, max=0`.
 */
data class HandConstraint(
    val groupKey: String,
    val min: Int,
    val max: Int,
)

/** All constraints must hold at once — the AND is what makes it a question. */
data class HandQuery(val constraints: List<HandConstraint>)

/**
 * Exact opening-hand odds over the deck's functional groups.
 *
 * This is the analytical payoff of drawing groups at all: once cards have
 * roles, "will this deck function" becomes a computable question — the chance
 * of opening at least one starter AND at least one handtrap, of opening two or
 * more bricks, of seeing the one-of.
 *
 * Deliberately exact, not simulated: the numbers are tiny. A deck is at most
 * sixty cards, a hand at most six, and the enumeration below visits the
 * compositions of the hand across the constrained groups — a few hundred
 * terms at worst. Multivariate hypergeometric, computed with multiplicative
 * binomials in `Double`; C(60,6) is ~5×10⁷, nowhere near precision trouble.
 *
 * The denominator uses the full section size, unresolved passcodes included —
 * an unknown card is still a card you shuffle. Same stance as
 * `DeckStatistics.openingHandOdds`, which remains the single-card special
 * case of this and is what the tests cross-check against.
 */
object HandOdds {

    /**
     * Probability that a [handSize]-card hand from a [deckSize]-card deck
     * satisfies every constraint in [query].
     *
     * [groupSizes] maps each constrained group's key to how many cards of the
     * deck belong to it. Cards in no constrained group are pooled as "the
     * rest" automatically; a constraint whose key is missing from the map is
     * treated as a constraint on an empty group.
     */
    fun probability(
        groupSizes: Map<String, Int>,
        deckSize: Int,
        handSize: Int,
        query: HandQuery,
    ): Double {
        if (deckSize <= 0) return 0.0
        val hand = handSize.coerceIn(0, deckSize)
        if (hand == 0) {
            return if (query.constraints.all { it.min <= 0 }) 1.0 else 0.0
        }

        // Merge duplicate constraints on the same group by intersection, so a
        // caller that says "≥1" and "≤2" separately means 1..2.
        val merged = query.constraints
            .groupBy { it.groupKey }
            .map { (key, list) ->
                HandConstraint(
                    groupKey = key,
                    min = list.maxOf { it.min }.coerceAtLeast(0),
                    max = list.minOf { it.max },
                )
            }

        val sizes = merged.map { (groupSizes[it.groupKey] ?: 0).coerceAtLeast(0) }
        val constrainedTotal = sizes.sum()
        if (constrainedTotal > deckSize) return 0.0
        val rest = deckSize - constrainedTotal

        val denominator = binomial(deckSize, hand)
        if (denominator <= 0.0) return 0.0

        var favourable = 0.0

        // Depth-first over how many of each constrained group the hand holds.
        fun visit(index: Int, drawn: Int, product: Double) {
            if (product == 0.0) return
            if (index == merged.size) {
                val fromRest = hand - drawn
                if (fromRest < 0 || fromRest > rest) return
                favourable += product * binomial(rest, fromRest)
                return
            }
            val constraint = merged[index]
            val size = sizes[index]
            val low = constraint.min.coerceAtLeast(0)
            val high = minOf(constraint.max, size, hand - drawn)
            for (k in low..high) {
                visit(index + 1, drawn + k, product * binomial(size, k))
            }
        }
        visit(0, 0, 1.0)

        return (favourable / denominator).coerceIn(0.0, 1.0)
    }

    /** C(n, k) as a Double, multiplicative form — exact well past deck sizes. */
    fun binomial(n: Int, k: Int): Double {
        if (k < 0 || k > n) return 0.0
        val kk = minOf(k, n - k)
        var result = 1.0
        for (i in 1..kk) {
            result = result * (n - kk + i) / i
        }
        return result
    }
}
