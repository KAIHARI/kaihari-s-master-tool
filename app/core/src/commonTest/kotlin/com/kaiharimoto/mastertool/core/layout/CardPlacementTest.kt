package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardPlacementTest {

    private val aspect = 59f / 86f
    private val cellWidth = 60f
    private val cellHeight = cellWidth / aspect

    private fun place(edges: CellEdges, crack: Float = 4f) =
        CardPlacement.place(cellWidth, cellHeight, edges, crack, aspect)

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertTrue(abs(expected - actual) < 0.01f, "$message expected $expected, was $actual")
    }

    // ---- the bug this exists to prevent -------------------------------------

    @Test
    fun aCardNeverLeavesItsCell() {
        // The crack used to be padding, which made the item bigger than the size
        // the fitter solved for and pushed the last row out of the pane. Whatever
        // cracks, the card stays inside the box it was given.
        val allEdges = listOf(
            CellEdges.NONE,
            CellEdges.ALL,
            CellEdges(start = true, top = false, end = false, bottom = false),
            CellEdges(start = false, top = true, end = false, bottom = true),
            CellEdges(start = true, top = false, end = true, bottom = false),
            CellEdges(start = false, top = false, end = true, bottom = true),
        )

        allEdges.forEach { edges ->
            val placed = place(edges)
            assertTrue(placed.left >= -0.01f, "$edges left ${placed.left}")
            assertTrue(placed.top >= -0.01f, "$edges top ${placed.top}")
            assertTrue(placed.left + placed.width <= cellWidth + 0.01f, "$edges overflows width")
            assertTrue(placed.top + placed.height <= cellHeight + 0.01f, "$edges overflows height")
        }
    }

    @Test
    fun anUncrackedCardFillsItsCellExactly() {
        // With no lens on, the deck is the mosaic it always was — every card at
        // exactly the size the fitter solved for, no fractions lost.
        val placed = place(CellEdges.NONE)

        assertClose(0f, placed.left)
        assertClose(0f, placed.top)
        assertClose(cellWidth, placed.width)
        assertClose(cellHeight, placed.height)
    }

    @Test
    fun aCrackOfNothingChangesNothing() {
        // The whole mode animates through this on the way in and out.
        val placed = CardPlacement.place(cellWidth, cellHeight, CellEdges.ALL, 0f, aspect)

        assertClose(cellWidth, placed.width)
        assertClose(cellHeight, placed.height)
    }

    // ---- the shape stays the shape ------------------------------------------

    @Test
    fun theCardIsAlwaysFiftyNineByEightySix() {
        listOf(0f, 1f, 4f, 9f).forEach { crack ->
            listOf(CellEdges.ALL, CellEdges(true, false, false, true)).forEach { edges ->
                val placed = place(edges, crack)
                assertClose(aspect, placed.width / placed.height, "crack $crack $edges:")
            }
        }
    }

    @Test
    fun aCardOnlyEverGetsSmaller() {
        val placed = place(CellEdges(start = true, top = false, end = false, bottom = false))

        assertTrue(placed.width < cellWidth)
        assertTrue(placed.height < cellHeight)
    }

    // ---- where the slack goes -----------------------------------------------

    @Test
    fun theGapOnACrackedSideIsExactlyTheCrack() {
        // The point of pushing the leftover onto the uncracked sides: every
        // crack in the deck is the same width, so the blocks look cut rather
        // than nudged.
        val placed = place(CellEdges(start = true, top = false, end = false, bottom = false))

        assertClose(4f, placed.left)
        // Width was the binding constraint, so the card spends all of what is
        // left and the height it gave up is what gets shared out.
        assertClose(cellWidth, placed.left + placed.width)
        assertClose((cellHeight - placed.height) / 2f, placed.top)
    }

    @Test
    fun leftoverHeightGoesToTheSideThatDidNotCrack() {
        val top = place(CellEdges(start = false, top = true, end = false, bottom = false))
        assertClose(4f, top.top)

        val bottom = place(CellEdges(start = false, top = false, end = false, bottom = true))
        assertClose(4f, cellHeight - (bottom.top + bottom.height))
    }

    @Test
    fun aCardCrackedOnBothSidesSitsCentred() {
        val placed = place(CellEdges.ALL)

        assertClose(cellWidth - (placed.left + placed.width), placed.left)
        assertClose(cellHeight - (placed.top + placed.height), placed.top)
    }

    @Test
    fun aVerticalCrackTakesTheHeightItAsksFor() {
        // The binding constraint on a tall cell: cracking top and bottom must
        // actually shrink the card, not be swallowed by the aspect ratio.
        val placed = place(CellEdges(start = false, top = true, end = false, bottom = true))

        assertClose(4f, placed.top)
        assertClose(cellHeight - 8f, placed.height)
        assertClose((cellHeight - 8f) * aspect, placed.width)
    }

    // ---- refusing to fall over ----------------------------------------------

    @Test
    fun aCrackWiderThanTheCellCollapsesRatherThanInverts() {
        val placed = CardPlacement.place(cellWidth, cellHeight, CellEdges.ALL, 100f, aspect)

        assertEquals(0f, placed.width)
        assertEquals(0f, placed.height)
    }

    @Test
    fun aCellWithNoRoomInItPlacesNothing() {
        val placed = CardPlacement.place(0f, 0f, CellEdges.NONE, 4f, aspect)

        assertEquals(0f, placed.width)
        assertEquals(0f, placed.height)
    }
}
