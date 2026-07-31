package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * One group, drawn as one shape.
 *
 * [cells] are grid cells (row-major, `row * columns + col`) — where the cards
 * are on screen. [deckIndices] are where those same cards live in the stored
 * deck. The two are different orders on purpose, and keeping both here is what
 * lets a drop on a piece be translated back into a real deck position.
 *
 * A null [groupId] is the remainder: everything not yet given a role.
 */
data class BreakdownPiece(
    val groupId: String?,
    val ordinal: Int,
    val colorIndex: Int,
    val slots: List<Int>,
    val deckIndices: List<Int>,
    val cells: List<Int>,
) {
    val size: Int get() = cells.size
    val isRemainder: Boolean get() = groupId == null
}

/**
 * Where every card is drawn, and what shape each group makes.
 *
 * [displayToDeck] and [deckToDisplay] are inverse permutations of the same
 * section — the display is rearranged, the stored deck is not touched.
 */
data class BreakdownPlan(
    val columns: Int,
    /**
     * Whether the rows alternate direction.
     *
     * True only when the deck is dissected. While a group is being picked out
     * the deck is shown exactly as it is stored, and a stored order shown with
     * every other row reversed is not the order anybody asked to see.
     */
    val serpentine: Boolean,
    val displayToDeck: List<Int>,
    val deckToDisplay: List<Int>,
    val pieces: List<BreakdownPiece>,
    /** Which piece owns each grid cell, for hit-testing a drop. */
    val pieceOfCell: Map<Int, Int>,
) {
    fun cellOfSlot(slot: Int): Int =
        if (serpentine) BreakdownLayout.cellOfSlot(slot, columns) else slot

    fun slotOfCell(cell: Int): Int =
        if (serpentine) BreakdownLayout.slotOfCell(cell, columns) else cell

    /** The card drawn in [cell], as a position in the stored deck. */
    fun deckIndexAt(cell: Int): Int? = displayToDeck.getOrNull(slotOfCell(cell))

    fun cellOfDeckIndex(deckIndex: Int): Int? =
        deckToDisplay.getOrNull(deckIndex)?.let(::cellOfSlot)

    fun pieceAt(cell: Int): BreakdownPiece? = pieceOfCell[cell]?.let(pieces::getOrNull)

    companion object {
        val EMPTY = BreakdownPlan(1, false, emptyList(), emptyList(), emptyList(), emptyMap())
    }
}

/**
 * The breakdown's geometry: which card is drawn where, and what that makes.
 *
 * The problem this solves is the one the previous two attempts both failed. A
 * group's cards sit at arbitrary positions in the deck file — six handtraps at
 * indices 3, 7, 12, 19, 25, 33 — so drawing the group where the cards already
 * are can only ever produce six unrelated marks. No amount of rendering fixes
 * that; the cards have to be next to each other, which means the *display* has
 * to be rearranged even though the deck is not.
 *
 * Grouping the display is the easy half. The other half is the trap: laid out
 * in ordinary reading order, a group that crosses a row boundary is drawn at
 * the right-hand end of one row and the left-hand end of the next, with the
 * whole grid between them — one group, two shapes, at opposite ends of the
 * screen.
 *
 * So the rows run alternately: left to right, then right to left, then left to
 * right, the way an ox ploughs a field. Consecutive display slots are then
 * *always* neighbouring cells — side by side within a row, and directly above
 * one another at a row change, because the snake turns at the same column it
 * arrived in. A group is a contiguous run of slots, so a group is always
 * exactly one connected region, whatever its size, wherever its cards sit in
 * the file, and at any column count. The pieces tile the deck's own rectangle
 * with no gaps and no extra rows, which is why the fitter never learns that
 * this mode exists.
 */
object BreakdownLayout {

    /** The grid cell a display slot is drawn in. */
    fun cellOfSlot(slot: Int, columns: Int): Int {
        val width = columns.coerceAtLeast(1)
        val row = slot / width
        val along = slot % width
        val col = if (row % 2 == 0) along else width - 1 - along
        return row * width + col
    }

