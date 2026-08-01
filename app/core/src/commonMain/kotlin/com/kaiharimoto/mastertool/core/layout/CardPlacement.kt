package com.kaiharimoto.mastertool.core.layout

import kotlin.math.max
import kotlin.math.min

/**
 * Where a card sits inside its cell, and how big it is drawn.
 *
 * The cell travels with it because the cell is the part that must not change:
 * a caller that lays out the placement without also pinning the cell is a
 * caller that has let the crack back into the layout.
 */
data class CellPlacement(
    val cellWidth: Float,
    val cellHeight: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * How a card gives way to the crack without leaving its cell.
 *
 * This exists because of a bug that shipped: the crack was applied as padding
 * around the card, which made the *item* bigger than the size the fitter had
 * solved for. Ten rows of that is forty dp the main deck was never given, so
 * the grid quietly overflowed its box and started scrolling — the exact failure
 * the fitter exists to prevent, reintroduced by the mode drawn on top of it.
 *
 * The rule that fixes it, and that the design handbook already implied: the
 * cell is fixed and the card moves inside it. A card gives way on the sides
 * that face another key, keeps its 59:86 exactly, and never grows. Whatever
 * width or height is left over after the aspect ratio is satisfied is pushed
 * onto the sides that did *not* crack, so the gap on a cracked side is exactly
 * the crack — the slack hides in the seam that was already there instead of
 * making one crack look wider than another.
 */
object CardPlacement {

    /**
     * @param cellWidth the cell the grid gave the card, which it may not exceed
     * @param crack how far a card pulls back from a neighbour in another key
     * @param aspectRatio width over height; 59:86 for a Yu-Gi-Oh! card
     */
    fun place(
        cellWidth: Float,
        cellHeight: Float,
        edges: CellEdges,
        crack: Float,
        aspectRatio: Float,
    ): CellPlacement {
        val ratio = if (aspectRatio > 0f) aspectRatio else 1f
        val give = max(0f, crack)

        val startGap = if (edges.start) give else 0f
        val topGap = if (edges.top) give else 0f
        val endGap = if (edges.end) give else 0f
        val bottomGap = if (edges.bottom) give else 0f

        // Never negative: a crack wider than the cell would otherwise flip the
        // card inside out. It collapses to nothing instead.
        val availableWidth = max(0f, cellWidth - startGap - endGap)
        val availableHeight = max(0f, cellHeight - topGap - bottomGap)

        val width = min(availableWidth, availableHeight * ratio)
        val height = if (ratio > 0f) width / ratio else 0f

        return CellPlacement(
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            left = startGap + share(availableWidth - width, edges.start, edges.end),
            top = topGap + share(availableHeight - height, edges.top, edges.bottom),
            width = width,
            height = height,
        )
    }

    /**
     * How much of [leftover] goes before the card.
     *
     * A side that cracked has the gap it was promised and wants no more; a side
     * that did not is against a card of the same key, where the extra fraction
     * of a dp disappears into the two dp gutter. When both sides cracked or
     * neither did, split it and let the card sit centred.
     */
    private fun share(leftover: Float, startCracked: Boolean, endCracked: Boolean): Float = when {
        leftover <= 0f -> 0f
        startCracked == endCracked -> leftover / 2f
        startCracked -> 0f
        else -> leftover
    }
}
