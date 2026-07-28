package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GridNavigationTest {

    // 0 1 2
    // 3 4 5
    // 6
    private fun step(from: Int, direction: GridStep, count: Int = 7, columns: Int = 3) =
        GridNavigation.step(from, count, columns, direction)

    @Test
    fun sidewaysIsTheNeighbouringCard() {
        assertEquals(2, step(1, GridStep.RIGHT))
        assertEquals(0, step(1, GridStep.LEFT))
    }

    @Test
    fun steppingLeftFromTheStartOfARowWrapsToTheRowAbove() {
        // The list is one-dimensional and the grid is a wrap of it. Stopping at
        // the row edge would make the last card of a row unreachable without
        // going up and along.
        assertEquals(2, step(3, GridStep.LEFT))
        assertEquals(3, step(2, GridStep.RIGHT))
    }

    @Test
    fun aRowAtATimeVertically() {
        assertEquals(4, step(1, GridStep.DOWN))
        assertEquals(1, step(4, GridStep.UP))
    }

    @Test
    fun steppingDownIntoAShortRowLandsOnTheLastCard() {
        // Row 1 is full, row 2 holds only card 6. Down from 4 has somewhere to
        // go, and it is 6 -- not nothing, and not off the end.
        assertEquals(6, step(4, GridStep.DOWN))
        assertEquals(6, step(5, GridStep.DOWN))
    }

    @Test
    fun theLastRowHasNothingBelowIt() {
        assertNull(step(6, GridStep.DOWN))
    }

    @Test
    fun aFullLastRowAlsoHasNothingBelowIt() {
        // Six cards in three columns: no short row to clamp into, and the check
        // has to notice that rather than clamping to the last card and leaving
        // the cursor stuck one square from where it started.
        assertNull(GridNavigation.step(4, count = 6, columns = 3, step = GridStep.DOWN))
    }

    @Test
    fun theEdgesReturnNothing() {
        assertNull(step(0, GridStep.LEFT))
        assertNull(step(6, GridStep.RIGHT))
        assertNull(step(1, GridStep.UP))
    }

    @Test
    fun oneColumnIsAList() {
        assertEquals(3, GridNavigation.step(2, count = 5, columns = 1, step = GridStep.DOWN))
        assertEquals(1, GridNavigation.step(2, count = 5, columns = 1, step = GridStep.UP))
        assertNull(GridNavigation.step(4, count = 5, columns = 1, step = GridStep.DOWN))
    }

    @Test
    fun oneRowHasNoVerticalAtAll() {
        GridStep.entries.filter { it == GridStep.UP || it == GridStep.DOWN }.forEach {
            assertNull(GridNavigation.step(1, count = 3, columns = 5, step = it))
        }
    }

    @Test
    fun aSingleCardGoesNowhere() {
        GridStep.entries.forEach {
            assertNull(GridNavigation.step(0, count = 1, columns = 3, step = it))
        }
    }

    @Test
    fun nonsenseIsRefusedRatherThanGuessedAt() {
        GridStep.entries.forEach {
            assertNull(GridNavigation.step(0, count = 0, columns = 3, step = it))
            assertNull(GridNavigation.step(2, count = 2, columns = 3, step = it))
            assertNull(GridNavigation.step(-1, count = 5, columns = 3, step = it))
            assertNull(GridNavigation.step(0, count = 5, columns = 0, step = it))
        }
    }

    @Test
    fun aStepAlwaysLandsSomewhereReal() {
        // Whatever the shape, a non-null answer is a position in the list and is
        // never where it started -- a cursor that reports having moved and did
        // not is worse than one that refused.
        (1..25).forEach { count ->
            (1..7).forEach { columns ->
                (0 until count).forEach { from ->
                    GridStep.entries.forEach { direction ->
                        val to = GridNavigation.step(from, count, columns, direction)
                        if (to != null) {
                            check(to in 0 until count) { "$from $direction -> $to of $count" }
                            check(to != from) { "$from $direction went nowhere" }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun upAndDownAreEachOthersInverseInTheFullPartOfTheGrid() {
        (0 until 6).forEach { from ->
            val down = GridNavigation.step(from, count = 9, columns = 3, step = GridStep.DOWN)!!
            assertEquals(from, GridNavigation.step(down, 9, 3, GridStep.UP))
        }
    }
}
