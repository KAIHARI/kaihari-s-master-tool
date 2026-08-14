package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That distance can take contrast, that it cannot take anything else, and that
 * the dial offering it means the same thing wherever you are sitting.
 *
 * The last of those is new and is most of this file. Defocus shipped switched
 * off and, when switched on, very nearly invisible — and both halves of that
 * were the same fault twice: a focus plane and a falloff both quoted against the
 * *camera distance* when the thing they are about is the depth of the **board**.
 * The board is a twenty-seventh of the camera distance deep at the reading seat
 * and a third of it at the POV chair, so one constant could not be right at two
 * seats, and the shipped one was right at none.
 *
 * The rest are about the effect being *absent*: off by default, off where the
 * camera has not been told a surface yet, off for a card at the focus plane, and
 * off for every nonsense number a slider or a settings file can produce — each
 * of which is a way the picture could silently change when nobody asked it to.
 */
class DefocusTest {

    private val width = 1600f
    private val height = 1000f
    private val cameraDistance = 1450f

    /** The board's own half-depth, which is what everything here is quoted in. */
    private val reach = 0.1236f

    private fun haze(
        depth: Float,
        focus: Float = 0f,
        fNumber: Float = Defocus.REFERENCE_F,
        strength: Float = 0.12f,
        reach: Float = this.reach,
    ) = Defocus.hazeAt(depth, cameraDistance, reach, focus, fNumber, strength)

    /** A depth, given as a fraction of the board's own reach. */
    private fun across(fraction: Float) = fraction * reach * cameraDistance

    private fun planeOf(seat: StageSeat): StagePlane = seat.pose.planeFor(width, height)

    // ---- off unless asked -----------------------------------------------------------

    @Test
    fun zeroStrengthIsExactlyNothing() {
        // The default, and the property that lets this ship without changing a
        // single pixel of anybody's stage.
        listOf(-600f, -100f, 0f, 100f, 600f).forEach {
            assertEquals(0f, Defocus.hazeAt(it, cameraDistance, reach, 0f, 8f, 0f), "at depth $it")
        }
    }

    @Test
    fun aStageWithNoCameraYetIsLeftAlone() {
        // `StagePlane.reaches` guards the same case for the same reason: the
        // first composition of the play screen builds a plane with no surface
        // behind it, and dividing by its camera distance is dividing by zero.
        assertEquals(0f, Defocus.hazeAt(200f, 0f, reach, 0f, 8f, 0.2f))
        assertEquals(0f, Defocus.hazeAt(200f, -5f, reach, 0f, 8f, 0.2f))
    }

    @Test
    fun aTableWithNoDepthAtAllHasNothingToDefocus() {
        // A reach of nothing is a table seen exactly flat on — `depthReach` at a
        // pitch of zero — and it is also the same first-composition plane. Both
        // want no haze rather than a division.
        assertEquals(0f, Defocus.hazeAt(200f, cameraDistance, 0f, 0f, 8f, 0.4f))
        assertEquals(0f, Defocus.hazeAt(200f, cameraDistance, -1f, 0f, 8f, 0.4f))
    }

    @Test
    fun aCardAtTheFocusPlaneIsUntouchedAndSoIsItsNeighbour() {
        // The dead zone. Without it every card on the table is fractionally
        // hazed, which does not read as a shallow depth of field — it reads as
        // a dirty screen. It is a fraction of the *board* now, so it is a hair
        // at every seat rather than a tenth of the table at one and half of it
        // at another.
        assertEquals(0f, haze(0f))
        assertEquals(0f, haze(across(Defocus.GATE * 0.9f)))
        assertTrue(haze(across(Defocus.GATE * 4f)) > 0f)
    }

    // ---- and monotone when it is ------------------------------------------------------

