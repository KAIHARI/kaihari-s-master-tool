package com.kaiharimoto.mastertool.core.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mark that makes the object on the desk the Millennium Puzzle.
 *
 * There is nothing to assert about whether it *looks* like an eye — that is what
 * a picture is for, and one was taken. What is here is the contract the drawing
 * has with the surface it is drawn on: six marks, all of them on the facet.
 */
class WdjatTest {

    @Test
    fun itIsTheSixMarksOfAWedjat() {
        // The outer half, the pupil, the brow, the inner half, the curl and the
        // teardrop — 1/2, 1/4, 1/8, 1/16, 1/32, 1/64, which is 63/64 and is the
        // reason the symbol has the parts it has.
        assertEquals(6, Wdjat.parts().size)
        Wdjat.parts().forEachIndexed { index, part ->
            assertTrue(part.size >= 8, "mark $index is ${part.size} points, which is not a curve")
        }
    }

    @Test
    fun everyMarkStaysOnTheFaceItIsCastInto() {
        // It is drawn through `Homography.squareToQuad` onto one facet of the
        // pyramid, so a point outside the unit square is a point outside the
        // facet — a stripe of eye lying across the desk beside it, and one that
        // moves as the camera turns.
        Wdjat.parts().forEachIndexed { index, part ->
            part.forEach { at ->
                assertTrue(
                    at.x in 0f..1f && at.y in 0f..1f,
                    "mark $index reaches (${at.x}, ${at.y}), off the facet",
                )
            }
        }
    }

    @Test
    fun itFillsTheFaceWithoutTouchingItsEdges() {
        // A mark that runs to the edge of a face reads as a texture the face is
        // wrapped in; one with a margin reads as a mark cast *into* it.
        val all = Wdjat.parts().flatten()
        val left = all.minOf { it.x }
        val right = all.maxOf { it.x }
        val top = all.minOf { it.y }
        val bottom = all.maxOf { it.y }
        assertTrue(left > Wdjat.MARGIN - 1e-3f && right < 1f - Wdjat.MARGIN + 1e-3f, "the eye runs to the sides: $left..$right")
        assertTrue(top > Wdjat.MARGIN - 1e-3f && bottom < 1f - Wdjat.MARGIN + 1e-3f, "the eye runs to the ends: $top..$bottom")
        assertTrue(right - left > 0.5f, "the eye is lost on the face: only ${right - left} of it")
    }
}
