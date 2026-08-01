package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.deck.DeckLenses
import com.kaiharimoto.mastertool.core.deck.Lens
import com.kaiharimoto.mastertool.core.deck.LensKeying
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * One key of a lens, drawn as one part of the deck.
 *
 * [cells] are positions in the section — which, because the breakdown never
 * moves a card, are both where the cards are stored and where they are drawn.
 * Cards belonging to no key get no piece at all: they are what is left, and
 * what is left should look like the deck.
 */
data class BreakdownPiece(
    val keyId: String,
    val ordinal: Int,
    val cells: List<Int>,
) {
    val size: Int get() = cells.size
}

/**
 * Which sides of a card face a different key.
 *
 * This is the whole breakdown, per card. A side that faces the same key is not
 * an edge — the two cards are part of one block and sit flush against each
 * other. A side that faces a different key, or the outside of the deck, is
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

/** A lens's keys, as blocks over the grid the deck is already drawn in. */
data class BreakdownPlan(
    val columns: Int,
    val count: Int,
    val pieces: List<BreakdownPiece>,
    private val keyOfCell: List<String?>,
) {
    val isEmpty: Boolean get() = pieces.isEmpty()

    fun pieceAt(cell: Int): BreakdownPiece? {
        val key = keyOfCell.getOrNull(cell) ?: return null
        return pieces.firstOrNull { it.keyId == key }
    }

    fun keyAt(cell: Int): String? = keyOfCell.getOrNull(cell)

    /**
     * Which sides of [cell] are the edge of its block.
     *
     * A neighbour off the end of the deck counts as a different key, so the
     * outermost cards are framed like any other edge and every block is inset
     * from its surroundings by the same amount, wherever it sits.
     *
     * A card belonging to no key has no edges at all. The unclaimed part of the
     * deck is not a group with a shape; it is the surface the blocks are being
     * lifted out of, and it stays a mosaic while they go.
     */
    fun edgesAt(cell: Int): CellEdges {
        if (cell !in 0 until count) return CellEdges.NONE
        val key = keyOfCell.getOrNull(cell) ?: return CellEdges.NONE
        val column = cell % columns

        fun sameAs(other: Int, sameRow: Boolean): Boolean {
            if (other !in 0 until count) return false
            if (sameRow && other / columns != cell / columns) return false
            return keyOfCell.getOrNull(other) == key
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

        /** A grid with nothing keyed in it: the plain mosaic, [columns] across. */
        fun empty(columns: Int) =
            BreakdownPlan(columns.coerceAtLeast(1), 0, emptyList(), emptyList())
    }
}

/**
 * The breakdown, as geometry over the deck exactly as it is stored.
 *
 * Nothing is rearranged. The deck is drawn in its own order, ten across, the
 * same grid with a lens on as with it off — so the grid you edit is always the
 * deck you save, and a card is never somewhere you did not put it.
 *
 * What a lens changes is the *space* between cards. With no lens the deck is a
 * near-seamless mosaic; with one on, a card pulls away from its neighbour only
 * where the two belong to different keys. Cards of one key stay flush, so a key
 * reads as a single slab of the deck with a solid edge of its colour, and the
 * deck cracks along exactly the lines the lens draws.
 *
 * The consequence to be honest about: a key whose cards are scattered through
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

    /**
     * The plan for a partition of the section.
     *
     * Key order comes from the lens, not from where the cards happen to fall,
     * so the legend and the deck agree and neither reorders itself as cards
     * move around.
     */
    fun plan(keying: LensKeying, columns: Int): BreakdownPlan {
        val width = columns.coerceAtLeast(1)
        if (keying.keyOfCell.isEmpty()) return BreakdownPlan.empty(width)

        val keyOfCell = keying.keyOfCell
        val pieces = mutableListOf<BreakdownPiece>()

        keying.keyOrder.forEachIndexed { ordinal, keyId ->
            val cells = keyOfCell.indices.filter { keyOfCell[it] == keyId }
            // A key nothing falls in has no block; it still exists in the bar,
            // where — if the lens is editable — it can be filled.
            if (cells.isNotEmpty()) {
                pieces += BreakdownPiece(keyId, ordinal, cells)
            }
        }

        return BreakdownPlan(width, keyOfCell.size, pieces, keyOfCell)
    }

    /** The plan for the user's own groups, which is the [Lens.ROLES] keying. */
    fun plan(section: List<CardId>, groups: DeckGroups, columns: Int): BreakdownPlan =
        plan(DeckLenses.key(Lens.ROLES, section, cards = { null }, groups = groups), columns)

    /**
     * The blocks a set of cells falls into: runs of cards that touch.
     *
     * One key can be several blocks, and each one is drawn as its own shape.
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
