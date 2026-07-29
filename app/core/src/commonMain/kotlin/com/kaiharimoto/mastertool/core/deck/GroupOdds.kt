package com.kaiharimoto.mastertool.core.deck

/**
 * How often a hand contains some number of a particular set of cards.
 *
 * The hypergeometric distribution, which is the only piece of mathematics a deck
 * builder genuinely needs: draws without replacement, out of a deck of known
 * size, where a card either is or is not one of the ones being counted.
 *
 * Kept separate from [DeckStatistics.openingHandOdds] rather than folded into
 * it. That one answers "will I see this card", which is a question about one
 * card and its copies; this one answers "will I see one of *these*", which is a
 * question about a set and is the question people actually build decks around.
 */
object Hypergeometric {

    /**
     * Probability of drawing exactly [wanted] of the [successes] cards.
     *
     * Returns zero rather than throwing for a question with no answer — asking
     * for three of a two-card group is a thing a caller does while a deck is
     * being edited, not a mistake worth stopping for.
     */
    fun exactly(deckSize: Int, successes: Int, handSize: Int, wanted: Int): Double {
        if (deckSize <= 0 || handSize <= 0 || wanted < 0) return 0.0
        if (successes < 0 || successes > deckSize) return 0.0
        if (wanted > successes || wanted > handSize) return 0.0

        // A hand cannot be bigger than the deck it comes off, and a deck being
        // built is under forty cards for most of its life.
        val hand = handSize.coerceAtMost(deckSize)
        // Not enough other cards to make up the rest of the hand, so a hand with
        // exactly this many of them cannot be dealt.
        if (hand - wanted > deckSize - successes) return 0.0

        return choose(successes, wanted) *
            choose(deckSize - successes, hand - wanted) /
            choose(deckSize, hand)
    }

    /** Probability of drawing [wanted] or more of them. */
    fun atLeast(deckSize: Int, successes: Int, handSize: Int, wanted: Int): Double {
        if (wanted <= 0) return 1.0
        // Summed from the top rather than as 1 - P(fewer), so the small
        // probabilities that matter here are not the difference of two numbers
        // close to one.
        var total = 0.0
        for (k in wanted..minOf(successes, handSize)) {
            total += exactly(deckSize, successes, handSize, k)
        }
        return total.coerceIn(0.0, 1.0)
    }

    /**
     * How many of them a hand holds on average.
     *
     * Worth having next to the probabilities because it answers the question
     * they do not: two groups can both be near-certain to show up and still be
     * very different amounts of the deck.
     */
    fun expected(deckSize: Int, successes: Int, handSize: Int): Double {
        if (deckSize <= 0) return 0.0
        return handSize.coerceAtMost(deckSize).toDouble() * successes / deckSize
    }

    /**
     * n choose k, as a Double.
     *
     * Multiplied and divided in step rather than as three factorials: a deck is
     * at most sixty cards and a hand at most six, so nothing here comes close to
     * overflowing — but 60! does, immediately, and the version that computes it
     * is the version that silently returns infinity.
     */
    internal fun choose(n: Int, k: Int): Double {
        if (k < 0 || k > n || n < 0) return 0.0
        val smaller = minOf(k, n - k)
        var result = 1.0
        for (i in 1..smaller) {
            result = result * (n - smaller + i) / i
        }
        return result
    }
}

/**
 * What one group of a deck does to an opening hand.
 *
 * [size] cards out of [deckSize], and the three numbers worth reading: how often
 * a hand has none of them, at least one, and at least two. Two is not an
 * arbitrary third number — it is the one that distinguishes a group you can rely
 * on from one you merely usually see, and the difference between those is
 * most of what deck building is.
 */
data class GroupOdds(
    val size: Int,
    val deckSize: Int,
    val handSize: Int,
) {
    val none: Double get() = Hypergeometric.exactly(deckSize, size, handSize, 0)
    val one: Double get() = Hypergeometric.atLeast(deckSize, size, handSize, 1)
    val two: Double get() = Hypergeometric.atLeast(deckSize, size, handSize, 2)
    val average: Double get() = Hypergeometric.expected(deckSize, size, handSize)

    companion object {
        /**
         * The odds for every group a section has been divided into.
         *
         * The groups are the ones already on screen — the gaps somebody pushed
         * into their own deck — so this asks nothing of them that they have not
         * already said. That is the whole idea: an arrangement made to be looked
         * at turns out to be an arrangement that can be measured.
         */
        fun forGroups(
            breaks: Breaks,
            sectionSize: Int,
            handSize: Int = DEFAULT_HAND,
        ): List<GroupOdds> =
            breaks.groups(sectionSize).map {
                GroupOdds(size = it.count(), deckSize = sectionSize, handSize = handSize)
            }

        /** Five, which is what you draw going first, and the harder half. */
        const val DEFAULT_HAND = 5
    }
}
