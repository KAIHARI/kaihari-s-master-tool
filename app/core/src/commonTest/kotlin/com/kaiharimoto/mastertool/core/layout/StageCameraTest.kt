package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.SpringSpec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The camera, and the two properties every other thing on the stage rests on.
 *
 * A projection that cannot be inverted exactly takes the pile edges, the card
 * thickness and every airborne shadow with it, because `flatten` is the
 * projection composed with its own inverse. So the round-trip and the seam at
 * z = 0 are checked over a grid of poses rather than at one — the old tests
 * checked them at the only pose there was, which is no longer a reassuring
 * thing to have checked.
 */
class StageCameraTest {

    private val width = 1600f
    private val height = 1000f

    /**
     * Poses worth caring about: the seats, the corners of the envelope, and the
     * yaws where a different edge of the table is nearest the camera.
     */
    private val envelope = CameraEnvelope()

    private val poses: List<CameraPose> = buildList {
        StageSeat.entries.forEach { add(it.pose) }
        listOf(0f, 17f, 45f, 90f, 137f, 180f, 233f, 315f, -62f).forEach { yaw ->
            add(CameraPose(yaw, 15f, 1.45f))
            add(CameraPose(yaw, 4f, 2.6f))
            // Through the envelope, because a pose the rig would refuse is not a
            // pose the renderer will ever be handed — and asking the projection
            // to invert one is asking it about a table behind the camera.
            add(envelope.clamp(CameraPose(yaw, 58f, 0.5f), width, height))
        }
    }

    private fun plane(pose: CameraPose) = pose.planeFor(width, height)

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    // ---- the round trip, at every angle ----------------------------------------

    @Test
    fun aPointOnTheMatComesBackToItselfFromAnywhereTheCameraCanBe() {
        val points = listOf(
            0f to 0f,
            800f to 500f,
            1600f to 1000f,
            120f to 880f,
            1480f to 90f,
        )
        poses.forEach { pose ->
            val stage = plane(pose)
            points.forEach { (x, y) ->
                val screen = stage.project(x, y)
                val back = stage.unproject(screen.x, screen.y)
                assertClose(x, back.x, 0.2f, "x at ($x, $y) from $pose:")
                assertClose(y, back.y, 0.2f, "y at ($x, $y) from $pose:")
            }
        }
    }

    @Test
    fun theCentreOfTheMatIsStillTheOnePointThatDoesNotMove() {
        // Whatever the table is turned to, it turns about its own middle.
        poses.forEach { pose ->
            val stage = plane(pose)
            val middle = stage.project(stage.centreX, stage.centreY)
            assertClose(stage.centreX, middle.x, note = "x at $pose:")
            assertClose(stage.centreY, middle.y, note = "y at $pose:")
            assertClose(1f, middle.scale, note = "scale at $pose:")
        }
    }

    @Test
    fun somethingTouchingTheMatIsDrawnWhereItWasComputedAtEveryAngle() {
        // The seam. If flatten stops being the identity at z = 0, every card
        // jumps the moment it is picked up — at some angles and not others,
        // which is the worst way for it to be wrong.
        poses.forEach { pose ->
            val stage = plane(pose)
            listOf(0f to 0f, 800f to 500f, 1600f to 1000f, 300f to 900f).forEach { (x, y) ->
                val flat = stage.flatten(x, y, 0f)
                assertClose(x, flat.x, note = "x at ($x, $y) from $pose:")
                assertClose(y, flat.y, note = "y at ($x, $y) from $pose:")
                assertClose(1f, flat.scale, note = "scale at ($x, $y) from $pose:")
            }
        }
    }

    @Test
    fun aFlattenedPointLandsWhereTheRealOneWouldAtEveryAngle() {
        poses.forEach { pose ->
            val stage = plane(pose)
            listOf(Triple(800f, 500f, 60f), Triple(300f, 900f, 25f), Triple(1400f, 120f, 140f))
                .forEach { (x, y, z) ->
                    val wanted = stage.project(x, y, z)
                    val drawn = stage.flatten(x, y, z)
                    val landed = stage.project(drawn.x, drawn.y, 0f)
                    assertClose(wanted.x, landed.x, 0.3f, "x for ($x, $y, $z) from $pose:")
                    assertClose(wanted.y, landed.y, 0.3f, "y for ($x, $y, $z) from $pose:")
                }
        }
    }

    // ---- what turning the table does --------------------------------------------

