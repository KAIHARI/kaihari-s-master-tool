package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.random.Random

/**
 * Everything on the table: what is on the board, what is in hand, what is left.
 *
 * One value rather than three, because every interesting operation moves a card
 * between them and none of them is meaningful on its own. Playing a card is
 * `hand → board`; drawing is `library → hand`; and a snapshot of all three is
 * what undo has to restore, which is exactly what makes them one thing.
 *
 * Nothing here knows a rule of the game. It knows a card cannot be in two places
 * at once and that a monster zone holds one monster; a sandbox that enforced
 * summoning restrictions would be a sandbox nobody could use to check the thing
 * they were actually unsure about.
 */
data class TableState(
    val board: Board = Board.EMPTY,
    val hand: List<CardId> = emptyList(),
    /** Face-down, in order. The top of the deck is the last element. */
    val library: List<CardId> = emptyList(),
    val extra: List<CardId> = emptyList(),
) {
    val isEmpty: Boolean get() = board.isEmpty && hand.isEmpty()

    /**
     * Draws from the top, and stops when the deck runs out.
     *
     * Decking out is a real thing that happens and not an error — the sandbox
     * says so by there being nothing left to draw rather than by refusing.
     */
    fun draw(count: Int = 1): TableState {
        val taken = minOf(count, library.size)
        if (taken == 0) return this
        return copy(
            hand = hand + library.takeLast(taken).reversed(),
            library = library.dropLast(taken),
        )
    }

    /**
     * Puts a card from the hand into a zone, or returns null if it will not go.
     *
     * Null covers both ways that happens — an index that is not in the hand and
     * a zone that is already full — because the caller does the same thing about
     * either: nothing, and the card stays where it was.
     */
    fun play(handIndex: Int, zone: ZoneId, placement: Placement): TableState? {
        val card = hand.getOrNull(handIndex) ?: return null
        val placed = board.place(zone, PlacedCard(card, placement)) ?: return null
        return copy(board = placed, hand = hand.withoutIndex(handIndex))
    }

    /** From the hand straight to a pile, for a discard or a banish cost. */
    fun sendFromHand(handIndex: Int, zone: ZoneId): TableState? {
        val card = hand.getOrNull(handIndex) ?: return null
        val placed = board.place(zone, PlacedCard(card)) ?: return null
        return copy(board = placed, hand = hand.withoutIndex(handIndex))
    }

    /**
     * Out of the Extra deck and onto the board.
     *
     * The other half of the sandbox, and the half a modern board is made of: a
     * turn that ends in five monsters ends in five monsters that were never in
     * the hand. Without this the board can only ever show an opening, not what
     * the opening builds.
     *
     * The Extra deck is a set, not a stack — you summon the one you want — so it
     * is taken by index and never by "the top".
     */
    fun summon(extraIndex: Int, zone: ZoneId, placement: Placement): TableState? {
        val card = extra.getOrNull(extraIndex) ?: return null
        val placed = board.place(zone, PlacedCard(card, placement)) ?: return null
        return copy(board = placed, extra = extra.withoutIndex(extraIndex))
    }

    /**
     * Out of the deck and into the hand: what a searcher does.
     *
     * By index rather than by name, because the caller is looking at the list.
     * The rest of the deck keeps its order — a search does not shuffle, and
     * pretending otherwise would quietly ruin anything set up on top.
     */
    fun searchLibrary(index: Int): TableState? {
        val card = library.getOrNull(index) ?: return null
        return copy(hand = hand + card, library = library.withoutIndex(index))
    }

    /**
     * Puts a token in a zone.
     *
     * It comes from nowhere, which is exactly right — nothing leaves the hand,
     * the deck or the Extra deck, because a token was never in any of them.
     */
    fun makeToken(zone: ZoneId, placement: Placement = Placement.ATTACK): TableState? {
        val placed = board.place(zone, PlacedCard.token(placement)) ?: return null
        return copy(board = placed)
    }

    /** Out of a pile and back into the hand, one card from anywhere in it. */
    fun retrieve(from: ZoneId, index: Int): TableState? {
        val card = board[from].getOrNull(index) ?: return null
        val lifted = board.removeAt(from, index) ?: return null
        return copy(board = lifted, hand = hand + card.id)
    }

    /** Out of a pile and back onto the board: a revival, which decks do constantly. */
    fun raise(from: ZoneId, index: Int, to: ZoneId, placement: Placement): TableState? {
        if (from == to) return null
        val card = board[from].getOrNull(index) ?: return null
        val lifted = board.removeAt(from, index) ?: return null
        val placed = lifted.place(to, PlacedCard(card.id, placement)) ?: return null
        return copy(board = placed)
    }

    /**
     * Back off the board and into the hand, for a bounce or a misplacement.
     *
     * A token bounced to the hand stops existing instead, for the same reason it
     * cannot go to a graveyard: there is no card to hold.
     */
    fun toHand(zone: ZoneId): TableState? {
        val card = board[zone].lastOrNull() ?: return null
        val lifted = board.lift(zone)
        if (card.token) return copy(board = lifted)
        return copy(board = lifted, hand = hand + card.id)
    }

    private fun List<CardId>.withoutIndex(index: Int): List<CardId> =
        take(index) + drop(index + 1)

    companion object {
        const val OPENING_HAND = 5

        /**
         * Shuffles a decklist out onto the table and draws an opening hand.
         *
         * The Extra deck is kept aside rather than shuffled in, because it is
         * not part of the deck you draw from and putting it there would be the
         * one rule of the game a sandbox does have to know.
         */
        fun from(deck: Deck, random: Random, handSize: Int = OPENING_HAND): TableState =
            TableState(library = deck.main.shuffled(random), extra = deck.extra)
                .draw(handSize)

        /**
         * Lays a deck out around a hand somebody already has.
         *
         * For the moment a test hand turns out to be interesting and the
         * question stops being "would I keep this" and becomes "what does it
         * actually do" — which is a question you can only answer by playing it,
         * and which used to mean shuffling until the same five came up again.
         *
         * A card in [hand] that is not in the deck is still dealt. It came from
         * somewhere real, and a hand that silently lost a card would be a worse
         * answer than one that is slightly generous.
         */
        fun holding(deck: Deck, hand: List<CardId>, random: Random): TableState {
            val rest = deck.main.toMutableList()
            hand.forEach { rest.remove(it) }
            return TableState(hand = hand, library = rest.shuffled(random), extra = deck.extra)
        }
    }
}
