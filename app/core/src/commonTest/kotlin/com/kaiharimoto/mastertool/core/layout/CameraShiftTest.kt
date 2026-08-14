package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The lens shift: that it aims the picture, that it aims nothing else, and that
 * it is not there until somebody moves it.
 *
 * The third of those is most of this file, and it is the third time this pose
 * has grown a field. `CameraPose.lens` reset itself on every frame the camera
 * sprang, because two methods built a pose positionally out of the fields that
 * existed when they were written. `CameraPose.panX` inherited the same trap and
 * was saved by `copy`. This one adds a fourth failure mode the other two never
 * had — a seat that *declares* a shift makes the rig spring toward one, and a
 * field that is carried rather than sprung arrives in a single frame at the end
 * of the travel.
 *
 * The other half is the claim the whole thing rests on: that moving the picture
 * is not moving the eye. A shift must leave the inverse exact, the distance
 * floor still, and every light on the table exactly where it was.
 */
class CameraShiftTest {

    private val width = 1600f
    private val height = 1000f
    private val governing = kotlin.math.max(height, width * 0.55f)
    private val envelope = CameraEnvelope()

    private fun plane(pose: CameraPose) = pose.planeFor(width, height)

    private val poses = listOf(
        StageSeat.OVERHEAD.pose,
        StageSeat.TABLE.pose,
        StageSeat.SEATED.pose,
        CameraPose(yawDegrees = 38f, pitchDegrees = 15f, distance = 1.45f),
        CameraPose(yawDegrees = 150f, pitchDegrees = 52f, distance = 1.96f),
        CameraPose(yawDegrees = -90f, pitchDegrees = 74f, distance = 1.6f),
        CameraPose(yawDegrees = 0f, pitchDegrees = 0f, distance = 1.62f),
    )

