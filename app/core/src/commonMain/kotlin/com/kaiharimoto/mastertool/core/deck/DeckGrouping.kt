package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/** One card and how many copies of it a section holds. */
data class CardStack(
    val id: CardId,
    val count: Int,
    /** Where the first copy sits, so a stack can still point back at the list. */
    val firstIndex: Int,
)

/**
 * Collapses a section's copies into one entry per distinct card.
 *
 * A projection for display only. The deck itself stays an ordered multiset,
 * because that is what a `.ydk` file is and what has to round trip: a stacked
 * view has no positional identity, so writing one back would mean inventing an
 * order the player never chose. Reordering is therefore switched off while the
 * stacked view is on — there is no coherent way to drag a stack — and the
 * per-stack stepper takes its place.
 */
object DeckGrouping {

    fun stacks(section: List<CardId>): List<CardStack> {
        val firstIndex = LinkedHashMap<CardId, Int>()
        val counts = LinkedHashMap<CardId, Int>()

        section.forEachIndexed { index, id ->
            if (id !in firstIndex) firstIndex[id] = index
            counts[id] = (counts[id] ?: 0) + 1
        }

        // LinkedHashMap iterates in insertion order, which here is first
        // appearance — so a stacked view lists cards in the order the deck does.
        return counts.map { (id, count) -> CardStack(id, count, firstIndex.getValue(id)) }
    }
}
