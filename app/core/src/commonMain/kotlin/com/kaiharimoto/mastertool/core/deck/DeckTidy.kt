package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * The tidies, which are deliberately not sorts.
 *
 * [DeckSorter] already decides a whole order from one property, and that is the
 * destructive operation every builder has: press it once and whatever thought
 * went into the layout is gone. These are the opposite. Each is a stable
 * partition — cards are collected into groups and the groups laid end to end,
 * but inside a group nothing moves at all.
 *
 * Only three, and each is here because the sorter cannot express it:
 *
 * - [COPIES] has no sort equivalent whatsoever. It is the gentlest edit in the
 *   program: the stray third copy snaps back beside its siblings and not one
 *   other card moves.
 * - [CATEGORY] brings the monsters together without also re-levelling and
 *   alphabetising them, which is what `SortMode.TYPE` does on the way past.
 * - [ARCHETYPE] groups by engine and leaves the engines in the order they were
 *   already in. A sort would alphabetise them, silently undoing the decision to
 *   put one at the front.
 *
 * Anything a sort already does is left to the sort. Two menu entries that differ
 * by a subtlety is a worse offer than one that is clear about what it costs.
 */
enum class TidyBy(val label: String, val blurb: String) {
    COPIES("Gather copies", "strays rejoin their siblings"),
    CATEGORY("Group by type", "order kept inside each group"),
    ARCHETYPE("Group by archetype", "engines stay where you put them"),
}

object DeckTidy {

    /**
     * Applies a [TidyBy] to a list of card ids.
     *
     * [lookup] returning null — a card the pool has not downloaded, which is the
     * normal state moments after an import — is not an error. That card has no
     * key, so it lands at the end, still in the order it was in. Dropping it
     * would be data loss dressed as a convenience.
     */
    fun apply(ids: List<CardId>, mode: TidyBy, lookup: (CardId) -> Card?): List<CardId> =
        when (mode) {
            TidyBy.COPIES -> Arrangement.gatherCopies(ids)

            // Enum order is the reading order of a decklist, so the ordinal is
            // the comparator and there is nothing to keep in step with it.
            TidyBy.CATEGORY -> Arrangement.groupBy(ids, compareBy { it.ordinal }) {
                lookup(it)?.category
            }

            // No comparator, on purpose. See the note above.
            TidyBy.ARCHETYPE -> Arrangement.groupBy(ids) { lookup(it)?.archetype }
        }
}