    private val shifts = listOf(-0.6f, -0.3f, -0.08f, 0f, 0.08f, 0.3f, 0.6f)

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, note: String = "") {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "$note expected $expected, was $actual",
        )
    }

    // ---- at nothing, nothing happened ---------------------------------------------------

    /**
     * At a shift of zero every projection is what it was, to the bit.
     *
     * Not "close to". `GoldenStageTest` is a recording of this projection at
     * three poses and the whole argument for landing the field inert is that it
     * cannot see it — the same argument `CameraPose.lens` and `panX` made, and
     * the same one that lets this ship in a release ahead of anything that
     * moves it.
     *
     * The reference is the arithmetic the old code did: the pivot landed at the
     * middle of the glass, so `axisX` is `width / 2` and `axisY` is `height / 2`
     * and every term after them is untouched.
     */
    @Test
    fun aShiftOfNothingLeavesEveryProjectionExactlyWhereItWas() {
        poses.forEach { pose ->
            val p = plane(pose)
            assertEquals(width / 2f, p.axisX, "axisX at $pose")
            assertEquals(height / 2f, p.axisY, "axisY at $pose")
            assertEquals(p.centreX, p.axisX, "the axis is the middle at $pose")
            assertEquals(p.centreY, p.axisY, "the axis is the middle at $pose")

            // And the three things the projection hands out, against the closed
            // form written the old way.
            listOf(0f to 0f, width to height, width * 0.31f to height * 0.77f).forEach { (x, y) ->
                val was = p.project(x, y)
                assertEquals(p.centreX + (was.x - p.axisX), was.x, "x at $pose")
                assertEquals(p.centreY + (was.y - p.axisY), was.y, "y at $pose")
            }
        }
    }

    /** And the pose itself defaults to nothing, which is what makes the above true. */
    @Test
    fun everySeatIsStillAimedAtTheMiddleOfTheGlass() {
        StageSeat.entries.forEach { seat ->
            assertEquals(0f, seat.pose.shiftX, "${seat.name} shiftX")
            assertEquals(0f, seat.pose.shiftY, "${seat.name} shiftY")
        }
        assertEquals(0f, CameraPose().shiftX)
        assertEquals(0f, CameraPose().shiftY)
    }

    // ---- it aims the picture ------------------------------------------------------------

    /**
     * What the camera is aimed at is drawn where the shift says, and nowhere else.
     *
     * The defining property. `targetX` is a point on the mat and `axisX` is a
     * point on the glass, and the whole of the shift is that the first lands on
     * the second.
     */
    @Test
    fun whatTheCameraIsAimedAtIsDrawnWhereTheLensIsAimed() {
        poses.forEach { pose ->
            shifts.forEach { sx ->
                shifts.forEach { sy ->
                    val safe = envelope.clamp(pose.copy(shiftX = sx, shiftY = sy), width, height)
                    val p = plane(safe)
                    val middle = p.project(p.targetX, p.targetY)
                    assertClose(width / 2f + sx * governing, middle.x, note = "x at $safe:")
                    assertClose(height / 2f + sy * governing, middle.y, note = "y at $safe:")
                }
            }
        }
    }

    /**
     * A shift moves the whole picture rigidly, and does not restage it.
     *
     * The difference between a shift and a pan, stated as arithmetic: every
     * point moves by exactly the same number of pixels, so no scale, no depth
     * and no shape anywhere in the frame changes. A pan cannot say that — it
     * moves the eye's target, and the perspective across the table follows it.
     */
    @Test
    fun aShiftSlidesThePictureAndChangesNothingInIt() {
        poses.forEach { pose ->
            val at0 = plane(pose)
            val shifted = plane(pose.copy(shiftX = 0.21f, shiftY = -0.14f))
            listOf(
                Vec3(0f, 0f, 0f),
                Vec3(width, height, 0f),
                Vec3(width * 0.4f, height * 0.2f, 90f),
                Vec3(width * 0.8f, height * 0.9f, -30f),
            ).forEach { at ->
                val a = at0.project(at)
                val b = shifted.project(at)
                assertClose(0.21f * governing, b.x - a.x, note = "dx at $pose $at:")
                assertClose(-0.14f * governing, b.y - a.y, note = "dy at $pose $at:")
                assertEquals(a.scale, b.scale, "scale at $pose $at")
                assertEquals(a.depth, b.depth, "depth at $pose $at")
            }
        }
    }

    // ---- and it leaves the inverse exact -------------------------------------------------

    /**
     * `project` and `unprojectAt` still agree, at every shift the envelope allows.
     *
     * This is the claim the KDoc's "one subtraction" rests on, and it is the one
     * that would take the stage down with it: `flatten` is projection composed
     * with its own inverse, and every pile edge, card thickness and airborne
     * shadow on this table is drawn through it.
     */
    @Test
    fun theInverseStaysExactUnderAnyShift() {
        poses.forEach { pose ->
            shifts.forEach { sx ->
                shifts.forEach { sy ->
                    val p = plane(envelope.clamp(pose.copy(shiftX = sx, shiftY = sy), width, height))
                    listOf(
                        Triple(width * 0.2f, height * 0.3f, 0f),
                        Triple(width * 0.75f, height * 0.62f, 0f),
                        Triple(width * 0.5f, height * 0.5f, 120f),
                        Triple(width * 0.1f, height * 0.85f, -45f),
                    ).forEach { (x, y, z) ->
                        val screen = p.project(x, y, z)
                        val back = p.unprojectAt(screen.x, screen.y, z)
                        assertClose(x, back.x, 0.05f, "x at $pose ($sx,$sy):")
                        assertClose(y, back.y, 0.05f, "y at $pose ($sx,$sy):")
                    }
                }
            }
        }
    }

    /**
     * And `flatten` is still the identity on the felt.
     *
     * The narrower claim, and the one worth its own name: geometry that touches
     * the mat has to be drawn exactly where it was computed, or the hit test and
     * the picture stop agreeing about which card you are pointing at.
     */
    @Test
    fun somethingLyingOnTheMatIsStillDrawnWhereItLies() {
        poses.forEach { pose ->
            shifts.forEach { sy ->
                val p = plane(envelope.clamp(pose.copy(shiftY = sy), width, height))
                listOf(0.15f to 0.2f, 0.5f to 0.5f, 0.9f to 0.8f).forEach { (fx, fy) ->
                    val x = width * fx
                    val y = height * fy
                    val flat = p.flatten(x, y, 0f)
                    assertClose(x, flat.x, 0.01f, "x at $pose $sy:")
                    assertClose(y, flat.y, 0.01f, "y at $pose $sy:")
                    assertClose(1f, flat.scale, 0.001f, "scale at $pose $sy:")
                }
            }
        }
    }

    /**
     * The horizon moves with the lens, exactly and by exactly as much.
     *
     * It is a screen row, so it slides with the picture like everything else.
     * Written against the divisor rather than against `horizonY` for the same
     * reason `above` is: two answers about the one row between them must not be
     * able to disagree.
     */
    @Test
    fun theHorizonSlidesWithThePictureAndTheSkyGoesWithIt() {
        val steep = CameraPose(pitchDegrees = 76f, distance = 1.6f)
        val at0 = plane(steep)
        val down = plane(steep.copy(shiftY = 0.25f))

        val was = assertNotNull(at0.horizonY, "there is a horizon at 76 degrees")
        val now = assertNotNull(down.horizonY, "and still one when the lens is aimed")
        assertClose(0.25f * governing, now - was, note = "the horizon slid:")

        // And the sky slid with it, rather than staying where it was. Aiming the
        // lens *down* moves the horizon down the glass, so a row that was
        // looking at the table ends up above the horizon and becomes sky —
        // which is the direction that matters, because the other one would pass
        // whether the sky had moved or not.
        assertTrue(at0.above(was - 4f), "just above the old horizon was sky")
        assertTrue(!at0.above(was + 4f), "and just below it was table")
        assertTrue(down.above(was + 4f), "the same row is sky once the lens is aimed down")
        assertTrue(!down.above(now + 4f), "and the table starts under the new horizon")

        // The clamp, too, or a card dragged toward the top would teleport at one
        // row and not the other.
        assertClose(0.25f * governing, down.belowHorizon(0f) - at0.belowHorizon(0f), note = "the clamp slid:")
    }

    // ---- and moves nothing about where the camera is --------------------------------------

    /**
     * Aiming the lens does not move the distance floor.
     *
     * The mirror of `CameraLensTest.theLensDoesNotMoveTheDistanceFloor`, and the
     * same result for the same reason: the floor is about the mat's corner
     * reaching the lens *plane*, and a shift does not move the camera, so it
     * cannot bring anything closer to it. A pan does and is passed in; this is
     * not, and that is deliberate rather than forgotten.
     */
    @Test
    fun aimingTheLensDoesNotMoveTheDistanceFloor() {
        listOf(0f, 21f, 34f, 58f, 80f).forEach { pitch ->
            val floor = envelope.minDistanceAt(pitch, width, height)
            shifts.forEach { s ->
                val clamped = envelope.clamp(
                    CameraPose(pitchDegrees = pitch, distance = 0.01f, shiftX = s, shiftY = s),
                    width,
                    height,
                )
                assertEquals(floor, clamped.distance, "the floor at $pitch moved with a shift of $s")
            }
        }
    }

    /**
     * And it does not move the eye, so no light on the table moves.
     *
     * `StagePlane.eyePoint` is where every specular pool, every cast shadow and
     * every graze on this stage is solved from. A shift that moved it would
     * slide sixty highlights across the board when all that happened was the
     * picture being framed differently — which is the *opposite* of what a lens
     * shift means, and would be visible immediately.
     */
    @Test
    fun aimingTheLensMovesNoLightOnTheTable() {
        val direction = Vec3(0.3f, 0.45f, 0.84f)
        poses.forEach { pose ->
            val was = plane(pose).eyePoint(direction)
            shifts.forEach { s ->
                val now = plane(pose.copy(shiftX = s, shiftY = -s)).eyePoint(direction)
                assertEquals(was.x, now.x, "eye x at $pose moved with a shift of $s")
                assertEquals(was.y, now.y, "eye y at $pose moved with a shift of $s")
                assertEquals(was.z, now.z, "eye z at $pose moved with a shift of $s")
            }
        }
    }

    // ---- the envelope holds it -----------------------------------------------------------

    @Test
    fun aShiftFromAnywhereAtAllStaysInsideTheLeash() {
        listOf(-40f, -0.61f, 0f, 0.61f, 40f, Float.NaN, Float.POSITIVE_INFINITY).forEach { s ->
            val held = envelope.clamp(
                StageSeat.TABLE.pose.copy(shiftX = s, shiftY = s),
                width,
                height,
            )
            assertTrue(held.shiftX.isFinite(), "shiftX of $s came back as ${held.shiftX}")
            assertTrue(held.shiftY.isFinite(), "shiftY of $s came back as ${held.shiftY}")
            assertTrue(abs(held.shiftX) <= envelope.maxShift, "shiftX of $s was ${held.shiftX}")
            assertTrue(abs(held.shiftY) <= envelope.maxShift, "shiftY of $s was ${held.shiftY}")
            // And a poisoned shift may not reach the projection, which would
            // take every pile edge downstream of it with no exception thrown.
            val p = plane(held)
            assertTrue(p.axisX.isFinite() && p.axisY.isFinite(), "the axis at $s")
        }
    }

    // ---- and it travels rather than arriving --------------------------------------------

    /**
     * A camera sent somewhere with a different shift *travels* to it.
     *
     * The failure this replaces is specific and would have looked like a
     * rendering bug: `step` sprang three fields and then assigned `pose =
     * target` on the frame those three settled, which snapped the other four in
     * one frame. Nobody could see it, because every seat declares a pan of
     * nothing and the pan was the only one of the four a gesture ever moved.
     *
     * So the claim is about the *middle* of the travel, not the end: partway
     * there, the shift is partway there too.
     */
    @Test
    fun aCameraSentSomewhereAimedElsewhereTravelsRatherThanArriving() {
        val rig = CameraRig(seat = StageSeat.TABLE)
        rig.width = width
        rig.height = height
        rig.aimAt(StageSeat.TABLE.pose.copy(shiftY = 0.4f, panX = 0.3f))

        var sawPartway = false
        repeat(400) {
            if (!rig.moving) return@repeat
            rig.step(SpringSpec.Snappy, 1f / 120f)
            val shift = rig.pose.shiftY
            if (shift > 0.02f && shift < 0.38f) sawPartway = true
        }
        assertTrue(sawPartway, "the shift jumped to ${rig.pose.shiftY} instead of travelling")
        assertTrue(!rig.moving, "and it should have come to rest")
        assertClose(0.4f, rig.pose.shiftY, 0.001f, "it arrived:")
        assertClose(0.3f, rig.pose.panX, 0.001f, "and so did the pan:")
    }

    /**
     * A drag does not reset it, and neither does springing to a seat.
     *
     * Two of the three places `CameraPose` has been rebuilt positionally, and
     * both of them cost a release when the lens landed. The third is `glide`.
     */
    @Test
    fun nothingTheCameraDoesForgetsWhereTheLensIsAimed() {
        val rig = CameraRig(seat = StageSeat.TABLE)
        rig.width = width
        rig.height = height
        rig.placeAt(StageSeat.TABLE.pose.copy(shiftY = 0.22f, lens = 1.4f))
        assertClose(0.22f, rig.pose.shiftY, 0.001f, "placed:")

        rig.nudge(deltaYaw = 12f, deltaPitch = 4f, dollyBy = 0.1f)
        assertClose(0.22f, rig.pose.shiftY, 0.001f, "after a drag:")
        assertClose(1.4f, rig.pose.lens, 0.001f, "and the lens after a drag:")

        rig.coast(yawPerSecond = 220f, pitchPerSecond = 0f)
        repeat(300) { rig.step(SpringSpec.Snappy, 1f / 120f) }
        assertClose(0.22f, rig.pose.shiftY, 0.001f, "after a flick ran down:")

        // And a seat zeroes it, because a seat is a place and this is part of
        // being in it — the pan's side of that line, not the lens's.
        rig.aimAt(StageSeat.SEATED)
        repeat(600) { rig.step(SpringSpec.Snappy, 1f / 120f) }
        assertClose(0f, rig.pose.shiftY, 0.001f, "a seat put it back:")
        assertClose(1.4f, rig.pose.lens, 0.001f, "and kept the lens:")
    }

    /**
     * A seat you have aimed away from is not the seat you are in.
     *
     * The readout and the detent both ask `Turns.seatAt`, and a camera that
     * still called itself "Table" while the whole board sat in the bottom third
     * of the glass would be reporting on the wrong thing. The pan is compared
     * for the same reason; the lens is not, because a focal length is not a
     * place.
     */
    @Test
    fun aSeatAimedElsewhereIsNotThatSeat() {
        assertEquals(StageSeat.TABLE, Turns.seatAt(StageSeat.TABLE.pose))
        assertEquals(
            StageSeat.TABLE,
            Turns.seatAt(StageSeat.TABLE.pose.copy(lens = 1.7f)),
            "a lens is not a chair",
        )
        assertEquals(
            null,
            Turns.seatAt(StageSeat.TABLE.pose.copy(shiftY = 0.2f)),
            "but where you are looking is",
        )
        assertEquals(
            null,
            Turns.seatAt(StageSeat.TABLE.pose.copy(shiftX = 0.2f)),
            "across the glass too",
        )
    }
}
