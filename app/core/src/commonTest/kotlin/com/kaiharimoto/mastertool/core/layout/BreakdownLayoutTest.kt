package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreakdownLayoutTest {

    private val engine = DeckGroup("g-engine", "Engine", color = 2, order = 0)
    private val handtraps = DeckGroup("g-traps", "Handtraps", color = 5, order = 1)
    private val bricks = DeckGroup("g-brick", "Bricks", color = 0, order = 2)

    /** Forty distinct passcodes, so a deck index and a card are the same thing. */
    private val deck = (1..40).map { CardId(it) }

    private fun groupsWith(
        engineAt: List<Int> = emptyList(),
        trapsAt: List<Int> = emptyList(),
        bricksAt: List<Int> = emptyList(),
    ): DeckGroups {
        var groups = DeckGroups.EMPTY.upsert(engine).upsert(handtraps).upsert(bricks)
        engineAt.forEach { groups = groups.assign(deck[it], engine.id) }
        trapsAt.forEach { groups = groups.assign(deck[it], handtraps.id) }
        bricksAt.forEach { groups = groups.assign(deck[it], bricks.id) }
        return groups
    }

    // ---- the snake --------------------------------------------------------

    @Test
    fun consecutiveSlotsAreAlwaysNeighbouringCells() {
        // The property the whole design rests on. Reading order fails this at
        // every row boundary — slot 9 and slot 10 land at opposite ends of the
        // grid — and that failure is what made groups draw as two shapes.
        for (columns in 3..20) {
            val slots = columns * 8
            for (slot in 0 until slots - 1) {
                val here = BreakdownLayout.cellOfSlot(slot, columns)
                val next = BreakdownLayout.cellOfSlot(slot + 1, columns)
                assertTrue(
                    BreakdownLayout.adjacent(here, next, columns),
                    "columns=$columns slot=$slot: cells $here and $next are not neighbours",
                )
            }
        }
    }

    @Test
    fun theSnakeIsAPermutationAndItsOwnInverse() {
        for (columns in 3..20) {
            val cells = (0 until columns * 5).map { BreakdownLayout.cellOfSlot(it, columns) }
            assertEquals(cells.toSet().size, cells.size, "columns=$columns: cells collide")
            cells.forEachIndexed { slot, cell ->
                assertEquals(slot, BreakdownLayout.slotOfCell(cell, columns))
            }
        }
    }

    @Test
    fun everyOtherRowRunsBackwards() {
        // Ten wide: row 0 left to right, row 1 right to left.
        assertEquals(listOf(0, 1, 2), (0..2).map { BreakdownLayout.cellOfSlot(it, 10) })
        assertEquals(listOf(19, 18, 17), (10..12).map { BreakdownLayout.cellOfSlot(it, 10) })
        // And the turn: slot 9 is cell 9, slot 10 is the cell directly below it.
        assertEquals(9, BreakdownLayout.cellOfSlot(9, 10))
        assertEquals(19, BreakdownLayout.cellOfSlot(10, 10))
    }

    // ---- the dissection ---------------------------------------------------

    @Test
    fun aScatteredGroupBecomesExactlyOnePiece() {
        // The complaint, answered: six handtraps at arbitrary deck positions.
        val plan = BreakdownLayout.plan(deck, groupsWith(trapsAt = listOf(3, 7, 12, 19, 25, 33)), 10)

        val piece = plan.pieces.single { it.groupId == handtraps.id }
        assertEquals(6, piece.size)
        assertEquals(listOf(3, 7, 12, 19, 25, 33), piece.deckIndices)
        // One connected region, and therefore one outline with one ring.
        assertTrue(GridRegion.isConnected(piece.cells, columns = 10))
        assertEquals(1, GridRegion.outline(piece.cells, columns = 10).size)
    }

    @Test
    fun everyPieceIsOneShapeAtEveryColumnCount() {
        // Awkward on purpose: three groups interleaved with the remainder, sized
        // so that pieces start and end mid-row and wrap.
        val groups = groupsWith(
            engineAt = listOf(0, 4, 8, 11, 15, 17, 21, 24, 28, 31, 35, 39),
            trapsAt = listOf(1, 5, 9, 14, 22, 30),
            bricksAt = listOf(2, 13, 26, 37),
        )

        for (columns in 3..20) {
            val plan = BreakdownLayout.plan(deck, groups, columns)
            plan.pieces.forEach { piece ->
                assertTrue(
                    GridRegion.isConnected(piece.cells, columns),
                    "columns=$columns: ${piece.groupId} is not connected",
                )
                assertEquals(
                    1,
                    GridRegion.outline(piece.cells, columns).count { !it.isHole },
                    "columns=$columns: ${piece.groupId} draws more than one shape",
                )
            }
        }
    }

    @Test
    fun thePiecesTileTheDeckExactly() {
        val plan = BreakdownLayout.plan(deck, groupsWith(
            engineAt = listOf(0, 1, 2, 5, 9),
            trapsAt = listOf(3, 7, 12, 19, 25, 33),
            bricksAt = listOf(4, 8),
        ), 10)

        val cells = plan.pieces.flatMap { it.cells }
        assertEquals(deck.size, cells.size)
        assertEquals(deck.indices.toSet(), cells.toSet())
        // No cell is claimed twice.
        assertEquals(cells.size, cells.toSet().size)
    }

    @Test
    fun groupsKeepTheirOrderAndTheRemainderIsLast() {
        val plan = BreakdownLayout.plan(deck, groupsWith(
            engineAt = listOf(30, 31),
            trapsAt = listOf(0, 1),
            bricksAt = listOf(20),
        ), 10)

        assertEquals(
            listOf(engine.id, handtraps.id, bricks.id, null),
            plan.pieces.map { it.groupId },
        )
        assertTrue(plan.pieces.last().isRemainder)
        // Within a group, the deck's own order survives.
        assertEquals(listOf(30, 31), plan.pieces[0].deckIndices)
    }

    @Test
    fun anEmptyGroupDrawsNoPiece() {
        val plan = BreakdownLayout.plan(deck, groupsWith(trapsAt = listOf(2, 3)), 10)

        assertEquals(listOf(handtraps.id, null), plan.pieces.map { it.groupId })
    }

    @Test
    fun aFullyAssignedDeckHasNoRemainder() {
        val everything = deck.indices.toList()
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = everything), 10)

        assertEquals(1, plan.pieces.size)
        assertTrue(plan.pieces.none { it.isRemainder })
    }

    @Test
    fun theStoredOrderIsNeverTouchedAndTheMapsAreInverse() {
        val plan = BreakdownLayout.plan(deck, groupsWith(
            trapsAt = listOf(3, 7, 12, 19, 25, 33),
            engineAt = listOf(0, 1, 2),
        ), 10)

        assertEquals(deck.size, plan.displayToDeck.size)
        assertEquals(deck.indices.toSet(), plan.displayToDeck.toSet())
        plan.displayToDeck.forEachIndexed { slot, deckIndex ->
            assertEquals(slot, plan.deckToDisplay[deckIndex])
        }
        // And every card can be found on screen and read back.
        deck.indices.forEach { deckIndex ->
            val cell = plan.cellOfDeckIndex(deckIndex)!!
            assertEquals(deckIndex, plan.deckIndexAt(cell))
        }
    }

    @Test
    fun aDropOnAPieceLandsWithTheGroupItJoined() {
        val plan = BreakdownLayout.plan(deck, groupsWith(trapsAt = listOf(3, 7, 12)), 10)
        val traps = plan.pieces.first { it.groupId == handtraps.id }

        assertEquals(13, BreakdownLayout.insertIndexFor(traps, deck.size))
        // Nothing under the pointer means the end of the deck.
        assertEquals(deck.size, BreakdownLayout.insertIndexFor(null, deck.size))
    }

    @Test
    fun aPieceCanBeFoundFromACellUnderThePointer() {
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = listOf(0, 1, 2, 3)), 10)

        // The engine's four cards take display slots 0..3, which in a snake are
        // cells 0..3 on the first row.
        assertEquals(engine.id, plan.pieceAt(0)?.groupId)
        assertEquals(engine.id, plan.pieceAt(3)?.groupId)
        assertNull(plan.pieceAt(4)?.groupId)
    }

    // ---- picking ----------------------------------------------------------

    @Test
    fun theIdentityPlanLeavesEveryCardWhereTheDeckPutIt() {
        val groups = groupsWith(trapsAt = listOf(3, 7, 12, 19, 25, 33))
        val plan = BreakdownLayout.identity(deck, groups, 10)

        assertEquals(deck.indices.toList(), plan.displayToDeck)
        deck.indices.forEach { assertEquals(it, plan.cellOfDeckIndex(it)) }
        // The group's cells are exactly where its cards are — scattered, which
        // is why the picking state draws seats and not an outline.
        val piece = plan.pieces.single { it.groupId == handtraps.id }
        assertEquals(listOf(3, 7, 12, 19, 25, 33), piece.cells)
    }

    @Test
    fun cellsOfMapsASelectionThroughWhicheverPlanIsShowing() {
        val groups = groupsWith(trapsAt = listOf(3, 7, 12))
        val picking = BreakdownLayout.identity(deck, groups, 10)
        val cut = BreakdownLayout.plan(deck, groups, 10)

        assertEquals(listOf(3, 7, 12), BreakdownLayout.cellsOf(picking, listOf(3, 7, 12)))
        // Dissected, the same three cards are the first three cells of the grid.
        assertEquals(listOf(0, 1, 2), BreakdownLayout.cellsOf(cut, listOf(3, 7, 12)))
    }

    @Test
    fun anEmptySectionPlansWithoutFalling() {
        val plan = BreakdownLayout.plan(emptyList(), DeckGroups.EMPTY, 10)

        assertTrue(plan.pieces.isEmpty())
        assertNull(plan.deckIndexAt(0))
        assertEquals(10, plan.columns)
    }
}
