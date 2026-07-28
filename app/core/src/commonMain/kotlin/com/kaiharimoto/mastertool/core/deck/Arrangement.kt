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
    ): List<CardId> {
        if (ids.size < 2) return ids

        val groups = LinkedHashMap<K, MutableList<CardId>>()
        val unkeyed = ArrayList<CardId>()

        ids.forEach { id ->
            val key = keyOf(id)
            if (key == null) unkeyed.add(id) else groups.getOrPut(key) { ArrayList() }.add(id)
        }

        val keys = if (groupOrder == null) groups.keys.toList() else groups.keys.sortedWith(groupOrder)

        val out = ArrayList<CardId>(ids.size)
        keys.forEach { out.addAll(groups.getValue(it)) }
        out.addAll(unkeyed)
        return out
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
}
