package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckFitterTest {

    private val aspect = 59f / 86f
    private val spacing = 6f
    private val chrome = 40f

    private fun requests(
        main: Int = 40,
        extra: Int = 15,
        side: Int = 15,
        mainCollapsed: Boolean = false,
    ) = listOf(
        SectionFitRequest(main, columns = 10, baselineCount = 40, collapsed = mainCollapsed, chromeHeight = chrome),
        SectionFitRequest(extra, columns = 15, baselineCount = 15, chromeHeight = chrome),
        SectionFitRequest(side, columns = 15, baselineCount = 15, chromeHeight = chrome),
    )

    private fun plan(
        width: Float = 780f,
        height: Float = 720f,
        main: Int = 40,
        extra: Int = 15,
        side: Int = 15,
        mainCollapsed: Boolean = false,
    ) = DeckFitter.plan(
        requests = requests(main, extra, side, mainCollapsed),
        availableWidth = width,
        availableHeight = height,
        spacing = spacing,
        aspectRatio = aspect,
        paneGap = 8f,
    )

    @Test
    fun theWholeDeckFitsTheColumnItWasGiven() {
        val fit = plan()

        assertTrue(fit.fits)
        assertTrue(fit.totalHeight <= 720f)
        // Four rows of ten, one row of fifteen twice — the shape of a decklist.
        assertEquals(listOf(4, 1, 1), fit.sections.map { it.rows })
    }

    @Test
    fun everySectionShrinksByTheSameFactor() {
        // Uniform scale is what keeps a main-deck card larger than an extra-deck
        // one. Scaling each section to its own space would end with the extra
        // deck — one row, all the room in the world — drawn biggest.
        val fit = plan(height = 400f)

        assertTrue(fit.scale < 1f)
        val (main, extra, side) = fit.sections
        assertEquals(extra.cardWidth, side.cardWidth, 0.01f)
        assertTrue(main.cardWidth > extra.cardWidth)

        val full = plan(height = 4000f)
        val ratio = main.cardWidth / full.sections[0].cardWidth
        assertEquals(ratio, extra.cardWidth / full.sections[1].cardWidth, 0.001f)
    }

    @Test
    fun aScaledGridIsNarrowerThanTheColumn() {
        val roomy = plan(height = 4000f)
        // Nothing to shrink for: the grids fill the width they were given.
        assertEquals(1f, roomy.scale)
        assertEquals(780f, roomy.sections[0].gridWidth, 0.01f)

        // Height-bound, so the cards are smaller and the grid no longer spans
        // the column — the caller centres what is left.
        val tight = plan(height = 420f)
        assertTrue(tight.sections[0].gridWidth < 780f)
    }

    @Test
    fun sizeIsHeldSteadyUntilTheDeckOutgrowsItsBaseline() {
        // A deck being built must not resize on every card added.
        assertEquals(plan(main = 40).scale, plan(main = 0).scale)
        assertEquals(plan(main = 40).scale, plan(main = 39).scale)

        // Forty-one needs a fifth row, and only now does everything zoom out.
        val past = plan(main = 41)
        assertEquals(5, past.sections[0].rows)
        assertTrue(past.scale < plan(main = 40).scale)
    }

    @Test
    fun aCollapsedSectionGivesItsSpaceToTheOthers() {
        // In a column too short to hold everything at full size, so there is
        // something for the collapse to hand over.
        val collapsed = plan(height = 400f, mainCollapsed = true)

        assertEquals(0, collapsed.sections[0].rows)
        assertEquals(chrome, collapsed.sections[0].paneHeight, 0.01f)
        assertTrue(collapsed.sections[1].cardWidth > plan(height = 400f).sections[1].cardWidth)
    }

    @Test
    fun paneHeightsAddUpToWhatWasReported() {
        val fit = plan(height = 500f)
        val sum = fit.sections.sumOf { it.paneHeight.toDouble() }.toFloat() + 8f * 2

        assertTrue(abs(sum - fit.totalHeight) < 0.01f)
    }

    @Test
    fun aSpaceNothingFitsInSaysSo() {
        // The column then scrolls rather than drawing cards nobody can read.
        val fit = plan(height = 120f)

        assertEquals(DeckFitter.MIN_SCALE, fit.scale)
        assertFalse(fit.fits)
    }

    @Test
    fun zeroWidthIsSurvivedRatherThanDividedBy() {
        val fit = plan(width = 0f)

        assertFalse(fit.fits)
        assertTrue(fit.sections.all { it.cardWidth == 0f })
    }
}
