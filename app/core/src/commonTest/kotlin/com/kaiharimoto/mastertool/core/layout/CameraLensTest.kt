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
    fun aShorterLensWidensTheViewAndALongerOneNarrowsIt() {
        val wide = fieldOfView(StageSeat.TABLE.pose.copy(lens = 0.7f))
        val shipped = fieldOfView(StageSeat.TABLE.pose)
        val long = fieldOfView(StageSeat.TABLE.pose.copy(lens = 2f))

        assertTrue(wide > shipped && shipped > long, "$wide / $shipped / $long")
        // And the numbers the KDoc's table promises, to the degree.
        assertTrue(abs(wide - 76f) < 1f, "wide end was $wide degrees")
        assertTrue(abs(shipped - 58f) < 1f, "the shipped lens is $shipped degrees")
        assertTrue(abs(long - 31f) < 1f, "long end was $long degrees")
    }

    @Test
    fun theMiddleOfTheTableDoesNotMoveAndTheCornersDo() {
        // The whole difference between a focal length and a dolly, as one claim.
        // A point at zero depth projects at scale one whatever the camera
        // distance, so pinning `zoom` pins the subject and frees the perspective.
        val centre = Triple(width / 2f, height / 2f, 0f)
        val shipped = plane(StageSeat.TABLE.pose)
        val wide = plane(StageSeat.TABLE.pose.copy(lens = 0.7f))

        val a = shipped.project(centre.first, centre.second, centre.third)
        val b = wide.project(centre.first, centre.second, centre.third)
        assertTrue(abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f, "the subject moved")
        assertTrue(abs(a.scale - b.scale) < 1e-4f, "the subject changed size")

        // The near edge is where the lens shows.
        val nearShipped = shipped.project(width / 2f, height, 0f).scale
        val nearWide = wide.project(width / 2f, height, 0f).scale
        assertTrue(nearWide > nearShipped * 1.05f, "$nearWide against $nearShipped")
    }

    @Test
    fun theKeystoneGrowsAsTheLensShortens() {
        // `perspectiveGrowth` is what `BoardLayouter` is solved against, so this
        // is also the reason a *baked-in* lens re-solves the board.
        val growths = listOf(2f, 1.5f, 1f, 0.7f)
            .map { plane(StageSeat.TABLE.pose.copy(lens = it)).perspectiveGrowth }

        growths.zipWithNext { longer, shorter ->
            assertTrue(shorter > longer, "growth did not rise as the lens shortened: $growths")
        }
    }

    // ---- the floor moves with it -------------------------------------------------------

    @Test
    fun aWiderLensMakesYouStandFurtherBack() {
        // The dangerous one. A short lens deepens the table toward the camera in
        // exactly the way laying it back does, so the clearance floor has to rise
        // with it or the far corner crosses the lens.
        val long = envelope.minDistanceAt(45f, width, height, lens = 2f)
        val shipped = envelope.minDistanceAt(45f, width, height)
        val wide = envelope.minDistanceAt(45f, width, height, lens = 0.7f)

        assertTrue(wide > shipped && shipped > long, "$wide / $shipped / $long")
        assertEquals(shipped, envelope.minDistanceAt(45f, width, height, lens = 1f))
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
    fun theEnvelopeKeepsTheLensInRangeAndClampsItBeforeItAsksForTheFloor() {
        val squashed = envelope.clamp(CameraPose(0f, 21f, 1.45f, 0.01f), width, height)
        val stretched = envelope.clamp(CameraPose(0f, 21f, 1.45f, 99f), width, height)

        assertEquals(envelope.minLens, squashed.lens)
        assertEquals(envelope.maxLens, stretched.lens)
        // And the distance floor it was given is the one for the *clamped* lens,
        // not for the nonsense that came in.
        assertEquals(
            envelope.minDistanceAt(21f, width, height, envelope.minLens),
            envelope.minDistanceAt(21f, width, height, squashed.lens),
        )
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
