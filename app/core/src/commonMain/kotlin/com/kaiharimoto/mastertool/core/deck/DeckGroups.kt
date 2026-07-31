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
 * when you were reasoning about it. Here the deck stays in its stored order and
 * the groups are drawn as *space*: a gap opens where one run of a group ends
 * and the next begins, so the shape of the list is readable without a single
 * card having moved.
 */
data class BreakdownSlot(
    val index: Int,
    val groupId: String?,
    /** A gap opens to the left of this card. */
    val gapBefore: Boolean,
    /** A gap opens to its right. */
    val gapAfter: Boolean,
)

object DeckBreakdown {

    /**
     * Where the gaps fall for a [columns]-wide grid.
     *
     * A boundary that already lands on a row break needs no gap — the row break
     * is the separation — which is what keeps the effect subtle on a deck where
     * every other card belongs to something different.
     */
    fun slots(section: List<CardId>, groups: DeckGroups, columns: Int): List<BreakdownSlot> {
        val width = columns.coerceAtLeast(1)
        val owners = section.map(groups::groupOf)

        return owners.mapIndexed { index, owner ->
            BreakdownSlot(
                index = index,
                groupId = owner,
                gapBefore = index > 0 &&
                    index % width != 0 &&
                    owners[index - 1] != owner,
                gapAfter = index < owners.lastIndex &&
                    (index + 1) % width != 0 &&
                    owners[index + 1] != owner,
            )
        }
    }
}
