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

    /**
     * The lens field, at one, is not there — measured against the code without it.
     *
     * Not "close to". `GoldenStageTest` is a recording of this projection and the
     * whole argument for landing the field inert is that it cannot see it.
     *
     * The reference formula is the pre-lens code **with the focal-length fix
     * applied**, and the difference matters. The literal historical line was
     * `cameraDistance = distance · G`, which put the camera's own position on the
     * lens; that was the bug fixed alongside this, and comparing against it now
     * would be pinning a projection nobody wants back. What survives is the claim
     * this test was written to make — that adding a *lens* changed nothing — and
     * it is still exact.
     */
    @Test
    fun theShippedLensLeavesEveryProjectionExactlyWhereItWas() {
        listOf(
            StageSeat.OVERHEAD.pose, StageSeat.TABLE.pose, StageSeat.SEATED.pose,
            CameraPose(37f, 12f, 2.1f), CameraPose(-140f, 51f, 1.9f),
        ).forEach { pose ->
            val withField = plane(pose)
            val governing = kotlin.math.max(height, width * 0.55f)
            val asItWas = StagePlane(
                width = width,
                height = height,
                tiltDegrees = pose.pitchDegrees,
                cameraDistance = CameraPose.HOME_DISTANCE * governing,
                yawDegrees = pose.yawDegrees,
                // Spelled as the production line spells it — a focal length over
                // a real distance — rather than as the algebraically equal
                // `HOME / distance`. The two differ in the last bit at 2.1, and
                // this asserts exact equality on purpose, so the reference has to
                // round the same way. A test that has to be "close to" here is a
                // test that cannot say the golden is safe.
                zoom = (CameraPose.HOME_DISTANCE * governing) / (pose.distance * governing),
            )
            assertEquals(asItWas.cameraDistance, withField.cameraDistance, "at $pose")
            assertEquals(asItWas.zoom, withField.zoom, "at $pose")
        }
    }

    /**
     * And at the home distance it is what the historical line built, to the bit.
     *
     * The one pose where the old projection and the corrected one agree, which is
     * why `GoldenStageTest`'s `Home` and `Turned` recordings — both at
     * [CameraPose.HOME_DISTANCE] — did not move when the focal length was taken
     * off the distance, and its `Steep` one did. Worth a test of its own because
     * "only the steep golden had to be re-recorded" is a claim about this exact
     * arithmetic, and the next person deserves to be able to check it.
     */
    @Test
    fun atTheSeatEverythingWasTunedAtTheOldProjectionIsTheNewOne() {
        val governing = kotlin.math.max(height, width * 0.55f)
        listOf(StageSeat.TABLE.pose, CameraPose(38f, 15f, CameraPose.HOME_DISTANCE)).forEach { pose ->
            val now = plane(pose)
            assertEquals(pose.distance * governing, now.cameraDistance, "at $pose")
            assertEquals(1f, now.zoom, "at $pose")
        }
    }

    // ---- and the fault that hid behind it ----------------------------------------------

    /**
     * **Walking toward the table does not change the lens.** kai's first
     * complaint, and this is the whole of it in one assertion.
     *
     * `cameraDistance` is the focal length in pixels — [StagePlane.project]
     * divides by `cameraDistance − depth` and Compose's `graphicsLayer` does the
     * same thing — and it carried [CameraPose.distance] for as long as there was
     * a camera. So the field of view swung from 34 degrees at the back of the
     * envelope to 77 at the front, a fifty-seven millimetre lens to a twenty-one,
     * with the lens dial sitting still. Every dolly was a dolly zoom.
     */
    @Test
    fun theFieldOfViewDoesNotMoveWhenYouWalkTowardTheTable() {
        val home = fieldOfView(StageSeat.TABLE.pose)
        var distance = envelope.minDistance
        while (distance <= envelope.maxDistance) {
            assertEquals(
                home,
                fieldOfView(CameraPose(distance = distance)),
                "the lens moved at a distance of $distance",
            )
            distance += 0.05f
        }
    }

    /**
     * And the perspective across the table moves as far as walking moves it.
     *
     * The honest measure of "how strong is the perspective" is what the nearest
     * corner grows to, and for a camera at a fixed focal length that excess goes
     * as `1/distance`: stand half as far away and the keystone is twice as
     * strong. It used to go as `1/distance²`, because the distance was on the
     * focal length *and* on the magnification and the two multiplied — which is
     * the arithmetic behind "the perspective seems to shift a lot".
     */
    @Test
    fun theKeystoneGoesAsTheDistanceAndNotItsSquare() {
        listOf(1f, 2f, 4f).forEach { near ->
            val close = plane(CameraPose(pitchDegrees = 30f, distance = near)).perspectiveGrowth - 1f
            val far = plane(CameraPose(pitchDegrees = 30f, distance = near * 2f)).perspectiveGrowth - 1f
            // Not exactly a half. The excess is `a / (d − a)` for a constant `a`
            // — a quarter, here, being half the stage height times the sine of
            // thirty over the governing dimension — so doubling the distance
            // divides it by `(2d − a) / (d − a)`: 2.33 at the front of this
            // sweep, 2.07 at the back, and two in the limit.
            //
            // The number that matters is what the *square* law would say, which
            // is `(4d² − a) / (d² − a)` and is five at the front. There is no
            // overlap, which is what makes this a measurement rather than a
            // tolerance somebody widened until it went green.
            val ratio = close / far
            assertTrue(
                ratio in 2f..2.4f,
                "doubling the distance from $near should roughly halve the keystone " +
                    "(a square law would put this near five), ratio was $ratio",
            )
        }
    }

    /**
     * And tilting does not change the lens either, which was the same bug's tail.
     *
     * The pitch moves [CameraEnvelope.minDistanceAt], which moved the distance,
     * which moved the focal length. So a one-finger drag *down* the felt — the
     * plainest gesture on the stage — quietly zoomed. Nothing about that was
     * visible on the tuning panel, which is why it took a measurement rather than
     * a reading to find.
     */
    @Test
    fun tiltingTheCameraDoesNotChangeTheLens() {
        val rig = CameraRig()
        rig.width = width
        rig.height = height
        rig.placeAt(StageSeat.TABLE.pose)

        val lens = plane(rig.pose).cameraDistance
        repeat(60) {
            rig.nudge(deltaYaw = 0f, deltaPitch = 1.2f)
            assertEquals(
                lens,
                plane(rig.pose).cameraDistance,
                "the lens moved at a pitch of ${rig.pose.pitchDegrees}",
            )
        }
        assertTrue(rig.pose.pitchDegrees > 60f, "the sweep never reached the steep end")
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
    fun theMillimetresBesideTheDialAreTheOnesTheProjectionHas() {
        // `StageTuner` prints `33mm × lens` next to the slider, and that number is
        // honest only if the field of view really is proportional to `1/lens` —
        // which is to say only if this is a focal length and not a crop. It is:
        // the visible world shrinks by the lens and the camera's world distance
        // does not move (`cameraDistance / zoom` is lens-free), so the angle goes
        // as `atan(1/lens)`. On a 36mm frame a focal length is `18/tan(halfFov)`.
        //
        // This is also the only test that would catch the mm readout drifting
        // away from the projection, which is exactly the defect kai reported.
        fun millimetres(lens: Float): Float {
            val half = fieldOfView(StageSeat.TABLE.pose.copy(lens = lens)) / 2f * (kotlin.math.PI.toFloat() / 180f)
            return 18f / kotlin.math.tan(half)
        }

        val shipped = millimetres(1f)
        assertTrue(abs(shipped - 33f) < 1f, "the shipped lens measures ${shipped}mm, not the 33 the panel prints")
        listOf(0.7f, 1.5f, 2f).forEach {
            assertTrue(
                abs(millimetres(it) - shipped * it) < 1f,
                "a lens of $it measures ${millimetres(it)}mm, not the ${shipped * it} the panel prints",
            )
        }
        // The proportionality above is exact and the *calibration* is not: 33mm is
        // this projection measured at the home distance, so a stage that ever
        // moves `HOME_DISTANCE` has to re-measure `StageTuner.HOME_MM` too. This
        // assertion is the tripwire for that, and it is the reason the seat used
        // here is TABLE rather than any other.
        assertEquals(CameraPose.HOME_DISTANCE, StageSeat.TABLE.pose.distance)
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
        listOf(envelope.minLens, 0.85f, 1f, 1.4f, 2f).forEach { lens ->
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
                        // And the tighter claim, which is the one `minDistanceAt`
                        // actually makes: the floor is solved so that the worst
                        // corner sits at exactly the envelope's own clearance, so
                        // no legal pose may exceed it. `reaches` above is the
                        // renderer's guard and has slack in it on purpose; this
                        // is the contract. A hair of tolerance because the worst
                        // case is the diagonal and the sweep steps yaw by 15°.
                        assertTrue(
                            at.reaches(corner, envelope.clearance + 0.01f),
                            "at lens $lens pitch $pitch yaw $yaw the corner $corner " +
                                "passed the envelope's own clearance of ${envelope.clearance}",
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
