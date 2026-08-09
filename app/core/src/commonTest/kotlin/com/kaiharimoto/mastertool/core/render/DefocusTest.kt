package com.kaiharimoto.mastertool.core.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That distance can take contrast, and cannot take anything else.
 *
 * Most of these are about the effect being *absent*. It is off by default, off
 * where the camera has not been told a surface yet, off for a card at the focus
 * plane, and off for every nonsense number a slider or a settings file can
 * produce — and each of those is a way the picture could silently change when
 * nobody asked it to.
 */
class DefocusTest {

    private val cameraDistance = 1450f

    private fun haze(
        depth: Float,
        focus: Float = 0f,
        fNumber: Float = Defocus.REFERENCE_F,
        strength: Float = 0.12f,
    ) = Defocus.hazeAt(depth, cameraDistance, focus, fNumber, strength)

    // ---- off unless asked -----------------------------------------------------------

    @Test
    fun zeroStrengthIsExactlyNothing() {
        // The default, and the property that lets this ship without changing a
        // single pixel of anybody's stage.
        listOf(-600f, -100f, 0f, 100f, 600f).forEach {
            assertEquals(0f, Defocus.hazeAt(it, cameraDistance, 0f, 8f, 0f), "at depth $it")
        }
    }

    @Test
    fun aStageWithNoCameraYetIsLeftAlone() {
        // `StagePlane.reaches` guards the same case for the same reason: the
        // first composition of the play screen builds a plane with no surface
        // behind it, and dividing by its camera distance is dividing by zero.
        assertEquals(0f, Defocus.hazeAt(200f, 0f, 0f, 8f, 0.2f))
        assertEquals(0f, Defocus.hazeAt(200f, -5f, 0f, 8f, 0.2f))
    }

    @Test
    fun aCardAtTheFocusPlaneIsUntouchedAndSoIsItsNeighbour() {
        // The dead zone. Without it every card on the table is fractionally
        // hazed, which does not read as a shallow depth of field — it reads as
        // a dirty screen.
        assertEquals(0f, haze(0f))
        assertEquals(0f, haze(cameraDistance * Defocus.GATE * 0.9f))
        assertTrue(haze(cameraDistance * Defocus.GATE * 4f) > 0f)
    }

    // ---- and monotone when it is ------------------------------------------------------

    @Test
    fun itGrowsWithDistanceFromTheFocusPlaneInBothDirections() {
        val near = (0..8).map { haze(cameraDistance * (0.02f + it * 0.02f)) }
        near.zipWithNext { closer, further ->
            assertTrue(further >= closer, "not monotone going near: $near")
        }
        // A card in front of the plane defocuses as much as one behind it, which
        // is what an aperture does and is why the term is an absolute value.
        assertEquals(haze(300f), haze(-300f))
    }

    @Test
    fun aWiderApertureFallsAwayFaster() {
        val depth = cameraDistance * 0.1f

        val deep = haze(depth, fNumber = 22f)
        val middling = haze(depth, fNumber = 8f)
        val wide = haze(depth, fNumber = 2f)

        assertTrue(wide > middling && middling > deep, "$wide / $middling / $deep")
    }

    @Test
    fun movingTheFocusPlaneMovesWhatIsSharp() {
        // The claim that makes the dial worth having: a card that was hazed
        // becomes sharp when the plane is put on it, and one that was sharp
        // stops being.
        // The dial runs -1..+1 across the *reachable* depth, which is half the
        // camera distance either way — so a card at -0.2 of the camera distance
        // is at -0.4 on the dial. Writing that out because getting it wrong is
        // what this test caught the first time it ran.
        val far = -cameraDistance * 0.2f

        assertTrue(haze(far, focus = 0f) > 0f, "the far card was already sharp")
        assertEquals(0f, haze(far, focus = -0.4f), "putting the plane on it did not sharpen it")
        assertTrue(haze(0f, focus = -0.4f) > 0f, "the middle did not go soft")
    }

    @Test
    fun itNeverExceedsTheStrengthItWasGiven() {
        // The alpha is what a person set on a dial, and a card at the far end of
        // the stage must not come back more hazed than the dial says. Anything
        // above about a fifth stops a card being readable, which is the thing
        // this application exists to prevent.
        listOf(0.05f, 0.12f, 0.25f).forEach { strength ->
            listOf(1f, 400f, 5000f, 100000f).forEach { depth ->
                val value = Defocus.hazeAt(depth, cameraDistance, 0f, 2f, strength)
                assertTrue(value <= strength + 1e-6f, "$value over $strength at depth $depth")
                assertTrue(value >= 0f)
            }
        }
    }

    @Test
    fun theDefaultApertureIsDeepEnoughToHaveToLookForIt() {
        // f/8 on the board's own span at the seated seat: present, and not a
        // thing that happens to you. The span is measured in `docs/TUNING.md`.
        val acrossTheBoard = cameraDistance * 0.09f

        val subtle = Defocus.hazeAt(acrossTheBoard, cameraDistance, 0f, 8f, 1f)
        assertTrue(subtle in 0.05f..0.45f, "f/8 across the board came out $subtle")
    }

    // ---- and nothing hostile gets through ----------------------------------------------

    @Test
    fun everyNonsenseNumberComesBackAsNoHazeRatherThanAsNaN() {
        // An alpha of NaN reaches `Color.copy(alpha = …)` and from there a draw
        // call, and the failure is a card that does not appear rather than a
        // stack trace.
        val bad = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        bad.forEach { value ->
            assertTrue(Defocus.hazeAt(value, cameraDistance, 0f, 8f, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, value, 8f, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, 0f, value, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, 0f, 8f, value).isFinite())
        }
        // And an aperture of nothing is not a divide by nothing.
        assertTrue(Defocus.hazeAt(200f, cameraDistance, 0f, 0f, 0.2f).isFinite())
    }

    @Test
    fun aFocusPlaneOffTheEndOfTheDialIsClampedRatherThanObeyed() {
        // Otherwise a pasted document with focus = 40 puts the sharp plane
        // somewhere the stage cannot hold geometry, and the whole board hazes.
        assertEquals(haze(0f, focus = 1f), haze(0f, focus = 90f))
        assertEquals(haze(0f, focus = -1f), haze(0f, focus = -90f))
    }
}
