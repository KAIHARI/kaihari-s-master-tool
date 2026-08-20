package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The portrait deck column: width in, card size out, and scroll.
 *
 * [DeckFitter.plan] is the tablet's question — *how wide do the cards have to
 * be for the whole deck to fit this height* — and in a portrait window its
 * answer is a twenty-dp card. These are the tests for the other question.
 */
class DeckStackTest {

    private val aspect = 59f / 86f
    private val spacing = 2f
    private val chrome = 44f
    private val paneGap = 8f

    /** A 360dp phone, minus padding, with the pool docked at half height. */
    private fun stack(
        width: Float = 348f,
        height: Float = 330f,
        columns: Int = 6,
        main: Int = 40,
        extra: Int = 15,
        side: Int = 15,
        mainCollapsed: Boolean = false,
    ) = DeckFitter.stack(
        requests = listOf(
            SectionFitRequest(main, columns, baselineCount = 40, spacing = spacing, collapsed = mainCollapsed, chromeHeight = chrome),
            SectionFitRequest(extra, columns, baselineCount = 15, spacing = spacing, chromeHeight = chrome),
            SectionFitRequest(side, columns, baselineCount = 15, spacing = spacing, chromeHeight = chrome),
        ),
        availableWidth = width,
        availableHeight = height,
        aspectRatio = aspect,
        paneGap = paneGap,
    )

    @Test
    fun everySectionIsDrawnAtTheFullWidthItWasGiven() {
        val fit = stack()
        assertEquals(348f, fit.contentWidth)
        // One card size for the whole deck, which is the point of one column
        // count: 348 = 6·w + 5·2.
        val expected = (348f - spacing * 5) / 6f
        fit.sections.forEach { section ->
            assertTrue(abs(section.cardWidth - expected) < 0.01f, "${section.cardWidth}")
        }
    }

    @Test
    fun theCardIsBigEnoughToBeAPicture() {
        // The reason the portrait column scrolls rather than fitting: at six
        // across a card is 56dp wide, and the fitter's answer for the same
        // window is 23dp — under half of it, and under a third of the 59dp a
        // real card would need for its art to be anything but a swatch.
        val stacked = stack().sections.first().cardWidth
        val fitted = DeckFitter.plan(
            requests = listOf(
                SectionFitRequest(40, 10, baselineCount = 40, spacing = spacing, chromeHeight = chrome),
                SectionFitRequest(15, 15, baselineCount = 15, spacing = spacing, chromeHeight = chrome),
                SectionFitRequest(15, 15, baselineCount = 15, spacing = spacing, chromeHeight = chrome),
            ),
            availableWidth = 348f,
            availableHeight = 330f,
            aspectRatio = aspect,
            paneGap = paneGap,
        ).sections.first().cardWidth

        assertTrue(stacked > 2 * fitted, "stacked $stacked vs fitted $fitted")
        assertTrue(fitted < 25f, "fitted $fitted")
    }

    @Test
    fun rowsFollowFromTheDeckAndTheColumnScrolls() {
        val fit = stack()
        assertEquals(listOf(7, 3, 3), fit.sections.map { it.rows })
        assertFalse(fit.fits)
        assertTrue(fit.totalHeight > 330f)
    }

    @Test
    fun aDeckThatDoesFitSaysSo() {
        // Nothing but a side deck, and a tall window: the caller should centre
        // this rather than hand it a scroll container with nothing to scroll.
        val fit = stack(main = 0, extra = 0, side = 15, height = 900f, mainCollapsed = true)
        assertTrue(fit.fits)
    }

    @Test
    fun aCollapsedSectionCostsOnlyItsHeader() {
        val fit = stack(mainCollapsed = true)
        assertEquals(0, fit.sections[0].rows)
        assertEquals(0f, fit.sections[0].gridHeight)
        assertEquals(chrome, fit.sections[0].paneHeight)
    }

    @Test
    fun theBaselineStillHoldsTheEmptyDeckOpen() {
        // Same rule as the fitter: a new deck is sized for forty from the start
        // so nothing reflows until it outgrows it.
        assertEquals(stack(main = 40).sections[0].rows, stack(main = 0).sections[0].rows)
    }

    @Test
    fun aZeroWidthWindowIsNotADivideByZero() {
        val fit = stack(width = 0f)
        assertFalse(fit.fits)
        assertTrue(fit.sections.all { it.cardWidth == 0f })
    }
}
