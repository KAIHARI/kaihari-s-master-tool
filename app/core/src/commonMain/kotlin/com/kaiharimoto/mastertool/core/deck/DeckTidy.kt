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
 * - [SUMMONING] is the one the Extra deck actually wants. Grouping fifteen cards
 *   by "card type" tells you they are all monsters; grouping them by how they
 *   are summoned is how every player already reads that pane.
 *
 * Anything a sort already does is left to the sort. Two menu entries that differ
 * by a subtlety is a worse offer than one that is clear about what it costs.
 */
enum class TidyBy(val label: String, val blurb: String) {
    COPIES("Gather copies", "strays rejoin their siblings"),
    CATEGORY("Group by type", "order kept inside each group"),
    ARCHETYPE("Group by archetype", "engines stay where you put them"),
    SUMMONING("Group by summon type", "Fusion, Synchro, Xyz, Link"),
}

/**
 * How a card is summoned, read off the frame rather than the type line.
 *
 * The frame is what carries it — the same reason [com.kaiharimoto.mastertool
 * .core.model.Card.isExtraDeck] reads it — and a Fusion Pendulum monster has a
 * compound frame, so this matches on containment and takes the first hit. The
 * order is declaration order, which is the order an Extra deck is read in.
 */
private enum class SummonFrame(val fragment: String) {
    RITUAL("ritual"),
    FUSION("fusion"),
    SYNCHRO("synchro"),
    XYZ("xyz"),
    LINK("link"),
    PENDULUM("pendulum"),
    NORMAL("normal"),
    EFFECT("effect"),
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

            TidyBy.SUMMONING -> Arrangement.groupBy(ids, compareBy { it.ordinal }) { id ->
                lookup(id)?.frameType?.let { frame ->
                    SummonFrame.entries.firstOrNull { frame.contains(it.fragment, true) }
                }
            }
        }
}
