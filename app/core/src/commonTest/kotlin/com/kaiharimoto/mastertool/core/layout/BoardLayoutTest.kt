package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.board.FieldZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardLayoutTest {

    private val aspect = 59f / 86f

    /** A landscape tablet, which is what this is drawn for. */
    private fun table(width: Float = 1600f, height: Float = 1000f) =
        BoardLayouter.solve(width, height, aspect)

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertTrue(abs(expected - actual) < 0.01f, "$message expected $expected, was $actual")
    }

    private val everySlot: List<BoardSlot> = buildList {
        repeat(5) { add(BoardSlot.Zone(FieldZone.Monster(it))) }
        repeat(5) { add(BoardSlot.Zone(FieldZone.SpellTrap(it))) }
        repeat(2) { add(BoardSlot.Zone(FieldZone.ExtraMonster(it))) }
        add(BoardSlot.Zone(FieldZone.FieldSpell))
        add(BoardSlot.Deck)
        add(BoardSlot.ExtraDeck)
        add(BoardSlot.Graveyard)
        add(BoardSlot.Banished)
    }

    // ---- everything the board has has somewhere to be -----------------------

    @Test
    fun everyZoneAndEveryPileGetsASlot() {
        val layout = table()

        everySlot.forEach { assertNotNull(layout[it], "$it has nowhere to be") }
        assertEquals(everySlot.size, layout.slots.size)
    }

    @Test
    fun everyCardIsDrawnAtTheSameSize() {
        // A deck pile is a card, a monster is a card, a set spell is a card.
        // One of them being bigger would say something about it that is not true.
        val layout = table()

        layout.slots.values.forEach {
            assertClose(layout.cardWidth, it.width)
            assertClose(layout.cardHeight, it.height)
        }
    }

    @Test
    fun cardsKeepTheFiftyNineByEightySixEverywhereElseUses() {
        listOf(table(), table(900f, 600f), table(2400f, 1400f)).forEach { layout ->
            assertClose(aspect, layout.cardWidth / layout.cardHeight)
        }
    }

    // ---- the layout is the one printed on a mat -----------------------------

    @Test
    fun theMonsterRowSitsAboveTheSpellRow() {
        val layout = table()

        repeat(5) { i ->
            val monster = layout[FieldZone.Monster(i)]!!
            val spellTrap = layout[FieldZone.SpellTrap(i)]!!
            assertTrue(monster.bottom <= spellTrap.top, "row $i overlaps")
            assertClose(monster.left, spellTrap.left, "column $i is not straight:")
        }
    }

    @Test
    fun theZonesRunLeftToRightInOrder() {
        val layout = table()

        val monsters = (0..4).map { layout[FieldZone.Monster(it)]!!.left }
        assertEquals(monsters.sorted(), monsters)
    }

    @Test
    fun theExtraMonsterZonesSitAboveTheSecondAndFourthMonster() {
        // Where they are on the mat, so a board reads the way a board reads.
        val layout = table()

        assertClose(
            layout[FieldZone.Monster(1)]!!.centerX,
            layout[FieldZone.ExtraMonster(0)]!!.centerX,
        )
        assertClose(
            layout[FieldZone.Monster(3)]!!.centerX,
            layout[FieldZone.ExtraMonster(1)]!!.centerX,
        )
        assertTrue(
            layout[FieldZone.ExtraMonster(0)]!!.bottom <= layout[FieldZone.Monster(0)]!!.top,
        )
    }

    @Test
    fun thePilesFlankTheZonesTheWayTheMatDoes() {
        val layout = table()

        // Field spell and extra deck on the left, graveyard and deck on the right.
        assertTrue(layout[BoardSlot.ExtraDeck]!!.right <= layout[FieldZone.SpellTrap(0)]!!.left)
        assertTrue(layout[FieldZone.FieldSpell]!!.right <= layout[FieldZone.Monster(0)]!!.left)
        assertTrue(layout[BoardSlot.Deck]!!.left >= layout[FieldZone.SpellTrap(4)]!!.right)
        assertTrue(layout[BoardSlot.Graveyard]!!.left >= layout[FieldZone.Monster(4)]!!.right)
        // Banished above the graveyard, which is where a hand puts it.
        assertClose(
            layout[BoardSlot.Graveyard]!!.centerX,
            layout[BoardSlot.Banished]!!.centerX,
        )
    }

    @Test
    fun nothingOverlapsAnythingElse() {
        val layout = table()
        val rects = layout.slots.values.toList()

        rects.indices.forEach { i ->
            (i + 1 until rects.size).forEach { j ->
                val a = rects[i]
                val b = rects[j]
                val apart = a.right <= b.left || b.right <= a.left ||
                    a.bottom <= b.top || b.bottom <= a.top
                assertTrue(apart, "$a overlaps $b")
            }
        }
    }

    // ---- the hand is part of the budget, not drawn over the leftovers -------

    @Test
    fun theHandSitsBelowTheFieldAndClearOfIt() {
        val layout = table()

        assertTrue(layout.hand.top > layout.field.bottom, "the hand is on the field")
        assertClose(layout.field.left, layout.hand.left)
        assertClose(layout.field.width, layout.hand.width)
    }

    @Test
    fun theWholeTableFitsTheSurfaceItWasGiven() {
        // The bug this file exists to prevent: a hand drawn after the fact over
        // whatever was left, and a field sized as if it were free.
        listOf(
            1600f to 1000f,
            1280f to 800f,
            2400f to 1080f,
            1000f to 1400f,
        ).forEach { (width, height) ->
            val layout = table(width, height)
            assertTrue(layout.bounds.top >= -0.01f, "$width×$height starts above the surface")
            assertTrue(layout.bounds.left >= -0.01f, "$width×$height starts left of it")
            assertTrue(layout.bounds.right <= width + 0.01f, "$width×$height runs off the side")
            assertTrue(layout.bounds.bottom <= height + 0.01f, "$width×$height runs off the bottom")
        }
    }

    @Test
    fun aWideSurfaceLeavesTheTableCentredRatherThanCornered() {
        val layout = table(width = 2400f, height = 900f)

        val leftMargin = layout.field.left
        val rightMargin = 2400f - layout.field.right
        assertClose(leftMargin, rightMargin)
        assertTrue(leftMargin > 0f)
    }

    @Test
    fun aShortSurfaceSizesTheCardsDownRatherThanCroppingThem() {
        val tall = table(1600f, 1000f)
        val short = table(1600f, 600f)

        assertTrue(short.cardWidth < tall.cardWidth)
        assertTrue(short.hand.bottom <= 600f + 0.01f)
    }

    @Test
    fun aSurfaceTooSmallForAReadableCardSaysSo() {
        // Reported rather than drawn: the caller decides, exactly as the deck
        // fitter does when even the smallest card will not fit.
        assertFalse(BoardLayouter.solve(200f, 120f, aspect).fits)
        assertTrue(table().fits)
    }

    @Test
    fun anEmptySurfaceDoesNotFallOver() {
        val layout = BoardLayouter.solve(0f, 0f, aspect)

        assertEquals(0f, layout.cardWidth)
        assertFalse(layout.fits)
        assertEquals(everySlot.size, layout.slots.size)
    }

    // ---- dropping a card ----------------------------------------------------

    @Test
    fun aDropInAZoneFindsThatZone() {
        val layout = table()

        everySlot.forEach { slot ->
            val rect = layout[slot]!!
            assertEquals(slot, layout.slotAt(rect.centerX, rect.centerY))
        }
    }

    @Test
    fun theLanesBetweenZonesAreNotDeadSpace() {
        // A card let go a few pixels wide of a zone should land in it, not
        // vanish — the gap belongs to the zones either side of it.
        val layout = table()
        val monster = layout[FieldZone.Monster(2)]!!

        val justRight = layout.slotAt(monster.right + layout.gap * 0.25f, monster.centerY)
        assertEquals(BoardSlot.Zone(FieldZone.Monster(2)), justRight)
    }

    @Test
    fun aDropOffTheTableFindsNothing() {
        val layout = table()

        assertNull(layout.slotAt(-100f, -100f))
        assertNull(layout.slotAt(layout.hand.centerX, layout.hand.centerY))
    }
}
