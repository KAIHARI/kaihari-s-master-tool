package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/** A hand off the top of a shuffled deck, and what is left under it. */
data class OpeningHand(
    val cards: List<CardId>,
    val remaining: List<CardId>,
    /** Five going first, six going second. */
    val goingFirst: Boolean,
) {
    val size: Int get() = cards.size
}

/**
 * How often a deck opened well.
 *
 * Not a win rate and not a simulation — a count of hands somebody looked at and
 * judged. That is the honest measure available here, and it is also the one that
 * changes a decklist: nobody adjusts ratios because of a number a program
 * produced, they adjust them because they kept seeing the same dead hand.
 */
data class HandTally(val playable: Int = 0, val bricks: Int = 0) {
    val total: Int get() = playable + bricks

    /** Share of hands judged unplayable, or null before anything has been judged. */
    val brickRate: Double? get() = if (total == 0) null else bricks.toDouble() / total

    fun judged(playable: Boolean): HandTally =
        if (playable) copy(playable = this.playable + 1) else copy(bricks = this.bricks + 1)
}

/**
 * Deals opening hands from a decklist.
 *
 * Deck building is mostly a question about ratios, and the fastest way to answer
 * it has always been to shuffle up and look. Opening-hand odds say what the
 * numbers are; this says what they feel like, which is the thing that actually
 * changes a list.
 *
 * The shuffle takes a [Random] rather than reaching for a global one, so the
 * same seed deals the same hand and every one of these is testable.
 */
object HandSimulator {

    const val GOING_FIRST = 5
    const val GOING_SECOND = 6

    fun deal(deck: List<CardId>, goingFirst: Boolean, random: Random): OpeningHand {
        val size = if (goingFirst) GOING_FIRST else GOING_SECOND
        val shuffled = deck.shuffled(random)

        // A deck shorter than a hand deals what it has rather than throwing:
        // decks are edited while this is open, and a half-built one is exactly
        // when somebody is asking this question.
        val drawn = shuffled.take(size)
        return OpeningHand(
            cards = drawn,
            remaining = shuffled.drop(drawn.size),
            goingFirst = goingFirst,
        )
    }

    /**
     * Draws one more card from what is left, for playing a hand out by hand.
     *
     * Returns the same hand when the deck is empty, because running out is a
     * real answer to "what happens if this goes long" and not an error.
     */
    fun drawOne(hand: OpeningHand): OpeningHand {
        val next = hand.remaining.firstOrNull() ?: return hand
        return hand.copy(
            cards = hand.cards + next,
            remaining = hand.remaining.drop(1),
        )
    }
}
