package com.kaiharimoto.mastertool.core.visual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoilMathTest {

    // A deck tile and the preview of the same card, which must shade alike.
    private val tileW = 68f
    private val tileH = 99f
    private val previewW = 280f
    private val previewH = 408f

    private fun tile(x: Float, y: Float) = FoilMath.shade(x, y, tileW, tileH)

    @Test
    fun aPointerDeadCentreLeavesTheCardFlat() {
        val shading = tile(tileW / 2f, tileH / 2f)

        assertEquals(135f, shading.gradientAngleDegrees, 0.001f)
        assertEquals(0f, shading.rotationXDegrees, 0.001f)
        assertEquals(0f, shading.rotationYDegrees, 0.001f)
        assertEquals(0.5f, shading.specularX, 0.001f)
        assertEquals(0.5f, shading.specularY, 0.001f)
    }

    @Test
    fun theBandSwingsFortyDegreesEitherWay() {
        // Steepest at the top-right corner, shallowest at the bottom-left.
        assertEquals(175f, tile(tileW, 0f).gradientAngleDegrees, 0.001f)
        assertEquals(95f, tile(0f, tileH).gradientAngleDegrees, 0.001f)
    }

    @Test
    fun theHighlightSitsOppositeThePointer() {
        // The reflection is on the far side from where you are leaning, which is
        // what stops it reading as a blob stuck to the cursor.
        val topLeft = tile(0f, 0f)
        assertEquals(1f, topLeft.specularX, 0.001f)
        assertEquals(1f, topLeft.specularY, 0.001f)

        val bottomRight = tile(tileW, tileH)
        assertEquals(0f, bottomRight.specularX, 0.001f)
        assertEquals(0f, bottomRight.specularY, 0.001f)
    }

    @Test
    fun everyCardTiltsTheSameNoMatterHowLargeItIsDrawn() {
        // The original derived tilt from raw pixel offsets, so a 280dp preview
        // would have tilted four times as far as a 68px deck tile. Same point on
        // the card has to mean the same tilt.
        listOf(0f to 0f, 0.25f to 0.8f, 1f to 1f).forEach { (u, v) ->
            val small = FoilMath.shade(u * tileW, v * tileH, tileW, tileH)
            val large = FoilMath.shade(u * previewW, v * previewH, previewW, previewH)

            assertEquals(small.rotationXDegrees, large.rotationXDegrees, 0.001f)
            assertEquals(small.rotationYDegrees, large.rotationYDegrees, 0.001f)
            assertEquals(small.gradientAngleDegrees, large.gradientAngleDegrees, 0.001f)
        }
    }

    @Test
    fun leaningOnTheBottomOfACardTipsItsTopAway() {
        assertTrue(tile(tileW / 2f, tileH).rotationXDegrees > 0f)
        assertTrue(tile(tileW / 2f, 0f).rotationXDegrees < 0f)
    }

    @Test
    fun leaningOnTheRightOfACardPushesThatSideAway() {
        assertTrue(tile(tileW, tileH / 2f).rotationYDegrees < 0f)
        assertTrue(tile(0f, tileH / 2f).rotationYDegrees > 0f)
    }

    // The tint window is exactly the angle range the pointer can produce, so the
    // two corners that reach the extremes land on the pure colours.

    @Test
    fun theShallowCornerIsPink() {
        val pink = tile(0f, tileH)
        assertEquals(1f, pink.specularRed, 0.001f)
        assertEquals(182f / 255f, pink.specularGreen, 0.001f)
        assertEquals(193f / 255f, pink.specularBlue, 0.001f)
    }

    @Test
    fun theSteepCornerIsCyan() {
        val cyan = tile(tileW, 0f)
        assertEquals(0f, cyan.specularRed, 0.001f)
        assertEquals(1f, cyan.specularGreen, 0.001f)
        assertEquals(1f, cyan.specularBlue, 0.001f)
    }

    @Test
    fun theTintCrossesFromPinkToCyanWithoutDoublingBack() {
        // Hue tracking the tilt is what reads as foil; a tint that reversed
        // partway would read as a light flickering.
        var previous = 2f
        (0..20).forEach { step ->
            // Bottom-left to top-right, the axis the angle actually varies along.
            val t = step / 20f
            val shading = tile(t * tileW, (1f - t) * tileH)
            assertTrue(
                shading.specularRed <= previous + 0.001f,
                "red rose again at $t",
            )
            previous = shading.specularRed
        }
    }

    @Test
    fun aPointerDraggedOffTheCardClampsToItsEdge() {
        // During a drag the pointer leaves the card it picked up. Extrapolating
        // would spin the band far past anything a real card does.
        assertEquals(tile(tileW, tileH).gradientAngleDegrees, tile(tileW * 4f, tileH * 9f).gradientAngleDegrees, 0.001f)
        assertEquals(tile(0f, 0f).gradientAngleDegrees, tile(-500f, -500f).gradientAngleDegrees, 0.001f)
        assertEquals(tile(0f, 0f).rotationXDegrees, tile(-500f, -500f).rotationXDegrees, 0.001f)
    }

    @Test
    fun anUnmeasuredCardIsDrawnFlat() {
        assertEquals(FoilShading.FLAT, FoilMath.shade(10f, 10f, 0f, 100f))
        assertEquals(FoilShading.FLAT, FoilMath.shade(10f, 10f, 100f, 0f))
    }

    // The idle sweep, which is the only foil a touch device ever sees: on a
    // tablet a card is only pointed at while a finger is covering it.

    @Test
    fun theIdleSweepStaysWithinWhatAPointerCouldDo() {
        (0..40).forEach { step ->
            val shading = FoilMath.sweep(step / 40f, tileW, tileH)
            assertTrue(
                shading.gradientAngleDegrees in 95f..175f,
                "sweep left the pointer's range at step $step",
            )
            assertTrue(shading.specularX in 0f..1f && shading.specularY in 0f..1f)
        }
    }

    @Test
    fun theIdleSweepLoopsCleanly() {
        // Rendered from an animation clock that runs past 1 and never resets, so
        // it has to be periodic and to have no seam at the wrap.
        assertEquals(
            FoilMath.sweep(0f, tileW, tileH),
            FoilMath.sweep(3f, tileW, tileH),
        )
        assertEquals(
            FoilMath.sweep(0.25f, tileW, tileH),
            FoilMath.sweep(-1.75f, tileW, tileH),
        )
    }

    @Test
    fun theIdleSweepNeverJumps() {
        // A visible step would read as a glitch rather than as a moving light.
        var previous = FoilMath.sweep(0f, tileW, tileH)
        (1..200).forEach { step ->
            val next = FoilMath.sweep(step / 200f, tileW, tileH)
            val jump = kotlin.math.abs(next.gradientAngleDegrees - previous.gradientAngleDegrees)
            assertTrue(jump < 2f, "the band jumped ${jump}deg at step $step")
            previous = next
        }
    }
}