    @Test
    fun turningTheTableBringsADifferentEdgeForward() {
        // Ninety degrees round, the left edge is where the near edge was.
        val square = plane(CameraPose(yawDegrees = 0f))
        val quarter = plane(CameraPose(yawDegrees = 90f))

        val nearEdge = square.project(width / 2f, height)
        assertTrue(nearEdge.depth > 0f, "the bottom edge should be nearest square on")

        val sameEdgeTurned = quarter.project(width / 2f, height)
        assertTrue(
            sameEdgeTurned.depth < nearEdge.depth,
            "turning should take that edge away, was ${sameEdgeTurned.depth}",
        )
        assertTrue(
            quarter.project(0f, height / 2f).depth > 0f,
            "and bring a side edge forward instead",
        )
    }

    @Test
    fun halfATurnPutsYouOnTheOtherSideOfTheTable() {
        val across = plane(CameraPose(yawDegrees = 180f))
        val far = across.project(width / 2f, 0f)
        val near = across.project(width / 2f, height)

        assertTrue(far.scale > near.scale, "the far edge should now be the near one")
    }

    @Test
    fun aTableSquareOnIsTheTableWeAlreadyHad() {
        // The whole change has to be invisible at the pose everything was tuned
        // at, or every constant on the stage quietly moved.
        val before = StagePlane.forStage(width, height)
        val now = plane(CameraPose(0f, StagePlane.TILT, CameraPose.HOME_DISTANCE))

        assertClose(before.cameraDistance, now.cameraDistance, 0.5f)
        assertClose(before.perspectiveGrowth, now.perspectiveGrowth, 1e-4f)
        listOf(0f to 0f, 800f to 500f, 1600f to 1000f).forEach { (x, y) ->
            assertClose(before.project(x, y).x, now.project(x, y).x, 0.01f, "x at ($x, $y):")
            assertClose(before.project(x, y).y, now.project(x, y).y, 0.01f, "y at ($x, $y):")
        }
    }

    @Test
    fun theGrowthIsMeasuredAtWhicheverCornerIsNearest() {
        // Down the middle is the right answer only while the table is square on.
        // Turned, the nearest thing to the camera is a corner, and a growth that
        // missed it would let the board overhang the glass.
        val turned = plane(CameraPose(yawDegrees = 45f, pitchDegrees = 30f))
        val downTheMiddle = turned.project(turned.centreX, height).scale

        assertTrue(
            turned.perspectiveGrowth >= downTheMiddle,
            "${turned.perspectiveGrowth} should be at least $downTheMiddle",
        )
        assertTrue(turned.perspectiveGrowth > 1f)
    }

    // ---- the envelope ---------------------------------------------------------------

    @Test
    fun theCameraCannotGetUnderTheTableOrFlattenItCompletely() {
        assertEquals(envelope.minPitch, envelope.clamp(CameraPose(pitchDegrees = -40f)).pitchDegrees)
        assertEquals(envelope.maxPitch, envelope.clamp(CameraPose(pitchDegrees = 89f)).pitchDegrees)
        assertEquals(envelope.minDistance, envelope.clamp(CameraPose(distance = 0.1f)).distance)
        assertEquals(envelope.maxDistance, envelope.clamp(CameraPose(distance = 9f)).distance)
    }

    @Test
    fun aTableTurnsAllTheWayRound() {
        // Yaw is the one axis with no limit, because there is no angle you
        // cannot look at a table from.
        listOf(-720f, -37f, 0f, 199f, 1080f).forEach {
            assertEquals(it, envelope.clamp(CameraPose(yawDegrees = it)).yawDegrees)
        }
    }

    @Test
    fun everySeatIsInsideTheEnvelope() {
        StageSeat.entries.forEach {
            assertEquals(
                it.pose,
                envelope.clamp(it.pose, width, height),
                "$it is not a legal place to sit",
            )
        }
    }

    @Test
    fun theTableNeverReachesTheCamera() {
        // The failure this floor exists for: at a steep pitch the far corner of
        // the mat travels toward the lens until it passes it, the perspective
        // divide clamps rather than exploding, and unproject quietly stops being
        // the inverse of project. Nothing crashes; the shadows just tear.
        listOf(4f, 15f, 34f, 46f, 58f).forEach { pitch ->
            val pose = envelope.clamp(CameraPose(yawDegrees = 45f, pitchDegrees = pitch, distance = 0.1f), width, height)
            val stage = plane(pose)
            for (x in 0..1) {
                for (y in 0..1) {
                    val depth = stage.project(x * width, y * height).depth
                    assertTrue(
                        depth < stage.cameraDistance,
                        "at $pitch degrees a corner reached the lens: $depth of ${stage.cameraDistance}",
                    )
                }
            }
        }
    }

