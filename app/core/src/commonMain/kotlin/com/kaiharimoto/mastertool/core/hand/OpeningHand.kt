package com.kaiharimoto.mastertool.core.hand

import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/**
 * One goldfish session: the main deck shuffled, hands dealt, mulligans taken.
 *
 * Pure and seeded so the UI can replay any open exactly — the same seed deals
 * the same cards, which is what makes "show me that hand again" and the tests
 * possible at all. The state machine is deliberately tiny: shuffle, draw,
 * mulligan (sweep the hand back, reshuffle what remains unseen plus the swept
 * cards, draw again), and draw-one for turns beyond the opener.
 */
data class OpeningHand(
    val seed: Long,
    /** The shuffled order; index 0 is the top of the deck. */
    val library: List<CardId>,
    /** Cards drawn, in draw order. */
    val hand: List<CardId>,
    /** How many cards the library has given out. */
    val drawn: Int,
    val mulligans: Int,
) {
    val remaining: List<CardId> get() = library.drop(drawn)

    companion object {
        /** Fisher–Yates over [deck] with [seed]; deals [handSize] immediately. */
        fun deal(deck: List<CardId>, seed: Long, handSize: Int): OpeningHand {
            val shuffled = deck.toMutableList()
            val random = Random(seed)
            for (i in shuffled.indices.reversed()) {
                val j = random.nextInt(i + 1)
                val tmp = shuffled[i]
                shuffled[i] = shuffled[j]
                shuffled[j] = tmp
            }
            val take = handSize.coerceIn(0, shuffled.size)
            return OpeningHand(
                seed = seed,
                library = shuffled,
                hand = shuffled.take(take),
                drawn = take,
                mulligans = 0,
            )
        }
    }

    /** One more card off the top, for playing past the opener. */
    fun drawOne(): OpeningHand {
        if (drawn >= library.size) return this
        return copy(hand = hand + library[drawn], drawn = drawn + 1)
    }

    /**
     * Sweep the hand back and deal a fresh one from a reshuffle of everything
     * not yet seen plus the swept cards. Derives its randomness from the seed
     * and the mulligan count, so the whole session stays replayable from
     * `(deck, seed)` alone.
     */
    fun mulligan(handSize: Int): OpeningHand {
        val pool = hand + remaining
        val next = deal(pool, seed = seed + mulligans + 1, handSize = handSize)
        return next.copy(mulligans = mulligans + 1)
    }

    /** Copies of each group in the current hand — the readout chip's numbers. */
    fun tally(groups: DeckGroups): Map<String?, Int> =
        hand.groupingBy { groups.assignments[it] }.eachCount()
}
