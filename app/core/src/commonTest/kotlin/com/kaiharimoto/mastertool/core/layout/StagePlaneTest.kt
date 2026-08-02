package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StagePlaneTest {

    private val stage = StagePlane.forStage(1600f, 1000f)

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    // ---- the seam that must not exist ----------------------------------------

    @Test
    fun aPointOnTheMatProjectsAndComesBackToItself() {
        // The whole reason projection and its inverse are one file: a card
        // lifted off the mat has to leave from exactly where it was resting.
        listOf(
            0f to 0f,
            800f to 500f,
            1600f to 1000f,
            100f to 900f,
            1500f to 80f,
        ).forEach { (x, y) ->
            val screen = stage.project(x, y)
            val back = stage.unproject(screen.x, screen.y)
            assertClose(x, back.x, 0.1f, "x at ($x, $y):")
            assertClose(y, back.y, 0.1f, "y at ($x, $y):")
        }
    }

    @Test
    fun theCentreOfTheMatIsTheOnePointThatDoesNotMove() {
        val middle = stage.project(stage.centreX, stage.centreY)

        assertClose(stage.centreX, middle.x)
        assertClose(stage.centreY, middle.y)
        assertClose(1f, middle.scale)
    }

    // ---- the tilt does what a tilt does ---------------------------------------

    @Test
    fun theNearEdgeComesTowardYouAndTheFarEdgeGoesAway() {
        // A positive tilt brings the bottom edge forward, which is what
        // Compose's rotationX means and what the zone table already assumes.
        val near = stage.project(stage.centreX, stage.height)
        val far = stage.project(stage.centreX, 0f)

        assertTrue(near.scale > 1f, "the near edge should be larger, was ${near.scale}")
        assertTrue(far.scale < 1f, "the far edge should be smaller, was ${far.scale}")
        assertTrue(near.depth > far.depth)
    }

    @Test
    fun aFlatStageIsNoStageAtAll() {
        val flat = StagePlane(1600f, 1000f, tiltDegrees = 0f, cameraDistance = 2000f)

        listOf(0f, 500f, 1000f).forEach { y ->
            assertClose(1f, flat.project(800f, y).scale, note = "y=$y:")
        }
    }

    @Test
    fun aWiderStageDoesNotGetAViolentKeystone() {
        // On an ultrawide window the height stops being the thing that sets the
        // field of view, so the camera is pushed back by the width instead.
        val wide = StagePlane.forStage(4000f, 900f)
        val square = StagePlane.forStage(1000f, 900f)

        assertTrue(wide.cameraDistance > square.cameraDistance)
    }

    // ---- growth, reporting on itself -------------------------------------------

    @Test
    fun theStageSaysHowMuchRoomItsOwnTiltCosts() {
        // Which is the point: the fitter and the renderer cannot disagree,
        // because there is only one number and the projection computes it.
        val growth = stage.perspectiveGrowth

        assertTrue(growth > 1f, "a tilt always grows the near edge")
        assertTrue(growth < 1.15f, "and not by much at this tilt: $growth")
        assertClose(stage.project(stage.centreX, stage.height).scale, growth)
    }

    @Test
    fun aTableSolvedForThatGrowthStaysInsideItsBox() {
        // The end-to-end version of the rule: hand the stage's own number to
        // the fitter and the projected mat cannot overhang the surface.
        val layout = BoardLayouter.solve(
            width = 1600f,
            height = 1000f,
            aspectRatio = 59f / 86f,
            perspectiveGrowth = stage.perspectiveGrowth,
        )

        val corners = listOf(
            layout.bounds.left to layout.bounds.top,
            layout.bounds.right to layout.bounds.bottom,
            layout.bounds.left to layout.bounds.bottom,
            layout.bounds.right to layout.bounds.top,
        )

        corners.forEach { (x, y) ->
            val projected = stage.project(x, y)
            assertTrue(projected.x >= -1f && projected.x <= 1601f, "x off stage: ${projected.x}")
            assertTrue(projected.y >= -1f && projected.y <= 1001f, "y off stage: ${projected.y}")
        }
    }

    // ---- lifting ----------------------------------------------------------------

    @Test
    fun aCardLiftedOffTheMatGetsBigger() {
        assertClose(1f, stage.liftScale(0f))
        assertTrue(stage.liftScale(100f) > 1f)
        assertTrue(stage.liftScale(200f) > stage.liftScale(100f))
    }

    @Test
    fun theLiftIsTheSameDivideAsThePerspective() {
        // Which is why a lifted card grows by exactly as much as the stage says
        // it should, rather than by a second constant that drifts away from it.
        val z = 120f
        val atCentre = stage.project(stage.centreX, stage.centreY, z)

        assertClose(stage.liftScale(z), atCentre.scale)
    }

    @Test
    fun liftingMovesACardUpTheScreenAsWellAsOut() {
        // Along the plane's normal, not straight at the camera — which is what
        // taking a card off a table actually does, and gives the parallax free.
        val resting = stage.project(stage.centreX, stage.centreY, 0f)
        val raised = stage.project(stage.centreX, stage.centreY, 200f)

        assertTrue(raised.y < resting.y, "a raised card should ride up the screen")
    }

    // ---- refusing to fall over ----------------------------------------------------

    @Test
    fun aPointAtTheCameraDoesNotDivideByZero() {
        val shallow = StagePlane(1600f, 1000f, tiltDegrees = 11f, cameraDistance = 10f)

        val projected = shallow.project(800f, 1000f, 10_000f)
        assertTrue(projected.scale.isFinite(), "scale was ${projected.scale}")
        assertTrue(projected.x.isFinite() && projected.y.isFinite())
    }

    @Test
    fun aStageWithNoSizeStillAnswers() {
        val nothing = StagePlane.forStage(0f, 0f)

        val projected = nothing.project(0f, 0f)
        assertTrue(projected.x.isFinite() && projected.y.isFinite())
        assertEquals(0f, nothing.centreX)
    }
}