    @Test
    fun youMayComeCloseLookingDownAndMustStepBackLookingAlong() {
        val high = envelope.minDistanceAt(6f, width, height)
        val low = envelope.minDistanceAt(58f, width, height)

        assertTrue(low > high, "the floor should rise with the pitch: $low against $high")
        assertEquals(envelope.minDistance, envelope.minDistanceAt(0f, width, height))
    }

    @Test
    fun theReadingSeatIsFlatterThanThePlayingSeat() {
        assertTrue(StageSeat.OVERHEAD.pose.pitchDegrees < StageSeat.TABLE.pose.pitchDegrees)
        assertTrue(StageSeat.TABLE.pose.pitchDegrees < StageSeat.SEATED.pose.pitchDegrees)
        assertTrue(StageSeat.OVERHEAD.pose.distance > StageSeat.SEATED.pose.distance)
    }

    // ---- keeping the board on the glass ----------------------------------------------

    private val board = BoardLayouter.solve(
        width = width,
        height = height,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = StagePlane.forStage(width, height).perspectiveGrowth,
    ).bounds

    @Test
    fun theSeatTheStageOpensAtNeedsNoCorrectionAtAll() {
        val fitted = CameraFit.fit(StageSeat.TABLE.pose, board, envelope, width, height, ::plane)

        assertEquals(StageSeat.TABLE.pose, fitted, "the home seat should already fit")
    }

    @Test
    fun turningTheTableStepsYouBackFarEnoughToSeeIt() {
        // The whole trade this class exists to make: the cards keep their size
        // and the camera is what gives.
        var stepped = 0

        listOf(20f, 45f, 90f, 135f).forEach { yaw ->
            val wanted = CameraPose(yawDegrees = yaw, pitchDegrees = 40f, distance = envelope.minDistance)
            val fitted = CameraFit.fit(wanted, board, envelope, width, height, ::plane)

            assertEquals(yaw, fitted.yawDegrees, "the angle asked for should be honoured")
            assertEquals(40f, fitted.pitchDegrees, "and so should the pitch")
            assertTrue(
                CameraFit.holds(plane(fitted), board),
                "the board still left the glass at $yaw degrees",
            )
            if (fitted.distance > envelope.minDistanceAt(40f, width, height)) stepped++
        }

        assertTrue(stepped > 0, "no angle needed a correction, so this proves nothing")
    }

    @Test
    fun itNeverDolliesFurtherThanItHasTo() {
        val wanted = CameraPose(yawDegrees = 45f, pitchDegrees = 45f, distance = envelope.minDistance)
        val fitted = CameraFit.fit(wanted, board, envelope, width, height, ::plane)

        assertTrue(CameraFit.holds(plane(fitted), board))
        // A hair nearer and it would not: the answer is on the boundary rather
        // than at some safe distance chosen to avoid thinking about it.
        val nearer = fitted.copy(distance = fitted.distance * 0.97f)
        assertTrue(
            !CameraFit.holds(plane(nearer), board),
            "it stopped ${fitted.distance} short of the boundary",
        )
    }

    @Test
    fun aBoardThatCannotFitAtAllStillGivesYouAPicture() {
        // Rather than refusing the gesture, which reads as the camera being
        // broken. The furthest seat is the best answer available.
        val huge = Slot(left = -4000f, top = -4000f, width = 12000f, height = 12000f)
        val fitted = CameraFit.fit(CameraPose(distance = 1f), huge, envelope, width, height, ::plane)

        assertEquals(envelope.maxDistance, fitted.distance)
    }

    // ---- turning by the short way round --------------------------------------------

    @Test
    fun aTableGoingFromThreeFiftyToTenTurnsTwentyDegrees() {
        assertClose(370f, Turns.nearest(350f, 10f), note = "the short way:")
        assertClose(-10f, Turns.nearest(10f, 350f), note = "and back again:")
    }

    @Test
    fun theShortWayIsNeverMoreThanHalfATurn() {
        listOf(0f, 45f, 179f, 181f, 359f, -400f, 1000f).forEach { current ->
            listOf(0f, 90f, 180f, 270f, 359f).forEach { target ->
                val moved = abs(Turns.nearest(current, target) - current)
                assertTrue(moved <= 180.001f, "went $moved from $current to $target")
            }
        }
    }

    @Test
    fun aTurnOfExactlyHalfGoesOneWayRatherThanNowhere() {
        assertClose(180f, Turns.nearest(0f, 180f))
        assertClose(180f, Turns.nearest(0f, -180f))
    }

    @Test
    fun theBearingReadsAsACompassRatherThanAnAccumulatedAngle() {
        assertEquals(0, Turns.bearing(CameraPose(yawDegrees = 720f)))
        assertEquals(90, Turns.bearing(CameraPose(yawDegrees = 450f)))
        assertEquals(270, Turns.bearing(CameraPose(yawDegrees = -90f)))
    }

