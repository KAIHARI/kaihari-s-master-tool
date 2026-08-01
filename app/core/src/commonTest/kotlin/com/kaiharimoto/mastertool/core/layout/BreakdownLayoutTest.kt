package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.deck.DeckLenses
import com.kaiharimoto.mastertool.core.deck.KeyPaint
import com.kaiharimoto.mastertool.core.deck.Lens
import com.kaiharimoto.mastertool.core.deck.LensKey
import com.kaiharimoto.mastertool.core.deck.LensKeying
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

    /** A partition written straight out, for the cases groups cannot express. */
    private fun keying(vararg cells: String?): LensKeying = LensKeying(
        lens = Lens.COPIES,
        keys = cells.filterNotNull().distinct().sorted().map {
            LensKey(it, it, it, KeyPaint.Hue(2))
        },
        keyOfCell = cells.toList(),
    )

    // ---- the deck is never rearranged --------------------------------------

    @Test
    fun everyCardStaysWhereTheDeckPutIt() {
        // The whole contract of this mode: cell N holds deck position N, with
        // a lens on exactly as with it off.
        val plan = BreakdownLayout.plan(deck, groupsWith(trapsAt = listOf(3, 7, 12)), 10)

        assertEquals(deck.size, plan.count)
        assertEquals(handtraps.id, plan.keyAt(3))
        assertEquals(handtraps.id, plan.keyAt(12))
        assertNull(plan.keyAt(4))
        assertEquals(listOf(3, 7, 12), plan.pieces.first { it.keyId == handtraps.id }.cells)
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
    fun theUnclaimedPartOfTheDeckDoesNotCrackAtAll() {
        // What is left over is not a group with a shape — it is the surface the
        // blocks are being lifted out of, and it stays a mosaic while they go.
        val plan = BreakdownLayout.plan(deck, groupsWith(engineAt = listOf(0, 1)), 10)

        assertEquals(CellEdges.NONE, plan.edgesAt(2))
        assertEquals(CellEdges.NONE, plan.edgesAt(20))
        // The block still pulls away from it, from its own side.
        assertTrue(plan.edgesAt(1).end)
        assertTrue(plan.edgesAt(1).bottom)
    }

    @Test
    fun aCardWithNoDeckAroundItIsFramedOnEverySide() {
        val groups = DeckGroups.EMPTY.upsert(engine).assign(CardId(1), engine.id)
        val plan = BreakdownLayout.plan(listOf(CardId(1)), groups, 10)

        assertEquals(CellEdges.ALL, plan.edgesAt(0))
    }

    @Test
    fun aLensWithNothingInItLeavesTheDeckWhole() {
        // Roles on a deck nobody has labelled: identical to no lens at all,
        // which is the invitation to make the first group rather than an error.
        val plan = BreakdownLayout.plan(deck, DeckGroups.EMPTY, 10)

        assertTrue(plan.isEmpty)
        deck.indices.forEach { assertEquals(CellEdges.NONE, plan.edgesAt(it)) }
    }

    // ---- any partition, not just groups ------------------------------------

    @Test
    fun anyPartitionCracksTheSameWay() {
        // 0,1 one key; 2 another; the crack falls between them wherever the
        // partition came from.
        val plan = BreakdownLayout.plan(keying("a", "a", "b", "b"), 4)

        assertFalse(plan.edgesAt(0).end)
        assertTrue(plan.edgesAt(1).end)
        assertTrue(plan.edgesAt(2).start)
        assertEquals(2, plan.pieces.size)
    }

    @Test
    fun piecesComeBackInTheLensOrderNotTheDecksOrder() {
        // The bar and the deck have to agree, and the bar is ordered by the
        // lens — otherwise the legend reshuffles as cards move.
        val plan = BreakdownLayout.plan(keying("b", "a", "b", "a"), 4)

        assertEquals(listOf("a", "b"), plan.pieces.map { it.keyId })
        assertEquals(listOf(1, 3), plan.pieces.first().cells)
    }

    @Test
    fun aKeyNothingFallsInGetsNoBlock() {
        val plan = BreakdownLayout.plan(
            LensKeying(
                lens = Lens.ROLES,
                keys = listOf("a", "b").map { LensKey(it, it, it, KeyPaint.Hue(2)) },
                keyOfCell = listOf("a", "a", null, null),
            ),
            4,
        )

        assertEquals(listOf("a"), plan.pieces.map { it.keyId })
    }

    @Test
    fun theCopiesLensCracksBetweenTheRunsOfACard() {
        // Three, three, two, one across four columns — the reading the lens is
        // for, drawn as three slabs and a chip.
        val section = listOf(1, 1, 1, 2, 2, 2, 3, 3, 4).map(::CardId)
        val plan = BreakdownLayout.plan(
            DeckLenses.key(Lens.COPIES, section, cards = { null }, groups = DeckGroups.EMPTY),
            3,
        )

        assertEquals(listOf("c1", "c2", "c3"), plan.pieces.map { it.keyId }.sorted())
        // Rows 0 and 1 are whole runs of threes, so they never crack inside.
        assertFalse(plan.edgesAt(1).start)
        assertTrue(plan.edgesAt(5).bottom)
        assertEquals(0, plan.pieces.sumOf { it.size } - section.size)
    }

    @Test
    fun anEmptySectionPlansWithoutFalling() {
        val plan = BreakdownLayout.plan(emptyList(), DeckGroups.EMPTY, 10)

        assertTrue(plan.pieces.isEmpty())
        assertEquals(CellEdges.NONE, plan.edgesAt(0))
        assertNull(plan.keyAt(0))
    }

    @Test
    fun everyClaimedCellBelongsToExactlyOnePiece() {
        val plan = BreakdownLayout.plan(
            deck,
            groupsWith(engineAt = listOf(0, 1, 2, 11), trapsAt = listOf(3, 7, 12, 19)),
            10,
        )

        val claimed = plan.pieces.flatMap { it.cells }
        assertEquals(claimed.size, claimed.distinct().size)
        deck.indices.forEach { cell ->
            assertEquals(plan.keyAt(cell), plan.pieceAt(cell)?.keyId)
        }
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
}
