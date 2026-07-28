package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionTest {

    private val main = DeckSection.MAIN
    private val side = DeckSection.SIDE

    @Test
    fun aTapSelectsOneCard() {
        val selection = Selections.only(main, 4)

        assertEquals(setOf(4), selection.indices)
        assertTrue(selection.contains(main, 4))
        assertFalse(selection.contains(side, 4))
    }

    @Test
    fun togglingAddsThenTakesAway() {
        var selection = Selections.only(main, 1)
        selection = Selections.toggle(selection, main, 5)
        assertEquals(setOf(1, 5), selection.indices)

        selection = Selections.toggle(selection, main, 1)
        assertEquals(setOf(5), selection.indices)
    }

    @Test
    fun togglingTheLastCardOffEmptiesTheSelection() {
        val selection = Selections.toggle(Selections.only(main, 3), main, 3)

        assertTrue(selection.isEmpty)
        assertEquals(Selection.EMPTY, selection)
    }

    @Test
    fun touchingAnotherSectionStartsOver() {
        // A selection spanning two sections is a state with no action behind it:
        // one drag with two destinations, one delete with two legality rules.
        val selection = Selections.toggle(Selections.only(main, 1), side, 2)

        assertEquals(side, selection.section)
        assertEquals(setOf(2), selection.indices)
    }

    @Test
    fun aRangeCoversEverythingBetween() {
        val selection = Selections.extendTo(Selections.only(main, 2), main, 6)

        assertEquals(setOf(2, 3, 4, 5, 6), selection.indices)
    }

    @Test
    fun aRangeWorksBackwards() {
        assertEquals(
            setOf(2, 3, 4, 5, 6),
            Selections.extendTo(Selections.only(main, 6), main, 2).indices,
        )
    }

    @Test
    fun draggingARangeBackShrinksIt() {
        // Replaced rather than added to, so a shift-selection swept out too far
        // and pulled back gives what it looks like it gives.
        var selection = Selections.extendTo(Selections.only(main, 2), main, 9)
        selection = Selections.extendTo(selection, main, 4)

        assertEquals(setOf(2, 3, 4), selection.indices)
    }

    @Test
    fun theAnchorFollowsTheLastCardTouched() {
        var selection = Selections.only(main, 1)
        selection = Selections.toggle(selection, main, 7)
        selection = Selections.extendTo(selection, main, 9)

        // From 7, not from 1.
        assertEquals(setOf(7, 8, 9), selection.indices)
    }

    @Test
    fun extendingWithNoAnchorJustSelectsTheOne() {
        assertEquals(setOf(3), Selections.extendTo(Selection.EMPTY, main, 3).indices)
    }

    // A block is the other useful meaning of "between these two cards" in a grid,
    // and the one a reading-order range cannot express.

    @Test
    fun aBlockIsARectangleNotARun() {
        // 10 columns: from index 2 to index 23 is columns 2..3, rows 0..2.
        val selection = Selections.extendBlockTo(
            Selections.only(main, 2),
            main,
            index = 23,
            columns = 10,
            count = 40,
        )

        assertEquals(setOf(2, 3, 12, 13, 22, 23), selection.indices)
    }

    @Test
    fun aBlockDownOneColumnIsThatColumn() {
        val selection = Selections.extendBlockTo(
            Selections.only(main, 4),
            main,
            index = 34,
            columns = 10,
            count = 40,
        )

        assertEquals(setOf(4, 14, 24, 34), selection.indices)
    }

    @Test
    fun aBlockStopsAtTheEndOfTheDeck() {
        // The last row is usually short, and a rectangle drawn over it would
        // otherwise select positions that do not exist.
        val selection = Selections.extendBlockTo(
            Selections.only(main, 0),
            main,
            index = 22,
            columns = 10,
            count = 23,
        )

        assertTrue(selection.indices.all { it < 23 })
        assertTrue(22 in selection.indices)
    }

    @Test
    fun aBlockWithNoGridFallsBackToARun() {
        assertEquals(
            Selections.extendTo(Selections.only(main, 1), main, 4).indices,
            Selections.extendBlockTo(Selections.only(main, 1), main, 4, columns = 0, count = 10).indices,
        )
    }

    @Test
    fun editingTheSectionDropsTheSelection() {
        // Total loss rather than a best effort at tracking: a removal shifts every
        // index after it, and a selection that quietly came to mean different
        // cards than the highlighted ones would be worse than none, because the
        // next action on it moves real cards.
        val selection = Selections.extendTo(Selections.only(main, 2), main, 6)

        assertTrue(Selections.afterEdit(selection, main).isEmpty)
    }

    @Test
    fun editingAnotherSectionLeavesItAlone() {
        val selection = Selections.extendTo(Selections.only(main, 2), main, 6)

        assertEquals(selection, Selections.afterEdit(selection, side))
    }

    @Test
    fun aSelectionReadsOutInDeckOrder() {
        // The order a multi-card move has to preserve, whatever order it was
        // clicked in.
        var selection = Selections.only(main, 7)
        selection = Selections.toggle(selection, main, 2)
        selection = Selections.toggle(selection, main, 5)

        assertEquals(listOf(2, 5, 7), selection.ordered())
    }
}