    @Test
    fun itGrowsWithDistanceFromTheFocusPlaneInBothDirections() {
        val near = (0..8).map { haze(across(0.1f + it * 0.1f)) }
        near.zipWithNext { closer, further ->
            assertTrue(further >= closer, "not monotone going near: $near")
        }
        // A card in front of the plane defocuses as much as one behind it, which
        // is what an aperture does and is why the term is an absolute value.
        assertEquals(haze(across(0.5f)), haze(across(-0.5f)))
    }

    @Test
    fun aWiderApertureFallsAwayFaster() {
        val depth = across(0.6f)

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
        //
        // The dial runs −1..+1 across the board's own depth now, so a card at
        // eight tenths of the way to the far corner is at −0.8 on the dial —
        // which is the arithmetic this test caught the first time it ran, when
        // the two scales differed by a factor of four.
        val far = across(-0.8f)

        assertTrue(haze(far, focus = 0f) > 0f, "the far card was already sharp")
        assertEquals(0f, haze(far, focus = -0.8f), "putting the plane on it did not sharpen it")
        assertTrue(haze(0f, focus = -0.8f) > 0f, "the middle did not go soft")
    }

    @Test
    fun itNeverExceedsTheStrengthItWasGiven() {
        // The alpha is what a person set on a dial, and a card at the far end of
        // the stage must not come back more hazed than the dial says.
        listOf(0.05f, 0.12f, 0.5f).forEach { strength ->
            listOf(1f, 400f, 5000f, 100000f).forEach { depth ->
                val value = Defocus.hazeAt(depth, cameraDistance, reach, 0f, 2f, strength)
                assertTrue(value <= strength + 1e-6f, "$value over $strength at depth $depth")
                assertTrue(value >= 0f)
            }
        }
    }

    // ---- the calibration, which is the whole repair -------------------------------------

    /**
     * The far corner of the board reads the same at every seat.
     *
     * The defining claim. `reach` divides out, so the haze at the end of the
     * board is a function of the aperture and the dial alone — which is what
     * makes this an *instrument* rather than a number that happened to look
     * right at the seat somebody tuned it from.
     *
     * The four reaches here differ by a factor of twelve (0.027 overhead against
     * 0.319 at the POV chair), and the answer is identical to the last bit.
     */
    @Test
    fun theFarCornerOfTheBoardReadsTheSameAtEverySeat() {
        val answers = StageSeat.entries.map { seat ->
            val plane = planeOf(seat)
            val reach = plane.depthReach
            assertTrue(reach > 0f, "${seat.name} has no depth at all")
            Defocus.hazeAt(
                depth = -reach * plane.cameraDistance,
                cameraDistance = plane.cameraDistance,
                reach = reach,
                focus = 0f,
                fNumber = 8f,
                strength = 0.4f,
            )
        }
        answers.zipWithNext { a, b ->
            assertTrue(abs(a - b) < 1e-6f, "the seats disagree about the far corner: $answers")
        }
        assertTrue(answers.first() > 0.05f, "and it is not merely equally invisible: $answers")
    }

    /**
     * The widest stop the dial offers just saturates at the far corner.
     *
     * The calibration, said as a test. Every other stop follows from it by the
     * reciprocal of the f-number, so this one number fixes the whole scale — and
     * pinning it at the *end* of the range is what stops the old failure, where
     * a gradient quoted at f/8 was read off a far-edge fraction somebody chose
     * and turned out to be nine per cent at one seat and one per cent at
     * another.
     */
    @Test
    fun theWidestStopJustSaturatesAtTheFarCorner() {
        StageSeat.entries.forEach { seat ->
            val plane = planeOf(seat)
            val reach = plane.depthReach
            val corner = Defocus.hazeAt(
                depth = -reach * plane.cameraDistance,
                cameraDistance = plane.cameraDistance,
                reach = reach,
                focus = 0f,
                fNumber = Defocus.WIDE_F,
                strength = 1f,
            )
            assertEquals(1f, corner, 1e-5f, "f/${Defocus.WIDE_F} at ${seat.name}")

            // And it is only *just*: three quarters of the way out it is still
            // short of the ceiling, so the aperture has somewhere to go.
            val partway = Defocus.hazeAt(
                depth = -reach * plane.cameraDistance * 0.75f,
                cameraDistance = plane.cameraDistance,
                reach = reach,
                focus = 0f,
                fNumber = Defocus.WIDE_F,
                strength = 1f,
            )
            assertTrue(partway < 0.95f, "it saturates before the corner at ${seat.name}: $partway")
        }
    }

