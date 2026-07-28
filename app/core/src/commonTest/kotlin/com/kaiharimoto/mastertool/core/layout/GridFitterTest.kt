package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridFitterTest {

    // Real card proportions, and a pane roughly the shape the Main deck gets on a
    // landscape tablet.
    private val aspect = 59f / 86f

    private fun fit(
        count: Int,
        width: Float = 900f,
        height: Float = 500f,
        spacing: Float = 6f,
        min: Int = 3,
        max: Int = 20,
    ) = GridFitter.fit(count, width, height, spacing, aspect, min, max)

    @Test
    fun anEmptySectionUsesTheSmallestGridAndFits() {
        val result = fit(count = 0)
        assertEquals(3, result.columns)
        assertTrue(result.fits)
    }

    @Test
    fun aFewCardsGetTheFewestColumnsAndSoTheLargestCards() {
        assertEquals(3, fit(count = 3).columns)
    }

    @Test
    fun moreCardsNeverMeansFewerColumns() {
        var previous = 0
        (1..60).forEach { count ->
            val columns = fit(count).columns
            assertTrue(columns >= previous, "$count cards used fewer columns than ${count - 1}")
            previous = columns
        }
    }

    @Test
    fun aFullMainDeckStillFits() {
        val result = fit(count = 60)
        assertTrue(result.fits, "60 cards should fit in a 900x500 pane")
        assertTrue(result.columns in 3..20)
    }

    @Test
    fun theChosenGridActuallyFitsTheSpace() {
        // The contract: whenever `fits` is true, the maths really does land inside
        // the pane. Off-by-one here is a bottom row nobody can see.
        (1..60).forEach { count ->
            val result = fit(count)
            if (result.fits) {
                val needed = GridFitter.requiredHeight(count, result.columns, 900f, 6f, aspect)
                assertTrue(needed <= 500f, "$count cards in ${result.columns} columns overflowed")
            }
        }
    }

    @Test
    fun oneFewerColumnWouldNotHaveFit() {
        // Fewest columns means largest cards, so the answer has to be the first
        // one that works, not merely one that works.
        val result = fit(count = 40)
        if (result.columns > 3) {
            val needed = GridFitter.requiredHeight(40, result.columns - 1, 900f, 6f, aspect)
            assertTrue(needed > 500f, "could have used ${result.columns - 1} columns")
        }
    }

    @Test
    fun aShortPaneNeedsMoreColumnsThanATallOne() {
        val tall = fit(count = 40, height = 700f).columns
        val short = fit(count = 40, height = 220f).columns
        assertTrue(short > tall, "a shorter pane should pack the cards tighter")
    }

    @Test
    fun aPaneTooSmallForTheDeckReportsThatItDoesNotFit() {
        // The Side deck squeezed to a sliver: the answer is the tightest grid
        // available, and the caller is told it will still scroll.
        val result = fit(count = 60, height = 40f)
        assertEquals(20, result.columns)
        assertFalse(result.fits)
    }

    @Test
    fun theColumnCountStaysWithinTheBoundsGiven() {
        assertEquals(6, fit(count = 1, min = 6, max = 10).columns)
        assertEquals(10, fit(count = 60, height = 30f, min = 6, max = 10).columns)
    }

    @Test
    fun anUnmeasuredPaneDoesNotClaimToFit() {
        // Before the first layout pass there is nothing to fit into, and saying
        // otherwise would switch drag into its no-scroll mode too early.
        assertFalse(fit(count = 10, width = 0f).fits)
        assertFalse(fit(count = 10, height = 0f).fits)
    }

    @Test
    fun requiredHeightAccountsForTheGapsBetweenRows() {
        // Two rows of cards plus one gap, not two rows and nothing else.
        val oneRow = GridFitter.requiredHeight(3, 3, 300f, 10f, 1f)
        val twoRows = GridFitter.requiredHeight(6, 3, 300f, 10f, 1f)

        val cardWidth = (300f - 10f * 2) / 3
        assertEquals(cardWidth, oneRow, 0.01f)
        assertEquals(cardWidth * 2 + 10f, twoRows, 0.01f)
    }

    @Test
    fun swappingBetweenSensibleSizesIsStable() {
        // Recomputed on every layout pass, so the same inputs must always give the
        // same answer — a grid that alternates between two column counts would
        // never settle.
        repeat(5) { assertEquals(fit(count = 41).columns, fit(count = 41).columns) }
    }
}
