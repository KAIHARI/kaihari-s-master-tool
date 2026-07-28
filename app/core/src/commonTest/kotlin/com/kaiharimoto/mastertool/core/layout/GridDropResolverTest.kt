package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class GridDropResolverTest {

    // A 3-column grid of 100x100 tiles laid out at the origin. Item n sits at
    // column n % 3, row n / 3, so centres land on 50/150/250 in both axes.
    private fun grid(count: Int, columns: Int = 3, size: Float = 100f): List<ItemBox> =
        (0 until count).map { index ->
            val left = (index % columns) * size
            val top = (index / columns) * size
            ItemBox(index, left, top, left + size, top + size)
        }

    private fun resolve(
        items: List<ItemBox>,
        x: Float,
        y: Float,
        hysteresis: Float = 0f,
        previous: Int? = null,
    ) = GridDropResolver.insertionIndex(
        items = items,
        cursorX = x,
        cursorY = y,
        rowTolerance = 8f,
        hysteresis = hysteresis,
        previous = previous,
    )

    @Test
    fun anEmptyGridTakesTheFirstSlot() {
        assertEquals(0, resolve(emptyList(), 100f, 100f))
    }

    @Test
    fun droppingLeftOfTheFirstItemInsertsBeforeIt() {
        assertEquals(0, resolve(grid(6), x = 10f, y = 50f))
    }

    @Test
    fun droppingPastAnItemsCentreInsertsAfterIt() {
        // Left half of item 1 -> before it; right half -> after it.
        assertEquals(1, resolve(grid(6), x = 120f, y = 50f))
        assertEquals(2, resolve(grid(6), x = 180f, y = 50f))
    }

    @Test
    fun droppingRightOfTheLastItemInARowAppendsToThatRow() {
        assertEquals(3, resolve(grid(6), x = 290f, y = 50f))
    }

    @Test
    fun theRowIsChosenBeforeTheColumn() {
        // Same x, different row: a grid drop is two-dimensional, and picking the
        // nearest item outright would ignore which row the cursor is over.
        assertEquals(1, resolve(grid(9), x = 120f, y = 50f))
        assertEquals(4, resolve(grid(9), x = 120f, y = 150f))
        assertEquals(7, resolve(grid(9), x = 120f, y = 250f))
    }

    @Test
    fun aRaggedLastRowStillAppendsAtItsEnd() {
        // Seven items: the last row holds only item 6.
        assertEquals(7, resolve(grid(7), x = 90f, y = 250f))
    }

    @Test
    fun droppingAboveTheGridLandsInTheFirstRow() {
        assertEquals(0, resolve(grid(9), x = 10f, y = -400f))
    }

    @Test
    fun droppingBelowTheGridLandsInTheLastRow() {
        assertEquals(9, resolve(grid(9), x = 290f, y = 900f))
    }

    @Test
    fun aSingleRowGridResolvesAcrossItsWholeWidth() {
        val items = grid(count = 3, columns = 3)
        assertEquals(0, resolve(items, x = 0f, y = 50f))
        assertEquals(3, resolve(items, x = 299f, y = 50f))
    }

    // Hysteresis: the answer must not flip while the cursor sits on a boundary.

    @Test
    fun withoutHysteresisTheBoundaryFlipsImmediately() {
        assertEquals(2, resolve(grid(6), x = 151f, y = 50f, previous = 1))
    }

    @Test
    fun aCursorJustPastTheBoundaryKeepsThePreviousAnswer() {
        // Item 1's centre is x=150, so this is 1px over — inside the deadzone.
        assertEquals(1, resolve(grid(6), x = 151f, y = 50f, hysteresis = 12f, previous = 1))
    }

    @Test
    fun aCursorWellPastTheBoundaryCommits() {
        assertEquals(2, resolve(grid(6), x = 170f, y = 50f, hysteresis = 12f, previous = 1))
    }

    @Test
    fun theDeadzoneWorksInBothDirections() {
        // Coming back leftwards across the same boundary.
        assertEquals(2, resolve(grid(6), x = 145f, y = 50f, hysteresis = 12f, previous = 2))
        assertEquals(1, resolve(grid(6), x = 130f, y = 50f, hysteresis = 12f, previous = 2))
    }

    @Test
    fun nudgingBackAndForthOnABoundaryNeverOscillates() {
        // The symptom hysteresis exists to kill: a hand that is not quite still
        // must not produce a marker that cannot make up its mind.
        val items = grid(6)
        var current = resolve(items, x = 140f, y = 50f, hysteresis = 12f, previous = null)

        listOf(148f, 152f, 149f, 151f, 150f, 153f, 147f).forEach { x ->
            val next = resolve(items, x = x, y = 50f, hysteresis = 12f, previous = current)
            assertEquals(current, next, "insertion point moved at x=$x")
            current = next
        }
    }

    @Test
    fun aDeliberateJumpIsNotDamped() {
        // More than one gap away is a real move, however fast the drag was.
        assertEquals(3, resolve(grid(6), x = 290f, y = 50f, hysteresis = 12f, previous = 0))
    }

    @Test
    fun aBoundaryThatIsNoLongerLaidOutFallsBackToTheRawAnswer() {
        // The gap being damped is the one past the end of the grid, whose
        // boundary item does not exist. Pinning to `previous` there would leave
        // the marker stuck; the resolver has to give a real answer instead.
        assertEquals(6, resolve(grid(6), x = 290f, y = 150f, hysteresis = 12f, previous = 7))
    }
}
