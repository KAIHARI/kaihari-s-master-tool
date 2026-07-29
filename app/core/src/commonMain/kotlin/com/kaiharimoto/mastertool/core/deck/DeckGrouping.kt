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

    /**
     * Where the player's gaps fall once the copies are collapsed.
     *
     * The stacked view had no gaps in it at all, which quietly said that turning
     * the density down throws your arrangement away. It does not: a stack sits
     * where its first copy sat, so a gap falls before the first stack that
     * begins at or after it.
     *
     * A gap *inside* a run of copies — between the second and third Ash Blossom
     * — has no seam of its own once those three are one tile, so it moves
     * forward to the next one there is. Dropping it instead would mean a mark
     * disappearing when the density is turned down, which reads as the
     * arrangement being lost; two gaps landing on the same seam simply become
     * one. A gap inside the *last* run has nothing after it at all and is not
     * drawn, which is the same rule `Breaks` applies at the end of a section.
     */
    fun breaksOverStacks(breaks: Breaks, stacks: List<CardStack>, sectionSize: Int): Breaks {
        if (breaks.isEmpty || stacks.isEmpty()) return Breaks.NONE

        val out = HashSet<Int>()
        breaks.clampedTo(sectionSize).before.forEach { cut ->
            val at = stacks.indexOfFirst { it.firstIndex >= cut }
            if (at > 0) out.add(at)
        }
        return Breaks(out)
    }
}
