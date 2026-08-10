package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.motion.Springs
import kotlin.math.abs
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The focal length: that it does what a lens does, and that it does nothing at
 * all until somebody moves it.
 *
 * The second half is most of this file. A fourth field on a pose is four places
 * that used to build a pose out of three numbers, and every one of them fails
 * silently — a camera that quietly forgets its lens the moment you touch the
 * table reads as the tuning tool being broken, not as the camera being wrong.
 */
class CameraLensTest {

    private val width = 1600f
    private val height = 1000f
    private val envelope = CameraEnvelope()

    private fun plane(pose: CameraPose) = pose.planeFor(width, height)

    private val growthAtHome = plane(StageSeat.TABLE.pose).perspectiveGrowth

    /** Horizontal field of view in degrees, which is what a lens is usually sold by. */
    private fun fieldOfView(pose: CameraPose): Float =
        2f * atan(width / (2f * plane(pose).cameraDistance)) * (180f / kotlin.math.PI.toFloat())

    // ---- at one, nothing happened -----------------------------------------------------

    @Test
    fun theShippedLensLeavesEveryProjectionExactlyWhereItWas() {
        // Not "close to". `GoldenStageTest` is a recording of this projection and
        // the whole argument for landing the field inert is that it cannot see it.
        listOf(
            StageSeat.OVERHEAD.pose, StageSeat.TABLE.pose, StageSeat.SEATED.pose,
            CameraPose(37f, 12f, 2.1f), CameraPose(-140f, 51f, 1.9f),
        ).forEach { pose ->
            val withField = plane(pose)
            val asItWas = StagePlane(
                width = width,
                height = height,
                tiltDegrees = pose.pitchDegrees,
                cameraDistance = pose.distance * kotlin.math.max(height, width * 0.55f),
                yawDegrees = pose.yawDegrees,
                zoom = CameraPose.HOME_DISTANCE / pose.distance,
            )
            assertEquals(asItWas.cameraDistance, withField.cameraDistance, "at $pose")
            assertEquals(asItWas.zoom, withField.zoom, "at $pose")
        }
    }

    @Test
    fun everySeatIsStillAtTheShippedLens() {
        // If a seat is ever given a lens of its own, `Turns.seatAt`'s tolerance
        // and `StageCameraTest.theSeatTheStageOpensAtNeedsNoCorrectionAtAll`
        // both have to be looked at again. This is the tripwire.
        StageSeat.entries.forEach {
            assertEquals(CameraPose.HOME_LENS, it.pose.lens, "${it.label} has its own lens")
        }
    }

    // ---- and what it does when it moves ------------------------------------------------

    @Test
    fun aLongerLensMagnifiesAndAShorterOneDoesNot() {
        // What a focal length is: the board gets bigger. Measured at the middle
        // of the table, where the projection's own scale is exactly one, so the
        // only thing that can have moved it is the zoom.
        fun widthOnGlass(lens: Float): Float {
            val at = plane(StageSeat.TABLE.pose.copy(lens = lens))
            return at.project(width, height / 2f, 0f).x - at.project(0f, height / 2f, 0f).x
        }

        val wide = widthOnGlass(0.7f)
        val shipped = widthOnGlass(1f)
        val long = widthOnGlass(2f)

        assertTrue(long > shipped && shipped > wide, "$wide / $shipped / $long")
        // And it is a *multiplier*: twice the lens is twice the board.
        assertTrue(abs(long / shipped - 2f) < 0.02f, "2.0 gave ${long / shipped}x")
        assertTrue(abs(wide / shipped - 0.7f) < 0.02f, "0.7 gave ${wide / shipped}x")
    }

    @Test
    fun theLensDoesNotTouchThePerspectiveAtAll() {
        // The claim the whole re-parameterisation is for, and the one the first
        // version got backwards: v1.2.38 put the lens on `cameraDistance` alone,
        // which pins the framing and moves the perspective — a dolly zoom. Both
        // dials then changed the perspective, so tuning one appeared to undo the
        // other.
        //
        // Perspective is the ratio between the near edge and the far edge, and a
        // magnification cannot change a ratio.
        fun keystone(lens: Float): Float {
            val at = plane(StageSeat.TABLE.pose.copy(lens = lens))
            return at.project(width / 2f, height, 0f).scale / at.project(width / 2f, 0f, 0f).scale
        }

        val shipped = keystone(1f)
        listOf(0.7f, 0.85f, 1.4f, 2f).forEach {
            assertTrue(
                abs(keystone(it) - shipped) < 1e-3f,
                "a lens of $it changed the keystone from $shipped to ${keystone(it)}",
            )
        }
        // Which is also why a baked-in lens no longer threatens the bound
        // `StagePlaneTest.theStageSaysHowMuchRoomItsOwnTiltCosts` asserts.
        listOf(0.7f, 1f, 2f).forEach {
            assertTrue(
                abs(plane(StageSeat.TABLE.pose.copy(lens = it)).perspectiveGrowth - growthAtHome) < 1e-4f,
                "a lens of $it moved perspectiveGrowth",
            )
        }
    }

