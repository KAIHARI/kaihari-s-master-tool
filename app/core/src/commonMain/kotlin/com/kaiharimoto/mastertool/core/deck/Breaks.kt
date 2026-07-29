package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * Where the player has left gaps in a section.
 *
 * The arrangement of a deck is the one thing this program treats as the
 * player's own — the order is never touched without being asked, because it is
 * how somebody sees their own list. But an order alone cannot say *these nine
 * are the engine and those six are the handtraps*, and on a real table that is
 * said by pushing the piles apart.
 *
 * So: a set of positions with a gap before them. Not a card, not a label, not a
 * folder — a gap, which is what a player would actually do with their hands, and
 * which costs nothing to ignore.
 *
 * Positions rather than cards, because a break belongs to the layout and not to
 * any card in it. Anchoring a gap to "before the second Ash Blossom" would mean
 * the gap moving when a copy is cut, which is not what anybody drew it to mean.
 */
data class Breaks(val before: Set<Int> = emptySet()) {

    val isEmpty: Boolean get() = before.isEmpty()

    /** Puts a gap here, or takes one away if there already is one. */
    fun toggledAt(index: Int): Breaks =
        if (index in before) Breaks(before - index) else Breaks(before + index)

    /**
     * Keeps only the gaps that still mean something.
     *
     * A break at zero is not a gap, it is the start of the section; a break at
     * or past the end is a gap with nothing after it. Both happen naturally as
     * cards come and go, and both are meaningless rather than wrong — so they
     * are dropped on the way out rather than guarded against on the way in.
     */
    fun clampedTo(size: Int): Breaks = Breaks(before.filterTo(HashSet()) { it in 1 until size })

    /**
     * The runs of cards between the gaps.
     *
     * Empty for an empty section, and one whole run when there are no breaks —
     * so a caller can lay out every section the same way whether or not anybody
     * has ever put a gap in it.
     */
    fun groups(size: Int): List<IntRange> {
        if (size <= 0) return emptyList()
        val cuts = clampedTo(size).before.sorted()
        var start = 0
        val out = ArrayList<IntRange>(cuts.size + 1)
        cuts.forEach { cut ->
            out.add(start until cut)
            start = cut
        }
        out.add(start until size)
        return out
    }

    /**
     * The run of cards this position belongs to.
     *
     * What "that pile" means when somebody points at one card in it. Null for a
     * position outside the section, which is what a stale cursor is.
     */
    fun groupAt(index: Int, size: Int): IntRange? =
        groups(size).firstOrNull { index in it }

    /**
     * Moves the gaps along after cards are inserted at [at].
     *
     * A card dropped exactly on a gap joins the group *before* it. Either answer
     * is defensible and this one has the property that matters: appending to the
     * end of a section never disturbs a gap, which is what most additions are.
     */
    fun afterInsert(at: Int, count: Int = 1): Breaks =
        Breaks(before.mapTo(HashSet()) { if (it >= at) it + count else it })

    /**
     * Moves the gaps back after the card at [at] is taken out.
     *
     * A gap sitting immediately before the removed card stays where it is, so it
     * now falls before whatever moved up into that place. That is the reading
     * that keeps a gap between two groups when the last card of the first group
     * is cut, rather than swallowing it.
     */
    fun afterRemove(at: Int): Breaks =
        Breaks(before.mapTo(HashSet()) { if (it > at) it - 1 else it })

    /**
     * A card that was at [from] and is now at [to].
     *
     * Which is a remove and an insert, and reads correctly across a gap without
     * anything extra: drag a card from the first group into the second and the
     * first loses one while the second gains one, because the gap is a position
     * and the cards moved past it.
     */
    fun afterShift(from: Int, to: Int): Breaks =
        if (from == to) this else afterRemove(from).afterInsert(to)

    companion object {
        val NONE = Breaks()

        /** The gaps that fall between runs of the given lengths. */
        fun betweenRuns(lengths: List<Int>): Breaks {
            val cuts = HashSet<Int>()
            var at = 0
            lengths.forEach { length ->
                at += length
                cuts.add(at)
            }
            // The last run ends at the end of the section, and a gap there is
            // not a gap. `clampedTo` would drop it anyway; not adding it keeps
            // this honest without depending on that.
            cuts.remove(at)
            return Breaks(cuts)
        }
    }
}

/** A list and the gaps in it, which is what a tidy actually produces. */
data class Arranged(val ids: List<CardId>, val breaks: Breaks)
