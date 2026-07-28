package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeScrollTest {

    // A Main deck pane roughly the height it gets on a landscape tablet.
    private val top = 100f
    private val bottom = 600f
    private val max = 2000f

    private fun at(y: Float) = EdgeScroll.velocity(y, top, bottom, max)

    @Test
    fun theMiddleOfAPaneDoesNotScroll() {
        assertEquals(0f, at(350f))
        assertEquals(0f, at(300f))
        assertEquals(0f, at(400f))
    }

    @Test
    fun theTopEdgeScrollsBackAndTheBottomEdgeScrollsOn() {
        assertTrue(at(top + 2f) < 0f)
        assertTrue(at(bottom - 2f) > 0f)
    }

    @Test
    fun speedRisesWithDepthIntoTheZone() {
        var previous = 0f
        // From the neutral middle out to the very edge.
        (0..40).forEach { step ->
            val y = bottom - (40 - step)
            val speed = at(y)
            assertTrue(speed >= previous - 0.001f, "speed fell at y=$y")
            previous = speed
        }
    }

    @Test
    fun theZoneStartsAlmostStill() {
        // The point of the squared ramp: entering the band while aiming at the
        // last row must not drag the pane out from under you.
        val height = bottom - top
        val zone = minOf(height * EdgeScroll.ZONE_FRACTION, EdgeScroll.MAX_ZONE, height / 3f)
        val justInside = at(bottom - zone * 0.95f)

        assertTrue(justInside > 0f, "the zone should be live at its outer edge")
        assertTrue(justInside < max * 0.01f, "but barely moving: was $justInside")
    }

    @Test
    fun theEdgeItselfScrollsAtFullSpeed() {
        assertEquals(max, at(bottom), 0.001f)
        assertEquals(-max, at(top), 0.001f)
    }

    @Test
    fun draggingPastAPaneDoesNotScrollFasterThanFullSpeed() {
        // Carrying a card towards the next pane crosses this one's edge. Speeding
        // up out there would fling the section while you were leaving it.
        assertEquals(max, at(bottom + 400f), 0.001f)
        assertEquals(-max, at(top - 400f), 0.001f)
    }

    @Test
    fun theTwoZonesAreMirrorImages() {
        (1..20).forEach { step ->
            val depth = step * 4f
            assertEquals(abs(at(top + depth)), abs(at(bottom - depth)), 0.001f)
        }
    }

    @Test
    fun aShortPaneKeepsSomewhereNeutralToAimAt() {
        // Both zones capped at a third of the height, so a collapsed-ish pane
        // does not scroll no matter where in it the card is held.
        val shortTop = 0f
        val shortBottom = 60f
        val middle = EdgeScroll.velocity(30f, shortTop, shortBottom, max)

        assertEquals(0f, middle)
    }

    @Test
    fun aTallPaneOnlyScrollsNearItsEdges() {
        // MAX_ZONE is what stops a very tall Main pane scrolling from its middle.
        val tallBottom = 2000f
        assertEquals(0f, EdgeScroll.velocity(1000f, 0f, tallBottom, max))
        assertTrue(EdgeScroll.velocity(tallBottom - 10f, 0f, tallBottom, max) > 0f)
    }

    @Test
    fun anUnmeasuredPaneDoesNotScroll() {
        // Panes register their bounds on layout; before that there is nothing to
        // scroll and a divide by zero waiting.
        assertEquals(0f, EdgeScroll.velocity(50f, 0f, 0f, max))
        assertEquals(0f, EdgeScroll.velocity(50f, 600f, 100f, max))
        assertEquals(0f, EdgeScroll.velocity(50f, 0f, 600f, 0f))
    }
}