    @Test
    fun whereYouStandIsWhatChangesThePerspective() {
        // The other half of the pair, stated so that the two are documented as a
        // pair: distance is a position, so it does what walking backwards does.
        fun keystone(distance: Float): Float {
            val at = plane(StageSeat.TABLE.pose.copy(distance = distance))
            return at.project(width / 2f, height, 0f).scale / at.project(width / 2f, 0f, 0f).scale
        }

        assertTrue(keystone(1.0f) > keystone(1.45f), "stepping in did not deepen it")
        assertTrue(keystone(1.45f) > keystone(2.5f), "stepping back did not flatten it")
    }

    // ---- and the floor does not move with it -------------------------------------------

    @Test
    fun theLensDoesNotMoveTheDistanceFloor() {
        // It cancels: the clearance constraint is
        // `halfDiagonal · zoom · sin ≤ CLEARANCE · cameraDistance`, and a focal
        // length multiplies both sides' lens term. Zooming does not move the
        // camera, so it cannot bring the table nearer to it. The first version
        // did carry a lens factor here, because the first version was a dolly.
        val floor = envelope.minDistanceAt(45f, width, height)

        listOf(0.7f, 1f, 1.5f, 2f).forEach {
            val pose = envelope.clamp(CameraPose(0f, 45f, 0.5f, it), width, height)
            assertTrue(abs(pose.distance - floor) < 1e-4f, "a lens of $it moved the floor to ${pose.distance}")
        }
    }

    @Test
    fun noLegalPoseAtAnyLensEverLetsTheTableReachTheCamera() {
        // The claim the floor exists for, swept rather than sampled: `project`
        // clamps rather than dividing by zero, and a clamp cannot be inverted —
        // so `unproject` stops being the inverse, `flatten` tears, and every pile
        // edge, card thickness, airborne shadow, card picture and hit test
        // silently disagrees. Nothing crashes and nothing logs.
        val corners = listOf(
            com.kaiharimoto.mastertool.core.motion.Vec3(0f, 0f, 0f),
            com.kaiharimoto.mastertool.core.motion.Vec3(width, 0f, 0f),
            com.kaiharimoto.mastertool.core.motion.Vec3(width, height, 0f),
            com.kaiharimoto.mastertool.core.motion.Vec3(0f, height, 0f),
        )
        var checked = 0
        listOf(0.7f, 0.85f, 1f, 1.4f, 2f).forEach { lens ->
            var pitch = envelope.minPitch
            while (pitch <= envelope.maxPitch) {
                var yaw = 0f
                while (yaw < 360f) {
                    // Through the envelope, because a pose the rig would refuse is
                    // not a pose the renderer is ever handed.
                    val pose = envelope.clamp(
                        CameraPose(yaw, pitch, envelope.minDistance, lens),
                        width,
                        height,
                    )
                    val at = plane(pose)
                    corners.forEach { corner ->
                        checked++
                        assertTrue(
                            at.reaches(corner),
                            "at lens $lens pitch $pitch yaw $yaw the corner $corner crossed the lens",
                        )
                    }
                    yaw += 15f
                }
                pitch += 6f
            }
        }
        assertTrue(checked > 1000, "only $checked corners were checked")
    }

    @Test
    fun theEnvelopeKeepsTheLensInRange() {
        val squashed = envelope.clamp(CameraPose(0f, 21f, 1.45f, 0.01f), width, height)
        val stretched = envelope.clamp(CameraPose(0f, 21f, 1.45f, 99f), width, height)

        assertEquals(envelope.minLens, squashed.lens)
        assertEquals(envelope.maxLens, stretched.lens)
        // And clamping it changes nothing else, which is only true because the
        // floor no longer depends on it.
        assertEquals(1.45f, squashed.distance)
        assertEquals(21f, squashed.pitchDegrees)
    }

