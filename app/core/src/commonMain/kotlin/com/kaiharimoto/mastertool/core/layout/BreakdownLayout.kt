package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * One group, drawn as one block of the deck.
 *
 * [cells] are positions in the section — which, because the breakdown never
 * moves a card, are both where the cards are stored and where they are drawn.
 * A null [groupId] is the remainder: everything not yet given a role.
 */
data class BreakdownPiece(
    val groupId: String?,
    val ordinal: Int,
    val colorIndex: Int,
    val cells: List<Int>,
) {
    val size: Int get() = cells.size
    val isRemainder: Boolean get() = groupId == null
}

/**
 * Which sides of a card face a different group.
 *
 * This is the whole breakdown, per card. A side that faces the same group is
 * not an edge — the two cards are part of one block and sit flush against each
 * other. A side that faces a different group, or the outside of the deck, is
 * where the block ends, and that is where the deck cracks open.
 */
data class CellEdges(
    val start: Boolean,
    val top: Boolean,
    val end: Boolean,
    val bottom: Boolean,
) {
    val any: Boolean get() = start || top || end || bottom

    companion object {
        val NONE = CellEdges(start = false, top = false, end = false, bottom = false)
        val ALL = CellEdges(start = true, top = true, end = true, bottom = true)
    }
}

/** The deck's groups, as blocks over the grid the deck is already drawn in. */
data class BreakdownPlan(
    val columns: Int,
    val count: Int,
    val pieces: List<BreakdownPiece>,
    private val groupOfCell: List<String?>,
) {
    fun pieceAt(cell: Int): BreakdownPiece? {
        val group = groupOfCell.getOrNull(cell) ?: return pieces.firstOrNull { it.isRemainder }
        return pieces.firstOrNull { it.groupId == group }
    }

    fun groupAt(cell: Int): String? = groupOfCell.getOrNull(cell)

    /**
     * Which sides of [cell] are the edge of its block.
     *
     * A neighbour off the end of the deck counts as a different group, so the
     * outermost cards are framed like any other edge and every block is inset
     * from its surroundings by the same amount, wherever it sits.
     */
    fun edgesAt(cell: Int): CellEdges {
        if (cell !in 0 until count) return CellEdges.NONE
        val group = groupOfCell.getOrNull(cell)
        val column = cell % columns

        fun sameAs(other: Int, sameRow: Boolean): Boolean {
            if (other !in 0 until count) return false
            if (sameRow && other / columns != cell / columns) return false
            return groupOfCell.getOrNull(other) == group
        }

        return CellEdges(
            start = column == 0 || !sameAs(cell - 1, sameRow = true),
            top = !sameAs(cell - columns, sameRow = false),
            end = column == columns - 1 || !sameAs(cell + 1, sameRow = true),
            bottom = !sameAs(cell + columns, sameRow = false),
        )
    }

    companion object {
        val EMPTY = BreakdownPlan(1, 0, emptyList(), emptyList())
    }
}

/**
 * The breakdown, as geometry over the deck exactly as it is stored.
 *
 * Nothing is rearranged. The deck is drawn in its own order, ten across, the
 * same grid with the lens on as with it off — so the grid you edit is always
 * the deck you save, and a card is never somewhere you did not put it.
 *
 * What the lens changes is the *space* between cards. With it off the deck is a
 * near-seamless mosaic; with it on, a card pulls away from its neighbour only
 * where the two belong to different groups. Cards of one group stay flush, so a
 * group reads as a single slab of the deck with a solid edge of its colour, and
 * the deck cracks along exactly the lines the groups draw — one block per run
 * of adjacent cards, however the deck happens to be ordered.
 *
 * The consequence to be honest about: a group whose cards are scattered through
 * the deck is several blocks, because that is genuinely where those cards are.
 * The tool's job is to show the deck, not to flatter it — and a handful of
 * blocks in one colour is itself the useful reading, since it says the deck is
 * not sorted the way it is being thought about.
 */
object BreakdownLayout {

    /** Whether two cells share an edge in a [columns]-wide grid. */
    fun adjacent(a: Int, b: Int, columns: Int): Boolean {
        val width = columns.coerceAtLeast(1)
        val rowA = a / width
        val colA = a % width
        val rowB = b / width
        val colB = b % width
        return (rowA == rowB && (colA - colB) * (colA - colB) == 1) ||
            (colA == colB && (rowA - rowB) * (rowA - rowB) == 1)
    }

    fun plan(section: List<CardId>, groups: DeckGroups, columns: Int): BreakdownPlan {
        val width = columns.coerceAtLeast(1)
        if (section.isEmpty()) return BreakdownPlan.EMPTY.copy(columns = width)

        val groupOfCell = section.map(groups::groupOf)
        val ordered = groups.ordered()

        val pieces = mutableListOf<BreakdownPiece>()
        ordered.forEachIndexed { ordinal, group ->
            val cells = groupOfCell.indices.filter { groupOfCell[it] == group.id }
            // A group nobody has put a card in yet has no block; it still
            // exists in the legend, where it can be filled.
            if (cells.isNotEmpty()) {
                pieces += BreakdownPiece(group.id, ordinal, group.color, cells)
            }
        }

        val remainder = groupOfCell.indices.filter { groupOfCell[it] == null }
        if (remainder.isNotEmpty()) {
            pieces += BreakdownPiece(null, pieces.size, -1, remainder)
        }

        return BreakdownPlan(width, section.size, pieces, groupOfCell)
    }

    /**
     * The blocks a set of cells falls into: runs of cards that touch.
     *
     * One group can be several blocks, and each one is drawn as its own shape.
     * Which cells belong together is the same question [GridRegion] answers
     * when it traces them, asked in advance so the caller can count them.
     */
    fun blocks(cells: List<Int>, columns: Int): List<List<Int>> {
        val remaining = cells.toMutableSet()
        val blocks = mutableListOf<List<Int>>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            remaining.remove(seed)
            val block = mutableListOf(seed)
            val frontier = ArrayDeque(listOf(seed))

            while (frontier.isNotEmpty()) {
                val cell = frontier.removeFirst()
                remaining.filter { adjacent(cell, it, columns) }.forEach { neighbour ->
                    remaining.remove(neighbour)
                    block += neighbour
                    frontier.addLast(neighbour)
                }
            }

            blocks += block.sorted()
        }

        return blocks.sortedBy { it.first() }
    }
}
