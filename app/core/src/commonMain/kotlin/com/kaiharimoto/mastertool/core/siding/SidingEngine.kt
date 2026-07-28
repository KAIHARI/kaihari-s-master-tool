package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection

/** Something that did not go to plan, named well enough to act on. */
sealed interface SidingProblem {
    /** A card the plan takes out is not in the Main deck to take out. */
    data class NotInMain(val card: CardId, val wanted: Int, val found: Int) : SidingProblem

    /** A card the plan brings in is not in the Side deck to bring in. */
    data class NotInSide(val card: CardId, val wanted: Int, val found: Int) : SidingProblem

    /** The plan changes the size of the Main deck. Legal, and almost always a slip. */
    data class Unbalanced(val cardsIn: Int, val cardsOut: Int) : SidingProblem
}

/** The decklist after siding, and everything that did not fit. */
data class SidingResult(
    val deck: Deck,
    val problems: List<SidingProblem>,
) {
    val isClean: Boolean get() = problems.isEmpty()
}

/**
 * Applies a siding plan to a decklist.
 *
 * Pure and total: a plan that does not fit the deck still produces a deck, and
 * says what it could not do. Between rounds there is a clock running, so
 * refusing to side at all because one card was already moved would be the wrong
 * trade — swap what can be swapped, and be specific about the rest.
 *
 * **An incoming card takes the place of the card it replaces.** Out and in are
 * paired in order, and the card entering the Main deck is written at the index
 * the card leaving it occupied. Appending instead would be simpler and would
 * quietly destroy the arrangement — this application's whole premise is that the
 * order of a decklist is something its owner chose, and siding between games two
 * and three is the last moment to start reshuffling it.
 */
object SidingEngine {

    fun apply(deck: Deck, pattern: SidingPattern, goingFirst: Boolean): SidingResult =
        apply(deck, pattern.swapFor(goingFirst))

    fun apply(deck: Deck, swap: SidingSwap): SidingResult {
        val problems = mutableListOf<SidingProblem>()

        val main = deck.main.toMutableList()
        val side = deck.side.toMutableList()

        // Where each departing card sat, so an arriving one can take that spot.
        val vacated = mutableListOf<Int>()

        swap.cardsOut.groupingBy { it }.eachCount().forEach { (card, wanted) ->
            val positions = main.withIndex().filter { it.value == card }.map { it.index }
            if (positions.size < wanted) {
                problems += SidingProblem.NotInMain(card, wanted, positions.size)
            }
            // Latest copies first: a player who arranged their deck put the
            // copies they think about first, and the third one is the spare.
            positions.takeLast(minOf(wanted, positions.size)).forEach { vacated += it }
        }

        swap.cardsIn.groupingBy { it }.eachCount().forEach { (card, wanted) ->
            val available = side.count { it == card }
            if (available < wanted) {
                problems += SidingProblem.NotInSide(card, wanted, available)
            }
        }

        // Fill vacated slots in the order they appear in the deck, so pairing is
        // positional and predictable rather than dependent on how the plan was
        // typed. A slot with nobody to fill it is removed afterwards.
        vacated.sort()

        val arriving = swap.cardsIn.filter { card ->
            val taken = side.indexOf(card)
            if (taken < 0) false else { side.removeAt(taken); true }
        }

        val leaving = mutableListOf<CardId>()
        val unfilled = mutableListOf<Int>()

        vacated.forEachIndexed { position, index ->
            leaving += main[index]
            val replacement = arriving.getOrNull(position)
            if (replacement != null) main[index] = replacement else unfilled += index
        }

        // Removed back to front so the earlier indices stay valid.
        unfilled.sortedDescending().forEach { main.removeAt(it) }

        // Anything with no slot to take goes on the end. Only reachable when the
        // plan brings in more than it takes out, which `Unbalanced` also flags.
        if (arriving.size > vacated.size) {
            main += arriving.drop(vacated.size)
        }

        side += leaving

        if (!swap.isBalanced) {
            problems += SidingProblem.Unbalanced(swap.cardsIn.size, swap.cardsOut.size)
        }

        return SidingResult(
            deck = deck.with(DeckSection.MAIN, main).with(DeckSection.SIDE, side),
            problems = problems,
        )
    }
}
