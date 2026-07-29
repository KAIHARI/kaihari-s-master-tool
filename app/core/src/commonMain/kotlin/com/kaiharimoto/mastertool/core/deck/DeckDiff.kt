package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection

/** One card, and how many of it each list holds. */
data class DeckChange(val id: CardId, val before: Int, val after: Int) {
    val delta: Int get() = after - before
}

/**
 * What changed in one section.
 *
 * Two lists rather than one signed one, because that is how the question is
 * actually asked: *what went in, what came out.* A single list ordered by delta
 * puts a card that went from three to two next to one that went from nought to
 * one, which reads as a pair of unrelated facts.
 */
data class SectionDiff(
    val section: DeckSection,
    val added: List<DeckChange>,
    val removed: List<DeckChange>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()

    /** Cards gained, counting copies. */
    val cardsAdded: Int get() = added.sumOf { it.delta }

    /** Cards lost, as a positive number — nobody says "minus three came out". */
    val cardsRemoved: Int get() = removed.sumOf { -it.delta }

    val isBalanced: Boolean get() = cardsAdded == cardsRemoved
}

/**
 * The difference between two versions of a deck.
 *
 * The question every list gets asked between events: *what did I change since
 * last week.* A decklist is an ordered multiset and the answer people want is
 * about the multiset — three Bonfire became two — while the order is a separate
 * question with a separate answer, which is why [reorderedOnly] exists.
 */
data class DeckDiff(
    val sections: List<SectionDiff>,
    /** Whether the two lists hold their cards in the same order. */
    val sameOrder: Boolean,
) {

    val isEmpty: Boolean get() = sections.all { it.isEmpty }

    operator fun get(section: DeckSection): SectionDiff =
        sections.first { it.section == section }

    val cardsAdded: Int get() = sections.sumOf { it.cardsAdded }
    val cardsRemoved: Int get() = sections.sumOf { it.cardsRemoved }

    /**
     * Whether the two lists hold the same cards in a different order.
     *
     * Worth saying out loud rather than reporting as "no changes". This program
     * treats the arrangement as the player's own work, so two decks that differ
     * only in it differ in the thing it cares most about — and a comparison that
     * called them identical would be contradicting the rest of the application.
     */
    val reorderedOnly: Boolean get() = isEmpty && !sameOrder

    companion object {
        fun between(before: Deck, after: Deck): DeckDiff = DeckDiff(
            sections = DeckSection.entries.map { section ->
                section(section, before[section], after[section])
            },
            sameOrder = DeckSection.entries.all { before[it] == after[it] },
        )

        private fun section(
            section: DeckSection,
            before: List<CardId>,
            after: List<CardId>,
        ): SectionDiff {
            val was = before.groupingBy { it }.eachCount()
            val now = after.groupingBy { it }.eachCount()

            // In the order each list holds them, so the answer reads like the
            // decks rather than like a set. A card that arrived is looked for
            // where it now sits; one that left is remembered where it was.
            val added = after.distinct()
                .filter { (now[it] ?: 0) > (was[it] ?: 0) }
                .map { DeckChange(it, was[it] ?: 0, now[it] ?: 0) }

            val removed = before.distinct()
                .filter { (now[it] ?: 0) < (was[it] ?: 0) }
                .map { DeckChange(it, was[it] ?: 0, now[it] ?: 0) }

            return SectionDiff(section, added, removed)
        }
    }
}
