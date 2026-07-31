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

    /**
     * Splits a section into display blocks: one per group in order, each
     * holding its cards in the order the deck stores them, then everything
     * unassigned. Groups with no cards still appear — an empty block is where
     * a card gets dropped to join it.
     */
    fun partition(section: List<CardId>): List<GroupBlock> {
        val blocks = ordered().map { group ->
            GroupBlock(group, section.filter { assignments[it] == group.id })
        }
        val ungrouped = section.filter { assignments[it] !in groups.map { g -> g.id }.toSet() }
        return blocks + GroupBlock(group = null, ids = ungrouped)
    }

    companion object {
        val EMPTY = DeckGroups(emptyList(), emptyMap())
    }
}

/** One rendered block of the breakdown. A null [group] is the Ungrouped tail. */
data class GroupBlock(
    val group: DeckGroup?,
    val ids: List<CardId>,
)

/**
 * The breakdown flattened to what a grid actually renders: a full-width header
 * then that block's cards, repeated. Kept in core because the drop maths needs
 * it — "which group did this land in" is a question about this list, and it
 * has to answer the same way the screen drew it.
 */
sealed interface BreakdownEntry {
    val groupId: String?

    data class Header(override val groupId: String?, val name: String, val color: Int?) :
        BreakdownEntry

    data class CardEntry(
        val id: CardId,
        /** Position of this copy in the raw section list. */
        val rawIndex: Int,
        override val groupId: String?,
    ) : BreakdownEntry
}

object DeckBreakdown {

    fun flatten(section: List<CardId>, groups: DeckGroups): List<BreakdownEntry> {
        // Raw indices per id, consumed first-to-last so duplicate copies keep
        // distinct positions.
        val remaining = HashMap<CardId, ArrayDeque<Int>>()
        section.forEachIndexed { index, id ->
            remaining.getOrPut(id) { ArrayDeque() }.addLast(index)
        }

        val entries = mutableListOf<BreakdownEntry>()
        groups.partition(section).forEach { block ->
            entries += BreakdownEntry.Header(
                groupId = block.group?.id,
                name = block.group?.name ?: "Ungrouped",
                color = block.group?.color,
            )
            block.ids.forEach { id ->
                val rawIndex = remaining.getValue(id).removeFirst()
                entries += BreakdownEntry.CardEntry(id, rawIndex, block.group?.id)
            }
        }
        return entries
    }

    /**
     * Which group a drop at [insertionIndex] (a "before this entry" index into
     * [entries], `0..entries.size`) lands in.
     *
     * Dropping onto a header joins the group it heads; past the end joins the
     * final block. Returns the group id, or null for Ungrouped.
     */
    fun dropGroup(entries: List<BreakdownEntry>, insertionIndex: Int): String? {
        if (entries.isEmpty()) return null
        val at = insertionIndex.coerceIn(0, entries.size)
        // Before a header means "into the block it opens"; anywhere else means
        // "into the block this position is inside".
        return if (at < entries.size) entries[at].groupId else entries.last().groupId
    }
}
