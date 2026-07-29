package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * Tidying that does not throw away the arrangement.
 *
 * Every other builder offers a sort, and a sort is destructive: it decides the
 * whole order from one property and whatever thought went into the layout is
 * gone. That is why players in this program arrange by hand and then never touch
 * the sort button again.
 *
 * So there is exactly one operation here, and it is a *stable partition*. Cards
 * are collected into groups and the groups are laid out end to end, but inside
 * each group nothing moves — the relative order that was there is the relative
 * order that comes out. Tidying a deck by type does not shuffle the monsters; it
 * only brings them together. Run it twice and the second run changes nothing.
 *
 * Which properties are worth grouping by is a question for the caller, because
 * that is the part that is opinion. This part is arithmetic.
 */
object Arrangement {

    /**
     * Groups [ids] by [keyOf], preserving order within each group.
     *
     * Groups appear in the order their first member appeared, unless
     * [groupOrder] says otherwise. First appearance is the default because it is
     * the choice that defers to the existing layout: whatever was at the front
     * stays at the front, and the tidy reads as things sliding together rather
     * than as the deck being re-sorted underneath.
     *
     * Cards for which [keyOf] returns null are not a group. They keep their
     * order and go last — a spell has no Level, and interleaving it with the
     * Level 1s would be inventing an answer rather than admitting there isn't
     * one.
     */
    fun <K : Any> groupBy(
        ids: List<CardId>,
        groupOrder: Comparator<K>? = null,
        keyOf: (CardId) -> K?,
    ): List<CardId> = groupedBy(ids, groupOrder, keyOf).ids

    /**
     * The same partition, and where the groups meet.
     *
     * The gaps are the point. A tidy that brings the monsters together and then
     * leaves them butted against the spells has done half the job — it is the
     * space between the groups that says there are groups, which is exactly what
     * a player does with their hands when they sort a pile on a table.
     */
    fun <K : Any> groupedBy(
        ids: List<CardId>,
        groupOrder: Comparator<K>? = null,
        keyOf: (CardId) -> K?,
    ): Arranged {
        if (ids.size < 2) return Arranged(ids, Breaks.NONE)

        val groups = LinkedHashMap<K, MutableList<CardId>>()
        val unkeyed = ArrayList<CardId>()

        ids.forEach { id ->
            val key = keyOf(id)
            if (key == null) unkeyed.add(id) else groups.getOrPut(key) { ArrayList() }.add(id)
        }

        val keys = if (groupOrder == null) groups.keys.toList() else groups.keys.sortedWith(groupOrder)

        val out = ArrayList<CardId>(ids.size)
        val lengths = ArrayList<Int>(keys.size + 1)
        keys.forEach {
            val group = groups.getValue(it)
            out.addAll(group)
            lengths.add(group.size)
        }
        if (unkeyed.isNotEmpty()) {
            out.addAll(unkeyed)
            lengths.add(unkeyed.size)
        }
        return Arranged(out, Breaks.betweenRuns(lengths))
    }

    /**
     * Brings copies of the same card together at the first one's position.
     *
     * The gentlest tidy there is, and the one worth reaching for most often: a
     * third copy that ended up on the far side of the pane snaps back beside its
     * siblings and nothing else moves at all. It is [groupBy] keyed on the card
     * itself, which is not a coincidence — it is the same operation, and writing
     * it twice would be how the two came to behave differently.
     */
    fun gatherCopies(ids: List<CardId>): List<CardId> = groupBy(ids) { it }

    /**
     * Gathering copies without drawing a gap around every playset.
     *
     * The one tidy whose groups are not worth marking: forty cards would become
     * twenty gaps, which says nothing and looks like a fault.
     */
    fun gathered(ids: List<CardId>): Arranged = Arranged(gatherCopies(ids), Breaks.NONE)
}
