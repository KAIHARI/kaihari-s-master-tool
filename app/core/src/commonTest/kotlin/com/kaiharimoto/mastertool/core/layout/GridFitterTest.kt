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
    fun touchingCardsUseTheWholeWidth() {
        // The default gutter is zero, so nothing is subtracted for gaps and the row
        // is exactly the cards. Getting this wrong shrinks every card by a hair per
        // column, which compounds across ten of them.
        assertEquals(100f, GridFitter.requiredHeight(3, 3, 300f, 0f, 1f), 0.01f)
        assertEquals(200f, GridFitter.requiredHeight(6, 3, 300f, 0f, 1f), 0.01f)
    }

    @Test
    fun closingTheGutterWidensEveryCard() {
        // The point of a zero gutter: at a given column count the width the gaps
        // were taking goes to the cards instead.
        (3..20).forEach { columns ->
            val spaced = GridFitter.requiredHeight(60, columns, 900f, 6f, aspect)
            val touching = GridFitter.requiredHeight(60, columns, 900f, 0f, aspect)
            assertTrue(touching > spaced, "$columns columns did not grow when the gutter closed")
        }
    }

    @Test
    fun closingTheGutterCanCostAColumn() {
        // Not the intuition, and worth pinning down. Wider cards are also taller,
        // and past a certain row count that growth outweighs the row gaps removed:
        // at 13 columns a near-full deck grows past the pane once the gutter goes,
        // so the fitter correctly reaches for a fourteenth.
        assertEquals(13, fit(count = 55, spacing = 6f).columns)
        assertEquals(14, fit(count = 55, spacing = 0f).columns)
    }

    @Test
    fun theChosenGridFitsWithNoGutterToo() {
        // Same contract as `theChosenGridActuallyFitsTheSpace`, at the spacing the
        // app actually ships with.
        (1..60).forEach { count ->
            val result = fit(count, spacing = 0f)
            if (result.fits) {
                val needed = GridFitter.requiredHeight(count, result.columns, 900f, 0f, aspect)
                assertTrue(needed <= 500f, "$count cards in ${result.columns} columns overflowed")
            }
        }
    }

    @Test
    fun swappingBetweenSensibleSizesIsStable() {
        // Recomputed on every layout pass, so the same inputs must always give the
        // same answer — a grid that alternates between two column counts would
        // never settle.
        repeat(5) { assertEquals(fit(count = 41).columns, fit(count = 41).columns) }
    }
}

/**
 * The card-width formula, which two things now share.
 *
 * The fitter uses it to decide whether a section fits; the empty pane uses it to
 * draw slots at the size cards will actually be. If those two ever disagreed it
 * would show as ghost outlines the first real card does not sit inside.
 */
class CardWidthTest {

    @Test
    fun oneColumnTakesTheWholeWidth() {
        assertEquals(400f, GridFitter.cardWidth(400f, columns = 1, spacing = 6f))
    }

    @Test
    fun gutterIsSharedBetweenTheCards() {
        // Four cards have three gutters between them, not four.
        assertEquals(97f, GridFitter.cardWidth(400f, columns = 4, spacing = 4f))
    }

    @Test
    fun closingTheGutterGivesTheWidthBack() {
        assertEquals(100f, GridFitter.cardWidth(400f, columns = 4, spacing = 0f))
    }

    @Test
    fun nonsenseIsZeroRatherThanInfinite() {
        assertEquals(0f, GridFitter.cardWidth(400f, columns = 0, spacing = 4f))
        assertEquals(0f, GridFitter.cardWidth(400f, columns = -2, spacing = 4f))
    }

    @Test
    fun aGutterWiderThanTheSpaceGoesNegativeRatherThanPretending() {
        // The fitter checks for this and gives up; silently clamping to zero here
        // would make it think a card of no width fits.
        assertTrue(GridFitter.cardWidth(10f, columns = 5, spacing = 20f) < 0f)
    }

