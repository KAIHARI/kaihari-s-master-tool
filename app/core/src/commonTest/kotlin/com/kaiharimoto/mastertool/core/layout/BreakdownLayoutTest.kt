package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreakdownLayoutTest {

    private val engine = DeckGroup("g-engine", "Engine", color = 2, order = 0)
    private val handtraps = DeckGroup("g-traps", "Handtraps", color = 5, order = 1)

    /** Forty distinct passcodes, so a deck index and a card are the same thing. */
    private val deck = (1..40).map { CardId(it) }

    private fun groupsWith(
        engineAt: List<Int> = emptyList(),
        trapsAt: List<Int> = emptyList(),
    ): DeckGroups {
        var groups = DeckGroups.EMPTY.upsert(engine).upsert(handtraps)
        engineAt.forEach { groups = groups.assign(deck[it], engine.id) }
        trapsAt.forEach { groups = groups.assign(deck[it], handtraps.id) }
        return groups
    }

    // ---- the deck is never rearranged --------------------------------------

    @Test
    fun everyCardStaysWhereTheDeckPutIt() {
        // The whole contract of this mode: cell N holds deck position N, with
        // the lens on exactly as with it off.
        val plan = BreakdownLayout.plan(deck, groupsWith(trapsAt = listOf(3, 7, 12)), 10)

        assertEquals(deck.size, plan.count)
        assertEquals(handtraps.id, plan.groupAt(3))
        assertEquals(handtraps.id, plan.groupAt(12))
        assertNull(plan.groupAt(4))
        assertEquals(listOf(3, 7, 12), plan.pieces.first { it.groupId == handtraps.id }.cells)
    }

    // ---- where the deck cracks open ----------------------------------------

    @Test
    fun aCardInTheMiddleOfItsGroupHasNoEdges() {
        // Cells 0..9 are one group, so the card at 5 is flush on both sides and
        // flush below against 15 — nothing pulls away from it.
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = (0..19).toList()), 10)

        val middle = plan.edgesAt(5)
        assertFalse(middle.start)
        assertFalse(middle.end)
        assertFalse(middle.bottom)
        // Except upward, where the deck ends.
        assertTrue(middle.top)
    }

    @Test
    fun aCardCracksAwayOnlyFromADifferentGroup() {
        // Engine 0..4, handtraps 5..9: the break falls between 4 and 5 and
        // nowhere else on that row.
        val plan = BreakdownLayout.plan(
            deck,
            groupsWith(engineAt = (0..4).toList(), trapsAt = (5..9).toList()),
            10,
        )

        assertTrue(plan.edgesAt(4).end)
        assertTrue(plan.edgesAt(5).start)
        assertFalse(plan.edgesAt(3).end)
        assertFalse(plan.edgesAt(6).start)
    }

    @Test
    fun theEndOfARowIsAlwaysAnEdge() {
        // Cell 9 and cell 10 are neighbours in the list but opposite ends of the
        // screen; treating them as flush would draw a block that wraps around.
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = (0..19).toList()), 10)

        assertTrue(plan.edgesAt(9).end)
        assertTrue(plan.edgesAt(10).start)
    }

    @Test
    fun theOutsideOfTheDeckIsAnEdgeToo() {
        // So the outermost cards are framed exactly like any other block edge,
        // and a block never looks like it has been cut off.
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = (0..39).toList()), 10)

        assertTrue(plan.edgesAt(0).start)
        assertTrue(plan.edgesAt(0).top)
        assertTrue(plan.edgesAt(39).end)
        assertTrue(plan.edgesAt(39).bottom)
        assertFalse(plan.edgesAt(15).top)
        assertFalse(plan.edgesAt(15).bottom)
    }

    @Test
    fun ungroupedCardsAreOneBlockAmongThemselves() {
        // The remainder is not forty separate cards with cracks between them —
        // it is the part of the deck not yet broken down, and it holds together.
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = listOf(0, 1)), 10)

        assertFalse(plan.edgesAt(5).end)
        assertFalse(plan.edgesAt(5).bottom)
        assertTrue(plan.edgesAt(2).start)
        assertTrue(plan.edgesAt(1).end)
    }

    @Test
    fun aCardWithNoDeckAroundItIsFramedOnEverySide() {
        val plan = BreakdownLayout.plan(listOf(CardId(1)), DeckGroups.EMPTY, 10)

        assertEquals(CellEdges.ALL, plan.edgesAt(0))
    }

    // ---- blocks -------------------------------------------------------------

    @Test
    fun cardsThatTouchAreOneBlock() {
        // Five in a row and three stacked below them: one block, because every
        // card can be reached from every other without leaving the group.
        val cells = listOf(0, 1, 2, 3, 4, 10, 11, 12)
        val blocks = BreakdownLayout.blocks(cells, 10)

        assertEquals(1, blocks.size)
        assertEquals(cells.sorted(), blocks.single())
        assertTrue(GridRegion.isConnected(blocks.single(), 10))
    }

    @Test
    fun scatteredCardsAreSeparateBlocks() {
        // The honest reading of an unsorted deck: six handtraps at arbitrary
        // positions are six blocks, and the deck says so rather than pretending.
        val blocks = BreakdownLayout.blocks(listOf(3, 7, 12, 19, 25, 33), 10)

        assertEquals(6, blocks.size)
        assertTrue(blocks.all { it.size == 1 })
    }

    @Test
    fun blocksComeBackInDeckOrder() {
        // 16 is deliberately clear of the others: cell 11 would have touched 21
        // from the row above, which is the kind of join that makes a block.
        val blocks = BreakdownLayout.blocks(listOf(20, 21, 3, 4, 16), 10)

        assertEquals(listOf(3, 4), blocks[0])
        assertEquals(listOf(16), blocks[1])
        assertEquals(listOf(20, 21), blocks[2])
    }

    @Test
    fun aBlockNeverWrapsARow() {
        // 9 and 10 are consecutive in the deck and adjacent in the list, but
        // they are at opposite ends of the screen.
        val blocks = BreakdownLayout.blocks(listOf(9, 10), 10)

        assertEquals(2, blocks.size)
    }

    @Test
    fun anEmptySectionPlansWithoutFalling() {
        val plan = BreakdownLayout.plan(emptyList(), DeckGroups.EMPTY, 10)

        assertTrue(plan.pieces.isEmpty())
        assertEquals(CellEdges.NONE, plan.edgesAt(0))
        assertNull(plan.groupAt(0))
    }

    @Test
    fun everyCellBelongsToExactlyOnePiece() {
        val plan = BreakdownLayout.plan(
            deck,
            groupsWith(engineAt = listOf(0, 1, 2, 11), trapsAt = listOf(3, 7, 12, 19)),
            10,
        )

        val claimed = plan.pieces.flatMap { it.cells }
        assertEquals(deck.size, claimed.size)
        assertEquals(deck.indices.toSet(), claimed.toSet())
        deck.indices.forEach { cell ->
            assertEquals(plan.groupAt(cell), plan.pieceAt(cell)?.groupId)
        }
    }
}
