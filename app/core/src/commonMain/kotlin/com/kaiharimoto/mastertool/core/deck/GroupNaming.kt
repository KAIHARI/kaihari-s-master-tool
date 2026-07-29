package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory

/**
 * What to call a group somebody pushed apart, without asking them to name it.
 *
 * [Breaks] is deliberately a gap and not a label — "not a card, not a label, not
 * a folder", because a gap is what a player does with their hands and costs
 * nothing to ignore. That principle is worth keeping, and it left the statistics
 * panel listing groups by size alone: two nine-card groups are two identical
 * rows, and the reader has to count along the deck to work out which is which.
 *
 * So the name is *derived* rather than typed. Nothing new is stored, nothing new
 * is asked for, and — unlike a name somebody typed six weeks ago — it cannot go
 * stale, because it is recomputed from whatever is in the group now. Cut the
 * last Snake-Eye card out of a group and it stops being called Snake-Eye.
 *
 * Null when the group has nothing to say for itself, which is honest: a pile of
 * eight unrelated cards is a pile of eight unrelated cards, and inventing a name
 * for it would be the program claiming to have understood something.
 */
object GroupNaming {

    /**
     * How much of a group one archetype has to be before it names the whole.
     *
     * Half. Below that the group is a mixture, and calling a mixture by its
     * largest part is the kind of nearly-true label that stops being read.
     */
    const val SHARE = 0.5

    /** At least this many cards, so one card in a pair never names the pair. */
    const val LEAST = 2

    fun nameOf(cards: List<Card>): String? {
        // A group of one is already as specific as a name could make it: the
        // reader can see the single card. "Monsters" underneath it says nothing.
        if (cards.size < LEAST) return null

        dominantArchetype(cards)?.let { return it }
        wholeCategory(cards)?.let { return it }
        return repeatedCard(cards)
    }

    /** The archetype at least half the group belongs to, if there is one. */
    private fun dominantArchetype(cards: List<Card>): String? {
        val counts = cards.mapNotNull { it.archetype?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }
            .eachCount()

        val (name, count) = counts.entries
            // By count, then by name, so a tie between two archetypes settles
            // the same way every time it is drawn rather than by map order.
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.let { it.key to it.value }
            ?: return null

        return name.takeIf { count >= LEAST && count >= cards.size * SHARE }
    }

    /** "Spells" when every card in the group is one, and nothing when they are mixed. */
    private fun wholeCategory(cards: List<Card>): String? {
        val categories = cards.map { it.category }.toSet()
        val only = categories.singleOrNull() ?: return null

        return when (only) {
            CardCategory.MONSTER -> "Monsters"
            CardCategory.SPELL -> "Spells"
            CardCategory.TRAP -> "Traps"
            CardCategory.OTHER -> null
        }
    }

    /**
     * The card there are most copies of, when there is more than one of it.
     *
     * The last resort, and a weak claim deliberately worded as one: a group with
     * three of something is usually *about* that thing, but it is being read off
     * a count rather than off anything the game says, so the count is shown.
     */
    private fun repeatedCard(cards: List<Card>): String? {
        val (card, copies) = cards.groupingBy { it.name }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.let { it.key to it.value }
            ?: return null

        return if (copies >= LEAST) "$card ×$copies" else null
    }
}
