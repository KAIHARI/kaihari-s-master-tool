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

    // ---- flattening: height, drawn by a canvas that has no height -----------------

    @Test
    fun somethingTouchingTheMatIsDrawnExactlyWhereItWasComputed() {
        // The identity at z = 0, which is what lets shadow and slab geometry be
        // handed to the mat's canvas without a special case for "resting".
        listOf(0f to 0f, 800f to 500f, 1600f to 1000f, 120f to 880f).forEach { (x, y) ->
            val flat = stage.flatten(x, y, 0f)
            assertClose(x, flat.x, note = "x at ($x, $y):")
            assertClose(y, flat.y, note = "y at ($x, $y):")
            assertClose(1f, flat.scale, note = "scale at ($x, $y):")
        }
    }

    @Test
    fun aFlattenedPointLandsWhereTheRealOneWould() {
        // The whole contract. Draw the flattened point on the mat, let the
        // plane's own transform run, and it has to land on the projection of
        // the point it came from — otherwise a pile's edge and the card on top
        // of it disagree about where the pile is.
        listOf(
            Triple(800f, 500f, 60f),
            Triple(300f, 900f, 25f),
            Triple(1400f, 120f, 140f),
        ).forEach { (x, y, z) ->
            val wanted = stage.project(x, y, z)
            val drawn = stage.flatten(x, y, z)
            val landed = stage.project(drawn.x, drawn.y, 0f)

            assertClose(wanted.x, landed.x, 0.2f, "x for ($x, $y, $z):")
            assertClose(wanted.y, landed.y, 0.2f, "y for ($x, $y, $z):")
        }
    }

    @Test
    fun heightMovesAPointUpTheScreenAndTheFlattenedOneToo() {
        val resting = stage.flatten(800f, 500f, 0f)
        val raised = stage.flatten(800f, 500f, 120f)

        assertTrue(raised.y < resting.y, "a pile's top should sit above its base")
    }

    @Test
    fun theTopOfAPileIsDrawnBiggerThanTheFeltUnderIt() {
        // The correction that comes with the position. Flattening moves the
        // point onto a part of the mat that is *further away* than the point
        // really is, so without this the card on top of a deck is drawn very
        // slightly too small — which is the whole tell that it is a picture of
        // a deck rather than a card resting on one.
        val raised = stage.flatten(800f, 500f, 120f)
        val wanted = stage.project(800f, 500f, 120f).scale
        val asFelt = stage.project(raised.x, raised.y, 0f).scale

        assertTrue(raised.scale > 1f, "it should grow, was ${raised.scale}")
        assertClose(wanted, asFelt * raised.scale, 0.001f, "corrected size:")
    }

    @Test
    fun aTallerPileRisesFurther() {
        val heights = listOf(0f, 20f, 60f, 150f).map { stage.flatten(800f, 500f, it).y }

        assertEquals(heights.sortedDescending(), heights, "further up the screen each time")
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