    @Test
    fun theHeightCalculationUsesTheSameWidth() {
        val width = GridFitter.cardWidth(500f, columns = 5, spacing = 3f)
        val oneRow = GridFitter.requiredHeight(5, 5, 500f, spacing = 3f, aspectRatio = 0.5f)

        assertEquals(width / 0.5f, oneRow, 0.001f)
    }
}

/**
 * The whole deck at one card size.
 *
 * Three sections sized independently would give the Side deck's fifteen cards a
 * different scale from the Main's forty, and a deck laid out at two scales does
 * not read as one deck. So they share a column count, and it is the fewest that
 * lets everything fit.
 */
class FitAllTest {

    private fun fit(
        counts: List<Int>,
        width: Float = 1200f,
        height: Float = 800f,
        gaps: Float = 0f,
    ) = GridFitter.fitAll(
        counts = counts,
        availableWidth = width,
        availableHeight = height,
        spacing = 0f,
        gaps = gaps,
        aspectRatio = 59f / 86f,
        minColumns = 3,
        maxColumns = 20,
    )

    @Test
    fun everythingHasToFitNotJustTheBiggestBlock() {
        // The Main deck alone would fit at fewer columns; the Extra and Side sit
        // under it and are competing for the same height.
        val mainOnly = fit(listOf(40))
        val wholeDeck = fit(listOf(40, 15, 15))

        assertTrue(
            wholeDeck.columns >= mainOnly.columns,
            "adding cards below cannot let the cards get bigger",
        )
    }

    @Test
    fun anEmptySectionCostsNothing() {
        // A deck with no Side deck must lay out exactly like one that has no
        // Side deck section at all, rather than paying for an absent row.
        assertEquals(fit(listOf(40, 15)).columns, fit(listOf(40, 15, 0)).columns)
    }

    @Test
    fun everySectionRoundsUpToAWholeRowOfItsOwn() {
        // The sections do not share rows: 41 cards in 8 columns is 6 rows, and a
        // 1-card Extra deck under it is a 7th, not a card tucked into the gap.
        val separate = fit(listOf(41, 1), height = 500f)
        val combined = fit(listOf(42), height = 500f)

        assertTrue(separate.columns >= combined.columns)
    }

    @Test
    fun theGapsComeOutOfTheSameBudget() {
        val tight = fit(listOf(40, 15, 15), height = 620f, gaps = 0f)
        val withGaps = fit(listOf(40, 15, 15), height = 620f, gaps = 120f)

        assertTrue(withGaps.columns >= tight.columns)
    }

    @Test
    fun nothingToLayOutFitsTrivially() {
        assertTrue(fit(emptyList()).fits)
        assertTrue(fit(listOf(0, 0, 0)).fits)
        assertEquals(3, fit(listOf(0, 0, 0)).columns)
    }

    @Test
    fun aDeckThatCannotFitSaysSoAtTheSmallestCards() {
        val impossible = fit(listOf(60, 15, 15), height = 40f)

        assertFalse(impossible.fits)
        assertEquals(20, impossible.columns)
    }

    @Test
    fun gapsBiggerThanTheSpaceDoNotLoop() {
        val result = fit(listOf(40), height = 100f, gaps = 400f)

        assertFalse(result.fits)
        assertEquals(20, result.columns)
    }

    @Test
    fun nonsenseDimensionsAreRefused() {
        assertFalse(fit(listOf(40), width = 0f).fits)
        assertFalse(fit(listOf(40), height = 0f).fits)
    }

    @Test
    fun oneSectionAgreesWithTheOrdinaryFitter() {
        // fitAll is a generalisation, not a second implementation, and this is
        // what says so.
        listOf(1, 12, 40, 60).forEach { count ->
            assertEquals(
                GridFitter.fit(count, 1200f, 800f, 0f, 59f / 86f, 3, 20).columns,
                fit(listOf(count)).columns,
                "$count cards",
            )
        }
    }
}