    // ---- and nothing quietly forgets it ------------------------------------------------

    @Test
    fun draggingTheTableDoesNotResetTheLens() {
        // `nudge` built a fresh pose out of three arguments, so a fourth field
        // went back to its default on every frame of every drag. The slider
        // would have worked right up until you touched the table.
        val rig = CameraRig().apply { width = this@CameraLensTest.width; height = this@CameraLensTest.height }
        rig.placeAt(CameraPose(0f, 21f, 1.6f, lens = 1.7f))

        rig.nudge(deltaYaw = 30f, deltaPitch = 5f, dollyBy = 0.1f)

        assertEquals(1.7f, rig.pose.lens, "a drag reset the lens")
        assertTrue(abs(rig.pose.yawDegrees - 30f) < 0.01f, "the drag did not turn the table")
    }

    @Test
    fun springingToASeatDoesNotResetTheLens() {
        // Same bug in the other clock: `step` rebuilt the pose from its three
        // springs every frame. Three springs, four fields.
        val rig = CameraRig().apply { width = this@CameraLensTest.width; height = this@CameraLensTest.height }
        rig.placeAt(CameraPose(0f, 5f, 1.62f, lens = 0.8f))
        rig.aimAt(StageSeat.SEATED)

        var frames = 0
        while (rig.moving && frames < 600) {
            rig.step(SpringSpec.Snappy, 1f / 120f)
            assertEquals(0.8f, rig.pose.lens, "the lens was reset $frames frames into the travel")
            frames++
        }
        assertTrue(frames in 1..599, "the camera never arrived — $frames frames")
        assertEquals(0.8f, rig.pose.lens)
    }

    @Test
    fun aSeatIsAChairAndNotAChairAndALens() {
        // The lens is not part of seat identity, and travelling to a seat keeps
        // it. Both halves of that are one decision: a person who has tuned a
        // focal length and then taps "Seated" has asked to move, not to undo.
        assertNotNull(Turns.seatAt(StageSeat.TABLE.pose.copy(lens = 1.6f)))
        assertNull(Turns.seatAt(StageSeat.TABLE.pose.copy(pitchDegrees = 40f)))

        val rig = CameraRig().apply { width = this@CameraLensTest.width; height = this@CameraLensTest.height }
        rig.placeAt(CameraPose(0f, 5f, 1.62f, lens = 0.8f))
        rig.aimAt(StageSeat.SEATED)
        assertEquals(0.8f, rig.target.lens, "aiming at a seat took the seat's lens")
    }

    @Test
    fun theFitterCarriesTheLensThroughUntouched() {
        // `CameraFit` moves only the distance, and `copy` is what keeps that
        // true now that a pose has a fourth thing to lose.
        val tuned = CameraPose(35f, 40f, 1.2f, lens = 1.45f)
        val board = BoardLayouter.solve(width, height, 59f / 86f, 1.2f, Scenery.ROOM_ABOVE)
        val fitted = CameraFit.fit(
            wanted = tuned,
            bounds = board.bounds,
            envelope = envelope,
            surfaceWidth = width,
            surfaceHeight = height,
            plane = { it.planeFor(width, height) },
        )

        assertEquals(tuned.lens, fitted.lens, "the fitter changed the lens")
        assertEquals(tuned.yawDegrees, fitted.yawDegrees)
        assertEquals(tuned.pitchDegrees, fitted.pitchDegrees)
    }

    @Test
    fun aSprungCameraStillSettles() {
        // Guard against the `copy` above quietly breaking the settle test: the
        // spring reads the pose it just wrote, so a field carried wrongly would
        // show up as a camera that never parks.
        val rig = CameraRig().apply { width = this@CameraLensTest.width; height = this@CameraLensTest.height }
        rig.aimAt(CameraPose(90f, 40f, 1.5f, lens = 1.3f))
        var frames = 0
        while (rig.moving && frames < 2000) {
            rig.step(SpringSpec.Snappy, 1f / 120f)
            frames++
        }
        assertTrue(!rig.moving, "the camera never parked")
        assertTrue(
            Springs.settled(
                com.kaiharimoto.mastertool.core.motion.SpringValue(rig.pose.pitchDegrees, 0f),
                40f,
                0.1f,
                0.1f,
            ),
            "it parked at ${rig.pose}",
        )
    }
}
