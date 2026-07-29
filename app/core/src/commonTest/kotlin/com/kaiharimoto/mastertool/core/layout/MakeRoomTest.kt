package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MakeRoomTest {

    private fun shift(index: Int, insertAt: Int, columns: Int = 8, size: Int = 40) =
        MakeRoom.shiftFor(index, insertAt, columns, size)

    @Test
    fun theTwoCardsAtTheSeamLeanApart() {
        // Dropping before card 4: card 3 goes left, card 4 goes right, by the
        // same amount, and together they open one notch.
        assertEquals(-MakeRoom.OPENING / 2f, shift(3, insertAt = 4), 1e-6f)
        assertEquals(MakeRoom.OPENING / 2f, shift(4, insertAt = 4), 1e-6f)
    }

    @Test
    fun theCardsBehindThemLeanLessAndThenNotAtAll() {
        val nearest = shift(4, insertAt = 4)
        val next = shift(5, insertAt = 4)

        assertTrue(next > 0f && next < nearest, "the second card leans less than the first")
        assertEquals(0f, shift(6, insertAt = 4), "past the reach nothing moves")
        assertEquals(0f, shift(1, insertAt = 4))
    }

    @Test
    fun nothingLeansOffItsOwnRow() {
        // Eight columns, so 7 and 8 are neighbours in the list and strangers on
        // the screen. A card sliding sideways with nothing beside it reads as a
        // card falling over.
        assertEquals(0f, shift(7, insertAt = 8), "the end of the row above stays put")
    }

    @Test
    fun aSeamOnARowBoundaryOpensNothing() {
        // There is already a gap there -- the edge of the row is one -- and a
        // one-sided lean would slide the whole row sideways.
        (0 until 16).forEach { index ->
            assertEquals(0f, shift(index, insertAt = 8), "card $index moved")
        }
        (0 until 8).forEach { index ->
            assertEquals(0f, shift(index, insertAt = 0), "card $index moved")
        }
    }

    @Test
    fun aFullRowNeverDrifts() {
        // The property the whole thing turns on. Without it a seam one card in
        // from the left pushes the row's last card out past the edge of the
        // pane, where it is drawn cut in half.
        (0..40).forEach { seam ->
            val row = (seam / 8).coerceAtMost(4)
            val total = (row * 8 until minOf(row * 8 + 8, 40)).sumOf {
                shift(it, seam).toDouble()
            }
            assertEquals(0.0, total, 1e-5, "row $row drifted when the seam was at $seam")
        }
    }

    @Test
    fun aNotchNearTheEdgeOfARowLosesItsTailRatherThanGoingLopsided() {
        // One card to the left of the seam, so one card to the right leans too,
        // and the third stays put. The notch is the same width; what it loses is
        // the slight fan behind it.
        assertEquals(-MakeRoom.OPENING / 2f, shift(8, insertAt = 9), 1e-6f)
        assertEquals(MakeRoom.OPENING / 2f, shift(9, insertAt = 9), 1e-6f)
        assertEquals(0f, shift(10, insertAt = 9), "nothing behind it, or the row drifts")
    }

    @Test
    fun aPartRowLeansIntoTheSpaceAfterIt() {
        // The last row of a 36-card deck: four cards and four empty slots, so
        // there is somewhere for them to go and the two sides need not match.
        assertTrue(shift(35, insertAt = 36, size = 36) < 0f)
        assertTrue(shift(34, insertAt = 36, size = 36) < 0f)
        assertEquals(0f, shift(33, insertAt = 36, size = 36))
    }

    @Test
    fun droppingOntoTheEndOfAFullDeckIsQuiet() {
        // Forty cards in eight columns ends the last row exactly, so appending
        // has no row of its own to open a notch in.
        (32 until 40).forEach { assertEquals(0f, shift(it, insertAt = 40)) }
    }

    @Test
    fun theNotchIsNeverWiderThanItWasAskedToBe() {
        (0 until 40).forEach { index ->
            (0..40).forEach { seam ->
                val moved = shift(index, seam)
                assertTrue(
                    moved <= MakeRoom.OPENING / 2f + 1e-6f && moved >= -MakeRoom.OPENING / 2f - 1e-6f,
                    "card $index at seam $seam moved $moved",
                )
            }
        }
    }

    @Test
    fun nonsenseMovesNothing() {
        assertEquals(0f, MakeRoom.shiftFor(index = 0, insertAt = 0, columns = 0, size = 40))
        assertEquals(0f, MakeRoom.shiftFor(index = 0, insertAt = 0, columns = 8, size = 0))
        assertEquals(0f, MakeRoom.shiftFor(index = -1, insertAt = 0, columns = 8, size = 40))
        assertEquals(0f, MakeRoom.shiftFor(index = 40, insertAt = 0, columns = 8, size = 40))
        assertEquals(0f, MakeRoom.shiftFor(index = 0, insertAt = 41, columns = 8, size = 40))
        assertEquals(0f, MakeRoom.shiftFor(index = 0, insertAt = -1, columns = 8, size = 40))
    }

    @Test
    fun aSingleColumnHasNoRoomToPartAndMakesNone() {
        // Every card is its own row, so there is never a neighbour to lean
        // against. The insertion marker still says where the card is going.
        (0..2).forEach { seam ->
            (0 until 2).forEach { index ->
                assertEquals(0f, MakeRoom.shiftFor(index, seam, columns = 1, size = 2))
            }
        }
    }
}