    @Test
    fun aCameraRestingAtASeatSaysWhichOne() {
        assertEquals(StageSeat.TABLE, Turns.seatAt(StageSeat.TABLE.pose))
        assertEquals(StageSeat.OVERHEAD, Turns.seatAt(StageSeat.OVERHEAD.pose))
        // And one turned away from it does not claim to be there.
        assertNull(Turns.seatAt(StageSeat.TABLE.pose.copy(yawDegrees = 40f)))
        assertNull(Turns.seatAt(StageSeat.TABLE.pose.copy(distance = 2f)))
    }

    // ---- the rig --------------------------------------------------------------------

    private fun settle(rig: CameraRig, seconds: Float = 4f) {
        var t = 0f
        while (t < seconds && rig.step(SpringSpec.Snappy, 1f / 120f)) t += 1f / 120f
    }

    @Test
    fun aCameraAtRestCostsTheFrameLoopNothing() {
        val rig = CameraRig()

        assertTrue(!rig.step(SpringSpec.Snappy, 1f / 120f), "a parked camera should skip")
        assertTrue(!rig.moving)
    }

    @Test
    fun aCameraSentToASeatArrivesAtIt() {
        val rig = CameraRig()
        rig.aimAt(StageSeat.OVERHEAD)
        assertTrue(rig.moving)

        settle(rig)

        assertEquals(StageSeat.OVERHEAD.pose, rig.pose)
        assertTrue(!rig.moving, "it should have parked")
    }

    @Test
    fun aCameraDraggedGoesExactlyWhereItIsPutAndStaysThere() {
        // The same rule a carried card obeys. A spring between a finger and the
        // thing it is moving is lag, and lag is what makes it feel remote.
        val rig = CameraRig()
        rig.nudge(deltaYaw = 30f, deltaPitch = 5f)

        assertClose(30f, rig.pose.yawDegrees)
        assertClose(StagePlane.TILT + 5f, rig.pose.pitchDegrees)
        assertTrue(!rig.step(SpringSpec.Snappy, 1f / 120f), "a dragged camera must not then spring")
    }

    @Test
    fun aCameraDraggedPastTheEnvelopeStopsAtIt() {
        val rig = CameraRig()
        repeat(40) { rig.nudge(deltaYaw = 0f, deltaPitch = 10f) }

        assertEquals(rig.envelope.maxPitch, rig.pose.pitchDegrees)
    }

    @Test
    fun aCameraTakesTheShortWayToASeatOnTheOtherSide() {
        val rig = CameraRig()
        rig.nudge(deltaYaw = 350f, deltaPitch = 0f)
        rig.aimAt(StageSeat.TABLE)

        // Not back through 350 degrees of table.
        assertClose(360f, rig.target.yawDegrees, note = "the target should be the near representative:")
        settle(rig)
        assertClose(360f, rig.pose.yawDegrees, 0.2f)
        assertEquals(0, Turns.bearing(rig.pose), "and it is square on when it gets there")
    }

    @Test
    fun placingTheCameraSomewhereDoesNotAnimateThere() {
        val rig = CameraRig()
        rig.placeAt(StageSeat.SEATED.pose)

        assertEquals(StageSeat.SEATED.pose, rig.pose)
        assertTrue(!rig.moving)
    }

    @Test
    fun aRigOpenedAtASeatIsAtThatSeat() {
        val rig = CameraRig(seat = StageSeat.OVERHEAD)

        assertEquals(StageSeat.OVERHEAD.pose, rig.pose)
        assertEquals(StageSeat.OVERHEAD.pose, rig.target)
    }

    // ---- refusing to fall over -------------------------------------------------------

    @Test
    fun aStageWithNoSizeStillAnswersAtEveryAngle() {
        listOf(0f, 45f, 180f).forEach { yaw ->
            val nothing = CameraPose(yawDegrees = yaw).planeFor(0f, 0f)
            val projected = nothing.project(0f, 0f)
            assertTrue(projected.x.isFinite() && projected.y.isFinite(), "at $yaw degrees")
            assertTrue(nothing.perspectiveGrowth.isFinite())
        }
    }

    @Test
    fun aPointAtTheCameraDoesNotDivideByZeroHoweverTheTableIsTurned() {
        val shallow = StagePlane(1600f, 1000f, tiltDegrees = 15f, cameraDistance = 10f, yawDegrees = 63f)
        val projected = shallow.project(800f, 1000f, 10_000f)

        assertTrue(projected.scale.isFinite(), "scale was ${projected.scale}")
        assertTrue(projected.x.isFinite() && projected.y.isFinite())
    }
}