    /**
     * The display slot drawn in a grid cell.
     *
     * The same formula: reversing every other row is its own inverse, which is
     * a small piece of luck worth stating rather than a coincidence to rely on
     * silently.
     */
    fun slotOfCell(cell: Int, columns: Int): Int = cellOfSlot(cell, columns)

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
     * The dissected layout: cards gathered into their groups, groups in the
     * order the user put them in, everything unassigned last.
     *
     * Within a group the cards keep their relative order in the deck, so the
     * rearrangement is a stable partition — the least surprising permutation
     * there is, and the one that puts a card back where you expect when the
     * lens goes off.
     */
    fun plan(section: List<CardId>, groups: DeckGroups, columns: Int): BreakdownPlan {
        val width = columns.coerceAtLeast(1)
        if (section.isEmpty()) return BreakdownPlan.EMPTY.copy(columns = width, serpentine = true)

        val ordered = groups.ordered()
        val byGroup = LinkedHashMap<String, MutableList<Int>>()
        ordered.forEach { byGroup[it.id] = mutableListOf() }
        val remainder = mutableListOf<Int>()

        section.forEachIndexed { deckIndex, id ->
            val owner = groups.groupOf(id)
            if (owner != null) byGroup.getValue(owner) += deckIndex else remainder += deckIndex
        }

        val displayToDeck = ArrayList<Int>(section.size)
        val pieces = mutableListOf<BreakdownPiece>()

        fun addPiece(groupId: String?, colorIndex: Int, deckIndices: List<Int>) {
            // A group nobody has put a card in yet has no shape; it still exists
            // in the legend, where it can be filled.
            if (deckIndices.isEmpty()) return
            val slots = (displayToDeck.size until displayToDeck.size + deckIndices.size).toList()
            displayToDeck += deckIndices
            pieces += BreakdownPiece(
                groupId = groupId,
                ordinal = pieces.size,
                colorIndex = colorIndex,
                slots = slots,
                deckIndices = deckIndices,
                cells = slots.map { cellOfSlot(it, width) },
            )
        }

        ordered.forEach { group -> addPiece(group.id, group.color, byGroup.getValue(group.id)) }
        addPiece(groupId = null, colorIndex = -1, deckIndices = remainder)

        return finish(width, serpentine = true, displayToDeck = displayToDeck, pieces = pieces)
    }

    /**
     * The undissected layout: every card where the deck stores it.
     *
     * Used while a group is being picked out, where moving cards under the
     * finger choosing them would be intolerable. The pieces here are scattered
     * by definition, and the caller draws them as one seat per card rather
     * than as an outline.
     */
    fun identity(section: List<CardId>, groups: DeckGroups, columns: Int): BreakdownPlan {
        val width = columns.coerceAtLeast(1)
        if (section.isEmpty()) return BreakdownPlan.EMPTY.copy(columns = width)

        val displayToDeck = section.indices.toList()
        val ordered = groups.ordered()

        val pieces = ordered.mapIndexedNotNull { ordinal, group ->
            val deckIndices = section.indices.filter { groups.groupOf(section[it]) == group.id }
            if (deckIndices.isEmpty()) {
                null
            } else {
                BreakdownPiece(
                    groupId = group.id,
                    ordinal = ordinal,
                    colorIndex = group.color,
                    slots = deckIndices,
                    deckIndices = deckIndices,
                    // Reading order, so the cells a group occupies here are
                    // simply where its cards already are.
                    cells = deckIndices,
                )
            }
        }

        return finish(width, serpentine = false, displayToDeck = displayToDeck, pieces = pieces)
    }

    /** The cells a set of deck positions occupies, under a plan. */
    fun cellsOf(plan: BreakdownPlan, deckIndices: Collection<Int>): List<Int> =
        deckIndices.mapNotNull(plan::cellOfDeckIndex).sorted()

    /**
     * Where a card dropped onto [piece] should go in the stored deck.
     *
     * Straight after the piece's last card, so a card filed into a group lands
     * with the group it joined rather than at the end of the deck — and, since
     * a piece's deck indices are ascending, that is a real position and not a
     * number invented from a screen coordinate.
     */
    fun insertIndexFor(piece: BreakdownPiece?, sectionSize: Int): Int =
        piece?.deckIndices?.lastOrNull()?.plus(1) ?: sectionSize

    private fun finish(
        columns: Int,
        serpentine: Boolean,
        displayToDeck: List<Int>,
        pieces: List<BreakdownPiece>,
    ): BreakdownPlan {
        val deckToDisplay = MutableList(displayToDeck.size) { 0 }
        displayToDeck.forEachIndexed { slot, deckIndex -> deckToDisplay[deckIndex] = slot }

        val pieceOfCell = HashMap<Int, Int>()
        pieces.forEachIndexed { index, piece ->
            piece.cells.forEach { cell -> pieceOfCell[cell] = index }
        }

        return BreakdownPlan(columns, serpentine, displayToDeck, deckToDisplay, pieces, pieceOfCell)
    }
}
