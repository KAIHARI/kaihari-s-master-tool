package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StableFitTest {

    private fun fitFor(count: Int, baseline: Int = 40): GridFit = GridFitter.stableFit(
        count = count,
        baselineCount = baseline,
        availableWidth = 1000f,
        availableHeight = 800f,
        spacing = 6f,
        aspectRatio = 59f / 86f,
        minColumns = 3,
        maxColumns = 20,
    )

    @Test
    fun anEmptySectionIsSizedLikeAFullOne() {
        // The whole point: card size must not depend on how many cards are in
        // yet, so the first card added lands in a 40-card-shaped grid.
        assertEquals(fitFor(40), fitFor(0))
        assertEquals(fitFor(40), fitFor(1))
        assertEquals(fitFor(40), fitFor(39))
    }

    @Test
    fun sizeOnlyChangesPastTheBaseline() {
        val atBaseline = fitFor(40)
        assertEquals(atBaseline, fitFor(40))

        // 41 cards need another row (or another column) — only now may the
        // grid zoom out.
        val past = fitFor(41)
        assertTrue(past.columns >= atBaseline.columns)
    }

    @Test
    fun sideAndExtraUseTheirOwnBaseline() {
        assertEquals(fitFor(15, baseline = 15), fitFor(0, baseline = 15))
        assertEquals(fitFor(15, baseline = 15), fitFor(14, baseline = 15))
    }
}