    @Test
    fun theShippedStopIsAQuarterOfThat() {
        // f/8 against f/2, so the depth of field goes as the reciprocal of the
        // f-number — which is not a metaphor here, it is what an f-number is.
        val plane = planeOf(StageSeat.SEATED)
        val reach = plane.depthReach
        val corner = { f: Float ->
            Defocus.hazeAt(-reach * plane.cameraDistance, plane.cameraDistance, reach, 0f, f, 1f)
        }
        assertEquals(0.25f, corner(8f), 1e-5f, "f/8")
        assertEquals(0.5f, corner(4f), 1e-5f, "f/4")
        assertEquals(1f / 11f, corner(22f), 1e-5f, "f/22")
    }

    /**
     * And the whole dial does something, at the seat with the least depth to
     * work with.
     *
     * The regression this file exists for. Overhead is the hardest case — a
     * board 0.027 of the camera distance deep — and the old arithmetic put its
     * far corner at **one and a half per cent** of the dial there, which is a
     * knob nobody could tell was connected.
     */
    @Test
    fun everySeatGetsAUsableDialAndNotJustTheOneItWasTunedFrom() {
        StageSeat.entries.forEach { seat ->
            val plane = planeOf(seat)
            val reach = plane.depthReach
            val corner = Defocus.hazeAt(
                depth = -reach * plane.cameraDistance,
                cameraDistance = plane.cameraDistance,
                reach = reach,
                focus = 0f,
                fNumber = 4f,
                strength = 0.4f,
            )
            assertTrue(corner > 0.15f, "${seat.name} still has a dial that does nothing: $corner")

            // And the far half of the *dial* still moves something, rather than
            // pushing the plane past the end of the table.
            val sharpened = Defocus.hazeAt(
                depth = -reach * plane.cameraDistance,
                cameraDistance = plane.cameraDistance,
                reach = reach,
                focus = -1f,
                fNumber = 4f,
                strength = 0.4f,
            )
            assertEquals(0f, sharpened, "the end of the dial did not reach the far corner at ${seat.name}")
        }
    }

    // ---- and nothing hostile gets through ----------------------------------------------

    @Test
    fun everyNonsenseNumberComesBackAsNoHazeRatherThanAsNaN() {
        // An alpha of NaN reaches `Color.copy(alpha = …)` and from there a draw
        // call, and the failure is a card that does not appear rather than a
        // stack trace.
        val bad = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        bad.forEach { value ->
            assertTrue(Defocus.hazeAt(value, cameraDistance, reach, 0f, 8f, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, value, 0f, 8f, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, reach, value, 8f, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, reach, 0f, value, 0.2f).isFinite())
            assertTrue(Defocus.hazeAt(200f, cameraDistance, reach, 0f, 8f, value).isFinite())
        }
        // And an aperture of nothing is not a divide by nothing.
        assertTrue(Defocus.hazeAt(200f, cameraDistance, reach, 0f, 0f, 0.2f).isFinite())
    }

    @Test
    fun aFocusPlaneOffTheEndOfTheDialIsClampedRatherThanObeyed() {
        // Otherwise a pasted document with focus = 40 puts the sharp plane
        // somewhere the stage cannot hold geometry, and the whole board hazes.
        assertEquals(haze(0f, focus = 1f), haze(0f, focus = 90f))
        assertEquals(haze(0f, focus = -1f), haze(0f, focus = -90f))
    }
}
