package com.kaiharimoto.mastertool.core.visual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun blackOnWhiteIsAsFarApartAsColoursGet() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.001)
        assertEquals(21.0, Contrast.ratio(white, black), 0.001, "the question is symmetric")
    }

    @Test
    fun aColourAgainstItselfIsNoContrastAtAll() {
        assertEquals(1.0, Contrast.ratio(black, black), 1e-9)
        assertEquals(1.0, Contrast.ratio(0xFF16203A.toInt(), 0xFF16203A.toInt()), 1e-9)
    }

    @Test
    fun theEndsOfTheScaleAreWhereTheyShouldBe() {
        assertEquals(0.0, Contrast.relativeLuminance(black), 1e-9)
        assertEquals(1.0, Contrast.relativeLuminance(white), 1e-9)
    }

    @Test
    fun greenCarriesTheBrightnessAndBlueCarriesAlmostNone() {
        // Not the average of the channels, which is the mistake that makes white
        // text look fine on pure blue in a calculation and unreadable on screen.
        val red = Contrast.relativeLuminance(0xFFFF0000.toInt())
        val green = Contrast.relativeLuminance(0xFF00FF00.toInt())
        val blue = Contrast.relativeLuminance(0xFF0000FF.toInt())

        assertEquals(0.2126, red, 1e-6)
        assertEquals(0.7152, green, 1e-6)
        assertEquals(0.0722, blue, 1e-6)
        assertTrue(green > red && red > blue)
    }

    @Test
    fun aPublishedPairComesOutAtItsPublishedRatio() {
        // #767676 on white is the shade every guideline uses as its example of
        // exactly passing, which makes it the right thing to check the transfer
        // curve against.
        assertEquals(4.54, Contrast.ratio(0xFF767676.toInt(), white), 0.01)
    }

    @Test
    fun theAlphaChannelIsNotPartOfTheAnswer() {
        // Colours arrive as packed ARGB and the top byte is not a colour. A
        // formula that let it in would read a translucent black as a light grey.
        assertEquals(
            Contrast.relativeLuminance(0xFF3B82F6.toInt()),
            Contrast.relativeLuminance(0x003B82F6),
            1e-12,
        )
    }

    @Test
    fun theThresholdsAreTheOnesGuidelinesUse() {
        assertEquals(4.5, Contrast.READABLE)
        assertEquals(3.0, Contrast.READABLE_LARGE)
    }
}
