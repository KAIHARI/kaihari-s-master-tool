package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * One user-defined functional group: "engine", "handtraps", "non-engine
 * starters" — whatever the person building the deck thinks in.
 *
 * [color] is an index into the UI's prismatic ramp, deliberately not a raw
 * colour value: groups keep looking like part of the app when the theme
 * evolves, and the file format never learns what a colour is.
 */
data class DeckGroup(
    val id: String,
    val name: String,
    val color: Int,
    val order: Int,
)

/**
 * The deck's functional breakdown, exactly as the user drew it.
 *
 * Assignment is deliberately fully manual — no heuristics, no auto-tagging.
 * Deciding what counts as a starter *is* deckbuilding; the tool's job is to
 * hold the answer, not to guess it. Assignment is also per passcode, not per
 * copy: all copies of a card share one role, which is how ratios are actually
 * reasoned about ("three starters", never "two copies as starters and one as
 * something else").
 */
data class DeckGroups(
    val groups: List<DeckGroup>,
    val assignments: Map<CardId, String>,
) {
    val isEmpty: Boolean get() = groups.isEmpty() && assignments.isEmpty()

    fun byId(id: String): DeckGroup? = groups.firstOrNull { it.id == id }

    /** Groups in display order. */
    fun ordered(): List<DeckGroup> = groups.sortedWith(compareBy({ it.order }, { it.id }))

    fun upsert(group: DeckGroup): DeckGroups =
        copy(groups = groups.filterNot { it.id == group.id } + group)

    /** Removing a group frees its cards rather than stranding them. */
    fun remove(groupId: String): DeckGroups = DeckGroups(
        groups = groups.filterNot { it.id == groupId },
        assignments = assignments.filterValues { it != groupId },
    )

    /** [groupId] null clears the assignment — the card returns to Ungrouped. */
    fun assign(cardId: CardId, groupId: String?): DeckGroups = when {
        groupId == null -> copy(assignments = assignments - cardId)
        byId(groupId) == null -> this
        else -> copy(assignments = assignments + (cardId to groupId))
    }

    /** Moves a group to [toIndex] in display order, renumbering densely. */
    fun reorder(groupId: String, toIndex: Int): DeckGroups {
        val current = ordered().toMutableList()
        val from = current.indexOfFirst { it.id == groupId }
        if (from < 0) return this
        val moved = current.removeAt(from)
        current.add(toIndex.coerceIn(0, current.size), moved)
        return copy(groups = current.mapIndexed { index, group -> group.copy(order = index) })
    }

    /** Assignments that point at no live group, or into [deck]-absent cards, pruned. */
    fun pruned(deckIds: Collection<CardId>): DeckGroups {
        val live = groups.map { it.id }.toSet()
        val present = deckIds.toSet()
        return copy(
            assignments = assignments.filter { (card, group) -> group in live && card in present }
        )
    }

    /** How many cards of [section] belong to [groupId]. */
    fun countIn(section: List<CardId>, groupId: String): Int =
        section.count { assignments[it] == groupId }

    /** The group a card belongs to, ignoring assignments to deleted groups. */
    fun groupOf(id: CardId): String? = assignments[id]?.takeIf { byId(it) != null }

    companion object {
        val EMPTY = DeckGroups(emptyList(), emptyMap())
    }
}

/**
 * One card's place in the breakdown lens.
 *
 * The lens does not reorder anything. An earlier version split the section into
 * labelled blocks, which meant the grid you edited was not the deck you saved —
 * position in a block was display order, so a drop there could only mean
 * "assign", never "insert", and the deck's real order was invisible exactly
 * when you were reasoning about it.
 *
 * Instead the deck stays in its stored order and the groups are drawn as
 * *pieces*: each unbroken run of one group is one shape, laid over the cards it
 * covers. A run stops at the end of a row because a piece that wrapped would be
 * two shapes pretending to be one — which is why [runLength] is counted within
 * the row and never across it.
 */
data class BreakdownSlot(
    val index: Int,
    val groupId: String?,
    /** Where this card sits in its run; 0 is the card the piece starts on. */
    val positionInRun: Int,
    /** How many cards the whole run holds. */
    val runLength: Int,
) {
    val startsRun: Boolean get() = positionInRun == 0
}

object DeckBreakdown {

    /**
     * The runs a [columns]-wide grid would draw, one slot per card.
     *
     * Ungrouped cards are given runs too — they are just never drawn as a piece
     * — so the list stays index-aligned with the section and a caller can look
     * up any position without a search.
     */
    fun slots(section: List<CardId>, groups: DeckGroups, columns: Int): List<BreakdownSlot> {
        val width = columns.coerceAtLeast(1)
        val owners = section.map(groups::groupOf)
        val slots = ArrayList<BreakdownSlot>(section.size)

        var start = 0
        while (start < owners.size) {
            // The run ends at a change of group, or at the end of the row —
            // whichever comes first.
            val rowEnd = (start / width + 1) * width
            var end = start + 1
            while (end < minOf(rowEnd, owners.size) && owners[end] == owners[start]) end++

            val length = end - start
            for (i in start until end) {
                slots += BreakdownSlot(
                    index = i,
                    groupId = owners[start],
                    positionInRun = i - start,
                    runLength = length,
                )
            }
            start = end
        }

        return slots
    }
}
